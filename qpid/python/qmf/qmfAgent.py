
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
import socket
import os
import logging
from threading import Thread, Lock
from qpid.messaging import Connection, Message
from uuid import uuid4
from qmfCommon import (AMQP_QMF_TOPIC, AMQP_QMF_DIRECT, AMQP_QMF_AGENT_LOCATE, 
                       AMQP_QMF_AGENT_INDICATION, AgentId, QmfManaged, makeSubject,
                       parseSubject, OpCode, Query, SchemaObjectClass, _doQuery)



  ##==============================================================================
  ## AGENT
  ##==============================================================================

class Agent(Thread):
    def __init__(self, vendor, product, name=None,
                 notifier=None, kwargs={}):
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
        self._conn = None
        self._lock = Lock()
        self._data_schema = {}
        self._event_schema = {}
        self._agent_data = {}

    def getAgentId(self):
        return AgentId(self.vendor, self.product, self.name)

    def setConnection(self, conn):
        self._conn = conn
        self._session = self._conn.session()
        self._locate_receiver = self._session.receiver(AMQP_QMF_AGENT_LOCATE)
        self._direct_receiver = self._session.receiver(AMQP_QMF_DIRECT + "/" + self._address)
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
            self._data_schema[schema.getClassId()] = schema
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
        count = 0   # @todo: hack
        while self._running:
            try:
                msg = self._locate_receiver.fetch(1)
                logging.debug("Agent Locate Rcvd: '%s'" % msg)
                if msg.content_type == "amqp/map":
                    self._dispatch(msg, _direct=False)
            except KeyboardInterrupt:
                break
            except:
                pass

            try:
                msg = self._direct_receiver.fetch(1)
                logging.debug("Agent Msg Rcvd: '%s'" % msg)
                if msg.content_type == "amqp/map":
                    self._dispatch(msg, _direct=True)
            except KeyboardInterrupt:
                break
            except:
                pass

            # @todo: actually implement the periodic agent-ind
            # message generation!
            count+= 1
            if count == 5:
                count = 0
                self._ind_sender.send(self._makeAgentIndMsg())
                logging.debug("Agent Indication Sent")

    #
    # Private:
    #

    def _makeAgentIndMsg(self):
        """
        Create an agent indication message identifying this agent
        """
        return Message( subject=makeSubject(OpCode.agent_ind),
                        properties={"method":"response"},
                        content={Query._TARGET_AGENT_ID: 
                                 self.getAgentId().mapEncode()})



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
            logging.warning("!!! GET_QUERY TBD !!!")
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
            if "query" in cmap:
                query = cmap["query"]
                # is the query an agent locate?
                if Query._TARGET in query and query[Query._TARGET] == {Query._TARGET_AGENT_ID:None}:
                    if Query._PREDICATE in query:
                        # does this agent match the predicate?
                        reply = _doQuery( query[Query._PREDICATE], self.getAgentId().mapEncode() )
                else:
                    reply = False
                    logging.debug("Ignoring query - not an agent-id query: '%s'" % query)
                reply=True

        if reply:
            try:
                tmp_snd = self._session.sender( msg.reply_to )
                m = self._makeAgentIndMsg()
                m.correlation_id = msg.correlation_id
                tmp_snd.send(m)
                logging.debug("agent-ind sent to [%s]" % msg.reply_to)
            except:
                logging.error("Failed to send reply to agent-ind msg '%s'" % msg)
        else:
            logging.debug("agent-locate msg not mine - no reply sent")



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
    
