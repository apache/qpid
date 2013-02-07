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

    # A Duration represents a period of time in milliseconds
    #
    # == Named Durations
    #
    # The following named +Durations+ are available as symbols:
    #
    # [FOREVER]
    #   The maximum integer value for the platform. Effectively this will wait
    #   forever.
    #
    # [IMMEDIATE]
    #   An alias for 0 milliseconds.
    #
    # [SECOND]
    #   An alias for 1,000 milliseconds.
    #
    # [MINUTE]
    #   And alias for 60,000 millisecons.
    #
    class Duration

      # Creates a Duration with the specified length, in milliseconds.
      #
      # ==== Options
      #
      # * +length+ - The duration in +milliseconds+.
      #
      # ==== Examples
      #
      #   # creates a duration of 15 seconds
      #   # REMEMBER: Duration deals in milliseconds
      #   delay = Qpid::Messaging::Duration.new 15000
      #
      def initialize length
        @duration_impl = Cqpid::Duration.new length
      end

      def duration_impl # :nodoc:
        @duration_impl
      end

      # Returns the period of time in +milliseconds+.
      #
      # ==== Examples
      #
      #   # doubling growth in waiting for messages in a loop
      #   do loop
      #     set the base duration waiting length
      #     timeout = Qpid::Messaging::Duration::SECOND
      #     msg = nil
      #     # loop until we receive a message
      #     while msg.nil?
      #       puts "Waiting #{timeout.milliseconds}ms"
      #       msg = recv.get timeout
      #       # if nothing was received, double the duration
      #       if msg.nil?
      #         # double out timeout
      #         timeout = timeout * 2
      #       else
      #         # do something with the message
      #         puts "Received: #{msg.content}"
      #       end
      #     end
      #   end
      #
      def milliseconds
        @duration_impl.getMilliseconds
      end

      # Multiplies the duration of the +Duration+ and returns a new instance.
      #
      # Raises exceptions on a negative factor. Returns
      # Qpid::Messaging::Duration::IMMEDIATE when the factor is 0.
      #
      # ==== Examples
      #
      #   # return a duration that is 2 minutes (120,000 ms)
      #   twominutes = Qpid::Messaging::Duration::MINUTE * 2
      #
      def *(factor)
        raise TypeError.new "Factors must be non-zero positive values" if factor < 0
        return Qpid::Messaging::Duration::IMMEDIATE if factor.zero?
        Qpid::Messaging::Duration.new((self.milliseconds * factor).floor)
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

