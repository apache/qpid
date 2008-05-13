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

from unittest import TestCase
from qpid.spec010 import load
from qpid.codec010 import StringCodec
from qpid.testlib import testrunner

class CodecTest(TestCase):

  def setUp(self):
    self.spec = load(testrunner.get_spec_file("amqp.0-10.xml"))

  def check(self, type, value):
    t = self.spec[type]
    sc = StringCodec(self.spec)
    t.encode(sc, value)
    decoded = t.decode(sc)
    assert decoded == value, "%s, %s" % (decoded, value)

  def testMapString(self):
    self.check("map", {"string": "this is a test"})

  def testMapInt(self):
    self.check("map", {"int": 3})

  def testMapLong(self):
    self.check("map", {"long": 2**32})

  def testMapNone(self):
    self.check("map", {"none": None})

  def testMapNested(self):
    self.check("map", {"map": {"string": "nested test"}})

  def testMapAll(self):
    self.check("map", {"string": "this is a test",
                       "int": 3,
                       "long": 2**32,
                       "none": None,
                       "map": {"string": "nested map"}})
