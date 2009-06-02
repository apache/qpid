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

# setup, usage, teardown, errors(sync), errors(async), stress, soak,
# boundary-conditions, config

import time
from qpid.tests import Test
from qpid.messaging import Connection, Disconnected, Empty, Message, uuid4
from Queue import Queue, Empty as QueueEmpty

class Base(Test):

  def setup_connection(self):
    return None

  def setup_session(self):
    return None

  def setup_sender(self):
    return None

  def setup_receiver(self):
    return None

  def setup(self):
    self.broker = self.config.broker
    self.conn = self.setup_connection()
    self.ssn = self.setup_session()
    self.snd = self.setup_sender()
    self.rcv = self.setup_receiver()

  def teardown(self):
    if self.conn is not None and self.conn.connected():
      self.conn.close()

  def ping(self, ssn):
    # send a message
    sender = ssn.sender("ping-queue")
    content = "ping[%s]" % uuid4()
    sender.send(content)
    receiver = ssn.receiver("ping-queue")
    msg = receiver.fetch(timeout=0)
    ssn.acknowledge()
    assert msg.content == content

  def drain(self, rcv, limit=None):
    msgs = []
    try:
      while limit is None or len(msgs) < limit:
        msgs.append(rcv.fetch(0))
    except Empty:
      pass
    return msgs

class SetupTests(Base):

  def testOpen(self):
    # XXX: need to flesh out URL support/syntax
    self.conn = Connection.open(self.broker.host, self.broker.port)
    self.ping(self.conn.session())

  def testConnect(self):
    # XXX: need to flesh out URL support/syntax
    self.conn = Connection(self.broker.host, self.broker.port)
    self.conn.connect()
    self.ping(self.conn.session())

class ConnectionTests(Base):

  def setup_connection(self):
    return Connection.open(self.broker.host, self.broker.port)

  def testSessionAnon(self):
    ssn1 = self.conn.session()
    ssn2 = self.conn.session()
    self.ping(ssn1)
    self.ping(ssn2)
    assert ssn1 is not ssn2

  def testSessionNamed(self):
    ssn1 = self.conn.session("one")
    ssn2 = self.conn.session("two")
    self.ping(ssn1)
    self.ping(ssn2)
    assert ssn1 is not ssn2
    assert ssn1 is self.conn.session("one")
    assert ssn2 is self.conn.session("two")

  def testDisconnect(self):
    ssn = self.conn.session()
    self.ping(ssn)
    self.conn.disconnect()
    import socket
    try:
      self.ping(ssn)
      assert False, "ping succeeded"
    except Disconnected:
      # this is the expected failure when pinging on a disconnected
      # connection
      pass
    self.conn.connect()
    self.ping(ssn)

  def testStart(self):
    ssn = self.conn.session()
    assert not ssn.started
    self.conn.start()
    assert ssn.started
    ssn2 = self.conn.session()
    assert ssn2.started

  def testStop(self):
    self.conn.start()
    ssn = self.conn.session()
    assert ssn.started
    self.conn.stop()
    assert not ssn.started
    ssn2 = self.conn.session()
    assert not ssn2.started

  def testClose(self):
    self.conn.close()
    assert not self.conn.connected()

class SessionTests(Base):

  def setup_connection(self):
    return Connection.open(self.broker.host, self.broker.port)

  def setup_session(self):
    return self.conn.session()

  def testSender(self):
    snd = self.ssn.sender("test-snd-queue")
    snd2 = self.ssn.sender(snd.target)
    assert snd is not snd2
    snd2.close()

    content = "testSender[%s]" % uuid4()
    snd.send(content)
    rcv = self.ssn.receiver(snd.target)
    msg = rcv.fetch(0)
    assert msg.content == content
    self.ssn.acknowledge(msg)

  def testReceiver(self):
    rcv = self.ssn.receiver("test-rcv-queue")
    rcv2 = self.ssn.receiver(rcv.source)
    assert rcv is not rcv2
    rcv2.close()

    content = "testReceiver[%s]" % uuid4()
    snd = self.ssn.sender(rcv.source)
    snd.send(content)
    msg = rcv.fetch(0)
    assert msg.content == content
    self.ssn.acknowledge(msg)

  def testStart(self):
    rcv = self.ssn.receiver("test-start-queue")
    assert not rcv.started
    self.ssn.start()
    assert rcv.started
    rcv = self.ssn.receiver("test-start-queue")
    assert rcv.started

  def testStop(self):
    self.ssn.start()
    rcv = self.ssn.receiver("test-stop-queue")
    assert rcv.started
    self.ssn.stop()
    assert not rcv.started
    rcv = self.ssn.receiver("test-stop-queue")
    assert not rcv.started

  # XXX, we need a convenient way to assert that required queues are
  # empty on setup, and possibly also to drain queues on teardown
  def testAcknowledge(self):
    # send a bunch of messages
    snd = self.ssn.sender("test-ack-queue")
    tid = "a"
    contents = ["testAcknowledge[%s, %s]" % (i, tid) for i in range(10)]
    for c in contents:
      snd.send(c)

    # drain the queue, verify the messages are there and then close
    # without acking
    rcv = self.ssn.receiver(snd.target)
    msgs = self.drain(rcv)
    assert contents == [m.content for m in msgs]
    self.ssn.close()

    # drain the queue again, verify that they are all the messages
    # were requeued, and ack this time before closing
    self.ssn = self.conn.session()
    rcv = self.ssn.receiver("test-ack-queue")
    msgs = self.drain(rcv)
    assert contents == [m.content for m in msgs]
    self.ssn.acknowledge()
    self.ssn.close()

    # drain the queue a final time and verify that the messages were
    # dequeued
    self.ssn = self.conn.session()
    rcv = self.ssn.receiver("test-ack-queue")
    msgs = self.drain(rcv)
    assert len(msgs) == 0

  def testClose(self):
    self.ssn.close()
    try:
      self.ping(self.ssn)
      assert False, "ping succeeded"
    except Disconnected:
      pass

class ReceiverTests(Base):

  def setup_connection(self):
    return Connection.open(self.broker.host, self.broker.port)

  def setup_session(self):
    return self.conn.session()

  def setup_sender(self):
    return self.ssn.sender("test-receiver-queue")

  def setup_receiver(self):
    return self.ssn.receiver("test-receiver-queue")

  def send(self, base, count = None):
    if count is None:
      content = "%s[%s]" % (base, uuid4())
    else:
      content = "%s[%s, %s]" % (base, count, uuid4())
    self.snd.send(content)
    return content

  def testListen(self):
    msgs = Queue()
    def listener(m):
      msgs.put(m)
      self.ssn.acknowledge(m)
    self.rcv.listen(listener)
    content = self.send("testListen")
    try:
      msg = msgs.get(timeout=3)
      assert False, "did not expect message: %s" % msg
    except QueueEmpty:
      pass
    self.rcv.start()
    msg = msgs.get(timeout=3)
    assert msg.content == content

  def testFetch(self):
    try:
      msg = self.rcv.fetch(0)
      assert False, "unexpected message: %s" % msg
    except Empty:
      pass
    try:
      start = time.time()
      msg = self.rcv.fetch(3)
      assert False, "unexpected message: %s" % msg
    except Empty:
      elapsed = time.time() - start
      assert elapsed >= 3

    one = self.send("testListen", 1)
    two = self.send("testListen", 2)
    three = self.send("testListen", 3)
    msg = self.rcv.fetch(0)
    assert msg.content == one
    msg = self.rcv.fetch(3)
    assert msg.content == two
    msg = self.rcv.fetch()
    assert msg.content == three
    self.ssn.acknowledge()

  def testStart(self):
    content = self.send("testStart")
    time.sleep(2)
    assert self.rcv.pending() == 0
    self.rcv.start()
    time.sleep(2)
    assert self.rcv.pending() == 1
    msg = self.rcv.fetch(0)
    assert msg.content == content
    assert self.rcv.pending() == 0
    self.ssn.acknowledge()

  def testStop(self):
    self.rcv.start()
    one = self.send("testStop", 1)
    time.sleep(2)
    assert self.rcv.pending() == 1
    msg = self.rcv.fetch(0)
    assert msg.content == one

    self.rcv.stop()

    two = self.send("testStop", 2)
    time.sleep(2)
    assert self.rcv.pending() == 0
    msg = self.rcv.fetch(0)
    assert msg.content == two

    self.ssn.acknowledge()

  def testPending(self):
    self.rcv.start()

    assert self.rcv.pending() == 0

    for i in range(3):
      self.send("testPending", i)
    time.sleep(2)

    assert self.rcv.pending() == 3

    for i in range(3, 10):
      self.send("testPending", i)
    time.sleep(2)

    assert self.rcv.pending() == 10

    self.drain(self.rcv, limit=3)

    assert self.rcv.pending() == 7

    self.drain(self.rcv)

    assert self.rcv.pending() == 0

    self.ssn.acknowledge()

  # XXX: need testClose

class MessageTests(Base):

  def testCreateString(self):
    m = Message("string")
    assert m.content == "string"
    assert m.content_type is None

  def testCreateUnicode(self):
    m = Message(u"unicode")
    assert m.content == u"unicode"
    assert m.content_type == "text/plain; charset=utf8"

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

class MessageEchoTests(Base):

  def setup_connection(self):
    return Connection.open(self.broker.host, self.broker.port)

  def setup_session(self):
    return self.conn.session()

  def setup_sender(self):
    return self.ssn.sender("test-message-echo-queue")

  def setup_receiver(self):
    return self.ssn.receiver("test-message-echo-queue")

  def check(self, msg):
    self.snd.send(msg)
    echo = self.rcv.fetch(0)

    assert msg.id == echo.id
    assert msg.subject == echo.subject
    assert msg.user_id == echo.user_id
    assert msg.to == echo.to
    assert msg.reply_to == echo.reply_to
    assert msg.correlation_id == echo.correlation_id
    assert msg.properties == echo.properties
    assert msg.content_type == echo.content_type
    assert msg.content == echo.content

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
              "key8": []}

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
    msg.correlation_id = str(uuid4())
    msg.properties = MessageEchoTests.TEST_MAP
    msg.reply_to = "reply-address"
    self.check(msg)

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
