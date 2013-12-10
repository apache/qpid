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

Given /^an existing receiver for "([^"]*)"$/ do |address|
  steps %Q{
    Given an open session
    Then creating a receiver with "#{address}" succeeds
  }
end

Given /^the receiver has no pending messages$/ do
  available = @receiver.available
  available.should == 0
end

Then /^getting the next message raises an error$/ do
  lambda {
    @message = @receiver.get Qpid::Messaging::Duration::IMMEDIATE
  }.should raise_error
end

Given /^a sender and receiver for "([^"]*)"$/ do |address|
  steps %Q{
    Given an open session
    Then creating a sender with "#{address}" succeeds
    Then creating a receiver with "#{address}" succeeds
  }
end

Then /^the receiver should receive a message with "([^"]*)"$/ do |content|
  @message = @receiver.fetch Qpid::Messaging::Duration::IMMEDIATE

  @message.should_not be_nil
  @message.content.should == "#{content}"
end

Given /^the receiver has a capacity of (\d+)$/ do |capacity|
  @receiver.capacity = capacity.to_i
end

Then /^the receiver should have (\d+) message available$/ do |available|
  # TODO we shouldn't need to sleep a second in order to have this update
  sleep 1
  @receiver.available.should == available.to_i
end

Given /^given a sender for "([^"]*)"$/ do |address|
  @sender = @session.create_sender "#{address}"
end

Given /^given a receiver for "([^"]*)"$/ do |address|
  @receiver = @session.create_receiver "#{address}"
end
