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

require 'cqpid'
require 'qmf2'

class MyAgent < Qmf2::AgentHandler

  def initialize(session)
    super(session)
  end

  def authorize_query(query, user_id)
    puts "Authorizing #{user_id}"
    return true
  end

  def method_call(context, method_name, data_addr, args, user_id)
    puts "Method: #{method_name}"
    context._success
  end

end


class Program

  def initialize(url)
    @url = url
    @sess_options = "{allow-queries:False}"
  end

  def setup_schema(agent)
    @cls_control = Qmf2::Schema.new(Qmf2::SCHEMA_TYPE_DATA, "org.package", "control")
    @cls_control.add_property(Qmf2::SchemaProperty.new("state", Qmf2::SCHEMA_DATA_STRING))
    agent.register_schema(@cls_control)
  end

  def run
    connection = Cqpid::Connection.new(@url)
    connection.open

    session = Qmf2::AgentSession.new(connection, @sess_options)
    session.set_vendor("package.org")
    session.set_product("internal_agent")
    setup_schema(session)
    session.open

    control = Qmf2::Data.new(@cls_control)
    control.state = "OPERATIONAL"
    session.add_data(control)

    main = MyAgent.new(session)
    main.run
  end
end

prog = Program.new("localhost")
prog.run


