#!/usr/bin/ruby

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

require 'qmf'
require 'socket'

class App < Qmf::ConsoleHandler

  def main
    @settings = Qmf::ConnectionSettings.new
    @settings.set_attr("host", ARGV[0]) if ARGV.size > 0
    @settings.set_attr("port", ARGV[1].to_i) if ARGV.size > 1
    @connection = Qmf::Connection.new(@settings)
    @qmf = Qmf::Console.new

    @broker = @qmf.add_connection(@connection)
    @broker.waitForStable

    packages = @qmf.get_packages
    puts "----- Packages -----"
    packages.each do |p|
      puts p
      puts "    ----- Object Classes -----"
      classes = @qmf.get_classes(p)
      classes.each do |c|
        puts "    #{c.name}"
      end
      puts "    ----- Event Classes -----"
      classes = @qmf.get_classes(p, Qmf::CLASS_EVENT)
      classes.each do |c|
        puts "    #{c.name}"
      end
    end
    puts "-----"

    sleep
  end
end

app = App.new
app.main


