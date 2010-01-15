#
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
import os
import logging
import platform
import time
import datetime
import Queue
from threading import Thread
from threading import Lock
from threading import currentThread
from threading import Condition

from qpid.messaging import Connection, Message, Empty, SendError

from qmfCommon import (makeSubject, parseSubject, OpCode, QmfQuery, Notifier,
                       QmfQueryPredicate, MsgKey, QmfData, QmfAddress,
                       AMQP_QMF_AGENT_LOCATE, AMQP_QMF_AGENT_INDICATION,
                       SchemaClass, SchemaClassId, SchemaEventClass,
                       SchemaObjectClass, WorkItem, SchemaMethod)



# global flag that indicates which thread (if any) is
# running the console notifier callback
_callback_thread=None




##==============================================================================
## Sequence Manager  
##==============================================================================

class _Mailbox(object):
    """
    Virtual base class for all Mailbox-like objects
    """
    def __init__(self):
        self._msgs = []
        self._cv = Condition()
        self._waiting = False

    def deliver(self, obj):
        self._cv.acquire()
        try:
            self._msgs.append(obj)
            # if was empty, notify waiters
            if len(self._msgs) == 1:
                logging.error("Delivering @ %s" % time.time())
                self._cv.notify()
        finally:
            self._cv.release()

    def fetch(self, timeout=None):
        self._cv.acquire()
        try:
            if len(self._msgs) == 0:
                self._cv.wait(timeout)
            if len(self._msgs):
                return self._msgs.pop()
            return None
        finally:
            self._cv.release()



class SequencedWaiter(object):
    """ 
    Manage sequence numbers for asynchronous method calls. 
    Allows the caller to associate a generic piece of data with a unique sequence
    number."""

    def __init__(self):
        self.lock     = Lock()
        self.sequence = 1L
        self.pending  = {}


    def allocate(self):
        """ 
        Reserve a sequence number.
        
        @rtype: long
        @return: a unique nonzero sequence number.
        """
        self.lock.acquire()
        try:
            seq = self.sequence
            self.sequence = self.sequence + 1
            self.pending[seq] = _Mailbox()
        finally:
            self.lock.release()
        logging.debug( "sequence %d allocated" % seq)
        return seq


    def put_data(self, seq, new_data):
        seq = long(seq)
        logging.debug( "putting data [%r] to seq %d..." % (new_data, seq) )
        self.lock.acquire()
        try:
            if seq in self.pending:
                logging.error("Putting seq %d @ %s" % (seq,time.time()))
                self.pending[seq].deliver(new_data)
            else:
                logging.error( "seq %d not found!" % seq )
        finally:
            self.lock.release()



    def get_data(self, seq, timeout=None):
        """ 
        Release a sequence number reserved using the reserve method.  This must
        be called when the sequence is no longer needed.
        
        @type seq: int
        @param seq: a sequence previously allocated by calling reserve().
        @rtype: any
        @return: the data originally associated with the reserved sequence number.
        """
        seq = long(seq)
        logging.debug( "getting data for seq=%d" % seq)
        mbox = None
        self.lock.acquire()
        try:
            if seq in self.pending:
                mbox = self.pending[seq]
        finally:
            self.lock.release()

        # Note well: pending list is unlocked, so we can wait.
        # we reference mbox locally, so it will not be released
        # until we are done.

        if mbox:
            d = mbox.fetch(timeout)
            logging.debug( "seq %d fetched %r!" % (seq, d) )
            return d

        logging.debug( "seq %d not found!" % seq )
        return None


    def release(self, seq):
        """
        Release the sequence, and its mailbox
        """
        seq = long(seq)
        logging.debug( "releasing seq %d" % seq )
        self.lock.acquire()
        try:
            if seq in self.pending:
                del self.pending[seq]
        finally:
            self.lock.release()


    def isValid(self, seq):
        """
        True if seq is in use, else False (seq is unknown)
        """
        seq = long(seq)
        self.lock.acquire()
        try:
            return seq in self.pending
        finally:
            self.lock.release()
        return False


##==============================================================================
## DATA MODEL
##==============================================================================


class QmfConsoleData(QmfData):
    """
    Console's representation of an managed QmfData instance.  
    """
    def __init__(self, map_, agent, _schema=None):
        super(QmfConsoleData, self).__init__(_map=map_,
                                             _schema=_schema,
                                             _const=True) 
        self._agent = agent

    def get_timestamps(self): 
        """
        Returns a list of timestamps describing the lifecycle of
        the object.  All timestamps are represented by the AMQP
        timestamp type.  [0] = time of last update from Agent,
                         [1] = creation timestamp 
                         [2] = deletion timestamp, or zero if not
        deleted.
        """
        return [self._utime, self._ctime, self._dtime]

    def get_create_time(self): 
        """
        returns the creation timestamp
        """
        return self._ctime

    def get_update_time(self): 
        """
        returns the update timestamp
        """
        return self._utime

    def get_delete_time(self): 
        """
        returns the deletion timestamp, or zero if not yet deleted.
        """
        return self._dtime

    def is_deleted(self): 
        """
        True if deletion timestamp not zero.
        """
        return self._dtime != long(0)

    def refresh(self, _reply_handle=None, _timeout=None): 
        """
        request that the Agent update the value of this object's
        contents.
        """
        logging.error(" TBD!!!")
        return None

    def invoke_method(self, name, _in_args={}, _reply_handle=None,
                      _timeout=None):
        """
        invoke the named method.
        """
        assert self._agent
        assert self._agent._console

        oid = self.get_object_id()
        if oid is None:
            raise ValueError("Cannot invoke methods on unmanaged objects.")

        if _timeout is None:
            _timeout = self._agent._console._reply_timeout

        if _in_args:
            _in_args = _in_args.copy()

        if self._schema:
            # validate
            ms = self._schema.get_method(name)
            if ms is None:
                raise ValueError("Method '%s' is undefined." % ms)

            for aname,prop in ms.get_arguments().iteritems():
                if aname not in _in_args:
                    if prop.get_default():
                        _in_args[aname] = prop.get_default()
                    elif not prop.is_optional():
                        raise ValueError("Method '%s' requires argument '%s'"
                                         % (name, aname))
            for aname in _in_args.iterkeys():
                prop = ms.get_argument(aname)
                if prop is None:
                    raise ValueError("Method '%s' does not define argument"
                                     " '%s'" % (name, aname))
                if "I" not in prop.get_direction():
                    raise ValueError("Method '%s' argument '%s' is not an"
                                     " input." % (name, aname)) 

            # @todo check if value is correct (type, range, etc)

        handle = self._agent._console._req_correlation.allocate()
        if handle == 0:
            raise Exception("Can not allocate a correlation id!")

        _map = {self.KEY_OBJECT_ID:str(oid),
                SchemaMethod.KEY_NAME:name}
        if _in_args:
            _map[SchemaMethod.KEY_ARGUMENTS] = _in_args

        logging.debug("Sending method req to Agent (%s)" % time.time())
        try:
            self._agent._sendMethodReq(_map, handle)
        except SendError, e:
            logging.error(str(e))
            self._agent._console._req_correlation.release(handle)
            return None

        # @todo async method calls!!!
        if _reply_handle is not None:
            print("ASYNC TBD")

        logging.debug("Waiting for response to method req (%s)" % _timeout)
        replyMsg = self._agent._console._req_correlation.get_data(handle, _timeout)
        self._agent._console._req_correlation.release(handle)
        if not replyMsg:
            logging.debug("Agent method req wait timed-out.")
            return None

        _map = replyMsg.content.get(MsgKey.method)
        if not _map:
            logging.error("Invalid method call reply message")
            return None

        error=_map.get(SchemaMethod.KEY_ERROR)
        if error:
            return MethodResult(_error=QmfData.from_map(error))
        else:
            return MethodResult(_out_args=_map.get(SchemaMethod.KEY_ARGUMENTS))



class QmfLocalData(QmfData):
    """
    Console's representation of an unmanaged QmfData instance.  There
    is no remote agent associated with this instance. The Console has
    full control over this instance.
    """
    def __init__(self, values, _subtypes={}, _tag=None, _object_id=None,
                 _schema=None):
        # timestamp in millisec since epoch UTC
        ctime = long(time.time() * 1000)
        super(QmfLocalData, self).__init__(_values=values,
                                           _subtypes=_subtypes, _tag=_tag, 
                                           _object_id=_object_id,
                                           _schema=_schema, _ctime=ctime,
                                           _utime=ctime, _const=False)


class Agent(object):
    """
    A local representation of a remote agent managed by this console.
    """
    def __init__(self, name, console):
        """
        @type name: AgentId
        @param name: uniquely identifies this agent in the AMQP domain.
        """

        if not isinstance(console, Console):
            raise TypeError("parameter must be an instance of class Console")

        self._name = name
        self._address = QmfAddress.direct(name, console._domain)
        self._console = console
        self._sender = None
        self._packages = {} # map of {package-name:[list of class-names], } for this agent
        self._subscriptions = [] # list of active standing subscriptions for this agent
        self._announce_timestamp = None # datetime when last announce received
        logging.debug( "Created Agent with address: [%s]" % self._address )


    def get_name(self):
        return self._name

    def isActive(self):
        return self._announce_timestamp != None
    
    def _sendMsg(self, msg, correlation_id=None):
        """
        Low-level routine to asynchronously send a message to this agent.
        """
        msg.reply_to = str(self._console._address)
        # handle = self._console._req_correlation.allocate()
        # if handle == 0:
        #    raise Exception("Can not allocate a correlation id!")
        # msg.correlation_id = str(handle)
        if correlation_id:
            msg.correlation_id = str(correlation_id)
        self._sender.send(msg)
        # return handle

    def get_packages(self):
        """
        Return a list of the names of all packages known to this agent.
        """
        return self._packages.keys()

    def get_classes(self):
        """
        Return a dictionary [key:class] of classes known to this agent.
        """
        return self._packages.copy()

    def get_objects(self, query, kwargs={}):
        """
        Return a list of objects that satisfy the given query.

        @type query: dict, or qmfCommon.Query
        @param query: filter for requested objects
        @type kwargs: dict
        @param kwargs: ??? used to build match selector and query ???
        @rtype: list
        @return: list of matching objects, or None.
        """
        pass

    def get_object(self, query, kwargs={}):
        """
        Get one object - query is expected to match only one object.
        ??? Recommended: explicit timeout param, default None ???

        @type query: dict, or qmfCommon.Query
        @param query: filter for requested objects
        @type kwargs: dict
        @param kwargs: ??? used to build match selector and query ???
        @rtype: qmfConsole.ObjectProxy
        @return: one matching object, or none
        """
        pass


    def create_subscription(self, query):
        """
        Factory for creating standing subscriptions based on a given query.

        @type query: qmfCommon.Query object
        @param query: determines the list of objects for which this subscription applies
        @rtype: qmfConsole.Subscription
        @returns: an object representing the standing subscription.
        """
        pass


    def invoke_method(self, name, _in_args={}, _reply_handle=None,
                      _timeout=None): 
        """
        """
        assert self._console

        if _timeout is None:
            _timeout = self._console._reply_timeout

        if _in_args:
            _in_args = _in_args.copy()

        handle = self._console._req_correlation.allocate()
        if handle == 0:
            raise Exception("Can not allocate a correlation id!")

        _map = {SchemaMethod.KEY_NAME:name}
        if _in_args:
            _map[SchemaMethod.KEY_ARGUMENTS] = _in_args

        logging.debug("Sending method req to Agent (%s)" % time.time())
        try:
            self._sendMethodReq(_map, handle)
        except SendError, e:
            logging.error(str(e))
            self._console._req_correlation.release(handle)
            return None

        # @todo async method calls!!!
        if _reply_handle is not None:
            print("ASYNC TBD")

        logging.debug("Waiting for response to method req (%s)" % _timeout)
        replyMsg = self._console._req_correlation.get_data(handle, _timeout)
        self._console._req_correlation.release(handle)
        if not replyMsg:
            logging.debug("Agent method req wait timed-out.")
            return None

        _map = replyMsg.content.get(MsgKey.method)
        if not _map:
            logging.error("Invalid method call reply message")
            return None

        return MethodResult(_out_args=_map.get(SchemaMethod.KEY_ARGUMENTS),
                            _error=_map.get(SchemaMethod.KEY_ERROR))

    def __repr__(self):
        return str(self._address)
    
    def __str__(self):
        return self.__repr__()

    def _sendQuery(self, query, correlation_id=None):
        """
        """
        msg = Message(subject=makeSubject(OpCode.get_query),
                      properties={"method":"request"},
                      content={MsgKey.query: query.map_encode()})
        self._sendMsg( msg, correlation_id )


    def _sendMethodReq(self, mr_map, correlation_id=None):
        """
        """
        msg = Message(subject=makeSubject(OpCode.method_req),
                      properties={"method":"request"},
                      content=mr_map)
        self._sendMsg( msg, correlation_id )


  ##==============================================================================
  ## METHOD CALL
  ##==============================================================================

class MethodResult(object):
    def __init__(self, _out_args=None, _error=None):
        self._error = _error
        self._out_args = _out_args

    def succeeded(self): 
        return self._error is None

    def get_exception(self):
        return self._error

    def get_arguments(self): 
        return self._out_args

    def get_argument(self, name): 
        arg = None
        if self._out_args:
            arg = self._out_args.get(name)
        return arg


  ##==============================================================================
  ## CONSOLE
  ##==============================================================================






class Console(Thread):
    """
    A Console manages communications to a collection of agents on behalf of an application.
    """
    def __init__(self, name=None, _domain=None, notifier=None, 
                 reply_timeout = 60,
                 # agent_timeout = 120,
                 agent_timeout = 60,
                 kwargs={}):
        """
        @type name: str
        @param name: identifier for this console.  Must be unique.
        @type notifier: qmfConsole.Notifier
        @param notifier: invoked when events arrive for processing.
        @type kwargs: dict
        @param kwargs: ??? Unused
        """
        Thread.__init__(self)
        if not name:
            self._name = "qmfc-%s.%d" % (platform.node(), os.getpid())
        else:
            self._name = str(name)
        self._domain = _domain
        self._address = QmfAddress.direct(self._name, self._domain)
        self._notifier = notifier
        self._lock = Lock()
        self._conn = None
        self._session = None
        # dict of "agent-direct-address":class Agent entries
        self._agent_map = {}
        self._direct_recvr = None
        self._announce_recvr = None
        self._locate_sender = None
        self._schema_cache = {}
        self._req_correlation = SequencedWaiter()
        self._operational = False
        self._agent_discovery_filter = None
        self._reply_timeout = reply_timeout
        self._agent_timeout = agent_timeout
        self._next_agent_expire = None
        # lock out run() thread
        self._cv = Condition()
        # for passing WorkItems to the application
        self._work_q = Queue.Queue()
        self._work_q_put = False
        ## Old stuff below???
        #self._broker_list = []
        #self.impl = qmfengine.Console()
        #self._event = qmfengine.ConsoleEvent()
        ##self._cv = Condition()
        ##self._sync_count = 0
        ##self._sync_result = None
        ##self._select = {}
        ##self._cb_cond = Condition()



    def destroy(self, timeout=None):
        """
        Must be called before the Console is deleted.  
        Frees up all resources and shuts down all background threads.

        @type timeout: float
        @param timeout: maximum time in seconds to wait for all background threads to terminate.  Default: forever.
        """
        logging.debug("Destroying Console...")
        if self._conn:
            self.removeConnection(self._conn, timeout)
        logging.debug("Console Destroyed")



    def addConnection(self, conn):
        """
        Add a AMQP connection to the console.  The console will setup a session over the
        connection.  The console will then broadcast an Agent Locate Indication over
        the session in order to discover present agents.

        @type conn: qpid.messaging.Connection
        @param conn: the connection to the AMQP messaging infrastructure.
        """
        if self._conn:
            raise Exception( "Multiple connections per Console not supported." );
        self._conn = conn
        self._session = conn.session(name=self._name)
        self._direct_recvr = self._session.receiver(str(self._address) +
                                                    ";{create:always,"
                                                    " node-properties:"
                                                    " {type:topic,"
                                                    " x-properties:"
                                                    " {type:direct}}}", 
                                                    capacity=1)
        logging.error("local addr=%s" % self._address)
        ind_addr = QmfAddress.topic(AMQP_QMF_AGENT_INDICATION, self._domain)
        logging.error("agent.ind addr=%s" % ind_addr)
        self._announce_recvr = self._session.receiver(str(ind_addr) +
                                                      ";{create:always,"
                                                      " node-properties:{type:topic}}",
                                                      capacity=1)
        locate_addr = QmfAddress.topic(AMQP_QMF_AGENT_LOCATE, self._domain)
        logging.error("agent.locate addr=%s" % locate_addr)
        self._locate_sender = self._session.sender(str(locate_addr) +
                                                   ";{create:always,"
                                                   " node-properties:{type:topic}}")
        #
        # Now that receivers are created, fire off the receive thread...
        #
        self._operational = True
        self.start()



    def removeConnection(self, conn, timeout=None):
        """
        Remove an AMQP connection from the console.  Un-does the add_connection() operation,
        and releases any agents and sessions associated with the connection.

        @type conn: qpid.messaging.Connection
        @param conn: connection previously added by add_connection()
        """
        if self._conn and conn and conn != self._conn:
            logging.error( "Attempt to delete unknown connection: %s" % str(conn))

        # tell connection thread to shutdown
        self._operational = False
        if self.isAlive():
            # kick my thread to wake it up
            logging.debug("Making temp sender for [%s]" % self._address)
            tmp_sender = self._session.sender(str(self._address))
            try:
                msg = Message(subject=makeSubject(OpCode.noop))
                tmp_sender.send( msg, sync=True )
            except SendError, e:
                logging.error(str(e))
            logging.debug("waiting for console receiver thread to exit")
            self.join(timeout)
            if self.isAlive():
                logging.error( "Console thread '%s' is hung..." % self.getName() )
        self._direct_recvr.close()
        self._announce_recvr.close()
        self._locate_sender.close()
        self._session.close()
        self._session = None
        self._conn = None
        logging.debug("console connection removal complete")


    def getAddress(self):
        """
        The AMQP address this Console is listening to.
        """
        return self._address


    def destroyAgent( self, agent ):
        """
        Undoes create.
        """
        if not isinstance(agent, Agent):
            raise TypeError("agent must be an instance of class Agent")

        self._lock.acquire()
        try:
            if agent._id in self._agent_map:
                del self._agent_map[agent._id]
        finally:
            self._lock.release()

    def findAgent(self, name, timeout=None ):
        """
        Given the id of a particular agent, return an instance of class Agent 
        representing that agent.  Return None if the agent does not exist.
        """

        self._lock.acquire()
        try:
            agent = self._agent_map.get(name)
            if agent:
                return agent
        finally:
            self._lock.release()

        # agent not present yet - ping it with an agent_locate

        handle = self._req_correlation.allocate()
        if handle == 0:
            raise Exception("Can not allocate a correlation id!")
        try:
            tmp_sender = self._session.sender(str(QmfAddress.direct(name,
                                                                    self._domain))
                                              + ";{create:always,"
                                              " node-properties:"
                                              " {type:topic,"
                                              " x-properties:"
                                              " {type:direct}}}")

            query = QmfQuery.create_id(QmfQuery.TARGET_AGENT, name)
            msg = Message(subject=makeSubject(OpCode.agent_locate),
                          properties={"method":"request"},
                          content={MsgKey.query: query.map_encode()})
            msg.reply_to = str(self._address)
            msg.correlation_id = str(handle)
            logging.debug("Sending Agent Locate (%s)" % time.time())
            tmp_sender.send( msg )
        except SendError, e:
            logging.error(str(e))
            self._req_correlation.release(handle)
            return None

        if timeout is None:
            timeout = self._reply_timeout

        new_agent = None
        logging.debug("Waiting for response to Agent Locate (%s)" % timeout)
        self._req_correlation.get_data( handle, timeout )
        self._req_correlation.release(handle)
        logging.debug("Agent Locate wait ended (%s)" % time.time())
        self._lock.acquire()
        try:
            new_agent = self._agent_map.get(name)
        finally:
            self._lock.release()
        return new_agent


    def doQuery(self, agent, query, timeout=None ):
        """
        """

        target = query.get_target()
        handle = self._req_correlation.allocate()
        if handle == 0:
            raise Exception("Can not allocate a correlation id!")
        try:
            logging.debug("Sending Query to Agent (%s)" % time.time())
            agent._sendQuery(query, handle)
        except SendError, e:
            logging.error(str(e))
            self._req_correlation.release(handle)
            return None

        if not timeout:
            timeout = self._reply_timeout

        logging.debug("Waiting for response to Query (%s)" % timeout)
        reply = self._req_correlation.get_data(handle, timeout)
        self._req_correlation.release(handle)
        if not reply:
            logging.debug("Agent Query wait timed-out.")
            return None

        if target == QmfQuery.TARGET_PACKAGES:
            # simply pass back the list of package names
            logging.debug("Response to Packet Query received")
            return reply.content.get(MsgKey.package_info)
        elif target == QmfQuery.TARGET_OBJECT_ID:
            # simply pass back the list of object_id's
            logging.debug("Response to Object Id Query received")
            return reply.content.get(MsgKey.object_id)
        elif target == QmfQuery.TARGET_SCHEMA_ID:
            logging.debug("Response to Schema Id Query received")
            id_list = []
            for sid_map in reply.content.get(MsgKey.schema_id):
                id_list.append(SchemaClassId.from_map(sid_map))
            return id_list
        elif target == QmfQuery.TARGET_SCHEMA:
            logging.debug("Response to Schema Query received")
            schema_list = []
            for schema_map in reply.content.get(MsgKey.schema):
                # extract schema id, convert based on schema type
                sid_map = schema_map.get(SchemaClass.KEY_SCHEMA_ID)
                if sid_map:
                    sid = SchemaClassId.from_map(sid_map)
                    if sid:
                        if sid.get_type() == SchemaClassId.TYPE_DATA:
                            schema = SchemaObjectClass.from_map(schema_map)
                        else:
                            schema = SchemaEventClass.from_map(schema_map)
                        schema_list.append(schema)
                        self._add_schema(schema)
            return schema_list
        elif target == QmfQuery.TARGET_OBJECT:
            logging.debug("Response to Object Query received")
            obj_list = []
            for obj_map in reply.content.get(MsgKey.data_obj):
                # if the object references a schema, fetch it
                sid_map = obj_map.get(QmfData.KEY_SCHEMA_ID)
                if sid_map:
                    sid = SchemaClassId.from_map(sid_map)
                    schema = self._fetch_schema(sid, _agent=agent,
                                                _timeout=timeout)
                    if not schema:
                        logging.warning("Unknown schema, id=%s" % sid)
                        continue
                    obj = QmfConsoleData(map_=obj_map, agent=agent,
                                         _schema=schema)
                else:
                    # no schema needed
                    obj = QmfConsoleData(map_=obj_map, agent=agent)
                obj_list.append(obj)
            return obj_list
        else:
            logging.warning("Unexpected Target for a Query: '%s'" % target)
            return None

    def run(self):
        global _callback_thread
        #
        # @todo KAG Rewrite when api supports waiting on multiple receivers
        #
        while self._operational:

            # qLen = self._work_q.qsize()

            while True:
                try:
                    msg = self._announce_recvr.fetch(timeout=0)
                except Empty:
                    break
                self._dispatch(msg, _direct=False)

            while True:
                try:
                    msg = self._direct_recvr.fetch(timeout = 0)
                except Empty:
                    break
                self._dispatch(msg, _direct=True)

            self._expireAgents()   # check for expired agents

            #if qLen == 0 and self._work_q.qsize() and self._notifier:
            if self._work_q_put and self._notifier:
                # new stuff on work queue, kick the the application...
                self._work_q_put = False
                _callback_thread = currentThread()
                logging.info("Calling console notifier.indication")
                self._notifier.indication()
                _callback_thread = None

            if self._operational:
                # wait for a message to arrive or an agent
                # to expire
                now = datetime.datetime.utcnow()
                if self._next_agent_expire > now:
                    timeout = ((self._next_agent_expire - now) + datetime.timedelta(microseconds=999999)).seconds
                    try:
                        logging.error("waiting for next rcvr (timeout=%s)..." % timeout)
                        self._session.next_receiver(timeout = timeout)
                    except Empty:
                        pass


        logging.debug("Shutting down Console thread")



    # called by run() thread ONLY
    #
    def _dispatch(self, msg, _direct=True):
        """
        PRIVATE: Process a message received from an Agent
        """

        logging.error( "Message received from Agent! [%s]" % msg )

        try:
            version,opcode = parseSubject(msg.subject)
            # @todo: deal with version mismatch!!!
        except:
            logging.error("Ignoring unrecognized broadcast message '%s'" % msg.subject)
            return

        cmap = {}; props = {}
        if msg.content_type == "amqp/map":
            cmap = msg.content
        if msg.properties:
            props = msg.properties

        if opcode == OpCode.agent_ind:
            self._handleAgentIndMsg( msg, cmap, version, _direct )
        elif opcode == OpCode.data_ind:
            self._handleDataIndMsg(msg, cmap, version, _direct)
        elif opcode == OpCode.event_ind:
            logging.warning("!!! event_ind TBD !!!")
        elif opcode == OpCode.managed_object:
            logging.warning("!!! managed_object TBD !!!")
        elif opcode == OpCode.object_ind:
            logging.warning("!!! object_ind TBD !!!")
        elif opcode == OpCode.response:
            self._handleResponseMsg(msg, cmap, version, _direct)
        elif opcode == OpCode.schema_ind:
            logging.warning("!!! schema_ind TBD !!!")
        elif opcode == OpCode.noop:
             logging.debug("No-op msg received.")
        else:
            logging.warning("Ignoring message with unrecognized 'opcode' value: '%s'" % opcode)


    def _handleAgentIndMsg(self, msg, cmap, version, direct):
        """
        Process a received agent-ind message.  This message may be a response to a
        agent-locate, or it can be an unsolicited agent announce.
        """
        logging.debug("_handleAgentIndMsg '%s' (%s)" % (msg, time.time()))

        ai_map = cmap.get(MsgKey.agent_info)
        if not ai_map or not isinstance(ai_map, type({})):
            logging.warning("Bad agent-ind message received: '%s'" % msg)
            return
        name = ai_map.get("_name")
        if not name:
            logging.warning("Bad agent-ind message received: agent name missing"
                            " '%s'" % msg)
            return

        ignore = True
        matched = False
        correlated = False
        agent_query = self._agent_discovery_filter

        if msg.correlation_id:
            correlated = self._req_correlation.isValid(msg.correlation_id)

        if direct and correlated:
            ignore = False
        elif agent_query:
            matched = agent_query.evaluate(QmfData.create(values=ai_map))
            ignore = not matched

        if not ignore:
            agent = None
            self._lock.acquire()
            try:
                agent = self._agent_map.get(name)
            finally:
                self._lock.release()

            if not agent:
                # need to create and add a new agent
                agent = self._createAgent(name)

            # lock out expiration scanning code
            self._lock.acquire()
            try:
                old_timestamp = agent._announce_timestamp
                agent._announce_timestamp = datetime.datetime.utcnow()
            finally:
                self._lock.release()

            if old_timestamp == None and matched:
                logging.error("AGENT_ADDED for %s (%s)" % (agent, time.time()))
                wi = WorkItem(WorkItem.AGENT_ADDED, None, {"agent": agent})
                self._work_q.put(wi)
                self._work_q_put = True

            if correlated:
                # wake up all waiters
                logging.error("waking waiters for correlation id %s" % msg.correlation_id)
                self._req_correlation.put_data(msg.correlation_id, msg)




    def _handleDataIndMsg(self, msg, cmap, version, direct):
        """
        Process a received data-ind message.
        """
        logging.debug("_handleDataIndMsg '%s' (%s)" % (msg, time.time()))

        if not self._req_correlation.isValid(msg.correlation_id):
            logging.error("FIXME: uncorrelated data indicate??? msg='%s'" % str(msg))
            return

        # wake up all waiters
        logging.error("waking waiters for correlation id %s" % msg.correlation_id)
        self._req_correlation.put_data(msg.correlation_id, msg)


    def _handleResponseMsg(self, msg, cmap, version, direct):
        """
        Process a received data-ind message.
        """
        # @todo code replication - clean me.
        logging.debug("_handleResponseMsg '%s' (%s)" % (msg, time.time()))

        if not self._req_correlation.isValid(msg.correlation_id):
            logging.error("FIXME: uncorrelated response??? msg='%s'" % str(msg))
            return

        # wake up all waiters
        logging.error("waking waiters for correlation id %s" % msg.correlation_id)
        self._req_correlation.put_data(msg.correlation_id, msg)


    def _expireAgents(self):
        """
        Check for expired agents and issue notifications when they expire.
        """
        now = datetime.datetime.utcnow()
        if self._next_agent_expire and now < self._next_agent_expire:
            return
        lifetime_delta = datetime.timedelta(seconds = self._agent_timeout)
        next_expire_delta = lifetime_delta
        self._lock.acquire()
        try:
            logging.debug("!!! expiring agents '%s'" % now)
            for agent in self._agent_map.itervalues():
                if agent._announce_timestamp:
                    agent_deathtime = agent._announce_timestamp + lifetime_delta
                    if agent_deathtime <= now:
                        logging.debug("AGENT_DELETED for %s" % agent)
                        agent._announce_timestamp = None
                        wi = WorkItem(WorkItem.AGENT_DELETED, None,
                                      {"agent":agent})
                        self._work_q.put(wi)
                        self._work_q_put = True
                    else:
                        if (agent_deathtime - now) < next_expire_delta:
                            next_expire_delta = agent_deathtime - now

            self._next_agent_expire = now + next_expire_delta
            logging.debug("!!! next expire cycle = '%s'" % self._next_agent_expire)
        finally:
            self._lock.release()



    def _createAgent( self, name ):
        """
        Factory to create/retrieve an agent for this console
        """

        self._lock.acquire()
        try:
            agent = self._agent_map.get(name)
            if agent:
                return agent

            agent = Agent(name, self)
            agent._sender = self._session.sender(str(agent._address) + 
                                                    ";{create:always,"
                                                    " node-properties:"
                                                    " {type:topic,"
                                                    " x-properties:"
                                                    " {type:direct}}}") 

            self._agent_map[name] = agent
        finally:
            self._lock.release()

        # new agent - query for its schema database for
        # seeding the schema cache (@todo)
        # query = QmfQuery({QmfQuery.TARGET_SCHEMA_ID:None})
        # agent._sendQuery( query )

        return agent



    def enable_agent_discovery(self, _query=None):
        """
        Called to enable the asynchronous Agent Discovery process.
        Once enabled, AGENT_ADD work items can arrive on the WorkQueue.
        """
        # @todo: fix - take predicate only, not entire query!
        if _query is not None:
            if (not isinstance(_query, QmfQuery) or
                _query.get_target() != QmfQuery.TARGET_AGENT):
                raise TypeError("Type QmfQuery with target == TARGET_AGENT expected")
            self._agent_discovery_filter = _query
        else:
            # create a match-all agent query (no predicate)
            self._agent_discovery_filter = QmfQuery.create_wildcard(QmfQuery.TARGET_AGENT) 

    def disable_agent_discovery(self):
        """
        Called to disable the async Agent Discovery process enabled by
        calling enableAgentDiscovery()
        """
        self._agent_discovery_filter = None



    def get_workitem_count(self):
        """
        Returns the count of pending WorkItems that can be retrieved.
        """
        return self._work_q.qsize()



    def get_next_workitem(self, timeout=None):
        """
        Returns the next pending work item, or None if none available.
        @todo: subclass and return an Empty event instead.
        """
        try:
            wi = self._work_q.get(True, timeout)
        except Queue.Empty:
            return None
        return wi


    def release_workitem(self, wi):
        """
        Return a WorkItem to the Console when it is no longer needed.
        @todo: call Queue.task_done() - only 2.5+

        @type wi: class qmfConsole.WorkItem
        @param wi: work item object to return.
        """
        pass

    def _add_schema(self, schema):
        """
        @todo
        """
        if not isinstance(schema, SchemaClass):
            raise TypeError("SchemaClass type expected")

        self._lock.acquire()
        try:
            sid = schema.get_class_id()
            if not self._schema_cache.has_key(sid):
                self._schema_cache[sid] = schema
        finally:
            self._lock.release()

    def _fetch_schema(self, schema_id, _agent=None, _timeout=None):
        """
        Find the schema identified by schema_id.  If not in the cache, ask the
        agent for it.
        """
        if not isinstance(schema_id, SchemaClassId):
            raise TypeError("SchemaClassId type expected")

        self._lock.acquire()
        try:
            schema = self._schema_cache.get(schema_id)
            if schema:
                return schema
        finally:
            self._lock.release()

        if _agent is None:
            return None

        # note: doQuery will add the new schema to the cache automatically.
        slist = self.doQuery(_agent,
                             QmfQuery.create_id(QmfQuery.TARGET_SCHEMA, schema_id),
                             _timeout)
        if slist:
            return slist[0]
        else:
            return None



    # def get_packages(self):
    #     plist = []
    #     for i in range(self.impl.packageCount()):
    #         plist.append(self.impl.getPackageName(i))
    #     return plist
    
    
    # def get_classes(self, package, kind=CLASS_OBJECT):
    #     clist = []
    #     for i in range(self.impl.classCount(package)):
    #         key = self.impl.getClass(package, i)
    #         class_kind = self.impl.getClassKind(key)
    #         if class_kind == kind:
    #             if kind == CLASS_OBJECT:
    #                 clist.append(SchemaObjectClass(None, None, {"impl":self.impl.getObjectClass(key)}))
    #             elif kind == CLASS_EVENT:
    #                 clist.append(SchemaEventClass(None, None, {"impl":self.impl.getEventClass(key)}))
    #     return clist
    
    
    # def bind_package(self, package):
    #     return self.impl.bindPackage(package)
    
    
    # def bind_class(self, kwargs = {}):
    #     if "key" in kwargs:
    #         self.impl.bindClass(kwargs["key"])
    #     elif "package" in kwargs:
    #         package = kwargs["package"]
    #         if "class" in kwargs:
    #             self.impl.bindClass(package, kwargs["class"])
    #         else:
    #             self.impl.bindClass(package)
    #     else:
    #         raise Exception("Argument error: invalid arguments, use 'key' or 'package'[,'class']")
    
    
    # def get_agents(self, broker=None):
    #     blist = []
    #     if broker:
    #         blist.append(broker)
    #     else:
    #         self._cv.acquire()
    #         try:
    #             # copy while holding lock
    #             blist = self._broker_list[:]
    #         finally:
    #             self._cv.release()

    #     agents = []
    #     for b in blist:
    #         for idx in range(b.impl.agentCount()):
    #             agents.append(AgentProxy(b.impl.getAgent(idx), b))

    #     return agents
    
    
    # def get_objects(self, query, kwargs = {}):
    #     timeout = 30
    #     agent = None
    #     temp_args = kwargs.copy()
    #     if type(query) == type({}):
    #         temp_args.update(query)

    #     if "_timeout" in temp_args:
    #         timeout = temp_args["_timeout"]
    #         temp_args.pop("_timeout")

    #     if "_agent" in temp_args:
    #         agent = temp_args["_agent"]
    #         temp_args.pop("_agent")

    #     if type(query) == type({}):
    #         query = Query(temp_args)

    #     self._select = {}
    #     for k in temp_args.iterkeys():
    #         if type(k) == str:
    #             self._select[k] = temp_args[k]

    #     self._cv.acquire()
    #     try:
    #         self._sync_count = 1
    #         self._sync_result = []
    #         broker = self._broker_list[0]
    #         broker.send_query(query.impl, None, agent)
    #         self._cv.wait(timeout)
    #         if self._sync_count == 1:
    #             raise Exception("Timed out: waiting for query response")
    #     finally:
    #         self._cv.release()

    #     return self._sync_result
    
    
    # def get_object(self, query, kwargs = {}):
    #     '''
    #     Return one and only one object or None.
    #     '''
    #     objs = objects(query, kwargs)
    #     if len(objs) == 1:
    #         return objs[0]
    #     else:
    #         return None


    # def first_object(self, query, kwargs = {}):
    #     '''
    #     Return the first of potentially many objects.
    #     '''
    #     objs = objects(query, kwargs)
    #     if objs:
    #         return objs[0]
    #     else:
    #         return None


    # # Check the object against select to check for a match
    # def _select_match(self, object):
    #     schema_props = object.properties()
    #     for key in self._select.iterkeys():
    #         for prop in schema_props:
    #             if key == p[0].name() and self._select[key] != p[1]:
    #                 return False
    #     return True


    # def _get_result(self, list, context):
    #     '''
    #     Called by Broker proxy to return the result of a query.
    #     '''
    #     self._cv.acquire()
    #     try:
    #         for item in list:
    #             if self._select_match(item):
    #                 self._sync_result.append(item)
    #         self._sync_count -= 1
    #         self._cv.notify()
    #     finally:
    #         self._cv.release()


    # def start_sync(self, query): pass
    
    
    # def touch_sync(self, sync): pass
    
    
    # def end_sync(self, sync): pass
    
    


#     def start_console_events(self):
#         self._cb_cond.acquire()
#         try:
#             self._cb_cond.notify()
#         finally:
#             self._cb_cond.release()


#     def _do_console_events(self):
#         '''
#         Called by the Console thread to poll for events.  Passes the events
#         onto the ConsoleHandler associated with this Console.  Is called
#         periodically, but can also be kicked by Console.start_console_events().
#         '''
#         count = 0
#         valid = self.impl.getEvent(self._event)
#         while valid:
#             count += 1
#             try:
#                 if self._event.kind == qmfengine.ConsoleEvent.AGENT_ADDED:
#                     logging.debug("Console Event AGENT_ADDED received")
#                     if self._handler:
#                         self._handler.agent_added(AgentProxy(self._event.agent, None))
#                 elif self._event.kind == qmfengine.ConsoleEvent.AGENT_DELETED:
#                     logging.debug("Console Event AGENT_DELETED received")
#                     if self._handler:
#                         self._handler.agent_deleted(AgentProxy(self._event.agent, None))
#                 elif self._event.kind == qmfengine.ConsoleEvent.NEW_PACKAGE:
#                     logging.debug("Console Event NEW_PACKAGE received")
#                     if self._handler:
#                         self._handler.new_package(self._event.name)
#                 elif self._event.kind == qmfengine.ConsoleEvent.NEW_CLASS:
#                     logging.debug("Console Event NEW_CLASS received")
#                     if self._handler:
#                         self._handler.new_class(SchemaClassKey(self._event.classKey))
#                 elif self._event.kind == qmfengine.ConsoleEvent.OBJECT_UPDATE:
#                     logging.debug("Console Event OBJECT_UPDATE received")
#                     if self._handler:
#                         self._handler.object_update(ConsoleObject(None, {"impl":self._event.object}),
#                                                     self._event.hasProps, self._event.hasStats)
#                 elif self._event.kind == qmfengine.ConsoleEvent.EVENT_RECEIVED:
#                     logging.debug("Console Event EVENT_RECEIVED received")
#                 elif self._event.kind == qmfengine.ConsoleEvent.AGENT_HEARTBEAT:
#                     logging.debug("Console Event AGENT_HEARTBEAT received")
#                     if self._handler:
#                         self._handler.agent_heartbeat(AgentProxy(self._event.agent, None), self._event.timestamp)
#                 elif self._event.kind == qmfengine.ConsoleEvent.METHOD_RESPONSE:
#                     logging.debug("Console Event METHOD_RESPONSE received")
#                 else:
#                     logging.debug("Console thread received unknown event: '%s'" % str(self._event.kind))
#             except e:
#                 print "Exception caught in callback thread:", e
#             self.impl.popEvent()
#             valid = self.impl.getEvent(self._event)
#         return count





# class Broker(ConnectionHandler):
#     #   attr_reader :impl :conn, :console, :broker_bank
#     def __init__(self, console, conn):
#         self.broker_bank = 1
#         self.console = console
#         self.conn = conn
#         self._session = None
#         self._cv = Condition()
#         self._stable = None
#         self._event = qmfengine.BrokerEvent()
#         self._xmtMessage = qmfengine.Message()
#         self.impl = qmfengine.BrokerProxy(self.console.impl)
#         self.console.impl.addConnection(self.impl, self)
#         self.conn.add_conn_handler(self)
#         self._operational = True
    
    
#     def shutdown(self):
#         logging.debug("broker.shutdown() called.")
#         self.console.impl.delConnection(self.impl)
#         self.conn.del_conn_handler(self)
#         if self._session:
#             self.impl.sessionClosed()
#             logging.debug("broker.shutdown() sessionClosed done.")
#             self._session.destroy()
#             logging.debug("broker.shutdown() session destroy done.")
#             self._session = None
#         self._operational = False
#         logging.debug("broker.shutdown() done.")


#     def wait_for_stable(self, timeout = None):
#         self._cv.acquire()
#         try:
#             if self._stable:
#                 return
#             if timeout:
#                 self._cv.wait(timeout)
#                 if not self._stable:
#                     raise Exception("Timed out: waiting for broker connection to become stable")
#             else:
#                 while not self._stable:
#                     self._cv.wait()
#         finally:
#             self._cv.release()


#     def send_query(self, query, ctx, agent):
#         agent_impl = None
#         if agent:
#             agent_impl = agent.impl
#         self.impl.sendQuery(query, ctx, agent_impl)
#         self.conn.kick()


#     def _do_broker_events(self):
#         count = 0
#         valid = self.impl.getEvent(self._event)
#         while valid:
#             count += 1
#             if self._event.kind == qmfengine.BrokerEvent.BROKER_INFO:
#                 logging.debug("Broker Event BROKER_INFO received");
#             elif self._event.kind == qmfengine.BrokerEvent.DECLARE_QUEUE:
#                 logging.debug("Broker Event DECLARE_QUEUE received");
#                 self.conn.impl.declareQueue(self._session.handle, self._event.name)
#             elif self._event.kind == qmfengine.BrokerEvent.DELETE_QUEUE:
#                 logging.debug("Broker Event DELETE_QUEUE received");
#                 self.conn.impl.deleteQueue(self._session.handle, self._event.name)
#             elif self._event.kind == qmfengine.BrokerEvent.BIND:
#                 logging.debug("Broker Event BIND received");
#                 self.conn.impl.bind(self._session.handle, self._event.exchange, self._event.name, self._event.bindingKey)
#             elif self._event.kind == qmfengine.BrokerEvent.UNBIND:
#                 logging.debug("Broker Event UNBIND received");
#                 self.conn.impl.unbind(self._session.handle, self._event.exchange, self._event.name, self._event.bindingKey)
#             elif self._event.kind == qmfengine.BrokerEvent.SETUP_COMPLETE:
#                 logging.debug("Broker Event SETUP_COMPLETE received");
#                 self.impl.startProtocol()
#             elif self._event.kind == qmfengine.BrokerEvent.STABLE:
#                 logging.debug("Broker Event STABLE received");
#                 self._cv.acquire()
#                 try:
#                     self._stable = True
#                     self._cv.notify()
#                 finally:
#                     self._cv.release()
#             elif self._event.kind == qmfengine.BrokerEvent.QUERY_COMPLETE:
#                 result = []
#                 for idx in range(self._event.queryResponse.getObjectCount()):
#                     result.append(ConsoleObject(None, {"impl":self._event.queryResponse.getObject(idx), "broker":self}))
#                 self.console._get_result(result, self._event.context)
#             elif self._event.kind == qmfengine.BrokerEvent.METHOD_RESPONSE:
#                 obj = self._event.context
#                 obj._method_result(MethodResponse(self._event.methodResponse()))
            
#             self.impl.popEvent()
#             valid = self.impl.getEvent(self._event)
        
#         return count
    
    
#     def _do_broker_messages(self):
#         count = 0
#         valid = self.impl.getXmtMessage(self._xmtMessage)
#         while valid:
#             count += 1
#             logging.debug("Broker: sending msg on connection")
#             self.conn.impl.sendMessage(self._session.handle, self._xmtMessage)
#             self.impl.popXmt()
#             valid = self.impl.getXmtMessage(self._xmtMessage)
        
#         return count
    
    
#     def _do_events(self):
#         while True:
#             self.console.start_console_events()
#             bcnt = self._do_broker_events()
#             mcnt = self._do_broker_messages()
#             if bcnt == 0 and mcnt == 0:
#                 break;
    
    
#     def conn_event_connected(self):
#         logging.debug("Broker: Connection event CONNECTED")
#         self._session = Session(self.conn, "qmfc-%s.%d" % (socket.gethostname(), os.getpid()), self)
#         self.impl.sessionOpened(self._session.handle)
#         self._do_events()
    
    
#     def conn_event_disconnected(self, error):
#         logging.debug("Broker: Connection event DISCONNECTED")
#         pass
    
    
#     def conn_event_visit(self):
#         self._do_events()


#     def sess_event_session_closed(self, context, error):
#         logging.debug("Broker: Session event CLOSED")
#         self.impl.sessionClosed()
    
    
#     def sess_event_recv(self, context, message):
#         logging.debug("Broker: Session event MSG_RECV")
#         if not self._operational:
#             logging.warning("Unexpected session event message received by Broker proxy: context='%s'" % str(context))
#         self.impl.handleRcvMessage(message)
#         self._do_events()



################################################################################
################################################################################
################################################################################
################################################################################
#                 TEMPORARY TEST CODE - TO BE DELETED
################################################################################
################################################################################
################################################################################
################################################################################

if __name__ == '__main__':
    # temp test code
    from qmfCommon import (qmfTypes, QmfEvent, SchemaProperty)

    logging.getLogger().setLevel(logging.INFO)

    logging.info( "************* Creating Async Console **************" )

    class MyNotifier(Notifier):
        def __init__(self, context):
            self._myContext = context
            self.WorkAvailable = False

        def indication(self):
            print("Indication received! context=%d" % self._myContext)
            self.WorkAvailable = True

    _noteMe = MyNotifier( 666 )

    _myConsole = Console(notifier=_noteMe)

    _myConsole.enable_agent_discovery()
    logging.info("Waiting...")


    logging.info( "Destroying console:" )
    _myConsole.destroy( 10 )

    logging.info( "******** Messing around with Schema ********" )

    _sec = SchemaEventClass( _classId=SchemaClassId("myPackage", "myClass",
                                                    stype=SchemaClassId.TYPE_EVENT), 
                             _desc="A typical event schema",
                             _props={"Argument-1": SchemaProperty(_type_code=qmfTypes.TYPE_UINT8,
                                                                  kwargs = {"min":0,
                                                                            "max":100,
                                                                            "unit":"seconds",
                                                                            "desc":"sleep value"}),
                                     "Argument-2": SchemaProperty(_type_code=qmfTypes.TYPE_LSTR,
                                                                  kwargs={"maxlen":100,
                                                                          "desc":"a string argument"})})
    print("_sec=%s" % _sec.get_class_id())
    print("_sec.gePropertyCount()=%d" % _sec.get_property_count() )
    print("_sec.getProperty('Argument-1`)=%s" % _sec.get_property('Argument-1') )
    print("_sec.getProperty('Argument-2`)=%s" % _sec.get_property('Argument-2') )
    try:
        print("_sec.getProperty('not-found')=%s" % _sec.get_property('not-found') )
    except:
        pass
    print("_sec.getProperties()='%s'" % _sec.get_properties())

    print("Adding another argument")
    _arg3 = SchemaProperty( _type_code=qmfTypes.TYPE_BOOL,
                            kwargs={"dir":"IO",
                                    "desc":"a boolean argument"})
    _sec.add_property('Argument-3', _arg3)
    print("_sec=%s" % _sec.get_class_id())
    print("_sec.getPropertyCount()=%d" % _sec.get_property_count() )
    print("_sec.getProperty('Argument-1')=%s" % _sec.get_property('Argument-1') )
    print("_sec.getProperty('Argument-2')=%s" % _sec.get_property('Argument-2') )
    print("_sec.getProperty('Argument-3')=%s" % _sec.get_property('Argument-3') )

    print("_arg3.mapEncode()='%s'" % _arg3.map_encode() )

    _secmap = _sec.map_encode()
    print("_sec.mapEncode()='%s'" % _secmap )

    _sec2 = SchemaEventClass( _map=_secmap )

    print("_sec=%s" % _sec.get_class_id())
    print("_sec2=%s" % _sec2.get_class_id())

    _soc = SchemaObjectClass( _map = {"_schema_id": {"_package_name": "myOtherPackage",
                                                     "_class_name":   "myOtherClass",
                                                     "_type":         "_data"},
                                      "_desc": "A test data object",
                                      "_values":
                                          {"prop1": {"amqp_type": qmfTypes.TYPE_UINT8,
                                                     "access": "RO",
                                                     "index": True,
                                                     "unit": "degrees"},
                                           "prop2": {"amqp_type": qmfTypes.TYPE_UINT8,
                                                     "access": "RW",
                                                     "index": True,
                                                     "desc": "The Second Property(tm)",
                                                     "unit": "radians"},
                                           "statistics": { "amqp_type": qmfTypes.TYPE_DELTATIME,
                                                           "unit": "seconds",
                                                           "desc": "time until I retire"},
                                           "meth1": {"_desc": "A test method",
                                                     "_arguments":
                                                         {"arg1": {"amqp_type": qmfTypes.TYPE_UINT32,
                                                                   "desc": "an argument 1",
                                                                   "dir":  "I"},
                                                          "arg2": {"amqp_type": qmfTypes.TYPE_BOOL,
                                                                   "dir":  "IO",
                                                                   "desc": "some weird boolean"}}},
                                           "meth2": {"_desc": "A test method",
                                                     "_arguments":
                                                         {"m2arg1": {"amqp_type": qmfTypes.TYPE_UINT32,
                                                                     "desc": "an 'nuther argument",
                                                                     "dir":
                                                                         "I"}}}},
                                      "_subtypes":
                                          {"prop1":"qmfProperty",
                                           "prop2":"qmfProperty",
                                           "statistics":"qmfProperty",
                                           "meth1":"qmfMethod",
                                           "meth2":"qmfMethod"},
                                      "_primary_key_names": ["prop2", "prop1"]})

    print("_soc='%s'" % _soc)

    print("_soc.getPrimaryKeyList='%s'" % _soc.get_id_names())

    print("_soc.getPropertyCount='%d'" % _soc.get_property_count())
    print("_soc.getProperties='%s'" % _soc.get_properties())
    print("_soc.getProperty('prop2')='%s'" % _soc.get_property('prop2'))

    print("_soc.getMethodCount='%d'" % _soc.get_method_count())
    print("_soc.getMethods='%s'" % _soc.get_methods())
    print("_soc.getMethod('meth2')='%s'" % _soc.get_method('meth2'))

    _socmap = _soc.map_encode()
    print("_socmap='%s'" % _socmap)
    _soc2 = SchemaObjectClass( _map=_socmap )
    print("_soc='%s'" % _soc)
    print("_soc2='%s'" % _soc2)

    if _soc2.get_class_id() == _soc.get_class_id():
        print("soc and soc2 are the same schema")


    logging.info( "******** Messing around with ObjectIds ********" )


    qd = QmfData( _values={"prop1":1, "prop2":True, "prop3": {"a":"map"}, "prop4": "astring"} )
    print("qd='%s':" % qd)

    print("prop1=%d prop2=%s prop3=%s prop4=%s" % (qd.prop1, qd.prop2, qd.prop3, qd.prop4))

    print("qd map='%s'" % qd.map_encode())
    print("qd getProperty('prop4')='%s'" % qd.get_value("prop4"))
    qd.set_value("prop4", 4, "A test property called 4")
    print("qd setProperty('prop4', 4)='%s'" % qd.get_value("prop4"))
    qd.prop4 = 9
    print("qd.prop4 = 9 ='%s'" % qd.prop4)
    qd["prop4"] = 11
    print("qd[prop4] = 11 ='%s'" % qd["prop4"])

    print("qd.mapEncode()='%s'" % qd.map_encode())
    _qd2 = QmfData( _map = qd.map_encode() )
    print("_qd2.mapEncode()='%s'" % _qd2.map_encode())

    _qmfDesc1 = QmfConsoleData( {"_values" : {"prop1": 1, "statistics": 666,
                                              "prop2": 0}},
                                agent="some agent name?",
                                _schema = _soc)

    print("_qmfDesc1 map='%s'" % _qmfDesc1.map_encode())

    _qmfDesc1._set_schema( _soc )

    print("_qmfDesc1 prop2 = '%s'" % _qmfDesc1.get_value("prop2"))
    print("_qmfDesc1 primarykey = '%s'" % _qmfDesc1.get_object_id())
    print("_qmfDesc1 classid = '%s'" % _qmfDesc1.get_schema_class_id())


    _qmfDescMap = _qmfDesc1.map_encode()
    print("_qmfDescMap='%s'" % _qmfDescMap)

    _qmfDesc2 = QmfData( _map=_qmfDescMap, _schema=_soc )

    print("_qmfDesc2 map='%s'" % _qmfDesc2.map_encode())
    print("_qmfDesc2 prop2 = '%s'" % _qmfDesc2.get_value("prop2"))
    print("_qmfDesc2 primary key = '%s'" % _qmfDesc2.get_object_id())


    logging.info( "******** Messing around with QmfEvents ********" )


    _qmfevent1 = QmfEvent( _timestamp = 1111,
                           _schema = _sec,
                           _values = {"Argument-1": 77, 
                                      "Argument-3": True,
                                      "Argument-2": "a string"})
    print("_qmfevent1.mapEncode()='%s'" % _qmfevent1.map_encode())
    print("_qmfevent1.getTimestamp()='%s'" % _qmfevent1.get_timestamp())

    _qmfevent1Map = _qmfevent1.map_encode()

    _qmfevent2 = QmfEvent(_map=_qmfevent1Map, _schema=_sec)
    print("_qmfevent2.mapEncode()='%s'" % _qmfevent2.map_encode())


    logging.info( "******** Messing around with Queries ********" )

    _q1 = QmfQuery.create_predicate(QmfQuery.TARGET_AGENT,
                                    QmfQueryPredicate({QmfQuery.LOGIC_AND:
                                                           [{QmfQuery.CMP_EQ: ["vendor",  "AVendor"]},
                                                            {QmfQuery.CMP_EQ: ["product", "SomeProduct"]},
                                                            {QmfQuery.CMP_EQ: ["name", "Thingy"]},
                                                            {QmfQuery.LOGIC_OR:
                                                                 [{QmfQuery.CMP_LE: ["temperature", -10]},
                                                                  {QmfQuery.CMP_FALSE: None},
                                                                  {QmfQuery.CMP_EXISTS: ["namey"]}]}]}))

    print("_q1.mapEncode() = [%s]" % _q1.map_encode())
