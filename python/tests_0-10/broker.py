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
from qpid.testlib import TestBase010
from qpid.datatypes import Message

class BrokerTests(TestBase010):
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
        self.subscribe(ch, queue = "myqueue", destination = ctag)
        body = "test no-ack"
        ch.message_transfer(content = Content(body, properties = {"routing_key" : "myqueue"}))
        msg = self.client.queue(ctag).get(timeout = 5)
        self.assert_(msg.content.body == body)

        # Acknowledging consumer
        self.queue_declare(ch, queue = "otherqueue")
        ctag = "tag2"
        self.subscribe(ch, queue = "otherqueue", destination = ctag, confirm_mode = 1)
        ch.message_flow(destination=ctag, unit=0, value=0xFFFFFFFF)
        ch.message_flow(destination=ctag, unit=1, value=0xFFFFFFFF)
        body = "test ack"
        ch.message_transfer(content = Content(body, properties = {"routing_key" : "otherqueue"}))
        msg = self.client.queue(ctag).get(timeout = 5)
        msg.complete()
        self.assert_(msg.content.body == body)
        
    def test_simple_delivery_immediate(self):
        """
        Test simple message delivery where consume is issued before publish
        """
        session = self.session
        session.queue_declare(queue="test-queue", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="test-queue", exchange="amq.fanout")
        consumer_tag = "tag1"
        session.message_subscribe(queue="test-queue", destination=consumer_tag)
        session.message_flow(unit = 0, value = 0xFFFFFFFF, destination = consumer_tag)
        session.message_flow(unit = 1, value = 0xFFFFFFFF, destination = consumer_tag)
        queue = session.incoming(consumer_tag)

        body = "Immediate Delivery"
        session.message_transfer("amq.fanout", None, None, Message(body))
        msg = queue.get(timeout=5)
        self.assert_(msg.body == body)

    def test_simple_delivery_queued(self):
        """
        Test basic message delivery where publish is issued before consume
        (i.e. requires queueing of the message)
        """
        session = self.session
        session.queue_declare(queue="test-queue", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="test-queue", exchange="amq.fanout")
        body = "Queued Delivery"
        session.message_transfer("amq.fanout", None, None, Message(body))

        consumer_tag = "tag1"
        session.message_subscribe(queue="test-queue", destination=consumer_tag)
        session.message_flow(unit = 0, value = 0xFFFFFFFF, destination = consumer_tag)
        session.message_flow(unit = 1, value = 0xFFFFFFFF, destination = consumer_tag)
        queue = session.incoming(consumer_tag)
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
        channel.session_open()
        channel.session_close()
        try:
            channel.queue_declare(exclusive=True)
            self.fail("Expected error on queue_declare for closed channel")
        except Closed, e:
            if isinstance(e.args[0], str): self.fail(e)
            self.assertConnectionException(504, e.args[0])
