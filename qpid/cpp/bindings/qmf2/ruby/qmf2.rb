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
  ## AGENT HANDLER TODO
  ##==============================================================================

  class AgentHandler
    def get_query(context, query, userId); end
    def method_call(context, name, object_id, args, userId); end
  end

  ##==============================================================================
  ## CONSOLE SESSION
  ##==============================================================================

  class ConsoleSession
    attr_reader :impl

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

    def add_data(data, name="", persistent=:false)
      DataAddr.new(@impl.addData(data.impl, name, persistent))
    end

    def del_data(addr)
      @impl.del_data(addr.impl)
    end

    def method_success(handle)
      @impl.methodSuccess(handle)
    end

    def raise_exception(handle, data)
      if data.class == Data
        @impl.raiseException(handle, data.impl)
      else
        @impl.raiseException(handle, data)
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
  ## QUERY TODO
  ##==============================================================================

  class Query
    attr_reader :impl
    def initialize(arg1, arg2=nil, arg3=nil)
      if arg1.class == Qmf2::DataAddr
        @impl = Cqmf2::Query.new(arg1.impl)
      end
    end
  end

  ##==============================================================================
  ## DATA
  ##==============================================================================

  class Data
    attr_reader :impl

    def initialize(arg=nil)
      if arg == nil
        @impl = Cqmf2::Data.new
      elsif arg.class == Cqmf2::Data
        @impl = arg
      elsif arg.class == Schema
        @impl = Cqmf2::Data(arg.impl)
      else
        raise "Unsupported initializer for Data"
      end
      @schema = nil
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

    def addr
      if @impl.hasAddr
        return DataAddr.new(@impl.getAddr)
      end
      return nil
    end

    def agent
      return Agent.new(@impl.getAgent)
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

    def initialize(arg)
      if arg.class == Hash
        @impl = Cqmf2::DataAddr.new(arg)
      else
        @impl = arg
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


