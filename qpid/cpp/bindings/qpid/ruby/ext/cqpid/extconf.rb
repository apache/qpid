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

# To create the Makefile then you need to specify the location
# of the Qpid shared libraries using the commandline:
#
#  $ ruby extconf.rb --with-qpid-lib=[path to libraries]
#

require 'mkmf'

(rver, rrev, rmin) = RUBY_VERSION.split('.')

old_ruby = (rver == "1" && rrev < "9") # pre-1.9

# Setup the build environment.
if old_ruby
  $CFLAGS = "-fPIC -fno-inline -x c++ -lstdc++"
else
  $CFLAGS = "-fPIC -fno-inline"
end

REQUIRED_LIBRARIES = [
                      'stdc++',
                      'qpidclient',
                      'qpidcommon',
                      'qpidmessaging',
                      'qpidtypes'
                     ]

REQUIRED_HEADERS = [
                    'qpid/messaging/Address.h',
                    'qpid/messaging/Connection.h',
                    'qpid/messaging/Duration.h',
                    'qpid/messaging/exceptions.h',
                    'qpid/messaging/FailoverUpdates.h',
                    'qpid/messaging/Handle.h',
                    'qpid/messaging/ImportExport.h',
                    'qpid/messaging/Message.h',
                    'qpid/messaging/Receiver.h',
                    'qpid/messaging/Sender.h',
                    'qpid/messaging/Session.h'
                   ]

dir_config('qpid')

def abort_build filetype, filename
  abort "Missing required #{filetype}: #{filename}"
end

def require_library lib
  abort_build "library", lib unless have_library lib
end

def require_header header
  abort_build "header", header unless have_header header
end

have_library('stdc++')

REQUIRED_LIBRARIES.each {|library| require_library library}

REQUIRED_HEADERS.each {|header| require_header header} if old_ruby

create_makefile('cqpid')

