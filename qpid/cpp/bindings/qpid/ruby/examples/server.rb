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
  broker  = ARGV[0] || "amqp:tcp:localhost:5672"
  options = ARGV[1] || ""

  connection = Qpid::Messaging::Connection.new :url => broker, :options =>options
  connection.open
  session = connection.create_session
  receiver = session.create_receiver "service_queue; {create:always}"

  loop do
    request = receiver.fetch
    address = request.reply_to

    if !address.nil?
      sender = session.create_sender address
      response = Qpid::Messaging::Message.new :content => request.content_object.upcase
      sender.send response
      puts "Processed request: #{request.content_object} -> #{response.content_object}"
      session.acknowledge
    else
      puts "Error: no reply address specified for request: #{request.content_object}"
      session.reject request
    end
  end

  connection.close
end

