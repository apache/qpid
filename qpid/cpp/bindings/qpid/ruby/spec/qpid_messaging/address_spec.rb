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

    describe Address do

      before(:each) do
        @address = Qpid::Messaging::Address.new "my-name/my-subject;{create:always}"
      end

      it "stores the name, subject and options when created" do
        name    = @address.name
        subject = @address.subject
        create  = @address.options["create"]

        name.should    == "my-name"
        subject.should == "my-subject"
        create.should  == "always"
      end

      it "can update the name" do
        @address.name = "new-name"

        name = @address.name

        name.should == "new-name"
      end

      it "can update the subject" do
        @address.subject = "new-subject"

        subject = @address.subject

        subject.should == "new-subject"
      end

      it "can update the type" do
        @address.address_type = "routed"

        type = @address.address_type

        type.should == "routed"
      end

      it "can update the options" do
        @address.options[:create] = :never

        create = @address.options["create"]

        create.should == "always"
      end

      it "can return a string representation" do
        address = Qpid::Messaging::Address.new "foo/bar:{create:always,link:durable}"
        result = address.to_s

        result.should =~ /foo\/bar/
        result.should =~ /create:always/
        result.should =~ /link:durable/
      end

    end

  end

end
