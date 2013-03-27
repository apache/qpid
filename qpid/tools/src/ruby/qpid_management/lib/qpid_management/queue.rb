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
  module Management
    # Representation of a queue. Properties include:
    # - acquires
    # - arguments
    # - autoDelete
    # - bindingCount
    # - bindingCountHigh
    # - bindingCountLow
    # - byteDepth
    # - byteFtdDepth
    # - byteFtdDequeues
    # - byteFtdEnqueues
    # - bytePersistDequeues
    # - bytePersistEnqueues
    # - byteTotalDequeues
    # - byteTotalEnqueues
    # - byteTxnDequeues
    # - byteTxnEnqueues
    # - consumerCount
    # - consumerCountHigh
    # - consumerCountLow
    # - discardsLvq
    # - discardsOverflow
    # - discardsPurge
    # - discardsRing
    # - discardsSubscriber
    # - discardsTtl
    # - durable
    # - exclusive
    # - flowStopped
    # - flowStoppedCount
    # - messageLatencyAvg
    # - messageLatencyCount
    # - messageLatencyMax
    # - messageLatencyMin
    # - msgDepth
    # - msgFtdDepth
    # - msgFtdDequeues
    # - msgFtdEnqueues
    # - msgPersistDequeues
    # - msgPersistEnqueues
    # - msgTotalDequeues
    # - msgTotalEnqueues
    # - msgTxnDequeues
    # - msgTxnEnqueues
    # - name
    # - releases
    # - reroutes
    # - unackedMessages
    # - unackedMessagesHigh
    # - unackedMessagesLow
    # - vhostRef
    class Queue < BrokerObject
      # Purges (removes) messages from this queue
      # @param [Fixnum] message_count number of messages to remove from the queue, or 0 for all messages
      # @param [Hash] filter an optional filter to use when removing messages
      def purge(message_count, filter={})
        invoke_method('purge', {'request' => message_count, 'filter' => filter}, "org.apache.qpid.broker:queue:#{name}")
      end

      # Reroutes messages from this queue to an exchange, either the queue's
      # alternate exchange, or the specified exchange
      # @param [Fixnum] message_count number of messages to reroute from the queue, or 0 for all messages
      # @param [Boolean] use_alternate_exchange whether to use the queue's alternate exchange as the destination
      # @param [String] exchange name of destination exchange
      # @param [Hash] filter an optional filter to use when rerouting messages
      def reroute(message_count, use_alternate_exchange, exchange, filter)
        args = {'request' => message_count,
                'useAltExchange' => use_alternate_exchange,
                'exchange' => exchange,
                'filter' => filter}

        invoke_method('reroute', args, "org.apache.qpid.broker:queue:#{name}")
      end
    end
  end
end
