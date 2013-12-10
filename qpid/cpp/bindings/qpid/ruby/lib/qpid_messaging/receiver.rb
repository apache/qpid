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

    # +Receiver+ is the entity through which messages are received.
    #
    # An instance of +Receiver+ can only be created using an active (i.e., not
    # previously closed) Session. See Qpid::Messaging::Session.create_receiver
    # for more details.
    #
    # ==== Example
    #
    #   # create a connection and a session
    #   conn     = Qpid::Messaging::Connection.new :url => "mybroker:5762"
    #   conn.open
    #   session  = conn.create_session
    #
    #   # create a receiver that listens on the "updates" topic of "alerts"
    #   receiver = session.create_receiver "alerts/updates"
    #
    #   # wait for an incoming message and process it
    #   incoming = receiver.get Qpid::Messaging::Duration::FOREVER
    #   process(incoming)
    #
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
      # If no message is received within the specified time then a
      # MessagingException is raised.
      #
      # ==== Options
      #
      # * duration - the timeout to wait
      #
      # ==== Examples
      #
      #   # retrieves a message, also handles exceptions raised on no messages
      #   begin
      #     # checks for a message, returning immediately
      #     msg = recv.get Qpid::Messaging::Duration::IMMEDIATE
      #     puts "Received this message: #{message.content}"
      #   rescue
      #     puts "No messages available.
      #   end
      #
      def get(duration = Qpid::Messaging::Duration::FOREVER)
        message_impl = @receiver_impl.get duration.duration_impl
        create_message_wrapper message_impl unless message_impl.nil?
      end

      # Retrieves a message from the receiver's subscription, or waits
      # for up to the duration specified for one to become available.
      #
      # If no message is fetched within the specified time then a
      # MessagingException is raised.
      #
      # ==== Options
      #
      # * duration - the timeout to wait (def. Duration::FOREVER)
      #
      # ==== Examples
      #
      #   # retrieves a message, also handles exceptions raised on no messages
      #   begin
      #     # checks for a message, times out after one second
      #     msg = recv.fetch Qpid::Messaging::Duration::SECOND
      #     puts "Fetched this message: #{message.content}"
      #   rescue
      #     puts "No messages available.
      #   end
      #
      def fetch(duration = Qpid::Messaging::Duration::FOREVER)
        message_impl = @receiver_impl.fetch duration.duration_impl
        create_message_wrapper message_impl unless message_impl.nil?
      end

      # Sets the capacity.
      #
      # The capacity of a +Receiver+ is the number of Messages that can be
      # pre-fetched from the broker and held locally. If capacity is 0 then
      # messages will never be pre-fetched and all messages must instead be
      # retrieved using #fetch.
      #
      # ==== Options
      #
      # * capacity - the capacity
      #
      # ==== Examples
      #
      #   # create a receiver and give it a capacity of 50
      #   recv = session.create_receiver "alerts/minor"
      #   recv.capacity = 50
      #
      def capacity=(capacity); @receiver_impl.setCapacity capacity; end

      # Returns the capacity.
      def capacity; @receiver_impl.getCapacity; end

      # Returns the number of messages locally held.
      #
      # The available is always 0 <= available <= capacity.
      #
      # If the #capacity is set to 0 then available will always be 0.
      #
      # ==== Examples
      #
      #   # output the number of messages waiting while processing
      #   loop do
      #     puts "There are #{recv.available} messages pending..."
      #     # wait forever (the default) for the next message
      #     msg = recv.get
      #     # process the message
      #     dispatch_message msg
      #   end
      #
      def available; @receiver_impl.getAvailable; end

      # Returns the number of messages that have been received and acknowledged
      # but whose acknowledgements have not been confirmed by the sender.
      def unsettled; @receiver_impl.getUnsettled; end

      # Closes this +Receiver+.
      #
      # This does not affect the owning Session or Connection.
      def close; @receiver_impl.close; end

      # Returns whether the +Receiver+ is closed.
      def closed?; @receiver_impl.isClosed; end

      # Returns the name of this +Receiver+.
      def name; @receiver_impl.getName; end

      # Returns the owning Session for this +Receiver+.
      def session; @session; end

      private

      def create_message_wrapper message_impl # :nodoc:
        Qpid::Messaging::Message.new(:impl => message_impl)
      end

    end

  end

end

