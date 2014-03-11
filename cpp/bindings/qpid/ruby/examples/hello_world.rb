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

# This is your classic Hello World application, written in
# Ruby, that uses Qpid. It demonstrates how to send and
# also receive messages.
#
if __FILE__ == $0
  broker  = ARGV[0] || "localhost:5672"
  address = ARGV[1] || "amq.topic"
  options = ARGV[2] || ""

  connection = Qpid::Messaging::Connection.new :url => broker, :options => options
  connection.open
  session    = connection.create_session
  receiver   = session.create_receiver address
  sender     = session.create_sender address

  # Send a simple message
  sender.send Qpid::Messaging::Message.new :content => "Hello world!"

  # Now receive the message
  message = receiver.fetch Qpid::Messaging::Duration::SECOND
  puts "#{message.content_object}"
  session.acknowledge

  connection.close
end

