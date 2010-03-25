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

import errno, os, time
from qpid import compat
from qpid.messaging import *
from qpid.tests.messaging import Base

class SetupTests(Base):

  def testOpen(self):
    # XXX: need to flesh out URL support/syntax
    self.conn = Connection.open(self.broker.host, self.broker.port,
                                reconnect=self.reconnect())
    self.ping(self.conn.session())

  def testConnect(self):
    # XXX: need to flesh out URL support/syntax
    self.conn = Connection(self.broker.host, self.broker.port,
                           reconnect=self.reconnect())
    self.conn.connect()
    self.ping(self.conn.session())

  def testConnectError(self):
    try:
      self.conn = Connection.open("localhost", 0)
      assert False, "connect succeeded"
    except ConnectError, e:
      # XXX: should verify that e includes appropriate diagnostic info
      pass

  def use_fds(self):
    fds = []
    try:
      while True:
        fds.append(os.open(os.devnull, os.O_RDONLY))
    except OSError, e:
      if e.errno != errno.EMFILE:
        raise e
      else:
        return fds

  def testOpenCloseResourceLeaks(self):
    fds = self.use_fds()
    try:
      for i in range(32):
        if fds: os.close(fds.pop())
      for i in xrange(64):
        conn = Connection.open(self.broker.host, self.broker.port)
        conn.close()
    finally:
      while fds:
        os.close(fds.pop())

class ConnectionTests(Base):

  def setup_connection(self):
    return Connection.open(self.broker.host, self.broker.port,
                           reconnect=self.reconnect())

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

  def testClose(self):
    self.conn.close()
    assert not self.conn.connected()

ACK_QC = 'test-ack-queue; {create: always}'
ACK_QD = 'test-ack-queue; {delete: always}'

class SessionTests(Base):

  def setup_connection(self):
    return Connection.open(self.broker.host, self.broker.port,
                           reconnect=self.reconnect())

  def setup_session(self):
    return self.conn.session()

  def testSender(self):
    snd = self.ssn.sender('test-snd-queue; {create: sender, delete: receiver}',
                          durable=self.durable())
    snd2 = self.ssn.sender(snd.target, durable=self.durable())
    assert snd is not snd2
    snd2.close()

    content = self.content("testSender")
    snd.send(content)
    rcv = self.ssn.receiver(snd.target)
    msg = rcv.fetch(0)
    assert msg.content == content
    self.ssn.acknowledge(msg)

  def testReceiver(self):
    rcv = self.ssn.receiver('test-rcv-queue; {create: always}')
    rcv2 = self.ssn.receiver(rcv.source)
    assert rcv is not rcv2
    rcv2.close()

    content = self.content("testReceiver")
    snd = self.ssn.sender(rcv.source, durable=self.durable())
    snd.send(content)
    msg = rcv.fetch(0)
    assert msg.content == content
    self.ssn.acknowledge(msg)
    snd2 = self.ssn.receiver('test-rcv-queue; {delete: always}')

  def testDisconnectedReceiver(self):
    self.conn.disconnect()
    rcv = self.ssn.receiver("test-dis-rcv-queue; {create: always, delete: always}")
    m = self.content("testDisconnectedReceiver")
    self.conn.connect()
    snd = self.ssn.sender("test-dis-rcv-queue")
    snd.send(m)
    self.drain(rcv, expected=[m])

  def testNextReceiver(self):
    ADDR = 'test-next-rcv-queue; {create: always, delete: always}'
    rcv1 = self.ssn.receiver(ADDR, capacity=UNLIMITED)
    rcv2 = self.ssn.receiver(ADDR, capacity=UNLIMITED)
    rcv3 = self.ssn.receiver(ADDR, capacity=UNLIMITED)

    snd = self.ssn.sender(ADDR)

    msgs = []
    for i in range(10):
      content = self.content("testNextReceiver", i)
      snd.send(content)
      msgs.append(content)

    fetched = []
    try:
      while True:
        rcv = self.ssn.next_receiver(timeout=self.delay())
        assert rcv in (rcv1, rcv2, rcv3)
        assert rcv.pending() > 0
        fetched.append(rcv.fetch().content)
    except Empty:
      pass
    assert msgs == fetched, "expecting %s, got %s" % (msgs, fetched)
    self.ssn.acknowledge()
    #we set the capacity to 0 to prevent the deletion of the queue -
    #triggered the deletion policy when the first receiver is closed -
    #resulting in session exceptions being issued for the remaining
    #active subscriptions:
    for r in [rcv1, rcv2, rcv3]:
      r.capacity = 0

  # XXX, we need a convenient way to assert that required queues are
  # empty on setup, and possibly also to drain queues on teardown
  def ackTest(self, acker, ack_capacity=None):
    # send a bunch of messages
    snd = self.ssn.sender(ACK_QC, durable=self.durable())
    contents = [self.content("ackTest", i) for i in range(15)]
    for c in contents:
      snd.send(c)

    # drain the queue, verify the messages are there and then close
    # without acking
    rcv = self.ssn.receiver(ACK_QC)
    self.drain(rcv, expected=contents)
    self.ssn.close()

    # drain the queue again, verify that they are all the messages
    # were requeued, and ack this time before closing
    self.ssn = self.conn.session()
    if ack_capacity is not None:
      self.ssn.ack_capacity = ack_capacity
    rcv = self.ssn.receiver(ACK_QC)
    self.drain(rcv, expected=contents)
    acker(self.ssn)
    self.ssn.close()

    # drain the queue a final time and verify that the messages were
    # dequeued
    self.ssn = self.conn.session()
    rcv = self.ssn.receiver(ACK_QD)
    self.assertEmpty(rcv)

  def testAcknowledge(self):
    self.ackTest(lambda ssn: ssn.acknowledge())

  def testAcknowledgeAsync(self):
    self.ackTest(lambda ssn: ssn.acknowledge(sync=False))

  def testAcknowledgeAsyncAckCap0(self):
    try:
      try:
        self.ackTest(lambda ssn: ssn.acknowledge(sync=False), 0)
        assert False, "acknowledge shouldn't succeed with ack_capacity of zero"
      except InsufficientCapacity:
        pass
    finally:
      self.ssn.ack_capacity = UNLIMITED
      self.drain(self.ssn.receiver(ACK_QD))
      self.ssn.acknowledge()

  def testAcknowledgeAsyncAckCap1(self):
    self.ackTest(lambda ssn: ssn.acknowledge(sync=False), 1)

  def testAcknowledgeAsyncAckCap5(self):
    self.ackTest(lambda ssn: ssn.acknowledge(sync=False), 5)

  def testAcknowledgeAsyncAckCapUNLIMITED(self):
    self.ackTest(lambda ssn: ssn.acknowledge(sync=False), UNLIMITED)

  def testRelease(self):
    msgs = [self.message("testRelease", i) for i in range(3)]
    snd = self.ssn.sender("test-release-queue; {create: always, delete: always}")
    for m in msgs:
      snd.send(m)
    rcv = self.ssn.receiver(snd.target)
    echos = self.drain(rcv, expected=msgs)
    self.ssn.acknowledge(echos[0])
    self.ssn.acknowledge(echos[1], Disposition(RELEASED, set_redelivered=True))
    self.ssn.acknowledge(echos[2], Disposition(RELEASED))
    self.drain(rcv, limit=1, expected=msgs[1:2], redelivered=True)
    self.drain(rcv, expected=msgs[2:3])
    self.ssn.acknowledge()

  def testReject(self):
    msgs = [self.message("testReject", i) for i in range(3)]
    snd = self.ssn.sender("""
      test-reject-queue; {
        create: always,
        delete: always,
        node: {
          x-declare: {
            alternate-exchange: 'amq.topic'
          }
        }
      }
""")
    for m in msgs:
      snd.send(m)
    rcv = self.ssn.receiver(snd.target)
    rej = self.ssn.receiver("amq.topic")
    echos = self.drain(rcv, expected=msgs)
    self.ssn.acknowledge(echos[0])
    self.ssn.acknowledge(echos[1], Disposition(REJECTED))
    self.ssn.acknowledge(echos[2],
                         Disposition(REJECTED, code=3, text="test-reject"))
    self.drain(rej, expected=msgs[1:])
    self.ssn.acknowledge()

  def send(self, ssn, target, base, count=1):
    snd = ssn.sender(target, durable=self.durable())
    messages = []
    for i in range(count):
      c = self.message(base, i)
      snd.send(c)
      messages.append(c)
    snd.close()
    return messages

  def txTest(self, commit):
    TX_Q = 'test-tx-queue; {create: sender, delete: receiver}'
    TX_Q_COPY = 'test-tx-queue-copy; {create: always, delete: always}'
    txssn = self.conn.session(transactional=True)
    messages = self.send(self.ssn, TX_Q, "txTest", 3)
    txrcv = txssn.receiver(TX_Q)
    txsnd = txssn.sender(TX_Q_COPY, durable=self.durable())
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
      self.drain(copy_rcv, expected=messages)
    else:
      txssn.rollback()
      self.drain(rcv, expected=messages, redelivered=True)
      self.assertEmpty(copy_rcv)
    self.ssn.acknowledge()

  def testCommit(self):
    self.txTest(True)

  def testRollback(self):
    self.txTest(False)

  def txTestSend(self, commit):
    TX_SEND_Q = 'test-tx-send-queue; {create: sender, delete: receiver}'
    txssn = self.conn.session(transactional=True)
    messages = self.send(txssn, TX_SEND_Q, "txTestSend", 3)
    rcv = self.ssn.receiver(TX_SEND_Q)
    self.assertEmpty(rcv)

    if commit:
      txssn.commit()
      self.drain(rcv, expected=messages)
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
    TX_ACK_QC = 'test-tx-ack-queue; {create: always}'
    TX_ACK_QD = 'test-tx-ack-queue; {delete: always}'
    txssn = self.conn.session(transactional=True)
    txrcv = txssn.receiver(TX_ACK_QC)
    self.assertEmpty(txrcv)
    messages = self.send(self.ssn, TX_ACK_QC, "txTestAck", 3)
    self.drain(txrcv, expected=messages)

    if commit:
      txssn.acknowledge()
    else:
      txssn.rollback()
      self.drain(txrcv, expected=messages, redelivered=True)
      txssn.acknowledge()
      txssn.rollback()
      self.drain(txrcv, expected=messages, redelivered=True)
      txssn.commit() # commit without ack
      self.assertEmpty(txrcv)

    txssn.close()

    txssn = self.conn.session(transactional=True)
    txrcv = txssn.receiver(TX_ACK_QC)
    self.drain(txrcv, expected=messages, redelivered=True)
    txssn.acknowledge()
    txssn.commit()
    rcv = self.ssn.receiver(TX_ACK_QD)
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

RECEIVER_Q = 'test-receiver-queue; {create: always, delete: always}'

class ReceiverTests(Base):

  def setup_connection(self):
    return Connection.open(self.broker.host, self.broker.port,
                           reconnect=self.reconnect())

  def setup_session(self):
    return self.conn.session()

  def setup_sender(self):
    return self.ssn.sender(RECEIVER_Q)

  def setup_receiver(self):
    return self.ssn.receiver(RECEIVER_Q)

  def send(self, base, count = None):
    content = self.content(base, count)
    self.snd.send(content)
    return content

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

  def testCapacityIncrease(self):
    content = self.send("testCapacityIncrease")
    self.sleep()
    assert self.rcv.pending() == 0
    self.rcv.capacity = UNLIMITED
    self.sleep()
    assert self.rcv.pending() == 1
    msg = self.rcv.fetch(0)
    assert msg.content == content
    assert self.rcv.pending() == 0
    self.ssn.acknowledge()

  def testCapacityDecrease(self):
    self.rcv.capacity = UNLIMITED
    one = self.send("testCapacityDecrease", 1)
    self.sleep()
    assert self.rcv.pending() == 1
    msg = self.rcv.fetch(0)
    assert msg.content == one

    self.rcv.capacity = 0

    two = self.send("testCapacityDecrease", 2)
    self.sleep()
    assert self.rcv.pending() == 0
    msg = self.rcv.fetch(0)
    assert msg.content == two

    self.ssn.acknowledge()

  def testCapacity(self):
    self.rcv.capacity = 5
    self.assertPending(self.rcv, 0)

    for i in range(15):
      self.send("testCapacity", i)
    self.sleep()
    self.assertPending(self.rcv, 5)

    self.drain(self.rcv, limit = 5)
    self.sleep()
    self.assertPending(self.rcv, 5)

    drained = self.drain(self.rcv)
    assert len(drained) == 10, "%s, %s" % (len(drained), drained)
    self.assertPending(self.rcv, 0)

    self.ssn.acknowledge()

  def testCapacityUNLIMITED(self):
    self.rcv.capacity = UNLIMITED
    self.assertPending(self.rcv, 0)

    for i in range(10):
      self.send("testCapacityUNLIMITED", i)
    self.sleep()
    self.assertPending(self.rcv, 10)

    self.drain(self.rcv)
    self.assertPending(self.rcv, 0)

    self.ssn.acknowledge()

  def testPending(self):
    self.rcv.capacity = UNLIMITED
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

  def testDoubleClose(self):
    m1 = self.content("testDoubleClose", 1)
    m2 = self.content("testDoubleClose", 2)

    snd = self.ssn.sender("""test-double-close; {
  create: always,
  delete: sender,
  node: {
    type: topic
  }
}
""")
    r1 = self.ssn.receiver(snd.target)
    r2 = self.ssn.receiver(snd.target)
    snd.send(m1)
    self.drain(r1, expected=[m1])
    self.drain(r2, expected=[m1])
    r1.close()
    snd.send(m2)
    self.drain(r2, expected=[m2])
    r2.close()

  # XXX: need testClose

  def testMode(self):
    msgs = [self.content("testMode", 1),
            self.content("testMode", 2),
            self.content("testMode", 3)]

    for m in msgs:
      self.snd.send(m)

    rb = self.ssn.receiver('test-receiver-queue; {mode: browse}')
    rc = self.ssn.receiver('test-receiver-queue; {mode: consume}')
    self.drain(rb, expected=msgs)
    self.drain(rc, expected=msgs)
    rb2 = self.ssn.receiver(rb.source)
    self.assertEmpty(rb2)
    self.drain(self.rcv, expected=[])

class AddressTests(Base):

  def setup_connection(self):
    return Connection.open(self.broker.host, self.broker.port,
                           reconnect=self.reconnect())

  def setup_session(self):
    return self.conn.session()

  def badOption(self, options, error):
    try:
      self.ssn.sender("test-bad-options-snd; %s" % options)
      assert False
    except SendError, e:
      assert "error in options: %s" % error == str(e), e

    try:
      self.ssn.receiver("test-bad-options-rcv; %s" % options)
      assert False
    except ReceiveError, e:
      assert "error in options: %s" % error == str(e), e

  def testIllegalKey(self):
    self.badOption("{create: always, node: "
                   "{this-property-does-not-exist: 3}}",
                   "node: this-property-does-not-exist: "
                   "illegal key")

  def testWrongValue(self):
    self.badOption("{create: asdf}", "create: asdf not in "
                   "('always', 'sender', 'receiver', 'never')")

  def testWrongType1(self):
    self.badOption("{node: asdf}",
                   "node: asdf is not a map")

  def testWrongType2(self):
    self.badOption("{node: {durable: []}}",
                   "node: durable: [] is not a bool")

  def testCreateQueue(self):
    snd = self.ssn.sender("test-create-queue; {create: always, delete: always, "
                          "node: {type: queue, durable: False, "
                          "x-declare: {auto_delete: true}}}")
    content = self.content("testCreateQueue")
    snd.send(content)
    rcv = self.ssn.receiver("test-create-queue")
    self.drain(rcv, expected=[content])

  def createExchangeTest(self, props=""):
    addr = """test-create-exchange; {
                create: always,
                delete: always,
                node: {
                  type: topic,
                  durable: False,
                  x-declare: {auto_delete: true, %s}
                }
              }""" % props
    snd = self.ssn.sender(addr)
    snd.send("ping")
    rcv1 = self.ssn.receiver("test-create-exchange/first")
    rcv2 = self.ssn.receiver("test-create-exchange/first")
    rcv3 = self.ssn.receiver("test-create-exchange/second")
    for r in (rcv1, rcv2, rcv3):
      try:
        r.fetch(0)
        assert False
      except Empty:
        pass
    msg1 = Message(self.content("testCreateExchange", 1), subject="first")
    msg2 = Message(self.content("testCreateExchange", 2), subject="second")
    snd.send(msg1)
    snd.send(msg2)
    self.drain(rcv1, expected=[msg1.content])
    self.drain(rcv2, expected=[msg1.content])
    self.drain(rcv3, expected=[msg2.content])

  def testCreateExchange(self):
    self.createExchangeTest()

  def testCreateExchangeDirect(self):
    self.createExchangeTest("type: direct")

  def testCreateExchangeTopic(self):
    self.createExchangeTest("type: topic")

  def testDeleteBySender(self):
    snd = self.ssn.sender("test-delete; {create: always}")
    snd.send("ping")
    snd.close()
    snd = self.ssn.sender("test-delete; {delete: always}")
    snd.send("ping")
    snd.close()
    try:
      self.ssn.sender("test-delete")
    except SendError, e:
      assert "no such queue" in str(e)

  def testDeleteByReceiver(self):
    rcv = self.ssn.receiver("test-delete; {create: always, delete: always}")
    try:
      rcv.fetch(0)
    except Empty:
      pass
    rcv.close()

    try:
      self.ssn.receiver("test-delete")
      assert False
    except ReceiveError, e:
      assert "no such queue" in str(e)

  def testDeleteSpecial(self):
    snd = self.ssn.sender("amq.topic; {delete: always}")
    snd.send("asdf")
    try:
      snd.close()
    except SessionError, e:
      assert "Cannot delete default exchange" in str(e)
    # XXX: need to figure out close after error
    self.conn._remove_session(self.ssn)

  def testNodeBindingsQueue(self):
    snd = self.ssn.sender("""
test-node-bindings-queue; {
  create: always,
  delete: always,
  node: {
    x-bindings: [{exchange: "amq.topic", key: "a.#"},
                 {exchange: "amq.direct", key: "b"},
                 {exchange: "amq.topic", key: "c.*"}]
  }
}
""")
    snd.send("one")
    snd_a = self.ssn.sender("amq.topic/a.foo")
    snd_b = self.ssn.sender("amq.direct/b")
    snd_c = self.ssn.sender("amq.topic/c.bar")
    snd_a.send("two")
    snd_b.send("three")
    snd_c.send("four")
    rcv = self.ssn.receiver("test-node-bindings-queue")
    self.drain(rcv, expected=["one", "two", "three", "four"])

  def testNodeBindingsTopic(self):
    rcv = self.ssn.receiver("test-node-bindings-topic-queue; {create: always, delete: always}")
    rcv_a = self.ssn.receiver("test-node-bindings-topic-queue-a; {create: always, delete: always}")
    rcv_b = self.ssn.receiver("test-node-bindings-topic-queue-b; {create: always, delete: always}")
    rcv_c = self.ssn.receiver("test-node-bindings-topic-queue-c; {create: always, delete: always}")
    snd = self.ssn.sender("""
test-node-bindings-topic; {
  create: always,
  delete: always,
  node: {
    type: topic,
    x-bindings: [{queue: test-node-bindings-topic-queue, key: "#"},
                 {queue: test-node-bindings-topic-queue-a, key: "a.#"},
                 {queue: test-node-bindings-topic-queue-b, key: "b"},
                 {queue: test-node-bindings-topic-queue-c, key: "c.*"}]
  }
}
""")
    m1 = Message("one")
    m2 = Message(subject="a.foo", content="two")
    m3 = Message(subject="b", content="three")
    m4 = Message(subject="c.bar", content="four")
    snd.send(m1)
    snd.send(m2)
    snd.send(m3)
    snd.send(m4)
    self.drain(rcv, expected=[m1, m2, m3, m4])
    self.drain(rcv_a, expected=[m2])
    self.drain(rcv_b, expected=[m3])
    self.drain(rcv_c, expected=[m4])

  def testLinkBindings(self):
    m_a = self.message("testLinkBindings", 1, subject="a")
    m_b = self.message("testLinkBindings", 2, subject="b")

    self.ssn.sender("test-link-bindings-queue; {create: always, delete: always}")
    snd = self.ssn.sender("amq.topic")

    snd.send(m_a)
    snd.send(m_b)
    snd.close()

    rcv = self.ssn.receiver("test-link-bindings-queue")
    self.assertEmpty(rcv)

    snd = self.ssn.sender("""
amq.topic; {
  link: {
    x-bindings: [{queue: test-link-bindings-queue, key: a}]
  }
}
""")

    snd.send(m_a)
    snd.send(m_b)

    self.drain(rcv, expected=[m_a])
    rcv.close()

    rcv = self.ssn.receiver("""
test-link-bindings-queue; {
  link: {
    x-bindings: [{exchange: "amq.topic", key: b}]
  }
}
""")

    snd.send(m_a)
    snd.send(m_b)

    self.drain(rcv, expected=[m_a, m_b])

  def testSubjectOverride(self):
    snd = self.ssn.sender("amq.topic/a")
    rcv_a = self.ssn.receiver("amq.topic/a")
    rcv_b = self.ssn.receiver("amq.topic/b")
    m1 = self.content("testSubjectOverride", 1)
    m2 = self.content("testSubjectOverride", 2)
    snd.send(m1)
    snd.send(Message(subject="b", content=m2))
    self.drain(rcv_a, expected=[m1])
    self.drain(rcv_b, expected=[m2])

  def testSubjectDefault(self):
    m1 = self.content("testSubjectDefault", 1)
    m2 = self.content("testSubjectDefault", 2)
    snd = self.ssn.sender("amq.topic/a")
    rcv = self.ssn.receiver("amq.topic")
    snd.send(m1)
    snd.send(Message(subject="b", content=m2))
    e1 = rcv.fetch(timeout=0)
    e2 = rcv.fetch(timeout=0)
    assert e1.subject == "a", "subject: %s" % e1.subject
    assert e2.subject == "b", "subject: %s" % e2.subject
    self.assertEmpty(rcv)

  def doReliabilityTest(self, reliability, messages, expected):
    snd = self.ssn.sender("amq.topic")
    rcv = self.ssn.receiver("amq.topic; {link: {reliability: %s}}" % reliability)
    for m in messages:
      snd.send(m)
    self.conn.disconnect()
    self.conn.connect()
    self.drain(rcv, expected=expected)

  def testReliabilityUnreliable(self):
    msgs = [self.message("testReliabilityUnreliable", i) for i in range(3)]
    self.doReliabilityTest("unreliable", msgs, [])

  def testReliabilityAtLeastOnce(self):
    msgs = [self.message("testReliabilityAtLeastOnce", i) for i in range(3)]
    self.doReliabilityTest("at-least-once", msgs, msgs)

  def testLinkName(self):
    msgs = [self.message("testLinkName", i) for i in range(3)]
    snd = self.ssn.sender("amq.topic")
    trcv = self.ssn.receiver("amq.topic; {link: {name: test-link-name}}")
    qrcv = self.ssn.receiver("test-link-name")
    for m in msgs:
      snd.send(m)
    self.drain(qrcv, expected=msgs)

NOSUCH_Q = "this-queue-should-not-exist"
UNPARSEABLE_ADDR = "name/subject; {bad options"
UNLEXABLE_ADDR = "\0x0\0x1\0x2\0x3"

class AddressErrorTests(Base):

  def setup_connection(self):
    return Connection.open(self.broker.host, self.broker.port,
                           reconnect=self.reconnect())

  def setup_session(self):
    return self.conn.session()

  def senderErrorTest(self, addr, exc, check=lambda e: True):
    try:
      self.ssn.sender(addr, durable=self.durable())
      assert False, "sender creation succeeded"
    except exc, e:
      assert check(e), "unexpected error: %s" % compat.format_exc(e)

  def receiverErrorTest(self, addr, exc, check=lambda e: True):
    try:
      self.ssn.receiver(addr)
      assert False, "receiver creation succeeded"
    except exc, e:
      assert check(e), "unexpected error: %s" % compat.format_exc(e)

  def testNoneTarget(self):
    # XXX: should have specific exception for this
    self.senderErrorTest(None, SendError)

  def testNoneSource(self):
    # XXX: should have specific exception for this
    self.receiverErrorTest(None, ReceiveError)

  def testNoTarget(self):
    # XXX: should have specific exception for this
    self.senderErrorTest(NOSUCH_Q, SendError, lambda e: NOSUCH_Q in str(e))

  def testNoSource(self):
    # XXX: should have specific exception for this
    self.receiverErrorTest(NOSUCH_Q, ReceiveError, lambda e: NOSUCH_Q in str(e))

  def testUnparseableTarget(self):
    # XXX: should have specific exception for this
    self.senderErrorTest(UNPARSEABLE_ADDR, SendError,
                         lambda e: "expecting COLON" in str(e))

  def testUnparseableSource(self):
    # XXX: should have specific exception for this
    self.receiverErrorTest(UNPARSEABLE_ADDR, ReceiveError,
                           lambda e: "expecting COLON" in str(e))

  def testUnlexableTarget(self):
    # XXX: should have specific exception for this
    self.senderErrorTest(UNLEXABLE_ADDR, SendError,
                         lambda e: "unrecognized characters" in str(e))

  def testUnlexableSource(self):
    # XXX: should have specific exception for this
    self.receiverErrorTest(UNLEXABLE_ADDR, ReceiveError,
                           lambda e: "unrecognized characters" in str(e))

  def testInvalidMode(self):
    # XXX: should have specific exception for this
    self.receiverErrorTest('name; {mode: "this-is-a-bad-receiver-mode"}',
                           ReceiveError,
                           lambda e: "not in ('browse', 'consume')" in str(e))

SENDER_Q = 'test-sender-q; {create: always, delete: always}'

class SenderTests(Base):

  def setup_connection(self):
    return Connection.open(self.broker.host, self.broker.port,
                           reconnect=self.reconnect())

  def setup_session(self):
    return self.conn.session()

  def setup_sender(self):
    return self.ssn.sender(SENDER_Q)

  def setup_receiver(self):
    return self.ssn.receiver(SENDER_Q)

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

  def asyncTest(self, capacity):
    self.snd.capacity = capacity
    msgs = [self.content("asyncTest", i) for i in range(15)]
    for m in msgs:
      self.snd.send(m, sync=False)
    self.drain(self.rcv, timeout=self.delay(), expected=msgs)
    self.ssn.acknowledge()

  def testSendAsyncCapacity0(self):
    try:
      self.asyncTest(0)
      assert False, "send shouldn't succeed with zero capacity"
    except InsufficientCapacity:
      # this is expected
      pass

  def testSendAsyncCapacity1(self):
    self.asyncTest(1)

  def testSendAsyncCapacity5(self):
    self.asyncTest(5)

  def testSendAsyncCapacityUNLIMITED(self):
    self.asyncTest(UNLIMITED)

  def testCapacityTimeout(self):
    self.snd.capacity = 1
    msgs = []
    caught = False
    while len(msgs) < 100:
      m = self.content("testCapacity", len(msgs))
      try:
        self.snd.send(m, sync=False, timeout=0)
        msgs.append(m)
      except InsufficientCapacity:
        caught = True
        break
    self.snd.sync()
    self.drain(self.rcv, expected=msgs)
    self.ssn.acknowledge()
    assert caught, "did not exceed capacity"
