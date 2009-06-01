
require 'qmfengine'
require 'thread'

module Qmf

    # Pull all the TYPE_* constants into Qmf namespace.  Maybe there's an easier way?
    Qmfengine.constants.each do |c|
        if c.index('TYPE_') == 0 or c.index('ACCESS_') == 0 or c.index('DIR_') == 0
            const_set(c, Qmfengine.const_get(c))
        end
    end

    class ConnectionSettings < Qmfengine::ConnectionSettings
    end

    class ConnectionEvent
        def conn_event_connected(); end
        def conn_event_disconnected(error); end
        def conn_event_session_closed(context, error); end
        def conn_event_recv(context, message); end
    end

    class Query
      attr_reader :impl
      def initialize(i)
        @impl = i
      end

      def package_name
        @impl.getPackage
      end

      def class_name
        @impl.getClass
      end

      def object_id
        objid = @impl.getObjectId
        if objid.class == NilClass
          return nil
        end
        return ObjectId.new(objid)
      end
    end

    class AgentHandler
      def get_query(context, query, userId); end
      def method_call(context, name, object_id, args, userId); end
    end

    class Connection
      attr_reader :impl

      def initialize(settings, event_handler = nil, delay_min = 1, delay_max = 128, delay_factor = 2)
        @impl = Qmfengine::ResilientConnection.new(settings, delay_min, delay_max, delay_factor)
        @sockEngine, @sock = Socket::socketpair(Socket::PF_UNIX, Socket::SOCK_STREAM, 0)
        @impl.setNotifyFd(@sockEngine.fileno)
        @new_conn_handlers = Array.new
        @conn_handlers = Array.new
        @sess_handlers = Array.new

        @thread = Thread.new do
          run
        end
      end

      def add_conn_handler(handler)
        @new_conn_handlers.push(handler)
        @sockEngine.write("x")
      end

      def add_sess_handler(handler)
        @sess_handlers.push(handler)
      end

      def run()
        event = Qmfengine::ResilientConnectionEvent.new
        connected = nil
        while :true
          @sock.read(1)

          @new_conn_handlers.each do |nh|
            @conn_handlers.push(nh)
            nh.conn_event_connected() if connected
          end
          @new_conn_handlers = Array.new

          valid = @impl.getEvent(event)
          while valid
            begin
              case event.kind
              when Qmfengine::ResilientConnectionEvent::CONNECTED
                connected = :true
                @conn_handlers.each { |h| h.conn_event_connected() }
              when Qmfengine::ResilientConnectionEvent::DISCONNECTED
                connected = nil
                @conn_handlers.each { |h| h.conn_event_disconnected(event.errorText) }
              when Qmfengine::ResilientConnectionEvent::SESSION_CLOSED
                event.sessionContext.handler.sess_event_session_closed(event.sessionContext, event.errorText)
              when Qmfengine::ResilientConnectionEvent::RECV
                event.sessionContext.handler.sess_event_recv(event.sessionContext, event.message)
              end
            rescue Exception => ex
              puts "Event Exception: #{ex}"
              puts ex.backtrace
            end
            @impl.popEvent
            valid = @impl.getEvent(event)
          end
        end
      end
    end

    class Session
      attr_reader :handle, :handler

      def initialize(conn, label, handler)
        @conn = conn
        @label = label
        @handler = handler
        @handle = Qmfengine::SessionHandle.new
        @conn.add_sess_handler(@handler)
        result = @conn.impl.createSession(label, self, @handle)
      end
    end

    class ObjectId
      attr_reader :impl
      def initialize(impl=nil)
        if impl
          @impl = impl
        else
          @impl = Qmfengine::ObjectId.new
        end
      end
      def object_num_high
        return @impl.getObjectNumHi
      end
      def object_num_low
        return @impl.getObjectNumLo
      end
    end

    class Arguments
      attr_reader :map
      def initialize(map)
        @map = map
        @by_hash = {}
        key_count = @map.keyCount
        a = 0
        while a < key_count
          @by_hash[@map.key(a)] = by_key(@map.key(a))
          a += 1
        end

      end

      def [] (key)
        return @by_hash[key]
      end

      def []= (key, value)
        @by_hash[key] = value
        set(key, value)
      end

      def each
        @by_hash.each { |k, v| yield(k, v) }
      end

      def by_key(key)
        val = @map.byKey(key)
        case val.getType
        when TYPE_UINT8, TYPE_UINT16, TYPE_UINT32 then val.asUint
        when TYPE_UINT64                          then val.asUint64
        when TYPE_SSTR, TYPE_LSTR                 then val.asString
        when TYPE_ABSTIME                         then val.asInt64
        when TYPE_DELTATIME                       then val.asUint64
        when TYPE_REF                             then val.asObjectId
        when TYPE_BOOL                            then val.asBool
        when TYPE_FLOAT                           then val.asFloat
        when TYPE_DOUBLE                          then val.asDouble
        when TYPE_UUID                            then val.asUuid
        when TYPE_INT8, TYPE_INT16, TYPE_INT32    then val.asInt
        when TYPE_INT64                           then val.asInt64
        when TYPE_MAP
        when TYPE_OBJECT
        when TYPE_LIST
        when TYPE_ARRAY
        end
      end

      def set(key, value)
        val = @map.byKey(key)
        case val.getType
        when TYPE_UINT8, TYPE_UINT16, TYPE_UINT32 then val.setUint(value)
        when TYPE_UINT64 then val.setUint64(value)
        when TYPE_SSTR, TYPE_LSTR then value ? val.setString(value) : val.setString('')
        when TYPE_ABSTIME then val.setInt64(value)
        when TYPE_DELTATIME then val.setUint64(value)
        when TYPE_REF then val.setObjectId(value.impl)
        when TYPE_BOOL then value ? val.setBool(value) : val.setBool(0)
        when TYPE_FLOAT then val.setFloat(value)
        when TYPE_DOUBLE then val.setDouble(value)
        when TYPE_UUID then val.setUuid(value)
        when TYPE_INT8, TYPE_INT16, TYPE_INT32 then val.setInt(value)
        when TYPE_INT64 then val.setInt64(value)
        when TYPE_MAP
        when TYPE_OBJECT
        when TYPE_LIST
        when TYPE_ARRAY
        end
      end
    end

    class Agent
      def initialize(handler, label="")
        if label == ""
          @agentLabel = "rb-%s.%d" % [Socket.gethostname, Process::pid]
        else
          @agentLabel = label
        end
        @conn = nil
        @handler = handler
        @impl = Qmfengine::Agent.new(@agentLabel)
        @event = Qmfengine::AgentEvent.new
        @xmtMessage = Qmfengine::Message.new
      end

      def set_connection(conn)
        @conn = conn
        @conn.add_conn_handler(self)
      end

      def register_class(cls)
        @impl.registerClass(cls.impl)
      end

      def alloc_object_id(low = 0, high = 0)
        ObjectId.new(@impl.allocObjectId(low, high))
      end

      def query_response(context, object)
        @impl.queryResponse(context, object.impl)
      end

      def query_complete(context)
        @impl.queryComplete(context)
      end

      def method_response(context, status, text, arguments)
        @impl.methodResponse(context, status, text, arguments.map)
      end

      def do_agent_events()
        count = 0
        valid = @impl.getEvent(@event)
        while valid
          count += 1
          case @event.kind
          when Qmfengine::AgentEvent::GET_QUERY
            @handler.get_query(@event.sequence, Query.new(@event.query), @event.authUserId)
          when Qmfengine::AgentEvent::START_SYNC
          when Qmfengine::AgentEvent::END_SYNC
          when Qmfengine::AgentEvent::METHOD_CALL
            args = Arguments.new(@event.arguments)
            @handler.method_call(@event.sequence, @event.name, ObjectId.new(@event.objectId),
                                 args, @event.authUserId)
          when Qmfengine::AgentEvent::DECLARE_QUEUE
            @conn.impl.declareQueue(@session.handle, @event.name)
          when Qmfengine::AgentEvent::DELETE_QUEUE
            @conn.impl.deleteQueue(@session.handle, @event.name)
          when Qmfengine::AgentEvent::BIND
            @conn.impl.bind(@session.handle, @event.exchange, @event.name, @event.bindingKey)
          when Qmfengine::AgentEvent::UNBIND
            @conn.impl.unbind(@session.handle, @event.exchange, @event.name, @event.bindingKey)
          when Qmfengine::AgentEvent::SETUP_COMPLETE
            @impl.startProtocol()
          end
          @impl.popEvent
          valid = @impl.getEvent(@event)
        end
        return count
      end

      def do_agent_messages()
        count = 0
        valid = @impl.getXmtMessage(@xmtMessage)
        while valid
          count += 1
          @conn.impl.sendMessage(@session.handle, @xmtMessage)
          @impl.popXmt
          valid = @impl.getXmtMessage(@xmtMessage)
        end
        return count
      end

      def do_events()
        begin
          ecnt = do_agent_events
          mcnt = do_agent_messages
        end until ecnt == 0 and mcnt == 0
      end

      def conn_event_connected()
        puts "Agent Connection Established..."
        @session = Session.new(@conn, "qmfa-%s.%d" % [Socket.gethostname, Process::pid], self)
        @impl.newSession
        do_events
      end

      def conn_event_disconnected(error)
        puts "Agent Connection Lost"
      end

      def sess_event_session_closed(context, error)
        puts "Agent Session Lost"
      end

      def sess_event_recv(context, message)
        @impl.handleRcvMessage(message)
        do_events
      end
    end

    class SchemaArgument
      attr_reader :impl
      def initialize(name, typecode, kwargs={})
        @impl = Qmfengine::SchemaArgument.new(name, typecode)
        @impl.setDirection(kwargs[:dir]) if kwargs.include?(:dir)
        @impl.setUnit(kwargs[:unit])     if kwargs.include?(:unit)
        @impl.setDesc(kwargs[:desc])     if kwargs.include?(:desc)
      end
    end

    class SchemaMethod
      attr_reader :impl
      def initialize(name, kwargs={})
        @impl = Qmfengine::SchemaMethod.new(name)
        @impl.setDesc(kwargs[:desc]) if kwargs.include?(:desc)
        @arguments = []
      end

      def add_argument(arg)
        @arguments << arg
        @impl.addArgument(arg.impl)
      end
    end

    class SchemaProperty
      attr_reader :impl
      def initialize(name, typecode, kwargs={})
        @impl = Qmfengine::SchemaProperty.new(name, typecode)
        @impl.setAccess(kwargs[:access])     if kwargs.include?(:access)
        @impl.setIndex(kwargs[:index])       if kwargs.include?(:index)
        @impl.setOptional(kwargs[:optional]) if kwargs.include?(:optional)
        @impl.setUnit(kwargs[:unit])         if kwargs.include?(:unit)
        @impl.setDesc(kwargs[:desc])         if kwargs.include?(:desc)
      end
    end

    class SchemaStatistic
      attr_reader :impl
      def initialize(name, typecode, kwargs={})
        @impl = Qmfengine::SchemaStatistic.new(name, typecode)
        @impl.setUnit(kwargs[:unit]) if kwargs.include?(:unit)
        @impl.setDesc(kwargs[:desc]) if kwargs.include?(:desc)
      end
    end

    class SchemaObjectClass
      attr_reader :impl
      def initialize(package, name, kwargs={})
        @impl = Qmfengine::SchemaObjectClass.new(package, name)
        @properties = []
        @statistics = []
        @methods = []
      end

      def add_property(prop)
        @properties << prop
        @impl.addProperty(prop.impl)
      end

      def add_statistic(stat)
        @statistics << stat
        @impl.addStatistic(stat.impl)
      end

      def add_method(meth)
        @methods << meth
        @impl.addMethod(meth.impl)
      end
    end

    class SchemaEventClass
      attr_reader :impl
      def initialize(package, name, kwargs={})
        @impl = Qmfengine::SchemaEventClass.new(package, name)
        @impl.setDesc(kwargs[:desc]) if kwargs.include?(:desc)
        @arguments = []
      end

      def add_argument(arg)
        @arguments << arg
        @impl.addArgument(arg.impl)
      end
    end

    class QmfObject
      attr_reader :impl, :object_class
      def initialize(cls)
        @object_class = cls
        @impl = Qmfengine::Object.new(@object_class.impl)
      end

      def destroy
        @impl.destroy
      end

      def object_id
        return ObjectId.new(@impl.getObjectId)
      end

      def set_object_id(oid)
        @impl.setObjectId(oid.impl)
      end

      def get_attr(name)
        val = value(name)
        case val.getType
        when TYPE_UINT8, TYPE_UINT16, TYPE_UINT32 then val.asUint
        when TYPE_UINT64 then val.asUint64
        when TYPE_SSTR, TYPE_LSTR then val.asString
        when TYPE_ABSTIME then val.asInt64
        when TYPE_DELTATIME then val.asUint64
        when TYPE_REF then val.asObjectId
        when TYPE_BOOL then val.asBool
        when TYPE_FLOAT then val.asFloat
        when TYPE_DOUBLE then val.asDouble
        when TYPE_UUID then val.asUuid
        when TYPE_INT8, TYPE_INT16, TYPE_INT32 then val.asInt
        when TYPE_INT64 then val.asInt64
        when TYPE_MAP
        when TYPE_OBJECT
        when TYPE_LIST
        when TYPE_ARRAY
        end
      end

      def set_attr(name, value)
        val = value(name)
        case val.getType
        when TYPE_UINT8, TYPE_UINT16, TYPE_UINT32 then val.setUint(value)
        when TYPE_UINT64 then val.setUint64(value)
        when TYPE_SSTR, TYPE_LSTR then value ? val.setString(value) : val.setString('')
        when TYPE_ABSTIME then val.setInt64(value)
        when TYPE_DELTATIME then val.setUint64(value)
        when TYPE_REF then val.setObjectId(value.impl)
        when TYPE_BOOL then value ? val.setBool(value) : val.setBool(0)
        when TYPE_FLOAT then val.setFloat(value)
        when TYPE_DOUBLE then val.setDouble(value)
        when TYPE_UUID then val.setUuid(value)
        when TYPE_INT8, TYPE_INT16, TYPE_INT32 then val.setInt(value)
        when TYPE_INT64 then val.setInt64(value)
        when TYPE_MAP
        when TYPE_OBJECT
        when TYPE_LIST
        when TYPE_ARRAY
        end
      end

      def [](name)
          get_attr(name)
      end

      def []=(name, value)
          set_attr(name, value)
      end

      def inc_attr(name, by=1)
        set_attr(name, get_attr(name) + by)
      end

      def dec_attr(name, by=1)
        set_attr(name, get_attr(name) - by)
      end

      private
      def value(name)
          val = @impl.getValue(name.to_s)
          if val.nil?
              raise ArgumentError, "Attribute '#{name}' not defined for class #{@object_class.impl.getName}"
          end
          return val
      end
    end

end
