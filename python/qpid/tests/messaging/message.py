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

from qpid.messaging import *
from qpid.tests.messaging import Base

class MessageTests(Base):

  def testCreateString(self):
    m = Message("string")
    assert m.content == "string"
    assert m.content_type is None

  def testCreateUnicode(self):
    m = Message(u"unicode")
    assert m.content == u"unicode"
    assert m.content_type == "text/plain"

  def testCreateMap(self):
    m = Message({})
    assert m.content == {}
    assert m.content_type == "amqp/map"

  def testCreateList(self):
    m = Message([])
    assert m.content == []
    assert m.content_type == "amqp/list"

  def testContentTypeOverride(self):
    m = Message()
    m.content_type = "text/html; charset=utf8"
    m.content = u"<html/>"
    assert m.content_type == "text/html; charset=utf8"

ECHO_Q = 'test-message-echo-queue; {create: always, delete: always}'

class MessageEchoTests(Base):

  def setup_connection(self):
    return Connection.open(self.broker.host, self.broker.port,
                           reconnect=self.reconnect())

  def setup_session(self):
    return self.conn.session()

  def setup_sender(self):
    return self.ssn.sender(ECHO_Q)

  def setup_receiver(self):
    return self.ssn.receiver(ECHO_Q)

  def check(self, msg):
    self.snd.send(msg)
    echo = self.rcv.fetch(0)

    assert msg.id == echo.id
    assert msg.subject == echo.subject
    assert msg.user_id == echo.user_id
    assert msg.to == echo.to
    assert msg.reply_to == echo.reply_to
    assert msg.correlation_id == echo.correlation_id
    assert msg.durable == echo.durable
    assert msg.priority == echo.priority
    if msg.ttl is None:
      assert echo.ttl is None
    else:
      assert msg.ttl >= echo.ttl
    assert msg.properties == echo.properties
    assert msg.content_type == echo.content_type
    assert msg.content == echo.content, "%s, %s" % (msg, echo)

    self.ssn.acknowledge(echo)

  def testStringContent(self):
    self.check(Message("string"))

  def testUnicodeContent(self):
    self.check(Message(u"unicode"))


  TEST_MAP = {"key1": "string",
              "key2": u"unicode",
              "key3": 3,
              "key4": -3,
              "key5": 3.14,
              "key6": -3.14,
              "key7": ["one", 2, 3.14],
              "key8": [],
              "key9": {"sub-key0": 3}}

  def testMapContent(self):
    self.check(Message(MessageEchoTests.TEST_MAP))

  def testListContent(self):
    self.check(Message([]))
    self.check(Message([1, 2, 3]))
    self.check(Message(["one", 2, 3.14, {"four": 4}]))

  def testProperties(self):
    msg = Message()
    msg.to = "to-address"
    msg.subject = "subject"
    msg.correlation_id = str(self.test_id)
    msg.durable = True
    msg.priority = 7
    msg.ttl = 60
    msg.properties = MessageEchoTests.TEST_MAP
    msg.reply_to = "reply-address"
    self.check(msg)
