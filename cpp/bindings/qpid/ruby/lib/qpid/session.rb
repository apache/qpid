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

require 'cqpid'

require 'qpid/errors'

module Qpid

  module Messaging

    # A Session represents a distinct conversation between end points.
    class Session

      def initialize(session) # :nodoc:
        @session_impl = session
      end

      def session_impl # :nodoc:
        @session_impl
      end

      # Returns the +Connection+ for the +Session+.
      def connection
        connection_impl = @session_impl.getConnection
        Qpid::Messaging::Connection.new "", {}, connection_impl
      end

      # Creates a new endpoint for sending messages.
      def create_sender(address)
        _address = address

        if address.class == Qpid::Messaging::Address
          _address = address.address_impl
        end

        Qpid::Messaging::Sender.new(@session_impl.createSender(_address))
      end

      # Retrieves the +Sender+ with the specified name.
      def sender(name)
        result = nil

        begin
          sender_impl = @session_impl.getSender name
          result = Sender.for_impl sender_impl
        rescue
          # treat any error as a key error
        end

        raise Qpid::Messaging::KeyError, "No such sender: #{name}" if result.nil?
        result
      end

      # Retrieves the +Receiver+ with the specified name.
      def receiver(name)
        result = nil

        begin
          receiver_impl = @session_impl.getReceiver name
          result = Receiver.for_impl receiver_impl
        rescue
          # treat any error as a key error
        end

        raise Qpid::Messaging::KeyError, "No such receiver: #{name}" if result.nil?
        result
      end

      # Creates a new endpoint for receiving messages.
      def create_receiver(address)
        result = nil

        if address.class == Qpid::Messaging::Address
          address_impl = address.address_impl
          result = Qpid::Messaging::Receiver.new(@session_impl.createReceiver(address_impl))
        else
          result = Qpid::Messaging::Receiver.new(@session_impl.createReceiver(address))
        end

        return result
      end

      # Closes the Session and all associated Senders and Receivers.
      # All Sessions are closed when the associated Connection is closed.
      def close; @session_impl.close; end

      # Commits any pending transactions for a transactional session.
      def commit; @session_impl.commit; end

      # Rolls back any uncommitted transactions on a transactional session.
      def rollback; @session_impl.rollback; end

      # Acknowledges one or more outstanding messages that have been received
      # on this session.
      #
      # If a message is submitted (:message => something_message) then only
      # that message is acknowledged. Otherwise all messsages are acknowledged.
      #
      # If :sync => true then the call will block until the server completes
      # processing the acknowledgements.
      # If :sync => true then the call will block until processed by the server (def. false)
      def acknowledge(args = {})
        sync = args[:sync] || false
        message = args[:message] if args[:message]

        unless message.nil?
          @session_impl.acknowledge message.message_impl, sync
        else
          @session_impl.acknowledge sync
        end
      end

      # Rejects the specified message. A rejected message will not be redelivered.
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
      # If :block => true then the call will block until the server acknowledges.
      #
      # If :block => false (default) then the call will complete and the server
      # will send notification on completion.
      def sync(args = {})
        block = args[:block] || false
        @session_impl.sync block
      end

      # Returns the total number of receivable messages, and messages already received,
      # by Receivers associated with this session.
      def receivable; @session_impl.getReceivable; end

      # Returns the number of messages that have been acknowledged by this session
      # whose acknowledgements have not been confirmed as processed by the server.
      def unsettled_acks; @session_impl.getUnsettledAcks; end

      # Fetches the receiver for the next message.
      def next_receiver(timeout = Qpid::Messaging::Duration::FOREVER)
        receiver_impl = @session_impl.nextReceiver(timeout.duration_impl)
        Qpid::Messaging::Receiver.new receiver_impl
      end

      # Returns whether there are errors on this session.
      def error?; @session_impl.hasError; end

      def check_error; @session_impl.checkError; end

      # Returns if the underlying session is valid.
      def valid?; @session_impl.isValid; end

      # Returns if the underlying session is null.
      def null?; @session_impl.isNull; end

      def swap session
        @session_impl.swap session.session_impl
      end

    end

  end

end

