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

class FindAgents < Qmf2::ConsoleHandler

  def initialize(session)
    super(session)
  end

  def agent_added(agent)
    puts "Agent Added: #{agent.name}"
  end

  def agent_deleted(agent, reason)
    puts "Agent Deleted: #{agent.to_s} reason: #{reason}"
  end

  def agent_restarted(agent)
    puts "Agent Restarted: #{agent.to_s} epoch: #{agent.epoch}"
  end

  def agent_schema_updated(agent)
    puts "Agent with new Schemata: #{agent.to_s}"
  end

  def event_raised(agent, data, timestamp, severity)
    puts "Event Raised time=#{timestamp} sev=#{severity} data=#{data.properties}"
  end
end


url     = "localhost"
options = ""

connection = Cqpid::Connection.new(url, options)
connection.open

session = Qmf2::ConsoleSession.new(connection)
session.open
session.set_agent_filter("[]")

main = FindAgents.new(session)
main.run

