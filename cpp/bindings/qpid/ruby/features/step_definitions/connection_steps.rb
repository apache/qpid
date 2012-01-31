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
