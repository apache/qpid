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
from qpid.client import Client, Closed
from qpid.queue import Empty
from qpid.datatypes import Message, RangedSet
from qpid.testlib import testrunner, TestBase010

class TxTests(TestBase010):
    """
    Tests for 'methods' on the amqp tx 'class'
    """

    def test_commit(self):
        """
        Test that commited publishes are delivered and commited acks are not re-delivered
        """
        session2 = self.conn.session("worker", 2)
        self.perform_txn_work(session2, "tx-commit-a", "tx-commit-b", "tx-commit-c")
        session2.tx_commit()
        session2.close()

        #use a different session with new subscriptions to ensure
        #there is no redelivery of acked messages:
        session = self.session
        session.tx_select()

        self.subscribe(session, queue="tx-commit-a", destination="qa")
        queue_a = session.incoming("qa")

        self.subscribe(session, queue="tx-commit-b", destination="qb")
        queue_b = session.incoming("qb")

        self.subscribe(session, queue="tx-commit-c", destination="qc")
        queue_c = session.incoming("qc")

        #check results
        for i in range(1, 5):
            msg = queue_c.get(timeout=1)
            self.assertEqual("TxMessage %d" % i, msg.body)
            session.message_accept(RangedSet(msg.id))

        msg = queue_b.get(timeout=1)
        self.assertEqual("TxMessage 6", msg.body)
        session.message_accept(RangedSet(msg.id))

        msg = queue_a.get(timeout=1)
        self.assertEqual("TxMessage 7", msg.body)
        session.message_accept(RangedSet(msg.id))

        for q in [queue_a, queue_b, queue_c]:
            try:
                extra = q.get(timeout=1)
                self.fail("Got unexpected message: " + extra.body)
            except Empty: None

        #cleanup
        session.tx_commit()

    def test_auto_rollback(self):
        """
        Test that a session closed with an open transaction is effectively rolled back
        """
        session2 = self.conn.session("worker", 2)
        queue_a, queue_b, queue_c, ignore = self.perform_txn_work(session2, "tx-autorollback-a", "tx-autorollback-b", "tx-autorollback-c")

        for q in [queue_a, queue_b, queue_c]:
            try:
                extra = q.get(timeout=1)
                self.fail("Got unexpected message: " + extra.body)
            except Empty: None

        session2.close()

        session = self.session
        session.tx_select()

        self.subscribe(session, queue="tx-autorollback-a", destination="qa")
        queue_a = session.incoming("qa")

        self.subscribe(session, queue="tx-autorollback-b", destination="qb")
        queue_b = session.incoming("qb")

        self.subscribe(session, queue="tx-autorollback-c", destination="qc")
        queue_c = session.incoming("qc")

        #check results
        for i in range(1, 5):
            msg = queue_a.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)
            session.message_accept(RangedSet(msg.id))

        msg = queue_b.get(timeout=1)
        self.assertEqual("Message 6", msg.body)
        session.message_accept(RangedSet(msg.id))

        msg = queue_c.get(timeout=1)
        self.assertEqual("Message 7", msg.body)
        session.message_accept(RangedSet(msg.id))

        for q in [queue_a, queue_b, queue_c]:
            try:
                extra = q.get(timeout=1)
                self.fail("Got unexpected message: " + extra.body)
            except Empty: None

        #cleanup
        session.tx_commit()

    def test_rollback(self):
        """
        Test that rolled back publishes are not delivered and rolled back acks are re-delivered
        """
        session = self.session
        queue_a, queue_b, queue_c, consumed = self.perform_txn_work(session, "tx-rollback-a", "tx-rollback-b", "tx-rollback-c")

        for q in [queue_a, queue_b, queue_c]:
            try:
                extra = q.get(timeout=1)
                self.fail("Got unexpected message: " + extra.body)
            except Empty: None

        session.tx_rollback()

        #need to release messages to get them redelivered now:
        session.message_release(consumed)

        #check results
        for i in range(1, 5):
            msg = queue_a.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)
            session.message_accept(RangedSet(msg.id))

        msg = queue_b.get(timeout=1)
        self.assertEqual("Message 6", msg.body)
        session.message_accept(RangedSet(msg.id))

        msg = queue_c.get(timeout=1)
        self.assertEqual("Message 7", msg.body)
        session.message_accept(RangedSet(msg.id))

        for q in [queue_a, queue_b, queue_c]:
            try:
                extra = q.get(timeout=1)
                self.fail("Got unexpected message: " + extra.body)
            except Empty: None

        #cleanup
        session.tx_commit()

    def perform_txn_work(self, session, name_a, name_b, name_c):
        """
        Utility method that does some setup and some work under a transaction. Used for testing both
        commit and rollback
        """
        #setup:
        session.queue_declare(queue=name_a, exclusive=True, auto_delete=True)
        session.queue_declare(queue=name_b, exclusive=True, auto_delete=True)
        session.queue_declare(queue=name_c, exclusive=True, auto_delete=True)

        key = "my_key_" + name_b
        topic = "my_topic_" + name_c 
    
        session.exchange_bind(queue=name_b, exchange="amq.direct", binding_key=key)
        session.exchange_bind(queue=name_c, exchange="amq.topic", binding_key=topic)

        dp = session.delivery_properties(routing_key=name_a)
        for i in range(1, 5):
            mp = session.message_properties(message_id="msg%d" % i)
            session.message_transfer(message=Message(dp, mp, "Message %d" % i))

        dp = session.delivery_properties(routing_key=key)
        mp = session.message_properties(message_id="msg6")
        session.message_transfer(destination="amq.direct", message=Message(dp, mp, "Message 6"))

        dp = session.delivery_properties(routing_key=topic)
        mp = session.message_properties(message_id="msg7")
        session.message_transfer(destination="amq.topic", message=Message(dp, mp, "Message 7"))

        session.tx_select()

        #consume and ack messages
        acked = RangedSet()
        self.subscribe(session, queue=name_a, destination="sub_a")
        queue_a = session.incoming("sub_a")
        for i in range(1, 5):
            msg = queue_a.get(timeout=1)
            acked.add(msg.id)
            self.assertEqual("Message %d" % i, msg.body)

        self.subscribe(session, queue=name_b, destination="sub_b")
        queue_b = session.incoming("sub_b")
        msg = queue_b.get(timeout=1)
        self.assertEqual("Message 6", msg.body)
        acked.add(msg.id)

        sub_c = self.subscribe(session, queue=name_c, destination="sub_c")
        queue_c = session.incoming("sub_c")
        msg = queue_c.get(timeout=1)
        self.assertEqual("Message 7", msg.body)
        acked.add(msg.id)

        session.message_accept(acked)

        dp = session.delivery_properties(routing_key=topic)
        #publish messages
        for i in range(1, 5):
            mp = session.message_properties(message_id="tx-msg%d" % i)
            session.message_transfer(destination="amq.topic", message=Message(dp, mp, "TxMessage %d" % i))

        dp = session.delivery_properties(routing_key=key)
        mp = session.message_properties(message_id="tx-msg6")
        session.message_transfer(destination="amq.direct", message=Message(dp, mp, "TxMessage 6"))
        
        dp = session.delivery_properties(routing_key=name_a)
        mp = session.message_properties(message_id="tx-msg7")
        session.message_transfer(message=Message(dp, mp, "TxMessage 7"))
        return queue_a, queue_b, queue_c, acked

    def subscribe(self, session=None, **keys):
        session = session or self.session
        consumer_tag = keys["destination"]
        session.message_subscribe(**keys)
        session.message_flow(destination=consumer_tag, unit=0, value=0xFFFFFFFF)
        session.message_flow(destination=consumer_tag, unit=1, value=0xFFFFFFFF)

    def complete(self, session, msg):
        session.receiver._completed.add(msg.id)#TODO: this may be done automatically
        session.channel.session_completed(session.receiver._completed)

