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

require 'spec_helper'

module Qpid

  module Messaging

    describe Message do

      before(:each) do
        @message = Qpid::Messaging::Message.new :content => "My content"
      end

      it "returns its implementation" do
        impl = @message.message_impl

        impl.class.should == Cqpid::Message
      end

      it "can set the reply to address" do
        address = Qpid::Messaging::Address.new "my-queue;{create:always}"

        @message.reply_to = address

        reply_to = @message.reply_to

        reply_to.name.should == address.name
      end

      it "can set the reply to from an address string" do
        name = "my-queue"
        subject = "responses"
        address = "#{name}/#{subject}"

        @message.reply_to = address

        reply_to = @message.reply_to

        reply_to.name.should == name
        reply_to.subject.should == subject
      end

      it "should store the content when created" do
        content = @message.content

        content.should == "My content"
      end

      it "should store and retrieve string content properly" do
        content = random_string(64)

        @message.content_object = content
        @message.content_object.should == content
      end

      it "should store and retrieve numeric content properly" do
        content = rand(65536)

        @message.content_object = content
        @message.content_object.should == content
      end

      it "should store and retrieve map content properly" do
        content = {}
        (1..rand(128)).each { content["#{random_string(64)}"] = "#{random_string(64)}" }

        @message.content = content
        @message.content.should eq content
      end

      it "should store and retrieve list content properly" do
        content = []
        (1..rand(128)).each { content << "#{random_string(64)}" }

        @message.content = content
        @message.content.should eq content
      end

      it "should properly encode a map when created" do
        message = Qpid::Messaging::Message.new :content => {"foo" => "bar"}

        content      = message.content
        content_type = message.content_type

        content_type.should == "amqp/map"
        content.class == Hash
        content["foo"].should == "bar"
      end

      it "should properly encode a list when created" do
        message = Qpid::Messaging::Message.new :content => ["foo", "bar"]

        content      = message.content
        content_type = message.content_type

        content_type.should == "amqp/list"
        content.class == Array
        content.should include("foo")
        content.should include("bar")
      end

      it "should store the subject" do
        @message.subject = "new-subject"

        subject = @message.subject

        subject.should == "new-subject"
      end

      it "should update the content type" do
        @message.content_type = "amqp/audio"

        content_type = @message.content_type

        content_type.should == "amqp/audio"
      end

      it "should store the message id" do
        @message.message_id = "foo"

        id = @message.message_id

        id.should == "foo"
      end

      it "should store the user id" do
        @message.user_id = "foo"

        id = @message.user_id

        id.should == "foo"
      end

      it "should store the correlation id" do
        @message.correlation_id = "message1"

        id = @message.correlation_id

        id.should == "message1"
      end

      it "should store the priority" do
        @message.priority = 7

        priority = @message.priority

        priority.should == 7
      end

      it "should accept a Duration as the time to live" do
        @message.ttl = Qpid::Messaging::Duration::SECOND

        ttl = @message.ttl

        ttl.milliseconds.should == Qpid::Messaging::Duration::SECOND.milliseconds
      end

      it "should accept an integer value as the time to live" do
        @message.ttl = 15000

        ttl = @message.ttl

        ttl.milliseconds.should == 15000
      end

      it "should update the durable flag" do
        @message.durable = true

        durable = @message.durable

        durable.should == true
      end

      it "should update the redelivered flag" do
        @message.redelivered = true

        redelivered = @message.redelivered

        redelivered.should == true
      end

      it "should store a property" do
        property = @message[:test_property]

        property.should == nil

        @message[:test_property] = "test_value1"

        property = @message[:test_property]

        property.should == "test_value1"
      end

      it "should convert a symbol property value to a string" do
        @message[:test_property] = :test_value2

        property = @message[:test_property]

        property.should == "test_value2"
      end

      it "should convert a symbol property name to a string" do
        @message[:test_property] = "test_value3"

        property = @message["test_property"]

        property.should == "test_value3"
      end

      it "should store text content" do
        @message.content = "This is the content."

        content = @message.content

        content.should == "This is the content."
      end

      it "should store list content" do
        list = ["foo", "bar"]

        @message.content = list

        content      = @message.content
        content_type = @message.content_type

        content.should      == list
        content_type.should == "amqp/list"
      end

      it "should convert symbol list elements to strings" do
        @message.content = [:farkle]

        content = @message.content.first

        content.should == "farkle"
      end

      it "should store map content" do
        map = {"foo" => "bar"}

        @message.content = map

        content      = @message.content
        content_type = @message.content_type

        content.should      == map
        content_type.should == "amqp/map"
      end

      it "should convert symbol map elements to strings" do
        @message.content = {:first_name => :qpid}

        content = @message.content["first_name"]

        content.should == "qpid"
      end

      describe "with content from the underlying implementation" do

        before(:each) do
          @message_impl = double("Cqpid::Message")
          @message = Qpid::Messaging::Message.new :impl => @message_impl
        end

        it "should return simple text content" do
          @message_impl.should_receive(:getContent).
            and_return("my content")
          @message_impl.should_receive(:getContentType).
            and_return("")

          content = @message.content

          content.should == "my content"
        end

        it "should decode a list" do
          list = ["first", "second"]

          @message_impl.should_receive(:getContent).
            and_return(list)
          @message_impl.should_receive(:getContentType).
            twice.
            and_return("amqp/list")
          Qpid::Messaging.stub!(:decode).
            with(@message, "amqp/list").
            and_return(list)

          content = @message.content

          content.should == list
        end

        it "should decode a map" do
          map = {"first" => "second"}

          @message_impl.should_receive(:getContent).
            and_return(map)
          @message_impl.should_receive(:getContentType).
            twice.
            and_return("amqp/map")
          Qpid::Messaging.stub!(:decode).
            with(@message, "amqp/map").
            and_return(map)

          content = @message.content

          content.should == map
        end


      end

    end

  end

end
