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

    describe Session do

      before(:each) do
        @connection    = double("Qpid::Messaging::Connection")
        @session_impl  = double("Cqpid::Session")
        @session       = Qpid::Messaging::Session.new @connection, @session_impl
        @sender_impl   = double("Cqpid::Sender")
        @receiver_impl = double("Cqpid::Receiver")
      end

      it "returns its implementation" do
        impl = @session.session_impl

        impl.should == @session_impl
      end

      it "returns its connection" do
        connection = @session.connection

        connection.should == @connection
      end

      it "creates a Sender from an Address" do
        address = Qpid::Messaging::Address.new "my-queue;{create:always}"

        @session_impl.should_receive(:createSender).
          with(address.address_impl).
          and_return(@sender_impl)
        @sender_impl.should_receive(:getName).
          and_return("my-queue")

        sender = @session.create_sender address

        sender.sender_impl.should == @sender_impl
      end

      it "creates a Sender from an address string" do
        address = "my-queue;{create:true}"

        @session_impl.should_receive(:createSender).
          with(address).
          and_return(@sender_impl)
        @sender_impl.should_receive(:getName).
          and_return("my-queue")

        sender = @session.create_sender address

        sender.sender_impl.should == @sender_impl
      end

      #######################################
      # scenarios involing an existing Sender
      #######################################
      describe "when retrieving a Sender by name" do

        before(:each) do
          address = "my-queue;{create:always}"
          @name   = "my-queue"

          @session_impl.should_receive(:createSender).
            with(address).
            and_return(@sender_impl)
          @sender_impl.should_receive(:getName).
            and_return(@name)

          @sender = @session.create_sender address
        end

        it "works when the name is valid" do
          sender = @session.sender @name

          sender.should == @sender
        end

        it "raises an error when the name is invalid" do
          expect {
            @session.sender @name.reverse
          }.to raise_error
        end

     end

      it "creates a Receiver from an Address" do
        address = Qpid::Messaging::Address.new "my-queue", ""

        @session_impl.should_receive(:createReceiver).
          with(address.address_impl).
          and_return(@receiver_impl)
        @receiver_impl.should_receive(:getName).
          and_return("my-queue")

        receiver = @session.create_receiver address

        receiver.receiver_impl.should == @receiver_impl
      end

      it "creates a Receiver from an address string" do
        address = "my-queue"

        @session_impl.should_receive(:createReceiver).
          with(address).
          and_return(@receiver_impl)
        @receiver_impl.should_receive(:getName).
          and_return("my-queue")

        receiver = @session.create_receiver address

        receiver.receiver_impl.should == @receiver_impl
      end

      #########################################
      # scenarios involving an existing Receiver
      ##########################################
      describe "when retrieving a Receiver by name" do

        before(:each) do
          address = "my-queue"
          @name   = "my-queue"

          @session_impl.should_receive(:createReceiver).
            with(address).
            and_return(@receiver_impl)
          @receiver_impl.should_receive(:getName).
            and_return(@name)

          @receiver = @session.create_receiver address
        end

        it "works with a valid name" do
          receiver = @session.receiver @name

          receiver.should == @receiver
        end

        it "raises an error when the name is invalid" do
          expect {
            @session.receiver @name.reverse
          }.to raise_error
        end

      end

      it "closes the session" do
        @session_impl.should_receive(:close)

        @session.close
      end

      it "commits a pending transaction" do
        @session_impl.should_receive(:commit)

        @session.commit
      end

      it "rolls back an uncommitted transaction" do
        @session_impl.should_receive(:rollback)

        @session.rollback
      end

      it "acknowledges all received messages" do
        @session_impl.should_receive(:acknowledge).
          with(false)

        @session.acknowledge
      end

      it "acknowledges all messages synchronously" do
        @session_impl.should_receive(:acknowledge).
          with(true)

        @session.acknowledge :sync => true
      end

      it "acknowledges all messages asynchronously" do
        @session_impl.should_receive(:acknowledge).
          with(false)

        @session.acknowledge :sync => false
      end

      ######################################
      # Scenarios involving a single message
      ######################################
      describe "with a single message" do

        before(:each) do
          @message = Qpid::Messaging::Message.new :content => "Testing"
        end

        it "can acknowledge asynchronously by default" do
          @session_impl.should_receive(:acknowledge).
            with(@message.message_impl, false)

          @session.acknowledge :message => @message
        end

        it "can acknowledge synchronously" do
          @session_impl.should_receive(:acknowledge).
            with(@message.message_impl, true)

          @session.acknowledge :message => @message, :sync => true
        end

        it "can acknowledge asynchronously" do
          @session_impl.should_receive(:acknowledge).
            with(@message.message_impl, false)

          @session.acknowledge :message => @message, :sync => false
        end

        it "can reject it" do
          @session_impl.should_receive(:reject).
            with(@message.message_impl)

          @session.reject @message
        end

        it "can release it" do
          @session_impl.should_receive(:release).
            with(@message.message_impl)

          @session.release @message
        end

      end

      it "does not block by default when synchronizating with the broker" do
        @session_impl.should_receive(:sync).
          with(false)

        @session.sync
      end

      it "can block while synchronizing with the broker" do
        @session_impl.should_receive(:sync).
          with(true)

        @session.sync :block => true
      end

      it "can not block while synchronizing with the broker" do
        @session_impl.should_receive(:sync).
          with(false)

        @session.sync :block => false
      end

      it "returns the number of messages that are receivable" do
        @session_impl.should_receive(:getReceivable).
          and_return(15)

        receivable = @session.receivable

        receivable.should == 15
      end

      it "returns the number of unsettled messages" do
        @session_impl.should_receive(:getUnsettledAcks).
          and_return(25)

        unsettled = @session.unsettled_acks

        unsettled.should == 25
      end

      it "waits forever by default for the next Receiver with messages" do
        @session_impl.should_receive(:nextReceiver).
          with(Qpid::Messaging::Duration::FOREVER.duration_impl).
          and_return(@receiver_impl)

        receiver = @session.next_receiver

        receiver.receiver_impl.should == @receiver_impl
      end

      it "uses the specified time when waiting for the next Receiver with messages" do
        @session_impl.should_receive(:nextReceiver).
          with(Qpid::Messaging::Duration::SECOND.duration_impl).
          and_return(@receiver_impl)

        receiver = @session.next_receiver Qpid::Messaging::Duration::SECOND

        receiver.receiver_impl.should == @receiver_impl
      end

      it "returns nil when no Receiver has messages within the timeout period" do
        @session_impl.should_receive(:nextReceiver).
          with(Qpid::Messaging::Duration::MINUTE.duration_impl).
          and_return(nil)

        receiver = @session.next_receiver Qpid::Messaging::Duration::MINUTE

        receiver.should be_nil
      end

      it "returns true when there are errors on the session" do
        @session_impl.should_receive(:hasError).
          and_return(true)

        errors = @session.errors?

        errors.should be_true
      end

      it "returns false when there are no errors on the session" do
        @session_impl.should_receive(:hasError).
          and_return(false)

        errors = @session.errors?

        errors.should be_false
      end

      it "causes exceptions to be raised when there are errors" do
        @session_impl.should_receive(:checkError).
          and_raise(RuntimeError)

        expect {
          @session.errors
        }.to raise_error(RuntimeError)
      end

    end

  end

end
