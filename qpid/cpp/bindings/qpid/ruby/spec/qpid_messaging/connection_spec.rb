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

    describe Connection do

      before(:each) do
        @session_impl    = double('Cqpid::Session')
        @connection_impl = double('Cqpid::Connection')

        @connection = Qpid::Messaging::Connection.new :impl => @connection_impl
      end

      it "accepts options on construction" do
        expect {
          connection = Qpid::Messaging::Connection.new :options => {:username => "foo"}

          connection.options.should include("username")
        }.to_not raise_error
      end

      it "returns the underlying implementation" do
        impl = @connection.connection_impl

        impl.should == @connection_impl
      end

      it "opens the connection" do
        @connection_impl.should_receive(:open)

        @connection.open
      end

      it "closes the connection" do
        @connection_impl.should_receive(:close)

        @connection.close
      end

      it "retrieves a session by name" do
        @connection_impl.should_receive(:getSession).
          with("farkle").
          and_return(@session_impl)

        session = @connection.session "farkle"

        session.session_impl.should == @session_impl
      end

      it "raises an error when a session name is invalid" do
        @connection_impl.should_receive(:getSession).
          with("farkle").
          and_raise(RuntimeError)

        expect {
          @connection.session "farkle"
        }.to raise_error
      end

      ####################################################################
      # test conditions for when a connection is not connected to a broker
      ####################################################################
      describe "when closed" do

        before(:each) do
          @connection_impl.should_receive(:isOpen).
            and_return(false)
        end

        it "returns false when not connected to a broker" do
          open = @connection.open?

          open.should == false
        end

        it "should raise an error when creating a session on a closed connection" do
          expect {
            @connection.create_session
          }.to raise_error(RuntimeError)
        end

        it "raises an error when creating a transactional session on a closed connection" do
          expect {
            @connection.create_session :transactional => true
          }.to raise_error(RuntimeError)
        end

        it "raises an error when creating a named session on a closed connection" do
          expect {
            @connection.create_session :name => "test", :transactional => true
          }.to raise_error(RuntimeError)
        end

        it "returns a null username when not connected" do
          username = @connection.authenticated_username

          username.should be_nil
        end

      end

      #########################################################
      # test conditions for when a connection must be connected
      #########################################################
      describe "when connected" do

        before(:each) do
          @connection_impl.should_receive(:isOpen).
            and_return(true)
        end

        it "returns true when connected to a broker" do
          open = @connection.open?

          open.should == true
        end

        it "creates a session" do
          @connection_impl.should_receive(:createSession).
            and_return(@session_impl)

          session = @connection.create_session

          session.session_impl.should == @session_impl
        end

        it "creates a named session with a name when provided" do
          @connection_impl.should_receive(:createSession).with("farkle").
            and_return(@session_impl)

          session = @connection.create_session :name => "farkle"

          session.session_impl.should == @session_impl
        end

        it "creates a transactional session when specified" do
          @connection_impl.should_receive(:createTransactionalSession).
            and_return(@session_impl)

          session = @connection.create_session :transactional => true

          session.session_impl.should == @session_impl
        end

        it "creates a named transactional session when specified" do
          @connection_impl.should_receive(:createTransactionalSession).
            with("farkle").
            and_return(@session_impl)

          session = @connection.create_session :transactional => true, :name => "farkle"

          session.session_impl.should == @session_impl
        end

        it "returns the authenticated username when connected" do
          @connection_impl.should_receive(:getAuthenticatedUsername).
            and_return("mcpierce")

          username = @connection.authenticated_username

          username.should == "mcpierce"
        end

      end

    end

  end

end
