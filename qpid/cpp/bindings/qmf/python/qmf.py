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
import socket
import os
from threading import Thread
from threading import RLock
import qmfengine
from qmfengine import (ACCESS_READ_CREATE, ACCESS_READ_ONLY, ACCESS_READ_WRITE)
from qmfengine import (CLASS_EVENT, CLASS_OBJECT)
from qmfengine import (DIR_IN, DIR_IN_OUT, DIR_OUT)
from qmfengine import (TYPE_ABSTIME, TYPE_ARRAY, TYPE_BOOL, TYPE_DELTATIME,
                       TYPE_DOUBLE, TYPE_FLOAT, TYPE_INT16, TYPE_INT32, TYPE_INT64,
                       TYPE_INT8, TYPE_LIST, TYPE_LSTR, TYPE_MAP, TYPE_OBJECT, 
                       TYPE_REF, TYPE_SSTR, TYPE_UINT16, TYPE_UINT32, TYPE_UINT64, 
                       TYPE_UINT8, TYPE_UUID)


  ##==============================================================================
  ## CONNECTION
  ##==============================================================================

class ConnectionSettings:
  #attr_reader :impl
    def __init__(self, url=None):
        if url:
            self.impl = qmfengine.ConnectionSettings(url)
        else:
            self.impl = qmfengine.ConnectionSettings()
    
    
    def set_attr(self, key, val):
        if type(val) == str:
            _v = qmfengine.Value(TYPE_LSTR)
            _v.setString(val)
        elif type(val) == bool:
            _v = qmfengine.Value(TYPE_BOOL)
            _v.setBool(val)
        elif type(val) == int:
            _v = qmfengine.Value(TYPE_UINT32)
            _v.setUint(val)
        else:
            raise ArgumentError("Value for attribute '%s' has unsupported type: %s" % ( key, type(val)))
        
        self.impl.setAttr(key, _v)



class ConnectionHandler:
    def conn_event_connected(self): None
    def conn_event_disconnected(self, error): None
    def sess_event_session_closed(self, context, error): None
    def sess_event_recv(self, context, message): None



class Connection(Thread):
    def __init__(self, settings):
        Thread.__init__(self)
        self._lock = RLock()
        self.impl = qmfengine.ResilientConnection(settings.impl)
        self._sockEngine, self._sock = socket.socketpair(socket.AF_UNIX, socket.SOCK_STREAM)
        self.impl.setNotifyFd(self._sockEngine.fileno())
        self._new_conn_handlers = []
        self._conn_handlers = []
        self.start()
    
    
    def add_conn_handler(self, handler):
        self._lock.acquire()
        try:
            self._new_conn_handlers.append(handler)
        finally:
            self._lock.release()
        self._sockEngine.send("x")
    
    
    def run(self):
        eventImpl = qmfengine.ResilientConnectionEvent()
        connected = False
        new_handlers = []
        bt_count = 0
        
        while True:
            # print "Waiting for socket data"
            self._sock.recv(1)
            
            self._lock.acquire()
            try:
                new_handlers = self._new_conn_handlers
                self._new_conn_handlers = []
            finally:
                self._lock.release()
            
            for nh in new_handlers:
                self._conn_handlers.append(nh)
                if connected:
                    nh.conn_event_connected()
            
            new_handlers = []
            
            valid = self.impl.getEvent(eventImpl)
            while valid:
                try:
                    if eventImpl.kind == qmfengine.ResilientConnectionEvent.CONNECTED:
                        connected = True
                        for h in self._conn_handlers:
                            h.conn_event_connected()
                        
                    elif eventImpl.kind == qmfengine.ResilientConnectionEvent.DISCONNECTED:
                        connected = False
                        for h in self._conn_handlers:
                            h.conn_event_disconnected(eventImpl.errorText)
                        
                    elif eventImpl.kind == qmfengine.ResilientConnectionEvent.SESSION_CLOSED:
                        eventImpl.sessionContext.handler.sess_event_session_closed(eventImpl.sessionContext, eventImpl.errorText)
                        
                    elif eventImpl.kind == qmfengine.ResilientConnectionEvent.RECV:
                        eventImpl.sessionContext.handler.sess_event_recv(eventImpl.sessionContext, eventImpl.message)
                        
                except:
                    import traceback
                    print "Event Exception:", sys.exc_info()
                    if bt_count < 2:
                        traceback.print_exc()
                        traceback.print_stack()
                        bt_count += 1
                
                self.impl.popEvent()
                valid = self.impl.getEvent(eventImpl)



class Session:
    def __init__(self, conn, label, handler):
        self._conn = conn
        self._label = label
        self.handler = handler
        self.handle = qmfengine.SessionHandle()
        result = self._conn.impl.createSession(label, self, self.handle)
    
    
    def __del__(self):
        self._conn.impl.destroySession(self.handle)



  ##==============================================================================
  ## OBJECTS
  ##==============================================================================

class QmfObject:
    # attr_reader :impl, :object_class
    def __init__(self, cls):
        self.object_class = cls
        self.impl = qmfengine.Object(self.object_class.impl)
    
    
    def __del__(self):
        self.impl.destroy()
    
    
    def object_id(self):
        return ObjectId(self.impl.getObjectId())
    
    
    def set_object_id(self, oid):
        self.impl.setObjectId(oid.impl)
    
    
    def get_attr(self, name):
        val = self._value(name)
        vType = val.getType()
        if vType == TYPE_UINT8:  return val.asUint()
        elif vType == TYPE_UINT16:  return val.asUint()
        elif vType == TYPE_UINT32:  return val.asUint()
        elif vType == TYPE_UINT64:  return val.asUint64()
        elif vType == TYPE_SSTR:  return val.asString()
        elif vType == TYPE_LSTR:  return val.asString()
        elif vType == TYPE_ABSTIME:  return val.asInt64()
        elif vType == TYPE_DELTATIME:  return val.asUint64()
        elif vType == TYPE_REF:  return val.asObjectId()
        elif vType == TYPE_BOOL:  return val.asBool()
        elif vType == TYPE_FLOAT:  return val.asFloat()
        elif vType == TYPE_DOUBLE:  return val.asDouble()
        elif vType == TYPE_UUID:  return val.asUuid()
        elif vType == TYPE_INT8:  return val.asInt()
        elif vType == TYPE_INT16:  return val.asInt()
        elif vType == TYPE_INT32:  return val.asInt()
        elif vType == TYPE_INT64:  return val.asInt64()
        else:
            # when TYPE_MAP
            # when TYPE_OBJECT
            # when TYPE_LIST
            # when TYPE_ARRAY
            print "Unsupported type for get_attr?", val.getType()
            return None
    
    
    def set_attr(self, name, v):
        val = self._value(name)
        vType = val.getType()
        if vType == TYPE_UINT8: return val.setUint(v)
        elif vType == TYPE_UINT16: return val.setUint(v)
        elif vType == TYPE_UINT32: return val.setUint(v)
        elif vType == TYPE_UINT64: return val.setUint64(v)
        elif vType == TYPE_SSTR:
            if v: return val.setString(v)
            else: return val.setString('')
        elif vType == TYPE_LSTR:
            if v: return val.setString(v) 
            else: return val.setString('')
        elif vType == TYPE_ABSTIME: return val.setInt64(v)
        elif vType == TYPE_DELTATIME: return val.setUint64(v)
        elif vType == TYPE_REF: return val.setObjectId(v.impl)
        elif vType == TYPE_BOOL: return val.setBool(v)
        elif vType == TYPE_FLOAT: return val.setFloat(v)
        elif vType == TYPE_DOUBLE: return val.setDouble(v)
        elif vType == TYPE_UUID: return val.setUuid(v)
        elif vType == TYPE_INT8: return val.setInt(v)
        elif vType == TYPE_INT16: return val.setInt(v)
        elif vType == TYPE_INT32: return val.setInt(v)
        elif vType == TYPE_INT64: return val.setInt64(v)
        else:
            # when TYPE_MAP
            # when TYPE_OBJECT
            # when TYPE_LIST
            # when TYPE_ARRAY
            print "Unsupported type for get_attr?", val.getType()
            return None
    
    
    def __getitem__(self, name):
        return self.get_attr(name)
    
    
    def __setitem__(self, name, value):
        self.set_attr(name, value)
    
    
    def inc_attr(self, name, by=1):
        self.set_attr(name, self.get_attr(name) + by)
    
    
    def dec_attr(self, name, by=1):
        self.set_attr(name, self.get_attr(name) - by)
    
    
    def _value(self, name):
        val = self.impl.getValue(name)
        if not val:
            raise ArgumentError("Attribute '%s' not defined for class %s" % (name, self.object_class.impl.getName()))
        return val



class ConsoleObject(QmfObject):
    # attr_reader :current_time, :create_time, :delete_time
    def __init__(self, cls):
        QmfObject.__init__(self, cls)
    
    
    def update(self): pass
    def mergeUpdate(self, newObject): pass
    def is_deleted(self):
        return self.delete_time > 0
    def index(self): pass
    def method_missing(self, name, *args): pass



class ObjectId:
    def __init__(self, impl=None):
        if impl:
            self.impl = impl
        else:
            self.impl = qmfengine.ObjectId()
    
    
    def object_num_high(self):
        return self.impl.getObjectNumHi()
    
    
    def object_num_low(self):
        return self.impl.getObjectNumLo()
    
    
    def __eq__(self, other):
        if self.__class__ != other.__class__: return False
        return (self.impl.getObjectNumHi() == other.impl.getObjectNumHi() and
                self.impl.getObjectNumLo() == other.impl.getObjectNumLo())
    
    
    def __ne__(self, other):
        return not self.__eq__(other)



class Arguments:
    def __init__(self, map):
        self.map = map
        self._by_hash = {}
        key_count = self.map.keyCount()
        a = 0
        while a < key_count:
            self._by_hash[self.map.key(a)] = self.by_key(self.map.key(a))
            a += 1
    
    
    def __getitem__(self, key):
        return self._by_hash[key]
    
    
    def __setitem__(self, key, value):
        self._by_hash[key] = value
        self.set(key, value)
    
    
    def __iter__(self):
        return _by_hash.__iter__
    
    
    def by_key(self, key):
        val = self.map.byKey(key)
        vType = val.getType()
        if vType == TYPE_UINT8: return val.asUint()
        elif vType == TYPE_UINT16: return val.asUint()
        elif vType == TYPE_UINT32: return val.asUint()
        elif vType == TYPE_UINT64: return val.asUint64()
        elif vType == TYPE_SSTR: return val.asString()
        elif vType == TYPE_LSTR: return val.asString()
        elif vType == TYPE_ABSTIME:   return val.asInt64()
        elif vType == TYPE_DELTATIME: return val.asUint64()
        elif vType == TYPE_REF:  return val.asObjectId()
        elif vType == TYPE_BOOL: return val.asBool()
        elif vType == TYPE_FLOAT:  return val.asFloat()
        elif vType == TYPE_DOUBLE: return val.asDouble()
        elif vType == TYPE_UUID: return val.asUuid()
        elif vType == TYPE_INT8: return val.asInt()
        elif vType == TYPE_INT16: return val.asInt()
        elif vType == TYPE_INT32: return val.asInt()
        elif vType == TYPE_INT64: return val.asInt64()
        else:
            # when TYPE_MAP
            # when TYPE_OBJECT
            # when TYPE_LIST
            # when TYPE_ARRAY
            print "Unsupported Type for Get?", val.getType()
            return None
    
    
    def set(self, key, value):
        val = self.map.byKey(key)
        vType = val.getType()
        if vType == TYPE_UINT8: return val.setUint(value)
        elif vType == TYPE_UINT16: return val.setUint(value)
        elif vType == TYPE_UINT32: return val.setUint(value)
        elif vType == TYPE_UINT64: return val.setUint64(value)
        elif vType == TYPE_SSTR: 
            if value:
                return val.setString(value)
            else:
                return val.setString('')
        elif vType == TYPE_LSTR:
            if value:
                return val.setString(value)
            else:
                return val.setString('')
        elif vType == TYPE_ABSTIME: return val.setInt64(value)
        elif vType == TYPE_DELTATIME: return val.setUint64(value)
        elif vType == TYPE_REF: return val.setObjectId(value.impl)
        elif vType == TYPE_BOOL: return val.setBool(value)
        elif vType == TYPE_FLOAT: return val.setFloat(value)
        elif vType == TYPE_DOUBLE: return val.setDouble(value)
        elif vType == TYPE_UUID: return val.setUuid(value)
        elif vType == TYPE_INT8: return val.setInt(value)
        elif vType == TYPE_INT16: return val.setInt(value)
        elif vType == TYPE_INT32: return val.setInt(value)
        elif vType == TYPE_INT64: return val.setInt64(value)
        else:
            # when TYPE_MAP
            # when TYPE_OBJECT
            # when TYPE_LIST
            # when TYPE_ARRAY
            print "Unsupported Type for Set?", val.getType()
            return None



class Query:
    def __init__(self, i=None):
        if i:
            self.impl = i
        else:
            self.impl = qmfengine.Query()
        

    def package_name(self): return self.impl.getPackage()
    def class_name(self): return self.impl.getClass()
    def object_id(self):
        _objid = self.impl.getObjectId()
        if _objid:
            return ObjectId(_objid)
        else:
            return None
    OPER_AND = qmfengine.Query.OPER_AND
    OPER_OR = qmfengine.Query.OPER_OR



  ##==============================================================================
  ## SCHEMA
  ##==============================================================================



class SchemaArgument:
    #attr_reader :impl
    def __init__(self, name, typecode, kwargs={}):
        self.impl = qmfengine.SchemaArgument(name, typecode)
        if kwargs.has_key("dir"):  self.impl.setDirection(kwargs["dir"])
        if kwargs.has_key("unit"): self.impl.setUnit(kwargs["unit"])
        if kwargs.has_key("desc"): self.impl.setDesc(kwargs["desc"])



class SchemaMethod:
    # attr_reader :impl
    def __init__(self, name, kwargs={}):
        self.impl = qmfengine.SchemaMethod(name)
        if kwargs.has_key("desc"): self.impl.setDesc(kwargs["desc"])
        self._arguments = []
    
    
    def add_argument(self, arg):
        self._arguments.append(arg)
        self.impl.addArgument(arg.impl)


class SchemaProperty:
    #attr_reader :impl
    def __init__(self, name, typecode, kwargs={}):
        self.impl = qmfengine.SchemaProperty(name, typecode)
        if kwargs.has_key("access"):   self.impl.setAccess(kwargs["access"])
        if kwargs.has_key("index"):    self.impl.setIndex(kwargs["index"])
        if kwargs.has_key("optional"): self.impl.setOptional(kwargs["optional"])
        if kwargs.has_key("unit"):     self.impl.setUnit(kwargs["unit"])
        if kwargs.has_key("desc"):     self.impl.setDesc(kwargs["desc"])
    
    
    def name(self):
        return self.impl.getName()



class SchemaStatistic:
    # attr_reader :impl
    def __init__(self, name, typecode, kwargs={}):
        self.impl = qmfengine.SchemaStatistic(name, typecode)
        if kwargs.has_key("unit"): self.impl.setUnit(kwargs["unit"])
        if kwargs.has_key("desc"): self.impl.setDesc(kwargs["desc"])



class SchemaClassKey:
    #attr_reader :impl
    def __init__(self, i):
        self.impl = i
    
    
    def get_package(self):
        self.impl.getPackageName()
    
    
    def get_class(self):
        self.impl.getClassName()



class SchemaObjectClass:
    # attr_reader :impl
    def __init__(self, package, name, kwargs={}):
        self.impl = qmfengine.SchemaObjectClass(package, name)
        self._properties = []
        self._statistics = []
        self._methods = []
    
    
    def add_property(self, prop):
        self._properties.append(prop)
        self.impl.addProperty(prop.impl)
    
    
    def add_statistic(self, stat):
        self._statistics.append(stat)
        self.impl.addStatistic(stat.impl)
    
    
    def add_method(self, meth):
        self._methods.append(meth)
        self.impl.addMethod(meth.impl)
    
    
    def name(self):
        return self.impl.getName()
    
    
    def properties(self):
        return self._properties


class SchemaEventClass:
    # attr_reader :impl
    def __init__(self, package, name, kwargs={}):
        self.impl = qmfengine.SchemaEventClass(package, name)
        if kwargs.has_key("desc"): self.impl.setDesc(kwargs["desc"])
        self._arguments = []
    
    
    def add_argument(self, arg):
        self._arguments.append(arg)
        self.impl.addArgument(arg.impl)



  ##==============================================================================
  ## CONSOLE
  ##==============================================================================



class ConsoleHandler:
    def agent_added(self, agent): pass
    def agent_deleted(self, agent): pass
    def new_package(self, package): pass
    def new_class(self, class_key): pass
    def object_update(self, obj, hasProps, hasStats): pass
    def event_received(self, event): pass
    def agent_heartbeat(self, agent, timestamp): pass
    def method_response(self, resp): pass
    def broker_info(self, broker): pass



class Console:
    #   attr_reader :impl
    def initialize(handler=None, kwargs={}):
       self._handler = handler
       self.impl = qmfengine.ConsoleEngine()
       self._event = qmfengine.ConsoleEvent()
       self._broker_list = []
    
    
    def add_connection(self, conn):
        broker = Broker(self, conn)
        self._broker_list.append(broker)
        return broker
    
    
    def del_connection(self, broker): pass
    
    
    def get_packages(self): pass
    
    
    def get_classes(self, package): pass
    
    
    def get_schema(self, class_key): pass
    
    
    def bind_package(self, package): pass
    
    
    def bind_class(self, kwargs = {}): pass
    
    
    def get_agents(self, broker=None): pass
    
    
    def get_objects(self, query, kwargs = {}): pass
    
    
    def start_sync(self, query): pass
    
    
    def touch_sync(self, sync): pass
    
    
    def end_sync(self, sync): pass
    
    
    def do_console_events(self):
        count = 0
        valid = self.impl.getEvent(self._event)
        while valid:
            count += 1
            print "Console Event:", self._event.kind
            if self._event.kind == qmfengine.ConsoleEvent.AGENT_ADDED:
                pass
            elif self._event.kind == qmfengine.ConsoleEvent.AGENT_DELETED:
                pass
            elif self._event.kind == qmfengine.ConsoleEvent.NEW_PACKAGE:
                pass
            elif self._event.kind == qmfengine.ConsoleEvent.NEW_CLASS:
                pass
            elif self._event.kind == qmfengine.ConsoleEvent.OBJECT_UPDATE:
                pass
            elif self._event.kind == qmfengine.ConsoleEvent.EVENT_RECEIVED:
                pass
            elif self._event.kind == qmfengine.ConsoleEvent.AGENT_HEARTBEAT:
                pass
            elif self._event.kind == qmfengine.ConsoleEvent.METHOD_RESPONSE:
                pass
            
            self.impl.popEvent()
            valid = self.impl.getEvent(self._event)
        return count



class Broker(ConnectionHandler):
    #   attr_reader :impl
    def __init__(self, console, conn):
        self._console = console
        self._conn = conn
        self._session = None
        self._event = qmfengine.BrokerEvent()
        self._xmtMessage = qmfengine.Message()
        self.impl = qmfengine.BrokerProxy(self._console.impl)
        self._console.impl.addConnection(self.impl, self)
        self._conn.add_conn_handler(self)
    
    
    def do_broker_events(self):
        count = 0
        valid = self.impl.getEvent(self._event)
        while valid:
            count += 1
            print "Broker Event: ", self._event.kind
            if self._event.kind == qmfengine.BrokerEvent.BROKER_INFO:
                pass
            elif self._event.kind == qmfengine.BrokerEvent.DECLARE_QUEUE:
                self._conn.impl.declareQueue(self._session.handle, self._event.name)
            elif self._event.kind == qmfengine.BrokerEvent.DELETE_QUEUE:
                self._conn.impl.deleteQueue(self._session.handle, self._event.name)
            elif self._event.kind == qmfengine.BrokerEvent.BIND:
                self._conn.impl.bind(self._session.handle, self._event.exchange, self._event.name, self._event.bindingKey)
            elif self._event.kind == qmfengine.BrokerEvent.UNBIND:
                self._conn.impl.unbind(self._session.handle, self._event.exchange, self._event.name, self._event.bindingKey)
            elif self._event.kind == qmfengine.BrokerEvent.SETUP_COMPLETE:
                self.impl.startProtocol()
            
            self.impl.popEvent()
            valid = self.impl.getEvent(self._event)
        
        return count
    
    
    def do_broker_messages(self):
        count = 0
        valid = self.impl.getXmtMessage(self._xmtMessage)
        while valid:
            count += 1
            self._conn.impl.sendMessage(self._session.handle, self._xmtMessage)
            self.impl.popXmt()
            valid = self.impl.getXmtMessage(self._xmtMessage)
        
        return count
    
    
    def do_events(self):
        while True:
            ccnt = self._console.do_console_events()
            bcnt = do_broker_events()
            mcnt = do_broker_messages()
            if ccnt == 0 and bcnt == 0 and mcnt == 0:
                break;
    
    
    def conn_event_connected(self):
        print "Console Connection Established..."
        self._session = Session(self._conn, "qmfc-%s.%d" % (socket.gethostname(), os.getpid()), self)
        self.impl.sessionOpened(self._session.handle)
        self.do_events()
    
    
    def conn_event_disconnected(self, error):
        print "Console Connection Lost"
        pass
    
    
    def sess_event_session_closed(self, context, error):
        print "Console Session Lost"
        self.impl.sessionClosed()
    
    
    def sess_event_recv(self, context, message):
        self.impl.handleRcvMessage(message)
        self.do_events()



  ##==============================================================================
  ## AGENT
  ##==============================================================================



class AgentHandler:
    def get_query(self, context, query, userId): None
    def method_call(self, context, name, object_id, args, userId): None



class Agent(ConnectionHandler):
    def __init__(self, handler, label=""):
        if label == "":
            self._agentLabel = "rb-%s.%d" % (socket.gethostname(), os.getpid())
        else:
            self._agentLabel = label
        self._conn = None
        self._handler = handler
        self.impl = qmfengine.AgentEngine(self._agentLabel)
        self._event = qmfengine.AgentEvent()
        self._xmtMessage = qmfengine.Message()
    
    
    def set_connection(self, conn):
        self._conn = conn
        self._conn.add_conn_handler(self)
    
    
    def register_class(self, cls):
        self.impl.registerClass(cls.impl)
    
    
    def alloc_object_id(self, low = 0, high = 0):
        return ObjectId(self.impl.allocObjectId(low, high))
    
    
    def query_response(self, context, obj):
        self.impl.queryResponse(context, obj.impl)
    
    
    def query_complete(self, context):
        self.impl.queryComplete(context)
    
    
    def method_response(self, context, status, text, arguments):
        self.impl.methodResponse(context, status, text, arguments.map)
    
    
    def do_agent_events(self):
        count = 0
        valid = self.impl.getEvent(self._event)
        while valid:
            count += 1
            if self._event.kind == qmfengine.AgentEvent.GET_QUERY:
                self._handler.get_query(self._event.sequence,
                                        Query(self._event.query),
                                        self._event.authUserId)
                
            elif self._event.kind == qmfengine.AgentEvent.START_SYNC:
                pass
            elif self._event.kind == qmfengine.AgentEvent.END_SYNC:
                pass
            elif self._event.kind == qmfengine.AgentEvent.METHOD_CALL:
                args = Arguments(self._event.arguments)
                self._handler.method_call(self._event.sequence, self._event.name,
                                          ObjectId(self._event.objectId),
                                          args, self._event.authUserId)
                
            elif self._event.kind == qmfengine.AgentEvent.DECLARE_QUEUE:
                self._conn.impl.declareQueue(self._session.handle, self._event.name)
                
            elif self._event.kind == qmfengine.AgentEvent.DELETE_QUEUE:
                self._conn.impl.deleteQueue(self._session.handle, self._event.name)
                
            elif self._event.kind == qmfengine.AgentEvent.BIND:
                self._conn.impl.bind(self._session.handle, self._event.exchange,
                                     self._event.name, self._event.bindingKey)
                
            elif self._event.kind == qmfengine.AgentEvent.UNBIND:
                self._conn.impl.unbind(self._session.handle, self._event.exchange,
                                       self._event.name, self._event.bindingKey)
                
            elif self._event.kind == qmfengine.AgentEvent.SETUP_COMPLETE:
                self.impl.startProtocol()
                
            self.impl.popEvent()
            valid = self.impl.getEvent(self._event)
        return count
    
    
    def do_agent_messages(self):
        count = 0
        valid = self.impl.getXmtMessage(self._xmtMessage)
        while valid:
            count += 1
            self._conn.impl.sendMessage(self._session.handle, self._xmtMessage)
            self.impl.popXmt()
            valid = self.impl.getXmtMessage(self._xmtMessage)
        return count
    
    
    def do_events(self):
        while True:
            ecnt = self.do_agent_events()
            mcnt = self.do_agent_messages()
            if ecnt == 0 and mcnt == 0:
                break
    
    
    def conn_event_connected(self):
        print "Agent Connection Established..."
        self._session = Session(self._conn,
                                "qmfa-%s.%d" % (socket.gethostname(), os.getpid()),
                                self)
        self.impl.newSession()
        self.do_events()
    
    
    def conn_event_disconnected(self, error):
        print "Agent Connection Lost"
        pass
    
    
    def sess_event_session_closed(self, context, error):
        print "Agent Session Lost"
        pass
    
    
    def sess_event_recv(self, context, message):
        self.impl.handleRcvMessage(message)
        self.do_events()


