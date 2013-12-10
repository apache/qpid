#--
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
#++

require 'cqpid'
require 'qpid_messaging/duration'
require 'qpid_messaging/address'
require 'qpid_messaging/encoding'
require 'qpid_messaging/message'
require 'qpid_messaging/sender'
require 'qpid_messaging/receiver'
require 'qpid_messaging/session'
require 'qpid_messaging/connection'

module Qpid

  # The Qpid Messaging framework is an enterprise messaging framework
  # based on the open-source AMQP protocol.
  #
  # ==== Example Application
  #
  # Here is a simple example application. It creates a link to a broker located
  # on a system named *broker.myqpiddomain.com*. It then creates a new messaging
  # queue named "qpid-examples" and publishes a message to it. It then consumes
  # that same message and closes the connection.
  #
  #   require 'rubygems'
  #   gem 'qpid_messaging'
  #   require 'qpid_messaging'
  #
  #   # create a connection, open it and then create a session named "session1"
  #   conn = Qpid::Messaging::Connection.new :name => "broker.myqpiddomain.com"
  #   conn.open
  #   session = conn.create_session "session1"
  #
  #   # create a sender and a receiver
  #   # the sender marks the queue as one that is deleted when trhe sender disconnects
  #   send = session.create_sender "qpid-examples;{create:always,delete:always}"
  #   recv = session.create_receiver "qpid-examples"
  #
  #   # create an outgoing message and send it
  #   outgoing = Qpid::Messaging::Message.new :content => "The time is #{Time.new}"
  #   sender.send outgoing
  #
  #   # set the receiver's capacity to 10 and then check out many messages are pending
  #   recv.capacity = 10
  #   puts "There are #{recv.available} messages waiting." # should report 1 message
  #
  #   # get the nextwaiting  message, which should be in the local queue now,
  #   # and output the contents
  #   incoming = recv.get Qpid::Messaging::Duration::IMMEDIATE
  #   puts "Received the following message: #{incoming.content}"
  #   # the output should be the text that was sent earlier
  #
  #   # acknowledge the message, letting the sender know the message was received
  #   puts "The sender currently has #{send.unsettled} message(s) pending."
  #   # should report 1 unsettled message
  #   session.acknowledge incoming # acknowledge the received message
  #   puts "Now sender currently has #{send.unsettled} message(s) pending."
  #   # should report 0 unsettled messages
  #
  #   # close the connection
  #   conn.close
  #
  module Messaging; end

end
