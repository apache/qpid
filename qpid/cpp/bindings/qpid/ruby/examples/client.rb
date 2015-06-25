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

if __FILE__ == $0
  broker  = ARGV[1] || "amqp:tcp:localhost:5672"
  options = ARGV[2] || ""

  connection = Qpid::Messaging::Connection.new :url => broker, :options => options
  connection.open
  session = connection.create_session
  sender = session.create_sender "service_queue"
  response_queue = Qpid::Messaging::Address.new("#response-queue;{create:always}")
  receiver = session.create_receiver response_queue

  ["Twas brillig, and the slithy toves",
   "Did gire and gymble in the wabe.",
   "All mimsy were the borogroves,",
   "And the mome raths outgrabe."].each do |line|
    request = Qpid::Messaging::Message.new :content => line
    request.reply_to = response_queue
    sender.send request
    response = receiver.fetch
    puts "#{request.content_object} -> #{response.content_object}"
  end

  connection.close
end

