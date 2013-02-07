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

    # A Session represents a distinct conversation between end points.
    class Session

      def initialize(connection, session) # :nodoc:
        @connection   = connection
        @session_impl = session
      end

      def session_impl # :nodoc:
        @session_impl
      end

      # Returns the +Connection+ associated with this session.
      def connection
        @connection
      end

      # Creates a new endpoint for sending messages.
      #
      # The +address+ can either be an instance +Address+ or else a
      # string that describes an address endpoint.
      #
      # ==== Arguments
      #
      # * +address+ The end point address.
      #
      # ==== Examples
      #
      #   sender = session.create_sender "my-queue;{create:always}"
      #
      def create_sender(address)
        _address = address

        if address.class == Qpid::Messaging::Address
          _address = address.address_impl
        end

        sender_impl = @session_impl.createSender(_address)
        sender_name = sender_impl.getName

        Qpid::Messaging::Sender.new(self, sender_impl)
      end

      # Retrieves the +Sender+ with the specified name.
      #
      # Raises an exception when no such Sender exists.
      #
      # ==== Arguments
      #
      # * +name+ The +Sender+ name.
      #
      # ==== Examples
      #
      #   sender = session.sender "my-queue"
      #
      def sender(name)
        Qpid::Messaging::Sender.new self, @session_impl.getSender(name)
      end

      # Creates a new endpoint for receiving messages.
      #
      # The +address+ can either be an instance +Address+ or else a
      # string that describes an address endpoint.
      #
      # ==== Arguments
      #
      # * +address+ The end point address.
      #
      # ==== Examples
      #
      #   receiver = session.create_receiver "my-queue"
      #
      def create_receiver(address)
        result        = nil
        receiver_impl = nil

        if address.class == Qpid::Messaging::Address
          address_impl = address.address_impl
          receiver_impl = @session_impl.createReceiver address_impl
        else
          receiver_impl = @session_impl.createReceiver(address)
        end

        Qpid::Messaging::Receiver.new self, receiver_impl
      end

      # Retrieves the +Receiver+ with the specified name, or nil if no such
      # Receiver exists.
      #
      # ==== Arguments
      #
      # * +name+ The +Receiver+ name.
      #
      # ==== Examples
      #
      #   receiver = session.receiver "my-queue"
      #
      def receiver(name)
        Qpid::Messaging::Receiver.new self, @session_impl.getReceiver(name)
      end

      # Closes the +Session+ and all associated +Sender+ and +Receiver+ instances.
      #
      # NOTE: All +Session+ instances for a +Connection+ are closed when the
      # +Connection+ is closed.
      def close; @session_impl.close; end

      # Commits any pending transactions for a transactional session.
      def commit; @session_impl.commit; end

      # Rolls back any uncommitted transactions on a transactional session.
      def rollback; @session_impl.rollback; end

      # Acknowledges one or more outstanding messages that have been received
      # on this session.
      #
      # ==== Arguments
      #
      # * :message - if specified, then only the +Message+ specified is acknowledged
      # * :sync - if true then the call will block until processed by the server (def. false)
      #
      # ==== Examples
      #
      #   session.acknowledge                     # acknowledges all received messages
      #   session.acknowledge :message => message # acknowledge one message
      #   session.acknowledge :sync => true       # blocks until the call completes
      #
      #--
      # TODO: Add an optional block to be used for blocking calls.
      #++
      def acknowledge(args = {})
        sync = args[:sync] || false
        message = args[:message] if args[:message]

        unless message.nil?
          @session_impl.acknowledge message.message_impl, sync
        else
          @session_impl.acknowledge sync
        end
      end

      # Rejects the specified message. A rejected message will not be
      # redelivered.
      #
      # NOTE: A message cannot be rejected once it has been acknowledged.
      def reject(message); @session_impl.reject message.message_impl; end

      # Releases the message, which allows the broker to attempt to
      # redeliver it.
      #
      # NOTE: A message connot be released once it has been acknowled.
      def release(message); @session_impl.release message.message_impl; end

      # Requests synchronization with the server.
      #
      # ==== Arguments
      #
      # * :block - if true then the call blocks until the server acknowledges it (def. false)
      #
      #--
      # TODO: Add an optional block to be used for blocking calls.
      #++
      def sync(args = {})
        block = args[:block] || false
        @session_impl.sync block
      end

      # Returns the total number of receivable messages, and messages already
      # received, by +Receiver+ instances associated with this +Session+.
      def receivable; @session_impl.getReceivable; end

      # Returns the number of messages that have been acknowledged by this session
      # whose acknowledgements have not been confirmed as processed by the server.
      def unsettled_acks; @session_impl.getUnsettledAcks; end

      # Fetches the +Receiver+ for the next message.
      #
      # ==== Arguments
      #
      # * timeout - time to wait for a +Receiver+ before timing out
      #
      # ==== Examples
      #
      #   recv = session.next_receiver # wait forever for the next +Receiver+
      #   # execute a block on the next receiver
      #   session.next_receiver do |recv|
      #     msg = recv.get
      #     puts "Received message: #{msg.content}"
      #   end
      def next_receiver(timeout = Qpid::Messaging::Duration::FOREVER, &block)
        receiver_impl = @session_impl.nextReceiver(timeout.duration_impl)

        unless receiver_impl.nil?
          recv = Qpid::Messaging::Receiver.new self, receiver_impl
          block.call recv unless block.nil?
        end

        return recv
      end

      # Returns true if there were exceptions on this session.
      #
      # ==== Examples
      #
      #   puts "There were session errors." if @session.errors?
      def errors?; @session_impl.hasError; end

      # If the +Session+ has been rendered invalid due to some exception,
      # this method will result in that exception being raised.
      #
      # If none have occurred, then no exceptions are raised.
      #
      # ==== Examples
      #
      #   if @session.errors?
      #     begin
      #       @session.errors
      #     rescue Exception => error
      #       puts "An error occurred: #{error}"
      #     end
      #   end
      def errors; @session_impl.checkError; end

    end

  end

end

