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

    describe Receiver do

      before(:each) do
        @message_impl  = double("Cqpid::Message")
        @session       = double("Qpid::Messaging::Session")
        @receiver_impl = double("Cqpid::Receiver")

        @receiver = Qpid::Messaging::Receiver.new @session, @receiver_impl
      end

      it "returns the underlying implementation" do
        impl = @receiver.receiver_impl

        impl.should == @receiver_impl
      end

      it "gets a message with the default duration" do
        @receiver_impl.should_receive(:get).
          with(Qpid::Messaging::Duration::FOREVER.duration_impl).
          and_return(@message_impl)

        message = @receiver.get

        message.message_impl.should == @message_impl
      end

      it "gets a message with a specified duration" do
        @receiver_impl.should_receive(:get).
          with(Qpid::Messaging::Duration::SECOND.duration_impl).
          and_return(@message_impl)

        message = @receiver.get Qpid::Messaging::Duration::SECOND

        message.message_impl.should == @message_impl
      end

      it "returns nil when get receives no message" do
        @receiver_impl.should_receive(:get).
          with(Qpid::Messaging::Duration::MINUTE.duration_impl).
          and_return(nil)

        message = @receiver.get Qpid::Messaging::Duration::MINUTE

        message.should be_nil
      end

      it "fetches a message with the default duration" do
        @receiver_impl.should_receive(:fetch).
          with(Qpid::Messaging::Duration::FOREVER.duration_impl).
          and_return(@message_impl)

        message = @receiver.fetch

        message.message_impl.should == @message_impl
      end

      it "fetches a message with a specified duration" do
        @receiver_impl.should_receive(:fetch).
          with(Qpid::Messaging::Duration::SECOND.duration_impl).
          and_return(@message_impl)

        message = @receiver.fetch Qpid::Messaging::Duration::SECOND

        message.message_impl.should == @message_impl
      end

      it "returns nil when fetch recieves no message" do
        @receiver_impl.should_receive(:fetch).
          with(Qpid::Messaging::Duration::MINUTE.duration_impl).
          and_return(nil)

        message = @receiver.fetch Qpid::Messaging::Duration::MINUTE

        message.should be_nil
      end

      it "assigns capacity" do
        @receiver_impl.should_receive(:setCapacity).
          with(10)

        @receiver.capacity = 10
      end

      it "returns the capacity" do
        @receiver_impl.should_receive(:getCapacity).
          and_return(10)

        capacity = @receiver.capacity

        capacity.should == 10
      end

      it "reports the number of available messages" do
        @receiver_impl.should_receive(:getAvailable).
          and_return(20)

        available = @receiver.available

        available.should == 20
      end

      it "reports the number of unsettled messages" do
        @receiver_impl.should_receive(:getUnsettled).
          and_return(25)

        unsettled = @receiver.unsettled

        unsettled.should == 25
      end

      it "closes" do
        @receiver_impl.should_receive(:close)

        @receiver.close
      end

      it "reports its closed status" do
        @receiver_impl.should_receive(:isClosed).
          and_return(true)

        closed = @receiver.closed?

        closed.should == true
      end

      it "returns its name" do
        @receiver_impl.should_receive(:getName).
          and_return("farkle")

        name = @receiver.name

        name.should == "farkle"
      end

      it "returns its related session" do
        session = @receiver.session

        session.should == @session
      end

    end

  end

end
