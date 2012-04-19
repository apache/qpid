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

Given /^a closed session/ do
  steps %Q{
    Given an open connection
    Then creating a session works
  }
  @connection.close
end

Then /^creating a sender with "([^"]*)" raises an exception$/ do |address|
  lambda {
    steps %Q{
      @sender = @session.create_sender "#{address}"
    }
  }.should raise_error
end

Then /^creating a receiver with "([^"]*)" raises an exception$/ do |address|
  lambda {
    steps %Q{
      @sender = @session.create_sender "#{address}"
    }
  }.should raise_error
end

Given /^an open session with a closed connection$/ do
  steps %Q{
    Given an open connection
    Then creating a session works
  }
  @session.connection.close
end

Given /^an open session$/ do
  steps %Q{
    Given an open connection
    Then creating a session works
  }
end

Given /^the session is closed$/ do
  @session.close
end

Then /^creating a sender with "([^"]*)" succeeds$/ do |address|
  @sender = @session.create_sender "#{address}"
  @sender.should_not be_nil
end

Then /^creating a sender with an Address succeeds$/ do
  @sender = @session.create_receiver @address
  @sender.should_not be_nil
end

Then /^creating a receiver for a nonexistent queue raises an exception$/ do
  lambda {
    steps %Q{
      Then creating a receiver with "queue-#{Time.new.to_i}" succeeds
    }
  }.should raise_error
end

Then /^creating a receiver with "([^"]*)" succeeds$/ do |address|
  @receiver = @session.create_receiver "#{address}"
  @receiver.should_not be_nil
end

Then /^creating a receiver with an Address succeeds$/ do
  @receiver = @session.create_receiver @address
  @receiver.should_not be_nil
end

Then /^closing the session does not raise an error$/ do
  lambda {
    @session.close
  }.should_not raise_error
end

Then /^the connection for the session is in the (open|closed) state$/ do |state|
  @session.connection.open?.should == false if state == "closed"
end
