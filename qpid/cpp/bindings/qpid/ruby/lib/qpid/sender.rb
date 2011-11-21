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

module Qpid

  module Messaging

    # Sender defines a type for sending messages.
    class Sender

      def initialize(sender_impl) # :nodoc:
        @sender_impl = sender_impl
      end

      def sender_impl # :nodoc:
        @sender_impl
      end

      # Sends a message.
      def send(message, args = {})
        sync = args[:sync] || false
        @sender_impl.send message.message_impl, sync
      end

      # Closes the sender.
      def close; @sender_impl.close; end

      # Returns the name for the sender.
      def name; @sender_impl.getName; end

      # Sets the capacity for the sender, which is the number of outgoing
      # messages that can be held pending confirmation or receipt by
      # the broker.
      def capacity=(capacity); @sender_impl.setCapacity capacity; end

      # Returns the capacity.
      def capacity; @sender_impl.getCapacity; end

      # Returns the number of messages sent that are pending receipt
      # confirmation by the broker.
      def unsettled; @sender_impl.getUnsettled; end

      # Returns the available capacity for sending messages.
      def available
        @sender_impl.getAvailable
      end

      # Returns the Session for this sender.
      def session; Qpid::Messaging::Session.new @sender_impl.getSession; end

      # Returns if the underlying sender is valid.
      def valid?; @sender_impl.isValid; end

      # Returns if the underlying sender is null.
      def null?; @sender_impl.isNull; end

      def swap sender
        @sender_impl.swap sender.sender_impl
      end

    end

  end

end

