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
from qpid.harness import Skipped
from qpid.messaging import Connection, ConnectError, Disconnected, Empty, Message, UNLIMITED, uuid4
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
    self.test_id = uuid4()
    self.broker = self.config.broker
    try:
      self.conn = self.setup_connection()
    except ConnectError, e:
      raise Skipped(e)
    self.ssn = self.setup_session()
    self.snd = self.setup_sender()
    self.rcv = self.setup_receiver()

  def teardown(self):
    if self.conn is not None and self.conn.connected():
      self.conn.close()

  def content(self, base, count = None):
    if count is None:
      return "%s[%s]" % (base, self.test_id)
    else:
      return "%s[%s, %s]" % (base, count, self.test_id)

  def ping(self, ssn):
    # send a message
    sender = ssn.sender("ping-queue")
    content = self.content("ping")
    sender.send(content)
    receiver = ssn.receiver("ping-queue")
    msg = receiver.fetch(0)
    ssn.acknowledge()
    assert msg.content == content, "expected %r, got %r" % (content, msg.content)

  def drain(self, rcv, limit=None):
    contents = []
    try:
      while limit is None or len(contents) < limit:
        contents.append(rcv.fetch(0).content)
    except Empty:
      pass
    return contents

  def assertEmpty(self, rcv):
    contents = self.drain(rcv)
    assert len(contents) == 0, "%s is supposed to be empty: %s" % (rcv, contents)

  def assertPending(self, rcv, expected):
    p = rcv.pending()
    assert p == expected, "expected %s, got %s" % (expected, p)

  def sleep(self):
    time.sleep(self.delay())

  def delay(self):
    return float(self.config.defines.get("delay", "2"))

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

  def testConnectError(self):
    try:
      self.conn = Connection.open("localhost", 0)
      assert False, "connect succeeded"
    except ConnectError, e:
      # XXX: should verify that e includes appropriate diagnostic info
      pass

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

    content = self.content("testSender")
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

    content = self.content("testReceiver")
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
    assert contents == self.drain(rcv)
    self.ssn.close()

    # drain the queue again, verify that they are all the messages
    # were requeued, and ack this time before closing
    self.ssn = self.conn.session()
    rcv = self.ssn.receiver("test-ack-queue")
    drained = self.drain(rcv)
    assert contents == drained, "expected %s, got %s" % (contents, drained)
    self.ssn.acknowledge()
    self.ssn.close()

    # drain the queue a final time and verify that the messages were
    # dequeued
    self.ssn = self.conn.session()
    rcv = self.ssn.receiver("test-ack-queue")
    self.assertEmpty(rcv)

  def send(self, ssn, queue, base, count=1):
    snd = ssn.sender(queue)
    contents = []
    for i in range(count):
      c = self.content(base, i)
      snd.send(c)
      contents.append(c)
    snd.close()
    return contents

  def txTest(self, commit):
    txssn = self.conn.session(transactional=True)
    contents = self.send(self.ssn, "test-tx-queue", "txTest", 3)
    txrcv = txssn.receiver("test-tx-queue")
    txsnd = txssn.sender("test-tx-queue-copy")
    rcv = self.ssn.receiver(txrcv.source)
    copy_rcv = self.ssn.receiver(txsnd.target)
    self.assertEmpty(copy_rcv)
    for i in range(3):
      m = txrcv.fetch(0)
      txsnd.send(m)
      self.assertEmpty(copy_rcv)
    txssn.acknowledge()
    if commit:
      txssn.commit()
      self.assertEmpty(rcv)
      assert contents == self.drain(copy_rcv)
    else:
      txssn.rollback()
      assert contents == self.drain(rcv)
      self.assertEmpty(copy_rcv)
    self.ssn.acknowledge()

  def testCommit(self):
    self.txTest(True)

  def testRollback(self):
    self.txTest(False)

  def txTestSend(self, commit):
    txssn = self.conn.session(transactional=True)
    contents = self.send(txssn, "test-tx-send-queue", "txTestSend", 3)
    rcv = self.ssn.receiver("test-tx-send-queue")
    self.assertEmpty(rcv)

    if commit:
      txssn.commit()
      assert contents == self.drain(rcv)
      self.ssn.acknowledge()
    else:
      txssn.rollback()
      self.assertEmpty(rcv)
      txssn.commit()
      self.assertEmpty(rcv)

  def testCommitSend(self):
    self.txTestSend(True)

  def testRollbackSend(self):
    self.txTestSend(False)

  def txTestAck(self, commit):
    txssn = self.conn.session(transactional=True)
    txrcv = txssn.receiver("test-tx-ack-queue")
    self.assertEmpty(txrcv)
    contents = self.send(self.ssn, "test-tx-ack-queue", "txTestAck", 3)
    assert contents == self.drain(txrcv)

    if commit:
      txssn.acknowledge()
    else:
      txssn.rollback()
      drained = self.drain(txrcv)
      assert contents == drained, "expected %s, got %s" % (contents, drained)
      txssn.acknowledge()
      txssn.rollback()
      assert contents == self.drain(txrcv)
      txssn.commit() # commit without ack
      self.assertEmpty(txrcv)

    txssn.close()

    txssn = self.conn.session(transactional=True)
    txrcv = txssn.receiver("test-tx-ack-queue")
    assert contents == self.drain(txrcv)
    txssn.acknowledge()
    txssn.commit()
    rcv = self.ssn.receiver("test-tx-ack-queue")
    self.assertEmpty(rcv)
    txssn.close()
    self.assertEmpty(rcv)

  def testCommitAck(self):
    self.txTestAck(True)

  def testRollbackAck(self):
    self.txTestAck(False)

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
    content = self.content(base, count)
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
      msg = msgs.get(timeout=self.delay())
      assert False, "did not expect message: %s" % msg
    except QueueEmpty:
      pass
    self.rcv.start()
    msg = msgs.get(timeout=self.delay())
    assert msg.content == content

  def testFetch(self):
    try:
      msg = self.rcv.fetch(0)
      assert False, "unexpected message: %s" % msg
    except Empty:
      pass
    try:
      start = time.time()
      msg = self.rcv.fetch(self.delay())
      assert False, "unexpected message: %s" % msg
    except Empty:
      elapsed = time.time() - start
      assert elapsed >= self.delay()

    one = self.send("testFetch", 1)
    two = self.send("testFetch", 2)
    three = self.send("testFetch", 3)
    msg = self.rcv.fetch(0)
    assert msg.content == one
    msg = self.rcv.fetch(self.delay())
    assert msg.content == two
    msg = self.rcv.fetch()
    assert msg.content == three
    self.ssn.acknowledge()

  def testStart(self):
    content = self.send("testStart")
    self.sleep()
    assert self.rcv.pending() == 0
    self.rcv.start()
    self.sleep()
    assert self.rcv.pending() == 1
    msg = self.rcv.fetch(0)
    assert msg.content == content
    assert self.rcv.pending() == 0
    self.ssn.acknowledge()

  def testStop(self):
    self.rcv.start()
    one = self.send("testStop", 1)
    self.sleep()
    assert self.rcv.pending() == 1
    msg = self.rcv.fetch(0)
    assert msg.content == one

    self.rcv.stop()

    two = self.send("testStop", 2)
    self.sleep()
    assert self.rcv.pending() == 0
    msg = self.rcv.fetch(0)
    assert msg.content == two

    self.ssn.acknowledge()

  def testPending(self):
    self.rcv.start()
    assert self.rcv.pending() == 0

    for i in range(3):
      self.send("testPending", i)
    self.sleep()
    assert self.rcv.pending() == 3

    for i in range(3, 10):
      self.send("testPending", i)
    self.sleep()
    assert self.rcv.pending() == 10

    self.drain(self.rcv, limit=3)
    assert self.rcv.pending() == 7

    self.drain(self.rcv)
    assert self.rcv.pending() == 0

    self.ssn.acknowledge()

  def testCapacity(self):
    self.rcv.capacity = 5
    self.rcv.start()
    self.assertPending(self.rcv, 0)

    for i in range(15):
      self.send("testCapacity", i)
    self.sleep()
    self.assertPending(self.rcv, 5)

    self.drain(self.rcv, limit = 5)
    self.sleep()
    self.assertPending(self.rcv, 5)

    drained = self.drain(self.rcv)
    assert len(drained) == 10
    self.assertPending(self.rcv, 0)

    self.ssn.acknowledge()

  def testCapacityUNLIMITED(self):
    self.rcv.capacity = UNLIMITED
    self.rcv.start()
    self.assertPending(self.rcv, 0)

    for i in range(10):
      self.send("testCapacityUNLIMITED", i)
    self.sleep()
    self.assertPending(self.rcv, 10)

    self.drain(self.rcv)
    self.assertPending(self.rcv, 0)

    self.ssn.acknowledge()

  # XXX: need testClose

class SenderTests(Base):

  def setup_connection(self):
    return Connection.open(self.broker.host, self.broker.port)

  def setup_session(self):
    return self.conn.session()

  def setup_sender(self):
    return self.ssn.sender("test-sender-queue")

  def setup_receiver(self):
    return self.ssn.receiver("test-sender-queue")

  def checkContent(self, content):
    self.snd.send(content)
    msg = self.rcv.fetch(0)
    assert msg.content == content

    out = Message(content)
    self.snd.send(out)
    echo = self.rcv.fetch(0)
    assert out.content == echo.content
    assert echo.content == msg.content
    self.ssn.acknowledge()

  def testSendString(self):
    self.checkContent(self.content("testSendString"))

  def testSendList(self):
    self.checkContent(["testSendList", 1, 3.14, self.test_id])

  def testSendMap(self):
    self.checkContent({"testSendMap": self.test_id, "pie": "blueberry", "pi": 3.14})

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
