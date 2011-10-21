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

require 'qpid/duration'

module Qpid

  module Messaging

    # Receiver defines a type for receiving messages.
    class Receiver

      def initialize(receiver_impl) # :nodoc:
        @receiver_impl = receiver_impl
      end

      def receiver_impl # :nodoc:
        @receiver_impl
      end

      # Retrieves a message from the receiver's local queue, or waits
      # for up to the duration specified for one to become available.
      def get(duration = Qpid::Messaging::Duration::FOREVER)
        message_impl = @receiver_impl.get duration.duration_impl
        create_message_wrapper message_impl unless message_impl.nil?
      end

      # Retrieves a message from the receiver's subscription, or waits
      # for up to the duration specified for one to become available.
      def fetch(duration = Qpid::Messaging::Duration::FOREVER)
        message_impl = @receiver_impl.fetch duration.duration_impl
        create_message_wrapper message_impl unless message_impl.nil?
      end

      # Sets the capacity.
      #
      # The capacity for a receiver determines the number of messages that
      # can be held in the receiver before being fetched.
      def capacity=(capacity); @receiver_impl.setCapacity capacity; end

      # Returns the capacity.
      def capacity; @receiver_impl.getCapacity; end

      # Returns the number of available messages waiting to be fetched.
      def available; @receiver_impl.getAvailable; end

      # Returns the number of messages that have been received and acknowledged
      # but whose acknowledgements have not been confirmed by the sender.
      def unsettled; @receiver_impl.getUnsettled; end

      # Cancels the reciever.
      def close; @receiver_impl.close; end

      # Returns whether the receiver is closed.
      def closed?; @receiver_impl.isClosed; end

      # Returns the name of the receiver
      def name; @receiver_impl.getName; end

      # Returns the Session for this receiver.
      def session; Qpid::Messaging::Session.new(@receiver_impl.getSession); end

      # Returns whether the underlying handle is valid.
      def valid?; @receiver_impl.isValid; end

      # Returns whether the underlying handle is null.
      def null?; @receiver_impl.isNull; end

      def swap receiver
        @receiver_impl.swap receiver.receiver_impl
      end

      private

      def create_message_wrapper message_impl
        Qpid::Messaging::Message.new({}, message_impl)
      end

    end

  end

end

