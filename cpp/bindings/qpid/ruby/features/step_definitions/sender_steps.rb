Given /^the message "([^"]*)" is sent$/ do |content|
  @sender.send Qpid::Messaging::Message.new :content => "#{content}"
end

Then /^sending the message "([^"]*)" should raise an error$/ do |content|
  lambda {
    steps %Q{
      Then sending the message "#{content}" succeeds
    }
  }.should raise_error
end

Then /^sending the message "([^"]*)" succeeds$/ do |content|
  @sender.send Qpid::Messaging::Message.new :content => "#{content}"
end
