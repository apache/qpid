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
import logging
import datetime
import time
import Queue
from threading import Thread, Lock, currentThread
from qpid.messaging import Connection, Message, Empty, SendError
from uuid import uuid4
from common import (make_subject, parse_subject, OpCode, QmfQuery,
                    SchemaObjectClass, MsgKey, QmfData, QmfAddress,
                    SchemaClass, SchemaClassId, WorkItem, SchemaMethod,
                    timedelta_to_secs)

# global flag that indicates which thread (if any) is
# running the agent notifier callback
_callback_thread=None

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
  ## AGENT
  ##==============================================================================

class Agent(Thread):
    def __init__(self, name, _domain=None, _notifier=None, _heartbeat_interval=30, 
                 _max_msg_size=0, _capacity=10):
        Thread.__init__(self)
        self._running = False

        self.name = str(name)
        self._domain = _domain
        self._address = QmfAddress.direct(self.name, self._domain)
        self._notifier = _notifier
        self._heartbeat_interval = _heartbeat_interval
        self._max_msg_size = _max_msg_size
        self._capacity = _capacity

        self._conn = None
        self._session = None
        self._direct_receiver = None
        self._topic_receiver = None
        self._direct_sender = None
        self._topic_sender = None

        self._lock = Lock()
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


    def destroy(self, timeout=None):
        """
        Must be called before the Agent is deleted.  
        Frees up all resources and shuts down all background threads.

        @type timeout: float
        @param timeout: maximum time in seconds to wait for all background threads to terminate.  Default: forever.
        """
        logging.debug("Destroying Agent %s" % self.name)
        if self._conn:
            self.remove_connection(timeout)
        logging.debug("Agent Destroyed")


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
        logging.debug("my direct addr=%s" % self._direct_receiver.source)

        # for sending directly addressed messages.
        self._direct_sender = self._session.sender(str(self._address.get_node()) +
                                                   ";{create:always,"
                                                   " node-properties:"
                                                   " {type:topic,"
                                                   " x-properties:"
                                                   " {type:direct}}}")
        logging.debug("my default direct send addr=%s" % self._direct_sender.target)

        # for receiving "broadcast" messages from consoles
        default_addr = QmfAddress.topic(QmfAddress.SUBJECT_CONSOLE_IND + ".#",
                                        self._domain)
        self._topic_receiver = self._session.receiver(str(default_addr) +
                                                       ";{create:always,"
                                                       " node-properties:"
                                                       " {type:topic}}",
                                                       capacity=self._capacity)
        logging.debug("console.ind addr=%s" % self._topic_receiver.source)

        # for sending to topic subscribers
        ind_addr = QmfAddress.topic(QmfAddress.SUBJECT_AGENT_IND,
                                    self._domain)
        self._topic_sender = self._session.sender(str(ind_addr) +
                                                ";{create:always,"
                                                " node-properties:"
                                                " {type:topic}}")
        logging.debug("agent.ind addr=%s" % self._topic_sender.target)

        self._running = True
        self.start()

    def remove_connection(self, timeout=None):
        # tell connection thread to shutdown
        self._running = False
        if self.isAlive():
            # kick my thread to wake it up
            try:
                msg = Message(properties={"method":"request",
                                          "qmf.subject":make_subject(OpCode.noop)},
                              subject=self.name,
                              content={"noop":"noop"})

                # TRACE
                #logging.error("!!! sending wakeup to myself: %s" % msg)
                self._direct_sender.send( msg, sync=True )
            except SendError, e:
                logging.error(str(e))
            logging.debug("waiting for agent receiver thread to exit")
            self.join(timeout)
            if self.isAlive():
                logging.error( "Agent thread '%s' is hung..." % self.name)
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
        logging.debug("agent connection removal complete")

    def register_object_class(self, schema):
        """
        Register an instance of a SchemaClass with this agent
        """
        # @todo: need to update subscriptions
        # @todo: need to mark schema as "non-const"
        if not isinstance(schema, SchemaClass):
            raise TypeError("SchemaClass instance expected")

        self._lock.acquire()
        try:
            classId = schema.get_class_id()
            pname = classId.get_package_name()
            cname = classId.get_class_name()
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
        _map = {"_name": self.get_name(),
                "_event": qmfEvent.map_encode()}
        msg = Message(subject=QmfAddress.SUBJECT_AGENT_EVENT + "." +
                      qmfEvent.get_severity() + "." + self.name,
                      properties={"method":"response",
                                  "qmf.subject":make_subject(OpCode.event_ind)},
                      content={MsgKey.event:_map})
        # TRACE
        # logging.error("!!! Agent %s sending Event (%s)" % 
        # (self.name, str(msg)))
        self._topic_sender.send(msg)

    def add_object(self, data):
        """
        Register an instance of a QmfAgentData object.
        """
        # @todo: need to update subscriptions
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

        msg = Message( properties={"method":"response",
                                   "qmf.subject":make_subject(OpCode.response)},
                       content={MsgKey.method:_map})
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
        while self._running:

            now = datetime.datetime.utcnow()
            # print("now=%s next_heartbeat=%s" % (now, next_heartbeat))
            if  now >= next_heartbeat:
                ind = self._makeAgentIndMsg()
                ind.subject = QmfAddress.SUBJECT_AGENT_HEARTBEAT
                # TRACE
                #logging.error("!!! Agent %s sending Heartbeat (%s)" % 
                # (self.name, str(ind)))
                self._topic_sender.send(ind)
                logging.debug("Agent Indication Sent")
                next_heartbeat = now + datetime.timedelta(seconds = self._heartbeat_interval)

            timeout = timedelta_to_secs(next_heartbeat - now)
            # print("now=%s next_heartbeat=%s timeout=%s" % (now, next_heartbeat, timeout))
            try:
                self._session.next_receiver(timeout=timeout)
            except Empty:
                continue

            for i in range(batch_limit):
                try:
                    msg = self._topic_receiver.fetch(timeout=0)
                except Empty:
                    break
                # TRACE
                # logging.error("!!! Agent %s: msg on %s [%s]" %
                # (self.name, self._topic_receiver.source, msg))
                self._dispatch(msg, _direct=False)

            for i in range(batch_limit):
                try:
                    msg = self._direct_receiver.fetch(timeout=0)
                except Empty:
                    break
                # TRACE
                # logging.error("!!! Agent %s: msg on %s [%s]" %
                # (self.name, self._direct_receiver.source, msg))
                self._dispatch(msg, _direct=True)

            if self._work_q_put and self._notifier:
                # new stuff on work queue, kick the the application...
                self._work_q_put = False
                _callback_thread = currentThread()
                logging.info("Calling agent notifier.indication")
                self._notifier.indication()
                _callback_thread = None

    #
    # Private:
    #

    def _makeAgentIndMsg(self):
        """
        Create an agent indication message identifying this agent
        """
        _map = {"_name": self.get_name(),
                "_schema_timestamp": self._schema_timestamp}
        return Message(properties={"method":"response",
                                   "qmf.subject":make_subject(OpCode.agent_ind)},
                       content={MsgKey.agent_info: _map})

    def _send_reply(self, msg, reply_to):
        """
        Send a reply message to the given reply_to address
        """
        if not isinstance(reply_to, QmfAddress):
            try:
                reply_to = QmfAddress.from_string(str(reply_to))
            except ValueError:
                logging.error("Invalid reply-to address '%s'" % reply_to)

        msg.subject = reply_to.get_subject()

        try:
            if reply_to.is_direct():
                # TRACE
                #logging.error("!!! Agent %s direct REPLY-To:%s (%s)" % 
                # (self.name, str(reply_to), str(msg)))
                self._direct_sender.send(msg)
            else:
                # TRACE
                # logging.error("!!! Agent %s topic REPLY-To:%s (%s)" % 
                # (self.name, str(reply_to), str(msg)))
                self._topic_sender.send(msg)
            logging.debug("reply msg sent to [%s]" % str(reply_to))
        except SendError, e:
            logging.error("Failed to send reply msg '%s' (%s)" % (msg, str(e)))


    def _dispatch(self, msg, _direct=False):
        """
        Process a message from a console.

        @param _direct: True if msg directly addressed to this agent.
        """
        logging.debug( "Message received from Console! [%s]" % msg )
        try:
            version,opcode = parse_subject(msg.properties.get("qmf.subject"))
        except:
            logging.warning("Ignoring unrecognized message '%s'" % msg.subject)
            return

        cmap = {}; props={}
        if msg.content_type == "amqp/map":
            cmap = msg.content
        if msg.properties:
            props = msg.properties

        if opcode == OpCode.agent_locate:
            self._handleAgentLocateMsg( msg, cmap, props, version, _direct )
        elif opcode == OpCode.get_query:
            self._handleQueryMsg( msg, cmap, props, version, _direct )
        elif opcode == OpCode.method_req:
            self._handleMethodReqMsg(msg, cmap, props, version, _direct)
        elif opcode == OpCode.cancel_subscription:
            logging.warning("!!! CANCEL_SUB TBD !!!")
        elif opcode == OpCode.create_subscription:
            logging.warning("!!! CREATE_SUB TBD !!!")
        elif opcode == OpCode.renew_subscription:
            logging.warning("!!! RENEW_SUB TBD !!!")
        elif opcode == OpCode.schema_query:
            logging.warning("!!! SCHEMA_QUERY TBD !!!")
        elif opcode == OpCode.noop:
            logging.debug("No-op msg received.")
        else:
            logging.warning("Ignoring message with unrecognized 'opcode' value: '%s'"
                            % opcode)

    def _handleAgentLocateMsg( self, msg, cmap, props, version, direct ):
        """
        Process a received agent-locate message
        """
        logging.debug("_handleAgentLocateMsg")

        reply = True
        if "method" in props and props["method"] == "request":
            query = cmap.get(MsgKey.query)
            if query is not None:
                # fake a QmfData containing my identifier for the query compare
                tmpData = QmfData(_values={QmfQuery.KEY_AGENT_NAME: self.get_name()})
                reply = QmfQuery.from_map(query).evaluate(tmpData)

        if reply:
            m = self._makeAgentIndMsg()
            m.correlation_id = msg.correlation_id
            self._send_reply(m, msg.reply_to)
        else:
            logging.debug("agent-locate msg not mine - no reply sent")


    def _handleQueryMsg(self, msg, cmap, props, version, _direct ):
        """
        Handle received query message
        """
        logging.debug("_handleQueryMsg")

        if "method" in props and props["method"] == "request":
            qmap = cmap.get(MsgKey.query)
            if qmap:
                query = QmfQuery.from_map(qmap)
                target = query.get_target()
                if target == QmfQuery.TARGET_PACKAGES:
                    self._queryPackages( msg, query )
                elif target == QmfQuery.TARGET_SCHEMA_ID:
                    self._querySchema( msg, query, _idOnly=True )
                elif target == QmfQuery.TARGET_SCHEMA:
                    self._querySchema( msg, query)
                elif target == QmfQuery.TARGET_AGENT:
                    logging.warning("!!! @todo: Query TARGET=AGENT TBD !!!")
                elif target == QmfQuery.TARGET_OBJECT_ID:
                    self._queryData(msg, query, _idOnly=True)
                elif target == QmfQuery.TARGET_OBJECT:
                    self._queryData(msg, query)
                else:
                    logging.warning("Unrecognized query target: '%s'" % str(target))



    def _handleMethodReqMsg(self, msg, cmap, props, version, _direct):
        """
        Process received Method Request
        """
        if "method" in props and props["method"] == "request":
            mname = cmap.get(SchemaMethod.KEY_NAME)
            if not mname:
                logging.warning("Invalid method call from '%s': no name"
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
            param = MethodCallParams( mname, oid, schema_id, in_args, msg.user_id)
            self._work_q.put(WorkItem(WorkItem.METHOD_CALL, handle, param))
            self._work_q_put = True

    def _queryPackages(self, msg, query):
        """
        Run a query against the list of known packages
        """
        pnames = []
        self._lock.acquire()
        try:
            for name in self._packages.iterkeys():
                if query.evaluate(QmfData.create({SchemaClassId.KEY_PACKAGE:name})):
                    pnames.append(name)
        finally:
            self._lock.release()

        m = Message(properties={"qmf.subject":make_subject(OpCode.data_ind),
                                "method":"response"},
                    content={MsgKey.package_info: pnames} )
        if msg.correlation_id != None:
            m.correlation_id = msg.correlation_id
        self._send_reply(m, msg.reply_to)

    def _querySchema( self, msg, query, _idOnly=False ):
        """
        """
        schemas = []
        # if querying for a specific schema, do a direct lookup
        if query.get_selector() == QmfQuery.ID:
            found = None
            self._lock.acquire()
            try:
                found = self._schema.get(query.get_id())
            finally:
                self._lock.release()
            if found:
                if _idOnly:
                    schemas.append(query.get_id().map_encode())
                else:
                    schemas.append(found.map_encode())
        else: # otherwise, evaluate all schema
            self._lock.acquire()
            try:
                for sid,val in self._schema.iteritems():
                    if query.evaluate(val):
                        if _idOnly:
                            schemas.append(sid.map_encode())
                        else:
                            schemas.append(val.map_encode())
            finally:
                self._lock.release()

        if _idOnly:
            content = {MsgKey.schema_id: schemas}
        else:
            content = {MsgKey.schema:schemas}

        m = Message(properties={"method":"response",
                                "qmf.subject":make_subject(OpCode.data_ind)},
                    content=content )
        if msg.correlation_id != None:
            m.correlation_id = msg.correlation_id

        self._send_reply(m, msg.reply_to)


    def _queryData( self, msg, query, _idOnly=False ):
        """
        """
        data_objs = []
        # extract optional schema_id from target params
        sid = None
        t_params = query.get_target_param()
        if t_params:
            sid = t_params.get(QmfData.KEY_SCHEMA_ID)

        # if querying for a specific object, do a direct lookup
        if query.get_selector() == QmfQuery.ID:
            oid = query.get_id()
            found = None
            self._lock.acquire()
            try:
                if sid:
                    found = self._described_data.get(sid)
                    if found:
                        found = found.get(oid)
                else:
                    found = self._undescribed_data.get(oid)
            finally:
                self._lock.release()
            if found:
                if _idOnly:
                    data_objs.append(query.get_id())
                else:
                    data_objs.append(found.map_encode())
        else: # otherwise, evaluate all data
            self._lock.acquire()
            try:
                if sid:
                    db = self._described_data.get(sid)
                else:
                    db = self._undescribed_data

                if db:
                    for oid,val in db.iteritems():
                        if query.evaluate(val):
                            if _idOnly:
                                data_objs.append(oid)
                            else:
                                data_objs.append(val.map_encode())
            finally:
                self._lock.release()

        if _idOnly:
            content = {MsgKey.object_id:data_objs}
        else:
            content = {MsgKey.data_obj:data_objs}

        m = Message(properties={"method":"response",
                                "qmf.subject":make_subject(OpCode.data_ind)},
                    content=content )
        if msg.correlation_id != None:
            m.correlation_id = msg.correlation_id

        self._send_reply(m, msg.reply_to)


  ##==============================================================================
  ## DATA MODEL
  ##==============================================================================


class QmfAgentData(QmfData):
    """
    A managed data object that is owned by an agent.
    """

    def __init__(self, agent, _values={}, _subtypes={}, _tag=None, _object_id=None,
                 _schema=None):
        # timestamp in millisec since epoch UTC
        ctime = long(time.time() * 1000)
        super(QmfAgentData, self).__init__(_values=_values, _subtypes=_subtypes,
                                           _tag=_tag, _ctime=ctime,
                                           _utime=ctime, _object_id=_object_id,
                                           _schema=_schema, _const=False)
        self._agent = agent

    def destroy(self): 
        self._dtime = long(time.time() * 1000)
        # @todo: publish change

    def is_deleted(self): 
        return self._dtime == 0

    def set_value(self, _name, _value, _subType=None):
        super(QmfAgentData, self).set_value(_name, _value, _subType)
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
        logging.error(" TBD!!!")


################################################################################
################################################################################
################################################################################
################################################################################

if __name__ == '__main__':
    # static test cases - no message passing, just exercise API
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
