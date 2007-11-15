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
        channel2 = self.client.channel(2)
        channel2.session_open()
        self.perform_txn_work(channel2, "tx-commit-a", "tx-commit-b", "tx-commit-c")
        channel2.tx_commit()
        channel2.session_close()

        #use a different channel with new subscriptions to ensure
        #there is no redelivery of acked messages:
        channel = self.channel
        channel.tx_select()

        self.subscribe(channel, queue="tx-commit-a", destination="qa", confirm_mode=1)
        queue_a = self.client.queue("qa")

        self.subscribe(channel, queue="tx-commit-b", destination="qb", confirm_mode=1)
        queue_b = self.client.queue("qb")

        self.subscribe(channel, queue="tx-commit-c", destination="qc", confirm_mode=1)
        queue_c = self.client.queue("qc")

        #check results
        for i in range(1, 5):
            msg = queue_c.get(timeout=1)
            self.assertEqual("TxMessage %d" % i, msg.content.body)
            msg.complete()

        msg = queue_b.get(timeout=1)
        self.assertEqual("TxMessage 6", msg.content.body)
        msg.complete()

        msg = queue_a.get(timeout=1)
        self.assertEqual("TxMessage 7", msg.content.body)
        msg.complete()

        for q in [queue_a, queue_b, queue_c]:
            try:
                extra = q.get(timeout=1)
                self.fail("Got unexpected message: " + extra.content.body)
            except Empty: None

        #cleanup
        channel.tx_commit()

    def test_auto_rollback(self):
        """
        Test that a channel closed with an open transaction is effectively rolled back
        """
        channel2 = self.client.channel(2)
        channel2.session_open()
        queue_a, queue_b, queue_c = self.perform_txn_work(channel2, "tx-autorollback-a", "tx-autorollback-b", "tx-autorollback-c")

        for q in [queue_a, queue_b, queue_c]:
            try:
                extra = q.get(timeout=1)
                self.fail("Got unexpected message: " + extra.content.body)
            except Empty: None

        channel2.session_close()
        channel = self.channel
        channel.tx_select()

        self.subscribe(channel, queue="tx-autorollback-a", destination="qa", confirm_mode=1)
        queue_a = self.client.queue("qa")

        self.subscribe(channel, queue="tx-autorollback-b", destination="qb", confirm_mode=1)
        queue_b = self.client.queue("qb")

        self.subscribe(channel, queue="tx-autorollback-c", destination="qc", confirm_mode=1)
        queue_c = self.client.queue("qc")

        #check results
        for i in range(1, 5):
            msg = queue_a.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.content.body)
            msg.complete()

        msg = queue_b.get(timeout=1)
        self.assertEqual("Message 6", msg.content.body)
        msg.complete()

        msg = queue_c.get(timeout=1)
        self.assertEqual("Message 7", msg.content.body)
        msg.complete()

        for q in [queue_a, queue_b, queue_c]:
            try:
                extra = q.get(timeout=1)
                self.fail("Got unexpected message: " + extra.content.body)
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
                self.fail("Got unexpected message: " + extra.content.body)
            except Empty: None

        #stop subscriptions (ensures no delivery occurs during rollback as messages are requeued)
        for d in ["sub_a", "sub_b", "sub_c"]:
            channel.message_stop(destination=d)

        channel.tx_rollback()

        #restart susbcriptions
        for d in ["sub_a", "sub_b", "sub_c"]:
            channel.message_flow(destination=d, unit=0, value=0xFFFFFFFF)
            channel.message_flow(destination=d, unit=1, value=0xFFFFFFFF)

        #check results
        for i in range(1, 5):
            msg = queue_a.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.content.body)
            msg.complete()

        msg = queue_b.get(timeout=1)
        self.assertEqual("Message 6", msg.content.body)
        msg.complete()

        msg = queue_c.get(timeout=1)
        self.assertEqual("Message 7", msg.content.body)
        msg.complete()

        for q in [queue_a, queue_b, queue_c]:
            try:
                extra = q.get(timeout=1)
                self.fail("Got unexpected message: " + extra.content.body)
            except Empty: None

        #cleanup
        channel.tx_commit()

    def perform_txn_work(self, channel, name_a, name_b, name_c):
        """
        Utility method that does some setup and some work under a transaction. Used for testing both
        commit and rollback
        """
        #setup:
        channel.queue_declare(queue=name_a, exclusive=True, auto_delete=True)
        channel.queue_declare(queue=name_b, exclusive=True, auto_delete=True)
        channel.queue_declare(queue=name_c, exclusive=True, auto_delete=True)

        key = "my_key_" + name_b
        topic = "my_topic_" + name_c 
    
        channel.queue_bind(queue=name_b, exchange="amq.direct", routing_key=key)
        channel.queue_bind(queue=name_c, exchange="amq.topic", routing_key=topic)

        for i in range(1, 5):
            channel.message_transfer(content=Content(properties={'routing_key':name_a, 'message_id':"msg%d" % i}, body="Message %d" % i))

        channel.message_transfer(destination="amq.direct",
                                 content=Content(properties={'routing_key':key, 'message_id':"msg6"}, body="Message 6"))
        channel.message_transfer(destination="amq.topic",
                                 content=Content(properties={'routing_key':topic, 'message_id':"msg7"}, body="Message 7"))

        channel.tx_select()

        #consume and ack messages
        self.subscribe(channel, queue=name_a, destination="sub_a", confirm_mode=1)
        queue_a = self.client.queue("sub_a")
        for i in range(1, 5):
            msg = queue_a.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.content.body)

        msg.complete()

        self.subscribe(channel, queue=name_b, destination="sub_b", confirm_mode=1)
        queue_b = self.client.queue("sub_b")
        msg = queue_b.get(timeout=1)
        self.assertEqual("Message 6", msg.content.body)
        msg.complete()

        sub_c = self.subscribe(channel, queue=name_c, destination="sub_c", confirm_mode=1)
        queue_c = self.client.queue("sub_c")
        msg = queue_c.get(timeout=1)
        self.assertEqual("Message 7", msg.content.body)
        msg.complete()

        #publish messages
        for i in range(1, 5):
            channel.message_transfer(destination="amq.topic",
                                     content=Content(properties={'routing_key':topic, 'message_id':"tx-msg%d" % i},
                                                     body="TxMessage %d" % i))

        channel.message_transfer(destination="amq.direct",
                                 content=Content(properties={'routing_key':key, 'message_id':"tx-msg6"},
                                                 body="TxMessage 6"))
        channel.message_transfer(content=Content(properties={'routing_key':name_a, 'message_id':"tx-msg7"},
                                                 body="TxMessage 7"))
        return queue_a, queue_b, queue_c
