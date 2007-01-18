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

    def test_amqp_basic_13(self):
        """
        First, this test tries to receive a message with a no-ack
        consumer. Second, this test tries to explicitely receive and
        acknowledge a message with an acknowledging consumer.
        """
        ch = self.channel
        self.queue_declare(ch, queue = "myqueue")

        # No ack consumer
        ctag = ch.basic_consume(queue = "myqueue", no_ack = True).consumer_tag
        body = "test no-ack"
        ch.basic_publish(routing_key = "myqueue", content = Content(body))
        msg = self.client.queue(ctag).get(timeout = 5)
        self.assert_(msg.content.body == body)

        # Acknowleding consumer
        self.queue_declare(ch, queue = "otherqueue")
        ctag = ch.basic_consume(queue = "otherqueue", no_ack = False).consumer_tag
        body = "test ack"
        ch.basic_publish(routing_key = "otherqueue", content = Content(body))
        msg = self.client.queue(ctag).get(timeout = 5)
        ch.basic_ack(delivery_tag = msg.delivery_tag)
        self.assert_(msg.content.body == body)
        
    def test_basic_delivery_immediate(self):
        """
        Test basic message delivery where consume is issued before publish
        """
        channel = self.channel
        self.exchange_declare(channel, exchange="test-exchange", type="direct")
        self.queue_declare(channel, queue="test-queue") 
        channel.queue_bind(queue="test-queue", exchange="test-exchange", routing_key="key")
        reply = channel.basic_consume(queue="test-queue", no_ack=True)
        queue = self.client.queue(reply.consumer_tag)

        body = "Immediate Delivery"
        channel.basic_publish(exchange="test-exchange", routing_key="key", content=Content(body), immediate=True)
        msg = queue.get(timeout=5)
        self.assert_(msg.content.body == body)

        # TODO: Ensure we fail if immediate=True and there's no consumer.


    def test_basic_delivery_queued(self):
        """
        Test basic message delivery where publish is issued before consume
        (i.e. requires queueing of the message)
        """
        channel = self.channel
        self.exchange_declare(channel, exchange="test-exchange", type="direct")
        self.queue_declare(channel, queue="test-queue")
        channel.queue_bind(queue="test-queue", exchange="test-exchange", routing_key="key")
        body = "Queued Delivery"
        channel.basic_publish(exchange="test-exchange", routing_key="key", content=Content(body))
        reply = channel.basic_consume(queue="test-queue", no_ack=True)
        queue = self.client.queue(reply.consumer_tag)
        msg = queue.get(timeout=5)
        self.assert_(msg.content.body == body)

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
            self.assertConnectionException(504, e.args[0])

