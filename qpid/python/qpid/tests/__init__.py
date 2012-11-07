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

class Test:

  def __init__(self, name):
    self.name = name

  def configure(self, config):
    self.config = config

# API Tests
import qpid.tests.framing
import qpid.tests.mimetype
import qpid.tests.messaging

# Legacy Tests
import qpid.tests.codec
import qpid.tests.queue
import qpid.tests.datatypes
import qpid.tests.connection
import qpid.tests.spec010
import qpid.tests.codec010
import qpid.tests.util

class TestTestsXXX(Test):

  def testFoo(self):
    print "this test has output"

  def testBar(self):
    print "this test "*8
    print "has"*10
    print "a"*75
    print "lot of"*10
    print "output"*10

  def testQux(self):
    import sys
    sys.stdout.write("this test has output with no newline")

  def testQuxFail(self):
    import sys
    sys.stdout.write("this test has output with no newline")
    fdsa
