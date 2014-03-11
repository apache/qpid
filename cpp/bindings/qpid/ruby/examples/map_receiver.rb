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

$:.unshift File.join(File.dirname(__FILE__), "..", "lib")

require 'qpid_messaging'

broker  = ARGV[0] || "amqp:tcp:127.0.0.1:5672"
address = ARGV[1] || "message_queue; {create: always}"
options = ARGV[2] || ""

connection = Qpid::Messaging::Connection.new :url => broker, :options => options
connection.open

def display_value value
  case value
  when Array
    result = ""
    value.each_with_index {|element, index| result += "#{', ' if index > 0}#{element}"}
    return "[#{result}]"
  end

  value.to_s
end

begin
  session = connection.create_session
  receiver = session.create_receiver address

  message = receiver.fetch
  content = message.content_object

  print "content-type:#{message.content_type}"
  print "{"
  content.keys.sort.each_with_index do |key, index|
    print "#{', ' if index > 0}#{key}:#{display_value content[key]}"
  end
  print "}\n"

  session.acknowledge

rescue Exception => error
  puts "Exception: #{error.message}"
end

connection.close

