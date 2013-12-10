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

    describe "encoding" do
    end

    describe "decoding" do

      before(:each) do
        @message = Qpid::Messaging::Message.new
      end

      it "can decode a message's text content" do
        @message.content = "This is an unencoded message."

        content = Qpid::Messaging.decode @message

        content.should == "This is an unencoded message."
      end

      it "can decode a message's list content" do
        @message.content = ["this", "that"]

        content = Qpid::Messaging.decode @message

        content.should == ["this", "that"]
      end

      it "can decode a message's map content" do
        @message.content = {"this" => "that"}

        content = Qpid::Messaging.decode @message

        content.should == {"this" => "that"}
      end

    end

  end

end
