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
from qpid.content import Content
from qpid.testlib import testrunner, TestBase

class TxTests(TestBase):
    """
    Tests for 'methods' on the amqp tx 'class'
    """

    def test_commit(self):
        """
        Test that commited publishes are delivered and commited acks are not re-delivered
        """
        channel = self.channel
        queue_a, queue_b, queue_c = self.perform_txn_work(channel, "tx-commit-a", "tx-commit-b", "tx-commit-c")
        channel.tx_commit()

        #check results
        for i in range(1, 5):
            msg = queue_c.get(timeout=1)
            self.assertEqual("TxMessage %d" % i, msg.body)
            msg.ok()

        msg = queue_b.get(timeout=1)
        self.assertEqual("TxMessage 6", msg.body)
        msg.ok()

        msg = queue_a.get(timeout=1)
        self.assertEqual("TxMessage 7", msg.body)
        msg.ok()

        for q in [queue_a, queue_b, queue_c]:
            try:
                extra = q.get(timeout=1)
                self.fail("Got unexpected message: " + extra.body)
            except Empty: None

        #cleanup
        channel.tx_commit()

    def test_auto_rollback(self):
        """
        Test that a channel closed with an open transaction is effectively rolled back
        """
        channel = self.channel
        queue_a, queue_b, queue_c = self.perform_txn_work(channel, "tx-autorollback-a", "tx-autorollback-b", "tx-autorollback-c")

        for q in [queue_a, queue_b, queue_c]:
            try:
                extra = q.get(timeout=1)
                self.fail("Got unexpected message: " + extra.body)
            except Empty: None

        channel.tx_rollback()

        #check results
        for i in range(1, 5):
            msg = queue_a.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)
            msg.ok()

        msg = queue_b.get(timeout=1)
        self.assertEqual("Message 6", msg.body)
        msg.ok()

        msg = queue_c.get(timeout=1)
        self.assertEqual("Message 7", msg.body)
        msg.ok()

        for q in [queue_a, queue_b, queue_c]:
            try:
                extra = q.get(timeout=1)
                self.fail("Got unexpected message: " + extra.body)
            except Empty: None

        #cleanup
        channel.tx_commit()

    def test_rollback(self):
        """
        Test that rolled back publishes are not delivered and rolled back acks are re-delivered
        """
        channel = self.channel
        queue_a, queue_b, queue_c = self.perform_txn_work(channel, "tx-rollback-a", "tx-rollback-b", "tx-rollback-c")

        for q in [queue_a, queue_b, queue_c]:
            try:
                extra = q.get(timeout=1)
                self.fail("Got unexpected message: " + extra.body)
            except Empty: None

        channel.tx_rollback()

        #check results
        for i in range(1, 5):
            msg = queue_a.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)
            msg.ok()

        msg = queue_b.get(timeout=1)
        self.assertEqual("Message 6", msg.body)
        msg.ok()

        msg = queue_c.get(timeout=1)
        self.assertEqual("Message 7", msg.body)
        msg.ok()

        for q in [queue_a, queue_b, queue_c]:
            try:
                extra = q.get(timeout=1)
                self.fail("Got unexpected message: " + extra.body)
            except Empty: None

        #cleanup
        channel.tx_commit()

    def perform_txn_work(self, channel, name_a, name_b, name_c):
        """
        Utility method that does some setup and some work under a transaction. Used for testing both
        commit and rollback
        """
        #setup:
        channel.queue_declare(queue=name_a, exclusive=True)
        channel.queue_declare(queue=name_b, exclusive=True)
        channel.queue_declare(queue=name_c, exclusive=True)

        key = "my_key_" + name_b
        topic = "my_topic_" + name_c 
    
        channel.queue_bind(queue=name_b, exchange="amq.direct", routing_key=key)
        channel.queue_bind(queue=name_c, exchange="amq.topic", routing_key=topic)

        for i in range(1, 5):
            channel.message_transfer(routing_key=name_a, body="Message %d" % i)

        channel.message_transfer(routing_key=key, destination="amq.direct", body="Message 6")
        channel.message_transfer(routing_key=topic, destination="amq.topic", body="Message 7")

        channel.tx_select()

        #consume and ack messages
        channel.message_consume(queue=name_a, destination="sub_a", no_ack=False)
        queue_a = self.client.queue("sub_a")
        for i in range(1, 5):
            msg = queue_a.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)

        msg.ok(batchoffset=-3)

        channel.message_consume(queue=name_b, destination="sub_b", no_ack=False)
        queue_b = self.client.queue("sub_b")
        msg = queue_b.get(timeout=1)
        self.assertEqual("Message 6", msg.body)
        msg.ok()

        sub_c = channel.message_consume(queue=name_c, destination="sub_c", no_ack=False)
        queue_c = self.client.queue("sub_c")
        msg = queue_c.get(timeout=1)
        self.assertEqual("Message 7", msg.body)
        msg.ok()

        #publish messages
        for i in range(1, 5):
            channel.message_transfer(routing_key=topic, destination="amq.topic", body="TxMessage %d" % i)

        channel.message_transfer(routing_key=key, destination="amq.direct", body="TxMessage 6")
        channel.message_transfer(routing_key=name_a, body="TxMessage 7")

        return queue_a, queue_b, queue_c
