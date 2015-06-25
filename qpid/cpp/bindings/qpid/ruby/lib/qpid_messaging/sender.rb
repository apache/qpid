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

    # +Sender+ is the entity through which messages are sent.
    #
    # An instance of +Sender+ can only be created using an active (not previously
    # closed) Session. See Qpid::Messaging::Session.create_sender for more details.
    #
    # ==== Examples
    #
    #   # create a connection
    #   conn = Qpid::Messaging::Connection.new "mybroker:5672"
    #   conn.open
    #
    #   if conn.open?
    #
    #     # create a session
    #     session = conn.create_session
    #
    #     # create a sender that posts messages to the "updates" queue
    #     sender = session.create_sender "updates;{create:always}
    #
    #     # begin sending updates
    #     loop do
    #       # wait for the next event content then send it
    #       content = wait_for_event
    #       sender.send Qpid::Messaging::Message.new :content => content
    #     end
    #   end
    #
    class Sender

      def initialize(session, sender_impl) # :nodoc:
        @session     = session
        @sender_impl = sender_impl
      end

      def sender_impl # :nodoc:
        @sender_impl
      end

      # Sends a message, optionally blocking until the message is received
      # by the broker.
      #
      # ==== Options
      #
      # * +message+ - The message to send.
      # * +:sync+ - Block until received. See note below on synching.
      #
      # ==== Synching
      #
      # If :sync => true, then the call will block until the broker confirms
      # receipt of the message. Otherwise it will only block for available
      # capacity; i.e., until pending is equal to capacity.
      #
      # ==== Examples
      #
      #   # send a message
      #   outgoing = Qpid::Messaging::Message.new :content => content
      #   sender.send outgoing
      #
      #   # send a message, wait for confirmation from the broker
      #   outgoing = Qpid::Messaging::Message.new :content => content
      #   sender.send outgoing, :sync => true
      #
      def send(message, args = {}, &block)
        sync = args[:sync] || false
        @sender_impl.send message.message_impl, sync
        block.call message unless block.nil?
      end

      # Closes this +Sender+.
      #
      # This does not affect the owning Session or Connection.
      def close; @sender_impl.close; end

      # Returns the human-readable name for this +Sender+.
      def name; @sender_impl.getName; end

      # Sets the capacity for this +Sender+.
      #
      # The capacity is the number of outgoing messages that can be held
      # pending confirmation of receipt by the broker.
      #
      # ==== Options
      #
      # * +capacity+ - the capacity
      def capacity=(capacity); @sender_impl.setCapacity capacity; end

      # Returns the capacity.
      def capacity; @sender_impl.getCapacity; end

      # Returns the number of messages sent that are pending receipt
      # confirmation by the broker.
      def unsettled; @sender_impl.getUnsettled; end

      # Returns the available slots for sending messages.
      #
      # This differs from +capacity+ in that it is the available slots in
      # the senders capacity for holding outgoing messages. The difference
      # between capacity and available is the number of messages that
      # have not been delivered yet.
      def available
        @sender_impl.getAvailable
      end

      # Returns the Session for this sender.
      def session; @session; end

    end

  end

end

