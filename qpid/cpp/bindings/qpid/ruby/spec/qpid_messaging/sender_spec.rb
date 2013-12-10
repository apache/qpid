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

    describe Sender do

      before(:each) do
        @message      = double("Qpid::Messaging::Message")
        @message_impl = double("Cqpid::Message")
        @sender_impl  = double("Cqpid::Sender")
        @session      = double("Qpid::Messaging::Session")

        @sender = Qpid::Messaging::Sender.new @session, @sender_impl
      end

      it "returns its implementation" do
        impl = @sender.sender_impl

        impl.should == @sender_impl
      end

      it "sends a message" do
        @message.should_receive(:message_impl).
          and_return(@message_impl)
        @sender_impl.should_receive(:send).
          with(@message_impl, false)

        @sender.send @message
      end

      it "sends a message with optional synch" do
        @message.should_receive(:message_impl).
          and_return(@message_impl)
        @sender_impl.should_receive(:send).
          with(@message_impl, true)

        @sender.send @message, :sync => true
      end

      it "sends a message with an optional block" do
        block_called = false

        @message.should_receive(:message_impl).
          and_return(@message_impl)
        @sender_impl.should_receive(:send).
          with(@message_impl, false)

        @sender.send @message do |message|
          block_called = true if message == @message
        end

        block_called.should be_true
      end

      it "closes" do
        @sender_impl.should_receive(:close)

        @sender.close
      end

      it "returns its name" do
        @sender_impl.should_receive(:getName).
          and_return("farkle")

        name = @sender.name

        name.should == "farkle"
      end

      it "sets its capacity" do
        @sender_impl.should_receive(:setCapacity).
          with(100)

        @sender.capacity = 100
      end

      it "returns its capacity" do
        @sender_impl.should_receive(:getCapacity).
          and_return(25)

        capacity = @sender.capacity

        capacity.should == 25
      end

      it "returns the number of unsettled messages" do
        @sender_impl.should_receive(:getUnsettled).
          and_return(15)

        unsettled = @sender.unsettled

        unsettled.should == 15
      end

      it "returns the number of available message slots" do
        @sender_impl.should_receive(:getAvailable).
          and_return(50)

        available = @sender.available

        available.should == 50
      end

      it "returns a reference to its session" do
        session = @sender.session

        session.should == @session
      end

    end

  end

end
