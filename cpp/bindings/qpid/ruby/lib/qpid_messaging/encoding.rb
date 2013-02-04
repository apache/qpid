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

    # Encodes the supplied content into the given message.
    def self.encode content, message, encoding = nil
      prepared = content
      case content
      when Hash
        prepared = {}
        content.each_pair do |key,value|
          prepared[key.to_s] = value.to_s
        end
        Cqpid::encode prepared, message.message_impl
      when Array
        prepared = []
        content.each {|value| prepared << value.to_s}
        Cqpid::encode prepared, message.message_impl
      end
    end

    # Decodes and returns the message's content.
    def self.decode(message, content_type = nil)
      content_type = message.content_type if content_type.nil?

      case content_type
        when "amqp/map"
          return Cqpid.decodeMap message.message_impl
        when "amqp/list"
          return Cqpid.decodeList message.message_impl
      end

      message.content
    end

  end

end

