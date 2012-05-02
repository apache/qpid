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
