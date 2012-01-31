Given /^an Address with the name "([^"]*)" and subject "([^"]*)" and option "([^"]*)" set to "([^"]*)"$/ do |name, subject, key, value|
  options = Hash.new
  options["#{key}"] = "#{value}"
  @address = Qpid::Messaging::Address.new "#{name}", "#{subject}", options
end
