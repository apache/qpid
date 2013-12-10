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
    # Representation of an object in the broker retrieved via QMF
    class BrokerObject
      attr_reader :content

      # Creates a new BrokerObject
      # @param [BrokerAgent] agent the agent used to query the data from the broker
      # @param [Hash] content the raw QMF response data from the broker
      def initialize(agent, content)
        @agent = agent
        @content = content
        @values = content['_values']
      end

      # Refreshes the information associated with this instance by requerying the broker
      # @raise [ObjectDeletedError] if the object has been deleted
      def refresh!
        refreshed = @agent.named_object(self.class, id)
        if refreshed
          @content = refreshed.content
          @values = @content['_values']
        else
          raise ObjectDeletedError
        end
      end

      # Returns the full object id
      # @return [String] the full object id
      def id
        @content['_object_id']['_object_name']
      end

      # Helper method to convert a Class to its QMF name counterpart. For
      # example, QpidConfig::Connection will be converted to connection.
      # @param [Class] clazz the Class to convert
      # @return [String] the converted QMF name counterpart for this Class
      def self.qmf_class(clazz)
        clazz.name.split(/::/).last.downcase
      end

      # Returns the short object id, i.e. without the leading org.apache.qpid.broker:<class name>:
      # @return [String] the short object id
      def short_id
        clazz = BrokerObject.qmf_class(self.class)
        if id =~ /org.apache.qpid.broker:#{clazz}:(.*)/
          return $1;
        end
        return nil
      end

      # Returns the time at which this object was created
      # @return [Time] the time at which this object was created
      def created_at
        Time.at(content['_create_ts'] / 1000000000.0)
      end

      # Returns the time at which this object was deleted. Only ever applies to
      # BrokerObject instances created from a QMF event.
      # @return [Time] the time at which this object was deleted
      def deleted_at
        Time.at(content['_delete_ts'] / 1000000000.0)
      end

      # Returns the time at which this object was last updated
      # @return [Time] the time at which this object was last updated
      def updated_at
        Time.at(content['_update_ts'] / 1000000000.0)
      end
      
      # Exposes data from the QMF response
      # @param [String] key the key to look up a value, e.g. msgDepth for a queue
      # @return the value associated with the key, or nil if not found
      def [](key)
        return nil unless @values.has_key?(key)
        value = @values[key]
        if value.is_a?(Hash) and value.has_key?('_object_name')
          full_name = value['_object_name']
          colon = full_name.index(':')
          unless colon.nil?
            full_name = full_name[colon+1..-1]
            colon = full_name.index(':')
            return full_name[colon+1..-1] unless colon.nil?
          end
        end

        return value
      end

      # Exposes data from the QMF response via methods, e.g. queue.msgDepth
      def method_missing(method, *args, &block)
        key = method.to_s
        return self[key] if args.empty? and not self[key].nil?
        super
      end

      def to_s
        @values.to_s
      end

      # Invokes a QMF method
      # @see BrokerAgent#invoke_method
      def invoke_method(*args)
        @agent.invoke_method(*args)
      end
    end
  end
end
