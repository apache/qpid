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

$:.unshift File.join(File.dirname(__FILE__), "..", "lib")

require 'qpid_messaging'
require 'optparse'

options = {
  :broker => "localhost",
  :timeout => Qpid::Messaging::Duration::IMMEDIATE,
  :count => 1,
  :forever => false,
  :connection_options => ""
}

opts = OptionParser.new do |opts|
  opts.banner = "Usage: drain.rb [OPTIONS] ADDRESS"

  opts.separator ""
  opts.separator "Drains messages from the specified address"
  opts.separator ""

  opts.on("-h", "--help",
          "show this message") do
    puts opts
    exit
  end

  opts.on("-b", "--broker VALUE",
          "url of broker to connect to") do |broker|
    options[:broker] = broker
  end

  opts.on("-t", "--timeout VALUE", Integer,
          "timeout in seconds to wait before exiting") do |timeout|
    options[:timeout] = Qpid::Messaging::Duration.new timeout * 1000
  end

  opts.on("-f", "--forever",
          "ignore timeout and wait forever") do
    options[:forever] = true
  end

  opts.on("--connection-options VALUE",
          "connection options string in the form {name1:value,name2:value2}") do |conopts|
    options[:connection_options] = conopts
  end

  opts.on("-c", "--count VALUE", Integer,
          "number of messages to read before exiting") do |count|
    options[:count] = count
  end
end

opts.parse!(ARGV)

options[:address] = ARGV[0] || ""

connection = Qpid::Messaging::Connection.new :url => options[:broker], :options => options[:connection_options]
connection.open

def render_map map
  print "{"
  map.keys.sort.each_with_index {|key,index| print "#{index > 0 ? ', ' : ''}#{key}:#{map[key]}"}
  print "}"
end

begin
  session = connection.create_session
  receiver = session.create_receiver options[:address]
  done = false
  count = 0
  options[:timeout] = Qpid::Messaging::Duration::FOREVER if options[:forever]

  while !done && (count < options[:count])
    message = receiver.fetch(options[:timeout])
    print "Message(properties="
    render_map message.properties
    print ", content_object="
    if message.content_object == "amqp/map"
      print "'#{render_map message.content_object}')"
    else
      print "'#{message.content_object}'"
    end
    print ")\n"
    session.acknowledge message
    count += 1
  end
rescue Exception => error
  puts "Exception: #{error.to_s}"
end

connection.close

