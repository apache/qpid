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
import platform
import qpid
import struct
import socket
import re
import sys
from qpid.datatypes  import UUID
from qpid.datatypes  import timestamp
from qpid.datatypes  import datetime
from qpid.exceptions import Closed
from qpid.session    import SessionDetached
from qpid.connection import Connection, ConnectionFailed, Timeout
from qpid.datatypes  import Message, RangedSet, UUID
from qpid.util       import connect, ssl, URL
from qpid.codec010   import StringCodec as Codec
from threading       import Lock, Condition, Thread, Semaphore
from Queue           import Queue, Empty
from time            import time, strftime, gmtime, sleep
from cStringIO       import StringIO

#import qpid.log
#qpid.log.enable(name="qpid.io.cmd", level=qpid.log.DEBUG)

#===================================================================================================
# CONSOLE
#===================================================================================================
class Console:
  """ To access the asynchronous operations, a class must be derived from
  Console with overrides of any combination of the available methods. """

  def brokerConnected(self, broker):
    """ Invoked when a connection is established to a broker """
    pass

  def brokerConnectionFailed(self, broker):
    """ Invoked when a connection to a broker fails """
    pass

  def brokerDisconnected(self, broker):
    """ Invoked when the connection to a broker is lost """
    pass

  def newPackage(self, name):
    """ Invoked when a QMF package is discovered. """
    pass

  def newClass(self, kind, classKey):
    """ Invoked when a new class is discovered.  Session.getSchema can be
    used to obtain details about the class."""
    pass

  def newAgent(self, agent):
    """ Invoked when a QMF agent is discovered. """
    pass

  def delAgent(self, agent):
    """ Invoked when a QMF agent disconects. """
    pass

  def objectProps(self, broker, record):
    """ Invoked when an object is updated. """
    pass

  def objectStats(self, broker, record):
    """ Invoked when an object is updated. """
    pass

  def event(self, broker, event):
    """ Invoked when an event is raised. """
    pass

  def heartbeat(self, agent, timestamp):
    """ Invoked when an agent heartbeat is received. """
    pass

  def brokerInfo(self, broker):
    """ Invoked when the connection sequence reaches the point where broker information is available. """
    pass

  def methodResponse(self, broker, seq, response):
    """ Invoked when a method response from an asynchronous method call is received. """
    pass


#===================================================================================================
# BrokerURL
#===================================================================================================
class BrokerURL(URL):
  def __init__(self, *args, **kwargs):
    URL.__init__(self, *args, **kwargs)
    if self.port is None:
      if self.scheme == URL.AMQPS:
        self.port = 5671
      else:
        self.port = 5672
    self.authName = None
    self.authPass = None
    if self.user:
      self.authName = str(self.user)
    if self.password:
      self.authPass = str(self.password)

  def name(self):
    return str(self)

  def match(self, host, port):
    return socket.getaddrinfo(self.host, self.port)[0][4] == socket.getaddrinfo(host, port)[0][4]

#===================================================================================================
# Object
#===================================================================================================
class Object(object):
  """
  This class defines a 'proxy' object representing a real managed object on an agent.
  Actions taken on this proxy are remotely affected on the real managed object.
  """
  def __init__(self, agent, schema, codec=None, prop=None, stat=None, v2Map=None, agentName=None, kwargs={}):
    self._agent   = agent
    self._session = None
    self._broker  = None
    if agent:
      self._session = agent.session
      self._broker  = agent.broker
    self._schema  = schema
    self._properties  = []
    self._statistics  = []
    self._currentTime = None
    self._createTime  = None
    self._deleteTime  = 0
    self._objectId    = None
    if v2Map:
      self.v2Init(v2Map, agentName)
      return

    if self._agent:
      self._currentTime = codec.read_uint64()
      self._createTime  = codec.read_uint64()
      self._deleteTime  = codec.read_uint64()
      self._objectId    = ObjectId(codec)
    if codec:
      if prop:
        notPresent = self._parsePresenceMasks(codec, schema)
        for property in schema.getProperties():
          if property.name in notPresent:
            self._properties.append((property, None))
          else:
            self._properties.append((property, self._session._decodeValue(codec, property.type, self._broker)))
      if stat:
        for statistic in schema.getStatistics():
          self._statistics.append((statistic, self._session._decodeValue(codec, statistic.type, self._broker)))
    else:
      for property in schema.getProperties():
        if property.optional:
          self._properties.append((property, None))
        else:
          self._properties.append((property, self._session._defaultValue(property, self._broker, kwargs)))
      for statistic in schema.getStatistics():
          self._statistics.append((statistic, self._session._defaultValue(statistic, self._broker, kwargs)))

  def v2Init(self, omap, agentName):
    if omap.__class__ != dict:
      raise Exception("QMFv2 object data must be a map/dict")
    if '_values' not in omap:
      raise Exception("QMFv2 object must have '_values' element")

    values = omap['_values']
    for prop in self._schema.getProperties():
      if prop.name in values:
        if prop.type == 10: # Reference
          self._properties.append((prop, ObjectId(values[prop.name], agentName=agentName)))
        else:
          self._properties.append((prop, values[prop.name]))
    for stat in self._schema.getStatistics():
      if stat.name in values:
        self._statistics.append((stat, values[stat.name]))
    if '_subtypes' in omap:
      self._subtypes = omap['_subtypes']
    if '_object_id' in omap:
      self._objectId = ObjectId(omap['_object_id'], agentName=agentName)
    else:
      self._objectId = None

    self._currentTime = omap.get("_update_ts", 0)
    self._createTime = omap.get("_create_ts", 0)
    self._deleteTime = omap.get("_delete_ts", 0)

  def getAgent(self):
    """ Return the agent from which this object was sent """
    return self._agent

  def getBroker(self):
    """ Return the broker from which this object was sent """
    return self._broker

  def getV2RoutingKey(self):
    """ Get the QMFv2 routing key to address this object """
    return self._agent.getV2RoutingKey()

  def getObjectId(self):
    """ Return the object identifier for this object """
    return self._objectId

  def getClassKey(self):
    """ Return the class-key that references the schema describing this object. """
    return self._schema.getKey()

  def getSchema(self):
    """ Return the schema that describes this object. """
    return self._schema

  def getMethods(self):
    """ Return a list of methods available for this object. """
    return self._schema.getMethods()

  def getTimestamps(self):
    """ Return the current, creation, and deletion times for this object. """
    return self._currentTime, self._createTime, self._deleteTime

  def isDeleted(self):
    """ Return True iff this object has been deleted. """
    return self._deleteTime != 0

  def isManaged(self):
    """ Return True iff this object is a proxy for a managed object on an agent. """
    return self._objectId and self._agent

  def getIndex(self):
    """ Return a string describing this object's primary key. """
    if self._objectId.isV2:
      return self._objectId.getObject()
    result = u""
    for prop, value in self._properties:
      if prop.index:
        if result != u"":
          result += u":"
        try:
          valstr = unicode(self._session._displayValue(value, prop.type))
        except Exception, e:
          valstr = u"<undecodable>"
        result += valstr
    return result

  def getProperties(self):
    """ Return a list of object properties """
    return self._properties

  def getStatistics(self):
    """ Return a list of object statistics """
    return self._statistics

  def mergeUpdate(self, newer):
    """ Replace properties and/or statistics with a newly received update """
    if not self.isManaged():
      raise Exception("Object is not managed")
    if self._objectId != newer._objectId:
      raise Exception("Objects with different object-ids")
    if len(newer.getProperties()) > 0:
      self._properties = newer.getProperties()
    if len(newer.getStatistics()) > 0:
      self._statistics = newer.getStatistics()
    self._currentTime = newer._currentTime
    self._deleteTime = newer._deleteTime

  def update(self):
    """ Contact the agent and retrieve the lastest property and statistic values for this object. """
    if not self.isManaged():
      raise Exception("Object is not managed")
    obj = self._agent.getObjects(_objectId=self._objectId)
    if obj:
      self.mergeUpdate(obj[0])
    else:
      raise Exception("Underlying object no longer exists")

  def __repr__(self):
    if self.isManaged():
      id = self.getObjectId().__repr__()
    else:
      id = "unmanaged"
    key = self.getClassKey()
    return key.getPackageName() + ":" + key.getClassName() +\
        "[" + id + "] " + self.getIndex().encode("utf8")

  def __getattr__(self, name):
    for method in self._schema.getMethods():
      if name == method.name:
        return lambda *args, **kwargs : self._invoke(name, args, kwargs)
    for prop, value in self._properties:
      if name == prop.name:
        return value
      if name == "_" + prop.name + "_" and prop.type == 10:  # Dereference references
        deref = self._agent.getObjects(_objectId=value)
        if len(deref) != 1:
          return None
        else:
          return deref[0]
    for stat, value in self._statistics:
      if name == stat.name:
        return value

    #
    # Check to see if the name is in the schema.  If so, return None (i.e. this is a not-present attribute)
    #
    for prop in self._schema.getProperties():
      if name == prop.name:
        return None
    for stat in self._schema.getStatistics():
      if name == stat.name:
        return None
    raise Exception("Type Object has no attribute '%s'" % name)

  def __setattr__(self, name, value):
    if name[0] == '_':
      super.__setattr__(self, name, value)
      return

    for prop, unusedValue in self._properties:
      if name == prop.name:
        newprop = (prop, value)
        newlist = []
        for old, val in self._properties:
          if name == old.name:
            newlist.append(newprop)
          else:
            newlist.append((old, val))
        self._properties = newlist
        return
    super.__setattr__(self, name, value)

  def _parseDefault(self, typ, val):
    try:
      if typ in (2, 3, 4): # 16, 32, 64 bit numbers
        val = int(val, 0)
      elif typ == 11: # bool
        val = val.lower() in ("t", "true", "1", "yes", "y")
      elif typ == 15: # map
        val = eval(val)
    except:
      pass
    return val

  def _handleDefaultArguments(self, method, args, kwargs):
    count = len([x for x in method.arguments if x.dir.find("I") != -1])
    for kwarg in kwargs.keys():
      if not [x for x in method.arguments if x.dir.find("I") != -1 and \
                 x.name == kwarg]:
        del kwargs[kwarg]

    # If there were not enough args supplied, add any defaulted arguments
    # from the schema (starting at the end) until we either get enough
    # arguments or run out of defaults
    while count > len(args) + len(kwargs):
      for arg in reversed(method.arguments):
        if arg.dir.find("I") != -1 and getattr(arg, "default") is not None and \
                arg.name not in kwargs:
          # add missing defaulted value to the kwargs dict
          kwargs[arg.name] = self._parseDefault(arg.type, arg.default)
          break
      else:
        # no suitable defaulted args found, end the while loop
        break

    return count

  def _sendMethodRequest(self, name, args, kwargs, synchronous=False, timeWait=None):
    for method in self._schema.getMethods():
      if name == method.name:
        aIdx = 0
        sendCodec = Codec()
        seq = self._session.seqMgr._reserve((method, synchronous))

        count = self._handleDefaultArguments(method, args, kwargs)
        if count != len(args) + len(kwargs):
          raise Exception("Incorrect number of arguments: expected %d, got %d" % (count, len(args) + len(kwargs)))

        if self._agent.isV2:
          #
          # Compose and send a QMFv2 method request
          #
          call = {}
          call['_object_id'] = self._objectId.asMap()
          call['_method_name'] = name
          argMap = {}
          for arg in method.arguments:
            if arg.dir.find("I") != -1:
              # If any kwargs match this schema arg, insert them in the proper place
              if arg.name in kwargs:
                argMap[arg.name] = kwargs[arg.name]
              elif aIdx < len(args):
                argMap[arg.name] = args[aIdx]
                aIdx += 1
          call['_arguments'] = argMap

          dp = self._broker.amqpSession.delivery_properties()
          dp.routing_key = self.getV2RoutingKey()
          mp = self._broker.amqpSession.message_properties()
          mp.content_type = "amqp/map"
          if self._broker.saslUser:
            mp.user_id = self._broker.saslUser
          mp.correlation_id = str(seq)
          mp.app_id = "qmf2"
          mp.reply_to = self._broker.amqpSession.reply_to("qmf.default.direct", self._broker.v2_direct_queue)
          mp.application_headers = {'qmf.opcode':'_method_request'}
          sendCodec.write_map(call)
          smsg = Message(dp, mp, sendCodec.encoded)
          exchange = "qmf.default.direct"

        else:
          #
          # Associate this sequence with the agent hosting the object so we can correctly
          # route the method-response
          #
          agent = self._broker.getAgent(self._broker.getBrokerBank(), self._objectId.getAgentBank())
          self._broker._setSequence(seq, agent)

          #
          # Compose and send a QMFv1 method request
          #
          self._broker._setHeader(sendCodec, 'M', seq)
          self._objectId.encode(sendCodec)
          self._schema.getKey().encode(sendCodec)
          sendCodec.write_str8(name)

          for arg in method.arguments:
            if arg.dir.find("I") != -1:
              self._session._encodeValue(sendCodec, args[aIdx], arg.type)
              aIdx += 1
          smsg = self._broker._message(sendCodec.encoded, "agent.%d.%s" %
                                       (self._objectId.getBrokerBank(), self._objectId.getAgentBank()))
          exchange = "qpid.management"

        if synchronous:
          try:
            self._broker.cv.acquire()
            self._broker.syncInFlight = True
          finally:
            self._broker.cv.release()
        self._broker._send(smsg, exchange)
        return seq
    return None

  def _invoke(self, name, args, kwargs):
    if not self.isManaged():
      raise Exception("Object is not managed")
    if "_timeout" in kwargs:
      timeout = kwargs["_timeout"]
    else:
      timeout = self._broker.SYNC_TIME

    if "_async" in kwargs and kwargs["_async"]:
      sync = False
      if "_timeout" not in kwargs:
        timeout = None
    else:
      sync = True

    # Remove special "meta" kwargs before handing to _sendMethodRequest() to process
    if "_timeout" in kwargs: del kwargs["_timeout"]
    if "_async" in kwargs: del kwargs["_async"]

    seq = self._sendMethodRequest(name, args, kwargs, sync, timeout)
    if seq:
      if not sync:
        return seq
      self._broker.cv.acquire()
      try:
        starttime = time()
        while self._broker.syncInFlight and self._broker.error == None:
          self._broker.cv.wait(timeout)
          if time() - starttime > timeout:
            raise RuntimeError("Timed out waiting for method to respond")
      finally:
        self._session.seqMgr._release(seq)
        self._broker.cv.release()
      if self._broker.error != None:
        errorText = self._broker.error
        self._broker.error = None
        raise Exception(errorText)
      return self._broker.syncResult
    raise Exception("Invalid Method (software defect) [%s]" % name)

  def _encodeUnmanaged(self, codec):
    codec.write_uint8(20) 
    codec.write_str8(self._schema.getKey().getPackageName())
    codec.write_str8(self._schema.getKey().getClassName())
    codec.write_bin128(self._schema.getKey().getHash())

    # emit presence masks for optional properties
    mask = 0
    bit  = 0
    for prop, value in self._properties:
      if prop.optional:
        if bit == 0:
          bit = 1
        if value:
          mask |= bit
        bit = bit << 1
        if bit == 256:
          bit = 0
          codec.write_uint8(mask)
          mask = 0
    if bit != 0:
      codec.write_uint8(mask)    
    
    # encode properties
    for prop, value in self._properties:
      if value != None: 
        self._session._encodeValue(codec, value, prop.type)

    # encode statistics
    for stat, value in self._statistics:
      self._session._encodeValue(codec, value, stat.type)

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


#===================================================================================================
# Session
#===================================================================================================
class Session:
  """
  An instance of the Session class represents a console session running
  against one or more QMF brokers.  A single instance of Session is needed
  to interact with the management framework as a console.
  """
  _CONTEXT_SYNC     = 1
  _CONTEXT_STARTUP  = 2
  _CONTEXT_MULTIGET = 3

  DEFAULT_GET_WAIT_TIME = 60

  ENCODINGS = {
    str: 7,
    timestamp: 8,
    datetime: 8,  
    int: 9,
    long: 9,
    float: 13,
    UUID: 14,      
    Object: 20,    
    list: 21
    }  


  def __init__(self, console=None, rcvObjects=True, rcvEvents=True, rcvHeartbeats=True,
               manageConnections=False, userBindings=False):
    """
    Initialize a session.  If the console argument is provided, the
    more advanced asynchronous features are available.  If console is
    defaulted, the session will operate in a simpler, synchronous manner.

    The rcvObjects, rcvEvents, and rcvHeartbeats arguments are meaningful only if 'console'
    is provided.  They control whether object updates, events, and agent-heartbeats are
    subscribed to.  If the console is not interested in receiving one or more of the above,
    setting the argument to False will reduce tha bandwidth used by the API.

    If manageConnections is set to True, the Session object will manage connections to
    the brokers.  This means that if a broker is unreachable, it will retry until a connection
    can be established.  If a connection is lost, the Session will attempt to reconnect.

    If manageConnections is set to False, the user is responsible for handing failures.  In
    this case, an unreachable broker will cause addBroker to raise an exception.

    If userBindings is set to False (the default) and rcvObjects is True, the console will
    receive data for all object classes.  If userBindings is set to True, the user must select
    which classes the console shall receive by invoking the bindPackage or bindClass methods.
    This allows the console to be configured to receive only information that is relavant to
    a particular application.  If rcvObjects id False, userBindings has no meaning.
    """
    self.console           = console
    self.brokers           = []
    self.schemaCache       = SchemaCache()
    self.seqMgr            = SequenceManager()
    self.cv                = Condition()
    self.syncSequenceList  = []
    self.getResult         = []
    self.getSelect         = []
    self.error             = None
    self.rcvObjects        = rcvObjects
    self.rcvEvents         = rcvEvents
    self.rcvHeartbeats     = rcvHeartbeats
    self.userBindings      = userBindings
    if self.console == None:
      self.rcvObjects    = False
      self.rcvEvents     = False
      self.rcvHeartbeats = False
    self.v1BindingKeyList, self.v2BindingKeyList = self._bindingKeys()
    self.manageConnections = manageConnections
    # callback filters:
    self.agent_filter = []  # (vendor, product, instance) || v1-agent-label-str
    self.class_filter = []  # (pkg, class)
    self.event_filter = []  # (pkg, event)
    self.agent_heartbeat_min = 10 # minimum agent heartbeat timeout interval
    self.agent_heartbeat_miss = 3 # # of heartbeats to miss before deleting agent

    if self.userBindings and not self.console:
      raise Exception("userBindings can't be set unless a console is provided.")

  def close(self):
    """ Releases all resources held by the session.  Must be called by the
    application when it is done with the Session object.
    """
    self.cv.acquire()
    try:
      while len(self.brokers):
        b = self.brokers.pop()
        try:
          b._shutdown()
        except:
          pass
    finally:
      self.cv.release()

  def _getBrokerForAgentAddr(self, agent_addr):
    try:
      self.cv.acquire()
      key = (1, agent_addr)
      for b in self.brokers:
        if key in b.agents:
          return b
    finally:
      self.cv.release()
    return None


  def _getAgentForAgentAddr(self, agent_addr):
    try:
      self.cv.acquire()
      key = agent_addr
      for b in self.brokers:
        if key in b.agents:
          return b.agents[key]
    finally:
      self.cv.release()
    return None


  def __repr__(self):
    return "QMF Console Session Manager (brokers: %d)" % len(self.brokers)


  def addBroker(self, target="localhost", timeout=None, mechanisms=None, sessTimeout=None, **connectArgs):
    """ Connect to a Qpid broker.  Returns an object of type Broker.
    Will raise an exception if the session is not managing the connection and
    the connection setup to the broker fails.
    """
    if isinstance(target, BrokerURL):
      url = target
    else:
      url = BrokerURL(target)
    broker = Broker(self, url.host, url.port, mechanisms, url.authName, url.authPass,
                    ssl = url.scheme == URL.AMQPS, connTimeout=timeout, sessTimeout=sessTimeout, **connectArgs)

    self.brokers.append(broker)
    return broker


  def delBroker(self, broker):
    """ Disconnect from a broker, and deallocate the broker proxy object.  The
    'broker' argument is the object returned from the addBroker call.  Errors
    are ignored.
    """
    broker._shutdown()
    self.brokers.remove(broker)
    del broker


  def getPackages(self):
    """ Get the list of known QMF packages """
    for broker in self.brokers:
      broker._waitForStable()
    return self.schemaCache.getPackages()


  def getClasses(self, packageName):
    """ Get the list of known classes within a QMF package """
    for broker in self.brokers:
      broker._waitForStable()
    return self.schemaCache.getClasses(packageName)


  def getSchema(self, classKey):
    """ Get the schema for a QMF class """
    for broker in self.brokers:
      broker._waitForStable()
    return self.schemaCache.getSchema(classKey)


  def bindPackage(self, packageName):
    """ Filter object and event callbacks to only those elements of the
    specified package. Also filters newPackage and newClass callbacks to the
    given package. Only valid if userBindings is True.
    """
    if not self.userBindings:
      raise Exception("userBindings option must be set for this Session.")
    if not self.rcvObjects and not self.rcvEvents:
      raise Exception("Session needs to be configured to receive events or objects.")
    v1keys = ["console.obj.*.*.%s.#" % packageName, "console.event.*.*.%s.#" % packageName]
    v2keys = ["agent.ind.data.%s.#" % packageName.replace(".", "_"),
              "agent.ind.event.%s.#" % packageName.replace(".", "_"),]
    if (packageName, None) not in self.class_filter:
      self.class_filter.append((packageName, None))
    if (packageName, None) not in self.event_filter:
      self.event_filter.append((packageName, None))
    self.v1BindingKeyList.extend(v1keys)
    self.v2BindingKeyList.extend(v2keys)
    for broker in self.brokers:
      if broker.isConnected():
        for v1key in v1keys:
          broker.amqpSession.exchange_bind(exchange="qpid.management", queue=broker.topicName, binding_key=v1key)
        if broker.brokerSupportsV2:
          for v2key in v2keys:
            # data indications should arrive on the unsolicited indication queue
            broker.amqpSession.exchange_bind(exchange="qmf.default.topic", queue=broker.v2_topic_queue_ui, binding_key=v2key)


  def bindClass(self, pname, cname=None):
    """ Filter object callbacks to only those objects of the specified package
    and optional class. Will also filter newPackage/newClass callbacks to the
    specified package and class.  Only valid if userBindings is True and
    rcvObjects is True.
    """
    if not self.userBindings:
      raise Exception("userBindings option must be set for this Session.")
    if not self.rcvObjects:
      raise Exception("Session needs to be configured with rcvObjects=True.")
    if cname is not None:
      v1key = "console.obj.*.*.%s.%s.#" % (pname, cname)
      v2key = "agent.ind.data.%s.%s.#" % (pname.replace(".", "_"), cname.replace(".", "_"))
    else:
      v1key = "console.obj.*.*.%s.#" % pname
      v2key = "agent.ind.data.%s.#" % pname.replace(".", "_")
    self.v1BindingKeyList.append(v1key)
    self.v2BindingKeyList.append(v2key)
    if (pname, cname) not in self.class_filter:
      self.class_filter.append((pname, cname))
    for broker in self.brokers:
      if broker.isConnected():
        broker.amqpSession.exchange_bind(exchange="qpid.management", queue=broker.topicName, binding_key=v1key)
        if broker.brokerSupportsV2:
          # data indications should arrive on the unsolicited indication queue
          broker.amqpSession.exchange_bind(exchange="qmf.default.topic", queue=broker.v2_topic_queue_ui, binding_key=v2key)

      
  def bindClassKey(self, classKey):
    """ Filter object callbacks to only those objects of the specified
    class. Will also filter newPackage/newClass callbacks to the specified
    package and class.  Only valid if userBindings is True and rcvObjects is
    True.
    """
    pname = classKey.getPackageName()
    cname = classKey.getClassName()
    self.bindClass(pname, cname)

  def bindEvent(self, pname, ename=None):
    """ Filter event callbacks only from a particular class by package and
    event name, or all events in a package if ename=None.  Will also filter
    newPackage/newClass callbacks to the specified package and class. Only
    valid if userBindings is True and rcvEvents is True.
    """
    if not self.userBindings:
      raise Exception("userBindings option must be set for this Session.")
    if not self.rcvEvents:
      raise Exception("Session needs to be configured with rcvEvents=True.")
    if ename is not None:
      v1key = "console.event.*.*.%s.%s.#" % (pname, ename)
      v2key = "agent.ind.event.%s.%s.#" % (pname.replace(".", "_"), ename.replace(".", "_"))
    else:
      v1key = "console.event.*.*.%s.#" % pname
      v2key = "agent.ind.event.%s.#" % pname.replace(".", "_")
    self.v1BindingKeyList.append(v1key)
    self.v2BindingKeyList.append(v2key)
    if (pname, ename) not in self.event_filter:
      self.event_filter.append((pname, ename))
    for broker in self.brokers:
      if broker.isConnected():
        broker.amqpSession.exchange_bind(exchange="qpid.management", queue=broker.topicName, binding_key=v1key)
        if broker.brokerSupportsV2:
          # event indications should arrive on the unsolicited indication queue
          broker.amqpSession.exchange_bind(exchange="qmf.default.topic", queue=broker.v2_topic_queue_ui, binding_key=v2key)

  def bindEventKey(self, eventKey):
    """ Filter event callbacks only from a particular class key. Will also
    filter newPackage/newClass callbacks to the specified package and
    class. Only valid if userBindings is True and rcvEvents is True.
    """
    pname = eventKey.getPackageName()
    ename = eventKey.getClassName()
    self.bindEvent(pname, ename)

  def bindAgent(self, vendor=None, product=None, instance=None, label=None):
    """ Receive heartbeats, newAgent and delAgent callbacks only for those
    agent(s) that match the passed identification criteria:
    V2 agents: vendor, optionally product and instance strings
    V1 agents: the label string.
    Only valid if userBindings is True.
    """
    if not self.userBindings:
      raise Exception("Session not configured for binding specific agents.")
    if vendor is None and label is None:
      raise Exception("Must specify at least a vendor (V2 agents)"
                      " or label (V1 agents).")

    if vendor:   # V2 agent identification
      if product is not None:
        v2key = "agent.ind.heartbeat.%s.%s.#" % (vendor.replace(".", "_"), product.replace(".", "_"))
      else:
        v2key = "agent.ind.heartbeat.%s.#" % vendor.replace(".", "_")
      self.v2BindingKeyList.append(v2key)

      # allow wildcards - only add filter if a non-wildcarded component is given
      if vendor == "*":
        vendor = None
      if product == "*":
        product = None
      if instance == "*":
        instance = None
      if vendor or product or instance:
        if (vendor, product, instance) not in self.agent_filter:
          self.agent_filter.append((vendor, product, instance))

      for broker in self.brokers:
        if broker.isConnected():
          if broker.brokerSupportsV2:
            # heartbeats should arrive on the heartbeat queue
            broker.amqpSession.exchange_bind(exchange="qmf.default.topic",
                                             queue=broker.v2_topic_queue_hb,
                                             binding_key=v2key)
    elif label != "*": # non-wildcard V1 agent label
      # V1 format heartbeats do not have any agent identifier in the routing
      # key, so we cannot filter them by bindings.
      if label not in self.agent_filter:
        self.agent_filter.append(label)


  def getAgents(self, broker=None):
    """ Get a list of currently known agents """
    brokerList = []
    if broker == None:
      for b in self.brokers:
        brokerList.append(b)
    else:
      brokerList.append(broker)

    for b in brokerList:
      b._waitForStable()
    agentList = []
    for b in brokerList:
      for a in b.getAgents():
        agentList.append(a)
    return agentList


  def makeObject(self, classKey, **kwargs):
    """ Create a new, unmanaged object of the schema indicated by classKey """
    schema = self.getSchema(classKey)
    if schema == None:
      raise Exception("Schema not found for classKey")
    return Object(None, schema, None, True, True, kwargs)


  def getObjects(self, **kwargs):
    """ Get a list of objects from QMF agents.
    All arguments are passed by name(keyword).

    The class for queried objects may be specified in one of the following ways:

    _schema = <schema> - supply a schema object returned from getSchema.
    _key = <key>       - supply a classKey from the list returned by getClasses.
    _class = <name>    - supply a class name as a string.  If the class name exists
                         in multiple packages, a _package argument may also be supplied.
    _objectId = <id>   - get the object referenced by the object-id

    If objects should be obtained from only one agent, use the following argument.
    Otherwise, the query will go to all agents.

    _agent = <agent> - supply an agent from the list returned by getAgents.

    If the get query is to be restricted to one broker (as opposed to all connected brokers),
    add the following argument:

    _broker = <broker> - supply a broker as returned by addBroker.

    The default timeout for this synchronous operation is 60 seconds.  To change the timeout,
    use the following argument:

    _timeout = <time in seconds>

    If additional arguments are supplied, they are used as property selectors.  For example,
    if the argument name="test" is supplied, only objects whose "name" property is "test"
    will be returned in the result.
    """
    if "_broker" in kwargs:
      brokerList = []
      brokerList.append(kwargs["_broker"])
    else:
      brokerList = self.brokers
    for broker in brokerList:
      broker._waitForStable()
      if broker.isConnected():
        if "_package" not in kwargs or "_class" not in kwargs or \
              kwargs["_package"] != "org.apache.qpid.broker" or \
              kwargs["_class"] != "agent":
          self.getObjects(_package = "org.apache.qpid.broker", _class = "agent",
                     _agent = broker.getAgent(1,0))

    agentList = []
    if "_agent" in kwargs:
      agent = kwargs["_agent"]
      if agent.broker not in brokerList:
        raise Exception("Supplied agent is not accessible through the supplied broker")
      if agent.broker.isConnected():
        agentList.append(agent)
    else:
      if "_objectId" in kwargs:
        oid = kwargs["_objectId"]
        for broker in brokerList:
          for agent in broker.getAgents():
            if agent.getBrokerBank() == oid.getBrokerBank() and agent.getAgentBank() == oid.getAgentBank():
              agentList.append(agent)
      else:
        for broker in brokerList:
          for agent in broker.getAgents():
            if agent.broker.isConnected():
              agentList.append(agent)

    if len(agentList) == 0:
      return []

    #
    # We now have a list of agents to query, start the queries and gather the results.
    #
    request = SessionGetRequest(len(agentList))
    for agent in agentList:
      agent.getObjects(request, **kwargs)
    timeout = 60
    if '_timeout' in kwargs:
        timeout = kwargs['_timeout']
    request.wait(timeout)
    return request.result


  def addEventFilter(self, **kwargs):
    """Filter unsolicited events based on package and event name.
    QMF v2 also can filter on vendor, product, and severity values.

    By default, a console receives unsolicted events by binding to:

        qpid.management/console.event.#  (v1)

        qmf.default.topic/agent.ind.event.#  (v2)

    A V1 event filter binding uses the pattern:

        qpid.management/console.event.*.*[.<package>[.<event>]].#

    A V2 event filter binding uses the pattern:

        qmf.default.topic/agent.ind.event.<Vendor|*>.<Product|*>.<severity|*>.<package|*>.<event|*>.#
    """
    package = kwargs.get("package", "*")
    event = kwargs.get("event", "*")
    vendor = kwargs.get("vendor", "*")
    product = kwargs.get("product", "*")
    severity = kwargs.get("severity", "*")

    if package == "*" and event != "*":
      raise Exception("'package' parameter required if 'event' parameter"
                      " supplied")

    # V1 key - can only filter on package (and event)
    if package == "*":
      key = "console.event.*.*." + str(package)
      if event != "*":
        key += "." + str(event)
      key += ".#"

      if key not in self.v1BindingKeyList:
        self.v1BindingKeyList.append(key)
        try:
          # remove default wildcard binding
          self.v1BindingKeyList.remove("console.event.#")
        except:
          pass

    # V2 key - escape any "." in the filter strings

    key = "agent.ind.event." + str(package).replace(".", "_") \
        + "." + str(event).replace(".", "_") \
        + "." + str(severity).replace(".", "_") \
        + "." + str(vendor).replace(".", "_") \
        + "." + str(product).replace(".", "_") \
        + ".#"

    if key not in self.v2BindingKeyList:
      self.v2BindingKeyList.append(key)
      try:
        # remove default wildcard binding
        self.v2BindingKeyList.remove("agent.ind.event.#")
      except:
        pass

    if package != "*":
      if event != "*":
        f = (package, event)
      else:
        f = (package, None)
      if f not in self.event_filter:
        self.event_filter.append(f)


  def addAgentFilter(self, vendor, product=None):
    """ Deprecate - use bindAgent() instead
    """
    self.addHeartbeatFilter(vendor=vendor, product=product)

  def addHeartbeatFilter(self, **kwargs):
    """ Deprecate - use bindAgent() instead.
    """
    vendor = kwargs.get("vendor")
    product = kwargs.get("product")
    if vendor is None:
      raise Exception("vendor parameter required!")

    # V1 heartbeats do not have any agent identifier - we cannot
    # filter them by agent.

    # build the binding key - escape "."s...
    key = "agent.ind.heartbeat." + str(vendor).replace(".", "_")
    if product is not None:
      key += "." + str(product).replace(".", "_")
    key += ".#"

    if key not in self.v2BindingKeyList:
      self.v2BindingKeyList.append(key) 
      self.agent_filter.append((vendor, product, None))

    # be sure we don't ever filter the local broker
    local_broker_key = "agent.ind.heartbeat." + "org.apache".replace(".", "_") \
        + "." + "qpidd".replace(".", "_") + ".#"
    if local_broker_key not in self.v2BindingKeyList:
      self.v2BindingKeyList.append(local_broker_key)

    # remove the wildcard key if present
    try:
      self.v2BindingKeyList.remove("agent.ind.heartbeat.#")
    except:
      pass

  def _bindingKeys(self):
    v1KeyList = []
    v2KeyList = []
    v1KeyList.append("schema.#")
    # note well: any binding that starts with 'agent.ind.heartbeat' will be
    # bound to the heartbeat queue, otherwise it will be bound to the
    # unsolicited indication queue.  See _decOutstanding() for the binding.
    if not self.userBindings:
      if self.rcvObjects and self.rcvEvents and self.rcvHeartbeats:
        v1KeyList.append("console.#")
        v2KeyList.append("agent.ind.data.#")
        v2KeyList.append("agent.ind.event.#")
        v2KeyList.append("agent.ind.heartbeat.#")
      else:
        # need heartbeats for V2 newAgent()/delAgent()
        v2KeyList.append("agent.ind.heartbeat.#")
        if self.rcvObjects:
          v1KeyList.append("console.obj.#")
          v2KeyList.append("agent.ind.data.#")
        else:
          v1KeyList.append("console.obj.*.*.org.apache.qpid.broker.agent")
        if self.rcvEvents:
          v1KeyList.append("console.event.#")
          v2KeyList.append("agent.ind.event.#")
        else:
          v1KeyList.append("console.event.*.*.org.apache.qpid.broker.agent")
        if self.rcvHeartbeats:
          v1KeyList.append("console.heartbeat.#")
    else:
      # mandatory bindings
      v1KeyList.append("console.obj.*.*.org.apache.qpid.broker.agent")
      v1KeyList.append("console.event.*.*.org.apache.qpid.broker.agent")
      v1KeyList.append("console.heartbeat.#")  # no way to turn this on later
      v2KeyList.append("agent.ind.heartbeat.org_apache.qpidd.#")

    return (v1KeyList, v2KeyList)


  def _handleBrokerConnect(self, broker):
    if self.console:
      for agent in broker.getAgents():
        self._newAgentCallback(agent)
      self.console.brokerConnected(broker)


  def _handleBrokerDisconnect(self, broker):
    if self.console:
      for agent in broker.getAgents():
        self._delAgentCallback(agent)
      self.console.brokerDisconnected(broker)


  def _handleBrokerResp(self, broker, codec, seq):
    broker.brokerId = codec.read_uuid()
    if self.console != None:
      self.console.brokerInfo(broker)

    # Send a package request
    # (effectively inc and dec outstanding by not doing anything)
    sendCodec = Codec()
    seq = self.seqMgr._reserve(self._CONTEXT_STARTUP)
    broker._setHeader(sendCodec, 'P', seq)
    smsg = broker._message(sendCodec.encoded)
    broker._send(smsg)


  def _handlePackageInd(self, broker, codec, seq):
    pname = str(codec.read_str8())
    notify = self.schemaCache.declarePackage(pname)
    if notify and self.console != None:
      self._newPackageCallback(pname)

    # Send a class request
    broker._incOutstanding()
    sendCodec = Codec()
    seq = self.seqMgr._reserve(self._CONTEXT_STARTUP)
    broker._setHeader(sendCodec, 'Q', seq)
    sendCodec.write_str8(pname)
    smsg = broker._message(sendCodec.encoded)
    broker._send(smsg)


  def _handleCommandComplete(self, broker, codec, seq, agent):
    code = codec.read_uint32()
    text = codec.read_str8()
    context = self.seqMgr._release(seq)
    if context == self._CONTEXT_STARTUP:
      broker._decOutstanding()
    elif context == self._CONTEXT_SYNC and seq == broker.syncSequence:
      try:
        broker.cv.acquire()
        broker.syncInFlight = False
        broker.cv.notify()
      finally:
        broker.cv.release()
    elif context == self._CONTEXT_MULTIGET and seq in self.syncSequenceList:
      try:
        self.cv.acquire()
        self.syncSequenceList.remove(seq)
        if len(self.syncSequenceList) == 0:
          self.cv.notify()
      finally:
        self.cv.release()

    if agent:
      agent._handleV1Completion(seq, code, text)


  def _handleClassInd(self, broker, codec, seq):
    kind  = codec.read_uint8()
    classKey = ClassKey(codec)
    classKey._setType(kind)
    schema = self.schemaCache.getSchema(classKey)

    if not schema:
      # Send a schema request for the unknown class
      broker._incOutstanding()
      sendCodec = Codec()
      seq = self.seqMgr._reserve(self._CONTEXT_STARTUP)
      broker._setHeader(sendCodec, 'S', seq)
      classKey.encode(sendCodec)
      smsg = broker._message(sendCodec.encoded)
      broker._send(smsg)


  def _handleHeartbeatInd(self, broker, codec, seq, msg):
    brokerBank = 1
    agentBank = 0
    dp = msg.get("delivery_properties")
    if dp:
      key = dp["routing_key"]
      if key:
        keyElements = key.split(".")
        if len(keyElements) == 4:
          brokerBank = int(keyElements[2])
          agentBank = int(keyElements[3])
      else:
        # If there's no routing key in the delivery properties,
        # assume the message is from the broker.
        brokerBank = 1
        agentBank = 0

    agent = broker.getAgent(brokerBank, agentBank)
    if self.rcvHeartbeats and self.console != None and agent != None:
      timestamp = codec.read_uint64()
      self._heartbeatCallback(agent, timestamp)


  def _handleSchemaResp(self, broker, codec, seq, agent_addr):
    kind  = codec.read_uint8()
    classKey = ClassKey(codec)
    classKey._setType(kind)
    _class = SchemaClass(kind, classKey, codec, self)
    new_pkg, new_cls = self.schemaCache.declareClass(classKey, _class)
    ctx = self.seqMgr._release(seq)
    if ctx:
      broker._decOutstanding()
    if self.console != None:
      if new_pkg:
        self._newPackageCallback(classKey.getPackageName())
      if new_cls:
        self._newClassCallback(kind, classKey)

    if agent_addr and (agent_addr.__class__ == str or agent_addr.__class__ == unicode):
      agent = self._getAgentForAgentAddr(agent_addr)
      if agent:
        agent._schemaInfoFromV2Agent()


  def _v2HandleHeartbeatInd(self, broker, mp, ah, content):
    try:
      agentName = ah["qmf.agent"]
      values = content["_values"]

      if '_timestamp' in values:
        timestamp = values["_timestamp"]
      else:
        timestamp = values['timestamp']

      if '_heartbeat_interval' in values:
        interval = values['_heartbeat_interval']
      else:
        interval = values['heartbeat_interval']

      epoch = 0
      if '_epoch' in values:
        epoch = values['_epoch']
      elif 'epoch' in values:
        epoch = values['epoch']
    except Exception,e:
      return

    if self.agent_filter:
      # only allow V2 agents that satisfy the filter
      v = agentName.split(":", 2)
      if len(v) != 3 or ((v[0], None, None) not in self.agent_filter
                         and (v[0], v[1], None) not in self.agent_filter
                         and (v[0], v[1], v[2]) not in self.agent_filter):
        return

    ##
    ## We already have the "local-broker" agent in our list as ['0'].
    ##
    if '_vendor' in values and values['_vendor'] == 'apache.org' and \
          '_product' in values and values['_product'] == 'qpidd':
        agent = broker.getBrokerAgent()
    else:
        agent = broker.getAgent(1, agentName)
    if agent == None:
      agent = Agent(broker, agentName, "QMFv2 Agent", True, interval)
      agent.setEpoch(epoch)
      broker._addAgent(agentName, agent)
    else:
      agent.touch()
    if self.rcvHeartbeats and self.console and agent:
      self._heartbeatCallback(agent, timestamp)
    agent.update_schema_timestamp(values.get("_schema_updated", 0))


  def _v2HandleAgentLocateRsp(self, broker, mp, ah, content):
    self._v2HandleHeartbeatInd(broker, mp, ah, content)


  def _handleError(self, error):
    try:
      self.cv.acquire()
      if len(self.syncSequenceList) > 0:
        self.error = error
      self.syncSequenceList = []
      self.cv.notify()
    finally:
      self.cv.release()


  def _selectMatch(self, object):
    """ Check the object against self.getSelect to check for a match """
    for key, value in self.getSelect:
      for prop, propval in object.getProperties():
        if key == prop.name and value != propval:
          return False
    return True
  

  def _decodeValue(self, codec, typecode, broker=None):
    """ Decode, from the codec, a value based on its typecode. """
    if   typecode == 1:  data = codec.read_uint8()      # U8
    elif typecode == 2:  data = codec.read_uint16()     # U16
    elif typecode == 3:  data = codec.read_uint32()     # U32
    elif typecode == 4:  data = codec.read_uint64()     # U64
    elif typecode == 6:  data = codec.read_str8()       # SSTR
    elif typecode == 7:  data = codec.read_str16()      # LSTR
    elif typecode == 8:  data = codec.read_int64()      # ABSTIME
    elif typecode == 9:  data = codec.read_uint64()     # DELTATIME
    elif typecode == 10: data = ObjectId(codec)         # REF
    elif typecode == 11: data = codec.read_uint8() != 0 # BOOL
    elif typecode == 12: data = codec.read_float()      # FLOAT
    elif typecode == 13: data = codec.read_double()     # DOUBLE
    elif typecode == 14: data = codec.read_uuid()       # UUID
    elif typecode == 16: data = codec.read_int8()       # S8
    elif typecode == 17: data = codec.read_int16()      # S16
    elif typecode == 18: data = codec.read_int32()      # S32
    elif typecode == 19: data = codec.read_int64()      # S63
    elif typecode == 15: data = codec.read_map()        # FTABLE
    elif typecode == 20:                                # OBJECT
      # Peek at the type, and if it is still 20 pull it decode. If
      # Not, call back into self.
      inner_type_code = codec.read_uint8()
      if inner_type_code == 20:
          classKey = ClassKey(codec)
          schema = self.schemaCache.getSchema(classKey)
          if not schema:
            return None
          data = Object(self, broker, schema, codec, True, True, False)
      else:
          data = self._decodeValue(codec, inner_type_code, broker)
    elif typecode == 21: data = codec.read_list()       # List
    elif typecode == 22:                                #Array
        #taken from codec10.read_array
        sc = Codec(codec.read_vbin32()) 
        count = sc.read_uint32()
        type = sc.read_uint8()
        data = []
        while count > 0:
          data.append(self._decodeValue(sc,type,broker))
          count -= 1
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
    elif typecode == 7:  codec.write_str16  (value)         # LSTR
    elif typecode == 8:  codec.write_int64  (long(value))   # ABSTIME
    elif typecode == 9:  codec.write_uint64 (long(value))   # DELTATIME
    elif typecode == 10: value.encode       (codec)         # REF
    elif typecode == 11: codec.write_uint8  (int(value))    # BOOL
    elif typecode == 12: codec.write_float  (float(value))  # FLOAT
    elif typecode == 13: codec.write_double (float(value))  # DOUBLE
    elif typecode == 14: codec.write_uuid   (value.bytes)   # UUID
    elif typecode == 16: codec.write_int8   (int(value))    # S8
    elif typecode == 17: codec.write_int16  (int(value))    # S16
    elif typecode == 18: codec.write_int32  (int(value))    # S32
    elif typecode == 19: codec.write_int64  (int(value))    # S64
    elif typecode == 20: value._encodeUnmanaged(codec)      # OBJECT
    elif typecode == 15: codec.write_map    (value)         # FTABLE
    elif typecode == 21: codec.write_list   (value)         # List
    elif typecode == 22:                                    # Array
        sc = Codec()
        self._encodeValue(sc, len(value), 3)
        if len(value) > 0:
            ltype = self.encoding(value[0])
            self._encodeValue(sc,ltype,1)
            for o in value:
              self._encodeValue(sc, o, ltype)
        codec.write_vbin32(sc.encoded)
    else:
      raise ValueError ("Invalid type code: %d" % typecode)


  def encoding(self, value):
      return self._encoding(value.__class__)
      

  def _encoding(self, klass):
    if Session.ENCODINGS.has_key(klass):
      return self.ENCODINGS[klass]
    for base in klass.__bases__:
      result = self._encoding(base)
      if result != None:
        return result
            

  def _displayValue(self, value, typecode):
    """ """
    if   typecode == 1:  return unicode(value)
    elif typecode == 2:  return unicode(value)
    elif typecode == 3:  return unicode(value)
    elif typecode == 4:  return unicode(value)
    elif typecode == 6:  return value
    elif typecode == 7:  return value
    elif typecode == 8:  return unicode(strftime("%c", gmtime(value / 1000000000)))
    elif typecode == 9:  return unicode(value)
    elif typecode == 10: return unicode(value.__repr__())
    elif typecode == 11:
      if value: return u"T"
      else:     return u"F"
    elif typecode == 12: return unicode(value)
    elif typecode == 13: return unicode(value)
    elif typecode == 14: return unicode(value.__repr__())
    elif typecode == 15: return unicode(value.__repr__())
    elif typecode == 16: return unicode(value)
    elif typecode == 17: return unicode(value)
    elif typecode == 18: return unicode(value)
    elif typecode == 19: return unicode(value)
    elif typecode == 20: return unicode(value.__repr__())
    elif typecode == 21: return unicode(value.__repr__())
    elif typecode == 22: return unicode(value.__repr__())
    else:
      raise ValueError ("Invalid type code: %d" % typecode)
    

  def _defaultValue(self, stype, broker=None, kwargs={}):
    """ """
    typecode = stype.type
    if   typecode == 1:  return 0
    elif typecode == 2:  return 0
    elif typecode == 3:  return 0
    elif typecode == 4:  return 0
    elif typecode == 6:  return ""
    elif typecode == 7:  return ""
    elif typecode == 8:  return 0
    elif typecode == 9:  return 0
    elif typecode == 10: return ObjectId(None)
    elif typecode == 11: return False
    elif typecode == 12: return 0.0
    elif typecode == 13: return 0.0
    elif typecode == 14: return UUID(bytes=[0 for i in range(16)])
    elif typecode == 15: return {}
    elif typecode == 16: return 0
    elif typecode == 17: return 0
    elif typecode == 18: return 0
    elif typecode == 19: return 0
    elif typecode == 21: return []
    elif typecode == 22: return []
    elif typecode == 20:
      try:
        if "classKeys" in kwargs:
          keyList = kwargs["classKeys"]
        else:
          keyList = None
        classKey = self._bestClassKey(stype.refPackage, stype.refClass, keyList)
        if classKey:
          return self.makeObject(classKey, broker, kwargs)
      except:
        pass
      return None
    else:
      raise ValueError ("Invalid type code: %d" % typecode)


  def _bestClassKey(self, pname, cname, preferredList):
    """ """
    if pname == None or cname == None:
      if len(preferredList) == 0:
        return None
      return preferredList[0]
    for p in preferredList:
      if p.getPackageName() == pname and p.getClassName() == cname:
        return p
    clist = self.getClasses(pname)
    for c in clist:
      if c.getClassName() == cname:
        return c
    return None
    

  def _sendMethodRequest(self, broker, schemaKey, objectId, name, argList):
    """ This is a legacy function that is used by qpid-tool to invoke methods
    using the broker, objectId and schema.
    Methods are now invoked on the object itself.
    """
    objs = self.getObjects(_objectId=objectId)
    if objs:
      return objs[0]._sendMethodRequest(name, argList, {})
    return None

  def _newPackageCallback(self, pname):
    """
    Invokes the console.newPackage() callback if the callback is present and
    the package is not filtered.
    """
    if self.console:
      if len(self.class_filter) == 0 and len(self.event_filter) == 0:
        self.console.newPackage(pname)
      else:
        for x in self.class_filter:
          if x[0] == pname:
            self.console.newPackage(pname)
            return

        for x in self.event_filter:
          if x[0] == pname:
            self.console.newPackage(pname)
            return


  def _newClassCallback(self, ctype, ckey):
    """
    Invokes the console.newClass() callback if the callback is present and the
    class is not filtered.
    """
    if self.console:
      if ctype == ClassKey.TYPE_DATA:
        if (len(self.class_filter) == 0
            or (ckey.getPackageName(), ckey.getClassName()) in self.class_filter):
          self.console.newClass(ctype, ckey)
      elif ctype == ClassKey.TYPE_EVENT:
        if (len(self.event_filter) == 0
            or (ckey.getPackageName(), ckey.getClassName()) in self.event_filter):
          self.console.newClass(ctype, ckey)
      else: # old class keys did not contain type info, check both filters
        if ((len(self.class_filter) == 0 and len(self.event_filter) == 0)
            or (ckey.getPackageName(), ckey.getClassName()) in self.class_filter
            or (ckey.getPackageName(), ckey.getClassName()) in self.event_filter):
          self.console.newClass(ctype, ckey)

  def _agentAllowed(self, agentName, isV2):
    """ True if the agent is NOT filtered.
    """
    if self.agent_filter:
      if isV2:
        v = agentName.split(":", 2)
        return ((len(v) > 2 and (v[0], v[1], v[2]) in self.agent_filter)
                or (len(v) > 1 and (v[0], v[1], None) in self.agent_filter)
                or (v and (v[0], None, None) in self.agent_filter));
      else:
        return agentName in self.agent_filter
    return True

  def _heartbeatCallback(self, agent, timestamp):
    """
    Invokes the console.heartbeat() callback if the callback is present and the
    agent is not filtered.
    """
    if self.console and self.rcvHeartbeats:
      if ((agent.isV2 and self._agentAllowed(agent.agentBank, True))
          or ((not agent.isV2) and self._agentAllowed(agent.label, False))):
        self.console.heartbeat(agent, timestamp)

  def _newAgentCallback(self, agent):
    """
    Invokes the console.newAgent() callback if the callback is present and the
    agent is not filtered.
    """
    if self.console:
      if ((agent.isV2 and self._agentAllowed(agent.agentBank, True))
          or ((not agent.isV2) and self._agentAllowed(agent.label, False))):
        self.console.newAgent(agent)

  def _delAgentCallback(self, agent):
    """
    Invokes the console.delAgent() callback if the callback is present and the
    agent is not filtered.
    """
    if self.console:
      if ((agent.isV2 and self._agentAllowed(agent.agentBank, True))
          or ((not agent.isV2) and self._agentAllowed(agent.label, False))):
        self.console.delAgent(agent)

#===================================================================================================
# SessionGetRequest
#===================================================================================================
class SessionGetRequest(object):
  """
  This class is used to track get-object queries at the Session level.
  """
  def __init__(self, agentCount):
    self.agentCount = agentCount
    self.result = []
    self.cv = Condition()
    self.waiting = True

  def __call__(self, **kwargs):
    """
    Callable entry point for gathering collected objects.
    """
    try:
      self.cv.acquire()
      if 'qmf_object' in kwargs:
        self.result.append(kwargs['qmf_object'])
      elif 'qmf_complete' in kwargs or 'qmf_exception' in kwargs:
        self.agentCount -= 1
        if self.agentCount == 0:
          self.waiting = None
          self.cv.notify()
    finally:
      self.cv.release()

  def wait(self, timeout):
    starttime = time()
    try:
      self.cv.acquire()
      while self.waiting:
        if (time() - starttime) > timeout:
          raise Exception("Timed out after %d seconds" % timeout)
        self.cv.wait(1)
    finally:
      self.cv.release()


#===================================================================================================
# SchemaCache
#===================================================================================================
class SchemaCache(object):
  """
  The SchemaCache is a data structure that stores learned schema information.
  """
  def __init__(self):
    """
    Create a map of schema packages and a lock to protect this data structure.
    Note that this lock is at the bottom of any lock hierarchy.  If it is held, no other
    lock in the system should attempt to be acquired.
    """
    self.packages = {}
    self.lock = Lock()

  def getPackages(self):
    """ Get the list of known QMF packages """
    list = []
    try:
      self.lock.acquire()
      for package in self.packages:
        list.append(package)
    finally:
      self.lock.release()
    return list

  def getClasses(self, packageName):
    """ Get the list of known classes within a QMF package """
    list = []
    try:
      self.lock.acquire()
      if packageName in self.packages:
        for pkey in self.packages[packageName]:
          if isinstance(self.packages[packageName][pkey], SchemaClass):
            list.append(self.packages[packageName][pkey].getKey())
          elif self.packages[packageName][pkey] is not None:
            # schema not present yet, but we have schema type
            list.append(ClassKey({"_package_name": packageName,
                                  "_class_name": pkey[0],
                                  "_hash": pkey[1],
                                  "_type": self.packages[packageName][pkey]}))
    finally:
      self.lock.release()
    return list

  def getSchema(self, classKey):
    """ Get the schema for a QMF class, return None if schema not available """
    pname = classKey.getPackageName()
    pkey = classKey.getPackageKey()
    try:
      self.lock.acquire()
      if pname in self.packages:
        if (pkey in self.packages[pname] and
            isinstance(self.packages[pname][pkey], SchemaClass)):
          # hack: value may be schema type info if schema not available
          return self.packages[pname][pkey]
    finally:
      self.lock.release()
    return None

  def declarePackage(self, pname):
    """ Maybe add a package to the cache.  Return True if package was added, None if it pre-existed. """
    try:
      self.lock.acquire()
      if pname in self.packages:
        return None
      self.packages[pname] = {}
    finally:
      self.lock.release()
    return True

  def declareClass(self, classKey, classDef=None):
    """ Add a class definition to the cache, if supplied.  Return a pair
    indicating if the package or class is new.
    """
    new_package = False
    new_class = False
    pname = classKey.getPackageName()
    pkey = classKey.getPackageKey()
    try:
      self.lock.acquire()
      if pname not in self.packages:
        self.packages[pname] = {}
        new_package = True
      packageMap = self.packages[pname]
      if pkey not in packageMap or not isinstance(packageMap[pkey], SchemaClass):
        if classDef is not None:
          new_class = True
          packageMap[pkey] = classDef
        elif classKey.getType() is not None:
          # hack: don't indicate "new_class" to caller unless the classKey type
          # information is present.  "new_class" causes the console.newClass()
          # callback to be invoked, which -requires- a valid classKey type!
          new_class = True
          # store the type for the getClasses() method:
          packageMap[pkey] = classKey.getType()

    finally:
      self.lock.release()
    return (new_package, new_class)


#===================================================================================================
# ClassKey
#===================================================================================================
class ClassKey:
  """ A ClassKey uniquely identifies a class from the schema. """

  TYPE_DATA = "_data"
  TYPE_EVENT = "_event"

  def __init__(self, constructor):
    if constructor.__class__ == str:
      # construct from __repr__ string
      try:
        # supports two formats:
        # type present = P:C:T(H)
        # no type present = P:C(H)
        tmp = constructor.split(":")
        if len(tmp) == 3:
          self.pname, self.cname, rem = tmp
          self.type, hsh = rem.split("(")
        else:
          self.pname, rem = tmp
          self.cname, hsh = rem.split("(")
          self.type = None
        hsh = hsh.strip(")")
        hexValues = hsh.split("-")
        h0 = int(hexValues[0], 16)
        h1 = int(hexValues[1], 16)
        h2 = int(hexValues[2], 16)
        h3 = int(hexValues[3], 16)
        h4 = int(hexValues[4][0:4], 16)
        h5 = int(hexValues[4][4:12], 16)
        self.hash = UUID(bytes=struct.pack("!LHHHHL", h0, h1, h2, h3, h4, h5))
      except:
        raise Exception("Invalid ClassKey format")
    elif constructor.__class__ == dict:
      # construct from QMFv2 map
      try:
        self.pname = constructor['_package_name']
        self.cname = constructor['_class_name']
        self.hash  = constructor['_hash']
        self.type  = constructor.get('_type')
      except:
        raise Exception("Invalid ClassKey map format %s" % str(constructor))
    else:
      # construct from codec
      codec = constructor
      self.pname = str(codec.read_str8())
      self.cname = str(codec.read_str8())
      self.hash  = UUID(bytes=codec.read_bin128())
      # old V1 codec did not include "type"
      self.type = None

  def encode(self, codec):
    # old V1 codec did not include "type"
    codec.write_str8(self.pname)
    codec.write_str8(self.cname)
    codec.write_bin128(self.hash.bytes)

  def asMap(self):
    m = {'_package_name': self.pname,
            '_class_name': self.cname,
            '_hash': self.hash}
    if self.type is not None:
      m['_type'] = self.type
    return m

  def getPackageName(self):
    return self.pname

  def getClassName(self):
    return self.cname

  def getHash(self):
    return self.hash

  def getType(self):
    return self.type

  def getHashString(self):
    return str(self.hash)

  def getPackageKey(self):
    return (self.cname, self.hash)

  def __repr__(self):
    if self.type is None:
      return self.pname + ":" + self.cname + "(" + self.getHashString() + ")"
    return self.pname + ":" + self.cname + ":" + self.type +  "(" + self.getHashString()  + ")"

  def _setType(self, _type):
    if _type == 2 or _type == ClassKey.TYPE_EVENT:
      self.type = ClassKey.TYPE_EVENT
    else:
      self.type = ClassKey.TYPE_DATA

  def __hash__(self):
    ss = self.pname + self.cname + self.getHashString()
    return ss.__hash__()

  def __eq__(self, other):
    return self.__repr__() == other.__repr__()

#===================================================================================================
# SchemaClass
#===================================================================================================
class SchemaClass:
  """ """
  CLASS_KIND_TABLE = 1
  CLASS_KIND_EVENT = 2

  def __init__(self, kind, key, codec, session):
    self.kind = kind
    self.classKey = key
    self.properties = []
    self.statistics = []
    self.methods = []
    self.arguments = []
    self.session = session

    hasSupertype = 0  #codec.read_uint8()
    if self.kind == self.CLASS_KIND_TABLE:
      propCount   = codec.read_uint16()
      statCount   = codec.read_uint16()
      methodCount = codec.read_uint16()
      if hasSupertype == 1:
        self.superTypeKey = ClassKey(codec)
      else:
        self.superTypeKey = None ;
      for idx in range(propCount):
        self.properties.append(SchemaProperty(codec))
      for idx in range(statCount):
        self.statistics.append(SchemaStatistic(codec))
      for idx in range(methodCount):
        self.methods.append(SchemaMethod(codec))

    elif self.kind == self.CLASS_KIND_EVENT:
      argCount = codec.read_uint16()
      if (hasSupertype):
        self.superTypeKey = ClassKey(codec)
      else:
        self.superTypeKey = None ;      
      for idx in range(argCount):
        self.arguments.append(SchemaArgument(codec, methodArg=False))

  def __repr__(self):
    if self.kind == self.CLASS_KIND_TABLE:
      kindStr = "Table"
    elif self.kind == self.CLASS_KIND_EVENT:
      kindStr = "Event"
    else:
      kindStr = "Unsupported"
    result = "%s Class: %s " % (kindStr, self.classKey.__repr__())
    return result

  def getKey(self):
    """ Return the class-key for this class. """
    return self.classKey

  def getProperties(self):
    """ Return the list of properties for the class. """
    if (self.superTypeKey == None):
        return self.properties
    else:
        return self.properties + self.session.getSchema(self.superTypeKey).getProperties() 

  def getStatistics(self):
    """ Return the list of statistics for the class. """
    if (self.superTypeKey == None):
        return self.statistics
    else:
        return self.statistics + self.session.getSchema(self.superTypeKey).getStatistics()     

  def getMethods(self):
    """ Return the list of methods for the class. """
    if (self.superTypeKey == None):
        return self.methods
    else:
        return self.methods + self.session.getSchema(self.superTypeKey).getMethods()    

  def getArguments(self):
    """ Return the list of events for the class. """
    """ Return the list of methods for the class. """
    if (self.superTypeKey == None):
        return self.arguments
    else:
        return self.arguments + self.session.getSchema(self.superTypeKey).getArguments()  


#===================================================================================================
# SchemaProperty
#===================================================================================================
class SchemaProperty:
  """ """
  def __init__(self, codec):
    map = codec.read_map()
    self.name     = str(map["name"])
    self.type     = map["type"]
    self.access   = str(map["access"])
    self.index    = map["index"] != 0
    self.optional = map["optional"] != 0
    self.refPackage = None
    self.refClass   = None
    self.unit       = None
    self.min        = None
    self.max        = None
    self.maxlen     = None
    self.desc       = None

    for key, value in map.items():
      if   key == "unit"       : self.unit = value
      elif key == "min"        : self.min = value
      elif key == "max"        : self.max = value
      elif key == "maxlen"     : self.maxlen = value
      elif key == "desc"       : self.desc = value
      elif key == "refPackage" : self.refPackage = value
      elif key == "refClass"   : self.refClass = value

  def __repr__(self):
    return self.name


#===================================================================================================
# SchemaStatistic
#===================================================================================================
class SchemaStatistic:
  """ """
  def __init__(self, codec):
    map = codec.read_map()
    self.name     = str(map["name"])
    self.type     = map["type"]
    self.unit     = None
    self.desc     = None

    for key, value in map.items():
      if   key == "unit" : self.unit = value
      elif key == "desc" : self.desc = value

  def __repr__(self):
    return self.name


#===================================================================================================
# SchemaMethod
#===================================================================================================
class SchemaMethod:
  """ """
  def __init__(self, codec):
    map = codec.read_map()
    self.name = str(map["name"])
    argCount  = map["argCount"]
    if "desc" in map:
      self.desc = map["desc"]
    else:
      self.desc = None
    self.arguments = []

    for idx in range(argCount):
      self.arguments.append(SchemaArgument(codec, methodArg=True))

  def __repr__(self):
    result = self.name + "("
    first = True
    for arg in self.arguments:
      if arg.dir.find("I") != -1:
        if first:
          first = False
        else:
          result += ", "
        result += arg.name
    result += ")"
    return result


#===================================================================================================
# SchemaArgument
#===================================================================================================
class SchemaArgument:
  """ """
  def __init__(self, codec, methodArg):
    map = codec.read_map()
    self.name    = str(map["name"])
    self.type    = map["type"]
    if methodArg:
      self.dir   = str(map["dir"]).upper()
    self.unit    = None
    self.min     = None
    self.max     = None
    self.maxlen  = None
    self.desc    = None
    self.default = None
    self.refPackage = None
    self.refClass   = None

    for key, value in map.items():
      if   key == "unit"    : self.unit    = value
      elif key == "min"     : self.min     = value
      elif key == "max"     : self.max     = value
      elif key == "maxlen"  : self.maxlen  = value
      elif key == "desc"    : self.desc    = value
      elif key == "default" : self.default = value
      elif key == "refPackage" : self.refPackage = value
      elif key == "refClass"   : self.refClass = value


#===================================================================================================
# ObjectId
#===================================================================================================
class ObjectId:
  """ Object that represents QMF object identifiers """
  def __init__(self, constructor, first=0, second=0, agentName=None):
    if  constructor.__class__ == dict:
      self.isV2 = True
      self.agentName = agentName
      self.agentEpoch = 0
      if '_agent_name' in constructor:  self.agentName = constructor['_agent_name']
      if '_agent_epoch' in constructor: self.agentEpoch = constructor['_agent_epoch']
      if '_object_name' not in constructor:
        raise Exception("QMFv2 OBJECT_ID must have the '_object_name' field.")
      self.objectName = constructor['_object_name']
    else:
      self.isV2 = None
      if not constructor:
        first = first
        second = second
      else:
        first  = constructor.read_uint64()
        second = constructor.read_uint64()
      self.agentName = str(first & 0x000000000FFFFFFF)
      self.agentEpoch = (first & 0x0FFF000000000000) >> 48
      self.objectName = str(second)

  def _create(cls, agent_name, object_name, epoch=0):
    oid = {"_agent_name": agent_name,
           "_object_name": object_name,
           "_agent_epoch": epoch}
    return cls(oid)
  create = classmethod(_create)

  def __cmp__(self, other):    
    if other == None or not isinstance(other, ObjectId) :
      return 1

    if self.objectName < other.objectName:
      return -1
    if self.objectName > other.objectName:
      return 1

    if self.agentName < other.agentName:
      return -1
    if self.agentName > other.agentName:
      return 1

    if self.agentEpoch < other.agentEpoch:
      return -1
    if self.agentEpoch > other.agentEpoch:
      return 1
    return 0

  def __repr__(self):
    return "%d-%d-%d-%s-%s" % (self.getFlags(), self.getSequence(),
                               self.getBrokerBank(), self.getAgentBank(), self.getObject())

  def index(self):
    return self.__repr__()

  def getFlags(self):
    return 0

  def getSequence(self):
    return self.agentEpoch

  def getBrokerBank(self):
    return 1

  def getAgentBank(self):
    return self.agentName

  def getV2RoutingKey(self):
    if self.agentName == '0':
      return "broker"
    return self.agentName

  def getObject(self):
    return self.objectName

  def isDurable(self):
    return self.getSequence() == 0

  def encode(self, codec):
    first = (self.agentEpoch << 48) + (1 << 28)
    second = 0

    try:
      first += int(self.agentName)
    except:
      pass

    try:
      second = int(self.objectName)
    except:
      pass

    codec.write_uint64(first)
    codec.write_uint64(second)

  def asMap(self):
    omap = {'_agent_name': self.agentName, '_object_name': self.objectName}
    if self.agentEpoch != 0:
      omap['_agent_epoch'] = self.agentEpoch
    return omap

  def __hash__(self):
    return self.__repr__().__hash__()

  def __eq__(self, other):
    return self.__repr__().__eq__(other)


#===================================================================================================
# MethodResult
#===================================================================================================
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


#===================================================================================================
# Broker
#===================================================================================================
class Broker(Thread):
  """ This object represents a connection (or potential connection) to a QMF broker. """
  SYNC_TIME = 60
  nextSeq = 1

  # for connection recovery
  DELAY_MIN = 1
  DELAY_MAX = 128
  DELAY_FACTOR = 2

  class _q_item:
    """ Broker-private class to encapsulate data sent to the broker thread
    queue.
    """
    type_wakeup = 0
    type_v1msg = 1
    type_v2msg = 2

    def __init__(self, typecode, data):
      self.typecode = typecode
      self.data = data

  def __init__(self, session, host, port, authMechs, authUser, authPass,
               ssl=False, connTimeout=None, sessTimeout=None, **connectArgs):
    """ Create a broker proxy and setup a connection to the broker.  Will raise
    an exception if the connection fails and the session is not configured to
    retry connection setup (manageConnections = False).

    Spawns a thread to manage the broker connection.  Call _shutdown() to
    shutdown the thread when releasing the broker.
    """
    Thread.__init__(self)
    self.session  = session
    self.host = host
    self.port = port
    self.mechanisms = authMechs
    self.ssl = ssl
    if connTimeout is not None:
        connTimeout = float(connTimeout)
    self.connTimeout = connTimeout
    if sessTimeout is not None:
        sessTimeout = float(sessTimeout)
    else:
        sessTimeout = self.SYNC_TIME
    self.sessTimeout = sessTimeout
    self.authUser = authUser
    self.authPass = authPass
    self.saslUser = None
    self.cv = Condition()
    self.seqToAgentMap = {}
    self.error = None
    self.conn_exc = None  # exception hit by _tryToConnect()
    self.brokerId = None
    self.connected = False
    self.brokerAgent = None
    self.brokerSupportsV2 = None
    self.rcv_queue = Queue() # for msg received on session
    self.conn = None
    self.amqpSession = None
    self.amqpSessionId = "%s.%d.%d" % (platform.uname()[1], os.getpid(), Broker.nextSeq)
    Broker.nextSeq += 1
    self.last_age_check = time()
    self.connectArgs = connectArgs
    # thread control
    self.setDaemon(True)
    self.setName("Thread for broker: %s:%d" % (host, port))
    self.canceled = False
    self.ready = Semaphore(0)
    self.start()
    if not self.session.manageConnections:
      # wait for connection setup to complete in subthread.
      # On failure, propagate exception to caller
      self.ready.acquire()
      if self.conn_exc:
        self._shutdown()   # wait for the subthread to clean up...
        raise self.conn_exc
      # connection up - wait for stable...
      try:
        self._waitForStable()
        agent = self.getBrokerAgent()
        if agent:
          agent.getObjects(_class="agent")
      except:
        self._shutdown()   # wait for the subthread to clean up...
        raise


  def isConnected(self):
    """ Return True if there is an active connection to the broker. """
    return self.connected

  def getError(self):
    """ Return the last error message seen while trying to connect to the broker. """
    return self.error

  def getBrokerId(self):
    """ Get broker's unique identifier (UUID) """
    return self.brokerId

  def getBrokerBank(self):
    """ Return the broker-bank value.  This is the value that the broker assigns to
        objects within its control.  This value appears as a field in the ObjectId
        of objects created by agents controlled by this broker. """
    return 1

  def getAgent(self, brokerBank, agentBank):
    """ Return the agent object associated with a particular broker and agent bank value."""
    bankKey = str(agentBank)
    try:
      self.cv.acquire()
      if bankKey in self.agents:
        return self.agents[bankKey]
    finally:
      self.cv.release()
    return None

  def getBrokerAgent(self):
    return self.brokerAgent

  def getSessionId(self):
    """ Get the identifier of the AMQP session to the broker """
    return self.amqpSessionId

  def getAgents(self):
    """ Get the list of agents reachable via this broker """
    try:
      self.cv.acquire()
      return self.agents.values()
    finally:
      self.cv.release()

  def getAmqpSession(self):
    """ Get the AMQP session object for this connected broker. """
    return self.amqpSession

  def getUrl(self):
    """ """
    return BrokerURL(host=self.host, port=self.port)

  def getFullUrl(self, noAuthIfGuestDefault=True):
    """ """
    if self.ssl:
      scheme = "amqps"
    else:
      scheme = "amqp"
    if self.authUser == "" or \
          (noAuthIfGuestDefault and self.authUser == "guest" and self.authPass == "guest"):
      return BrokerURL(scheme=scheme, host=self.host, port=(self.port or 5672))
    else:
      return BrokerURL(scheme=scheme, user=self.authUser, password=self.authPass, host=self.host, port=(self.port or 5672))

  def __repr__(self):
    if self.connected:
      return "Broker connected at: %s" % self.getUrl()
    else:
      return "Disconnected Broker"

  def _setSequence(self, sequence, agent):
    try:
      self.cv.acquire()
      self.seqToAgentMap[sequence] = agent
    finally:
      self.cv.release()

  def _clearSequence(self, sequence):
    try:
      self.cv.acquire()
      self.seqToAgentMap.pop(sequence)
    finally:
      self.cv.release()

  def _tryToConnect(self):
    """ Connect to the broker.  Returns True if connection setup completes
    successfully, otherwise returns False and sets self.error/self.conn_exc
    with error info.  Does not raise exceptions.
    """
    self.error = None
    self.conn_exc = None
    try:
      try:
        self.cv.acquire()
        self.agents = {}
      finally:
        self.cv.release()

      self.topicBound = False
      self.syncInFlight = False
      self.syncRequest = 0
      self.syncResult = None
      self.reqsOutstanding = 1

      try:
        if self.amqpSession:
          self.amqpSession.close()
      except:
        pass
      self.amqpSession = None

      try:
        if self.conn:
          self.conn.close(5)
      except:
        pass
      self.conn = None

      sock = connect(self.host, self.port)
      sock.settimeout(5)
      oldTimeout = sock.gettimeout()
      sock.settimeout(self.connTimeout)
      connSock = None
      force_blocking = False
      if self.ssl:
        # Bug (QPID-4337): the "old" implementation of python SSL
        # fails if the socket is set to non-blocking (which settimeout()
        # may change).
        if sys.version_info[:2] < (2, 6):  # 2.6+ uses openssl - it's ok
          force_blocking = True
          sock.setblocking(1)
        certfile = None
        if 'ssl_certfile' in self.connectArgs:
          certfile = self.connectArgs['ssl_certfile']
        keyfile = None
        if 'ssl_keyfile' in self.connectArgs:
          keyfile = self.connectArgs['ssl_keyfile']
        connSock = ssl(sock, certfile=certfile, keyfile=keyfile)
      else:
        connSock = sock
      if not 'service' in self.connectArgs:
          self.connectArgs['service'] = 'qpidd'
      self.conn = Connection(connSock, username=self.authUser, password=self.authPass,
                             mechanism = self.mechanisms, host=self.host,
                             **self.connectArgs)
      def aborted():
        raise Timeout("Waiting for connection to be established with broker")
      oldAborted = self.conn.aborted
      self.conn.aborted = aborted
      self.conn.start()
      
      # Bug (QPID-4337): don't enable non-blocking (timeouts) for old SSL
      if not force_blocking:
        sock.settimeout(oldTimeout)
      self.conn.aborted = oldAborted
      uid = self.conn.user_id
      if uid.__class__ == tuple and len(uid) == 2:
        self.saslUser = uid[1]
      elif type(uid) is str:
        self.saslUser = uid;
      else:
        self.saslUser = None

      # prevent topic queues from filling up (and causing the agents to
      # disconnect) by discarding the oldest queued messages when full.
      topic_queue_options = {"qpid.policy_type":"ring"}

      self.replyName = "reply-%s" % self.amqpSessionId
      self.amqpSession = self.conn.session(self.amqpSessionId)
      self.amqpSession.timeout = self.sessTimeout
      self.amqpSession.auto_sync = True
      self.amqpSession.queue_declare(queue=self.replyName, exclusive=True, auto_delete=True)
      self.amqpSession.exchange_bind(exchange="amq.direct",
                                     queue=self.replyName, binding_key=self.replyName)
      self.amqpSession.message_subscribe(queue=self.replyName, destination="rdest",
                                         accept_mode=self.amqpSession.accept_mode.none,
                                         acquire_mode=self.amqpSession.acquire_mode.pre_acquired)
      self.amqpSession.incoming("rdest").listen(self._v1Cb, self._exceptionCb)
      self.amqpSession.message_set_flow_mode(destination="rdest", flow_mode=self.amqpSession.flow_mode.window)
      self.amqpSession.message_flow(destination="rdest", unit=self.amqpSession.credit_unit.byte, value=0xFFFFFFFFL)
      self.amqpSession.message_flow(destination="rdest", unit=self.amqpSession.credit_unit.message, value=200)

      self.topicName = "topic-%s" % self.amqpSessionId
      self.amqpSession.queue_declare(queue=self.topicName, exclusive=True,
                                     auto_delete=True,
                                     arguments=topic_queue_options)
      self.amqpSession.message_subscribe(queue=self.topicName, destination="tdest",
                                         accept_mode=self.amqpSession.accept_mode.none,
                                         acquire_mode=self.amqpSession.acquire_mode.pre_acquired)
      self.amqpSession.incoming("tdest").listen(self._v1Cb, self._exceptionCb)
      self.amqpSession.message_set_flow_mode(destination="tdest", flow_mode=self.amqpSession.flow_mode.window)
      self.amqpSession.message_flow(destination="tdest", unit=self.amqpSession.credit_unit.byte, value=0xFFFFFFFFL)
      self.amqpSession.message_flow(destination="tdest", unit=self.amqpSession.credit_unit.message, value=200)

      ##
      ## Check to see if the broker has QMFv2 exchanges configured
      ##
      direct_result = self.amqpSession.exchange_query("qmf.default.direct")
      topic_result = self.amqpSession.exchange_query("qmf.default.topic")
      self.brokerSupportsV2 = not (direct_result.not_found or topic_result.not_found)

      try:
        self.cv.acquire()
        self.agents = {}
        self.brokerAgent = Agent(self, 0, "BrokerAgent", isV2=self.brokerSupportsV2)
        self.agents['0'] = self.brokerAgent
      finally:
        self.cv.release()

      ##
      ## Set up connectivity for QMFv2
      ##
      if self.brokerSupportsV2:
        # set up 3 queues:
        # 1 direct queue - for responses destined to this console.
        # 2 topic queues - one for heartbeats (hb), one for unsolicited data
        # and event indications (ui).
        self.v2_direct_queue = "qmfc-v2-%s" % self.amqpSessionId
        self.amqpSession.queue_declare(queue=self.v2_direct_queue, exclusive=True, auto_delete=True)
        self.v2_topic_queue_ui = "qmfc-v2-ui-%s" % self.amqpSessionId
        self.amqpSession.queue_declare(queue=self.v2_topic_queue_ui,
                                       exclusive=True, auto_delete=True,
                                       arguments=topic_queue_options)
        self.v2_topic_queue_hb = "qmfc-v2-hb-%s" % self.amqpSessionId
        self.amqpSession.queue_declare(queue=self.v2_topic_queue_hb,
                                       exclusive=True, auto_delete=True,
                                       arguments=topic_queue_options)

        self.amqpSession.exchange_bind(exchange="qmf.default.direct",
                                       queue=self.v2_direct_queue, binding_key=self.v2_direct_queue)
        ## Other bindings here...

        self.amqpSession.message_subscribe(queue=self.v2_direct_queue, destination="v2dest",
                                           accept_mode=self.amqpSession.accept_mode.none,
                                           acquire_mode=self.amqpSession.acquire_mode.pre_acquired)
        self.amqpSession.incoming("v2dest").listen(self._v2Cb, self._exceptionCb)
        self.amqpSession.message_set_flow_mode(destination="v2dest", flow_mode=self.amqpSession.flow_mode.window)
        self.amqpSession.message_flow(destination="v2dest", unit=self.amqpSession.credit_unit.byte, value=0xFFFFFFFFL)
        self.amqpSession.message_flow(destination="v2dest", unit=self.amqpSession.credit_unit.message, value=50)

        self.amqpSession.message_subscribe(queue=self.v2_topic_queue_ui, destination="v2TopicUI",
                                           accept_mode=self.amqpSession.accept_mode.none,
                                           acquire_mode=self.amqpSession.acquire_mode.pre_acquired)
        self.amqpSession.incoming("v2TopicUI").listen(self._v2Cb, self._exceptionCb)
        self.amqpSession.message_set_flow_mode(destination="v2TopicUI", flow_mode=self.amqpSession.flow_mode.window)
        self.amqpSession.message_flow(destination="v2TopicUI", unit=self.amqpSession.credit_unit.byte, value=0xFFFFFFFFL)
        self.amqpSession.message_flow(destination="v2TopicUI", unit=self.amqpSession.credit_unit.message, value=25)


        self.amqpSession.message_subscribe(queue=self.v2_topic_queue_hb, destination="v2TopicHB",
                                           accept_mode=self.amqpSession.accept_mode.none,
                                           acquire_mode=self.amqpSession.acquire_mode.pre_acquired)
        self.amqpSession.incoming("v2TopicHB").listen(self._v2Cb, self._exceptionCb)
        self.amqpSession.message_set_flow_mode(destination="v2TopicHB", flow_mode=self.amqpSession.flow_mode.window)
        self.amqpSession.message_flow(destination="v2TopicHB", unit=self.amqpSession.credit_unit.byte, value=0xFFFFFFFFL)
        self.amqpSession.message_flow(destination="v2TopicHB", unit=self.amqpSession.credit_unit.message, value=100)

      codec = Codec()
      self._setHeader(codec, 'B')
      msg = self._message(codec.encoded)
      self._send(msg)

      return True  # connection complete

    except Exception, e:
      self.error = "Exception during connection setup: %s - %s" % (e.__class__.__name__, e)
      self.conn_exc = e
      if self.session.console:
        self.session.console.brokerConnectionFailed(self)
      return False     # connection failed

  def _updateAgent(self, obj):
    """
    Just received an object of class "org.apache.qpid.broker:agent", which
    represents a V1 agent.  Add or update the list of agent proxies.
    """
    bankKey = str(obj.agentBank)
    agent = None
    if obj._deleteTime == 0:
      try:
        self.cv.acquire()
        if bankKey not in self.agents:
          # add new agent only if label is not filtered
          if len(self.session.agent_filter) == 0 or obj.label in self.session.agent_filter:
            agent = Agent(self, obj.agentBank, obj.label)
            self.agents[bankKey] = agent
      finally:
        self.cv.release()
      if agent and self.session.console:
        self.session._newAgentCallback(agent)
    else:
      try:
        self.cv.acquire()
        agent = self.agents.pop(bankKey, None)
        if agent:
          agent.close()
      finally:
        self.cv.release()
      if agent and self.session.console:
        self.session._delAgentCallback(agent)

  def _addAgent(self, name, agent):
    try:
      self.cv.acquire()
      self.agents[name] = agent
    finally:
      self.cv.release()
    if self.session.console:
      self.session._newAgentCallback(agent)

  def _ageAgents(self):
    if (time() - self.last_age_check) < self.session.agent_heartbeat_min:
      # don't age if it's too soon
      return
    self.cv.acquire()
    try:
      to_delete = []
      to_notify = []
      for key in self.agents:
        if self.agents[key].isOld():
          to_delete.append(key)
      for key in to_delete:
        agent = self.agents.pop(key)
        agent.close()
        to_notify.append(agent)
      self.last_age_check = time()
    finally:
      self.cv.release()
    if self.session.console:
      for agent in to_notify:
        self.session._delAgentCallback(agent)

  def _v2SendAgentLocate(self, predicate=[]):
    """
    Broadcast an agent-locate request to cause all agents in the domain to tell us who they are.
    """
    # @todo: send locate only to those agents in agent_filter?
    dp = self.amqpSession.delivery_properties()
    dp.routing_key = "console.request.agent_locate"
    mp = self.amqpSession.message_properties()
    mp.content_type = "amqp/list"
    if self.saslUser:
      mp.user_id = self.saslUser
    mp.app_id = "qmf2"
    mp.reply_to = self.amqpSession.reply_to("qmf.default.direct", self.v2_direct_queue)
    mp.application_headers = {'qmf.opcode':'_agent_locate_request'}
    sendCodec = Codec()
    sendCodec.write_list(predicate)
    msg = Message(dp, mp, sendCodec.encoded)
    self._send(msg, "qmf.default.topic")

  def _setHeader(self, codec, opcode, seq=0):
    """ Compose the header of a management message. """
    codec.write_uint8(ord('A'))
    codec.write_uint8(ord('M'))
    codec.write_uint8(ord('2'))
    codec.write_uint8(ord(opcode))
    codec.write_uint32(seq)

  def _checkHeader(self, codec):
    """ Check the header of a management message and extract the opcode and class. """
    try:
      octet = chr(codec.read_uint8())
      if octet != 'A':
        return None, None
      octet = chr(codec.read_uint8())
      if octet != 'M':
        return None, None
      octet = chr(codec.read_uint8())
      if octet != '2':
        return None, None
      opcode = chr(codec.read_uint8())
      seq    = codec.read_uint32()
      return opcode, seq
    except:
      return None, None

  def _message (self, body, routing_key="broker", ttl=None):
    dp = self.amqpSession.delivery_properties()
    dp.routing_key = routing_key
    if ttl:
      dp.ttl = ttl
    mp = self.amqpSession.message_properties()
    mp.content_type = "x-application/qmf"
    if self.saslUser:
      mp.user_id = self.saslUser
    mp.reply_to = self.amqpSession.reply_to("amq.direct", self.replyName)
    return Message(dp, mp, body)

  def _send(self, msg, dest="qpid.management"):
    self.amqpSession.message_transfer(destination=dest, message=msg)

  def _disconnect(self, err_info=None):
    """ Called when the remote broker has disconnected. Re-initializes all
    state associated with the broker.
    """
    # notify any waiters, and callback
    self.cv.acquire()
    try:
      if err_info is not None:
        self.error = err_info
      _agents = self.agents
      self.agents = {}
      for agent in _agents.itervalues():
        agent.close()
      self.syncInFlight = False
      self.reqsOutstanding = 0
      self.cv.notifyAll()
    finally:
      self.cv.release()

    if self.session.console:
      for agent in _agents.itervalues():
        self.session._delAgentCallback(agent)

  def _shutdown(self, _timeout=10):
    """ Disconnect from a broker, and release its resources.   Errors are
    ignored.
    """
    if self.isAlive():
      # kick the thread
      self.canceled = True
      self.rcv_queue.put(Broker._q_item(Broker._q_item.type_wakeup, None))
      self.join(_timeout)

    # abort any pending transactions and delete agents
    self._disconnect("broker shutdown")

    try:
      if self.amqpSession:
        self.amqpSession.close();
    except:
      pass
    self.amqpSession = None
    try:
      if self.conn:
        self.conn.close(_timeout)
    except:
      pass
    self.conn = None
    self.connected = False

  def _waitForStable(self):
    try:
      self.cv.acquire()
      if not self.connected:
        return
      if self.reqsOutstanding == 0:
        return
      self.syncInFlight = True
      starttime = time()
      while self.reqsOutstanding != 0:
        self.cv.wait(self.SYNC_TIME)
        if time() - starttime > self.SYNC_TIME:
          raise RuntimeError("Timed out waiting for broker to synchronize")
    finally:
      self.cv.release()

  def _incOutstanding(self):
    try:
      self.cv.acquire()
      self.reqsOutstanding += 1
    finally:
      self.cv.release()

  def _decOutstanding(self):
    try:
      self.cv.acquire()
      self.reqsOutstanding -= 1
      if self.reqsOutstanding == 0 and not self.topicBound:
        self.topicBound = True
        for key in self.session.v1BindingKeyList:
          self.amqpSession.exchange_bind(exchange="qpid.management",
                                         queue=self.topicName, binding_key=key)
        if self.brokerSupportsV2:
          # do not drop heartbeat indications when under load from data
          # or event indications.  Put heartbeats on their own dedicated
          # queue.
          #
          for key in self.session.v2BindingKeyList:
            if key.startswith("agent.ind.heartbeat"):
              self.amqpSession.exchange_bind(exchange="qmf.default.topic",
                                             queue=self.v2_topic_queue_hb,
                                             binding_key=key)
            else:
              self.amqpSession.exchange_bind(exchange="qmf.default.topic",
                                             queue=self.v2_topic_queue_ui,
                                             binding_key=key)
          # solicit an agent locate now, after we bind to agent.ind.data,
          # because the agent locate will cause the agent to publish a
          # data indication - and now we're able to receive it!
          self._v2SendAgentLocate()


      if self.reqsOutstanding == 0 and self.syncInFlight:
        self.syncInFlight = False
        self.cv.notify()
    finally:
      self.cv.release()

  def _v1Cb(self, msg):
    """ Callback from session receive thread for V1 messages
    """
    self.rcv_queue.put(Broker._q_item(Broker._q_item.type_v1msg, msg))

  def _v1Dispatch(self, msg):
    try:
      self._v1DispatchProtected(msg)
    except Exception, e:
      print "EXCEPTION in Broker._v1Cb:", e
      import traceback
      traceback.print_exc()

  def _v1DispatchProtected(self, msg):
    """
    This is the general message handler for messages received via the QMFv1 exchanges.
    """
    try:
      agent = None
      agent_addr = None
      mp = msg.get("message_properties")
      ah = mp.application_headers
      if ah and 'qmf.agent' in ah:
        agent_addr = ah['qmf.agent']

      if not agent_addr:
        #
        # See if we can determine the agent identity from the routing key
        #
        dp = msg.get("delivery_properties")
        rkey = None
        if dp and dp.routing_key:
          rkey = dp.routing_key
          items = rkey.split('.')
          if len(items) >= 4:
            if items[0] == 'console' and items[3].isdigit():
              agent_addr = str(items[3])  # The QMFv1 Agent Bank
      if agent_addr != None and agent_addr in self.agents:
        agent = self.agents[agent_addr]

      codec = Codec(msg.body)
      alreadyTried = None
      while True:
        opcode, seq = self._checkHeader(codec)

        if not agent and not alreadyTried:
          alreadyTried = True
          try:
            self.cv.acquire()
            if seq in self.seqToAgentMap:
              agent = self.seqToAgentMap[seq]
          finally:
            self.cv.release()

        if   opcode == None: break
        if   opcode == 'b': self.session._handleBrokerResp      (self, codec, seq)
        elif opcode == 'p': self.session._handlePackageInd      (self, codec, seq)
        elif opcode == 'q': self.session._handleClassInd        (self, codec, seq)
        elif opcode == 's': self.session._handleSchemaResp      (self, codec, seq, agent_addr)
        elif opcode == 'h': self.session._handleHeartbeatInd    (self, codec, seq, msg)
        elif opcode == 'z': self.session._handleCommandComplete (self, codec, seq, agent)
        elif agent:
          agent._handleQmfV1Message(opcode, seq, mp, ah, codec)
          agent.touch() # mark agent as being alive

    finally:  # always ack the message!
      try:
        # ignore failures as the session may be shutting down...
        self.amqpSession.receiver._completed.add(msg.id)
        self.amqpSession.channel.session_completed(self.amqpSession.receiver._completed)
      except:
        pass


  def _v2Cb(self, msg):
    """ Callback from session receive thread for V2 messages
    """
    self.rcv_queue.put(Broker._q_item(Broker._q_item.type_v2msg, msg))

  def _v2Dispatch(self, msg):
    try:
      self._v2DispatchProtected(msg)
    except Exception, e:
      print "EXCEPTION in Broker._v2Cb:", e
      import traceback
      traceback.print_exc()

  def _v2DispatchProtected(self, msg):
    """
    This is the general message handler for messages received via QMFv2 exchanges.
    """
    try:
      mp = msg.get("message_properties")
      ah = mp["application_headers"]
      codec = Codec(msg.body)
  
      if 'qmf.opcode' in ah:
        opcode = ah['qmf.opcode']
        if mp.content_type == "amqp/list":
          try:
            content = codec.read_list()
            if not content:
              content = []
          except:
            # malformed list - ignore
            content = None
        elif mp.content_type == "amqp/map":
          try:
            content = codec.read_map()
            if not content:
              content = {}
          except:
            # malformed map - ignore
            content = None
        else:
          content = None

        if content != None:
          ##
          ## Directly handle agent heartbeats and agent locate responses as these are broker-scope (they are
          ## used to maintain the broker's list of agent proxies.
          ##
          if   opcode == '_agent_heartbeat_indication': self.session._v2HandleHeartbeatInd(self, mp, ah, content)
          elif opcode == '_agent_locate_response':      self.session._v2HandleAgentLocateRsp(self, mp, ah, content)
          else:
            ##
            ## All other opcodes are agent-scope and are forwarded to the agent proxy representing the sender
            ## of the message.
            ##
            # the broker's agent is mapped to index ['0']
            agentName = ah['qmf.agent']
            v = agentName.split(":")
            if agentName == 'broker' or (len(v) >= 2 and v[0] == 'apache.org'
                                         and v[1] == 'qpidd'):
                agentName = '0'
            if agentName in self.agents:
              agent = self.agents[agentName]
              agent._handleQmfV2Message(opcode, mp, ah, content)
              agent.touch()

    finally:  # always ack the message!
      try:
        # ignore failures as the session may be shutting down...
        self.amqpSession.receiver._completed.add(msg.id)
        self.amqpSession.channel.session_completed(self.amqpSession.receiver._completed)
      except:
        pass

  def _exceptionCb(self, data):
    """ Exception notification callback from session receive thread.
    """
    self.cv.acquire()
    try:
      self.connected = False
      self.error = "exception received from messaging layer: %s" % str(data)
    finally:
      self.cv.release()
    self.rcv_queue.put(Broker._q_item(Broker._q_item.type_wakeup, None))

  def run(self):
    """ Main body of the running thread. """

    # First, attempt a connection.  In the unmanaged case,
    # failure to connect needs to cause the Broker()
    # constructor to raise an exception.
    delay = self.DELAY_MIN
    while not self.canceled:
      if self._tryToConnect(): # connection up
        break
      # unmanaged connection - fail & wake up constructor
      if not self.session.manageConnections:
        self.ready.release()
        return
      # managed connection - try again
      count = 0
      while not self.canceled and count < delay:
        sleep(1)
        count += 1
      if delay < self.DELAY_MAX:
        delay *= self.DELAY_FACTOR

    if self.canceled:
      self.ready.release()
      return

    # connection successful!
    self.cv.acquire()
    try:
      self.connected = True
    finally:
      self.cv.release()

    self.session._handleBrokerConnect(self)
    self.ready.release()

    while not self.canceled:

      try:
        item = self.rcv_queue.get(timeout=self.session.agent_heartbeat_min)
      except Empty:
        item = None

      while not self.canceled and item is not None:

        if not self.connected:
          # connection failure
          while item:
            # drain the queue
            try:
              item = self.rcv_queue.get(block=False)
            except Empty:
              item = None
              break

          self._disconnect()  # clean up any pending agents
          self.session._handleError(self.error)
          self.session._handleBrokerDisconnect(self)

          if not self.session.manageConnections:
            return  # do not attempt recovery

          # retry connection setup
          delay = self.DELAY_MIN
          while not self.canceled:
            if self._tryToConnect():
              break
            # managed connection - try again
            count = 0
            while not self.canceled and count < delay:
              sleep(1)
              count += 1
            if delay < self.DELAY_MAX:
              delay *= self.DELAY_FACTOR

          if self.canceled:
            return

          # connection successful!
          self.cv.acquire()
          try:
            self.connected = True
          finally:
            self.cv.release()

          self.session._handleBrokerConnect(self)

        elif item.typecode == Broker._q_item.type_v1msg:
          self._v1Dispatch(item.data)
        elif item.typecode == Broker._q_item.type_v2msg:
          self._v2Dispatch(item.data)

        try:
          item = self.rcv_queue.get(block=False)
        except Empty:
          item = None

      # queue drained, age the agents...
      if not self.canceled:
        self._ageAgents()

#===================================================================================================
# Agent
#===================================================================================================
class Agent:
  """
  This class represents a proxy for a remote agent being managed
  """
  def __init__(self, broker, agentBank, label, isV2=False, interval=0):
    self.broker = broker
    self.session = broker.session
    self.schemaCache = self.session.schemaCache
    self.brokerBank = broker.getBrokerBank()
    self.agentBank = str(agentBank)
    self.label = label
    self.isV2 = isV2
    self.heartbeatInterval = 0
    if interval:
      if interval < self.session.agent_heartbeat_min:
        self.heartbeatInterval = self.session.agent_heartbeat_min
      else:
        self.heartbeatInterval = interval
    self.lock = Lock()
    self.seqMgr = self.session.seqMgr
    self.contextMap = {}
    self.unsolicitedContext = RequestContext(self, self)
    self.lastSeenTime = time()
    self.closed = None
    self.epoch = 0
    self.schema_timestamp = None


  def _checkClosed(self):
    if self.closed:
      raise Exception("Agent is disconnected")


  def __call__(self, **kwargs):
    """
    This is the handler for unsolicited stuff received from the agent
    """
    if 'qmf_object' in kwargs:
      if self.session.console:
        obj = kwargs['qmf_object']
        if self.session.class_filter and obj.getClassKey():
          # slow path: check classKey against event_filter
          pname = obj.getClassKey().getPackageName()
          cname = obj.getClassKey().getClassName()
          if ((pname, cname) not in self.session.class_filter
              and (pname, None) not in self.session.class_filter):
              return
        if obj.getProperties():
            self.session.console.objectProps(self.broker, obj)
        if obj.getStatistics():
            # QMFv2 objects may also contain statistic updates
            self.session.console.objectStats(self.broker, obj)
    elif 'qmf_object_stats' in kwargs:
      if self.session.console:
        obj = kwargs['qmf_object_stats']
        if len(self.session.class_filter) == 0:
          self.session.console.objectStats(self.broker, obj)
        elif obj.getClassKey():
          # slow path: check classKey against event_filter
          pname = obj.getClassKey().getPackageName()
          cname = obj.getClassKey().getClassName()
          if ((pname, cname) in self.session.class_filter
              or (pname, None) in self.session.class_filter):
            self.session.console.objectStats(self.broker, obj)
    elif 'qmf_event' in kwargs:
      if self.session.console:
        event = kwargs['qmf_event']
        if len(self.session.event_filter) == 0:
          self.session.console.event(self.broker, event)
        elif event.classKey:
          # slow path: check classKey against event_filter
          pname = event.classKey.getPackageName()
          ename = event.classKey.getClassName()
          if ((pname, ename) in self.session.event_filter
              or (pname, None) in self.session.event_filter):
            self.session.console.event(self.broker, event)
    elif 'qmf_schema_id' in kwargs:
      ckey = kwargs['qmf_schema_id']
      new_pkg, new_cls = self.session.schemaCache.declareClass(ckey)
      if self.session.console:
        if new_pkg:
          self.session._newPackageCallback(ckey.getPackageName())
        if new_cls:
          # translate V2's string based type value to legacy
          # integer value for backward compatibility
          cls_type = ckey.getType()
          if str(cls_type) == ckey.TYPE_DATA:
            cls_type = 1
          elif str(cls_type) == ckey.TYPE_EVENT:
            cls_type = 2
          self.session._newClassCallback(cls_type, ckey)

  def touch(self):
    if self.heartbeatInterval:
      self.lastSeenTime = time()


  def setEpoch(self, epoch):
    self.epoch = epoch

  def update_schema_timestamp(self, timestamp):
    """ Check the latest schema timestamp from the agent V2 heartbeat.  Issue a
    query for all packages & classes should the timestamp change.
    """
    self.lock.acquire()
    try:
      if self.schema_timestamp == timestamp:
        return
      self.schema_timestamp = timestamp

      context = RequestContext(self, self)
      sequence = self.seqMgr._reserve(context)

      self.contextMap[sequence] = context
      context.setSequence(sequence)

    finally:
      self.lock.release()

    self._v2SendSchemaIdQuery(sequence, {})


  def epochMismatch(self, epoch):
    if epoch == 0 or self.epoch == 0:
      return None
    if epoch == self.epoch:
      return None
    return True


  def isOld(self):
    if self.heartbeatInterval == 0:
      return None
    if time() - self.lastSeenTime > (self.session.agent_heartbeat_miss * self.heartbeatInterval):
      return True
    return None


  def close(self):
    self.closed = True
    copy = {}
    try:
      self.lock.acquire()
      for seq in self.contextMap:
        copy[seq] = self.contextMap[seq]
    finally:
      self.lock.release()

    for seq in copy:
      context = copy[seq]
      context.cancel("Agent disconnected")
      self.seqMgr._release(seq)


  def __repr__(self):
    if self.isV2:
      ver = "v2"
    else:
      ver = "v1"
    return "Agent(%s) at bank %d.%s (%s)" % (ver, self.brokerBank, self.agentBank, self.label)


  def getBroker(self):
    return self.broker


  def getBrokerBank(self):
    return self.brokerBank


  def getAgentBank(self):
    return self.agentBank


  def getV2RoutingKey(self):
    if self.agentBank == '0':
      return 'broker'
    return self.agentBank


  def getObjects(self, notifiable=None, **kwargs):
    """ Get a list of objects from QMF agents.
    All arguments are passed by name(keyword).

    If 'notifiable' is None (default), this call will block until completion or timeout.
    If supplied, notifiable is assumed to be a callable object that will be called when the
    list of queried objects arrives.  The single argument to the call shall be a list of
    the returned objects.

    The class for queried objects may be specified in one of the following ways:

    _schema = <schema> - supply a schema object returned from getSchema.
    _key = <key>       - supply a classKey from the list returned by getClasses.
    _class = <name>    - supply a class name as a string.  If the class name exists
                         in multiple packages, a _package argument may also be supplied.
    _objectId = <id>   - get the object referenced by the object-id

    The default timeout for this synchronous operation is 60 seconds.  To change the timeout,
    use the following argument:

    _timeout = <time in seconds>

    If additional arguments are supplied, they are used as property selectors.  For example,
    if the argument name="test" is supplied, only objects whose "name" property is "test"
    will be returned in the result.
    """
    self._checkClosed()
    if notifiable:
      if not callable(notifiable):
        raise Exception("notifiable object must be callable")

    #
    # Isolate the selectors from the kwargs
    #
    selectors = {}
    for key in kwargs:
      value = kwargs[key]
      if key[0] != '_':
        selectors[key] = value

    #
    # Allocate a context to track this asynchronous request.
    #
    context = RequestContext(self, notifiable, selectors)
    sequence = self.seqMgr._reserve(context)
    try:
      self.lock.acquire()
      self.contextMap[sequence] = context
      context.setSequence(sequence)
    finally:
      self.lock.release()

    #
    # Compose and send the query message to the agent using the appropriate protocol for the
    # agent's QMF version.
    #
    if self.isV2:
      self._v2SendGetQuery(sequence, kwargs)
    else:
      self.broker._setSequence(sequence, self)
      self._v1SendGetQuery(sequence, kwargs)

    #
    # If this is a synchronous call, block and wait for completion.
    #
    if not notifiable:
      timeout = 60
      if '_timeout' in kwargs:
        timeout = kwargs['_timeout']
      context.waitForSignal(timeout)
      if context.exception:
        raise Exception(context.exception)
      result = context.queryResults
      return result


  def _clearContext(self, sequence):
    try:
      self.lock.acquire()
      try:
        self.contextMap.pop(sequence)
        self.seqMgr._release(sequence)
      except KeyError:
        pass   # @todo - shouldn't happen, log a warning.
    finally:
      self.lock.release()


  def _schemaInfoFromV2Agent(self):
    """
    We have just received new schema information from this agent.  Check to see if there's
    more work that can now be done.
    """
    try:
      self.lock.acquire()
      copy_of_map = {}
      for item in self.contextMap:
        copy_of_map[item] = self.contextMap[item]
    finally:
      self.lock.release()

    self.unsolicitedContext.reprocess()
    for context in copy_of_map:
      copy_of_map[context].reprocess()


  def _handleV1Completion(self, sequence, code, text):
    """
    Called if one of this agent's V1 commands completed
    """
    context = None
    try:
      self.lock.acquire()
      if sequence in self.contextMap:
        context = self.contextMap[sequence]
    finally:
      self.lock.release()

    if context:
      if code != 0:
        ex = "Error %d: %s" % (code, text)
        context.setException(ex)
      context.signal()
    self.broker._clearSequence(sequence)


  def _v1HandleMethodResp(self, codec, seq):
    """
    Handle a QMFv1 method response
    """
    code = codec.read_uint32()
    text = codec.read_str16()
    outArgs = {}
    self.broker._clearSequence(seq)
    pair = self.seqMgr._release(seq)
    if pair == None:
      return
    method, synchronous = pair
    if code == 0:
      for arg in method.arguments:
        if arg.dir.find("O") != -1:
          outArgs[arg.name] = self.session._decodeValue(codec, arg.type, self.broker)
    result = MethodResult(code, text, outArgs)
    if synchronous:
      try:
        self.broker.cv.acquire()
        self.broker.syncResult = result
        self.broker.syncInFlight = False
        self.broker.cv.notify()
      finally:
        self.broker.cv.release()
    else:
      if self.session.console:
        self.session.console.methodResponse(self.broker, seq, result)


  def _v1HandleEventInd(self, codec, seq):
    """
    Handle a QMFv1 event indication
    """
    event = Event(self, codec)
    self.unsolicitedContext.doEvent(event)


  def _v1HandleContentInd(self, codec, sequence, prop=False, stat=False):
    """
    Handle a QMFv1 content indication
    """
    classKey = ClassKey(codec)
    schema = self.schemaCache.getSchema(classKey)
    if not schema:
      return

    obj = Object(self, schema, codec, prop, stat)
    if classKey.getPackageName() == "org.apache.qpid.broker" and classKey.getClassName() == "agent" and prop:
      self.broker._updateAgent(obj)

    context = self.unsolicitedContext
    try:
      self.lock.acquire()
      if sequence in self.contextMap:
        context = self.contextMap[sequence]
    finally:
      self.lock.release()

    context.addV1QueryResult(obj, prop, stat)


  def _v2HandleDataInd(self, mp, ah, content):
    """
    Handle a QMFv2 data indication from the agent.  Note: called from context
    of the Broker thread.
    """
    if content.__class__ != list:
      return

    if mp.correlation_id:
      try:
        self.lock.acquire()
        sequence = int(mp.correlation_id)
        if sequence not in self.contextMap:
          return
        context = self.contextMap[sequence]
      finally:
        self.lock.release()
    else:
      context = self.unsolicitedContext

    kind = "_data"
    if "qmf.content" in ah:
      kind = ah["qmf.content"]
    if kind == "_data":
      for omap in content:
        context.addV2QueryResult(omap)
      context.processV2Data()
      if 'partial' not in ah:
        context.signal()

    elif kind == "_event":
      for omap in content:
        event = Event(self, v2Map=omap)
        if event.classKey is None or event.schema:
          # schema optional or present
          context.doEvent(event)
        else:
          # schema not optional and not present
          if context.addPendingEvent(event):
            self._v2SendSchemaRequest(event.classKey)

    elif kind == "_schema_id":
      for sid in content:
        try:
          ckey = ClassKey(sid)
        except:
          # @todo: log error
          ckey = None
        if ckey is not None:
          # @todo: for now, the application cannot directly send a query for
          # _schema_id.  This request _must_ have been initiated by the framework
          # in order to update the schema cache.
          context.notifiable(qmf_schema_id=ckey)


  def _v2HandleMethodResp(self, mp, ah, content):
    """
    Handle a QMFv2 method response from the agent
    """
    context = None
    sequence = None
    if mp.correlation_id:
      try:
        self.lock.acquire()
        seq = int(mp.correlation_id)
      finally:
        self.lock.release()
    else:
      return

    pair = self.seqMgr._release(seq)
    if pair == None:
      return
    method, synchronous = pair

    result = MethodResult(0, 'OK', content['_arguments'])
    if synchronous:
      try:
        self.broker.cv.acquire()
        self.broker.syncResult = result
        self.broker.syncInFlight = False
        self.broker.cv.notify()
      finally:
        self.broker.cv.release()
    else:
      if self.session.console:
        self.session.console.methodResponse(self.broker, seq, result)

  def _v2HandleException(self, mp, ah, content):
    """
    Handle a QMFv2 exception
    """
    context = None
    if mp.correlation_id:
      try:
        self.lock.acquire()
        seq = int(mp.correlation_id)
      finally:
        self.lock.release()
    else:
      return

    values = {}
    if '_values' in content:
      values = content['_values']

    code = 7
    text = "error"
    if 'error_code' in values:
      code = values['error_code']
    if 'error_text' in values:
      text = values['error_text']

    pair = self.seqMgr._release(seq)
    if pair == None:
      return

    if pair.__class__ == RequestContext:
      pair.cancel(text)
      return

    method, synchronous = pair

    result = MethodResult(code, text, {})
    if synchronous:
      try:
        self.broker.cv.acquire()
        self.broker.syncResult = result
        self.broker.syncInFlight = False
        self.broker.cv.notify()
      finally:
        self.broker.cv.release()
    else:
      if self.session.console:
        self.session.console.methodResponse(self.broker, seq, result)


  def _v1SendGetQuery(self, sequence, kwargs):
    """
    Send a get query to a QMFv1 agent.
    """
    #
    # Build the query map
    #
    query = {}
    if '_class' in kwargs:
      query['_class'] = kwargs['_class']
      if '_package' in kwargs:
        query['_package'] = kwargs['_package']
    elif '_key' in kwargs:
      key = kwargs['_key']
      query['_class'] = key.getClassName()
      query['_package'] = key.getPackageName()
    elif '_objectId' in kwargs:
      query['_objectid'] = kwargs['_objectId'].__repr__()

    #
    # Construct and transmit the message
    #
    sendCodec = Codec()
    self.broker._setHeader(sendCodec, 'G', sequence)
    sendCodec.write_map(query)
    smsg = self.broker._message(sendCodec.encoded, "agent.%d.%s" % (self.brokerBank, self.agentBank))
    self.broker._send(smsg)


  def _v2SendQuery(self, query, sequence):
    """
    Given a query map, construct and send a V2 Query message.
    """
    dp = self.broker.amqpSession.delivery_properties()
    dp.routing_key = self.getV2RoutingKey()
    mp = self.broker.amqpSession.message_properties()
    mp.content_type = "amqp/map"
    if self.broker.saslUser:
      mp.user_id = self.broker.saslUser
    mp.correlation_id = str(sequence)
    mp.app_id = "qmf2"
    mp.reply_to = self.broker.amqpSession.reply_to("qmf.default.direct", self.broker.v2_direct_queue)
    mp.application_headers = {'qmf.opcode':'_query_request'}
    sendCodec = Codec()
    sendCodec.write_map(query)
    msg = Message(dp, mp, sendCodec.encoded)
    self.broker._send(msg, "qmf.default.direct")


  def _v2SendGetQuery(self, sequence, kwargs):
    """
    Send a get query to a QMFv2 agent.
    """
    #
    # Build the query map
    #
    query = {'_what': 'OBJECT'}
    if '_class' in kwargs:
      schemaMap = {'_class_name': kwargs['_class']}
      if '_package' in kwargs:
        schemaMap['_package_name'] = kwargs['_package']
      query['_schema_id'] = schemaMap
    elif '_key' in kwargs:
      query['_schema_id'] = kwargs['_key'].asMap()
    elif '_objectId' in kwargs:
      query['_object_id'] = kwargs['_objectId'].asMap()

    self._v2SendQuery(query, sequence)


  def _v2SendSchemaIdQuery(self, sequence, kwargs):
    """
    Send a query for all schema ids to a QMFv2 agent.
    """
    #
    # Build the query map
    #
    query = {'_what': 'SCHEMA_ID'}
    # @todo - predicate support. For now, return all known schema ids.

    self._v2SendQuery(query, sequence)


  def _v2SendSchemaRequest(self, schemaId):
    """
    Send a query to an agent to request details on a particular schema class.
    IMPORTANT:  This function currently sends a QMFv1 schema-request to the address of
                the agent.  The agent will send its response to amq.direct/<our-key>.
                Eventually, this will be converted to a proper QMFv2 schema query.
    """
    sendCodec = Codec()
    seq = self.seqMgr._reserve(None)
    self.broker._setHeader(sendCodec, 'S', seq)
    schemaId.encode(sendCodec)
    smsg = self.broker._message(sendCodec.encoded, self.agentBank)
    self.broker._send(smsg, "qmf.default.direct")


  def _handleQmfV1Message(self, opcode, seq, mp, ah, codec):
    """
    Process QMFv1 messages arriving from an agent.  Note well: this method is
    called from the context of the Broker thread.
    """
    if   opcode == 'm': self._v1HandleMethodResp(codec, seq)
    elif opcode == 'e': self._v1HandleEventInd(codec, seq)
    elif opcode == 'c': self._v1HandleContentInd(codec, seq, prop=True)
    elif opcode == 'i': self._v1HandleContentInd(codec, seq, stat=True)
    elif opcode == 'g': self._v1HandleContentInd(codec, seq, prop=True, stat=True)


  def _handleQmfV2Message(self, opcode, mp, ah, content):
    """
    Process QMFv2 messages arriving from an agent.  Note well: this method is
    called from the context of the Broker thread.
    """
    if   opcode == '_data_indication': self._v2HandleDataInd(mp, ah, content)
    elif opcode == '_query_response':  self._v2HandleDataInd(mp, ah, content)
    elif opcode == '_method_response': self._v2HandleMethodResp(mp, ah, content)
    elif opcode == '_exception':       self._v2HandleException(mp, ah, content)


#===================================================================================================
# RequestContext
#===================================================================================================
class RequestContext(object):
  """
  This class tracks an asynchronous request sent to an agent.
  TODO: Add logic for client-side selection and filtering deleted objects from get-queries
  """
  def __init__(self, agent, notifiable, selectors={}):
    self.sequence = None
    self.agent = agent
    self.schemaCache = self.agent.schemaCache
    self.notifiable = notifiable
    self.selectors = selectors
    self.startTime = time()
    self.rawQueryResults = []
    self.queryResults = []
    self.pendingEvents = {}
    self.exception = None
    self.waitingForSchema = None
    self.pendingSignal = None
    self.cv = Condition()
    self.blocked = notifiable == None


  def setSequence(self, sequence):
    self.sequence = sequence


  def addV1QueryResult(self, data, has_props, has_stats):
    values = {}
    if has_props:
      for prop, val in data.getProperties():
        values[prop.name] = val
    if has_stats:
      for stat, val in data.getStatistics():
        values[stat.name] = val
    for key in values:
      val = values[key]
      if key in self.selectors and val != self.selectors[key]:
        return

    if self.notifiable:
      if has_props:
        self.notifiable(qmf_object=data)
      if has_stats:
        self.notifiable(qmf_object_stats=data)
    else:
      self.queryResults.append(data)


  def addV2QueryResult(self, data):
    values = data['_values']
    for key in values:
      val = values[key]
      if key in self.selectors:
        sel_val = self.selectors[key]
        if sel_val.__class__ == ObjectId:
          val = ObjectId(val, agentName=self.agent.getAgentBank())
        if val != sel_val:
          return
    self.rawQueryResults.append(data)

  def addPendingEvent(self, event):
    """ Stores a received event that is pending a schema.  Returns True if this
    event is the first instance of a given schema identifier.
    """
    self.cv.acquire()
    try:
      if event.classKey in self.pendingEvents:
        self.pendingEvents[event.classKey].append((event, time()))
        return False
      self.pendingEvents[event.classKey] = [(event, time())]
      return True
    finally:
      self.cv.release()

  def processPendingEvents(self):
    """ Walk the pending events looking for schemas that are now
    available. Remove any events that now have schema, and process them.
    """
    keysToDelete = []
    events = []
    self.cv.acquire()
    try:
      for key in self.pendingEvents.iterkeys():
        schema = self.schemaCache.getSchema(key)
        if schema:
          keysToDelete.append(key)
          for item in self.pendingEvents[key]:
            # item is (timestamp, event-obj) tuple.
            # hack: I have no idea what a valid lifetime for an event
            # should be. 60 seconds???
            if (time() - item[1]) < 60:
              item[0].schema = schema
              events.append(item[0])
      for key in keysToDelete:
        self.pendingEvents.pop(key)
    finally:
      self.cv.release()
    for event in events:
      self.doEvent(event)

  def doEvent(self, data):
    if self.notifiable:
      self.notifiable(qmf_event=data)


  def setException(self, ex):
    self.exception = ex


  def getAge(self):
    return time() - self.startTime


  def cancel(self, exception):
    self.setException(exception)
    try:
      self.cv.acquire()
      self.blocked = None
      self.waitingForSchema = None
      self.cv.notify()
    finally:
      self.cv.release()
    self._complete()


  def waitForSignal(self, timeout):
    try:
      self.cv.acquire()
      while self.blocked:
        if (time() - self.startTime) > timeout:
          self.exception = "Request timed out after %d seconds" % timeout
          return
        self.cv.wait(1)
    finally:
      self.cv.release()


  def signal(self):
    try:
      self.cv.acquire()
      if self.waitingForSchema:
        self.pendingSignal = True
        return
      else:
        self.blocked = None
        self.cv.notify()
    finally:
      self.cv.release()
    self._complete()


  def _complete(self):
    if self.notifiable:
      if self.exception:
        self.notifiable(qmf_exception=self.exception)
      else:
        self.notifiable(qmf_complete=True)

    if self.sequence:
      self.agent._clearContext(self.sequence)


  def processV2Data(self):
    """
    Attempt to make progress on the entries in the raw_query_results queue.  If an entry has a schema
    that is in our schema cache, process it.  Otherwise, send a request for the schema information
    to the agent that manages the object.
    """
    schemaId = None
    queryResults = []
    try:
      self.cv.acquire()
      if self.waitingForSchema:
        return
      while (not self.waitingForSchema) and len(self.rawQueryResults) > 0:
        head = self.rawQueryResults[0]
        schemaId = self._getSchemaIdforV2ObjectLH(head)
        schema = self.schemaCache.getSchema(schemaId)
        if schema:
          obj = Object(self.agent, schema, v2Map=head, agentName=self.agent.agentBank)
          queryResults.append(obj)
          self.rawQueryResults.pop(0)
        else:
          self.waitingForSchema = True
    finally:
      self.cv.release()

    if self.waitingForSchema:
      self.agent._v2SendSchemaRequest(schemaId)

    for result in queryResults:
      key = result.getClassKey()
      if key.getPackageName() == "org.apache.qpid.broker" and key.getClassName() == "agent":
        self.agent.broker._updateAgent(result)
      if self.notifiable:
        self.notifiable(qmf_object=result)
      else:
        self.queryResults.append(result)

    complete = None
    try:
      self.cv.acquire()
      if not self.waitingForSchema and self.pendingSignal:
        self.blocked = None
        self.cv.notify()
        complete = True
    finally:
      self.cv.release()

    if complete:
      self._complete()


  def reprocess(self):
    """
    New schema information has been added to the schema-cache.  Clear our 'waiting' status
    and see if we can make more progress on any pending inbound events/objects.
    """
    try:
      self.cv.acquire()
      self.waitingForSchema = None
    finally:
      self.cv.release()
    self.processV2Data()
    self.processPendingEvents()

  def _getSchemaIdforV2ObjectLH(self, data):
    """
    Given a data map, extract the schema-identifier.
    """
    if data.__class__ != dict:
      return None
    if '_schema_id' in data:
      return ClassKey(data['_schema_id'])
    return None


#===================================================================================================
# Event
#===================================================================================================
class Event:
  """ """
  def __init__(self, agent, codec=None, v2Map=None):
    self.agent = agent
    self.session = agent.session
    self.broker  = agent.broker

    if isinstance(v2Map,dict):
      self.isV2 = True
      self.classKey = None
      self.schema = None
      try:
        self.arguments = v2Map["_values"]
        self.timestamp = long(v2Map["_timestamp"])
        self.severity = v2Map["_severity"]
        if "_schema_id" in v2Map:
          self.classKey = ClassKey(v2Map["_schema_id"])
          self.classKey._setType(ClassKey.TYPE_EVENT)
      except:
        raise Exception("Invalid event object: %s " % str(v2Map))
      if self.classKey is not None:
        self.schema = self.session.schemaCache.getSchema(self.classKey)

    elif codec is not None:
      self.isV2 = None
      self.classKey = ClassKey(codec)
      self.classKey._setType(ClassKey.TYPE_EVENT)
      self.timestamp = codec.read_int64()
      self.severity = codec.read_uint8()
      self.arguments = {}
      self.schema = self.session.schemaCache.getSchema(self.classKey)
      if not self.schema:
        return
      for arg in self.schema.arguments:
        self.arguments[arg.name] = self.session._decodeValue(codec, arg.type,
                                                             self.broker)
    else:
      raise Exception("No constructor for event object.")


  def __repr__(self):
    if self.schema == None:
      return "<uninterpretable>"
    out = strftime("%c", gmtime(self.timestamp / 1000000000))
    out += " " + self._sevName() + " " + self.classKey.getPackageName() + ":" + self.classKey.getClassName()
    out += " broker=" + str(self.broker.getUrl())
    for arg in self.schema.arguments:
      disp = self.session._displayValue(self.arguments[arg.name], arg.type).encode("utf8")
      if " " in disp:
        disp = "\"" + disp + "\""
      out += " " + arg.name + "=" + disp
    return out

  def _sevName(self):
    if self.severity == 0 : return "EMER "
    if self.severity == 1 : return "ALERT"
    if self.severity == 2 : return "CRIT "
    if self.severity == 3 : return "ERROR"
    if self.severity == 4 : return "WARN "
    if self.severity == 5 : return "NOTIC"
    if self.severity == 6 : return "INFO "
    if self.severity == 7 : return "DEBUG"
    return "INV-%d" % self.severity

  def getClassKey(self):
    return self.classKey

  def getArguments(self):
    return self.arguments

  def getTimestamp(self):
    return self.timestamp

  def getSchema(self):
    return self.schema


#===================================================================================================
# SequenceManager
#===================================================================================================
class SequenceManager:
  """ Manage sequence numbers for asynchronous method calls """
  def __init__(self):
    self.lock     = Lock()
    self.sequence = long(time())  # pseudo-randomize the start
    self.pending  = {}

  def _reserve(self, data):
    """ Reserve a unique sequence number """
    try:
      self.lock.acquire()
      result = self.sequence
      self.sequence = self.sequence + 1
      self.pending[result] = data
    finally:
      self.lock.release()
    return result

  def _release(self, seq):
    """ Release a reserved sequence number """
    data = None
    try:
      self.lock.acquire()
      if seq in self.pending:
        data = self.pending[seq]
        del self.pending[seq]
    finally:
      self.lock.release()
    return data


#===================================================================================================
# DebugConsole
#===================================================================================================
class DebugConsole(Console):
  """ """
  def brokerConnected(self, broker):
    print "brokerConnected:", broker

  def brokerConnectionFailed(self, broker):
    print "brokerConnectionFailed:", broker

  def brokerDisconnected(self, broker):
    print "brokerDisconnected:", broker

  def newPackage(self, name):
    print "newPackage:", name

  def newClass(self, kind, classKey):
    print "newClass:", kind, classKey

  def newAgent(self, agent):
    print "newAgent:", agent

  def delAgent(self, agent):
    print "delAgent:", agent

  def objectProps(self, broker, record):
    print "objectProps:", record

  def objectStats(self, broker, record):
    print "objectStats:", record

  def event(self, broker, event):
    print "event:", event

  def heartbeat(self, agent, timestamp):
    print "heartbeat:", agent

  def brokerInfo(self, broker):
    print "brokerInfo:", broker

