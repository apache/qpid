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

warn 'The qmf2 module is deprecated.  It will be removed in the future.'

require 'cqmf2'
require 'cqpid'
require 'thread'
require 'socket'
require 'monitor'

module Qmf2

  Cqmf2.constants.each do |c|
    if c.index('AGENT_') == 0 or c.index('CONSOLE_') == 0 or
        c.index('SCHEMA_') == 0 or c.index('SEV_') == 0 or c.index('QUERY') == 0
      const_set(c, Cqmf2.const_get(c))
    end
  end

  SCHEMA_TYPE_DATA  = Cqmf2::SCHEMA_TYPE_DATA()
  SCHEMA_TYPE_EVENT = Cqmf2::SCHEMA_TYPE_EVENT()

  SCHEMA_DATA_VOID   = Cqmf2::SCHEMA_DATA_VOID()
  SCHEMA_DATA_BOOL   = Cqmf2::SCHEMA_DATA_BOOL()
  SCHEMA_DATA_INT    = Cqmf2::SCHEMA_DATA_INT()
  SCHEMA_DATA_FLOAT  = Cqmf2::SCHEMA_DATA_FLOAT()
  SCHEMA_DATA_STRING = Cqmf2::SCHEMA_DATA_STRING()
  SCHEMA_DATA_MAP    = Cqmf2::SCHEMA_DATA_MAP()
  SCHEMA_DATA_LIST   = Cqmf2::SCHEMA_DATA_LIST()
  SCHEMA_DATA_UUID   = Cqmf2::SCHEMA_DATA_UUID()

  ACCESS_READ_CREATE = Cqmf2::ACCESS_READ_CREATE()
  ACCESS_READ_WRITE  = Cqmf2::ACCESS_READ_WRITE()
  ACCESS_READ_ONLY   = Cqmf2::ACCESS_READ_ONLY()

  DIR_IN     = Cqmf2::DIR_IN()
  DIR_OUT    = Cqmf2::DIR_OUT()
  DIR_IN_OUT = Cqmf2::DIR_IN_OUT()

  SEV_EMERG  = Cqmf2::SEV_EMERG()
  SEV_ALERT  = Cqmf2::SEV_ALERT()
  SEV_CRIT   = Cqmf2::SEV_CRIT()
  SEV_ERROR  = Cqmf2::SEV_ERROR()
  SEV_WARN   = Cqmf2::SEV_WARN()
  SEV_NOTICE = Cqmf2::SEV_NOTICE()
  SEV_INFORM = Cqmf2::SEV_INFORM()
  SEV_DEBUG  = Cqmf2::SEV_DEBUG()

  ##==============================================================================
  ## EXCEPTIONS
  ##==============================================================================

  class QmfAgentException < RuntimeError
    attr :data

    def initialize(data)
      @data = data
    end

    def to_s
      "QmfAgentException: #{@data}"
    end
  end

  ##==============================================================================
  ## AGENT HANDLER
  ##==============================================================================

  class AgentHandler

    def initialize(session)
      @_session = session
      @_running = false
      @_thread  = nil
    end

    ##
    ## Call the "start" method to run the handler on a new thread.
    ##
    def start
      @_thread = Thread.new do
        run
      end
    end

    ##
    ## Request that the running thread complete and exit.
    ##
    def cancel
      @_running = false
      @_thread.join if @_thread
      @_thread = nil
    end

    ##
    ## Call the "run" method only if you want the handler to run on your own thread.
    ##
    def run
      @_running = true
      event = Cqmf2::AgentEvent.new
      while @_running do
        valid = @_session.impl.nextEvent(event, Cqpid::Duration.SECOND)
        if valid and @_running
          case event.getType
          when Cqmf2::AGENT_AUTH_QUERY
            yes = authorize_query(Query.new(event.getQuery()), event.getUserId())
            if yes == true
              @_session.impl.authAccept(event)
            else
              @_session.impl.authReject(event)
            end

          when Cqmf2::AGENT_QUERY
            context = QueryContext.new(@_session, event)
            get_query(context, Query.new(event.getQuery()), event.getUserId())

          when Cqmf2::AGENT_METHOD
            context = MethodContext.new(@_session, event)
            begin
              method_call(context, event.getMethodName(), event.getDataAddr(), event.getArguments(), event.getUserId())
            rescue Exception => ex
              @_session.impl.raiseException(event, "#{ex}")
            end

          end
        end
      end
    end


    ##
    ## The following methods are intended to be overridden in a sub-class.  They are
    ## handlers for events that occur on QMF consoles.
    ##

    #
    # This method will only be invoked if the "allow-queries" option is enabled on the
    # agent session.  When invoked, it provides the query and the authenticated user-id
    # of the querying client.
    #
    # This method must return true if the query is permitted, false otherwise.
    #
    def authorize_query(query, user_id); end

    #
    # This method will only be invoked if the "external" option is "True" on the agent
    # session.  When invoked, the method should begin the process of responding to a data
    # query.  The authenticated user-id of the requestor is provided for informational
    # purposes.  The 'context' variable is used to provide the results back to the requestor.
    #
    # For each matching Data object, call context.response(data).  When the query is complete,
    # call context.complete().  After completing the query, you should not use 'context' any
    # longer.
    #
    # Note: It is not necessary to process the query synchronously.  If desired, this method
    # may store the context for asynchronous processing or pass it to another thread for
    # processing.  There is no restriction on the number of contexts that may be in-flight
    # concurrently.
    #
    def get_query(context, query, user_id); end

    #
    # This method is invoked when a console calls a QMF method on the agent.  Supplied are
    # a context for the response, the method name, the data address of the data object being
    # called, the input arguments (a dictionary), and the caller's authenticated user-id.
    #
    # A method call can end one of two ways:  Successful completion, in which the output
    # arguments (if any) are supplied; and Exceptional completion if there is an error.
    #
    # Successful Completion:
    #    For each output argument, assign the value directly to context (context.arg1 = "value")
    #    Once arguments are assigned, call context._success().
    #
    # Exceptional Completion:
    #    Method 1:  Call context._exception(data) where 'data' is a string or a Data object.
    #    Method 2:  Raise an exception (raise "Error Text") synchronously in the method body.
    #
    # Note: Like get_query, method_call may process methods synchronously or asynchronously.
    # This method may store the context for later asynchronous processing.  There is no
    # restriction on the number of contexts that may be in-flight concurrently.
    #
    # However, "Method 2" for Exceptional Completion can only be done synchronously.
    #
    def method_call(context, method_name, data_addr, args, user_id); end
  end

  class QueryContext
    def initialize(agent, context)
      @agent = agent
      @context = context
    end

    def response(data)
      @agent.impl.response(@context, data.impl)
    end

    def complete
      @agent.impl.complete(@context)
    end
  end

  class MethodContext
    def initialize(agent, context)
      @agent = agent
      @context = context
    end

    def _success
      @agent.impl.methodSuccess(@context)
    end

    def _exception(ex)
      if ex.class == Data
        @agent.impl.raiseException(@context, ex.impl)
      else
        @agent.impl.raiseException(@context, ex)
      end
    end

    def method_missing(name_in, *args)
      name = name_in.to_s
      if name[name.length - 1] == 61
        name = name[0..name.length - 2]
        @context.impl.addReturnArgument(name, args[0])
      else
        super.method_missing(name_in, args)
      end
    end
  end

  ##==============================================================================
  ## CONSOLE HANDLER
  ##==============================================================================

  class ConsoleHandler

    def initialize(session)
      @_session = session
      @_running = false
      @_thread  = nil
    end

    ##
    ## Call the "start" method to run the handler on a new thread.
    ##
    def start
      @_thread = Thread.new do
        run
      end
    end

    ##
    ## Request that the running thread complete and exit.
    ##
    def cancel
      @_running = false
      @_thread.join if @_thread
      @_thread = nil
    end

    ##
    ## Call the "run" method only if you want the handler to run on your own thread.
    ##
    def run
      @_running = true
      event = Cqmf2::ConsoleEvent.new
      while @_running do
        valid = @_session.impl.nextEvent(event, Cqpid::Duration.SECOND)
        if valid and @_running
          case event.getType
          when Cqmf2::CONSOLE_AGENT_ADD
            agent_added(Agent.new(event.getAgent))

          when Cqmf2::CONSOLE_AGENT_DEL
            reason = :filter
            reason = :aged if event.getAgentDelReason == Cqmf2::AGENT_DEL_AGED
            agent_deleted(Agent.new(event.getAgent), reason)

          when Cqmf2::CONSOLE_AGENT_RESTART
            agent_restarted(Agent.new(event.getAgent))

          when Cqmf2::CONSOLE_AGENT_SCHEMA_UPDATE
            agent_schema_updated(Agent.new(event.getAgent))

          when Cqmf2::CONSOLE_EVENT
            event_raised(Agent.new(event.getAgent), Data.new(event.getData(0)), event.getTimestamp, event.getSeverity)

          end
        end
      end
    end


    ##
    ## The following methods are intended to be overridden in a sub-class.  They are
    ## handlers for events that occur on QMF consoles.
    ##

    #
    # A new agent, whose attributes match the console's agent filter, has been discovered.
    #
    def agent_added(agent); end

    #
    # A known agent has been removed from the agent list.  There are two possible reasons
    # for agent deletion:
    #
    #    1) :aged   - The agent hasn't been heard from for the maximum age interval and is
    #                 presumed dead.
    #    2) :filter - The agent no longer matches the console's agent-filter and has been
    #                 effectively removed from the agent list.  Such occurrences are likely
    #                 to be seen immediately after setting the filter to a new value.
    #
    def agent_deleted(agent, reason); end

    #
    # An agent-restart was detected.  This occurs when the epoch number advertised by the
    # agent changes.  It indicates that the agent in question was shut-down/crashed and
    # restarted.
    #
    def agent_restarted(agent); end

    #
    # The agent has registered new schema information which can now be queried, if desired.
    #
    def agent_schema_updated(agent); end

    #
    # An agent raised an event.  The 'data' argument is a Data object that contains the
    # content of the event.
    #
    def event_raised(agent, data, timestamp, severity); end
  end

  ##==============================================================================
  ## CONSOLE SESSION
  ##==============================================================================

  class ConsoleSession
    attr_reader :impl

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
    def initialize(connection, options="")
      @impl = Cqmf2::ConsoleSession.new(connection, options)
    end

    def set_domain(domain)        @impl.setDomain(domain)      end
    def set_agent_filter(filter)  @impl.setAgentFilter(filter) end

    def open()   @impl.open  end
    def close()  @impl.close  end

    def agents
      result = []
      count = @impl.getAgentCount
      for i in 0...count
        result << Agent.new(@impl.getAgent(i))
      end
      return result
    end

    def connected_broker_agent
      Agent.new(@impl.getConnectedBrokerAgent)
    end
  end

  ##==============================================================================
  ## AGENT SESSION
  ##==============================================================================

  class AgentSession
    attr_reader :impl

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
    def initialize(connection, options="")
      @impl = Cqmf2::AgentSession.new(connection, options)
    end

    def set_domain(val)         @impl.setDomain(val)           end
    def set_vendor(val)         @impl.setVendor(val)           end
    def set_product(val)        @impl.setProduct(val)          end
    def set_instance(val)       @impl.setInstance(val)         end
    def set_attribute(key, val) @impl.setAttribute(key, val)   end
    def open()                  @impl.open                     end
    def close()                 @impl.close                    end
    def register_schema(cls)    @impl.registerSchema(cls.impl) end

    def add_data(data, name="", persistent=false)
      DataAddr.new(@impl.addData(data.impl, name, persistent))
    end

    def del_data(addr)
      @impl.del_data(addr.impl)
    end

    def raise_event(data, severity=nil)
      if !severity
        @impl.raiseEvent(data.impl)
      else
        @impl.raiseEvent(data.impl, severity)
      end
    end
  end

  ##==============================================================================
  ## AGENT PROXY
  ##==============================================================================

  class Agent
    attr_reader :impl

    def initialize(impl)
      @impl = impl
    end

    def name()       @impl.getName        end
    def epoch()      @impl.getEpoch       end
    def vendor()     @impl.getVendor      end
    def product()    @impl.getProduct     end
    def instance()   @impl.getInstance    end
    def attributes() @impl.getAttributes  end

    def to_s
      "#{vendor}:#{product}:#{instance}"
    end

    def query(q, timeout=30)
      if q.class == Query
        q_arg = q.impl
      else
        q_arg = q
      end
      dur = Cqpid::Duration.new(Cqpid::Duration.SECOND.getMilliseconds * timeout)
      result = @impl.query(q_arg, dur)
      raise QmfAgentException.new(Data.new(result.getData(0))) if result.getType == Cqmf2::CONSOLE_EXCEPTION
      raise "Protocol error, expected CONSOLE_QUERY_RESPONSE, got #{result.getType}" if result.getType != Cqmf2::CONSOLE_QUERY_RESPONSE
      data_list = []
      count = result.getDataCount
      for i in 0...count
        data_list << Data.new(result.getData(i))
      end
      return data_list
    end

    def load_schema_info(timeout=30)
      dur = Cqpid::Duration.new(Cqpid::Duration.SECOND.getMilliseconds * timeout)
      @impl.querySchema(dur)
    end

    def packages
      result = []
      count = @impl.getPackageCount
      for i in 0...count
        result << @impl.getPackage(i)
      end
      return result
    end

    def schema_ids(package)
      result = []
      count = @impl.getSchemaIdCount(package)
      for i in 0...count
        result << SchemaId.new(@impl.getSchemaId(package, i))
      end
      return result
    end

    def schema(sid, timeout=30)
      dur = Cqpid::Duration.new(Cqpid::Duration.SECOND.getMilliseconds * timeout)
      Schema.new(@impl.getSchema(sid.impl, dur))
    end
  end

  ##==============================================================================
  ## QUERY
  ##==============================================================================

  class Query
    attr_reader :impl
    def initialize(arg1, arg2=nil, arg3=nil)
      if arg1.class == Qmf2::DataAddr
        @impl = Cqmf2::Query.new(arg1.impl)
      end
    end

    def addr()      DataAddr.new(@impl.getDataAddr())  end
    def schema_id() SchemaId.new(@impl.getSchemaId())  end
    def predicate() @impl.getPredicate()               end

    def matches?(data)
      map = data
      map = data.properties if data.class == Data
      @impl.matchesPredicate(map)
    end
  end

  ##==============================================================================
  ## DATA
  ##==============================================================================

  class Data
    attr_reader :impl

    def initialize(arg=nil)
      @schema = nil
      if arg == nil
        @impl = Cqmf2::Data.new
      elsif arg.class == Cqmf2::Data
        @impl = arg
      elsif arg.class == Schema
        @impl = Cqmf2::Data.new(arg.impl)
        @schema = arg
      else
        raise "Unsupported initializer for Data"
      end
    end

    def to_s
      "#{@impl.getProperties}"
    end

    def schema_id
      if @impl.hasSchema
        return SchemaId.new(@impl.getSchemaId)
      end
      return nil
    end

    def set_addr(addr)
      @impl.setAddr(addr.impl)
    end

    def addr
      if @impl.hasAddr
        return DataAddr.new(@impl.getAddr)
      end
      return nil
    end

    def agent
      return Agent.new(@impl.getAgent)
    end

    def update(timeout=5)
      dur = Cqpid::Duration.new(Cqpid::Duration.SECOND.getMilliseconds * timeout)
      agent = @impl.getAgent
      query = Cqmf2::Query.new(@impl.getAddr)
      result = agent.query(query, dur)
      raise "Update query failed" if result.getType != Cqmf2::CONSOLE_QUERY_RESPONSE
      raise "Object no longer exists on agent" if result.getDataCount == 0
      @impl = Cqmf2::Data.new(result.getData(0))
      return nil
    end

    def properties
      return @impl.getProperties
    end

    def get_attr(name)
      @impl.getProperty(name)
    end

    def set_attr(name, v)
      @impl.setProperty(name, v)
    end

    def [](name)
      get_attr(name)
    end

    def []=(name, value)
      set_attr(name, value)
    end

    def _get_schema
      unless @schema
        raise "Data object has no schema" unless @impl.hasSchema
        @schema = Schema.new(@impl.getAgent.getSchema(@impl.getSchemaId))
      end
    end

    def method_missing(name_in, *args)
      #
      # Convert the name to a string and determine if it represents an
      # attribute assignment (i.e. "attr=")
      #
      name = name_in.to_s
      attr_set = (name[name.length - 1] == 61)
      name = name[0..name.length - 2] if attr_set

      #
      # We'll be needing the schema to determine how to proceed.  Get the schema.
      # Note that this call may block if the remote agent needs to be queried
      # for the schema (i.e. the schema isn't in the local cache).
      #
      _get_schema

      #
      # If the name matches a property name, set or return the value of the property.
      #
      @schema.properties.each do |prop|
        if prop.name == name
          if attr_set
            return set_attr(name, args[0])
          else
            return get_attr(name)
          end
        end
      end

      #
      # If we still haven't found a match for the name, check to see if
      # it matches a method name.  If so, marshall the arguments and invoke
      # the method.
      #
      @schema.methods.each do |method|
        if method.name == name
          raise "Sets not permitted on methods" if attr_set
          result = @impl.getAgent.callMethod(name, _marshall(method, args), @impl.getAddr)
          if result.getType == Cqmf2::CONSOLE_EXCEPTION
            raise QmfAgentException, result.getData(0)
          end
          return result.getArguments
        end
      end

      #
      # This name means nothing to us, pass it up the line to the parent
      # class's handler.
      #
      super.method_missing(name_in, args)
    end

    #
    # Convert a Ruby array of arguments (positional) into a Value object of type "map".
    #
    private
    def _marshall(schema, args)
      count = 0
      schema.arguments.each do |arg|
        if arg.direction == DIR_IN || arg.direction == DIR_IN_OUT
          count += 1
        end
      end
      raise "Wrong number of arguments: expecter #{count}, got #{arge.length}" if count != args.length
      map = {}
      count = 0
      schema.arguments.each do |arg|
        if arg.direction == DIR_IN || arg.direction == DIR_IN_OUT
          map[arg.name] = args[count]
          count += 1
        end
      end
      return map
    end
  end

  ##==============================================================================
  ## DATA ADDRESS
  ##==============================================================================

  class DataAddr
    attr_reader :impl

    def initialize(arg, agentName="")
      if arg.class == Hash
        @impl = Cqmf2::DataAddr.new(arg)
      elsif arg.class == Cqmf2::DataAddr
        @impl = arg
      else
        @impl = Cqmf2::DataAddr.new(arg, agentName)
      end
    end

    def ==(other)
      return @impl == other.impl
    end

    def as_map()       @impl.asMap          end
    def agent_name()   @impl.getAgentName   end
    def name()         @impl.getName        end
    def agent_epoch()  @impl.getAgentEpoch  end
  end

  ##==============================================================================
  ## SCHEMA ID
  ##==============================================================================

  class SchemaId
    attr_reader :impl
    def initialize(impl)
      @impl = impl
    end

    def type()         @impl.getType         end
    def package_name() @impl.getPackageName  end
    def class_name()   @impl.getName         end
    def hash()         @impl.getHash         end
  end

  ##==============================================================================
  ## SCHEMA
  ##==============================================================================

  class Schema
    attr_reader :impl
    def initialize(arg, packageName="", className="", kwargs={})
      if arg.class == Cqmf2::Schema
        @impl = arg
      else
        @impl = Cqmf2::Schema.new(arg, packageName, className)
        @impl.setDesc(kwargs[:desc]) if kwargs.include?(:desc)
        @impl.setDefaultSeverity(kwargs[:sev]) if kwargs.include?(:sev)
      end
    end

    def finalize()         @impl.finalize                   end
    def schema_id()        SchemaId.new(@impl.getSchemaId)  end
    def desc()             @impl.getDesc                    end
    def sev()              @impl.getDefaultSeverity         end
    def add_property(prop) @impl.addProperty(prop.impl)     end
    def add_method(meth)   @impl.addMethod(meth.impl)       end

    def properties
      result = []
      count = @impl.getPropertyCount
      for i in 0...count
        result << SchemaProperty.new(@impl.getProperty(i))
      end
      return result
    end

    def methods
      result = []
      count = @impl.getMethodCount
      for i in 0...count
        result << SchemaMethod.new(@impl.getMethod(i))
      end
      return result
    end
  end

  ##==============================================================================
  ## SCHEMA PROPERTY
  ##==============================================================================

  class SchemaProperty
    attr_reader :impl

    def initialize(arg, dtype=nil, kwargs={})
      if arg.class == Cqmf2::SchemaProperty
        @impl = arg
      else
        @impl = Cqmf2::SchemaProperty.new(arg, dtype)
        @impl.setAccess(kwargs[:access])       if kwargs.include?(:access)
        @impl.setIndex(kwargs[:index])         if kwargs.include?(:index)
        @impl.setOptional(kwargs[:optional])   if kwargs.include?(:optional)
        @impl.setUnit(kwargs[:unit])           if kwargs.include?(:unit)
        @impl.setDesc(kwargs[:desc])           if kwargs.include?(:desc)
        @impl.setSubtype(kwargs[:subtype])     if kwargs.include?(:subtype)
        @impl.setDirection(kwargs[:direction]) if kwargs.include?(:direction)
      end
    end

    def name()       @impl.getName       end
    def access()     @impl.getAccess     end 
    def index?()     @impl.isIndex       end
    def optional?()  @impl.isOptional    end
    def unit()       @impl.getUnit       end
    def desc()       @impl.getDesc       end
    def subtype()    @impl.getSubtype    end
    def direction()  @impl.getDirection  end

    def to_s
      name
    end
  end

  ##==============================================================================
  ## SCHEMA METHOD
  ##==============================================================================

  class SchemaMethod
    attr_reader :impl

    def initialize(arg, kwargs={})
      if arg.class == Cqmf2::SchemaMethod
        @impl = arg
      else
        @impl = Cqmf2::SchemaMethod.new(arg)
        @impl.setDesc(kwargs[:desc]) if kwargs.include?(:desc)
      end
    end

    def name()  @impl.getName  end
    def desc()  @impl.getDesc  end
    def add_argument(arg)  @impl.addArgument(arg.impl)  end

    def arguments
      result = []
      count = @impl.getArgumentCount
      for i in 0...count
        result << SchemaProperty.new(@impl.getArgument(i))
      end
      return result
    end

    def to_s
      name
    end
  end
end


