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

    # Message represents a message.
    class Message

      def initialize(args = {}, message_impl = nil)
        @message_impl = message_impl
        @message_impl = Cqpid::Message.new if @message_impl.nil?
        @message_impl.setContent args[:content].to_s if args[:content]
        @content = nil
      end

      def message_impl # :nodoc:
        @message_impl
      end

      # Assigns the reply to address.
      # The address must be an instance of Address.
      def reply_to=(address); @message_impl.setReplyTo address.address_impl; end

      # Returns the reply to address for the message as an instance of +Address+.
      def reply_to
        address_impl = @message_impl.getReplyTo
        # only return an address if a reply to was specified
        Qpid::Messaging::Address.new(nil, nil, nil, nil, address_impl) if address_impl
      end

      # Sets the subject.
      def subject=(subject); @message_impl.setSubject subject; end

      # Returns the subject.
      def subject; @message_impl.getSubject; end

      # Sets the content type.
      def content_type=(content_type); @message_impl.setContentType content_type; end

      # Returns the content type.
      def content_type; @message_impl.getContentType; end

      # Sets the message id.
      def message_id=(message_id); @message_impl.setMessageId message_id.to_s; end

      # Returns the message id.
      def message_id; @message_impl.getMessageId; end

      # Sets the user id.
      def user_id=(user_id); @message_impl.setUserId user_id; end

      # Returns the user id.
      def user_id; @message_impl.getUserId; end

      # Sets the correlation id.
      def correlation_id=(correlation_id); @message_impl.setCorrelationId correlation_id; end

      # Returns the correlation id.
      def correlation_id; @message_impl.getCorrelationId; end

      # Sets the priority.
      def priority=(priority); @message_impl.setPriority priority; end

      # Returns the priority.
      def priority; @message_impl.getPriority; end

      # Sets the time-to-live in milliseconds.
      def ttl=(duration); @message_impl.setTtl duration; end

      # Returns the time-to-live in milliseconds.
      def ttl; @message_impl.getTtl; end

      # Sets the durability.
      def durable=(durable); @message_impl.setDurable durable; end

      # Returns the durability.
      def durable; @message_impl.getDurable; end

      # Allows marking the message as redelivered.
      def redelivered=(redelivered); @message_impl.setRedelivered redelivered; end

      # Returns if the message was redelivered.
      def redelivered; @message_impl.getRedelivered; end

      # Returns all named properties.
      # *NOTE:* It is recommended to use the +foo[key]+ method for
      # retrieving properties.
      def properties; @message_impl.getProperties; end

      # Returns the value for the named property.
      def [](key); self.properties[key.to_s]; end

      # Assigns a value to the named property.
      def []=(key, value); @message_impl.setProperty(key.to_s, value.to_s); end

      # Sets the content.
      def content=(content)
        content_type = nil
        @content = content
        case @content
        when Hash
          content_type = "amqp/map"
        when Array
          content_type = "amqp/list"
        end
        if content_type.nil?
          @message_impl.setContent @content
        else
          Qpid::Messaging.encode @content, self, content_type
        end
      end

      # Returns the content.
      def content
        if @content.nil?
          @content = @message_impl.getContent

          # decode the content is necessary if it
          # has an encoded content type
          if ["amqp/list", "amqp/map"].include? @message_impl.getContentType
            @content = Qpid::Messaging.decode(self,
                                              @message_impl.getContentType)
          end

        end
        @content
      end

      # Returns the content's size.
      def content_size; @message_impl.getContentSize; end

    end

  end

end

