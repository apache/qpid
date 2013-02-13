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

    # A +Session+ represents a distinct conversation between end points. They are
    # created from an active (i.e., not closed) Connection.
    #
    # A +Session+ is used to acknowledge individual or all messages that have
    # passed through it
    class Session

      def initialize(connection, session) # :nodoc:
        @connection   = connection
        @session_impl = session
      end

      def session_impl # :nodoc:
        @session_impl
      end

      # Returns the Connection associated with this session.
      def connection
        @connection
      end

      # Creates a new endpoint for sending messages.
      #
      # The address can either be an instance Address or else an
      # address string.
      #
      # ==== Arguments
      #
      # * +address+ - the end point address.
      def create_sender(address)
        _address = address

        if address.class == Qpid::Messaging::Address
          _address = address.address_impl
        end

        sender_impl = @session_impl.createSender(_address)
        sender_name = sender_impl.getName

        Qpid::Messaging::Sender.new(self, sender_impl)
      end

      # Retrieves the Sender with the specified name.
      #
      # Raises an exception if no such Sender exists.
      #
      # ==== Arguments
      #
      # * +name+ - the name of the Sender
      def sender(name)
        Qpid::Messaging::Sender.new self, @session_impl.getSender(name)
      end

      # Creates a new endpoint for receiving messages.
      #
      # The +address+ can either be an instance Address or else an
      # address string.
      #
      # ==== Arguments
      #
      # * +address+ - the end point address.
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
      # * +name+ - the name of the Receiver
      def receiver(name)
        Qpid::Messaging::Receiver.new self, @session_impl.getReceiver(name)
      end

      # Closes the +Session+ and all associated +Sender+ and +Receiver+ instances.
      #
      # *NOTE:* All +Session+ instances for a Connection are closed when the
      # Connection is closed. But closing a +Session+ does not affect the
      # owning Connection.
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
      # * +options+ - the set of options
      #
      # ==== Options
      #
      # * :message - if specified, then only that Message is acknowledged
      # * :sync - if true, the call will block until processed by the broker
      #
      # ==== Examples
      #
      #   # acknowledge all received messages
      #   session.acknowledge
      #
      #   # acknowledge a single message
      #   session.acknowledge :message => message
      #
      #   # acknowledge all messages, wait until the call finishes
      #   session.acknowledge :sync => true
      #
      #--
      # TODO: Add an optional block to be used for blocking calls.
      #++
      def acknowledge(options = {})
        sync = options[:sync] || false
        message = options[:message] if options[:message]

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

      # Requests synchronization with the broker.
      #
      # ==== Arguments
      #
      # * +options+ - the list of options
      #
      # ==== Options
      #
      # * +:block+ - if true, the call blocks until the broker acknowledges it
      #
      #--
      # TODO: Add an optional block to be used for blocking calls.
      #++
      def sync(args = {})
        block = args[:block] || false
        @session_impl.sync block
      end

      # Returns the total number of receivable messages, and messages already
      # received, by Receiver instances associated with this +Session+.
      def receivable; @session_impl.getReceivable; end

      # Returns the number of messages that have been acknowledged by this
      # +Session+ whose acknowledgements have not been confirmed as processed
      # by the broker.
      def unsettled_acks; @session_impl.getUnsettledAcks; end

      # Fetches the next Receiver with a message pending. Waits the specified
      # number of milliseconds before timing out.
      #
      # For a Receiver to be returned, it must have a capacity > 0 and have
      # Messages locally queued.
      #
      # If no Receiver is found within the time out period, then a MessageError
      # is raised.
      #
      # ==== Arguments
      #
      # * +timeout+ - the duration
      #
      # ==== Examples
      #
      #   loop do
      #
      #     begin
      #       # wait a maximum of one minute for the next receiver to be ready
      #       recv = session.next_receiver Qpid::Messaging::Duration::MINUTE
      #
      #       # get and dispatch the message
      #       msg = recv.get
      #       dispatch_message msg
      #
      #     rescue
      #       puts "No receivers were returned"
      #     end
      #
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
      def errors?; @session_impl.hasError; end

      # If the +Session+ has been rendered invalid due to some exception,
      # this method will result in that exception being raised.
      #
      # If none have occurred, then no exceptions are raised.
      #
      # ==== Examples
      #
      #   # show any errors that occurred during the Session
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

