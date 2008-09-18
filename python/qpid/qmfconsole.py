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

""" Console API for Qpid Management Framework """

import os
import qpid
import struct
import socket
from qpid.peer       import Closed
from qpid.connection import Connection, ConnectionFailed
from qpid.datatypes  import uuid4
from qpid.util       import connect
from datatypes       import Message, RangedSet
from threading       import Lock, Condition
from codec010        import StringCodec as Codec
from time            import time
from cStringIO       import StringIO

class Console:
  """ To access the asynchronous operations, a class must be derived from
  Console with overrides of any combination of the available methods. """

  def newPackage(self, name):
    """ Invoked when a QMF package is discovered. """
    pass

  def newClass(self, classKey):
    """ Invoked when a new class is discovered.  Session.getSchema can be
    used to obtain details about the class."""
    pass

  def newAgent(self, broker, agent):
    """ Invoked when a QMF agent is discovered. """
    pass

  def objectProps(self, broker, id, record):
    """ Invoked when an object is updated. """
    pass

  def objectStats(self, broker, id, record):
    """ Invoked when an object is updated. """
    pass

  def event(self, broker, event):
    """ Invoked when an event is raised. """
    pass

  def heartbeat(self, agent, timestamp):
    """ """
    pass

  def brokerInfo(self, broker):
    """ """
    pass

class Session:
  """
  An instance of the Session class represents a console session running
  against one or more QMF brokers.  A single instance of Session is needed
  to interact with the management framework as a console.
  """
  _CONTEXT_SYNC     = 1
  _CONTEXT_STARTUP  = 2
  _CONTEXT_MULTIGET = 3

  GET_WAIT_TIME = 10

  def __init__(self, console=None):
    """
    Initialize a session.  If the console argument is provided, the
    more advanced asynchronous features are available.  If console is
    defaulted, the session will operate in a simpler, synchronous manner.
    """
    self.console          = console
    self.brokers          = []
    self.packages         = {}
    self.seqMgr           = SequenceManager()
    self.cv               = Condition()
    self.syncSequenceList = []
    self.getResult        = []

  def addBroker(self, host="localhost", port=5672,
                authMech="PLAIN", authName="guest", authPass="guest"):
    """ Connect to a Qpid broker.  Returns an object of type Broker. """
    broker = Broker(self, host, port, authMech, authName, authPass)
    self.brokers.append(broker)
    return broker

  def delBroker(self, broker):
    """ Disconnect from a broker.  The 'broker' argument is the object
    returned from the addBroker call """
    broker._shutdown()
    self.brokers.remove(broker)
    del broker

  def getPackages(self):
    """ Get the list of known QMF packages """
    for broker in self.brokers:
      broker._waitForStable()
    list = []
    for package in self.packages:
      list.append(package)
    return list

  def getClasses(self, packageName):
    """ Get the list of known classes within a QMF package """
    for broker in self.brokers:
      broker._waitForStable()
    list = []
    if packageName in self.packages:
      for cname, hash in self.packages[packageName]:
        list.append((packageName, cname, hash))
    return list

  def getSchema(self, classKey):
    """ Get the schema for a QMF class """
    for broker in self.brokers:
      broker._waitForStable()
    pname, cname, hash = classKey
    if pname in self.packages:
      if (cname, hash) in self.packages[pname]:
        return self.packages[pname][(cname, hash)]

  def getAgents(self):
    """ Get a list of currently known agents """
    for broker in self.brokers:
      broker._waitForStable()
    pass

  def getObjects(self, **kwargs):
    """ Get a list of objects from QMF agents.
    All arguments are passed by name(keyword).

    The class for queried objects may be specified in one of the following ways:

    schema = <schema> - supply a schema object returned from getSchema
    key = <key>       - supply a classKey from the list returned by getClasses
    name = <name>     - supply a class name as a string

    If objects should be obtained from only one agent, use the following argument.
    Otherwise, the query will go to all agents.

    agent = <agent> - supply an agent from the list returned by getAgents

    If the get query is to be restricted to one broker (as opposed to all connected brokers),
    add the following argument:

    broker = <broker> - supply a broker as returned by addBroker
    """
    if "broker" in kwargs:
      brokerList = [].append(kwargs["broker"])
    else:
      brokerList = self.brokers
    for broker in brokerList:
      broker._waitForStable()

    agentList = []
    if "agent" in kwargs:
      agent = kwargs["agent"]
      if agent.broker not in brokerList:
        raise Exception("Supplied agent is not accessible through the supplied broker")
      agentList = append(agent)
    else:
      for broker in brokerList:
        for agent in broker.getAgents():
          agentList.append(agent)

    cname = None
    if   "schema" in kwargs: pname, cname, hash = kwargs["schema"].getKey()
    elif "key"    in kwargs: pname, cname, hash = kwargs["key"]
    elif "name"   in kwargs: pname, cname, hash = None, kwargs["name"], None
    if cname == None:
      raise Exception("No class supplied, use 'schema', 'key', or 'name' argument")
    map = {}
    map["_class"] = cname
    if pname != None: map["_package"] = pname
    if hash  != None: map["_hash"]    = hash

    self.getResult = []
    for agent in agentList:
      broker = agent.broker
      sendCodec = Codec(broker.conn.spec)
      self.cv.acquire()
      seq = self.seqMgr._reserve(self._CONTEXT_MULTIGET)
      self.syncSequenceList.append(seq)
      self.cv.release()
      broker._setHeader(sendCodec, 'G', seq)
      sendCodec.write_map(map)
      smsg = broker._message(sendCodec.encoded, "agent.%d" % agent.bank)
      broker._send(smsg)

    starttime = time()
    timeout = False
    self.cv.acquire()
    while len(self.syncSequenceList) > 0:
      self.cv.wait(self.GET_WAIT_TIME)
      if time() - starttime > self.GET_WAIT_TIME:
        for pendingSeq in self.syncSequenceList:
          self.seqMgr._release(pendingSeq)
        self.syncSequenceList = []
        timeout = True
    self.cv.release()

    if len(self.getResult) == 0 and timeout:
      raise RuntimeError("No agent responded within timeout period")
    return self.getResult

  def setEventFilter(self, **kwargs):
    """ """
    pass

  def _handleBrokerResp(self, broker, codec, seq):
    broker.brokerId = codec.read_uuid()
    if self.console != None:
      self.console.brokerInfo(broker)

    # Send a package request
    # (effectively inc and dec outstanding by not doing anything)
    sendCodec = Codec(broker.conn.spec)
    seq = self.seqMgr._reserve(self._CONTEXT_STARTUP)
    broker._setHeader(sendCodec, 'P', seq)
    smsg = broker._message(sendCodec.encoded)
    broker._send(smsg)

  def _handlePackageInd(self, broker, codec, seq):
    pname = str(codec.read_str8())
    self.cv.acquire()
    if pname not in self.packages:
      self.packages[pname] = {}
      self.cv.release()
      if self.console != None:
        self.console.newPackage(pname)
    else:
      self.cv.release()

    # Send a class request
    broker._incOutstanding()
    sendCodec = Codec(broker.conn.spec)
    seq = self.seqMgr._reserve(self._CONTEXT_STARTUP)
    broker._setHeader(sendCodec, 'Q', seq)
    sendCodec.write_str8(pname)
    smsg = broker._message(sendCodec.encoded)
    broker._send(smsg)

  def _handleCommandComplete(self, broker, codec, seq):
    code = codec.read_uint32()
    text = str(codec.read_str8())
    context = self.seqMgr._release(seq)
    if context == self._CONTEXT_STARTUP:
      broker._decOutstanding()
    elif context == self._CONTEXT_SYNC and seq == broker.syncSequence:
      broker.cv.acquire()
      broker.syncInFlight = False
      broker.cv.notify()
      broker.cv.release()
    elif context == self._CONTEXT_MULTIGET and seq in self.syncSequenceList:
      self.cv.acquire()
      self.syncSequenceList.remove(seq)
      if len(self.syncSequenceList) == 0:
        self.cv.notify()
      self.cv.release()

  def _handleClassInd(self, broker, codec, seq):
    pname = str(codec.read_str8())
    cname = str(codec.read_str8())
    hash  = codec.read_bin128()

    self.cv.acquire()
    if pname not in self.packages:
      self.cv.release()
      return
    if (cname, hash) not in self.packages[pname]:
      # Send a schema request for the unknown class
      self.cv.release()
      broker._incOutstanding()
      sendCodec = Codec(broker.conn.spec)
      seq = self.seqMgr._reserve(self._CONTEXT_STARTUP)
      broker._setHeader(sendCodec, 'S', seq)
      sendCodec.write_str8(pname)
      sendCodec.write_str8(cname)
      sendCodec.write_bin128(hash)
      smsg = broker._message(sendCodec.encoded)
      broker._send(smsg)
    else:
      self.cv.release()

  def _handleMethodResp(self, broker, codec, seq):
    code = codec.read_uint32()
    text = str(codec.read_str8())
    outArgs = {}
    obj, method = self.seqMgr._release(seq)
    if code == 0:
      for arg in method.arguments:
        if arg.dir.find("O") != -1:
          outArgs[arg.name] = obj._decodeValue(codec, arg.type)
    broker.cv.acquire()
    broker.syncResult = MethodResult(code, text, outArgs)
    broker.syncInFlight = False
    broker.cv.notify()
    broker.cv.release()

  def _handleHeartbeatInd(self, broker, codec, seq):
    timestamp = codec.read_uint64()
    pass

  def _handleEventInd(self, broker, codec, seq):
    pass

  def _handleSchemaResp(self, broker, codec, seq):
    pname = str(codec.read_str8())
    cname = str(codec.read_str8())
    hash  = codec.read_bin128()
    classKey = (pname, cname, hash)
    _class = SchemaClass(classKey, codec)
    self.cv.acquire()
    self.packages[pname][(cname, hash)] = _class
    self.cv.release()
    broker._decOutstanding()
    if self.console != None:
      self.console.newClass(classKey)

  def _handleContentInd(self, broker, codec, seq, prop=False, stat=False):
    pname = str(codec.read_str8())
    cname = str(codec.read_str8())
    hash  = codec.read_bin128()
    classKey = (pname, cname, hash)
    self.cv.acquire()
    if pname not in self.packages:
      self.cv.release()
      return
    if (cname, hash) not in self.packages[pname]:
      self.cv.release()
      return
    self.cv.release()
    schema = self.packages[pname][(cname, hash)]
    object = Object(self, broker, schema, codec, prop, stat)

    self.cv.acquire()
    if seq in self.syncSequenceList:
      self.getResult.append(object)
      self.cv.release()
      return
    self.cv.release()

    if self.console != None:
      if prop:
        self.console.objectProps(broker, object.getObjectId(), object)
      if stat:
        self.console.objectStats(broker, object.getObjectId(), object)

class Package:
  """ """
  def __init__(self, name):
    self.name = name

class ClassKey:
  """ """
  def __init__(self, package, className, hash):
    self.package = package
    self.className = className
    self.hash = hash

class SchemaClass:
  """ """
  def __init__(self, key, codec):
    self.classKey = key
    self.properties = []
    self.statistics = []
    self.methods    = []
    self.events     = []

    propCount   = codec.read_uint16()
    statCount   = codec.read_uint16()
    methodCount = codec.read_uint16()
    eventCount  = codec.read_uint16()

    for idx in range(propCount):
      self.properties.append(SchemaProperty(codec))
    for idx in range(statCount):
      self.statistics.append(SchemaStatistic(codec))
    for idx in range(methodCount):
      self.methods.append(SchemaMethod(codec))
    for idx in range(eventCount):
      self.events.append(SchemaEvent(codec))

  def __repr__(self):
    pname, cname, hash = self.classKey
    result = "Class: %s:%s " % (pname, cname)
    result += "(%08x-%04x-%04x-%04x-%04x%08x)" % struct.unpack ("!LHHHHL", hash)
    return result

  def getKey(self):
    """ """
    return self.classKey

  def getProperties(self):
    """ """
    return self.properties

  def getStatistics(self):
    """ """
    return self.statistics

  def getMethods(self):
    """ """
    return self.methods

  def getEvents(self):
    """ """
    return self.events

class SchemaProperty:
  """ """
  def __init__(self, codec):
    map = codec.read_map()
    self.name     = str(map["name"])
    self.type     = map["type"]
    self.access   = map["access"]
    self.index    = map["index"] != 0
    self.optional = map["optional"] != 0
    self.unit     = None
    self.min      = None
    self.max      = None
    self.maxlan   = None
    self.desc     = None

    for key, value in map.items():
      if   key == "unit"   : self.unit   = str(value)
      elif key == "min"    : self.min    = value
      elif key == "max"    : self.max    = value
      elif key == "maxlen" : self.maxlen = value
      elif key == "desc"   : self.desc   = str(value)

class SchemaStatistic:
  """ """
  def __init__(self, codec):
    map = codec.read_map()
    self.name     = str(map["name"])
    self.type     = map["type"]
    self.unit     = None
    self.desc     = None

    for key, value in map.items():
      if   key == "unit" : self.unit = str(value)
      elif key == "desc" : self.desc = str(value)

class SchemaMethod:
  """ """
  def __init__(self, codec):
    map = codec.read_map()
    self.name = str(map["name"])
    argCount  = map["argCount"]
    if "desc" in map:
      self.desc = str(map["desc"])
    else:
      self.desc = None
    self.arguments = []

    for idx in range(argCount):
      self.arguments.append(SchemaArgument(codec, methodArg=True))

class SchemaEvent:
  """ """
  def __init__(self, codec):
    map = codec.read_map()
    self.name = str(map["name"])
    argCount  = map["argCount"]
    if "desc" in map:
      self.desc = str(map["desc"])
    else:
      self.desc = None
    self.arguments = []

    for idx in range(argCount):
      self.arguments.append(SchemaArgument(codec, methodArg=False))

class SchemaArgument:
  """ """
  def __init__(self, codec, methodArg):
    map = codec.read_map()
    self.name    = str(map["name"])
    self.type    = map["type"]
    if methodArg:
      self.dir   = str(map["dir"].upper())
    self.unit    = None
    self.min     = None
    self.max     = None
    self.maxlen  = None
    self.desc    = None
    self.default = None

    for key, value in map.items():
      if   key == "unit"    : self.unit    = str(value)
      elif key == "min"     : self.min     = value
      elif key == "max"     : self.max     = value
      elif key == "maxlen"  : self.maxlen  = value
      elif key == "desc"    : self.desc    = str(value)
      elif key == "default" : self.default = str(value)

class ObjectId(object):
  """ Object that represents QMF object identifiers """
  def __init__(self, codec, first=0, second=0):
    if codec:
      self.first  = codec.read_uint64()
      self.second = codec.read_uint64()
    else:
      self.first = first
      self.second = second

  def __cmp__(self, other):
    if other == None:
      return 1
    if self.first < other.first:
      return -1
    if self.first > other.first:
      return 1
    if self.second < other.second:
      return -1
    if self.second > other.second:
      return 1
    return 0

  def __repr__(self):
    return "%08x-%04x-%04x-%04x-%04x%08x" % ((self.first  & 0xFFFFFFFF00000000) >> 32,
                                             (self.first  & 0x00000000FFFF0000) >> 16,
                                             (self.first  & 0x000000000000FFFF),
                                             (self.second & 0xFFFF000000000000) >> 48,
                                             (self.second & 0x0000FFFF00000000) >> 32,
                                             (self.second & 0x00000000FFFFFFFF))

  def index(self):
    return (self.first, self.second)

  def getFlags(self):
    return (self.first & 0xF000000000000000) >> 60

  def getSequence(self):
    return (self.first & 0x0FFF000000000000) >> 48

  def getBroker(self):
    return (self.first & 0x0000FFFFF0000000) >> 28

  def getBank(self):
    return self.first & 0x000000000FFFFFFF

  def getObject(self):
    return self.second

  def isDurable(self):
    return self.getSequence() == 0

  def encode(self, codec):
    codec.write_uint64(self.first)
    codec.write_uint64(self.second)


class Object(object):
  """ """
  def __init__(self, session, broker, schema, codec, prop, stat):
    """ """
    self.session = session
    self.broker  = broker
    self.schema  = schema
    self.currentTime = codec.read_uint64()
    self.createTime  = codec.read_uint64()
    self.deleteTime  = codec.read_uint64()
    self.objectId    = ObjectId(codec)
    self.properties  = []
    self.statistics  = []
    if prop:
      notPresent = self._parsePresenceMasks(codec, schema)
      for property in schema.getProperties():
        if property.name in notPresent:
          self.properties.append((property, None))
        else:
          self.properties.append((property, self._decodeValue(codec, property.type)))
    if stat:
      for statistic in schema.getStatistics():
        self.statistics.append((statistic, self._decodeValue(codec, statistic.type)))

  def getObjectId(self):
    """ """
    return self.objectId

  def getClassKey(self):
    """ """
    return self.schema.getKey()

  def getSchema(self):
    """ """
    return self.schema

  def getTimestamps(self):
    """ """
    return self.currentTime, self.createTime, self.deleteTime

  def getIndex(self):
    """ """
    result = ""
    for property, value in self.properties:
      if property.index:
        if result != "":
          result += ":"
        result += str(value)
    return result

  def __repr__(self):
    return self.getIndex()

  def __getattr__(self, name):
    for method in self.schema.getMethods():
      if name == method.name:
        return lambda *args, **kwargs : self._invoke(name, args, kwargs)
    for property, value in self.properties:
      if name == property.name:
        return value
    for statistic, value in self.statistics:
      if name == statistic.name:
        return value

  def _invoke(self, name, args, kwargs):
    for method in self.schema.getMethods():
      if name == method.name:
        aIdx = 0
        sendCodec = Codec(self.broker.conn.spec)
        seq = self.session.seqMgr._reserve((self, method))
        self.broker._setHeader(sendCodec, 'M', seq)
        self.objectId.encode(sendCodec)
        pname, cname, hash = self.schema.getKey()
        sendCodec.write_str8(pname)
        sendCodec.write_str8(cname)
        sendCodec.write_bin128(hash)
        sendCodec.write_str8(name)
        for arg in method.arguments:
          if arg.dir.find("I") != -1:
            self._encodeValue(sendCodec, args[aIdx], arg.type)
            aIdx += 1
        smsg = self.broker._message(sendCodec.encoded, "agent." + str(self.objectId.getBank()))
        self.broker._send(smsg)
        self.broker.cv.acquire()
        self.broker.syncInFlight = True
        starttime = time()
        while self.broker.syncInFlight:
          self.broker.cv.wait(self.broker.SYNC_TIME)
          if time() - starttime > self.broker.SYNC_TIME:
            self.broker.cv.release()
            self.session.seqMgr._release(seq)
            raise RuntimeError("Timed out waiting for method to respond")
        self.broker.cv.release()
        return self.broker.syncResult
      else:
        raise Exception("Invalid Method (software defect)")

  def _parsePresenceMasks(self, codec, schema):
    excludeList = []
    bit = 0
    for property in schema.getProperties():
      if property.optional:
        if bit == 0:
          mask = codec.read_uint8()
          bit = 1
        if (mask & bit) == 0:
          excludeList.append(property.name)
        bit *= 2
        if bit == 256:
          bit = 0
    return excludeList

  def _decodeValue(self, codec, typecode):
    """ Decode, from the codec, a value based on its typecode. """
    if   typecode == 1:  data = codec.read_uint8()     # U8
    elif typecode == 2:  data = codec.read_uint16()    # U16
    elif typecode == 3:  data = codec.read_uint32()    # U32
    elif typecode == 4:  data = codec.read_uint64()    # U64
    elif typecode == 6:  data = str(codec.read_str8()) # SSTR
    elif typecode == 7:  data = codec.read_vbin32()    # LSTR
    elif typecode == 8:  data = codec.read_int64()     # ABSTIME
    elif typecode == 9:  data = codec.read_uint64()    # DELTATIME
    elif typecode == 10: data = ObjectId(codec)        # REF
    elif typecode == 11: data = codec.read_uint8()     # BOOL
    elif typecode == 12: data = codec.read_float()     # FLOAT
    elif typecode == 13: data = codec.read_double()    # DOUBLE
    elif typecode == 14: data = codec.read_uuid()      # UUID
    elif typecode == 15: data = codec.read_map()       # FTABLE
    elif typecode == 16: data = codec.read_int8()      # S8
    elif typecode == 17: data = codec.read_int16()     # S16
    elif typecode == 18: data = codec.read_int32()     # S32
    elif typecode == 19: data = codec.read_int64()     # S63
    else:
      raise ValueError("Invalid type code: %d" % typecode)
    return data

  def _encodeValue(self, codec, value, typecode):
    """ Encode, into the codec, a value based on its typecode. """
    if   typecode == 1:  codec.write_uint8  (int(value))    # U8
    elif typecode == 2:  codec.write_uint16 (int(value))    # U16
    elif typecode == 3:  codec.write_uint32 (long(value))   # U32
    elif typecode == 4:  codec.write_uint64 (long(value))   # U64
    elif typecode == 6:  codec.write_str8   (value)         # SSTR
    elif typecode == 7:  codec.write_vbin32 (value)         # LSTR
    elif typecode == 8:  codec.write_int64  (long(value))   # ABSTIME
    elif typecode == 9:  codec.write_uint64 (long(value))   # DELTATIME
    elif typecode == 10: value.encode       (codec)         # REF
    elif typecode == 11: codec.write_uint8  (int(value))    # BOOL
    elif typecode == 12: codec.write_float  (float(value))  # FLOAT
    elif typecode == 13: codec.write_double (double(value)) # DOUBLE
    elif typecode == 14: codec.write_uuid   (value)         # UUID
    elif typecode == 15: codec.write_map    (value)         # FTABLE
    elif typecode == 16: codec.write_int8   (int(value))    # S8
    elif typecode == 17: codec.write_int16  (int(value))    # S16
    elif typecode == 18: codec.write_int32  (int(value))    # S32
    elif typecode == 19: codec.write_int64  (int(value))    # S64
    else:
      raise ValueError ("Invalid type code: %d" % typecode)

class MethodResult(object):
  """ """
  def __init__(self, status, text, outArgs):
    """ """
    self.status  = status
    self.text    = text
    self.outArgs = outArgs

  def __getattr__(self, name):
    if name in self.outArgs:
      return self.outArgs[name]

  def __repr__(self):
    return "%s (%d) - %s" % (self.text, self.status, self.outArgs)

class Broker:
  """ """
  SYNC_TIME         = 10

  def __init__(self, session, host, port, authMech, authUser, authPass):
    self.session = session
    self.agents = []
    self.agents.append(Agent(self, 0))
    self.topicBound = False
    self.cv = Condition()
    self.syncInFlight = False
    self.syncRequest = 0
    self.syncResult = None
    self.reqsOutstanding = 1
    self.brokerId  = None
    err = None
    try:
      self.amqpSessionId = "%s.%d" % (os.uname()[1], os.getpid())
      self.conn = Connection(connect(host, port), username=authUser, password=authPass)
      self.conn.start()
      self.replyName = "reply-%s" % self.amqpSessionId
      self.amqpSession = self.conn.session(self.amqpSessionId)
      self.amqpSession.auto_sync = False
      self.amqpSession.queue_declare(queue=self.replyName, exclusive=True, auto_delete=True)
      self.amqpSession.exchange_bind(exchange="amq.direct",
                                 queue=self.replyName, binding_key=self.replyName)
      self.amqpSession.message_subscribe(queue=self.replyName, destination="rdest")
      self.amqpSession.incoming("rdest").listen(self._replyCb, self._exceptionCb)
      self.amqpSession.message_set_flow_mode(destination="rdest", flow_mode=1)
      self.amqpSession.message_flow(destination="rdest", unit=0, value=0xFFFFFFFF)
      self.amqpSession.message_flow(destination="rdest", unit=1, value=0xFFFFFFFF)

      if self.session.console != None:
        self.topicName = "topic-%s" % self.amqpSessionId
        self.amqpSession.queue_declare(queue=self.topicName, exclusive=True, auto_delete=True)
        self.amqpSession.message_subscribe(queue=self.topicName, destination="tdest")
        self.amqpSession.incoming("tdest").listen(self._replyCb)
        self.amqpSession.message_set_flow_mode(destination="tdest", flow_mode=1)
        self.amqpSession.message_flow(destination="tdest", unit=0, value=0xFFFFFFFF)
        self.amqpSession.message_flow(destination="tdest", unit=1, value=0xFFFFFFFF)

      codec = Codec(self.conn.spec)
      self._setHeader(codec, 'B')
      msg = self._message(codec.encoded)
      self._send(msg)

    except socket.error, e:
      err = "Socket Error %s - %s" % (e[0], e[1])
    except Closed, e:
      err = "Connect Failed %d - %s" % (e[0], e[1])
    except ConnectionFailed, e:
      err = "Connect Failed %d - %s" % (e[0], e[1])

    if err != None:
      raise Exception(err)

  def getBrokerId(self):
    """ Get broker's unique identifier (UUID) """
    return self.brokerId

  def getSessionId(self):
    """ Get the identifier of the AMQP session to the broker """
    return self.amqpSessionId

  def getAgents(self):
    """ Get the list of agents reachable via this broker """
    return self.agents

  def _setHeader(self, codec, opcode, seq=0):
    """ Compose the header of a management message. """
    codec.write_uint8(ord('A'))
    codec.write_uint8(ord('M'))
    codec.write_uint8(ord('1'))
    codec.write_uint8(ord(opcode))
    codec.write_uint32(seq)

  def _checkHeader(self, codec):
    """ Check the header of a management message and extract the opcode and class. """
    octet = chr(codec.read_uint8())
    if octet != 'A':
      return None, None
    octet = chr(codec.read_uint8())
    if octet != 'M':
      return None, None
    octet = chr(codec.read_uint8())
    if octet != '1':
      return None, None
    opcode = chr(codec.read_uint8())
    seq    = codec.read_uint32()
    return opcode, seq

  def _message (self, body, routing_key="broker"):
    dp = self.amqpSession.delivery_properties()
    dp.routing_key = routing_key
    mp = self.amqpSession.message_properties()
    mp.content_type = "x-application/qmf"
    mp.reply_to = self.amqpSession.reply_to("amq.direct", self.replyName)
    return Message(dp, mp, body)

  def _send(self, msg, dest="qpid.management"):
    self.amqpSession.message_transfer(destination=dest, message=msg)

  def _shutdown(self):
    self.amqpSession.incoming("rdest").stop()
    if self.session.console != None:
      self.amqpSession.incoming("tdest").stop()
    self.amqpSession.close()

  def _waitForStable(self):
    self.cv.acquire()
    if self.reqsOutstanding == 0:
      self.cv.release()
      return
    self.syncInFlight = True
    starttime = time()
    while self.reqsOutstanding != 0:
      self.cv.wait(self.SYNC_TIME)
      if time() - starttime > self.SYNC_TIME:
        self.cv.release()
        raise RuntimeError("Timed out waiting for broker to synchronize")
    self.cv.release()

  def _incOutstanding(self):
    self.cv.acquire()
    self.reqsOutstanding += 1
    self.cv.release()

  def _decOutstanding(self):
    self.cv.acquire()
    self.reqsOutstanding -= 1
    if self.reqsOutstanding == 0 and not self.topicBound and self.session.console != None:
      self.topicBound = True
      self.amqpSession.exchange_bind(exchange="qpid.management", queue=self.topicName, binding_key="mgmt.#")
    if self.reqsOutstanding == 0 and self.syncInFlight:
      self.syncInFlight = False
      self.cv.notify()
    self.cv.release()

  def _replyCb(self, msg):
    codec = Codec(self.conn.spec, msg.body)
    opcode, seq = self._checkHeader(codec)
    if opcode == None:
      return
    if   opcode == 'b': self.session._handleBrokerResp      (self, codec, seq)
    elif opcode == 'p': self.session._handlePackageInd      (self, codec, seq)
    elif opcode == 'z': self.session._handleCommandComplete (self, codec, seq)
    elif opcode == 'q': self.session._handleClassInd        (self, codec, seq)
    elif opcode == 'm': self.session._handleMethodResp      (self, codec, seq)
    elif opcode == 'h': self.session._handleHeartbeatInd    (self, codec, seq)
    elif opcode == 'e': self.session._handleEventInd        (self, codec, seq)
    elif opcode == 's': self.session._handleSchemaResp      (self, codec, seq)
    elif opcode == 'c': self.session._handleContentInd      (self, codec, seq, prop=True)
    elif opcode == 'i': self.session._handleContentInd      (self, codec, seq, stat=True)
    elif opcode == 'g': self.session._handleContentInd      (self, codec, seq, prop=True, stat=True)

  def _exceptionCb(self, data):
    pass

class Agent:
  """ """
  def __init__(self, broker, bank):
    self.broker = broker
    self.bank   = bank

class Event:
  """ """
  def __init__(self):
    pass

class SequenceManager:
  """ Manage sequence numbers for asynchronous method calls """
  def __init__(self):
    self.lock     = Lock()
    self.sequence = 0
    self.pending  = {}

  def _reserve(self, data):
    """ Reserve a unique sequence number """
    self.lock.acquire()
    result = self.sequence
    self.sequence = self.sequence + 1
    self.pending[result] = data
    self.lock.release()
    return result

  def _release(self, seq):
    """ Release a reserved sequence number """
    data = None
    self.lock.acquire()
    if seq in self.pending:
      data = self.pending[seq]
      del self.pending[seq]
    self.lock.release()
    return data


# TEST

#c = Console()
#s = Session(c)
#b = s.addBroker()
#cl = s.getClasses("org.apache.qpid.broker")
#sch = s.getSchema(cl[0])

