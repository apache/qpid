# -*- encoding: utf-8 -*-
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
 
lib = File.expand_path('lib/', __FILE__)
$:.unshift lib unless $:.include?(lib)

# Generate the Swig wrapper
system "swig -ruby -c++ -I../../../include -I../../ -o ext/cqpid/cqpid.cpp ruby.i"

Gem::Specification.new do |s|
  s.name        = "qpid_messaging"
  s.version     = "0.22.0"
  s.platform    = Gem::Platform::RUBY
  s.authors     = "Apache Qpid Project"
  s.email       = "dev@qpid.apache.org"
  s.homepage    = "http://qpid.apache.org"
  s.summary     = "Qpid is an enterprise messaging framework."
  s.description = s.summary

  s.extensions   = "ext/cqpid/extconf.rb"
  s.files        = Dir["LICENSE",
                   "ChangeLog",
                   "README.rdoc",
                   "TODO",
                   "lib/**/*.rb",
                   "ext/**/*",
                   "examples/**/*.rb"
                ]
  s.require_path = 'lib'
end

