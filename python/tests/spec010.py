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
from qpid.codec010 import Codec, StringCodec
from qpid.testlib import testrunner
from qpid.datatypes import Struct

class SpecTest(TestCase):

  def setUp(self):
    self.spec = load(testrunner.get_spec_file("amqp.0-10.xml"))

  def testSessionHeader(self):
    hdr = self.spec["session.header"]
    sc = StringCodec(self.spec)
    hdr.encode(sc, Struct({"sync": True}))
    assert sc.encoded == "\x01\x01"

    sc = StringCodec(self.spec)
    hdr.encode(sc, Struct({"sync": False}))
    assert sc.encoded == "\x01\x00"

  def encdec(self, type, value):
    sc = StringCodec(self.spec)
    type.encode(sc, value)
    decoded = type.decode(sc)
    return decoded

  def testMessageProperties(self):
    props = Struct({"content_length": 0xDEADBEEF,
                    "reply_to":
                      Struct({"exchange": "the exchange name", "routing_key": "the routing key"})})
    dec = self.encdec(self.spec["message.message_properties"], props)
    assert props.content_length == dec.content_length
    assert props.reply_to.exchange == dec.reply_to.exchange
    assert props.reply_to.routing_key == dec.reply_to.routing_key

  def testMessageSubscribe(self):
    cmd = Struct({"exclusive": True, "destination": "this is a test"})
    dec = self.encdec(self.spec["message.subscribe"], cmd)
    assert cmd.exclusive == dec.exclusive
    assert cmd.destination == dec.destination
