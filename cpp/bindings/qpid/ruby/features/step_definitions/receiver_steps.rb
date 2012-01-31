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
