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
        channel.queue_declare(queue="test-queue-1a", exclusive=True, auto_delete=True)
        channel.queue_declare(queue="test-queue-1b", exclusive=True, auto_delete=True)
        #establish two consumers one of which excludes delivery of locally sent messages
        self.subscribe(destination="local_included", queue="test-queue-1a")
        self.subscribe(destination="local_excluded", queue="test-queue-1b", no_local=True)

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

    def test_consume_no_local_awkward(self):

        """
        If an exclusive queue gets a no-local delivered to it, that
        message could 'block' delivery of subsequent messages or it
        could be left on the queue, possibly never being consumed
        (this is the case for example in the qpid JMS mapping of
        topics). This test excercises a Qpid C++ broker hack that
        deletes such messages.
        """

        channel = self.channel
        #setup:
        channel.queue_declare(queue="test-queue", exclusive=True, auto_delete=True)
        #establish consumer which excludes delivery of locally sent messages
        self.subscribe(destination="local_excluded", queue="test-queue", no_local=True)

        #send a 'local' message
        channel.message_transfer(content=Content(properties={'routing_key' : "test-queue"}, body="local"))

        #send a non local message
        other = self.connect()
        channel2 = other.channel(1)
        channel2.session_open()
        channel2.message_transfer(content=Content(properties={'routing_key' : "test-queue"}, body="foreign"))
        channel2.session_close()
        other.close()

        #check that the second message only is delivered
        excluded = self.client.queue("local_excluded")
        msg = excluded.get(timeout=1)
        self.assertEqual("foreign", msg.content.body)
        try:
            excluded.get(timeout=1)
            self.fail("Received extra message")
        except Empty: None
        #check queue is empty
        self.assertEqual(0, channel.queue_query(queue="test-queue").message_count)


    def test_consume_exclusive(self):
        """
        Test that the exclusive flag is honoured in the consume method
        """
        channel = self.channel
        #setup, declare a queue:
        channel.queue_declare(queue="test-queue-2", exclusive=True, auto_delete=True)

        #check that an exclusive consumer prevents other consumer being created:
        self.subscribe(destination="first", queue="test-queue-2", exclusive=True)
        try:
            self.subscribe(destination="second", queue="test-queue-2")
            self.fail("Expected consume request to fail due to previous exclusive consumer")
        except Closed, e:
            self.assertChannelException(403, e.args[0])

        #open new channel and cleanup last consumer:
        channel = self.client.channel(2)
        channel.session_open()

        #check that an exclusive consumer cannot be created if a consumer already exists:
        self.subscribe(channel, destination="first", queue="test-queue-2")
        try:
            self.subscribe(destination="second", queue="test-queue-2", exclusive=True)
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
            self.subscribe(queue="invalid-queue", destination="")
            self.fail("Expected failure when consuming from non-existent queue")
        except Closed, e:
            self.assertChannelException(404, e.args[0])

        channel = self.client.channel(2)
        channel.session_open()
        try:
            #queue not specified and none previously declared for channel:
            self.subscribe(channel, queue="", destination="")
            self.fail("Expected failure when consuming from unspecified queue")
        except Closed, e:
            self.assertConnectionException(530, e.args[0])

    def test_consume_unique_consumers(self):
        """
        Ensure unique consumer tags are enforced
        """
        channel = self.channel
        #setup, declare a queue:
        channel.queue_declare(queue="test-queue-3", exclusive=True, auto_delete=True)

        #check that attempts to use duplicate tags are detected and prevented:
        self.subscribe(destination="first", queue="test-queue-3")
        try:
            self.subscribe(destination="first", queue="test-queue-3")
            self.fail("Expected consume request to fail due to non-unique tag")
        except Closed, e:
            self.assertConnectionException(530, e.args[0])

    def test_cancel(self):
        """
        Test compliance of the basic.cancel method
        """
        channel = self.channel
        #setup, declare a queue:
        channel.queue_declare(queue="test-queue-4", exclusive=True, auto_delete=True)
        self.subscribe(destination="my-consumer", queue="test-queue-4")
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
        channel.queue_declare(queue="test-ack-queue", exclusive=True, auto_delete=True)

        self.subscribe(queue="test-ack-queue", destination="consumer_tag", confirm_mode=1)
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


    def test_recover(self):
        """
        Test recover behaviour
        """
        channel = self.channel
        channel.queue_declare(queue="queue-a", exclusive=True, auto_delete=True)
        channel.queue_bind(exchange="amq.fanout", queue="queue-a")
        channel.queue_declare(queue="queue-b", exclusive=True, auto_delete=True)
        channel.queue_bind(exchange="amq.fanout", queue="queue-b")

        self.subscribe(queue="queue-a", destination="unconfirmed", confirm_mode=1)
        self.subscribe(queue="queue-b", destination="confirmed", confirm_mode=0)
        confirmed = self.client.queue("confirmed")
        unconfirmed = self.client.queue("unconfirmed")

        data = ["One", "Two", "Three", "Four", "Five"]
        for d in data:
            channel.message_transfer(destination="amq.fanout", content=Content(body=d))

        for q in [confirmed, unconfirmed]:
            for d in data:
                self.assertEqual(d, q.get(timeout=1).content.body)
            self.assertEmpty(q)

        channel.message_recover(requeue=False)

        self.assertEmpty(confirmed)

        while len(data):
            msg = None
            for d in data:
                msg = unconfirmed.get(timeout=1)
                self.assertEqual(d, msg.content.body)
                self.assertEqual(True, msg.content['redelivered'])
            self.assertEmpty(unconfirmed)
            data.remove(msg.content.body)
            msg.complete(cumulative=False)
            channel.message_recover(requeue=False)


    def test_recover_requeue(self):
        """
        Test requeing on recovery
        """
        channel = self.channel
        channel.queue_declare(queue="test-requeue", exclusive=True, auto_delete=True)

        self.subscribe(queue="test-requeue", destination="consumer_tag", confirm_mode=1)
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

        self.subscribe(queue="test-requeue", destination="consumer_tag")
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
        channel.queue_declare(queue="test-prefetch-count", exclusive=True, auto_delete=True)
        subscription = self.subscribe(queue="test-prefetch-count", destination="consumer_tag", confirm_mode=1)
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
        channel.queue_declare(queue="test-prefetch-size", exclusive=True, auto_delete=True)
        subscription = self.subscribe(queue="test-prefetch-size", destination="consumer_tag", confirm_mode=1)
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
        channel.queue_declare(queue = "q", exclusive=True, auto_delete=True, alternate_exchange="amq.fanout")
        channel.queue_declare(queue = "r", exclusive=True, auto_delete=True)
        channel.queue_bind(queue = "r", exchange = "amq.fanout")

        self.subscribe(queue = "q", destination = "consumer", confirm_mode = 1)
        channel.message_transfer(content=Content(properties={'routing_key' : "q"}, body="blah, blah"))
        msg = self.client.queue("consumer").get(timeout = 1)
        self.assertEquals(msg.content.body, "blah, blah")
        channel.message_reject([msg.command_id, msg.command_id])

        self.subscribe(queue = "r", destination = "checker")
        msg = self.client.queue("checker").get(timeout = 1)
        self.assertEquals(msg.content.body, "blah, blah")

    def test_credit_flow_messages(self):
        """
        Test basic credit based flow control with unit = message
        """
        #declare an exclusive queue
        channel = self.channel
        channel.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        #create consumer (for now that defaults to infinite credit)
        channel.message_subscribe(queue = "q", destination = "c")
        channel.message_flow_mode(mode = 0, destination = "c")
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
        channel.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        #create consumer (for now that defaults to infinite credit)
        channel.message_subscribe(queue = "q", destination = "c")
        channel.message_flow_mode(mode = 0, destination = "c")
        #send batch of messages to queue
        for i in range(10):
            channel.message_transfer(content=Content(properties={'routing_key' : "q"}, body = "abcdefgh"))

        #each message is currently interpreted as requiring msg_size bytes of credit
        msg_size = 35

        #set byte credit to finite amount (less than enough for all messages)
        channel.message_flow(unit = 1, value = msg_size*5, destination = "c")
        #set infinite message credit
        channel.message_flow(unit = 0, value = 0xFFFFFFFF, destination = "c")
        #check that expected number were received
        q = self.client.queue("c")
        for i in range(5):
            self.assertDataEquals(channel, q.get(timeout = 1), "abcdefgh")
        self.assertEmpty(q)

        #increase credit again and check more are received
        for i in range(5):
            channel.message_flow(unit = 1, value = msg_size, destination = "c")
            self.assertDataEquals(channel, q.get(timeout = 1), "abcdefgh")
            self.assertEmpty(q)


    def test_window_flow_messages(self):
        """
        Test basic window based flow control with unit = message
        """
        #declare an exclusive queue
        channel = self.channel
        channel.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        #create consumer (for now that defaults to infinite credit)
        channel.message_subscribe(queue = "q", destination = "c", confirm_mode = 1)
        channel.message_flow_mode(mode = 1, destination = "c")
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
        channel.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        #create consumer (for now that defaults to infinite credit)
        channel.message_subscribe(queue = "q", destination = "c", confirm_mode = 1)
        channel.message_flow_mode(mode = 1, destination = "c")
        #send batch of messages to queue
        for i in range(10):
            channel.message_transfer(content=Content(properties={'routing_key' : "q"}, body = "abcdefgh"))

        #each message is currently interpreted as requiring msg_size bytes of credit
        msg_size = 40

        #set byte credit to finite amount (less than enough for all messages)
        channel.message_flow(unit = 1, value = msg_size*5, destination = "c")
        #set infinite message credit
        channel.message_flow(unit = 0, value = 0xFFFFFFFF, destination = "c")
        #check that expected number were received
        q = self.client.queue("c")
        msgs = []
        for i in range(5):
            msg = q.get(timeout = 1)
            msgs.append(msg)
            self.assertDataEquals(channel, msg, "abcdefgh")
        self.assertEmpty(q)

        #ack each message individually and check more are received
        for i in range(5):
            msg = msgs.pop()
            msg.complete(cumulative=False)
            self.assertDataEquals(channel, q.get(timeout = 1), "abcdefgh")
            self.assertEmpty(q)

    def test_subscribe_not_acquired(self):
        """
        Test the not-acquired modes works as expected for a simple case
        """
        channel = self.channel
        channel.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        for i in range(1, 6):
            channel.message_transfer(content=Content(properties={'routing_key' : "q"}, body = "Message %s" % i))

        self.subscribe(queue = "q", destination = "a", acquire_mode = 1)
        self.subscribe(queue = "q", destination = "b", acquire_mode = 1)

        for i in range(6, 11):
            channel.message_transfer(content=Content(properties={'routing_key' : "q"}, body = "Message %s" % i))

        #both subscribers should see all messages
        qA = self.client.queue("a")
        qB = self.client.queue("b")
        for i in range(1, 11):
            for q in [qA, qB]:
                msg = q.get(timeout = 1)
                self.assertEquals("Message %s" % i, msg.content.body)
                msg.complete()

        #messages should still be on the queue:
        self.assertEquals(10, channel.queue_query(queue = "q").message_count)

    def test_acquire(self):
        """
        Test explicit acquire function
        """
        channel = self.channel
        channel.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        channel.message_transfer(content=Content(properties={'routing_key' : "q"}, body = "acquire me"))

        self.subscribe(queue = "q", destination = "a", acquire_mode = 1, confirm_mode = 1)
        msg = self.client.queue("a").get(timeout = 1)
        #message should still be on the queue:
        self.assertEquals(1, channel.queue_query(queue = "q").message_count)

        channel.message_acquire([msg.command_id, msg.command_id])
        #check that we get notification (i.e. message_acquired)
        response = channel.control_queue.get(timeout=1)
        self.assertEquals(response.transfers, [msg.command_id, msg.command_id])
        #message should have been removed from the queue:
        self.assertEquals(0, channel.queue_query(queue = "q").message_count)
        msg.complete()




    def test_release(self):
        """
        Test explicit release function
        """
        channel = self.channel
        channel.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        channel.message_transfer(content=Content(properties={'routing_key' : "q"}, body = "release me"))

        self.subscribe(queue = "q", destination = "a", acquire_mode = 0, confirm_mode = 1)
        msg = self.client.queue("a").get(timeout = 1)
        channel.message_cancel(destination = "a")
        channel.message_release([msg.command_id, msg.command_id])
        msg.complete()

        #message should not have been removed from the queue:
        self.assertEquals(1, channel.queue_query(queue = "q").message_count)

    def test_release_ordering(self):
        """
        Test order of released messages is as expected
        """
        channel = self.channel
        channel.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        for i in range (1, 11):
            channel.message_transfer(content=Content(properties={'routing_key' : "q"}, body = "released message %s" % (i)))

        channel.message_subscribe(queue = "q", destination = "a", confirm_mode = 1)
        channel.message_flow(unit = 0, value = 10, destination = "a")
        channel.message_flow(unit = 1, value = 0xFFFFFFFF, destination = "a")
        queue = self.client.queue("a")
        first = queue.get(timeout = 1)
        for i in range (2, 10):
            self.assertEquals("released message %s" % (i), queue.get(timeout = 1).content.body)
        last = queue.get(timeout = 1)
        self.assertEmpty(queue)
        channel.message_release([first.command_id, last.command_id])
        last.complete()#will re-allocate credit, as in window mode
        for i in range (1, 11):
            self.assertEquals("released message %s" % (i), queue.get(timeout = 1).content.body)

    def test_ranged_ack(self):
        """
        Test acking of messages ranges
        """
        channel = self.channel
        channel.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        for i in range (1, 11):
            channel.message_transfer(content=Content(properties={'routing_key' : "q"}, body = "message %s" % (i)))

        channel.message_subscribe(queue = "q", destination = "a", confirm_mode = 1)
        channel.message_flow(unit = 0, value = 10, destination = "a")
        channel.message_flow(unit = 1, value = 0xFFFFFFFF, destination = "a")
        queue = self.client.queue("a")
        for i in range (1, 11):
            self.assertEquals("message %s" % (i), queue.get(timeout = 1).content.body)
        self.assertEmpty(queue)

        #ack all but the third message (command id 2)
        channel.execution_complete(cumulative_execution_mark=0xFFFFFFFF, ranged_execution_set=[0,1,3,6,7,7,8,9])
        channel.message_recover()
        self.assertEquals("message 3", queue.get(timeout = 1).content.body)
        self.assertEmpty(queue)

    def test_subscribe_not_acquired_2(self):
        channel = self.channel

        #publish some messages
        self.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        for i in range(1, 11):
            channel.message_transfer(content=Content(properties={'routing_key' : "q"}, body = "message-%d" % (i)))

        #consume some of them
        channel.message_subscribe(queue = "q", destination = "a", confirm_mode = 1)
        channel.message_flow_mode(mode = 0, destination = "a")
        channel.message_flow(unit = 0, value = 5, destination = "a")
        channel.message_flow(unit = 1, value = 0xFFFFFFFF, destination = "a")

        queue = self.client.queue("a")
        for i in range(1, 6):
            msg = queue.get(timeout = 1)
            self.assertEquals("message-%d" % (i), msg.content.body)
            msg.complete()
        self.assertEmpty(queue)

        #now create a not-acquired subscriber
        channel.message_subscribe(queue = "q", destination = "b", confirm_mode = 1, acquire_mode=1)
        channel.message_flow(unit = 1, value = 0xFFFFFFFF, destination = "b")

        #check it gets those not consumed
        queue = self.client.queue("b")
        channel.message_flow(unit = 0, value = 1, destination = "b")
        for i in range(6, 11):
            msg = queue.get(timeout = 1)
            self.assertEquals("message-%d" % (i), msg.content.body)
            msg.complete()
        channel.message_flow(unit = 0, value = 1, destination = "b")
        self.assertEmpty(queue)

        #check all 'browsed' messages are still on the queue
        self.assertEqual(5, channel.queue_query(queue="q").message_count)

    def test_no_size(self):
        self.queue_declare(queue = "q", exclusive=True, auto_delete=True)

        ch = self.channel
        ch.message_transfer(content=SizelessContent(properties={'routing_key' : "q"}, body="message-body"))

        ch.message_subscribe(queue = "q", destination="d", confirm_mode = 0)
        ch.message_flow(unit = 0, value = 0xFFFFFFFF, destination = "d")
        ch.message_flow(unit = 1, value = 0xFFFFFFFF, destination = "d")

        queue = self.client.queue("d")
        msg = queue.get(timeout = 3)
        self.assertEquals("message-body", msg.content.body)

    def assertDataEquals(self, channel, msg, expected):
        self.assertEquals(expected, msg.content.body)

    def assertEmpty(self, queue):
        try:
            extra = queue.get(timeout=1)
            self.fail("Queue not empty, contains: " + extra.content.body)
        except Empty: None

class SizelessContent(Content):

    def size(self):
        return None
