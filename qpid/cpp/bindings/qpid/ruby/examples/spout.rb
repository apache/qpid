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
  :broker => "127.0.0.1",
  :address => "",
  :timeout => 0,
  :durable => false,
  :count => 1,
  :properties => {},
  :content => nil,
  :mapped => {}
}

opts = OptionParser.new do |opts|
  opts.banner = "Usage: spout.rb [OPTIONS] ADDRESS"

  opts.on("-h", "--help",
          "show this message") do |help|
    puts opts
    exit
  end

  opts.on("-b","--broker VALUE",
          "url of broker to connect to ") do |broker|
    options[:broker] = broker
  end

  opts.on("-t", "--timeout VALUE", Integer,
          "exit after the specified time") do |timeout|
    options[:timeout] = Qpid::Messaging::Duration.new timeout * 1000
  end

  opts.on("-d", "--durable",
          "make the message durable (def. #{options[:durable]})") do
    options[:durable] = true
  end

  opts.on("-c", "--count VALUE", Integer,
          "stop after count messages have been sent, zero disables") do |count|
    options[:count] = count
  end

  opts.on("-i", "--id VALUE",
          "use the supplied id instead of generating one") do |id|
    options[:id] = id
  end

  opts.on("--reply-to VALUE",
          "specify reply-to address") do |replyto|
    options[:replyto] = replyto
  end

  opts.on("-P", "--property VALUE",
          "specify message property") do |property|
    name  = property.split(/=/)[0]
    value = property.split(/=/)[1]
    options[:properties][name] = value
  end

  opts.on("-M", "--map VALUE",
          "specify entry for map content") do |mapped|
    name  = mapped.split(/=/)[0]
    value = mapped.split(/=/)[1]
    options[:mapped][name] = value
  end

  opts.on("--content VALUE",
          "specify textual content") do |content|
    options[:content] = content
  end

  opts.on("--connection-options VALUE",
          "connection options string in the form {name1:value1, name2:value2}") do |conopts|

    options[:connection_options] = conopts
  end
end

begin
  opts.parse!(ARGV)
rescue => error
  opts.parse(["-h"])
end

# now get the non-arg options
options[:address] = ARGV[0] unless ARGV[0].nil?

# process the connection options
unless options[:connection_options].nil?
  fields = options[:connection_options].gsub(/^\{(.*)\}$/, '\1')
  # remove any surrounding braces
  if /\{.*\}/ =~ fields
    fields = fields[1..-2]
  end
  # break up the options separated by commas
  keysvalues = {}
  fields.split(",").each do |field|
    if /.+:.+/ =~ field
      (key, value) = field.split(":")
      keysvalues[key] = value
    end
  end
  # now store the options
  options[:connection_options] = keysvalues
end

connection = Qpid::Messaging::Connection.new(:url => options[:broker],
                                             :options => options[:connection_options])
connection.open
session = connection.create_session
sender = session.create_sender options[:address]
message = Qpid::Messaging::Message.new

options[:properties].each_key {|key| message[key] = options[:properties][key]}

(1..options[:count]).each do |count|
  if !options[:mapped].keys.empty?
    message.content_object = options[:mapped]
  elsif options[:content]
    message.content_object = options[:content]
  end
  message.durable = options[:durable]
  message.content_object = options[:content] unless options[:content].nil?
  message.properties["spout-id"] = "#{count}"
  message.reply_to = options[:replyto] unless options[:replyto].nil? || options[:replyto].empty?
  sender.send message
end

# session.sync

connection.close

