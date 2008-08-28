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
from qpid.client import Closed
from qpid.queue import Empty
from qpid.content import Content
from qpid.testlib import testrunner, TestBase

class BrokerTests(TestBase):
    """Tests for basic Broker functionality"""

    def test_ack_and_no_ack(self):
        """
        First, this test tries to receive a message with a no-ack
        consumer. Second, this test tries to explicitly receive and
        acknowledge a message with an acknowledging consumer.
        """
        ch = self.channel
        self.queue_declare(ch, queue = "myqueue")

        # No ack consumer
        ctag = "tag1"
        ch.message_consume(queue = "myqueue", destination = ctag, no_ack = True)
        body = "test no-ack"
        ch.message_transfer(routing_key = "myqueue", body = body)
        msg = self.client.queue(ctag).get(timeout = 5)
        self.assert_(msg.body == body)

        # Acknowledging consumer
        self.queue_declare(ch, queue = "otherqueue")
        ctag = "tag2"
        ch.message_consume(queue = "otherqueue", destination = ctag, no_ack = False)
        body = "test ack"
        ch.message_transfer(routing_key = "otherqueue", body = body)
        msg = self.client.queue(ctag).get(timeout = 5)
        msg.ok()
        self.assert_(msg.body == body)
        
    def test_simple_delivery_immediate(self):
        """
        Test simple message delivery where consume is issued before publish
        """
        channel = self.channel
        self.exchange_declare(channel, exchange="test-exchange", type="direct")
        self.queue_declare(channel, queue="test-queue") 
        channel.queue_bind(queue="test-queue", exchange="test-exchange", routing_key="key")
        consumer_tag = "tag1"
        channel.message_consume(queue="test-queue", destination=consumer_tag, no_ack=True)
        queue = self.client.queue(consumer_tag)

        body = "Immediate Delivery"
        channel.message_transfer(destination="test-exchange", routing_key="key", body=body, immediate=True)
        msg = queue.get(timeout=5)
        self.assert_(msg.body == body)

        # TODO: Ensure we fail if immediate=True and there's no consumer.


    def test_simple_delivery_queued(self):
        """
        Test basic message delivery where publish is issued before consume
        (i.e. requires queueing of the message)
        """
        channel = self.channel
        self.exchange_declare(channel, exchange="test-exchange", type="direct")
        self.queue_declare(channel, queue="test-queue")
        channel.queue_bind(queue="test-queue", exchange="test-exchange", routing_key="key")
        body = "Queued Delivery"
        channel.message_transfer(destination="test-exchange", routing_key="key", body=body)

        consumer_tag = "tag1"
        channel.message_consume(queue="test-queue", destination=consumer_tag, no_ack=True)
        queue = self.client.queue(consumer_tag)
        msg = queue.get(timeout=5)
        self.assert_(msg.body == body)

    def test_invalid_channel(self):
        channel = self.client.channel(200)
        try:
            channel.queue_declare(exclusive=True)
            self.fail("Expected error on queue_declare for invalid channel")
        except Closed, e:
            self.assertConnectionException(504, e.args[0])
        
    def test_closed_channel(self):
        channel = self.client.channel(200)
        channel.channel_open()
        channel.channel_close()
        try:
            channel.queue_declare(exclusive=True)
            self.fail("Expected error on queue_declare for closed channel")
        except Closed, e:
            if isinstance(e.args[0], str): self.fail(e)
            self.assertConnectionException(504, e.args[0])

    def test_ping_pong(self):
        channel = self.channel
        reply = channel.channel_ping()
        self.assertEqual(reply.method.klass.name, "channel")
        self.assertEqual(reply.method.name, "ok")
        #todo: provide a way to get notified of incoming pongs...

    def test_channel_flow(self):
        channel = self.channel
        channel.queue_declare(queue="flow_test_queue", exclusive=True)
        channel.message_consume(destination="my-tag", queue="flow_test_queue")
        incoming = self.client.queue("my-tag")
        
        channel.channel_flow(active=False)        
        channel.message_transfer(routing_key="flow_test_queue", body="abcdefghijklmnopqrstuvwxyz")
        try:
            incoming.get(timeout=1) 
            self.fail("Received message when flow turned off.")
        except Empty: None
        
        channel.channel_flow(active=True)
        msg = incoming.get(timeout=1)
        self.assertEqual("abcdefghijklmnopqrstuvwxyz", msg.body)
