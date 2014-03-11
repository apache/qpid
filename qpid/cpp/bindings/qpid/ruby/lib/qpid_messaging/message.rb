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

    # A +Message+ represents an routable piece of information.
    class Message

      # Creates a +Message+.
      #
      # ==== Options
      #
      # * +:content+ - the content
      #
      # ==== Examples
      #
      #   # create a simple message and sends it
      #   message = Qpid::Messaging::Message.new :content => "This is a message."
      #   sender.send message
      #
      def initialize(args = {})
        @message_impl = (args[:impl] if args[:impl]) || nil
        @message_impl = Cqpid::Message.new if @message_impl.nil?
        args = {} if args.nil?
        self.content_object = args[:content] if args[:content]
      end

      def message_impl # :nodoc:
        @message_impl
      end

      # Sets the reply-to address.
      #
      # The address can either be an instance of Address or else and
      # address string.
      #
      # ==== Options
      #
      # * +address+ - the address
      #
      # ==== Examples
      #
      #   # set replies using an Address
      #   msg.reply_to = Qpid:Messaging::Address.new "my-responses"
      #   # set replies using an address string
      #   msg.reply_to = "my-feed/responses"
      #
      def reply_to=(address)
        address = Qpid::Messaging::Address.new "#{address}" if !address.is_a? Qpid::Messaging::Address

        @message_impl.setReplyTo address.address_impl
      end

      # Returns the reply to address for the +Message+.
      def reply_to
        address_impl = @message_impl.getReplyTo
        # only return an address if a reply to was specified
        Qpid::Messaging::Address.new(nil, address_impl) if address_impl
      end

      # Sets the subject for the +Message+.
      #
      # ==== Options
      #
      # * +subject+ - the subject
      def subject=(subject); @message_impl.setSubject subject; end

      # Returns the subject of the +Message+.
      def subject; @message_impl.getSubject; end

      # Sets the content type for the +Message+.
      #
      # This should be set by the sending application and indicates to the
      # recipients of the message how to interpret or decode the content.
      #
      # By default, only dictionaries and maps are automatically given a content
      # type. If this content type is replaced then retrieving the content will
      # not behave correctly.
      #
      # ==== Options
      #
      # * +content_type+ - the content type
      #
      # ==== Examples
      #
      #   # send base64 encoded data in a mesage
      #   msg = Qpid::Messaging::Message.new :content = "UXBpZCBSdWxlcyEK"
      #   msg.content_type = "application/base64"
      #
      def content_type=(content_type); @message_impl.setContentType content_type; end

      # Returns the content type for the +Message+.
      def content_type; @message_impl.getContentType; end

      # Sets the message id.
      #
      # *NOTE:* this field must be a UUID type currently. A non-UUID value will
      # be converted to a zero UUID, though a blank ID will be left untouched.
      #
      # ==== Options
      #
      # * +id+ - the id
      #
      # ==== Examples
      #
      #   # this example only works in Ruby >= 1.9, for 1.8 use a UUID library
      #   require 'SecureRandom'
      #   msg.message_id = SecureRandom.uuid
      #
      def message_id=(message_id); @message_impl.setMessageId message_id.to_s; end

      # Returns the message id.
      def message_id; @message_impl.getMessageId; end

      # Sets the user id for the +Message+.
      #
      # This should in general be the user-id which was used when authenticating
      # the connection itself, as the messaging infrastructure will verify
      # this.
      #
      # See Qpid::Messaging::Connection.authenticated_username
      #
      # *NOTE:* If the id is not a +String+ then the id is set using
      # the object's string representation.
      #
      # ==== Options
      #
      # * +id+ - the id
      #
      def user_id=(user_id); @message_impl.setUserId user_id; end

      # Returns the user id for the +Message+.
      def user_id; @message_impl.getUserId; end

      # Sets the correlation id of the +Message+.
      #
      # The correlation id can be used as part of a protocol for message
      # exchange patterns; e.g., a request-response pattern might require
      # the correlation id of the request and the response to match, or it
      # might use the message id of the request as the correlation id on
      # the response.
      #
      # *NOTE:* If the id is not a +String+ then the id is setup using
      # the object's string representation.
      #
      # ==== Options
      #
      # * +id+ - the id
      #
      def correlation_id=(correlation_id); @message_impl.setCorrelationId correlation_id; end

      # Returns the correlation id of the +Message+.
      def correlation_id; @message_impl.getCorrelationId; end

      # Sets the priority of the +Message+.
      #
      # This may be used by the messaging infrastructure to prioritize
      # delivery of messages with higher priority.
      #
      # *NOTE:* If the priority is not an integer type then it is set using
      # the object's integer representation. If the integer value is greater
      # than 8-bits then only the first 8-bits are used.
      #
      # ==== Options
      #
      # * +priority+ - the priority
      #
      def priority=(priority); @message_impl.setPriority priority; end

      # Returns the priority for the +Message+.
      def priority; @message_impl.getPriority; end

      # Sets the time-to-live in milliseconds.
      #
      # This can be used by the messaging infrastructure to discard messages
      # that are no longer of relevance.
      #
      # ==== Options
      #
      # * +duration+ - the number of milliseconds
      #
      def ttl=(duration)
        if duration.is_a? Qpid::Messaging::Duration
          @message_impl.setTtl duration.duration_impl
        else
          @message_impl.setTtl Cqpid::Duration.new duration.to_i
        end
      end

      # Returns the time-to-live in milliseconds.
      def ttl; Qpid::Messaging::Duration.new @message_impl.getTtl.getMilliseconds; end

      # Sets the durability of the +Message+.
      #
      # This is a hint to the messaging infrastructure that the message
      # should be persisted or otherwise stored. This helps to ensure
      # that the message is not lost due to failures or a shutdown.
      #
      # ==== Options
      #
      # * +durable+ - the durability flag (def. false)
      #
      def durable=(durable); @message_impl.setDurable durable; end

      # Returns the durability for the +Message+.
      def durable; @message_impl.getDurable; end

      # This is a hint to the messaging infrastructure that if de-duplication
      # is required, that this message should be examined to determine if it
      # is a duplicate.
      #
      # ==== Options
      #
      # * +redelivered+ - sets the redelivered state (def. false)
      #
      # ==== Examples
      #
      #   # processed is a collection of messages already received
      #   msg.redelivered = true if processed.include? msg.message_id
      #
      def redelivered=(redelivered); @message_impl.setRedelivered redelivered; end

      # Returns whether the +Message+ has been marked as redelivered.
      def redelivered; @message_impl.getRedelivered; end

      # Returns all named properties.
      #
      # *NOTE:* It is recommended to use the []= method for
      # retrieving and setting properties. Using this method may
      # result in non-deterministic behavior.
      def properties; @message_impl.getProperties; end

      # Returns the value for the named property.
      #
      # ==== Options
      #
      # * +name+ - the property name
      #
      # ==== Examples
      #
      #   # use of message properties to mark a message as digitally signed
      #   verify(msg) if msg[:signed]
      #
      def [](key); self.properties[key.to_s]; end

      # Assigns a value to the named property.
      #
      # A property's name or value, if a symbol, will be converted to a string
      # representation. However, you will still be able to access them using
      # a symbol for the name.
      #
      # ==== Options
      #
      # * +name+ - the property name
      # * +value+ - the property value
      #
      # ==== Examples
      #
      #   # set the signed attribute on a message and then retrieve it
      #   msg[:signed] = true # sets "signed" => true
      #   puts "It's signed" if msg["signed"] # outputs "It's signed"
      #
      def []=(key, value)
        @message_impl.setProperty(key.to_s,
                                  Qpid::Messaging.stringify(value))
      end

      # Sets the content for the +Message+.
      #
      # Content is automatically encoded for Array and Hash types. Other types
      # need to set their own content types (via content_type) in order to
      # specify how recipients should process the content.
      #
      # ==== Options
      #
      # * +content+ - the content
      #
      # ==== Examples
      #
      #   # set a simple content for a message
      #   msg.content = "This is a simple message."
      #   # sets content that is automatically encoded
      #   msg.content = {:foo => :bar}
      #
      def content=(content)
        content_type = nil
        @content = Qpid::Messaging.stringify(content)
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

      # Returns the content of the +Message+.
      #
      # Content is automatically decoded based on the specified content type.
      # If the content type is application-specific, then no decoding is
      # performed and the content is returnedas a +String+ representation.
      #
      # For example, if an array of integers are sent, then the receiver will
      # find the message content to be an array of String objects, where each
      # String is a representation of the sent integer value.
      #
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

      # Returns the content's size in bytes.
      def content_size; @message_impl.getContentSize; end

      # Sets the message content.
      #
      # ==== Options
      #
      # * +content+ - the content
      #
      # ==== Examples
      #
      #   # set a simple content for a message
      #   msg.content_object = "This is a simple message."
      #   # sets content that is automatically encoded
      #   msg.content_object = {:foo => :bar}
      #
      def content_object=(content)
        @message_impl.setContentObject(Qpid::Messaging.stringify(content))
      end

      # Returns the content of the +Message+.
      #
      def content_object
        @message_impl.getContentObject()
      end

    end

  end

end

