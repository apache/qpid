
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
from threading import Thread, Lock
from qpid.messaging import Connection, Message, Empty, SendError
from uuid import uuid4
from qmfCommon import (AMQP_QMF_AGENT_LOCATE, AMQP_QMF_AGENT_INDICATION,
                       makeSubject, parseSubject, OpCode, QmfQuery,
                       SchemaObjectClass, MsgKey, QmfData, QmfAddress,
                       SchemaClass) 


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
        self._notifier = _notifier
        self._heartbeat_interval = _heartbeat_interval
        self._max_msg_size = _max_msg_size
        self._capacity = _capacity

        self._conn = None
        self._session = None
        self._lock = Lock()
        self._packages = {}
        self._schema_timestamp = long(0)
        self._schema = {}
        self._agent_data = {}

    def get_name(self):
        return self.name

    def setConnection(self, conn):
        my_addr = QmfAddress.direct(self.name, self._domain)
        locate_addr = QmfAddress.topic(AMQP_QMF_AGENT_LOCATE, self._domain)
        ind_addr = QmfAddress.topic(AMQP_QMF_AGENT_INDICATION, self._domain)

        logging.debug("my direct addr=%s" % my_addr)
        logging.debug("agent.locate addr=%s" % locate_addr)
        logging.debug("agent.ind addr=%s" % ind_addr)

        self._conn = conn
        self._session = self._conn.session()
        self._direct_receiver = self._session.receiver(str(my_addr) +
                                                       ";{create:always,"
                                                       " node-properties:"
                                                       " {type:topic, x-properties: {type:direct}}}", 
                                                       capacity=self._capacity)
        self._locate_receiver = self._session.receiver(str(locate_addr) + 
                                                       ";{create:always, node-properties:{type:topic}}",
                                                       capacity=self._capacity)
        self._ind_sender = self._session.sender(str(ind_addr) +
                                                ";{create:always, node-properties:{type:topic}}")

        self._running = True
        self.start()
    
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

    def raiseEvent(self, qmfEvent):
        logging.error("!!!Agent.raiseEvent() TBD!!!")

    def add_object(self, data ):
        """
        Register an instance of a QmfAgentData object.
        """
        # @todo: need to update subscriptions
        # @todo: need to mark schema as "non-const"
        if not isinstance(data, QmfAgentData):
            raise TypeError("QmfAgentData instance expected")

        id_ = data.get_object_id()
        if not id_:
            raise TypeError("No identifier assigned to QmfAgentData!")

        self._lock.acquire()
        try:
            self._agent_data[id_] = data
        finally:
            self._lock.release()


    def methodResponse(self, context, status, text, arguments):
        logging.error("!!!Agent.methodResponse() TBD!!!")

    def getWorkItemCount(self): 
        """ 
        Returns the count of pending WorkItems that can be retrieved.
        """
        logging.error("!!!Agent.getWorkItemCount() TBD!!!")

    def getNextWorkItem(self, timeout=None): 
        """
        Obtains the next pending work item, or None if none available. 
        """
        logging.error("!!!Agent.getNextWorkItem() TBD!!!")

    def releaseWorkItem(self, wi): 
        """
        Releases a WorkItem instance obtained by getNextWorkItem(). Called when 
        the application has finished processing the WorkItem. 
        """
        logging.error("!!!Agent.releaseWorkItem() TBD!!!")


    def run(self):
        next_heartbeat = datetime.datetime.utcnow()
        while self._running:

            now = datetime.datetime.utcnow()
            # print("now=%s next_heartbeat=%s" % (now, next_heartbeat))
            if  now >= next_heartbeat:
                self._ind_sender.send(self._makeAgentIndMsg())
                logging.debug("Agent Indication Sent")
                next_heartbeat = now + datetime.timedelta(seconds = self._heartbeat_interval)

            timeout = ((next_heartbeat - now) + datetime.timedelta(microseconds=999999)).seconds 
            # print("now=%s next_heartbeat=%s timeout=%s" % (now, next_heartbeat, timeout))
            try:
                self._session.next_receiver(timeout=timeout)
            except Empty:
                continue

            try:
                msg = self._locate_receiver.fetch(timeout = 0)
            except Empty:
                msg = None
            if msg and msg.content_type == "amqp/map":
                self._dispatch(msg, _direct=False)

            try:
                msg = self._direct_receiver.fetch(timeout = 0)
            except Empty:
                msg = None
            if msg and msg.content_type == "amqp/map":
                self._dispatch(msg, _direct=True)

    #
    # Private:
    #

    def _makeAgentIndMsg(self):
        """
        Create an agent indication message identifying this agent
        """
        _map = {"_name": self.get_name(),
                "_schema_timestamp": self._schema_timestamp}
        return Message( subject=makeSubject(OpCode.agent_ind),
                        properties={"method":"response"},
                        content={MsgKey.agent_info: _map})


    def _dispatch(self, msg, _direct=False):
        """
        Process a message from a console.

        @param _direct: True if msg directly addressed to this agent.
        """
        logging.error( "Message received from Console! [%s]" % msg )
        try:
            version,opcode = parseSubject(msg.subject)
        except:
            logging.debug("Ignoring unrecognized message '%s'" % msg.subject)
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
            logging.warning("!!! METHOD_REQ TBD !!!")
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
                tmpData = QmfData(_values={"_name": self.get_name()})
                reply = QmfQuery(query).evaluate(tmpData)

        if reply:
            try:
                tmp_snd = self._session.sender( msg.reply_to )
                m = self._makeAgentIndMsg()
                m.correlation_id = msg.correlation_id
                tmp_snd.send(m)
                logging.debug("agent-ind sent to [%s]" % msg.reply_to)
            except SendError, e:
                logging.error("Failed to send reply to agent-ind msg '%s' (%s)" % (msg, str(e)))
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
                query = QmfQuery(qmap)
                target = query.getTarget()
                if target == QmfQuery._TARGET_PACKAGES:
                    self._queryPackages( msg, query )
                elif target == QmfQuery._TARGET_SCHEMA_ID:
                    self._querySchema( msg, query, _idOnly=True )
                elif target == QmfQuery._TARGET_SCHEMA:
                    self._querySchema( msg, query)
                elif target == QmfQuery._TARGET_AGENT:
                    logging.warning("!!! Query TARGET=AGENT TBD !!!")
                elif target == QmfQuery._TARGET_OBJECT_ID:
                    logging.warning("!!! Query TARGET=OBJECT_ID TBD !!!")
                elif target == QmfQuery._TARGET_OBJECT:
                    logging.warning("!!! Query TARGET=OBJECT TBD !!!")
                else:
                    logging.warning("Unrecognized query target: '%s'" % str(target))


    def _queryPackages(self, msg, query):
        """
        Run a query against the list of known packages
        """
        pnames = []
        self._lock.acquire()
        try:
            for name in self._packages.iterkeys():
                if query.evaluate(QmfData.from_map({QmfQuery._PRED_PACKAGE:name})):
                    pnames.append(name)
        finally:
            self._lock.release()

        try:
            tmp_snd = self._session.sender( msg.reply_to )
            m = Message( subject=makeSubject(OpCode.data_ind),
                         properties={"method":"response"},
                         content={MsgKey.package_info: pnames} )
            if msg.correlation_id != None:
                m.correlation_id = msg.correlation_id
            tmp_snd.send(m)
            logging.debug("package_info sent to [%s]" % msg.reply_to)
        except SendError, e:
            logging.error("Failed to send reply to query msg '%s' (%s)" % (msg, str(e)))


    def _querySchema( self, msg, query, _idOnly=False ):
        """
        """
        schemas = []
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

        try:
            tmp_snd = self._session.sender( msg.reply_to )

            if _idOnly:
                content = {MsgKey.schema_id: schemas}
            else:
                content = {MsgKey.schema:schemas}

            m = Message( subject=makeSubject(OpCode.data_ind),
                         properties={"method":"response"},
                         content=content )
            if msg.correlation_id != None:
                m.correlation_id = msg.correlation_id
            tmp_snd.send(m)
            logging.debug("schema_id sent to [%s]" % msg.reply_to)
        except SendError, e:
            logging.error("Failed to send reply to query msg '%s' (%s)" % (msg, str(e)))




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

    def inc_value(self, name, delta):
        """ add the delta to the property """
        # @todo: need to take write-lock
        logging.error(" TBD!!!")

    def dec_value(self, name, delta): 
        """ subtract the delta from the property """
        # @todo: need to take write-lock
        logging.error(" TBD!!!")


################################################################################
################################################################################
################################################################################
################################################################################

if __name__ == '__main__':
    # static test cases - no message passing, just exercise API
    from qmfCommon import (AgentName, SchemaClassId, SchemaProperty, qmfTypes,
                           SchemaMethod, SchemaEventClass)

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
    _meth.addArgument( "arg_int", SchemaProperty(qmfTypes.TYPE_UINT32) )
    _meth.addArgument( "arg_str", SchemaProperty(qmfTypes.TYPE_LSTR) )
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
