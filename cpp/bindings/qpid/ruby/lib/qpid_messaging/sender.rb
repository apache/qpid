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

module Qpid

  module Messaging

    # Sender is the entity through which messages sent.
    #
    # An instance of Sender can only be created using an active (not previously
    # closed) Session.
    #
    # ==== Examples
    #
    #   conn    = Qpid::Messaging::Connection.new :url => "mybroker:5762"
    #   conn.open
    #   session = conn.create_session
    #   sender  = session.create_session "my-sender-queue;{create:always}"
    class Sender

      def initialize(session, sender_impl) # :nodoc:
        @session     = session
        @sender_impl = sender_impl
      end

      def sender_impl # :nodoc:
        @sender_impl
      end

      # Sends a message.
      #
      # If a block is given, then it will be invoked after the message
      # is sent.
      #
      # ==== Options
      #
      # * message - The message to send.
      # * :sync - See note below on synching.
      #
      # ==== Synching
      #
      # If :sync => true, then the call will block until the broker confirms
      # receipt of the message. Otherwise it will only block for available
      # capacity; i.e., until pending is equal to capacity.
      #
      # ==== Examples
      #
      #   sender.send message do |message|
      #     puts "Message sent: #{message.content}"
      #   end
      #
      def send(message, args = {}, &block)
        sync = args[:sync] || false
        @sender_impl.send message.message_impl, sync
        block.call message unless block.nil?
      end

      # Closes this +Sender+.
      #
      # This does not affect the +Session+.
      def close; @sender_impl.close; end

      # Returns the human-readable name for this +Sender+.
      #
      # ==== Examples
      #
      #   puts "Sender: #{sender.name}"
      #
      def name; @sender_impl.getName; end

      # Sets the capacity for this +Sender+.
      #
      # The capacity is the number of outgoing messages that can be held
      # pending confirmation or receipt by the broker.
      #
      # ==== Options
      #
      # * capacity - the capacity
      #
      # ==== Examples
      #
      #   sender.capacity = 50 # sets the outgoing capacity to 50 messages
      #
      def capacity=(capacity); @sender_impl.setCapacity capacity; end

      # Returns the capacity.
      #
      # The capacity is the total number of outgoing messages that can be
      # sent before a called to +send+ begins to block by default.
      #
      # ==== Examples
      #
      #   puts "You can send a maximum of #{sender.capacity} messages."
      #
      def capacity; @sender_impl.getCapacity; end

      # Returns the number of messages sent that are pending receipt
      # confirmation by the broker.
      #
      # ==== Examples
      #
      #   if sender.unsettled > 0
      #     puts "There are #{sender.unsettled} messages pending."
      #   end
      #
      def unsettled; @sender_impl.getUnsettled; end

      # Returns the available slots for sending messages.
      #
      # This differs from +capacity+ in that it is the available slots in
      # the senders capacity for holding outgoing messages. The difference
      # between capacity and available is the number of messages that
      # have not been delivered yet.
      #
      # ==== Examples
      #
      #   puts "You can send #{sender.available} messages before blocking."
      #
      def available
        @sender_impl.getAvailable
      end

      # Returns the +Session+ for this sender.
      #
      # ==== Examples
      #
      #   recv.session.close if done
      #
      def session; @session; end

    end

  end

end

