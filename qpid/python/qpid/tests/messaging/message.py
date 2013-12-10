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

from qpid.tests.messaging.implementation import *
from qpid.messaging.address import parse
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
    return Connection.establish(self.broker, **self.connection_options())

  def setup_session(self):
    return self.conn.session()

  def setup_sender(self):
    return self.ssn.sender(ECHO_Q)

  def setup_receiver(self):
    return self.ssn.receiver(ECHO_Q)

  def check(self, msg):
    self.snd.send(msg)
    echo = self.rcv.fetch(0)
    self.assertEcho(msg, echo)
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
              "key9": {"sub-key0": 3},
              "key10": True,
              "key11": False,
              "x-amqp-0-10.app-id": "test-app-id",
              "x-amqp-0-10.content-encoding": "test-content-encoding"}

  def testMapContent(self):
    self.check(Message(MessageEchoTests.TEST_MAP))

  def testListContent(self):
    self.check(Message([]))
    self.check(Message([1, 2, 3]))
    self.check(Message(["one", 2, 3.14, {"four": 4}]))

  def testProperties(self):
    msg = Message()
    msg.subject = "subject"
    msg.correlation_id = str(self.test_id)
    msg.durable = True
    msg.priority = 7
    msg.ttl = 60
    msg.properties = MessageEchoTests.TEST_MAP
    msg.reply_to = "reply-address"
    self.check(msg)

  def testApplicationProperties(self):
    msg = Message()
    msg.properties["a"] = u"A"
    msg.properties["b"] = 1
    msg.properties["c"] = ["x", 2]
    msg.properties["d"] = "D"
    #make sure deleting works as expected
    msg.properties["foo"] = "bar"
    del msg.properties["foo"]
    self.check(msg)

  def testContentTypeUnknown(self):
    msg = Message(content_type = "this-content-type-does-not-exist")
    self.check(msg)

  def testTextPlain(self):
    self.check(Message(content_type="text/plain", content="asdf"))

  def testTextPlainEmpty(self):
    self.check(Message(content_type="text/plain"))

  def check_rt(self, addr, expected=None):
    if expected is None:
      expected = addr
    msg = Message(reply_to=addr)
    self.snd.send(msg)
    echo = self.rcv.fetch(0)
    #reparse addresses and check individual parts as this avoids
    #failing due to differenecs in whitespace when running over
    #swigged client:
    (actual_name, actual_subject, actual_options) = parse(echo.reply_to)
    (expected_name, expected_subject, expected_options) = parse(expected)
    assert actual_name == expected_name, (actual_name, expected_name)
    assert actual_subject == expected_subject, (actual_subject, expected_subject)
    assert actual_options == expected_options, (actual_options, expected_options)
    self.ssn.acknowledge(echo)

  def testReplyTo(self):
    self.check_rt("name")

  def testReplyToQueue(self):
    self.check_rt("name; {node: {type: queue}}", "name")

  def testReplyToQueueSubject(self):
    self.check_rt("name/subject; {node: {type: queue}}", "name")

  def testReplyToTopic(self):
    self.check_rt("name; {node: {type: topic}}")

  def testReplyToTopicSubject(self):
    self.check_rt("name/subject; {node: {type: topic}}")

  def testBooleanEncoding(self):
    msg = Message({"true": True, "false": False})
    self.snd.send(msg)
    echo = self.rcv.fetch(0)
    self.assertEcho(msg, echo)
    t = echo.content["true"]
    f = echo.content["false"]
    assert isinstance(t, bool), t
    assert isinstance(f, bool), f

  def testExceptionRaisedMismatchedContentType(self):
    msg = Message(content_type="amqp/map", content="asdf")
    try:
      self.snd.send(msg)
      self.rcv.fetch(0)
      assert False, "Exception not raised on mismatched content/content_type"
    except Exception, e:
      pass

  def testRecoverAfterException(self):
    self.testExceptionRaisedMismatchedContentType()
    self.testTextPlain()
