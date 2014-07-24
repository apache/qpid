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
from qpidtoollibs import BrokerAgent
from time import sleep

#
# Broker tests using the new messaging API
#

class GeneralTests(Base):
    """
    Tests of the API and broker via the new API.
    """

    def assertEqual(self, left, right, text=None):
        if not left == right:
            print "assertEqual failure: %r != %r" % (left, right)
            if text:
                print "  %r" % text
            assert None

    def fail(self, text=None):
        if text:
            print "Fail: %r" % text
        assert None

    def setup_connection(self):
        return Connection.establish(self.broker, **self.connection_options())

    def setup_session(self):
        return self.conn.session()

    def test_not_found(self):
        ssn = self.setup_session()
        try:
            ssn.receiver("does-not-exist")
            self.fail("Expected non-existent node to cause NotFound exception")
        except NotFound, e: None

    def test_qpid_3481_acquired_to_alt_exchange(self):
        """
        Verify that acquired messages are routed to the alternate when the queue is deleted.
        """
        sess1 = self.setup_session()
        sess2 = self.setup_session()

        tx = sess1.sender("amq.direct/key")
        rx_main = sess1.receiver("amq.direct/key;{link:{reliability:at-least-once,x-declare:{alternate-exchange:'amq.fanout'}}}")
        rx_alt  = sess2.receiver("amq.fanout")
        rx_alt.capacity = 10

        tx.send("DATA")
        tx.send("DATA")
        tx.send("DATA")
        tx.send("DATA")
        tx.send("DATA")

        msg = rx_main.fetch()
        msg = rx_main.fetch()
        msg = rx_main.fetch()

        self.assertEqual(rx_alt.available(), 0, "No messages should have been routed to the alt_exchange")

        sess1.close()
        sleep(1)
        self.assertEqual(rx_alt.available(), 5, "All 5 messages should have been routed to the alt_exchange")

        sess2.close()

    def test_qpid_3481_acquired_to_alt_exchange_2_consumers(self):
        """
        Verify that acquired messages are routed to the alternate when the queue is deleted.
        """
        sess1 = self.setup_session()
        sess2 = self.setup_session()
        sess3 = self.setup_session()
        sess4 = self.setup_session()

        tx = sess1.sender("test_acquired;{create:always,delete:always,node:{x-declare:{alternate-exchange:'amq.fanout'}}}")
        rx_main1 = sess2.receiver("test_acquired")
        rx_main2 = sess3.receiver("test_acquired")
        rx_alt   = sess4.receiver("amq.fanout")
        rx_alt.capacity = 10

        tx.send("DATA")
        tx.send("DATA")
        tx.send("DATA")
        tx.send("DATA")
        tx.send("DATA")

        msg = rx_main1.fetch()
        msg = rx_main1.fetch()
        msg = rx_main1.fetch()

        self.assertEqual(rx_alt.available(), 0, "No messages should have been routed to the alt_exchange")

        # Close sess1; This will cause the queue to be deleted and all its messages (including those acquired) to be reouted to the alternate exchange
        sess1.close()
        sleep(1)
        self.assertEqual(rx_alt.available(), 5, "All the messages should have been routed to the alt_exchange")

        # Close sess2; This will cause the acquired messages to be requeued and routed to the alternate
        sess2.close()
        for i in range(5):
            try:
                m = rx_alt.fetch(0)
            except:
                self.fail("failed to receive all 5 messages via alternate exchange")

        sess3.close()
        self.assertEqual(rx_alt.available(), 0, "No further messages should be received via the alternate exchange")

        sess4.close()

    def test_next_receiver(self):
        keys = ["a", "b", "c"]
        receivers = [self.ssn.receiver("amq.direct/%s" % k) for k in keys]
        for r in receivers:
            r.capacity = 10

        snd = self.ssn.sender("amq.direct")

        for k in keys:
            snd.send(Message(subject=k, content=k))

        expected = keys
        while len(expected):
            rcv = self.ssn.next_receiver(timeout=self.delay())
            c = rcv.fetch().content
            assert c in expected
            expected.remove(c)
        self.ssn.acknowledge()

    def test_nolocal_rerouted(self):
        conn2 = Connection.establish(self.broker, **self.connection_options())
        ssn2 = conn2.session()

        s1 = self.ssn.sender("holding_q; {create:always, delete:always, node:{x-declare:{alternate-exchange:'amq.fanout'}}}");
        s2 = ssn2.sender("holding_q");

        s2.send(Message("a"));
        s1.send(Message("b"));
        s2.send(Message("c"));

        r = self.ssn.receiver("amq.fanout; {link:{x-declare:{arguments:{'no-local':True}}}}")

        # close connection of one of the publishers
        conn2.close()

        # close sender which should cause the orphaned messages on
        # holding_q to be rerouted through alternate exchange onto the
        # subscription queue of the receiver above
        s1.close()

        received = []
        try:
            while True:
                received.append(r.fetch(0).content)
        except Empty: pass
        self.assertEqual(received, ["a", "c"])

    def _node_disambiguation_test(self, e, q, ambiguous_send=False):
        s1 = self.ssn.sender("ambiguous; {node:{type:topic}}");
        s2 = self.ssn.sender("ambiguous; {node:{type:queue}}");
        s1.send(Message("a"))
        s2.send(Message("b"))
        if ambiguous_send:
            # pure python client defaults to using the queue when the
            # node name is ambiguous and no type is specified; the
            # swigged version treats this as an error
            s3 = self.ssn.sender("ambiguous");
            s3.send(Message("c"))
        self.assertEqual(e.fetch().content, "a")
        self.assertEqual(q.fetch().content, "b")
        if ambiguous_send:
            self.assertEqual(q.fetch().content, "c")
        for r in [e, q]:
            try:
                m = r.fetch(timeout=0)
                self.fail("Found unexpected message %s")
            except Empty: pass

    def _node_disambiguation_precreated(self, ambiguous_send):
        agent = BrokerAgent(self.conn)
        agent.addExchange("fanout", "ambiguous")
        agent.addQueue("ambiguous")
        try:
            r1 = self.ssn.receiver("ambiguous; {node:{type:topic}}")
            r2 = self.ssn.receiver("ambiguous; {node:{type:queue}}")
            self._node_disambiguation_test(r1, r2, ambiguous_send=ambiguous_send)
        finally:
            agent.delExchange("ambiguous")
            agent.delQueue("ambiguous", False, False)

    def test_node_disambiguation_1(self):
        self._node_disambiguation_precreated(False)

    def test_node_disambiguation_2(self):
        self._node_disambiguation_precreated(True)

    def test_ambiguous_create_1(self):
        #create queue first, then exchange
        r1 = self.ssn.receiver("ambiguous; {create:receiver, node:{type:queue}}")
        r2 = self.ssn.receiver("ambiguous; {create:receiver, node:{type:topic}}")
        agent = BrokerAgent(self.conn)
        exchange = agent.getExchange("ambiguous")
        queue = agent.getQueue("ambiguous")
        try:
            assert(exchange)
            assert(queue)
            self._node_disambiguation_test(r2, r1)
        finally:
            if exchange: agent.delExchange("ambiguous")
            if queue: agent.delQueue("ambiguous", False, False)

    def test_ambiguous_create_2(self):
        #create exchange first, then queue
        r1 = self.ssn.receiver("ambiguous; {create:receiver, node:{type:topic}}")
        r2 = self.ssn.receiver("ambiguous; {create:receiver, node:{type:queue}}")
        agent = BrokerAgent(self.conn)
        exchange = agent.getExchange("ambiguous")
        queue = agent.getQueue("ambiguous")
        try:
            assert(exchange)
            assert(queue)
            self._node_disambiguation_test(r1, r2)
        finally:
            if exchange: agent.delExchange("ambiguous")
            if queue: agent.delQueue("ambiguous", False, False)

    def test_ambiguous_delete_1(self):
        agent = BrokerAgent(self.conn)
        agent.addExchange("fanout", "ambiguous")
        agent.addQueue("ambiguous")
        self.ssn.receiver("ambiguous; {delete:receiver, node:{type:topic}}").close()
        exchange = agent.getExchange("ambiguous")
        queue = agent.getQueue("ambiguous")
        try:
            assert(not exchange)
            assert(queue)
        finally:
            if exchange: agent.delExchange("ambiguous")
            if queue: agent.delQueue("ambiguous", False, False)

    def test_ambiguous_delete_2(self):
        agent = BrokerAgent(self.conn)
        agent.addExchange("fanout", "ambiguous")
        agent.addQueue("ambiguous")
        self.ssn.receiver("ambiguous; {delete:receiver, node:{type:queue}}").close()
        exchange = agent.getExchange("ambiguous")
        queue = agent.getQueue("ambiguous")
        try:
            assert(exchange)
            assert(not queue)
        finally:
            if exchange: agent.delExchange("ambiguous")
            if queue: agent.delQueue("ambiguous", False, False)


class SequenceNumberTests(Base):
    """
    Tests of ring queue sequence number
    """

    def fail(self, text=None):
        if text:
            print "Fail: %r" % text
        assert None

    def setup_connection(self):
        return Connection.establish(self.broker, **self.connection_options())

    def setup_session(self):
        return self.conn.session()

    def setup_sender(self, name="ring-sequence-queue", key="qpid.queue_msg_sequence"):
        addr = "%s; {create:sender, node: {x-declare: {auto-delete: True, arguments: {'qpid.queue_msg_sequence':'%s', 'qpid.policy_type':'ring', 'qpid.max_count':4}}}}"  % (name, key)
        sender = self.ssn.sender(addr)
        return sender

    def test_create_sequence_queue(self):
        """
        Test a queue with sequencing can be created
        """

        #setup, declare a queue
        try:
            sender = self.setup_sender()
        except:
            self.fail("Unable to create ring queue with sequencing enabled")

    def test_get_sequence_number(self):
        """
        Test retrieving sequence number for queues 
        """

        key = "k"
        sender = self.setup_sender("ring-sequence-queue2", key=key)

        # send and receive 1 message and test the sequence number
        msg = Message()
        sender.send(msg)

        receiver = self.ssn.receiver("ring-sequence-queue2")
        msg = receiver.fetch(1)
        try:
            seqNo = msg.properties[key]
            if int(seqNo) != 1:
                txt = "Unexpected sequence number. Should be 1. Received (%s)" % seqNo
                self.fail(txt)
        except:
            txt = "Unable to get key (%s) from message properties" % key
            self.fail(txt)
        receiver.close()

    def test_sequence_number_gap(self):
        """
        Test that sequence number for ring queues shows gaps when queue
        messages are overwritten
        """
        key = "qpid.seq"
        sender = self.setup_sender("ring-sequence-queue3", key=key)
        receiver = self.ssn.receiver("ring-sequence-queue3")

        msg = Message()
        sender.send(msg)
        msg = receiver.fetch(1)

        # send 5 more messages to overflow the queue
        for i in range(5):
            sender.send(msg)

        msg = receiver.fetch(1)
        seqNo = msg.properties[key]
        if int(seqNo) != 3:
            txt = "Unexpected sequence number. Should be 3. Received (%s)" % seqNo
            self.fail(txt)
        receiver.close()

