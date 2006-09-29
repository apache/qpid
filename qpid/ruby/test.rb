#
# Copyright (c) 2006 The Apache Software Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

require "qpid/client"
require "qpid/spec"

def die(msg)
  puts msg
  exit(1)
end

specfile = $*[0]
die("usage: test.rb <spec file>") if specfile.nil?

c = Qpid::Client.new("0.0.0.0", 5672, Spec.load($*[0]))
c.start({"LOGIN" => "guest", "PASSWORD" => "guest"})
ch = c.channel(1)
p ch.channel_open()
p ch.queue_declare()
