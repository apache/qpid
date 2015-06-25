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
    # Representation of the broker. Properties include:
    # - abandoned
    # - abandonedViaAlt
    # - acquires
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
    # - connBacklog
    # - dataDir
    # - discardsLvq
    # - discardsNoRoute
    # - discardsOverflow
    # - discardsPurge
    # - discardsRing
    # - discardsSubscriber
    # - discardsTtl
    # - maxConns
    # - mgmtPubInterval
    # - mgmtPublish
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
    # - port
    # - queueCount
    # - releases
    # - reroutes
    # - stagingThreshold
    # - systemRef
    # - uptime
    # - version
    # - workerThreads
    class Broker < BrokerObject
      # Adds methods for the specified collections to be able to access all instances
      # of a given collection, as well as a single instance by oid.
      #
      # == Example
      # <tt>has_many :queues</tt> which will add:
      # * <tt>#queues</tt> to retrieve all queues
      # * <tt>#queue(oid)</tt> to retrieve a queue by oid (note, this is the short form of the object id, e.g. "myqueue" for a queue instead of "org.apache.qpid.broker:queue:myqueue"
      #
      # @param collections one or more symbols for the collections of objects a broker manages
      def self.has_many(*collections)
        [*collections].each do |collection|
          singular_form = collection.to_s[0..-2]
          capitalized_type = singular_form.gsub(/^\w/) { $&.upcase }

          define_method(collection) do
            @agent.find_all_by_class(Qpid::Management::const_get(capitalized_type))
          end

          define_method(singular_form) do |oid|
            @agent.find_by_object_id(Qpid::Management::const_get(capitalized_type), "org.apache.qpid.broker:#{singular_form}:#{oid}")
          end
        end
      end

      # Adds method for the specified types to be able to access the singular
      # instance of a given type.
      #
      # == Example
      # <tt>has_one :acl</tt> which will add:
      # * <tt>#acl</tt> to retrieve the Acl data for the Broker
      #
      # @param types one or more symbols for the singular objects a broker manages
      def self.has_one(*types)
        [*types].each do |type|
          capitalized_type = type.to_s.gsub(/^\w/) { $&.upcase }

          define_method("#{type}") do
            @agent.find_first_by_class(Qpid::Management::const_get(capitalized_type))
          end
        end
      end

      has_many :connections, :sessions, :subscriptions, :exchanges, :queues, :bindings, :links, :bridges
      has_one :acl, :memory

      # Adds an exchange to the broker
      # @param [String] type exchange type (fanout, direct, topic, headers, xml)
      # @param [String] name exchange name
      # @param [Hash] options exchange creation options
      def add_exchange(type, name, options={})
        create_broker_object('exchange', name, options.merge!({'exchange-type' => type}))
      end

      # Deletes an exchange from the broekr
      # @param [String] name exchange name
      def delete_exchange(name)
        invoke_method('delete', {'type' => 'exchange', 'name' => name})
      end

      # Adds a queue to the broker
      # @param [String] name queue name
      # @param [Hash] options queue creation options
      def add_queue(name, options={})
        create_broker_object('queue', name, options)
      end

      # Deletes a queue from the broker
      # @param [String] name queue name
      def delete_queue(name)
        invoke_method('delete', {'type' => 'queue', 'name' => name})
      end

      # Adds a binding from an exchange to a queue
      # @param [String] exchange exchange name
      # @param [String] queue queue name
      # @param [String] key binding key
      # @param [Hash] options binding creation options
      def add_binding(exchange, queue, key="", options={})
        create_broker_object('binding', "#{exchange}/#{queue}/#{key}", options)
      end

      # Deletes a binding from an exchange to a queue
      # @param [String] exchange exchange name
      # @param [String] queue queue name
      # @param [String] key binding key
      def delete_binding(exchange, queue, key="")
        invoke_method('delete', {'type' => 'binding', 'name' => "#{exchange}/#{queue}/#{key}"})
      end

      # Adds a link to a remote broker
      # @param [String] name link name
      # @param [String] host remote broker host name or IP address
      # @param [Fixnum] port remote broker port
      # @param [String] transport transport mechanism used to connect to the remote broker
      # @param [Boolean] durable should this link be persistent
      # @param [String] auth_mechanism authentication mechanism to use
      # @param [String] username user name to authenticate with the remote broker
      # @param [String] password password for the user name
      def add_link(name, host, port, transport='tcp', durable=false, auth_mechanism="", username="", password="")
        options = {
          'host' => host,
          'port' => port,
          'transport' => transport,
          'durable' => durable,
          'authMechanism' => auth_mechanism,
          'username' => username,
          'password' => password
        }

        create_broker_object('link', name, options)
      end

      # Deletes a link to a remote broker
      # @param [String] name link name
      def delete_link(name)
        invoke_method('delete', {'type' => 'link', 'name' => name})
      end

      # Adds a queue route
      # @param [String] name the name of the bridge to create
      # @param [Hash] options options for the queue route
      # @option options [String] :link the name of the link to use (required)
      # @option options [String] :queue the name of the source queue from which messages are pulled (required)
      # @option options [String] :exchange the name of the destination exchange to which messages are sent (required)
      # @option options [Fixnum] :sync the number of messages to send before issuing an explicit session sync (required)
      def add_queue_route(name, options={})
        validate_options(options, [:link, :queue, :exchange, :sync])

        properties = {
          'link' => options[:link],
          'src' => options[:queue],
          'dest' => options[:exchange],
          'srcIsQueue' => true,
          'sync' => options[:sync]
        }

        create_broker_object('bridge', name, properties)
      end

      # Adds an exchange route
      # @param [String] name the name of the bridge to create
      # @param [Hash] options options for the exchange route
      # @option options [String] :link the name of the link to use (required)
      # @option options [String] :exchange the name of the exchange to use (required)
      # @option options [String] :key routing key to federate (required)
      # @option options [Fixnum] :sync the number of messages to send before issuing an explicit session sync (required)
      # @option options [String] :bridge_queue name of the queue to use as a bridge queue (optional)
      def add_exchange_route(name, options={})
        validate_options(options, [:link, :exchange, :key, :sync])

        properties = {
          'link' => options[:link],
          'src' => options[:exchange],
          'dest' => options[:exchange],
          'key' => options[:key],
          'sync' => options[:sync]
        }

        properties['queue'] = options[:bridge_queue] if options.has_key?(:bridge_queue)

        create_broker_object('bridge', name, properties)
      end

      # Adds a dynamic route
      # @param [String] name the name of the bridge to create
      # @param [Hash] options options for the dynamic route
      # @option options [String] :link the name of the link to use (required)
      # @option options [String] :exchange the name of the exchange to use (required)
      # @option options [Fixnum] :sync the number of messages to send before issuing an explicit session sync (required)
      # @option options [String] :bridge_queue name of the queue to use as a bridge queue (optional)
      def add_dynamic_route(name, options={})
        validate_options(options, [:link, :exchange, :sync])

        properties = {
          'link' => options[:link],
          'src' => options[:exchange],
          'dest' => options[:exchange],
          'dynamic' => true,
          'sync' => options[:sync]
        }

        properties['queue'] = options[:bridge_queue] if options.has_key?(:bridge_queue)

        create_broker_object('bridge', name, properties)
      end

      # Deletes a bridge (route)
      # @param [String] name bridge name
      def delete_bridge(name)
        invoke_method('delete', {'type' => 'bridge', 'name' => name})
      end

    private

      def create_broker_object(type, name, options)
        invoke_method('create', {'type' => type,
                                 'name' => name,
                                 'properties' => options,
                                 'strict' => true})
      end

      def validate_options(options, required)
        required.each do |req|
          raise "Option :#{req.to_s} is required" unless options.has_key?(req)
        end
      end

    end
  end
end
