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

import warnings
warnings.warn("The qmf2 module is deprecated.  It will be removed in the future.",
              Warning, stacklevel=2)

import cqmf2
import qpid_messaging
from threading import Thread
import time

#===================================================================================================
# CONSTANTS
#===================================================================================================
SCHEMA_TYPE_DATA  = cqmf2.SCHEMA_TYPE_DATA
SCHEMA_TYPE_EVENT = cqmf2.SCHEMA_TYPE_EVENT

SCHEMA_DATA_VOID   = cqmf2.SCHEMA_DATA_VOID
SCHEMA_DATA_BOOL   = cqmf2.SCHEMA_DATA_BOOL
SCHEMA_DATA_INT    = cqmf2.SCHEMA_DATA_INT
SCHEMA_DATA_FLOAT  = cqmf2.SCHEMA_DATA_FLOAT
SCHEMA_DATA_STRING = cqmf2.SCHEMA_DATA_STRING
SCHEMA_DATA_MAP    = cqmf2.SCHEMA_DATA_MAP
SCHEMA_DATA_LIST   = cqmf2.SCHEMA_DATA_LIST
SCHEMA_DATA_UUID   = cqmf2.SCHEMA_DATA_UUID

ACCESS_READ_CREATE = cqmf2.ACCESS_READ_CREATE
ACCESS_READ_WRITE  = cqmf2.ACCESS_READ_WRITE
ACCESS_READ_ONLY   = cqmf2.ACCESS_READ_ONLY

DIR_IN     = cqmf2.DIR_IN
DIR_OUT    = cqmf2.DIR_OUT
DIR_IN_OUT = cqmf2.DIR_IN_OUT

SEV_EMERG  = cqmf2.SEV_EMERG
SEV_ALERT  = cqmf2.SEV_ALERT
SEV_CRIT   = cqmf2.SEV_CRIT
SEV_ERROR  = cqmf2.SEV_ERROR
SEV_WARN   = cqmf2.SEV_WARN
SEV_NOTICE = cqmf2.SEV_NOTICE
SEV_INFORM = cqmf2.SEV_INFORM
SEV_DEBUG  = cqmf2.SEV_DEBUG

QUERY_OBJECT    = cqmf2.QUERY_OBJECT
QUERY_OBJECT_ID = cqmf2.QUERY_OBJECT_ID
QUERY_SCHEMA    = cqmf2.QUERY_SCHEMA
QUERY_SCHEMA_ID = cqmf2.QUERY_SCHEMA_ID


#===================================================================================================
# EXCEPTIONS
#===================================================================================================
class QmfAgentException(Exception):
  """
  This exception class represents an exception that was raised by a remote agent and propagated
  to a console via QMFv2.
  """
  def __init__(self, data):
    self.value = data

  def __str__(self):
    return "From Remote Agent: %r" % self.value.getProperties()


#===================================================================================================
# AGENT HANDLER
#===================================================================================================
class AgentHandler(Thread):
  """
  Agent applications can create a subclass of AgentHandler to handle asynchronous events (like
  incoming method calls) that occur on the agent session.  AgentHandler contains a thread on which
  the handler callbacks are invoked.

  There are two ways to operate the handler:  Cause it to start its own thread by calling
  start() and later stop it by calling cancel(); and directly calling run() to operate it on the
  main thread.

  Example Usage:

      class MyAgentHandler(qmf2.AgentHandler):
          def __init__(self, agentSession):
              qmf2.AgentHandler.__init__(self, agentSession)
          def method(self, handle, methodName, args, subtypes, addr, userId):
              ...method handling code goes here...
              For success, add output arguments:
                  handle.addReturnArgument("argname", argvalue)
                  ...
                  self.agent.methodSuccess(handle)
              For failure, raise an exception:
                  self.agent.raiseException(handle, "error text")
              Or, if you have created a schema for a structured exception:
                  ex = qmf2.Data(exceptionSchema)
                  ex.whatHappened = "it failed"
                  ex.howBad = 84
                  ex.detailMap = {}
                  ...
                  self.agent.raiseException(handle, ex)
  """

  def __init__(self, agentSession):
    Thread.__init__(self)
    self.__agent = agentSession
    self.__running = True

  def cancel(self):
    """
    Stop the handler thread.
    """
    self.__running = None

  def run(self):
    event = cqmf2.AgentEvent()
    while self.__running:
      valid = self.__agent._impl.nextEvent(event, qpid_messaging.Duration.SECOND)
      if valid and self.__running:
        if event.getType() == cqmf2.AGENT_METHOD:
          self.method(event, event.getMethodName(), event.getArguments(), event.getArgumentSubtypes(),
                      DataAddr(event.getDataAddr()), event.getUserId())

  def method(self, handle, methodName, args, subtypes, addr, userId):
    """
    Override this method to create your own method handler.
    """
    pass


#===================================================================================================
# CONSOLE HANDLER
#===================================================================================================
class ConsoleHandler(Thread):

  def __init__(self, consoleSession):
    Thread.__init__(self)
    self.__session = consoleSession
    self.__running = True

  def cancel(self):
    """
    Stop the handler thread.
    """
    self.__running = None

  def run(self):
    event = cqmf2.ConsoleEvent()
    while self.__running:
      valid = self.__session._impl.nextEvent(event, qpid_messaging.Duration.SECOND)
      if valid and self.__running:
        if event.getType() == cqmf2.CONSOLE_AGENT_ADD:
          self.agentAdded(Agent(event.getAgent()))

        elif event.getType() == cqmf2.CONSOLE_AGENT_DEL:
          reason = 'filter'
          if event.getAgentDelReason() == cqmf2.AGENT_DEL_AGED:
            reason = 'aged'
          self.agentDeleted(Agent(event.getAgent()), reason)

        elif event.getType() == cqmf2.CONSOLE_AGENT_RESTART:
          self.agentRestarted(Agent(event.getAgent()))

        elif event.getType() == cqmf2.CONSOLE_AGENT_SCHEMA_UPDATE:
          self.agentSchemaUpdated(Agent(event.getAgent()))

        elif event.getType() == cqmf2.CONSOLE_EVENT:
          self.eventRaised(Agent(event.getAgent()), Data(event.getData(0)), event.getTimestamp(), event.getSeverity())

  ##
  ## The following methods are intended to be overridden in a sub-class.  They are
  ## handlers for events that occur on QMF consoles.
  ##

  #
  # A new agent, whose attributes match the console's agent filter, has been discovered.
  #
  def agentAdded(self, agent):
    pass

  #
  # A known agent has been removed from the agent list.  There are two possible reasons
  # for agent deletion:
  #
  #    1) 'aged'   - The agent hasn't been heard from for the maximum age interval and is
  #                  presumed dead.
  #    2) 'filter' - The agent no longer matches the console's agent-filter and has been
  #                  effectively removed from the agent list.  Such occurrences are likely
  #                  to be seen immediately after setting the filter to a new value.
  #
  def agentDeleted(self, agent, reason):
    pass

  #
  # An agent-restart was detected.  This occurs when the epoch number advertised by the
  # agent changes.  It indicates that the agent in question was shut-down/crashed and
  # restarted.
  #
  def agentRestarted(self, agent):
    pass

  #
  # The agent has registered new schema information which can now be queried, if desired.
  #
  def agentSchemaUpdated(self, agent):
    pass

  #
  # An agent raised an event.  The 'data' argument is a Data object that contains the
  # content of the event.
  #
  def eventRaised(self, agent, data, timestamp, severity):
    pass


#===================================================================================================
# CONSOLE SESSION
#===================================================================================================
class ConsoleSession(object):
  """
  """

  def __init__(self, connection, options=""):
    """
    ## The options string is of the form "{key:value,key:value}".  The following keys are supported:
    ##
    ##    domain:NAME                   - QMF Domain to join [default: "default"]
    ##    max-agent-age:N               - Maximum time, in minutes, that we will tolerate not hearing from
    ##                                    an agent before deleting it [default: 5]
    ##    listen-on-direct:{True,False} - If True:  Listen on legacy direct-exchange address for backward compatibility [default]
    ##                                    If False: Listen only on the routable direct address
    ##    strict-security:{True,False}  - If True:  Cooperate with the broker to enforce strict access control to the network
    ##                                  - If False: Operate more flexibly with regard to use of messaging facilities [default]
    ##
    """
    self._impl = cqmf2.ConsoleSession(connection, options)

  def setDomain(self, domain):
    """
    """
    self._impl.setDomain(domain)

  def setAgentFilter(self, filt):
    """
    """
    self._impl.setAgentFilter(filt)

  def open(self):
    """
    """
    self._impl.open()

  def close(self):
    """
    """
    self._impl.close()

  def getAgents(self):
    """
    """
    result = []
    count = self._impl.getAgentCount()
    for i in range(count):
      result.append(Agent(self._impl.getAgent(i)))
    return result

  def getConnectedBrokerAgent(self):
    """
    """
    return Agent(self._impl.getConnectedBrokerAgent())

  ## TODO: Async methods

#===================================================================================================
# AGENT SESSION
#===================================================================================================
class AgentSession(object):
  """
  """

  def __init__(self, connection, options=""):
    """
    ## The options string is of the form "{key:value,key:value}".  The following keys are supported:
    ##
    ##    interval:N                 - Heartbeat interval in seconds [default: 60]
    ##    external:{True,False}      - Use external data storage (queries and subscriptions are pass-through) [default: False]
    ##    allow-queries:{True,False} - If True:  automatically allow all queries [default]
    ##                                 If False: generate an AUTH_QUERY event to allow per-query authorization
    ##    allow-methods:{True,False} - If True:  automatically allow all methods [default]
    ##                                 If False: generate an AUTH_METHOD event to allow per-method authorization
    ##    max-subscriptions:N        - Maximum number of concurrent subscription queries permitted [default: 64]
    ##    min-sub-interval:N         - Minimum publish interval (in milliseconds) permitted for a subscription [default: 3000]
    ##    sub-lifetime:N             - Lifetime (in seconds with no keepalive) for a subscription [default: 300]
    ##    public-events:{True,False} - If True:  QMF events are sent to the topic exchange [default]
    ##                                 If False: QMF events are only sent to authorized subscribers
    ##    listen-on-direct:{True,False} - If True:  Listen on legacy direct-exchange address for backward compatibility [default]
    ##                                    If False: Listen only on the routable direct address
    ##    strict-security:{True,False}  - If True:  Cooperate with the broker to enforce strict access control to the network
    ##                                  - If False: Operate more flexibly with regard to use of messaging facilities [default]
    ##
    """
    self._impl = cqmf2.AgentSession(connection, options)

  def setDomain(self, domain):
    """
    """
    self._impl.setDomain(domain)

  def setVendor(self, val):
    """
    """
    self._impl.setVendor(val)

  def setProduct(self, val):
    """
    """
    self._impl.setProduct(val)

  def setInstance(self, val):
    """
    """
    self._impl.setInstance(val)

  def setAttribute(self, key, val):
    """
    """
    self._impl.setAttribute(key, val)

  def open(self):
    """
    """
    self._impl.open()

  def close(self):
    """
    """
    self._impl.close()

  def registerSchema(self, schema):
    """
    """
    self._impl.registerSchema(schema._impl)

  def addData(self, data, name="", persistent=False):
    """
    """
    return DataAddr(self._impl.addData(data._impl, name, persistent))

  def delData(self, addr):
    """
    """
    self._impl.delData(addr._impl)

  def methodSuccess(self, handle):
    """
    """
    self._impl.methodSuccess(handle)

  def raiseException(self, handle, data):
    """
    """
    if data.__class__ == Data:
      self._impl.raiseException(handle, data._impl)
    else:
      self._impl.raiseException(handle, data)

  def raiseEvent(self, data, severity=None):
    """
    """
    if not severity:
      self._impl.raiseEvent(data._impl)
    else:
      if (severity.__class__ != int and severity.__class__ != long) or severity < 0 or severity > 7:
        raise Exception("Severity must be an int between 0..7")
      self._impl.raiseEvent(data._impl, severity);


#===================================================================================================
# AGENT PROXY
#===================================================================================================
class Agent(object):
  """
  """

  def __init__(self, impl):
    self._impl = impl

  def __repr__(self):
    return self.getName()

  def getName(self):
    """
    """
    return self._impl.getName()

  def getEpoch(self):
    """
    """
    return self._impl.getEpoch()

  def getVendor(self):
    """
    """
    return self._impl.getVendor()

  def getProduct(self):
    """
    """
    return self._impl.getProduct()

  def getInstance(self):
    """
    """
    return self._impl.getInstance()

  def getAttributes(self):
    """
    """
    return self._impl.getAttributes()

  def query(self, q, timeout=30):
    """
    """
    if q.__class__ == Query:
      q_arg = q._impl
    else:
      q_arg = q
    dur = qpid_messaging.Duration(qpid_messaging.Duration.SECOND.getMilliseconds() * timeout)
    result = self._impl.query(q_arg, dur)
    if result.getType() == cqmf2.CONSOLE_EXCEPTION:
      raise Exception(Data(result.getData(0)))
    if result.getType() != cqmf2.CONSOLE_QUERY_RESPONSE:
      raise Exception("Protocol error, expected CONSOLE_QUERY_RESPONSE, got %d" % result.getType())
    dataList = []
    count = result.getDataCount()
    for i in range(count):
      dataList.append(Data(result.getData(i)))
    return dataList

  def loadSchemaInfo(self, timeout=30):
    """
    """
    dur = qpid_messaging.Duration(qpid_messaging.Duration.SECOND.getMilliseconds() * timeout)
    self._impl.querySchema(dur)

  def getPackages(self):
    """
    """
    result = []
    count = self._impl.getPackageCount()
    for i in range(count):
      result.append(self._impl.getPackage(i))
    return result

  def getSchemaIds(self, package):
    """
    """
    result = []
    count = self._impl.getSchemaIdCount(package)
    for i in range(count):
      result.append(SchemaId(self._impl.getSchemaId(package, i)))
    return result

  def getSchema(self, schemaId, timeout=30):
    """
    """
    dur = qpid_messaging.Duration(qpid_messaging.Duration.SECOND.getMilliseconds() * timeout)
    return Schema(self._impl.getSchema(schemaId._impl, dur))

  ## TODO: Async query
  ## TODO: Agent method

#===================================================================================================
# QUERY
#===================================================================================================
class Query(object):
  """
  """

  def __init__(self, arg1, arg2=None, arg3=None, *kwargs):
    """
    """
    if arg1.__class__ == DataAddr:
      self._impl = cqmf2.Query(arg1._impl)

  def getAddr(self):
    """
    """
    return DataAddr(self._impl.getDataAddr())

  def getSchemaId(self):
    """
    """
    return SchemaId(self._impl.getSchemaId())

  def getPredicate(self):
    """
    """
    return self._impl.getPredicate()

  def matches(self, data):
    """
    """
    m = data
    if data.__class__ == Data:
      m = data.getProperties()
    return self._impl.matchesPredicate(m)

#===================================================================================================
# DATA
#===================================================================================================
class Data(object):
  """
  """

  def __init__(self, arg=None):
    """
    """
    if arg == None:
      self._impl = cqmf2.Data()
    elif arg.__class__ == cqmf2.Data:
      self._impl = arg
    elif arg.__class__ == Schema:
      self._impl = cqmf2.Data(arg._impl)
    else:
      raise Exception("Unsupported initializer for Data")
    self._schema = None

  def getSchemaId(self):
    """
    """
    if self._impl.hasSchema():
      return SchemaId(self._impl.getSchemaId())
    return None

  def getAddr(self):
    """
    """
    if self._impl.hasAddr():
      return DataAddr(self._impl.getAddr())
    return None

  def getAgent(self):
    """
    """
    return Agent(self._impl.getAgent())

  def update(self, timeout=5):
    dur = qpid_messaging.Duration(qpid_messaging.Duration.SECOND.getMilliseconds() * timeout)
    agent = self._impl.getAgent()
    query = cqmf2.Query(self._impl.getAddr())
    result = agent.query(query, dur)
    if result.getType() != cqmf2.CONSOLE_QUERY_RESPONSE:
      raise "Update query failed"
    if result.getDataCount == 0:
      raise "Object no longer exists on agent"
    self._impl = cqmf2.Data(result.getData(0))

  def getProperties(self):
    """
    """
    return self._impl.getProperties();

  def _getSchema(self):
    if not self._schema:
      if not self._impl.hasSchema():
        raise Exception("Data object has no schema")
      self._schema = Schema(self._impl.getAgent().getSchema(self._impl.getSchemaId()))

  def _invoke(self, name, args, kwargs):
    ##
    ## Get local copies of the agent and the address of the data object
    ##
    agent = self._impl.getAgent()
    addr = self._impl.getAddr()

    ##
    ## Set up the timeout duration for the method call.  Set the default and override
    ## it if the _timeout keyword arg was supplied.
    ##
    timeout = 30
    if '_timeout' in kwargs:
      timeout = kwargs['_timeout']
    dur = qpid_messaging.Duration(qpid_messaging.Duration.SECOND.getMilliseconds() * timeout)

    ##
    ## Get the list of arguments from the schema, isolate those that are IN or IN_OUT,
    ## validate that we have the right number of arguments supplied, and marshall them
    ## into a map for transmission.
    ##
    arglist = []
    methods = self._schema.getMethods()
    for m in methods:
      if m.getName() == name:
        arglist = m.getArguments()
        break
    argmap = {}
    count = 0
    for a in arglist:
      if a.getDirection() == DIR_IN or a.getDirection() == DIR_IN_OUT:
        count += 1
    if count != len(args):
      raise Exception("Wrong number of arguments: expected %d, got %d" % (count, len(args)))
    i = 0
    for a in arglist:
      if a.getDirection() == DIR_IN or a.getDirection() == DIR_IN_OUT:
        argmap[a.getName()] = args[i]
        i += 1

    ##
    ## Invoke the method through the agent proxy.
    ##
    result = agent.callMethod(name, argmap, addr, dur)

    ##
    ## If the agent sent an exception, raise it in a QmfAgentException.
    ##
    if result.getType() == cqmf2.CONSOLE_EXCEPTION:
      exdata = result.getData(0)
      raise QmfAgentException(exdata)

    ##
    ## If a successful method response was received, collect the output arguments into a map
    ## and return them to the caller.
    ##
    if result.getType() != cqmf2.CONSOLE_METHOD_RESPONSE:
      raise Exception("Protocol error: Unexpected event type in method-response: %d" % result.getType())
    return result.getArguments()

  def __getattr__(self, name):
    ##
    ## If we have a schema and an address, check to see if this name is the name of a method.
    ##
    if self._impl.hasSchema() and self._impl.hasAddr() and self._impl.hasAgent():
      ##
      ## Get the schema for the data object.  Note that this call will block if the remote agent
      ## needs to be queried for the schema (i.e. the schema is not in the local cache).
      ##
      self._getSchema()
      methods = self._schema.getMethods()

      ##
      ## If the name matches a method in the schema, return a closure to be invoked.
      ##
      for method in methods:
        if name == method.getName():
          return lambda *args, **kwargs : self._invoke(name, args, kwargs)

    ##
    ## This is not a method call, return the property matching the name.
    ##
    return self._impl.getProperty(name)

  def __setattr__(self, name, value):
    if name[0] == '_':
      super.__setattr__(self, name, value)
      return
    self._impl.setProperty(name, value)

#===================================================================================================
# DATA ADDRESS
#===================================================================================================
class DataAddr(object):
  """
  """

  def __init__(self, arg, agentName=""):
    if arg.__class__ == dict:
      self._impl = cqmf2.DataAddr(arg)
    elif arg.__class__ == cqmf2.DataAddr:
      self._impl = arg
    else:
      self._impl = cqmf2.DataAddr(arg, agentName)

  def __repr__(self):
    return "%s:%s" % (self.getAgentName(), self.getName())

  def __eq__(self, other):
    return self.getAgentName() == other.getAgentName() and \
        self.getName() == other.getName() and \
        self.getAgentEpoch() == other.getAgentEpoch()

  def asMap(self):
    """
    """
    return self._impl.asMap()

  def getAgentName(self):
    """
    """
    return self._impl.getAgentName()

  def getName(self):
    """
    """
    return self._impl.getName()

  def getAgentEpoch(self):
    """
    """
    return self._impl.getAgentEpoch()

#===================================================================================================
# SCHEMA ID
#===================================================================================================
class SchemaId(object):
  """
  """

  def __init__(self, impl):
    self._impl = impl

  def __repr__(self):
    return "%s:%s" % (self.getPackageName(), self.getName())

  def getType(self):
    """
    """
    return self._impl.getType()

  def getPackageName(self):
    """
    """
    return self._impl.getPackageName()

  def getName(self):
    """
    """
    return self._impl.getName()

  def getHash(self):
    """
    """
    return self._impl.getHash()

#===================================================================================================
# SCHEMA
#===================================================================================================
class Schema(object):
  """
  """

  def __init__(self, stype, packageName=None, className=None, desc=None, sev=None):
    if stype.__class__ == cqmf2.Schema:
      self._impl = stype
    else:
      self._impl = cqmf2.Schema(stype, packageName, className)
      if desc:
        self._impl.setDesc(desc)
      if sev:
        self._impl.setDefaultSeverity(sev)

  def __repr__(self):
    return "QmfSchema:%r" % SchemaId(self._impl.getSchemaId())

  def finalize(self):
    """
    """
    self._impl.finalize()

  def getSchemaId(self):
    """
    """
    return SchemaId(self._impl.getSchemaId())

  def getDesc(self):
    """
    """
    return self._impl.getDesc()

  def getSev(self):
    """
    """
    return self._impl.getDefaultSeverity()

  def addProperty(self, prop):
    """
    """
    self._impl.addProperty(prop._impl)

  def addMethod(self, meth):
    """
    """
    self._impl.addMethod(meth._impl)

  def getProperties(self):
    """
    """
    props = []
    count = self._impl.getPropertyCount()
    for i in range(count):
      props.append(SchemaProperty(self._impl.getProperty(i)))
    return props

  def getMethods(self):
    """
    """
    meths = []
    count = self._impl.getMethodCount()
    for i in range(count):
      meths.append(SchemaMethod(self._impl.getMethod(i)))
    return meths

#===================================================================================================
# SCHEMA PROPERTY
#===================================================================================================
class SchemaProperty(object):
  """
  """

  def __init__(self, name, dtype=None, **kwargs):
    """
    """
    if name.__class__ == cqmf2.SchemaProperty:
      self._impl = name
    else:
      self._impl = cqmf2.SchemaProperty(name, dtype)
      if 'access' in kwargs:
        self._impl.setAccess(kwargs['access'])
      if 'index' in kwargs:
        self._impl.setIndex(kwargs['index'])
      if 'optional' in kwargs:
        self._impl.setOptional(kwargs['optional'])
      if 'unit' in kwargs:
        self._impl.setUnit(kwargs['unit'])
      if 'desc' in kwargs:
        self._impl.setDesc(kwargs['desc'])
      if 'subtype' in kwargs:
        self._impl.setSubtype(kwargs['subtype'])
      if 'direction' in kwargs:
        self._impl.setDirection(kwargs['direction'])

  def __repr__(self):
    return self._impl.getName()

  def getName(self):
    """
    """
    return self._impl.getName()

  def getType(self):
    """
    """
    return self._impl.getType()

  def getAccess(self):
    """
    """
    return self._impl.getAccess()

  def isIndex(self):
    """
    """
    return self._impl.isIndex()

  def isOptional(self):
    """
    """
    return self._impl.isOptional()

  def getUnit(self):
    """
    """
    return self._impl.getUnit()

  def getDesc(self):
    """
    """
    return self._impl.getDesc()

  def getSubtype(self):
    """
    """
    return self._impl.getSubtype()

  def getDirection(self):
    """
    """
    return self._impl.getDirection()

#===================================================================================================
# SCHEMA METHOD
#===================================================================================================
class SchemaMethod(object):
  """
  """

  def __init__(self, name, **kwargs):
    """
    """
    if name.__class__ == cqmf2.SchemaMethod:
      self._impl = name
    else:
      self._impl = cqmf2.SchemaMethod(name)
      if 'desc' in kwargs:
        self._impl.setDesc(kwargs['desc'])

  def __repr__(self):
    return "%s()" % self._impl.getName()

  def getName(self):
    """
    """
    return self._impl.getName()

  def getDesc(self):
    """
    """
    return self._impl.getDesc()

  def addArgument(self, arg):
    """
    """
    self._impl.addArgument(arg._impl)

  def getArguments(self):
    """
    """
    result = []
    count = self._impl.getArgumentCount()
    for i in range(count):
      result.append(SchemaProperty(self._impl.getArgument(i)))
    return result

