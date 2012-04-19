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

# close all connections
After do
  @connection.close if @connection
end

Given /^a new connection$/ do
  @connection = Qpid::Messaging::Connection.new unless @connection
end

Given /^an open connection$/ do
  steps %Q{
    Given a new connection
  }
  @connection.open
end

Given /^a closed connection$/ do
  steps %Q{
    Given a new connection
  }
  @connection.close if @connection.open?
end

Then /^the connection is in the (open|closed) state$/ do |state|
  @connection.open?.should == false if state == "closed"
  @connection.open?.should == true  if state == "open"
end

Given /^the connection is opened$/ do
  @connection.open
end

Given /^the connection is closed$/ do
  @connection.close
end

Then /^creating a session raises an exception$/ do
  lambda {
    @session = @connection.create_session
  }.should raise_error
end

Then /^creating a session works$/ do
  steps %Q{
    Given a session exists with the name "nameless"
  }
  @session.should_not be_nil
end

Given /^an existing session named "([^"]*)"$/ do |name|
  steps %Q{
    Given an open connection
    And a session exists with the name "#{name}"
  }
end

Given /^a session exists with the name "([^"]*)"$/ do |name|
  @session = @connection.create_session :name => "#{name}"
end

Then /^the session can be retrieved by the name "([^"]*)"$/ do |name|
  session = @connection.session "#{name}"
  session.should_not be_nil
end

Then /^calling close does not raise an exception$/ do
  lambda {
    @connection.close
  }.should_not raise_error
end

Then /^the authenticated username should be "([^"]*)"$/ do |username|
  @connection.authenticated_username.should == "#{username}"
end
