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

module Qpid

  module Messaging

    # A Duration represents a period of time in milliseconds
    #
    # It defines the following named values as symbols:
    #
    # :FOREVER :: the maximum integer value for the platform
    # :IMMEDIATE :: an alias for 0
    # :SECOND :: 1,000ms
    # :MINUTE :: 60,000ms
    class Duration

      def initialize duration # :nodoc:
        @duration_impl = Cqpid::Duration.new duration
      end

      def duration_impl # :nodoc:
        @duration_impl
      end

      def self.add_item(key, value) # :nodoc:
        @hash ||= {}
        @hash[key] = Duration.new value
      end

      def self.const_missing(key) # :nodoc:
        @hash[key]
      end

      self.add_item :FOREVER,   Cqpid::Duration.FOREVER.getMilliseconds
      self.add_item :IMMEDIATE, Cqpid::Duration.IMMEDIATE.getMilliseconds
      self.add_item :SECOND,    Cqpid::Duration.SECOND.getMilliseconds
      self.add_item :MINUTE,    Cqpid::Duration.MINUTE.getMilliseconds

    end

  end

end

