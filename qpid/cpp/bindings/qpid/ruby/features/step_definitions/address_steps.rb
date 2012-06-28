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

Given /^an Address with the name "([^"]*)" and subject "([^"]*)" and option "([^"]*)" set to "([^"]*)"$/ do |name, subject, key, value|
  options = Hash.new
  options["#{key}"] = "#{value}"
  @address = Qpid::Messaging::Address.new "#{name}", "#{subject}", options
end

Given /^an Address with the name "([^"]*)" and subject "([^"]*)" and option "([^"]*)" set to "([^"]*)" and "([^"]*)" set to "([^"]*)"$/ do |name, subject, key1, value1, key2, value2|
  options = Hash.new
  options["#{key1}"] = "#{value1}"
  options["#{key2}"] = "#{value2}"
  @address = Qpid::Messaging::Address.new "#{name}", "#{subject}", options
end
