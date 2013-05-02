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

import errno, os, socket, sys, time
from qpid import compat
from qpid.compat import set
from qpid.messaging import *
from qpid.messaging.transports import TRANSPORTS
from qpid.tests.messaging import Base
from threading import Thread

class SetupTests(Base):

  def testEstablish(self):
    self.conn = Connection.establish(self.broker, **self.connection_options())
    self.ping(self.conn.session())

  def testOpen(self):
    self.conn = Connection(self.broker, **self.connection_options())
    self.conn.open()
    self.ping(self.conn.session())

  def testOpenReconnectURLs(self):
    options = self.connection_options()
    options["reconnect_urls"] = [self.broker, self.broker]
    self.conn = Connection(self.broker, **options)
    self.conn.open()
    self.ping(self.conn.session())

  def testTcpNodelay(self):
    self.conn = Connection.establish(self.broker, tcp_nodelay=True)
    assert self.conn._driver._transport.socket.getsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY)

  def testConnectError(self):
    try:
      # Specifying port 0 yields a bad address on Windows; port 4 is unassigned
      self.conn = Connection.establish("localhost:4")
      assert False, "connect succeeded"
    except ConnectError, e:
      assert "refused" in str(e)

  def testGetError(self):
    self.conn = Connection("localhost:0")
    try:
      self.conn.open()
      assert False, "connect succeeded"
    except ConnectError, e:
      assert self.conn.get_error() == e

  def use_fds(self):
    fds = []
    try:
      while True:
        fds.append(os.open(getattr(os, "devnull", "/dev/null"), os.O_RDONLY))
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
        conn = Connection.establish(self.broker, **self.connection_options())
        conn.close()
    finally:
      while fds:
        os.close(fds.pop())

  def testOpenFailResourceLeaks(self):
    fds = self.use_fds()
    try:
      for i in range(32):
        if fds: os.close(fds.pop())
      for i in xrange(64):
        conn = Connection("localhost:0", **self.connection_options())
        # XXX: we need to force a waiter to be created for this test
        # to work
        conn._lock.acquire()
        conn._wait(lambda: False, timeout=0.001)
        conn._lock.release()
        try:
          conn.open()
        except ConnectError, e:
          pass
    finally:
      while fds:
        os.close(fds.pop())

  def testReconnect(self):
    options = self.connection_options()
    real = TRANSPORTS["tcp"]

    class flaky:

      def __init__(self, conn, host, port):
        self.real = real(conn, host, port)
        self.sent_count = 0
        self.recv_count = 0

      def fileno(self):
        return self.real.fileno()

      def reading(self, reading):
        return self.real.reading(reading)

      def writing(self, writing):
        return self.real.writing(writing)

      def send(self, bytes):
        if self.sent_count > 2048:
          raise socket.error("fake error")
        n = self.real.send(bytes)
        self.sent_count += n
        return n

      def recv(self, n):
        if self.recv_count > 2048:
          return ""
        bytes = self.real.recv(n)
        self.recv_count += len(bytes)
        return bytes

      def close(self):
        self.real.close()

    TRANSPORTS["flaky"] = flaky

    options["reconnect"] = True
    options["reconnect_interval"] = 0
    options["reconnect_limit"] = 100
    options["reconnect_log"] = False
    options["transport"] = "flaky"

    self.conn = Connection.establish(self.broker, **options)
    ssn = self.conn.session()
    snd = ssn.sender("test-reconnect-queue; {create: always, delete: always}")
    rcv = ssn.receiver(snd.target)

    msgs = [self.message("testReconnect", i) for i in range(20)]
    for m in msgs:
      snd.send(m)

    content = set()
    drained = []
    duplicates = []
    try:
      while True:
        m = rcv.fetch(timeout=0)
        if m.content not in content:
          content.add(m.content)
          drained.append(m)
        else:
          duplicates.append(m)
        ssn.acknowledge(m)
    except Empty:
      pass
    # XXX: apparently we don't always get duplicates, should figure out why
    #assert duplicates, "no duplicates"
    assert len(drained) == len(msgs)
    for m, d in zip(msgs, drained):
      # XXX: we should figure out how to provide proper end to end
      # redelivered
      self.assertEcho(m, d, d.redelivered)

class ConnectionTests(Base):

  def setup_connection(self):
    return Connection.establish(self.broker, **self.connection_options())

  def testCheckClosed(self):
    assert not self.conn.check_closed()

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

  def testDetach(self):
    ssn = self.conn.session()
    self.ping(ssn)
    self.conn.detach()
    try:
      self.ping(ssn)
      assert False, "ping succeeded"
    except Detached:
      # this is the expected failure when pinging on a detached
      # connection
      pass
    self.conn.attach()
    self.ping(ssn)

  def testClose(self):
    self.conn.close()
    assert not self.conn.attached()

  def testSimultaneousClose(self):
    ssns = [self.conn.session() for i in range(3)]
    for s in ssns:
      for i in range(3):
        s.receiver("amq.topic")
        s.sender("amq.topic")

    def closer(errors):
      try:
        self.conn.close()
      except:
        _, e, _ = sys.exc_info()
        errors.append(compat.format_exc(e))

    t1_errors = []
    t2_errors = []
    t1 = Thread(target=lambda: closer(t1_errors))
    t2 = Thread(target=lambda: closer(t2_errors))
    t1.start()
    t2.start()
    t1.join(self.delay())
    t2.join(self.delay())

    assert not t1_errors, t1_errors[0]
    assert not t2_errors, t2_errors[0]

class hangable:

  def __init__(self, conn, host, port):
    self.tcp = TRANSPORTS["tcp"](conn, host, port)
    self.hung = False

  def hang(self):
    self.hung = True

  def fileno(self):
    return self.tcp.fileno()

  def reading(self, reading):
    if self.hung:
      return True
    else:
      return self.tcp.reading(reading)

  def writing(self, writing):
    if self.hung:
      return False
    else:
      return self.tcp.writing(writing)

  def send(self, bytes):
    if self.hung:
      return 0
    else:
      return self.tcp.send(bytes)

  def recv(self, n):
    if self.hung:
      return ""
    else:
      return self.tcp.recv(n)

  def close(self):
    self.tcp.close()

TRANSPORTS["hangable"] = hangable

class TimeoutTests(Base):

  def setup_connection(self):
    options = self.connection_options()
    options["transport"] = "hangable"
    return Connection.establish(self.broker, **options)

  def setup_session(self):
    return self.conn.session()

  def setup_sender(self):
    return self.ssn.sender("amq.topic")

  def setup_receiver(self):
    return self.ssn.receiver("amq.topic; {link: {reliability: unreliable}}")

  def teardown_connection(self, conn):
    try:
      conn.detach(timeout=0)
    except Timeout:
      pass

  def hang(self):
    self.conn._driver._transport.hang()

  def timeoutTest(self, method):
    self.hang()
    try:
      method(timeout=self.delay())
      assert False, "did not time out"
    except Timeout:
      pass

  def testSenderSync(self):
    self.snd.send(self.content("testSenderSync"), sync=False)
    self.timeoutTest(self.snd.sync)

  def testSenderClose(self):
    self.snd.send(self.content("testSenderClose"), sync=False)
    self.timeoutTest(self.snd.close)

  def testReceiverClose(self):
    self.timeoutTest(self.rcv.close)

  def testSessionSync(self):
    self.snd.send(self.content("testSessionSync"), sync=False)
    self.timeoutTest(self.ssn.sync)

  def testSessionClose(self):
    self.timeoutTest(self.ssn.close)

  def testConnectionDetach(self):
    self.timeoutTest(self.conn.detach)

  def testConnectionClose(self):
    self.timeoutTest(self.conn.close)

  def testConnectionOpen(self):
    options = self.connection_options()
    options["reconnect"] = True
    options["reconnect_timeout"] = self.delay()
    try:
      bad_conn = Connection.establish("badhostname", **options)
      assert False, "did not time out"
    except Timeout:
      pass

ACK_QC = 'test-ack-queue; {create: always}'
ACK_QD = 'test-ack-queue; {delete: always}'

class SessionTests(Base):

  def setup_connection(self):
    return Connection.establish(self.broker, **self.connection_options())

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

  def testDetachedReceiver(self):
    self.conn.detach()
    rcv = self.ssn.receiver("test-dis-rcv-queue; {create: always, delete: always}")
    m = self.content("testDetachedReceiver")
    self.conn.attach()
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
        assert rcv.available() > 0
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
                         Disposition(REJECTED, code=0, text="test-reject"))
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

  def testDoubleCommit(self):
    ssn = self.conn.session(transactional=True)
    snd = ssn.sender("amq.direct/doubleCommit")
    rcv = ssn.receiver("amq.direct/doubleCommit")
    msgs = [self.message("testDoubleCommit", i, subject="doubleCommit") for i in range(3)]
    for m in msgs:
      snd.send(m)
    ssn.commit()
    self.drain(rcv, expected=msgs)
    ssn.acknowledge()
    ssn.commit()

  def testClose(self):
    self.ssn.close()
    try:
      self.ping(self.ssn)
      assert False, "ping succeeded"
    except Detached:
      pass

RECEIVER_Q = 'test-receiver-queue; {create: always, delete: always}'

class ReceiverTests(Base):

  def setup_connection(self):
    return Connection.establish(self.broker, **self.connection_options())

  def setup_session(self):
    return self.conn.session()

  def setup_sender(self):
    return self.ssn.sender(RECEIVER_Q)

  def setup_receiver(self):
    return self.ssn.receiver(RECEIVER_Q)

  def send(self, base, count = None, sync=True):
    content = self.content(base, count)
    self.snd.send(content, sync=sync)
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

  def fetchFromClosedTest(self, entry):
    entry.close()
    try:
      msg = self.rcv.fetch(0)
      assert False, "unexpected result: %s" % msg
    except Empty, e:
      assert False, "unexpected exception: %s" % e
    except LinkClosed, e:
      pass

  def testFetchFromClosedReceiver(self):
    self.fetchFromClosedTest(self.rcv)

  def testFetchFromClosedSession(self):
    self.fetchFromClosedTest(self.ssn)

  def testFetchFromClosedConnection(self):
    self.fetchFromClosedTest(self.conn)

  def fetchFromConcurrentCloseTest(self, entry):
    def closer():
      self.sleep()
      entry.close()
    t = Thread(target=closer)
    t.start()
    try:
      msg = self.rcv.fetch()
      assert False, "unexpected result: %s" % msg
    except Empty, e:
      assert False, "unexpected exception: %s" % e
    except LinkClosed, e:
      pass
    t.join()

  def testFetchFromConcurrentCloseReceiver(self):
    self.fetchFromConcurrentCloseTest(self.rcv)

  def testFetchFromConcurrentCloseSession(self):
    self.fetchFromConcurrentCloseTest(self.ssn)

  def testFetchFromConcurrentCloseConnection(self):
    self.fetchFromConcurrentCloseTest(self.conn)

  def testCapacityIncrease(self):
    content = self.send("testCapacityIncrease")
    self.sleep()
    assert self.rcv.available() == 0
    self.rcv.capacity = UNLIMITED
    self.sleep()
    assert self.rcv.available() == 1
    msg = self.rcv.fetch(0)
    assert msg.content == content
    assert self.rcv.available() == 0
    self.ssn.acknowledge()

  def testCapacityDecrease(self):
    self.rcv.capacity = UNLIMITED
    one = self.send("testCapacityDecrease", 1)
    self.sleep()
    assert self.rcv.available() == 1
    msg = self.rcv.fetch(0)
    assert msg.content == one

    self.rcv.capacity = 0

    two = self.send("testCapacityDecrease", 2)
    self.sleep()
    assert self.rcv.available() == 0
    msg = self.rcv.fetch(0)
    assert msg.content == two

    self.ssn.acknowledge()

  def capacityTest(self, capacity, threshold=None):
    if threshold is not None:
      self.rcv.threshold = threshold
    self.rcv.capacity = capacity
    self.assertAvailable(self.rcv, 0)

    for i in range(2*capacity):
      self.send("capacityTest(%s, %s)" % (capacity, threshold), i, sync=False)
    self.snd.sync()
    self.sleep()
    self.assertAvailable(self.rcv)

    first = capacity/2
    second = capacity - first
    self.drain(self.rcv, limit = first)
    self.sleep()
    self.assertAvailable(self.rcv)
    self.drain(self.rcv, limit = second)
    self.sleep()
    self.assertAvailable(self.rcv)

    drained = self.drain(self.rcv)
    assert len(drained) == capacity, "%s, %s" % (len(drained), drained)
    self.assertAvailable(self.rcv, 0)

    self.ssn.acknowledge()

  def testCapacity5(self):
    self.capacityTest(5)

  def testCapacity5Threshold1(self):
    self.capacityTest(5, 1)

  def testCapacity10(self):
    self.capacityTest(10)

  def testCapacity10Threshold1(self):
    self.capacityTest(10, 1)

  def testCapacity100(self):
    self.capacityTest(100)

  def testCapacity100Threshold1(self):
    self.capacityTest(100, 1)

  def testCapacityUNLIMITED(self):
    self.rcv.capacity = UNLIMITED
    self.assertAvailable(self.rcv, 0)

    for i in range(10):
      self.send("testCapacityUNLIMITED", i)
    self.sleep()
    self.assertAvailable(self.rcv, 10)

    self.drain(self.rcv)
    self.assertAvailable(self.rcv, 0)

    self.ssn.acknowledge()

  def testAvailable(self):
    self.rcv.capacity = UNLIMITED
    assert self.rcv.available() == 0

    for i in range(3):
      self.send("testAvailable", i)
    self.sleep()
    assert self.rcv.available() == 3

    for i in range(3, 10):
      self.send("testAvailable", i)
    self.sleep()
    assert self.rcv.available() == 10

    self.drain(self.rcv, limit=3)
    assert self.rcv.available() == 7

    self.drain(self.rcv)
    assert self.rcv.available() == 0

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

  def testUnsettled(self):
    # just tests the code path and not the value
    rcv = self.ssn.receiver('test-receiver-unsettled-queue; {create: always, delete: always}')
    rcv.unsettled()

  def unreliabilityTest(self, mode="unreliable"):
    msgs = [self.message("testUnreliable", i) for i in range(3)]
    snd = self.ssn.sender("test-unreliability-queue; {create: sender, delete: receiver}")
    rcv = self.ssn.receiver(snd.target)
    for m in msgs:
      snd.send(m)

    # close without ack on reliable receiver, messages should be requeued
    ssn = self.conn.session()
    rrcv = ssn.receiver("test-unreliability-queue")
    self.drain(rrcv, expected=msgs)
    ssn.close()

    # close without ack on unreliable receiver, messages should not be requeued
    ssn = self.conn.session()
    urcv = ssn.receiver("test-unreliability-queue; {link: {reliability: %s}}" % mode)
    self.drain(urcv, expected=msgs, redelivered=True)
    ssn.close()

    self.assertEmpty(rcv)

  def testUnreliable(self):
    self.unreliabilityTest(mode="unreliable")

  def testAtMostOnce(self):
    self.unreliabilityTest(mode="at-most-once")

class AddressTests(Base):

  def setup_connection(self):
    return Connection.establish(self.broker, **self.connection_options())

  def setup_session(self):
    return self.conn.session()

  def badOption(self, options, error):
    try:
      self.ssn.sender("test-bad-options-snd; %s" % options)
      assert False
    except InvalidOption, e:
      assert "error in options: %s" % error == str(e), e

    try:
      self.ssn.receiver("test-bad-options-rcv; %s" % options)
      assert False
    except InvalidOption, e:
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
    except NotFound, e:
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
    except NotFound, e:
      assert "no such queue" in str(e)

  def testDeleteSpecial(self):
    snd = self.ssn.sender("amq.topic; {delete: always}")
    snd.send("asdf")
    try:
      snd.close()
      assert False, "successfully deleted amq.topic"
    except SessionError, e:
      assert e.code == 530
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
    self.conn.detach()
    self.conn.attach()
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

  def testAssert1(self):
    try:
      snd = self.ssn.sender("amq.topic; {assert: always, node: {type: queue}}")
      assert 0, "assertion failed to trigger"
    except AssertionFailed, e:
      pass

  def testAssert2(self):
    snd = self.ssn.sender("amq.topic; {assert: always}")

NOSUCH_Q = "this-queue-should-not-exist"
UNPARSEABLE_ADDR = "name/subject; {bad options"
UNLEXABLE_ADDR = "\0x0\0x1\0x2\0x3"

class AddressErrorTests(Base):

  def setup_connection(self):
    return Connection.establish(self.broker, **self.connection_options())

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
    self.senderErrorTest(None, MalformedAddress)

  def testNoneSource(self):
    self.receiverErrorTest(None, MalformedAddress)

  def testNoTarget(self):
    self.senderErrorTest(NOSUCH_Q, NotFound, lambda e: NOSUCH_Q in str(e))

  def testNoSource(self):
    self.receiverErrorTest(NOSUCH_Q, NotFound, lambda e: NOSUCH_Q in str(e))

  def testUnparseableTarget(self):
    self.senderErrorTest(UNPARSEABLE_ADDR, MalformedAddress,
                         lambda e: "expecting COLON" in str(e))

  def testUnparseableSource(self):
    self.receiverErrorTest(UNPARSEABLE_ADDR, MalformedAddress,
                           lambda e: "expecting COLON" in str(e))

  def testUnlexableTarget(self):
    self.senderErrorTest(UNLEXABLE_ADDR, MalformedAddress,
                         lambda e: "unrecognized characters" in str(e))

  def testUnlexableSource(self):
    self.receiverErrorTest(UNLEXABLE_ADDR, MalformedAddress,
                           lambda e: "unrecognized characters" in str(e))

  def testInvalidMode(self):
    self.receiverErrorTest('name; {mode: "this-is-a-bad-receiver-mode"}',
                           InvalidOption,
                           lambda e: "not in ('browse', 'consume')" in str(e))

SENDER_Q = 'test-sender-q; {create: always, delete: always}'

class SenderTests(Base):

  def setup_connection(self):
    return Connection.establish(self.broker, **self.connection_options())

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

  def testEINTR(self):
    m1 = self.content("testEINTR", 0)
    m2 = self.content("testEINTR", 1)

    self.snd.send(m1, timeout=self.timeout())
    try:
      os.setuid(500)
      assert False, "setuid should fail"
    except:
      pass
    self.snd.send(m2, timeout=self.timeout())
