
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
from qmfCommon import (AMQP_QMF_TOPIC, AMQP_QMF_DIRECT, AMQP_QMF_AGENT_LOCATE, 
                       AMQP_QMF_AGENT_INDICATION, AgentId, QmfManaged, makeSubject,
                       parseSubject, OpCode, QmfQuery, SchemaObjectClass, MsgKey, 
                       QmfData)



  ##==============================================================================
  ## AGENT
  ##==============================================================================

class Agent(Thread):
    def __init__(self, vendor, product, name=None,
                 notifier=None, heartbeat_interval=30,
                 kwargs={}):
        Thread.__init__(self)
        self._running = False
        self.vendor = vendor
        self.product = product
        if name:
            self.name = name
        else:
            self.name = uuid4().get_urn().split(":")[2]
        self._id = AgentId(self.vendor, self.product, self.name)
        self._address = str(self._id)
        self._notifier = notifier
        self._heartbeat_interval = heartbeat_interval
        self._conn = None
        self._session = None
        self._lock = Lock()
        self._packages = {}
        self._schema_timestamp = long(0)
        self._schema = {}
        self._agent_data = {}

    def getAgentId(self):
        return AgentId(self.vendor, self.product, self.name)

    def setConnection(self, conn):
        self._conn = conn
        self._session = self._conn.session()
        self._locate_receiver = self._session.receiver(AMQP_QMF_AGENT_LOCATE, capacity=10)
        self._direct_receiver = self._session.receiver(AMQP_QMF_DIRECT + "/" + self._address,
                                                       capacity=10)
        self._ind_sender = self._session.sender(AMQP_QMF_AGENT_INDICATION)
        self._running = True
        self.start()
    
    def registerObjectClass(self, schema):
        """
        Register an instance of a SchemaObjectClass with this agent
        """
        # @todo: need to update subscriptions
        # @todo: need to mark schema as "non-const"
        if not isinstance(schema, SchemaObjectClass):
            raise TypeError("SchemaObjectClass instance expected")

        self._lock.acquire()
        try:
            classId = schema.getClassId()
            pname = classId.getPackageName()
            cname = classId.getClassName()
            if pname not in self._packages:
                self._packages[pname] = [cname]
            else:
                if cname not in self._packages[pname]:
                    self._packages[pname].append(cname)
            self._schema[classId] = schema
            self._schema_timestamp = long(time.time() * 1000)
        finally:
            self._lock.release()


    def registerEventClass(self, cls):
        logging.error("!!!Agent.registerEventClass() TBD!!!")

    def raiseEvent(self, qmfEvent):
        logging.error("!!!Agent.raiseEvent() TBD!!!")

    def addObject(self, data ):
        """
        Register an instance of a QmfAgentData object.
        """
        # @todo: need to update subscriptions
        # @todo: need to mark schema as "non-const"
        if not isinstance(data, QmfAgentData):
            raise TypeError("QmfAgentData instance expected")

        self._lock.acquire()
        try:
            self._agent_data[data.getObjectId()] = data
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
            print("now=%s next_heartbeat=%s" % (now, next_heartbeat))
            if  now >= next_heartbeat:
                self._ind_sender.send(self._makeAgentIndMsg())
                logging.debug("Agent Indication Sent")
                next_heartbeat = now + datetime.timedelta(seconds = self._heartbeat_interval)

            timeout = ((next_heartbeat - now) + datetime.timedelta(microseconds=999999)).seconds 
            print("now=%s next_heartbeat=%s timeout=%s" % (now, next_heartbeat, timeout))
            try:
                logging.error("waiting for next rcvr (timeout=%s)..." % timeout)
                self._session.next_receiver(timeout = timeout)
            except Empty:
                pass
            except KeyboardInterrupt:
                break

            try:
                msg = self._locate_receiver.fetch(timeout = 0)
                if msg.content_type == "amqp/map":
                    self._dispatch(msg, _direct=False)
            except Empty:
                pass

            try:
                msg = self._direct_receiver.fetch(timeout = 0)
                if msg.content_type == "amqp/map":
                    self._dispatch(msg, _direct=True)
            except Empty:
                pass



    #
    # Private:
    #

    def _makeAgentIndMsg(self):
        """
        Create an agent indication message identifying this agent
        """
        _map = self.getAgentId().mapEncode()
        _map["schemaTimestamp"] = self._schema_timestamp
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
            if MsgKey.query in cmap:
                agentIdMap = self.getAgentId().mapEncode()
                reply = QmfQuery(cmap[MsgKey.query]).evaluate(QmfData(agentIdMap))

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
                    self._querySchemaId( msg, query )
                elif target == QmfQuery._TARGET_SCHEMA:
                    logging.warning("!!! Query TARGET=SCHEMA TBD !!!")
                    #self._querySchema( query.getPredicate(), _idOnly=False )
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
                if query.evaluate(QmfData(_props={QmfQuery._PRED_PACKAGE:name})):
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


    def _querySchemaId( self, msg, query ):
        """
        """
        schemas = []
        self._lock.acquire()
        try:
            for schemaId in self._schema.iterkeys():
                if query.evaluate(QmfData(_props={QmfQuery._PRED_PACKAGE:schemaId.getPackageName()})):
                    schemas.append(schemaId.mapEncode())
        finally:
            self._lock.release()

        try:
            tmp_snd = self._session.sender( msg.reply_to )
            m = Message( subject=makeSubject(OpCode.data_ind),
                         properties={"method":"response"},
                         content={MsgKey.schema_id: schemas} )
            if msg.correlation_id != None:
                m.correlation_id = msg.correlation_id
            tmp_snd.send(m)
            logging.debug("schema_id sent to [%s]" % msg.reply_to)
        except SendError, e:
            logging.error("Failed to send reply to query msg '%s' (%s)" % (msg, str(e)))




  ##==============================================================================
  ## OBJECTS
  ##==============================================================================


class QmfAgentData(QmfManaged):
    """
    A managed data object that is owned by an agent.
    """
    def __init__(self, _agent, _schema, _props={}):
        """
        @type _agent: class Agent
        @param _agent: the agent that manages this object.
        @type _schema: class SchemaObjectClass
        @param _schema: the schema used to describe this data object
        @type _props: map of "name"=<value> pairs
        @param _props: initial values for all properties in this object
        """
        super(QmfAgentData, self).__init__(_agentId=_agent.getAgentId(), 
                                           _schema=_schema, 
                                           _props=_props)

    def destroy(self): 
        self._timestamps[QmfManaged._ts_delete] = long(time.time() * 1000)
        # @todo: publish change

    def setProperty( self, _name, _value):
        super(QmfAgentData, self).setProperty(_name, _value)
        # @todo: publish change



################################################################################
################################################################################
################################################################################
################################################################################

if __name__ == '__main__':
    import time
    logging.getLogger().setLevel(logging.INFO)
    logging.info( "Starting Connection" )
    _c = Connection("localhost")
    _c.connect()
    #c.start()

    logging.info( "Starting Agent" )
    _agent = Agent("redhat.com", "agent", "tross")
    _agent.setConnection(_c)

    logging.info( "Running Agent" )
    
    while True:
        try:
            time.sleep(10)
        except KeyboardInterrupt:
            break
    
