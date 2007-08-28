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
from qpid.reference import Reference, ReferenceId

class MessageTests(TestBase):
    """Tests for 'methods' on the amqp message 'class'"""

    def test_consume_no_local(self):
        """
        Test that the no_local flag is honoured in the consume method
        """
        channel = self.channel
        #setup, declare two queues:
        channel.queue_declare(queue="test-queue-1a", exclusive=True)
        channel.queue_declare(queue="test-queue-1b", exclusive=True)
        #establish two consumers one of which excludes delivery of locally sent messages
        channel.message_subscribe(destination="local_included", queue="test-queue-1a")
        channel.message_subscribe(destination="local_excluded", queue="test-queue-1b", no_local=True)

        #send a message
        channel.message_transfer(content=Content(properties={'routing_key' : "test-queue-1a"}, body="consume_no_local"))
        channel.message_transfer(content=Content(properties={'routing_key' : "test-queue-1b"}, body="consume_no_local"))

        #check the queues of the two consumers
        excluded = self.client.queue("local_excluded")
        included = self.client.queue("local_included")
        msg = included.get(timeout=1)
        self.assertEqual("consume_no_local", msg.content.body)
        try:
            excluded.get(timeout=1) 
            self.fail("Received locally published message though no_local=true")
        except Empty: None


    def test_consume_exclusive(self):
        """
        Test that the exclusive flag is honoured in the consume method
        """
        channel = self.channel
        #setup, declare a queue:
        channel.queue_declare(queue="test-queue-2", exclusive=True)

        #check that an exclusive consumer prevents other consumer being created:
        channel.message_subscribe(destination="first", queue="test-queue-2", exclusive=True)
        try:
            channel.message_subscribe(destination="second", queue="test-queue-2")
            self.fail("Expected consume request to fail due to previous exclusive consumer")
        except Closed, e:
            self.assertChannelException(403, e.args[0])

        #open new channel and cleanup last consumer:    
        channel = self.client.channel(2)
        channel.channel_open()

        #check that an exclusive consumer cannot be created if a consumer already exists:
        channel.message_subscribe(destination="first", queue="test-queue-2")
        try:
            channel.message_subscribe(destination="second", queue="test-queue-2", exclusive=True)
            self.fail("Expected exclusive consume request to fail due to previous consumer")
        except Closed, e:
            self.assertChannelException(403, e.args[0])

    def test_consume_queue_errors(self):
        """
        Test error conditions associated with the queue field of the consume method:
        """
        channel = self.channel
        try:
            #queue specified but doesn't exist:
            channel.message_subscribe(queue="invalid-queue")
            self.fail("Expected failure when consuming from non-existent queue")
        except Closed, e:
            self.assertChannelException(404, e.args[0])

        channel = self.client.channel(2)
        channel.channel_open()
        try:
            #queue not specified and none previously declared for channel:
            channel.message_subscribe(queue="")
            self.fail("Expected failure when consuming from unspecified queue")
        except Closed, e:
            self.assertConnectionException(530, e.args[0])

    def test_consume_unique_consumers(self):
        """
        Ensure unique consumer tags are enforced
        """
        channel = self.channel
        #setup, declare a queue:
        channel.queue_declare(queue="test-queue-3", exclusive=True)

        #check that attempts to use duplicate tags are detected and prevented:
        channel.message_subscribe(destination="first", queue="test-queue-3")
        try:
            channel.message_subscribe(destination="first", queue="test-queue-3")
            self.fail("Expected consume request to fail due to non-unique tag")
        except Closed, e:
            self.assertConnectionException(530, e.args[0])

    def test_cancel(self):
        """
        Test compliance of the basic.cancel method
        """
        channel = self.channel
        #setup, declare a queue:
        channel.queue_declare(queue="test-queue-4", exclusive=True)
        channel.message_subscribe(destination="my-consumer", queue="test-queue-4")
        channel.message_transfer(content=Content(properties={'routing_key' : "test-queue-4"}, body="One"))

        #cancel should stop messages being delivered
        channel.message_cancel(destination="my-consumer")
        channel.message_transfer(content=Content(properties={'routing_key' : "test-queue-4"}, body="Two"))
        myqueue = self.client.queue("my-consumer")
        msg = myqueue.get(timeout=1)
        self.assertEqual("One", msg.content.body)
        try:
            msg = myqueue.get(timeout=1) 
            self.fail("Got message after cancellation: " + msg)
        except Empty: None

        #cancellation of non-existant consumers should be handled without error
        channel.message_cancel(destination="my-consumer")
        channel.message_cancel(destination="this-never-existed")


    def test_ack(self):
        """
        Test basic ack/recover behaviour
        """
        channel = self.channel
        channel.queue_declare(queue="test-ack-queue", exclusive=True)
        
        channel.message_subscribe(queue="test-ack-queue", destination="consumer_tag", confirm_mode=1)
        queue = self.client.queue("consumer_tag")

        channel.message_transfer(content=Content(properties={'routing_key' : "test-ack-queue"}, body="One"))
        channel.message_transfer(content=Content(properties={'routing_key' : "test-ack-queue"}, body="Two"))
        channel.message_transfer(content=Content(properties={'routing_key' : "test-ack-queue"}, body="Three"))
        channel.message_transfer(content=Content(properties={'routing_key' : "test-ack-queue"}, body="Four"))
        channel.message_transfer(content=Content(properties={'routing_key' : "test-ack-queue"}, body="Five"))
                
        msg1 = queue.get(timeout=1)
        msg2 = queue.get(timeout=1)
        msg3 = queue.get(timeout=1)
        msg4 = queue.get(timeout=1)
        msg5 = queue.get(timeout=1)
        
        self.assertEqual("One", msg1.content.body)
        self.assertEqual("Two", msg2.content.body)
        self.assertEqual("Three", msg3.content.body)
        self.assertEqual("Four", msg4.content.body)
        self.assertEqual("Five", msg5.content.body)

        msg2.complete(cumulative=True)#One and Two
        msg4.complete(cumulative=False)

        channel.message_recover(requeue=False)
        
        msg3b = queue.get(timeout=1)
        msg5b = queue.get(timeout=1)
        
        self.assertEqual("Three", msg3b.content.body)
        self.assertEqual("Five", msg5b.content.body)

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message: " + extra.content.body)
        except Empty: None

    def test_recover_requeue(self):
        """
        Test requeing on recovery
        """
        channel = self.channel
        channel.queue_declare(queue="test-requeue", exclusive=True)
        
        channel.message_subscribe(queue="test-requeue", destination="consumer_tag", confirm_mode=1)
        queue = self.client.queue("consumer_tag")

        channel.message_transfer(content=Content(properties={'routing_key' : "test-requeue"}, body="One"))
        channel.message_transfer(content=Content(properties={'routing_key' : "test-requeue"}, body="Two"))
        channel.message_transfer(content=Content(properties={'routing_key' : "test-requeue"}, body="Three"))
        channel.message_transfer(content=Content(properties={'routing_key' : "test-requeue"}, body="Four"))
        channel.message_transfer(content=Content(properties={'routing_key' : "test-requeue"}, body="Five"))
                
        msg1 = queue.get(timeout=1)
        msg2 = queue.get(timeout=1)
        msg3 = queue.get(timeout=1)
        msg4 = queue.get(timeout=1)
        msg5 = queue.get(timeout=1)
        
        self.assertEqual("One", msg1.content.body)
        self.assertEqual("Two", msg2.content.body)
        self.assertEqual("Three", msg3.content.body)
        self.assertEqual("Four", msg4.content.body)
        self.assertEqual("Five", msg5.content.body)

        msg2.complete(cumulative=True)  #One and Two
        msg4.complete(cumulative=False)  #Four

        channel.message_cancel(destination="consumer_tag")

        #publish a new message
        channel.message_transfer(content=Content(properties={'routing_key' : "test-requeue"}, body="Six"))
        #requeue unacked messages (Three and Five)
        channel.message_recover(requeue=True)

        channel.message_subscribe(queue="test-requeue", destination="consumer_tag")
        queue2 = self.client.queue("consumer_tag")
        
        msg3b = queue2.get(timeout=1)
        msg5b = queue2.get(timeout=1)
        
        self.assertEqual("Three", msg3b.content.body)
        self.assertEqual("Five", msg5b.content.body)

        self.assertEqual(True, msg3b.content['redelivered'])
        self.assertEqual(True, msg5b.content['redelivered'])

        self.assertEqual("Six", queue2.get(timeout=1).content.body)

        try:
            extra = queue2.get(timeout=1)
            self.fail("Got unexpected message in second queue: " + extra.content.body)
        except Empty: None
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in original queue: " + extra.content.body)
        except Empty: None
        
        
    def test_qos_prefetch_count(self):
        """
        Test that the prefetch count specified is honoured
        """
        #setup: declare queue and subscribe
        channel = self.channel
        channel.queue_declare(queue="test-prefetch-count", exclusive=True)
        subscription = channel.message_subscribe(queue="test-prefetch-count", destination="consumer_tag", confirm_mode=1)
        queue = self.client.queue("consumer_tag")

        #set prefetch to 5:
        channel.message_qos(prefetch_count=5)

        #publish 10 messages:
        for i in range(1, 11):
            channel.message_transfer(content=Content(properties={'routing_key' : "test-prefetch-count"}, body="Message %d" % i))

        #only 5 messages should have been delivered:
        for i in range(1, 6):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.content.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected 6th message in original queue: " + extra.content.body)
        except Empty: None

        #ack messages and check that the next set arrive ok:
        msg.complete()

        for i in range(6, 11):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.content.body)

        msg.complete()

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected 11th message in original queue: " + extra.content.body)
        except Empty: None


        
    def test_qos_prefetch_size(self):
        """
        Test that the prefetch size specified is honoured
        """
        #setup: declare queue and subscribe
        channel = self.channel
        channel.queue_declare(queue="test-prefetch-size", exclusive=True)
        subscription = channel.message_subscribe(queue="test-prefetch-size", destination="consumer_tag", confirm_mode=1)
        queue = self.client.queue("consumer_tag")

        #set prefetch to 50 bytes (each message is 9 or 10 bytes):
        channel.message_qos(prefetch_size=50)

        #publish 10 messages:
        for i in range(1, 11):
            channel.message_transfer(content=Content(properties={'routing_key' : "test-prefetch-size"}, body="Message %d" % i))

        #only 5 messages should have been delivered (i.e. 45 bytes worth):
        for i in range(1, 6):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.content.body)

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected 6th message in original queue: " + extra.content.body)
        except Empty: None

        #ack messages and check that the next set arrive ok:
        msg.complete()

        for i in range(6, 11):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.content.body)

        msg.complete()

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected 11th message in original queue: " + extra.content.body)
        except Empty: None

        #make sure that a single oversized message still gets delivered
        large = "abcdefghijklmnopqrstuvwxyz"
        large = large + "-" + large;
        channel.message_transfer(content=Content(properties={'routing_key' : "test-prefetch-size"}, body=large))
        msg = queue.get(timeout=1)
        self.assertEqual(large, msg.content.body)



    def test_reject(self):
        channel = self.channel
        channel.queue_declare(queue = "q", exclusive=True)

        channel.message_subscribe(queue = "q", destination = "consumer")
        channel.message_transfer(content=Content(properties={'routing_key' : "q"}, body="blah, blah"))
        msg = self.client.queue("consumer").get(timeout = 1)
        self.assertEquals(msg.content.body, "blah, blah")
        channel.message_cancel(destination = "consumer")
        msg.reject()

        channel.message_subscribe(queue = "q", destination = "checker")
        msg = self.client.queue("checker").get(timeout = 1)
        self.assertEquals(msg.content.body, "blah, blah")

    def test_credit_flow_messages(self):
        """
        Test basic credit based flow control with unit = message
        """
        #declare an exclusive queue
        channel = self.channel
        channel.queue_declare(queue = "q", exclusive=True)
        #create consumer (for now that defaults to infinite credit)
        channel.message_subscribe(queue = "q", destination = "c")
        channel.message_flow_mode(mode = 0, destination = "c")
        #set credit to zero (can remove this once move to proper default for subscribe method)
        channel.message_stop(destination = "c")
        #send batch of messages to queue
        for i in range(1, 11):
            channel.message_transfer(content=Content(properties={'routing_key' : "q"}, body = "Message %d" % i))
        
        #set message credit to finite amount (less than enough for all messages)
        channel.message_flow(unit = 0, value = 5, destination = "c")
        #set infinite byte credit
        channel.message_flow(unit = 1, value = 0xFFFFFFFF, destination = "c")
        #check that expected number were received
        q = self.client.queue("c")
        for i in range(1, 6):
            self.assertDataEquals(channel, q.get(timeout = 1), "Message %d" % i)
        self.assertEmpty(q)
        
        #increase credit again and check more are received
        for i in range(6, 11):
            channel.message_flow(unit = 0, value = 1, destination = "c")
            self.assertDataEquals(channel, q.get(timeout = 1), "Message %d" % i)
            self.assertEmpty(q)

    def test_credit_flow_bytes(self):
        """
        Test basic credit based flow control with unit = bytes
        """
        #declare an exclusive queue
        channel = self.channel
        channel.queue_declare(queue = "q", exclusive=True)
        #create consumer (for now that defaults to infinite credit)
        channel.message_subscribe(queue = "q", destination = "c")
        channel.message_flow_mode(mode = 0, destination = "c")
        #set credit to zero (can remove this once move to proper default for subscribe method)
        channel.message_stop(destination = "c")
        #send batch of messages to queue
        for i in range(1, 11):
            channel.message_transfer(content=Content(properties={'routing_key' : "q"}, body = "abcdefgh"))

        #each message is currently interpreted as requiring 75 bytes of credit
        #set byte credit to finite amount (less than enough for all messages)
        channel.message_flow(unit = 1, value = 75*5, destination = "c")
        #set infinite message credit
        channel.message_flow(unit = 0, value = 0xFFFFFFFF, destination = "c")
        #check that expected number were received
        q = self.client.queue("c")
        for i in range(1, 6):
            self.assertDataEquals(channel, q.get(timeout = 1), "abcdefgh")
        self.assertEmpty(q)
        
        #increase credit again and check more are received
        for i in range(6, 11):
            channel.message_flow(unit = 1, value = 75, destination = "c")
            self.assertDataEquals(channel, q.get(timeout = 1), "abcdefgh")
            self.assertEmpty(q)


    def test_window_flow_messages(self):
        """
        Test basic window based flow control with unit = message
        """
        #declare an exclusive queue
        channel = self.channel
        channel.queue_declare(queue = "q", exclusive=True)
        #create consumer (for now that defaults to infinite credit)
        channel.message_subscribe(queue = "q", destination = "c", confirm_mode = 1)
        channel.message_flow_mode(mode = 1, destination = "c")
        #set credit to zero (can remove this once move to proper default for subscribe method)
        channel.message_stop(destination = "c")
        #send batch of messages to queue
        for i in range(1, 11):
            channel.message_transfer(content=Content(properties={'routing_key' : "q"}, body = "Message %d" % i))
        
        #set message credit to finite amount (less than enough for all messages)
        channel.message_flow(unit = 0, value = 5, destination = "c")
        #set infinite byte credit
        channel.message_flow(unit = 1, value = 0xFFFFFFFF, destination = "c")
        #check that expected number were received
        q = self.client.queue("c")
        for i in range(1, 6):
            msg = q.get(timeout = 1)
            self.assertDataEquals(channel, msg, "Message %d" % i)
        self.assertEmpty(q)
        
        #acknowledge messages and check more are received
        msg.complete(cumulative=True)
        for i in range(6, 11):
            self.assertDataEquals(channel, q.get(timeout = 1), "Message %d" % i)
        self.assertEmpty(q)


    def test_window_flow_bytes(self):
        """
        Test basic window based flow control with unit = bytes
        """
        #declare an exclusive queue
        channel = self.channel
        channel.queue_declare(queue = "q", exclusive=True)
        #create consumer (for now that defaults to infinite credit)
        channel.message_subscribe(queue = "q", destination = "c", confirm_mode = 1)
        channel.message_flow_mode(mode = 1, destination = "c")
        #set credit to zero (can remove this once move to proper default for subscribe method)
        channel.message_stop(destination = "c")
        #send batch of messages to queue
        for i in range(1, 11):
            channel.message_transfer(content=Content(properties={'routing_key' : "q"}, body = "abcdefgh"))

        #each message is currently interpreted as requiring 75 bytes of credit
        #set byte credit to finite amount (less than enough for all messages)
        channel.message_flow(unit = 1, value = 75*5, destination = "c")
        #set infinite message credit
        channel.message_flow(unit = 0, value = 0xFFFFFFFF, destination = "c")
        #check that expected number were received
        q = self.client.queue("c")
        msgs = []
        for i in range(1, 6):
            msg = q.get(timeout = 1)
            msgs.append(msg)
            self.assertDataEquals(channel, msg, "abcdefgh")
        self.assertEmpty(q)
        
        #ack each message individually and check more are received
        for i in range(6, 11):
            msg = msgs.pop()
            msg.complete(cumulative=False)
            self.assertDataEquals(channel, q.get(timeout = 1), "abcdefgh")
            self.assertEmpty(q)

    def assertDataEquals(self, channel, msg, expected):
        self.assertEquals(expected, msg.content.body)
