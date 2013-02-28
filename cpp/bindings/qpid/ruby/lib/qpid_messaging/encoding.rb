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

    # Encodes the supplied content into the given message.
    def self.encode content, message, encoding = nil # :nodoc:
      Cqpid::encode content, message.message_impl, encoding
    end

    # Decodes and returns the message's content.
    def self.decode(message, content_type = nil) # :nodoc:
      content_type = message.content_type if content_type.nil?

      case content_type
        when "amqp/map"
          return Cqpid.decodeMap message.message_impl
        when "amqp/list"
          return Cqpid.decodeList message.message_impl
      end

      message.content
    end

    # Takes as input any type and converts anything that's a symbol
    # into a string.
    def self.stringify(value) # :nodoc:
      # set the default value
      result = value

      case value

      when Symbol
        result = value.to_s

      when Hash
        result = {}
        value.each_pair do |key, value|
          result[stringify(key)] = stringify(value)
        end

      when Array
        result = []
        value.each do |element|
          result  << stringify(element)
        end

      end

      return result

    end

  end

end

