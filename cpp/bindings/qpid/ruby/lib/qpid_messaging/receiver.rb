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

    # Receiver is the entity through which messages are received.
    #
    # An instance of Receiver can only be created using an active (not
    # previously closed) Session.
    #
    # ==== Example
    #
    #   conn     = Qpid::Messaging::Connection.new :url => "mybroker:5762"
    #   conn.open
    #   session  = conn.create_session
    #   receiver = session.create_receiver "my-sender-queue"
    class Receiver

      def initialize(session, receiver_impl) # :nodoc:
        @session       = session
        @receiver_impl = receiver_impl
      end

      def receiver_impl # :nodoc:
        @receiver_impl
      end

      # Retrieves a message from the local queue, or waits for up to
      # the duration specified for one to become available.
      #
      # If a block is given, then it will be invaked after the next message
      # is received or the call times out, passing in the message or nil
      # respectively.
      #
      # ==== Options
      # * duration - the timeout to wait (def. Duration::FOREVER)
      #
      # ==== Examples
      #
      #   msg = rcvr.get # Uses the default timeout of forever
      #
      #   msg = rcvr.get Qpid::Messaging::Duration::IMMEDIATE # returns a message or exits immediately
      #
      #   # passes in a block to handle the received message
      #   rcvr.get Qpid::Messaging::Duration::SECOND do |message|
      #     if message.nil?
      #       puts "No message was received."
      #     else
      #       puts "Received this message: #{message.content}"
      #     end
      #   end
      def get(duration = Qpid::Messaging::Duration::FOREVER)
        message_impl = @receiver_impl.get duration.duration_impl
        create_message_wrapper message_impl unless message_impl.nil?
      end

      # Retrieves a message from the receiver's subscription, or waits
      # for up to the duration specified for one to become available.
      #
      # If a block is given, then it will be invaked after the next message
      # is received or the call times out, passing in the message or nil
      # respectively.
      #
      # ==== Options
      # * duration - the timeout to wait (def. Duration::FOREVER)
      #
      # ==== Examples
      #
      #   msg = rcvr.fetch # Uses the default timeout of forever
      #
      #   msg = rcvr.fetch Qpid::Messaging::Duration::IMMEDIATE # returns a message or exits immediately
      #
      #   # passes in a block to handle the received message
      #   rcvr.fetch Qpid::Messaging::Duration::SECOND do |message|
      #     if message.nil?
      #       puts "No message was received."
      #     else
      #       puts "Received this message: #{message.content}"
      #     end
      #   end
      def fetch(duration = Qpid::Messaging::Duration::FOREVER)
        message_impl = @receiver_impl.fetch duration.duration_impl
        create_message_wrapper message_impl unless message_impl.nil?
      end

      # Sets the capacity for this +Receiver+.
      #
      # ==== Options
      #
      # * capacity - the capacity
      #
      # ==== Examples
      #
      #   receiver.capacity = 50 # sets the incoming capacity to 50 messages
      #
      def capacity=(capacity); @receiver_impl.setCapacity capacity; end

      # Returns the capacity.
      #
      #
      # The capacity is the numnber of incoming messages that can be held
      # locally before being fetched.
      #
      # ==== Examples
      #
      #   puts "The receiver can hold #{rcv.capacity} messages."
      #
      def capacity; @receiver_impl.getCapacity; end

      # Returns the number of slots for receiving messages.
      #
      # This differs from +capacity+ in that it is the available slots in
      # the capacity for holding incoming messages, where available <= capacity.
      #
      # ==== Examples
      #
      #   puts "You can receive #{rcv.available} messages before blocking."
      #
      def available; @receiver_impl.getAvailable; end

      # Returns the number of messages that have been received and acknowledged
      # but whose acknowledgements have not been confirmed by the sender.
      #
      # ==== Examples
      #
      #   puts "You have #{rcv.unsettled} messages to be confirmed."
      #
      def unsettled; @receiver_impl.getUnsettled; end

      # Closes this +Receiver+.
      #
      # This does not affect the +Session+.
      def close; @receiver_impl.close; end

      # Returns whether the receiver is closed.
      #
      # ==== Examples
      #
      #   recv.close unless recv.closed?
      #
      def closed?; @receiver_impl.isClosed; end

      # Returns the name of this +Receiver+.
      #
      # ==== Examples
      #
      #   puts "Receiver: #{recv.name}"
      def name; @receiver_impl.getName; end

      # Returns the Session for this +Receiver+.
      def session; @session; end

      private

      def create_message_wrapper message_impl # :nodoc:
        Qpid::Messaging::Message.new(:impl => message_impl)
      end

    end

  end

end

