# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

import sys
import datetime
import time
import Queue
from logging import getLogger
from threading import Thread, RLock, currentThread, Event
from qpid.messaging import Connection, Message, Empty, SendError
from uuid import uuid4
from common import (OpCode, QmfQuery, ContentType, SchemaObjectClass,
                    QmfData, QmfAddress, SchemaClass, SchemaClassId, WorkItem,
                    SchemaMethod, timedelta_to_secs, QMF_APP_ID)

# global flag that indicates which thread (if any) is
# running the agent notifier callback
_callback_thread=None

log = getLogger("qmf")
trace = getLogger("qmf.agent")


  ##==============================================================================
  ## METHOD CALL
  ##==============================================================================

class _MethodCallHandle(object):
    """
    Private class used to hold context when handing off a method call to the
    application.  Given to the app in a WorkItem, provided to the agent when
    method_response() is invoked.
    """
    def __init__(self, correlation_id, reply_to, meth_name, _oid=None,
                 _schema_id=None):
        self.correlation_id = correlation_id
        self.reply_to = reply_to
        self.meth_name = meth_name
        self.oid = _oid
        self.schema_id = _schema_id

class MethodCallParams(object):
    """
    """
    def __init__(self, name, _oid=None, _schema_id=None, _in_args=None,
                 _user_id=None):
        self._meth_name = name
        self._oid = _oid
        self._schema_id = _schema_id
        self._in_args = _in_args
        self._user_id = _user_id

    def get_name(self):
        return self._meth_name

    def get_object_id(self):
        return self._oid

    def get_schema_id(self):
        return self._schema_id

    def get_args(self):
        return self._in_args

    def get_user_id(self):
        return self._user_id


  ##==============================================================================
  ## SUBSCRIPTIONS
  ##==============================================================================


class _SubscriptionState(object):
    """
    An internally-managed subscription.
    """
    def __init__(self, reply_to, cid, query, interval, duration):
        self.reply_to = reply_to
        self.correlation_id = cid
        self.query = query
        self.interval = interval
        self.duration = duration
        now = datetime.datetime.utcnow()
        self.next_update = now  # do an immediate update
        self.expiration = now + datetime.timedelta(seconds=duration)
        self.last_update = None
        self.id = 0

    def resubscribe(self, now, _duration=None):
        if _duration is not None:
            self.duration = _duration
        self.expiration = now + datetime.timedelta(seconds=self.duration)

    def published(self, now):
        self.next_update = now + datetime.timedelta(seconds=self.interval)
        self.last_update = now


  ##==============================================================================
  ## AGENT
  ##==============================================================================

class Agent(Thread):
    def __init__(self, name, _domain=None, _notifier=None, **options):
        Thread.__init__(self)
        self._running = False
        self._ready = Event()

        self.name = str(name)
        self._domain = _domain
        self._address = QmfAddress.direct(self.name, self._domain)
        self._notifier = _notifier

        # configurable parameters
        #
        self._heartbeat_interval = options.get("heartbeat_interval", 30)
        self._capacity = options.get("capacity", 10)
        self._default_duration = options.get("default_duration", 300)
        self._max_duration = options.get("max_duration", 3600)
        self._min_duration = options.get("min_duration", 10)
        self._default_interval = options.get("default_interval", 30)
        self._min_interval = options.get("min_interval", 5)

        # @todo: currently, max # of objects in a single reply message, would
        # be better if it were max bytesize of per-msg content...
        self._max_msg_size = options.get("max_msg_size", 0)

        self._conn = None
        self._session = None
        self._direct_receiver = None
        self._topic_receiver = None
        self._direct_sender = None
        self._topic_sender = None

        self._lock = RLock()
        self._packages = {}
        self._schema_timestamp = long(0)
        self._schema = {}
        # _described_data holds QmfData objects that are associated with schema
        # it is index by schema_id, object_id
        self._described_data = {}
        # _undescribed_data holds unstructured QmfData objects - these objects
        # have no schema.  it is indexed by object_id only.
        self._undescribed_data = {}
        self._work_q = Queue.Queue()
        self._work_q_put = False
        # subscriptions
        self._subscription_id = long(time.time())
        self._subscriptions = {}
        self._next_subscribe_event = None

        # prevents multiple _wake_thread() calls
        self._noop_pending = False


    def destroy(self, timeout=None):
        """
        Must be called before the Agent is deleted.  
        Frees up all resources and shuts down all background threads.

        @type timeout: float
        @param timeout: maximum time in seconds to wait for all background threads to terminate.  Default: forever.
        """
        trace.debug("Destroying Agent %s" % self.name)
        if self._conn:
            self.remove_connection(timeout)
        trace.debug("Agent Destroyed")


    def get_name(self):
        return self.name

    def set_connection(self, conn):
        self._conn = conn
        self._session = self._conn.session()

        # for messages directly addressed to me
        self._direct_receiver = self._session.receiver(str(self._address) +
                                                       ";{create:always,"
                                                       " node-properties:"
                                                       " {type:topic,"
                                                       " x-properties:"
                                                       " {type:direct}}}",
                                                       capacity=self._capacity)
        trace.debug("my direct addr=%s" % self._direct_receiver.source)

        # for sending directly addressed messages.
        self._direct_sender = self._session.sender(str(self._address.get_node()) +
                                                   ";{create:always,"
                                                   " node-properties:"
                                                   " {type:topic,"
                                                   " x-properties:"
                                                   " {type:direct}}}")
        trace.debug("my default direct send addr=%s" % self._direct_sender.target)

        # for receiving "broadcast" messages from consoles
        default_addr = QmfAddress.topic(QmfAddress.SUBJECT_CONSOLE_IND + ".#",
                                        self._domain)
        self._topic_receiver = self._session.receiver(str(default_addr) +
                                                       ";{create:always,"
                                                       " node-properties:"
                                                       " {type:topic}}",
                                                       capacity=self._capacity)
        trace.debug("console.ind addr=%s" % self._topic_receiver.source)

        # for sending to topic subscribers
        ind_addr = QmfAddress.topic(QmfAddress.SUBJECT_AGENT_IND,
                                    self._domain)
        self._topic_sender = self._session.sender(str(ind_addr) +
                                                ";{create:always,"
                                                " node-properties:"
                                                " {type:topic}}")
        trace.debug("agent.ind addr=%s" % self._topic_sender.target)

        self._running = True
        self.start()
        self._ready.wait(10)
        if not self._ready.isSet():
            raise Exception("Agent managment thread failed to start.")

    def remove_connection(self, timeout=None):
        # tell connection thread to shutdown
        self._running = False
        if self.isAlive():
            # kick my thread to wake it up
            self._wake_thread()
            trace.debug("waiting for agent receiver thread to exit")
            self.join(timeout)
            if self.isAlive():
                log.error( "Agent thread '%s' is hung..." % self.name)
        self._direct_receiver.close()
        self._direct_receiver = None
        self._direct_sender.close()
        self._direct_sender = None
        self._topic_receiver.close()
        self._topic_receiver = None
        self._topic_sender.close()
        self._topic_sender = None
        self._session.close()
        self._session = None
        self._conn = None
        trace.debug("agent connection removal complete")

    def register_object_class(self, schema):
        """
        Register an instance of a SchemaClass with this agent
        """
        # @todo: need to update subscriptions
        # @todo: need to mark schema as "non-const"
        if not isinstance(schema, SchemaClass):
            raise TypeError("SchemaClass instance expected")

        classId = schema.get_class_id()
        pname = classId.get_package_name()
        cname = classId.get_class_name()
        hstr = classId.get_hash_string()
        if not hstr:
            raise Exception("Schema hash is not set.")

        self._lock.acquire()
        try:
            if pname not in self._packages:
                self._packages[pname] = [cname]
            else:
                if cname not in self._packages[pname]:
                    self._packages[pname].append(cname)
            self._schema[classId] = schema
            self._schema_timestamp = long(time.time() * 1000)
        finally:
            self._lock.release()

    def register_event_class(self, schema):
        return self.register_object_class(schema)

    def raise_event(self, qmfEvent):
        """
        TBD
        """
        if not self._topic_sender:
            raise Exception("No connection available")

        # @todo: should we validate against the schema?
        msg = Message(id=QMF_APP_ID,
                      subject=QmfAddress.SUBJECT_AGENT_EVENT + "." +
                      qmfEvent.get_severity() + "." + self.name,
                      properties={"method":"indication",
                                  "qmf.opcode":OpCode.data_ind,
                                  "qmf.content": ContentType.event,
                                  "qmf.agent":self.name},
                      content=[qmfEvent.map_encode()])
        # TRACE
        # log.error("!!! Agent %s sending Event (%s)" % 
        # (self.name, str(msg)))
        self._topic_sender.send(msg)

    def add_object(self, data):
        """
        Register an instance of a QmfAgentData object.
        """
        # @todo: need to mark schema as "non-const"
        if not isinstance(data, QmfAgentData):
            raise TypeError("QmfAgentData instance expected")

        oid = data.get_object_id()
        if not oid:
            raise TypeError("No identifier assigned to QmfAgentData!")

        sid = data.get_schema_class_id()

        self._lock.acquire()
        try:
            if sid:
                if sid not in self._described_data:
                    self._described_data[sid] = {oid: data}
                else:
                    self._described_data[sid][oid] = data
            else:
                self._undescribed_data[oid] = data

            # does the new object match any subscriptions?
            now = datetime.datetime.utcnow()
            for sid,sub in self._subscriptions.iteritems():
                if sub.query.evaluate(data):
                    # matched.  Mark the subscription as needing to be
                    # serviced. The _publish() method will notice the new
                    # object and will publish it next time it runs.
                    sub.next_update = now
                    self._next_subscribe_event = None
                    # @todo: should we immediately publish?

        finally:
            self._lock.release()

    def get_object(self, oid, schema_id):
        data = None
        self._lock.acquire()
        try:
            if schema_id:
                data = self._described_data.get(schema_id)
                if data:
                    data = data.get(oid)
            else:
                data = self._undescribed_data.get(oid)
        finally:
            self._lock.release()
        return data


    def method_response(self, handle, _out_args=None, _error=None):
        """
        """
        if not isinstance(handle, _MethodCallHandle):
            raise TypeError("Invalid handle passed to method_response!")

        _map = {SchemaMethod.KEY_NAME:handle.meth_name}
        if handle.oid is not None:
            _map[QmfData.KEY_OBJECT_ID] = handle.oid
        if handle.schema_id is not None:
            _map[QmfData.KEY_SCHEMA_ID] = handle.schema_id.map_encode()
        if _out_args is not None:
            _map[SchemaMethod.KEY_ARGUMENTS] = _out_args.copy()
        if _error is not None:
            if not isinstance(_error, QmfData):
                raise TypeError("Invalid type for error - must be QmfData")
            _map[SchemaMethod.KEY_ERROR] = _error.map_encode()

        msg = Message(id=QMF_APP_ID,
                      properties={"method":"response",
                                  "qmf.opcode":OpCode.method_rsp},
                      content=_map)
        msg.correlation_id = handle.correlation_id

        self._send_reply(msg, handle.reply_to)

    def get_workitem_count(self): 
        """ 
        Returns the count of pending WorkItems that can be retrieved.
        """
        return self._work_q.qsize()

    def get_next_workitem(self, timeout=None): 
        """
        Obtains the next pending work item, or None if none available. 
        """
        try:
            wi = self._work_q.get(True, timeout)
        except Queue.Empty:
            return None
        return wi

    def release_workitem(self, wi): 
        """
        Releases a WorkItem instance obtained by getNextWorkItem(). Called when 
        the application has finished processing the WorkItem. 
        """
        pass


    def run(self):
        global _callback_thread
        next_heartbeat = datetime.datetime.utcnow()
        batch_limit = 10 # a guess

        self._ready.set()

        while self._running:

            #
            # Process inbound messages
            #
            trace.debug("%s processing inbound messages..." % self.name)
            for i in range(batch_limit):
                try:
                    msg = self._topic_receiver.fetch(timeout=0)
                except Empty:
                    break
                # TRACE
                # log.error("!!! Agent %s: msg on %s [%s]" %
                # (self.name, self._topic_receiver.source, msg))
                self._dispatch(msg, _direct=False)

            for i in range(batch_limit):
                try:
                    msg = self._direct_receiver.fetch(timeout=0)
                except Empty:
                    break
                # TRACE
                # log.error("!!! Agent %s: msg on %s [%s]" %
                # (self.name, self._direct_receiver.source, msg))
                self._dispatch(msg, _direct=True)

            #
            # Send Heartbeat Notification
            #
            now = datetime.datetime.utcnow()
            if now >= next_heartbeat:
                trace.debug("%s sending heartbeat..." % self.name)
                ind = Message(id=QMF_APP_ID,
                              subject=QmfAddress.SUBJECT_AGENT_HEARTBEAT,
                              properties={"method":"indication",
                                          "qmf.opcode":OpCode.agent_heartbeat_ind,
                                          "qmf.agent":self.name},
                              content=self._makeAgentInfoBody())
                # TRACE
                #log.error("!!! Agent %s sending Heartbeat (%s)" % 
                # (self.name, str(ind)))
                self._topic_sender.send(ind)
                trace.debug("Agent Indication Sent")
                next_heartbeat = now + datetime.timedelta(seconds = self._heartbeat_interval)

            #
            # Monitor Subscriptions
            #
            self._lock.acquire()
            try:
                now = datetime.datetime.utcnow()
                if (self._next_subscribe_event is None or
                    now >= self._next_subscribe_event):
                    trace.debug("%s polling subscriptions..." % self.name)
                    self._next_subscribe_event = now + datetime.timedelta(seconds=
                                                                      self._max_duration)
                    dead_ss = {}
                    for sid,ss in self._subscriptions.iteritems():
                        if now >= ss.expiration:
                            dead_ss[sid] = ss
                            continue
                        if now >= ss.next_update:
                            self._publish(ss)
                        next_timeout = min(ss.expiration, ss.next_update)
                        if next_timeout < self._next_subscribe_event:
                            self._next_subscribe_event = next_timeout

                    for sid,ss in dead_ss.iteritems():
                        del self._subscriptions[sid]
                        self._unpublish(ss)
            finally:
                self._lock.release()

            #
            # notify application of pending WorkItems
            #
            if self._work_q_put and self._notifier:
                trace.debug("%s notifying application..." % self.name)
                # new stuff on work queue, kick the the application...
                self._work_q_put = False
                _callback_thread = currentThread()
                trace.debug("Calling agent notifier.indication")
                self._notifier.indication()
                _callback_thread = None

            #
            # Sleep until messages arrive or something times out
            #
            now = datetime.datetime.utcnow()
            next_timeout = next_heartbeat
            self._lock.acquire()
            try:
                # the mailbox expire flag may be cleared by the
                # app thread(s) in order to force an immediate publish
                if self._next_subscribe_event is None:
                    next_timeout = now
                elif self._next_subscribe_event < next_timeout:
                    next_timeout = self._next_subscribe_event
            finally:
                self._lock.release()

            timeout = timedelta_to_secs(next_timeout - now)

            if self._running and timeout > 0.0:
                trace.debug("%s sleeping %s seconds..." % (self.name,
                                                             timeout))
                try:
                    self._session.next_receiver(timeout=timeout)
                except Empty:
                    pass


        trace.debug("Shutting down Agent %s thread" % self.name)

    #
    # Private:
    #

    def _makeAgentInfoBody(self):
        """
        Create an agent indication message body identifying this agent
        """
        return QmfData.create({"_name": self.get_name(),
                              "_schema_timestamp": self._schema_timestamp}).map_encode()

    def _send_reply(self, msg, reply_to):
        """
        Send a reply message to the given reply_to address
        """
        if not isinstance(reply_to, QmfAddress):
            try:
                reply_to = QmfAddress.from_string(str(reply_to))
            except ValueError:
                log.error("Invalid reply-to address '%s'" % reply_to)

        msg.subject = reply_to.get_subject()

        try:
            if reply_to.is_direct():
                # TRACE
                #log.error("!!! Agent %s direct REPLY-To:%s (%s)" % 
                # (self.name, str(reply_to), str(msg)))
                self._direct_sender.send(msg)
            else:
                # TRACE
                # log.error("!!! Agent %s topic REPLY-To:%s (%s)" % 
                # (self.name, str(reply_to), str(msg)))
                self._topic_sender.send(msg)
            trace.debug("reply msg sent to [%s]" % str(reply_to))
        except SendError, e:
            log.error("Failed to send reply msg '%s' (%s)" % (msg, str(e)))

    def _send_query_response(self, content_type, cid, reply_to, objects):
        """
        Send a response to a query, breaking the result into multiple
        messages based on the agent's _max_msg_size config parameter
        """

        total = len(objects)
        if self._max_msg_size:
            max_count = self._max_msg_size
        else:
            max_count = total

        start = 0
        end = min(total, max_count)
        # send partial response if too many objects present
        while end < total:
            m = Message(id=QMF_APP_ID,
                        properties={"method":"response",
                                    "partial":None,
                                    "qmf.opcode":OpCode.data_ind,
                                    "qmf.content":content_type,
                                    "qmf.agent":self.name},
                        correlation_id = cid,
                        content=objects[start:end])
            self._send_reply(m, reply_to)
            start = end
            end = min(total, end + max_count)

        m = Message(id=QMF_APP_ID,
                    properties={"method":"response",
                                "qmf.opcode":OpCode.data_ind,
                                "qmf.content":content_type,
                                "qmf.agent":self.name},
                    correlation_id = cid,
                    content=objects[start:end])
        self._send_reply(m, reply_to)

    def _dispatch(self, msg, _direct=False):
        """
        Process a message from a console.

        @param _direct: True if msg directly addressed to this agent.
        """
        trace.debug( "Message received from Console! [%s]" % msg )

        opcode = msg.properties.get("qmf.opcode")
        if not opcode:
            log.warning("Ignoring unrecognized message '%s'" % msg)
            return
        version = 2  # @todo: fix me
        cmap = {}; props={}
        if msg.content_type == "amqp/map":
            cmap = msg.content
        if msg.properties:
            props = msg.properties

        if opcode == OpCode.agent_locate_req:
            self._handleAgentLocateMsg( msg, cmap, props, version, _direct )
        elif opcode == OpCode.query_req:
            self._handleQueryMsg( msg, cmap, props, version, _direct )
        elif opcode == OpCode.method_req:
            self._handleMethodReqMsg(msg, cmap, props, version, _direct)
        elif opcode == OpCode.subscribe_req:
            self._handleSubscribeReqMsg(msg, cmap, props, version, _direct)
        elif opcode == OpCode.subscribe_refresh_req:
            self._handleResubscribeReqMsg(msg, cmap, props, version, _direct)
        elif opcode == OpCode.subscribe_cancel_ind:
            self._handleUnsubscribeReqMsg(msg, cmap, props, version, _direct)
        elif opcode == OpCode.noop:
            self._noop_pending = False
            trace.debug("No-op msg received.")
        else:
            log.warning("Ignoring message with unrecognized 'opcode' value: '%s'"
                            % opcode)

    def _handleAgentLocateMsg( self, msg, cmap, props, version, direct ):
        """
        Process a received agent-locate message
        """
        trace.debug("_handleAgentLocateMsg")

        reply = False
        if props.get("method") == "request":
            # if the message is addressed to me or wildcard, process it
            if (msg.subject == "console.ind" or
                msg.subject == "console.ind.locate" or
                msg.subject == "console.ind.locate." + self.name):
                pred = msg.content
                if not pred:
                    reply = True
                elif isinstance(pred, type([])):
                    # fake a QmfData containing my identifier for the query compare
                    query = QmfQuery.create_predicate(QmfQuery.TARGET_AGENT, pred)
                    tmpData = QmfData.create({QmfQuery.KEY_AGENT_NAME:
                                                  self.get_name()},
                                             _object_id="my-name")
                    reply = query.evaluate(tmpData)

        if reply:
            m = Message(id=QMF_APP_ID,
                        properties={"method":"response",
                                    "qmf.opcode":OpCode.agent_locate_rsp},
                        content=self._makeAgentInfoBody())
            m.correlation_id = msg.correlation_id
            self._send_reply(m, msg.reply_to)
        else:
            trace.debug("agent-locate msg not mine - no reply sent")


    def _handleQueryMsg(self, msg, cmap, props, version, _direct ):
        """
        Handle received query message
        """
        trace.debug("_handleQueryMsg")

        if "method" in props and props["method"] == "request":
            if cmap:
                try:
                    query = QmfQuery.from_map(cmap)
                except TypeError:
                    log.error("Invalid Query format: '%s'" % str(cmap))
                    return
                target = query.get_target()
                if target == QmfQuery.TARGET_PACKAGES:
                    self._queryPackagesReply( msg, query )
                elif target == QmfQuery.TARGET_SCHEMA_ID:
                    self._querySchemaReply( msg, query, _idOnly=True )
                elif target == QmfQuery.TARGET_SCHEMA:
                    self._querySchemaReply( msg, query)
                elif target == QmfQuery.TARGET_AGENT:
                    log.warning("!!! @todo: Query TARGET=AGENT TBD !!!")
                elif target == QmfQuery.TARGET_OBJECT_ID:
                    self._queryDataReply(msg, query, _idOnly=True)
                elif target == QmfQuery.TARGET_OBJECT:
                    self._queryDataReply(msg, query)
                else:
                    log.warning("Unrecognized query target: '%s'" % str(target))



    def _handleMethodReqMsg(self, msg, cmap, props, version, _direct):
        """
        Process received Method Request
        """
        if "method" in props and props["method"] == "request":
            mname = cmap.get(SchemaMethod.KEY_NAME)
            if not mname:
                log.warning("Invalid method call from '%s': no name"
                                % msg.reply_to)
                return

            in_args = cmap.get(SchemaMethod.KEY_ARGUMENTS)
            oid = cmap.get(QmfData.KEY_OBJECT_ID)
            schema_id = cmap.get(QmfData.KEY_SCHEMA_ID)
            if schema_id:
                schema_id = SchemaClassId.from_map(schema_id)
            handle = _MethodCallHandle(msg.correlation_id,
                                       msg.reply_to,
                                       mname,
                                       oid, schema_id)
            param = MethodCallParams( mname, oid, schema_id, in_args,
                                      msg.user_id)

            # @todo: validate the method against the schema:
            # if self._schema:
            #     # validate
            #     _in_args = _in_args.copy()
            #     ms = self._schema.get_method(name)
            #     if ms is None:
            #         raise ValueError("Method '%s' is undefined." % name)

            #     for aname,prop in ms.get_arguments().iteritems():
            #         if aname not in _in_args:
            #             if prop.get_default():
            #                 _in_args[aname] = prop.get_default()
            #             elif not prop.is_optional():
            #                 raise ValueError("Method '%s' requires argument '%s'"
            #                                  % (name, aname))
            #     for aname in _in_args.iterkeys():
            #         prop = ms.get_argument(aname)
            #         if prop is None:
            #             raise ValueError("Method '%s' does not define argument"
            #                              " '%s'" % (name, aname))
            #         if "I" not in prop.get_direction():
            #             raise ValueError("Method '%s' argument '%s' is not an"
            #                              " input." % (name, aname)) 

            #     # @todo check if value is correct (type, range, etc)

            self._work_q.put(WorkItem(WorkItem.METHOD_CALL, handle, param))
            self._work_q_put = True

    def _handleSubscribeReqMsg(self, msg, cmap, props, version, _direct):
        """
        Process received Subscription Request
        """
        if "method" in props and props["method"] == "request":
            query_map = cmap.get("_query")
            interval = cmap.get("_interval")
            duration = cmap.get("_duration")

            try:
                query = QmfQuery.from_map(query_map)
            except TypeError:
                log.warning("Invalid query for subscription: %s" %
                                str(query_map))
                return

            if isinstance(self, AgentExternal):
                # param = SubscriptionParams(_ConsoleHandle(console_handle,
                #                                           msg.reply_to),
                #                            query,
                #                            interval,
                #                            duration,
                #                            msg.user_id)
                # self._work_q.put(WorkItem(WorkItem.SUBSCRIBE_REQUEST,
                #                           msg.correlation_id, param))
                # self._work_q_put = True
                log.error("External Subscription TBD")
                return

            # validate the query - only specific objects, or
            # objects wildcard, are currently supported.
            if (query.get_target() != QmfQuery.TARGET_OBJECT or
                (query.get_selector() == QmfQuery.PREDICATE and
                 query.get_predicate())):
                log.error("Subscriptions only support (wildcard) Object"
                              " Queries.")
                err = QmfData.create(
                    {"reason": "Unsupported Query type for subscription.",
                     "query": str(query.map_encode())})
                m = Message(id=QMF_APP_ID,
                            properties={"method":"response",
                                        "qmf.opcode":OpCode.subscribe_rsp},
                            correlation_id = msg.correlation_id,
                            content={"_error": err.map_encode()})
                self._send_reply(m, msg.reply_to)
                return

            if duration is None:
                duration = self._default_duration
            else:
                try:
                    duration = float(duration)
                    if duration > self._max_duration:
                        duration = self._max_duration
                    elif duration < self._min_duration:
                        duration = self._min_duration
                except:
                    log.warning("Bad duration value: %s" % str(msg))
                    duration = self._default_duration

            if interval is None:
                interval = self._default_interval
            else:
                try:
                    interval = float(interval)
                    if interval < self._min_interval:
                        interval = self._min_interval
                except:
                    log.warning("Bad interval value: %s" % str(msg))
                    interval = self._default_interval

            ss = _SubscriptionState(msg.reply_to,
                                    msg.correlation_id,
                                    query,
                                    interval,
                                    duration)
            self._lock.acquire()
            try:
                sid = self._subscription_id
                self._subscription_id += 1
                ss.id = sid
                self._subscriptions[sid] = ss
                self._next_subscribe_event = None
            finally:
                self._lock.release()

            sr_map = {"_subscription_id": sid,
                      "_interval": interval,
                      "_duration": duration}
            m = Message(id=QMF_APP_ID,
                        properties={"method":"response",
                                   "qmf.opcode":OpCode.subscribe_rsp},
                        correlation_id = msg.correlation_id,
                        content=sr_map)
            self._send_reply(m, msg.reply_to)



    def _handleResubscribeReqMsg(self, msg, cmap, props, version, _direct):
        """
        Process received Renew Subscription Request
        """
        if props.get("method") == "request":
            sid = cmap.get("_subscription_id")
            if not sid:
                log.error("Invalid subscription refresh msg: %s" %
                              str(msg))
                return

            self._lock.acquire()
            try:
                ss = self._subscriptions.get(sid)
                if not ss:
                    log.error("Ignoring unknown subscription: %s" %
                                  str(sid))
                    return
                duration = cmap.get("_duration")
                if duration is not None:
                    try:
                        duration = float(duration)
                        if duration > self._max_duration:
                            duration = self._max_duration
                        elif duration < self._min_duration:
                            duration = self._min_duration
                    except:
                        log.error("Bad duration value: %s" % str(msg))
                        duration = None  # use existing duration

                ss.resubscribe(datetime.datetime.utcnow(), duration)

                new_duration = ss.duration
                new_interval = ss.interval

            finally:
                self._lock.release()


            sr_map = {"_subscription_id": sid,
                      "_interval": new_interval,
                      "_duration": new_duration}
            m = Message(id=QMF_APP_ID,
                        properties={"method":"response",
                                   "qmf.opcode":OpCode.subscribe_refresh_rsp},
                        correlation_id = msg.correlation_id,
                        content=sr_map)
            self._send_reply(m, msg.reply_to)


    def _handleUnsubscribeReqMsg(self, msg, cmap, props, version, _direct):
        """
        Process received Cancel Subscription Request
        """
        if props.get("method") == "request":
            sid = cmap.get("_subscription_id")
            if not sid:
                log.warning("No subscription id supplied: %s" % msg)
                return

            self._lock.acquire()
            try:
                if sid in self._subscriptions:
                    dead_sub = self._subscriptions[sid]
                    del self._subscriptions[sid]
            finally:
                self._lock.release()

            self._unpublish(dead_sub)


    def _queryPackagesReply(self, msg, query):
        """
        Run a query against the list of known packages
        """
        pnames = []
        self._lock.acquire()
        try:
            for name in self._packages.iterkeys():
                qmfData = QmfData.create({SchemaClassId.KEY_PACKAGE:name},
                                         _object_id="_package")
                if query.evaluate(qmfData):
                    pnames.append(name)

            self._send_query_response(ContentType.schema_package,
                                      msg.correlation_id,
                                      msg.reply_to,
                                      pnames)
        finally:
            self._lock.release()


    def _querySchemaReply( self, msg, query, _idOnly=False ):
        """
        """
        schemas = []

        self._lock.acquire()
        try:
            # if querying for a specific schema, do a direct lookup
            if query.get_selector() == QmfQuery.ID:
                found = self._schema.get(query.get_id())
                if found:
                    if _idOnly:
                        schemas.append(query.get_id().map_encode())
                    else:
                        schemas.append(found.map_encode())
            else: # otherwise, evaluate all schema
                for sid,val in self._schema.iteritems():
                    if query.evaluate(val):
                        if _idOnly:
                            schemas.append(sid.map_encode())
                        else:
                            schemas.append(val.map_encode())
            if _idOnly:
                msgkey = ContentType.schema_id
            else:
                msgkey = ContentType.schema_class

            self._send_query_response(msgkey,
                                      msg.correlation_id,
                                      msg.reply_to,
                                      schemas)
        finally:
            self._lock.release()


    def _queryDataReply( self, msg, query, _idOnly=False ):
        """
        """
        # hold the (recursive) lock for the duration so the Agent
        # won't send data that is currently being modified by the
        # app.
        self._lock.acquire()
        try:
            response = []
            data_objs = self._queryData(query)
            if _idOnly:
                for obj in data_objs:
                    response.append(obj.get_object_id())
            else:
                for obj in data_objs:
                    response.append(obj.map_encode())

            if _idOnly:
                msgkey = ContentType.object_id
            else:
                msgkey = ContentType.data

            self._send_query_response(msgkey,
                                      msg.correlation_id,
                                      msg.reply_to,
                                      response)
        finally:
            self._lock.release()


    def _queryData(self, query):
        """
        Return a list of QmfData objects that match a given query
        """
        data_objs = []
        # extract optional schema_id from target params
        sid = None
        t_params = query.get_target_param()
        if t_params:
            sid = t_params.get(QmfData.KEY_SCHEMA_ID)

        self._lock.acquire()
        try:
            # if querying for a specific object, do a direct lookup
            if query.get_selector() == QmfQuery.ID:
                oid = query.get_id()
                if sid and not sid.get_hash_string():
                    # wildcard schema_id match, check each schema
                    for name,db in self._described_data.iteritems():
                        if (name.get_class_name() == sid.get_class_name()
                            and name.get_package_name() == sid.get_package_name()):
                            found = db.get(oid)
                            if found:
                                data_objs.append(found)
                else:
                    found = None
                    if sid:
                        db = self._described_data.get(sid)
                        if db:
                            found = db.get(oid)
                    else:
                        found = self._undescribed_data.get(oid)
                    if found:
                        data_objs.append(found)

            else: # otherwise, evaluate all data
                if sid and not sid.get_hash_string():
                    # wildcard schema_id match, check each schema
                    for name,db in self._described_data.iteritems():
                        if (name.get_class_name() == sid.get_class_name()
                            and name.get_package_name() == sid.get_package_name()):
                            for oid,data in db.iteritems():
                                if query.evaluate(data):
                                    data_objs.append(data)
                else:
                    if sid:
                        db = self._described_data.get(sid)
                    else:
                        db = self._undescribed_data

                    if db:
                        for oid,data in db.iteritems():
                            if query.evaluate(data):
                                data_objs.append(data)
        finally:
            self._lock.release()

        return data_objs

    def _publish(self, sub):
        """ Publish a subscription.
        """
        response = []
        now = datetime.datetime.utcnow()
        objs = self._queryData(sub.query)
        if objs:
            for obj in objs:
                if sub.id not in obj._subscriptions:
                    # new to subscription - publish it
                    obj._subscriptions[sub.id] = sub
                    response.append(obj.map_encode())
                elif obj._dtime:
                    # obj._dtime is millisec since utc.  Convert to datetime
                    utcdt = datetime.datetime.utcfromtimestamp(obj._dtime/1000.0)
                    if utcdt > sub.last_update:
                        response.append(obj.map_encode())
                else:
                    # obj._utime is millisec since utc.  Convert to datetime
                    utcdt = datetime.datetime.utcfromtimestamp(obj._utime/1000.0)
                    if utcdt > sub.last_update:
                        response.append(obj.map_encode())

            if response:
                trace.debug("!!! %s publishing %s!!!" % (self.name, sub.correlation_id))
                self._send_query_response( ContentType.data,
                                           sub.correlation_id,
                                           sub.reply_to,
                                           response)
        sub.published(now)

    def _unpublish(self, sub):
        """ This subscription is about to be deleted, remove it from any
        referencing objects.
        """
        objs = self._queryData(sub.query)
        if objs:
            for obj in objs:
                if sub.id in obj._subscriptions:
                    del obj._subscriptions[sub.id]



    def _wake_thread(self):
        """
        Make the agent management thread loop wakeup from its next_receiver
        sleep.
        """
        self._lock.acquire()
        try:
            if not self._noop_pending:
                trace.debug("Sending noop to wake up [%s]" % self._address)
                msg = Message(id=QMF_APP_ID,
                              subject=self.name,
                              properties={"method":"indication",
                                          "qmf.opcode":OpCode.noop},
                              content={})
                try:
                    self._direct_sender.send( msg, sync=True )
                    self._noop_pending = True
                except SendError, e:
                    log.error(str(e))
        finally:
            self._lock.release()


  ##==============================================================================
  ## EXTERNAL DATABASE AGENT
  ##==============================================================================

class AgentExternal(Agent):
    """
    An Agent which uses an external management database.
    """
    def __init__(self, name, _domain=None, _notifier=None,
                 _heartbeat_interval=30, _max_msg_size=0, _capacity=10):
        super(AgentExternal, self).__init__(name, _domain, _notifier,
                                            _heartbeat_interval,
                                            _max_msg_size, _capacity)
        log.error("AgentExternal TBD")



  ##==============================================================================
  ## DATA MODEL
  ##==============================================================================


class QmfAgentData(QmfData):
    """
    A managed data object that is owned by an agent.
    """

    def __init__(self, agent, _values={}, _subtypes={}, _tag=None,
                 _object_id=None, _schema=None):
        schema_id = None
        if _schema:
            schema_id = _schema.get_class_id()

        if _object_id is None:
            if not isinstance(_schema, SchemaObjectClass):
                raise Exception("An object_id must be provided if the object"
                                "doesn't have an associated schema.")
            ids = _schema.get_id_names()
            if not ids:
                raise Exception("Object must have an Id or a schema that"
                                " provides an Id")
            _object_id = u""
            for key in ids:
                value = _values.get(key)
                if value is None:
                    raise Exception("Object must have a value for key"
                                    " attribute '%s'" % str(key))
                try:
                    _object_id += unicode(value)
                except:
                    raise Exception("Cannot create object_id from key" 
                                    " value '%s'" % str(value))

        # timestamp in millisec since epoch UTC
        ctime = long(time.time() * 1000)
        super(QmfAgentData, self).__init__(_values=_values, _subtypes=_subtypes,
                                           _tag=_tag, _ctime=ctime,
                                           _utime=ctime, _object_id=_object_id,
                                           _schema_id=schema_id, _const=False)
        self._agent = agent
        self._validated = False
        self._modified = True
        self._subscriptions = {}

    def destroy(self): 
        self._dtime = long(time.time() * 1000)
        self._touch()
        # @todo: publish change

    def is_deleted(self): 
        return self._dtime == 0

    def set_value(self, _name, _value, _subType=None):
        super(QmfAgentData, self).set_value(_name, _value, _subType)
        self._utime = long(time.time() * 1000)
        self._touch(_name)
        # @todo: publish change

    def inc_value(self, name, delta=1):
        """ add the delta to the property """
        # @todo: need to take write-lock
        val = self.get_value(name)
        try:
            val += delta
        except:
            raise
        self.set_value(name, val)

    def dec_value(self, name, delta=1): 
        """ subtract the delta from the property """
        # @todo: need to take write-lock
        val = self.get_value(name)
        try:
            val -= delta
        except:
            raise
        self.set_value(name, val)

    def validate(self):
        """
        Compares this object's data against the associated schema.  Throws an
        exception if the data does not conform to the schema.
        """
        props = self._schema.get_properties()
        for name,val in props.iteritems():
            # @todo validate: type compatible with amqp_type?
            # @todo validate: primary keys have values
            if name not in self._values:
                if val._isOptional:
                    # ok not to be present, put in dummy value
                    # to simplify access
                    self._values[name] = None
                else:
                    raise Exception("Required property '%s' not present." % name)
        self._validated = True

    def _touch(self, field=None):
        """
        Mark this object as modified.  Used to force a publish of this object
        if on subscription.
        """
        now = datetime.datetime.utcnow()
        publish = False
        if field:
            # if the named field is not continuous, mark any subscriptions as
            # needing to be published.
            sid = self.get_schema_class_id()
            if sid:
                self._agent._lock.acquire()
                try:
                    schema = self._agent._schema.get(sid)
                    if schema:
                        prop = schema.get_property(field)
                        if prop and not prop.is_continuous():
                            for sid,sub in self._subscriptions.iteritems():
                                sub.next_update = now
                                publish = True
                    if publish:
                        self._agent._next_subscribe_event = None
                        self._agent._wake_thread()
                finally:
                    self._agent._lock.release()



################################################################################
################################################################################
################################################################################
################################################################################

if __name__ == '__main__':
    # static test cases - no message passing, just exercise API
    import logging
    from common import (AgentName, SchemaProperty, qmfTypes, SchemaEventClass)

    logging.getLogger().setLevel(logging.INFO)

    logging.info( "Create an Agent" )
    _agent_name = AgentName("redhat.com", "agent", "tross")
    _agent = Agent(str(_agent_name))

    logging.info( "Get agent name: '%s'" % _agent.get_name())

    logging.info( "Create SchemaObjectClass" )

    _schema = SchemaObjectClass(SchemaClassId("MyPackage", "MyClass"),
                                _desc="A test data schema",
                                _object_id_names=["index1", "index2"])
    # add properties
    _schema.add_property("index1", SchemaProperty(qmfTypes.TYPE_UINT8))
    _schema.add_property("index2", SchemaProperty(qmfTypes.TYPE_LSTR)) 

    # these two properties are statistics
    _schema.add_property("query_count", SchemaProperty(qmfTypes.TYPE_UINT32))
    _schema.add_property("method_call_count", SchemaProperty(qmfTypes.TYPE_UINT32))
    # These two properties can be set via the method call
    _schema.add_property("set_string", SchemaProperty(qmfTypes.TYPE_LSTR))
    _schema.add_property("set_int", SchemaProperty(qmfTypes.TYPE_UINT32))

    # add method
    _meth = SchemaMethod(_desc="Method to set string and int in object." )
    _meth.add_argument( "arg_int", SchemaProperty(qmfTypes.TYPE_UINT32) )
    _meth.add_argument( "arg_str", SchemaProperty(qmfTypes.TYPE_LSTR) )
    _schema.add_method( "set_meth", _meth )

    # Add schema to Agent

    print("Schema Map='%s'" % str(_schema.map_encode()))

    _agent.register_object_class(_schema)

    # instantiate managed data objects matching the schema

    logging.info( "Create QmfAgentData" )

    _obj = QmfAgentData( _agent, _schema=_schema )
    _obj.set_value("index1", 100)
    _obj.set_value("index2", "a name" )
    _obj.set_value("set_string", "UNSET")
    _obj.set_value("set_int", 0)
    _obj.set_value("query_count", 0)
    _obj.set_value("method_call_count", 0)

    print("Obj1 Map='%s'" % str(_obj.map_encode()))

    _agent.add_object( _obj )

    _obj = QmfAgentData( _agent, 
                         _values={"index1":99, 
                                  "index2": "another name",
                                  "set_string": "UNSET",
                                  "set_int": 0,
                                  "query_count": 0,
                                  "method_call_count": 0},
                         _schema=_schema)

    print("Obj2 Map='%s'" % str(_obj.map_encode()))

    _agent.add_object(_obj)

    ##############



    logging.info( "Create SchemaEventClass" )

    _event = SchemaEventClass(SchemaClassId("MyPackage", "MyEvent",
                                            stype=SchemaClassId.TYPE_EVENT),
                              _desc="A test data schema",
                              _props={"edata_1": SchemaProperty(qmfTypes.TYPE_UINT32)})
    _event.add_property("edata_2", SchemaProperty(qmfTypes.TYPE_LSTR)) 

    print("Event Map='%s'" % str(_event.map_encode()))

    _agent.register_event_class(_event)
