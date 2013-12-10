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
from qpid.tests.messaging import Base
from time import sleep
from qpidtoollibs.broker import BrokerAgent

#
# Tests the Broker's statistics reporting
#

class BrokerStatsTests(Base):
    """
    Tests of the broker's statistics
    """

    def assertEqual(self, left, right, text=None):
        if not left == right:
            print "assertEqual failure: %r != %r" % (left, right)
            if text:
                print "  %r" % text
            assert None

    def failUnless(self, value, text=None):
        if value:
            return
        print "failUnless failure",
        if text:
            print ": %r" % text
        else:
            print
        assert None

    def fail(self, text=None):
        if text:
            print "Fail: %r" % text
        assert None

    def setup_connection(self):
        return Connection.establish(self.broker, **self.connection_options())

    def setup_session(self, tx=False):
        return self.conn.session(transactional=tx)

    def setup_access(self):
        return BrokerAgent(self.conn)

    def test_exchange_stats(self):
        agent = self.setup_access()
        start_broker = agent.getBroker()

        agent.addExchange("direct", "stats-test-exchange")
        try:
            sess = self.setup_session()
            tx_a = sess.sender("stats-test-exchange/a")
            tx_b = sess.sender("stats-test-exchange/b")
            rx_a = sess.receiver("stats-test-exchange/a")

            exchange = agent.getExchange("stats-test-exchange")
            self.failUnless(exchange, "expected a valid exchange object")
            self.assertEqual(exchange.msgReceives, 0, "msgReceives")
            self.assertEqual(exchange.msgDrops, 0, "msgDrops")
            self.assertEqual(exchange.msgRoutes, 0, "msgRoutes")
            self.assertEqual(exchange.byteReceives, 0, "byteReceives")
            self.assertEqual(exchange.byteDrops, 0, "byteDrops")
            self.assertEqual(exchange.byteRoutes, 0, "byteRoutes")

            tx_a.send("0123456789")
            tx_b.send("01234567890123456789")
            tx_a.send("012345678901234567890123456789")
            tx_b.send("0123456789012345678901234567890123456789")

            overhead = 63 #overhead added to message from headers
            exchange.update()
            self.assertEqual(exchange.msgReceives, 4, "msgReceives")
            self.assertEqual(exchange.msgDrops, 2, "msgDrops")
            self.assertEqual(exchange.msgRoutes, 2, "msgRoutes")
            self.assertEqual(exchange.byteReceives, 100+(4*overhead), "byteReceives")
            self.assertEqual(exchange.byteDrops, 60+(2*overhead), "byteDrops")
            self.assertEqual(exchange.byteRoutes, 40+(2*overhead), "byteRoutes")
        finally:
            agent.delExchange("stats-test-exchange")

    def test_enqueues_dequeues(self):
        agent = self.setup_access()
        start_broker = agent.getBroker()

        sess = self.setup_session()
        tx = sess.sender("enqueue_test;{create:always,delete:always}")
        rx = sess.receiver("enqueue_test")

        queue = agent.getQueue("enqueue_test")
        self.failUnless(queue, "expected a valid queue object")
        self.assertEqual(queue.msgTotalEnqueues, 0, "msgTotalEnqueues")
        self.assertEqual(queue.byteTotalEnqueues, 0, "byteTotalEnqueues")
        self.assertEqual(queue.msgTotalDequeues, 0, "msgTotalDequeues")
        self.assertEqual(queue.byteTotalDequeues, 0, "byteTotalDequeues")
        self.assertEqual(queue.msgDepth, 0, "msgDepth")
        self.assertEqual(queue.byteDepth, 0, "byteDepth")

        tx.send("0123456789")
        tx.send("01234567890123456789")
        tx.send("012345678901234567890123456789")
        tx.send("0123456789012345678901234567890123456789")
        overhead = 38 #overhead added to message from headers

        queue.update()
        self.assertEqual(queue.msgTotalEnqueues, 4, "msgTotalEnqueues")
        self.assertEqual(queue.byteTotalEnqueues, 100+(4*overhead), "byteTotalEnqueues")
        self.assertEqual(queue.msgTotalDequeues, 0, "msgTotalDequeues")
        self.assertEqual(queue.byteTotalDequeues, 0, "byteTotalDequeues")
        self.assertEqual(queue.msgDepth, 4, "msgDepth")
        self.assertEqual(queue.byteDepth, 100+(4*overhead), "byteDepth")

        now_broker = agent.getBroker()
        self.failUnless((now_broker.msgTotalEnqueues - start_broker.msgTotalEnqueues) >= 4, "broker msgTotalEnqueues")
        self.failUnless((now_broker.byteTotalEnqueues - start_broker.byteTotalEnqueues) >= 100, "broker byteTotalEnqueues")

        m = rx.fetch()
        m = rx.fetch()
        sess.acknowledge()

        queue.update()
        self.assertEqual(queue.msgTotalEnqueues, 4, "msgTotalEnqueues")
        self.assertEqual(queue.byteTotalEnqueues, 100+(4*overhead), "byteTotalEnqueues")
        self.assertEqual(queue.msgTotalDequeues, 2, "msgTotalDequeues")
        self.assertEqual(queue.byteTotalDequeues, 30+(2*overhead), "byteTotalDequeues")
        self.assertEqual(queue.msgDepth, 2, "msgDepth")
        self.assertEqual(queue.byteDepth, 70+(2*overhead), "byteDepth")

        now_broker = agent.getBroker()
        self.failUnless((now_broker.msgTotalDequeues - start_broker.msgTotalDequeues) >= 2, "broker msgTotalDequeues")
        self.failUnless((now_broker.byteTotalDequeues - start_broker.byteTotalDequeues) >= 30, "broker byteTotalDequeues")
        
        sess.close()

        now_broker = agent.getBroker()
        self.assertEqual(now_broker.abandoned - start_broker.abandoned, 2, "expect 2 abandoned messages")
        self.assertEqual(now_broker.msgDepth, start_broker.msgDepth, "expect broker message depth to be unchanged")
        self.assertEqual(now_broker.byteDepth, start_broker.byteDepth, "expect broker byte depth to be unchanged")


    def test_transactional_enqueues_dequeues(self):
        agent = self.setup_access()
        start_broker = agent.getBroker()

        sess = self.setup_session(True)
        tx = sess.sender("tx_enqueue_test;{create:always,delete:always}")

        tx.send("0123456789")
        tx.send("0123456789")
        tx.send("0123456789")
        tx.send("0123456789")
        overhead = 41 #overhead added to message from headers

        queue = agent.getQueue("tx_enqueue_test")
        self.failUnless(queue, "expected a valid queue object")
        self.assertEqual(queue.msgTotalEnqueues,  0, "msgTotalEnqueues pre-tx-commit")
        self.assertEqual(queue.byteTotalEnqueues, 0, "byteTotalEnqueues pre-tx-commit")
        self.assertEqual(queue.msgTxnEnqueues,    0, "msgTxnEnqueues pre-tx-commit")
        self.assertEqual(queue.byteTxnEnqueues,   0, "byteTxnEnqueues pre-tx-commit")
        self.assertEqual(queue.msgTotalDequeues,  0, "msgTotalDequeues pre-tx-commit")
        self.assertEqual(queue.byteTotalDequeues, 0, "byteTotalDequeues pre-tx-commit")
        self.assertEqual(queue.msgTxnDequeues,    0, "msgTxnDequeues pre-tx-commit")
        self.assertEqual(queue.byteTxnDequeues,   0, "byteTxnDequeues pre-tx-commit")

        sess.commit()
        queue.update()
        self.assertEqual(queue.msgTotalEnqueues,   4, "msgTotalEnqueues post-tx-commit")
        self.assertEqual(queue.byteTotalEnqueues, 40+(4*overhead), "byteTotalEnqueues post-tx-commit")
        self.assertEqual(queue.msgTxnEnqueues,     4, "msgTxnEnqueues post-tx-commit")
        self.assertEqual(queue.byteTxnEnqueues,   40+(4*overhead), "byteTxnEnqueues post-tx-commit")
        self.assertEqual(queue.msgTotalDequeues,   0, "msgTotalDequeues post-tx-commit")
        self.assertEqual(queue.byteTotalDequeues,  0, "byteTotalDequeues post-tx-commit")
        self.assertEqual(queue.msgTxnDequeues,     0, "msgTxnDequeues post-tx-commit")
        self.assertEqual(queue.byteTxnDequeues,    0, "byteTxnDequeues post-tx-commit")

        sess2 = self.setup_session(True)
        rx = sess2.receiver("tx_enqueue_test")

        m = rx.fetch()
        m = rx.fetch()
        m = rx.fetch()
        m = rx.fetch()

        queue.update()
        self.assertEqual(queue.msgTotalEnqueues,   4, "msgTotalEnqueues pre-rx-commit")
        self.assertEqual(queue.byteTotalEnqueues, 40+(4*overhead), "byteTotalEnqueues pre-rx-commit")
        self.assertEqual(queue.msgTxnEnqueues,     4, "msgTxnEnqueues pre-rx-commit")
        self.assertEqual(queue.byteTxnEnqueues,   40+(4*overhead), "byteTxnEnqueues pre-rx-commit")
        self.assertEqual(queue.msgTotalDequeues,   0, "msgTotalDequeues pre-rx-commit")
        self.assertEqual(queue.byteTotalDequeues,  0, "byteTotalDequeues pre-rx-commit")
        self.assertEqual(queue.msgTxnDequeues,     0, "msgTxnDequeues pre-rx-commit")
        self.assertEqual(queue.byteTxnDequeues,    0, "byteTxnDequeues pre-rx-commit")

        sess2.acknowledge()
        sess2.commit()

        queue.update()
        self.assertEqual(queue.msgTotalEnqueues,   4, "msgTotalEnqueues post-rx-commit")
        self.assertEqual(queue.byteTotalEnqueues, 40+(4*overhead), "byteTotalEnqueues post-rx-commit")
        self.assertEqual(queue.msgTxnEnqueues,     4, "msgTxnEnqueues post-rx-commit")
        self.assertEqual(queue.byteTxnEnqueues,   40+(4*overhead), "byteTxnEnqueues post-rx-commit")
        self.assertEqual(queue.msgTotalDequeues,   4, "msgTotalDequeues post-rx-commit")
        self.assertEqual(queue.byteTotalDequeues, 40+(4*overhead), "byteTotalDequeues post-rx-commit")
        self.assertEqual(queue.msgTxnDequeues,     4, "msgTxnDequeues post-rx-commit")
        self.assertEqual(queue.byteTxnDequeues,   40+(4*overhead), "byteTxnDequeues post-rx-commit")

        sess.close()
        sess2.close()

        now_broker = agent.getBroker()
        self.assertEqual(now_broker.msgTxnEnqueues  - start_broker.msgTxnEnqueues,   4, "broker msgTxnEnqueues")
        self.assertEqual(now_broker.byteTxnEnqueues - start_broker.byteTxnEnqueues, 40+(4*overhead), "broker byteTxnEnqueues")
        self.assertEqual(now_broker.msgTxnDequeues  - start_broker.msgTxnDequeues,   4, "broker msgTxnDequeues")
        self.assertEqual(now_broker.byteTxnDequeues - start_broker.byteTxnDequeues, 40+(4*overhead), "broker byteTxnDequeues")


    def test_discards_no_route(self):
        agent = self.setup_access()
        start_broker = agent.getBroker()

        sess = self.setup_session()
        tx = sess.sender("amq.topic/non.existing.key")
        tx.send("NO_ROUTE")
        tx.send("NO_ROUTE")
        tx.send("NO_ROUTE")
        tx.send("NO_ROUTE")
        tx.send("NO_ROUTE")

        now_broker = agent.getBroker()

        self.failUnless((now_broker.discardsNoRoute - start_broker.discardsNoRoute) >= 5, "Expect at least 5 no-routes")

        sess.close()


    def test_abandoned_alt(self):
        agent = self.setup_access()
        start_broker = agent.getBroker()

        sess = self.setup_session()
        tx = sess.sender("abandon_alt;{create:always,delete:always,node:{x-declare:{alternate-exchange:'amq.fanout'}}}")
        rx = sess.receiver("abandon_alt")
        rx.capacity = 2

        tx.send("ABANDON_ALT")
        tx.send("ABANDON_ALT")
        tx.send("ABANDON_ALT")
        tx.send("ABANDON_ALT")
        tx.send("ABANDON_ALT")

        rx.fetch()

        sess.close()
        now_broker = agent.getBroker()
        self.assertEqual(now_broker.abandonedViaAlt - start_broker.abandonedViaAlt, 5, "Expect 5 abandonedViaAlt")
        self.assertEqual(now_broker.abandoned - start_broker.abandoned, 0, "Expect 0 abandoned")


    def test_discards_ttl(self):
        agent = self.setup_access()
        start_broker = agent.getBroker()

        sess = self.setup_session()
        tx = sess.sender("discards_ttl;{create:always,delete:always}")
        msg = Message("TTL")
        msg.ttl = 1

        tx.send(msg)
        tx.send(msg)
        tx.send(msg)
        tx.send(msg)
        tx.send(msg)
        tx.send(msg)

        sleep(2)

        rx = sess.receiver("discards_ttl")
        try:
            rx.fetch(0)
        except:
            pass

        now_broker = agent.getBroker()
        queue = agent.getQueue("discards_ttl")

        self.failUnless(queue, "expected a valid queue object")
        self.assertEqual(queue.discardsTtl, 6, "expect 6 TTL discards on queue")
        self.assertEqual(now_broker.discardsTtl - start_broker.discardsTtl, 6, "expect 6 TTL discards on broker")
        self.assertEqual(queue.msgTotalDequeues, 6, "expect 6 total dequeues on queue")

        sess.close()


    def test_discards_limit_overflow(self):
        agent = self.setup_access()
        start_broker = agent.getBroker()

        sess = self.setup_session()
        tx = sess.sender("discards_limit;{create:always,node:{x-declare:{arguments:{'qpid.max_count':3,'qpid.flow_stop_count':0}}}}")
        tx.send("LIMIT")
        tx.send("LIMIT")
        tx.send("LIMIT")
        try:
            tx.send("LIMIT")
            self.fail("expected to fail sending 4th message")
        except:
            pass

        now_broker = agent.getBroker()
        queue = agent.getQueue("discards_limit")

        self.failUnless(queue, "expected a valid queue object")
        self.assertEqual(queue.discardsOverflow, 1, "expect 1 overflow discard on queue")
        self.assertEqual(now_broker.discardsOverflow - start_broker.discardsOverflow, 1, "expect 1 overflow discard on broker")

        ##
        ## Shut down and restart the connection to clear the error condition.
        ##
        try:
            self.conn.close(timeout=.1)
        except:
            pass
        self.conn = self.setup_connection()

        ##
        ## Re-create the session to delete the queue.
        ##
        sess = self.setup_session()
        tx = sess.sender("discards_limit;{create:always,delete:always}")
        sess.close()


    def test_discards_ring_overflow(self):
        agent = self.setup_access()
        start_broker = agent.getBroker()

        sess = self.setup_session()
        tx = sess.sender("discards_ring;{create:always,delete:always,node:{x-declare:{arguments:{'qpid.max_count':3,'qpid.flow_stop_count':0,'qpid.policy_type':ring}}}}")

        tx.send("RING")
        tx.send("RING")
        tx.send("RING")
        tx.send("RING")
        tx.send("RING")

        now_broker = agent.getBroker()
        queue = agent.getQueue("discards_ring")

        self.failUnless(queue, "expected a valid queue object")
        self.assertEqual(queue.discardsRing, 2, "expect 2 ring discards on queue")
        self.assertEqual(now_broker.discardsRing - start_broker.discardsRing, 2, "expect 2 ring discards on broker")
        self.assertEqual(queue.msgTotalDequeues, 2, "expect 2 total dequeues on queue")

        sess.close()


    def test_discards_lvq_replace(self):
        agent = self.setup_access()
        start_broker = agent.getBroker()

        sess = self.setup_session()
        tx = sess.sender("discards_lvq;{create:always,delete:always,node:{x-declare:{arguments:{'qpid.max_count':3,'qpid.flow_stop_count':0,'qpid.last_value_queue_key':key}}}}")
        msgA = Message("LVQ_A")
        msgA.properties['key'] = 'AAA'
        msgB = Message("LVQ_B")
        msgB.properties['key'] = 'BBB'

        tx.send(msgA)
        tx.send(msgB)
        tx.send(msgA)
        tx.send(msgA)
        tx.send(msgB)

        now_broker = agent.getBroker()
        queue = agent.getQueue("discards_lvq")

        self.failUnless(queue, "expected a valid queue object")
        self.assertEqual(queue.discardsLvq, 3, "expect 3 lvq discards on queue")
        self.assertEqual(now_broker.discardsLvq - start_broker.discardsLvq, 3, "expect 3 lvq discards on broker")
        self.assertEqual(queue.msgTotalDequeues, 3, "expect 3 total dequeues on queue")

        sess.close()


    def test_discards_reject(self):
        agent = self.setup_access()
        start_broker = agent.getBroker()

        sess = self.setup_session()
        tx = sess.sender("discards_reject;{create:always,delete:always}")
        tx.send("REJECT")
        tx.send("REJECT")
        tx.send("REJECT")

        rx = sess.receiver("discards_reject")
        m = rx.fetch()
        sess.acknowledge()
        m1 = rx.fetch()
        m2 = rx.fetch()
        sess.acknowledge(m1, Disposition(REJECTED))
        sess.acknowledge(m2, Disposition(REJECTED))

        now_broker = agent.getBroker()
        queue = agent.getQueue("discards_reject")

        self.failUnless(queue, "expected a valid queue object")
        self.assertEqual(queue.discardsSubscriber, 2, "expect 2 reject discards on queue")
        self.assertEqual(now_broker.discardsSubscriber - start_broker.discardsSubscriber, 2, "expect 2 reject discards on broker")
        self.assertEqual(queue.msgTotalDequeues, 3, "expect 3 total dequeues on queue")

        sess.close()


    def test_message_release(self):
        agent = self.setup_access()
        start_broker = agent.getBroker()

        sess = self.setup_session()
        tx = sess.sender("message_release;{create:always,delete:always}")
        tx.send("RELEASE")
        tx.send("RELEASE")
        tx.send("RELEASE")
        tx.send("RELEASE")
        tx.send("RELEASE")

        rx = sess.receiver("message_release")
        m1 = rx.fetch()
        m2 = rx.fetch()
        sess.acknowledge(m1, Disposition(RELEASED))
        sess.acknowledge(m2, Disposition(RELEASED))

        now_broker = agent.getBroker()
        queue = agent.getQueue("message_release")

        self.failUnless(queue, "expected a valid queue object")
        self.assertEqual(queue.acquires, 2, "expect 2 acquires on queue")
        self.failUnless(now_broker.acquires - start_broker.acquires >= 2, "expect at least 2 acquires on broker")
        self.assertEqual(queue.msgTotalDequeues, 0, "expect 0 total dequeues on queue")

        self.assertEqual(queue.releases, 2, "expect 2 releases on queue")
        self.failUnless(now_broker.releases - start_broker.releases >= 2, "expect at least 2 releases on broker")

        sess.close()


    def test_discards_purge(self):
        agent = self.setup_access()
        start_broker = agent.getBroker()

        sess = self.setup_session()
        tx = sess.sender("discards_purge;{create:always,delete:always}")
        tx.send("PURGE")
        tx.send("PURGE")
        tx.send("PURGE")
        tx.send("PURGE")
        tx.send("PURGE")

        queue = agent.getQueue("discards_purge")
        self.failUnless(queue, "expected a valid queue object")

        queue.purge(3)
        queue.update()

        now_broker = agent.getBroker()
        self.assertEqual(queue.discardsPurge, 3, "expect 3 purge discards on queue")
        self.assertEqual(now_broker.discardsPurge - start_broker.discardsPurge, 3, "expect 3 purge discards on broker")
        self.assertEqual(queue.msgTotalDequeues, 3, "expect 3 total dequeues on queue")

        sess.close()


    def test_reroutes(self):
        agent = self.setup_access()
        start_broker = agent.getBroker()

        sess = self.setup_session()
        tx = sess.sender("reroute;{create:always,delete:always}")
        tx.send("REROUTE")
        tx.send("REROUTE")
        tx.send("REROUTE")
        tx.send("REROUTE")
        tx.send("REROUTE")
        tx.send("REROUTE")
        tx.send("REROUTE")
        tx.send("REROUTE")

        queue = agent.getQueue("reroute")
        self.failUnless(queue, "expected a valid queue object")

        queue.reroute(5, False, 'amq.fanout')
        queue.update()

        now_broker = agent.getBroker()
        self.assertEqual(queue.reroutes, 5, "expect 5 reroutes on queue")
        self.assertEqual(now_broker.reroutes - start_broker.reroutes, 5, "expect 5 reroutes on broker")
        self.assertEqual(queue.msgTotalDequeues, 5, "expect 5 total dequeues on queue")

        sess.close()


