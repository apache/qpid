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

# for simplistic UUID - may want to consider something better in the future
require 'securerandom'

# Ruby 1.8 doesn't include SecureRandom#uuid, so let's add it if it's missing
unless SecureRandom.respond_to? :uuid
  module SecureRandom
    def self.uuid
      ary = self.random_bytes(16).unpack("NnnnnN")
      ary[2] = (ary[2] & 0x0fff) | 0x4000
      ary[3] = (ary[3] & 0x3fff) | 0x8000
      "%08x-%04x-%04x-%04x-%04x%08x" % ary
    end
  end
end

module Qpid
  module Management
    # This is the primary class that interacts with a Qpid messaging broker for
    # querying information from the broker and for configuring it.
    class BrokerAgent
      # Creates a new BrokerAgent instance. A new Qpid::Messaging::Session,
      # Qpid::Messaging::Receiver, and Qpid::Messaging::Sender will be created
      # so this instance of the BrokerAgent may send requests to the broker
      # and receive replies back.
      # @param [Qpid::Messaging::Connection] connection a valid, opened connection
      def initialize(connection)
        @connection = connection
        @session = @connection.create_session()
        @reply_to = "qmf.default.topic/direct.#{SecureRandom.uuid}; {node: {type:topic}, link:{x-declare:{auto-delete:True,exclusive:True}}}"
        @reply_rx = @session.create_receiver(@reply_to)
        @reply_rx.capacity = 10
        @tx = @session.create_sender("qmf.default.direct/broker")
      end

      # Closes the Qpid::Messaging::Session for this BrokerAgent.
      def close()
        @session.close()
      end

      # Queries the broker for the Broker QMF object.
      # @return [Broker] the broker QMF object
      def broker()
        find_first_by_class(Broker)
      end

      # Queries the broker for the Cluster QMF object.
      # @return [Cluster] the cluster QMF object
      def cluster
        find_first_by_class(Cluster)
      end

      # Queries the broker for the HaBroker QMF object.
      # @return [HaBroker] the HA broker QMF object
      def ha_broker
        find_first_by_class(HaBroker)
      end

      # Invokes a method on a target object.
      # @param [String] method the name of the method to invoke
      # @param [Hash] args the arguments to pass to the method
      # @param [String] addr the full id of the target object
      # @param [Fixnum] timeout the amount of time to wait for the broker to respond to the method invocation
      def invoke_method(method, args, addr="org.apache.qpid.broker:broker:amqp-broker", timeout=10)
        content = {'_object_id' => {'_object_name' => addr},
                   '_method_name' => method,
                   '_arguments' => args}

        message = Qpid::Messaging::Message.new()
        message.content = content
        message.reply_to = @reply_to
        message['method'] = 'request'
        message['qmf.opcode'] = '_method_request'
        message['x-amqp-0-10.app-id'] = 'qmf2'
        message.subject = 'broker'

        @tx.send(message)

        response = @reply_rx.fetch(Qpid::Messaging::Duration.new(timeout * 1000))
        @session.acknowledge()

        raise "Exception from Agent: #{response.content['_values']}" if response.properties['qmf.opcode'] == '_exception'
        raise "Bad response: #{response.properties}" if response.properties['qmf.opcode'] != '_method_response'

        return response.content['_arguments']
      end

      def send_query(query)
        message = Qpid::Messaging::Message.new()
        message.content = query
        message.reply_to = @reply_to
        message['method'] = 'request'
        message['qmf.opcode'] = '_query_request'
        message['x-amqp-0-10.app-id'] = 'qmf2'
        message.subject = 'broker'

        @tx.send(message)

        response = @reply_rx.fetch(Qpid::Messaging::Duration.new(10*1000))
        @session.acknowledge()

        raise 'Bad response' if response.properties['qmf.opcode'] != '_query_response'

        items = response.content

        while response.properties.has_key?('partial')
          response = @reply_rx.fetch(Qpid::Messaging::Duration.new(10*1000))
          items += response.content
          @session.acknowledge()
        end

        return items
      end

      def find_all_by_class(clazz)
        query = {
          '_what' => 'OBJECT',
          '_schema_id' => {
            '_class_name' => BrokerObject.qmf_class(clazz)
          }
        }

        items = send_query(query)

        [].tap do |objs|
          for item in items
            objs << clazz.new(self, item)
          end
        end
      end

      def find_first_by_class(clazz)
        objects = find_all_by_class(clazz)
        return objects[0] if objects.size > 0
        return nil
      end

      def find_by_object_id(clazz, oid)
        query = {
          '_what' => 'OBJECT',
          '_object_id' => {
            '_object_name' => oid
          }
        }

        results = send_query(query)

        return clazz.new(self, results[0]) if results.count == 1 and not results[0].nil?

        # return nil if not found
        return nil
      end
    end
  end
end
