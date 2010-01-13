#!/usr/bin/ruby

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

require 'qmf'
require 'socket'

class Model
  attr_reader :parent_class, :child_class, :event_class

  def initialize
    @parent_class = Qmf::SchemaObjectClass.new("org.apache.qpid.qmf", "parent")
    @parent_class.add_property(Qmf::SchemaProperty.new("name", Qmf::TYPE_SSTR, :index => true))
    @parent_class.add_property(Qmf::SchemaProperty.new("state", Qmf::TYPE_SSTR))

    @parent_class.add_property(Qmf::SchemaProperty.new("uint64val", Qmf::TYPE_UINT64))
    @parent_class.add_property(Qmf::SchemaProperty.new("uint32val", Qmf::TYPE_UINT32))
    @parent_class.add_property(Qmf::SchemaProperty.new("uint16val", Qmf::TYPE_UINT16))
    @parent_class.add_property(Qmf::SchemaProperty.new("uint8val", Qmf::TYPE_UINT8))

    @parent_class.add_property(Qmf::SchemaProperty.new("int64val", Qmf::TYPE_INT64))
    @parent_class.add_property(Qmf::SchemaProperty.new("int32val", Qmf::TYPE_INT32))
    @parent_class.add_property(Qmf::SchemaProperty.new("int16val", Qmf::TYPE_INT16))
    @parent_class.add_property(Qmf::SchemaProperty.new("int8val", Qmf::TYPE_INT8))

    @parent_class.add_property(Qmf::SchemaProperty.new("sstrval", Qmf::TYPE_SSTR))
    @parent_class.add_property(Qmf::SchemaProperty.new("lstrval", Qmf::TYPE_LSTR))

    @parent_class.add_property(Qmf::SchemaProperty.new("mapval", Qmf::TYPE_MAP))

    @parent_class.add_statistic(Qmf::SchemaStatistic.new("queryCount", Qmf::TYPE_UINT32, :unit => "query", :desc => "Query count"))

    method = Qmf::SchemaMethod.new("echo", :desc => "Check responsiveness of the agent object")
    method.add_argument(Qmf::SchemaArgument.new("sequence", Qmf::TYPE_UINT32, :dir => Qmf::DIR_IN_OUT))
    @parent_class.add_method(method)

    method = Qmf::SchemaMethod.new("set_numerics", :desc => "Set the numeric values in the object")
    method.add_argument(Qmf::SchemaArgument.new("test", Qmf::TYPE_SSTR, :dir => Qmf::DIR_IN))
    @parent_class.add_method(method)

    method = Qmf::SchemaMethod.new("set_map", :desc => "Set the map value in the object")
    method.add_argument(Qmf::SchemaArgument.new("value", Qmf::TYPE_MAP, :dir => Qmf::DIR_IN))
    method.add_argument(Qmf::SchemaArgument.new("output", Qmf::TYPE_MAP, :dir => Qmf::DIR_OUT))
    @parent_class.add_method(method)

    method = Qmf::SchemaMethod.new("set_short_string", :desc => "Set the short string value in the object")
    method.add_argument(Qmf::SchemaArgument.new("value", Qmf::TYPE_SSTR, :dir => Qmf::DIR_IN_OUT))
    @parent_class.add_method(method)

    method = Qmf::SchemaMethod.new("set_long_string", :desc => "Set the long string value in the object")
    method.add_argument(Qmf::SchemaArgument.new("value", Qmf::TYPE_LSTR, :dir => Qmf::DIR_IN_OUT))
    @parent_class.add_method(method)

    method = Qmf::SchemaMethod.new("create_child", :desc => "Create a new child object")
    method.add_argument(Qmf::SchemaArgument.new("child_name", Qmf::TYPE_LSTR, :dir => Qmf::DIR_IN))
    method.add_argument(Qmf::SchemaArgument.new("child_ref", Qmf::TYPE_REF, :dir => Qmf::DIR_OUT))
    @parent_class.add_method(method)

    method = Qmf::SchemaMethod.new("probe_userid", :desc => "Return the user-id for this method call")
    method.add_argument(Qmf::SchemaArgument.new("userid", Qmf::TYPE_SSTR, :dir => Qmf::DIR_OUT))
    @parent_class.add_method(method)

    @child_class = Qmf::SchemaObjectClass.new("org.apache.qpid.qmf", "child")
    @child_class.add_property(Qmf::SchemaProperty.new("name", Qmf::TYPE_SSTR, :index => true))

    @event_class = Qmf::SchemaEventClass.new("org.apache.qpid.qmf", "test_event", Qmf::SEV_INFORM)
    @event_class.add_argument(Qmf::SchemaArgument.new("uint32val", Qmf::TYPE_UINT32))
    @event_class.add_argument(Qmf::SchemaArgument.new("strval", Qmf::TYPE_LSTR))
  end

  def register(agent)
    agent.register_class(@parent_class)
    agent.register_class(@child_class)
    agent.register_class(@event_class)
  end
end


class App < Qmf::AgentHandler
  def get_query(context, query, userId)
#    puts "Query: user=#{userId} context=#{context} class=#{query.class_name} object_num=#{query.object_id if query.object_id}"
    if query.class_name == 'parent'
        @agent.query_response(context, @parent)
    elsif query.object_id == @parent_oid
        @agent.query_response(context, @parent)
    end
    @agent.query_complete(context)
  end

  def method_call(context, name, object_id, args, userId)
#    puts "Method: user=#{userId} context=#{context} method=#{name} object_num=#{object_id if object_id} args=#{args}"
    
    retCode = 0
    retText = "OK"

    if name == "echo"
      @agent.method_response(context, 0, "OK", args)

    elsif name == "set_map"

      map = args['value']

      map['added'] = 'Added Text'
      args['output'] = map

    elsif name == "set_numerics"

      if args['test'] == "big"
        @parent.uint64val = 0x9494949449494949
        @parent.uint32val = 0xa5a55a5a
        @parent.uint16val = 0xb66b
        @parent.uint8val  = 0xc7

        @parent.int64val = 1000000000000000000
        @parent.int32val = 1000000000
        @parent.int16val = 10000
        @parent.int8val  = 100

        @parent.mapval = {'u64'  => @parent.uint64val,
                          'u32'  => @parent.uint32val,
                          'u16'  => @parent.uint16val,
                          'u8'   => @parent.uint8val,
                          'i64'  => @parent.int64val,
                          'i32'  => @parent.int32val,
                          'i16'  => @parent.int16val,
                          'i8'   => @parent.int8val,
                          'sstr' => "Short String",
                          'map'  => {'first' => 'FIRST', 'second' => 'SECOND'}}

        event = Qmf::QmfEvent.new(@model.event_class)
        event.uint32val = @parent.uint32val
        event.strval = "Unused"
        @agent.raise_event(event)

      elsif args['test'] == "small"
        @parent.uint64val = 4
        @parent.uint32val = 5
        @parent.uint16val = 6
        @parent.uint8val  = 7

        @parent.int64val = 8
        @parent.int32val = 9
        @parent.int16val = 10
        @parent.int8val  = 11

        event = Qmf::QmfEvent.new(@model.event_class)
        event.uint32val = @parent.uint32val
        event.strval = "Unused"
        @agent.raise_event(event)

      elsif args['test'] == "negative"
        @parent.uint64val = 0
        @parent.uint32val = 0
        @parent.uint16val = 0
        @parent.uint8val  = 0

        @parent.int64val = -10000000000
        @parent.int32val = -100000
        @parent.int16val = -1000
        @parent.int8val  = -100

        event = Qmf::QmfEvent.new(@model.event_class)
        event.uint32val = @parent.uint32val
        event.strval = "Unused"
        @agent.raise_event(event)

      else
        retCode = 1
        retText = "Invalid argument value for test"
      end

    elsif name == "set_short_string"
      @parent.sstrval = args['value']

      event = Qmf::QmfEvent.new(@model.event_class)
      event.uint32val = 0
      event.strval = @parent.sstrval
      @agent.raise_event(event)

    elsif name == "set_long_string"
      @parent.lstrval = args['value']

      event = Qmf::QmfEvent.new(@model.event_class)
      event.uint32val = 0
      event.strval = @parent.lstrval
      @agent.raise_event(event)

    elsif name == "create_child"
      oid = @agent.alloc_object_id(2)
      args['child_ref'] = oid
      @child = Qmf::AgentObject.new(@model.child_class)
      @child.name = args.by_key("child_name")
      @child.set_object_id(oid)

    elsif name == "probe_userid"
      args['userid'] = userId

    else
      retCode = 1
      retText = "Unimplemented Method: #{name}"
    end

    @agent.method_response(context, retCode, retText, args)
  end

  def main
    @settings = Qmf::ConnectionSettings.new
    @settings.set_attr("host", ARGV[0]) if ARGV.size > 0
    @settings.set_attr("port", ARGV[1].to_i) if ARGV.size > 1
    @connection = Qmf::Connection.new(@settings)
    @agent = Qmf::Agent.new(self)

    @model = Model.new
    @model.register(@agent)

    @agent.set_connection(@connection)

    @parent = Qmf::AgentObject.new(@model.parent_class)
    @parent.name  = "Parent One"
    @parent.state = "OPERATIONAL"

    @parent.uint64val = 0
    @parent.uint32val = 0
    @parent.uint16val = 0
    @parent.uint8val  = 0

    @parent.int64val = 0
    @parent.int32val = 0
    @parent.int16val = 0
    @parent.int8val  = 0

    @parent_oid = @agent.alloc_object_id(1)
    @parent.set_object_id(@parent_oid)

    sleep
  end
end

app = App.new
app.main


