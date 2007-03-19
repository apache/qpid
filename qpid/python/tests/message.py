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
        channel.message_consume(destination="local_included", queue="test-queue-1a")
        channel.message_consume(destination="local_excluded", queue="test-queue-1b", no_local=True)

        #send a message
        channel.message_transfer(routing_key="test-queue-1a", body="consume_no_local")
        channel.message_transfer(routing_key="test-queue-1b", body="consume_no_local")

        #check the queues of the two consumers
        excluded = self.client.queue("local_excluded")
        included = self.client.queue("local_included")
        msg = included.get(timeout=1)
        self.assertEqual("consume_no_local", msg.body)
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
        channel.message_consume(destination="first", queue="test-queue-2", exclusive=True)
        try:
            channel.message_consume(destination="second", queue="test-queue-2")
            self.fail("Expected consume request to fail due to previous exclusive consumer")
        except Closed, e:
            self.assertChannelException(403, e.args[0])

        #open new channel and cleanup last consumer:    
        channel = self.client.channel(2)
        channel.channel_open()

        #check that an exclusive consumer cannot be created if a consumer already exists:
        channel.message_consume(destination="first", queue="test-queue-2")
        try:
            channel.message_consume(destination="second", queue="test-queue-2", exclusive=True)
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
            channel.message_consume(queue="invalid-queue")
            self.fail("Expected failure when consuming from non-existent queue")
        except Closed, e:
            self.assertChannelException(404, e.args[0])

        channel = self.client.channel(2)
        channel.channel_open()
        try:
            #queue not specified and none previously declared for channel:
            channel.message_consume(queue="")
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
        channel.message_consume(destination="first", queue="test-queue-3")
        try:
            channel.message_consume(destination="first", queue="test-queue-3")
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
        channel.message_consume(destination="my-consumer", queue="test-queue-4")
        channel.message_transfer(routing_key="test-queue-4", body="One")

        #cancel should stop messages being delivered
        channel.message_cancel(destination="my-consumer")
        channel.message_transfer(routing_key="test-queue-4", body="Two")
        myqueue = self.client.queue("my-consumer")
        msg = myqueue.get(timeout=1)
        self.assertEqual("One", msg.body)
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
        
        channel.message_consume(queue="test-ack-queue", destination="consumer_tag", no_ack=False)
        queue = self.client.queue("consumer_tag")

        channel.message_transfer(routing_key="test-ack-queue", body="One")
        channel.message_transfer(routing_key="test-ack-queue", body="Two")
        channel.message_transfer(routing_key="test-ack-queue", body="Three")
        channel.message_transfer(routing_key="test-ack-queue", body="Four")
        channel.message_transfer(routing_key="test-ack-queue", body="Five")
                
        msg1 = queue.get(timeout=1)
        msg2 = queue.get(timeout=1)
        msg3 = queue.get(timeout=1)
        msg4 = queue.get(timeout=1)
        msg5 = queue.get(timeout=1)
        
        self.assertEqual("One", msg1.body)
        self.assertEqual("Two", msg2.body)
        self.assertEqual("Three", msg3.body)
        self.assertEqual("Four", msg4.body)
        self.assertEqual("Five", msg5.body)

        msg1.ok()
        msg2.ok()
        msg4.ok()

        channel.message_recover(requeue=False)
        
        msg3b = queue.get(timeout=1)
        msg5b = queue.get(timeout=1)
        
        self.assertEqual("Three", msg3b.body)
        self.assertEqual("Five", msg5b.body)

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message: " + extra.body)
        except Empty: None

    def test_recover_requeue(self):
        """
        Test requeing on recovery
        """
        channel = self.channel
        channel.queue_declare(queue="test-requeue", exclusive=True)
        
        channel.message_consume(queue="test-requeue", destination="consumer_tag", no_ack=False)
        queue = self.client.queue("consumer_tag")

        channel.message_transfer(routing_key="test-requeue", body="One")
        channel.message_transfer(routing_key="test-requeue", body="Two")
        channel.message_transfer(routing_key="test-requeue", body="Three")
        channel.message_transfer(routing_key="test-requeue", body="Four")
        channel.message_transfer(routing_key="test-requeue", body="Five")
                
        msg1 = queue.get(timeout=1)
        msg2 = queue.get(timeout=1)
        msg3 = queue.get(timeout=1)
        msg4 = queue.get(timeout=1)
        msg5 = queue.get(timeout=1)
        
        self.assertEqual("One", msg1.body)
        self.assertEqual("Two", msg2.body)
        self.assertEqual("Three", msg3.body)
        self.assertEqual("Four", msg4.body)
        self.assertEqual("Five", msg5.body)

        msg1.ok()  #One
        msg2.ok()  #Two
        msg4.ok()  #Two

        channel.message_cancel(destination="consumer_tag")
        channel.message_consume(queue="test-requeue", destination="consumer_tag")
        queue2 = self.client.queue("consumer_tag")

        channel.message_recover(requeue=True)
        
        msg3b = queue2.get(timeout=1)
        msg5b = queue2.get(timeout=1)
        
        self.assertEqual("Three", msg3b.body)
        self.assertEqual("Five", msg5b.body)

        self.assertEqual(True, msg3b.redelivered)
        self.assertEqual(True, msg5b.redelivered)

        try:
            extra = queue2.get(timeout=1)
            self.fail("Got unexpected message in second queue: " + extra.body)
        except Empty: None
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in original queue: " + extra.body)
        except Empty: None
        
        
    def test_qos_prefetch_count(self):
        """
        Test that the prefetch count specified is honoured
        """
        #setup: declare queue and subscribe
        channel = self.channel
        channel.queue_declare(queue="test-prefetch-count", exclusive=True)
        subscription = channel.message_consume(queue="test-prefetch-count", destination="consumer_tag", no_ack=False)
        queue = self.client.queue("consumer_tag")

        #set prefetch to 5:
        channel.message_qos(prefetch_count=5)

        #publish 10 messages:
        for i in range(1, 11):
            channel.message_transfer(routing_key="test-prefetch-count", body="Message %d" % i)

        #only 5 messages should have been delivered:
        msgs = []
        for i in range(1, 6):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)
            msgs.add(msg)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected 6th message in original queue: " + extra.body)
        except Empty: None

        #ack messages and check that the next set arrive ok:
        #todo: once batching is implmented, send a single response for all messages
        for msg in msgs:
            msg.ok()
        msgs.clear()    

        for i in range(6, 11):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)
            msgs.add(msg)

        for msg in msgs:
            msg.ok()
        msgs.clear()    

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected 11th message in original queue: " + extra.body)
        except Empty: None


        
    def test_qos_prefetch_size(self):
        """
        Test that the prefetch size specified is honoured
        """
        #setup: declare queue and subscribe
        channel = self.channel
        channel.queue_declare(queue="test-prefetch-size", exclusive=True)
        subscription = channel.message_consume(queue="test-prefetch-size", destination="consumer_tag", no_ack=False)
        queue = self.client.queue("consumer_tag")

        #set prefetch to 50 bytes (each message is 9 or 10 bytes):
        channel.message_qos(prefetch_size=50)

        #publish 10 messages:
        for i in range(1, 11):
            channel.message_transfer(routing_key="test-prefetch-size", body="Message %d" % i)

        #only 5 messages should have been delivered (i.e. 45 bytes worth):
        msgs = []
        for i in range(1, 6):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)
            msgs.add(msg)

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected 6th message in original queue: " + extra.body)
        except Empty: None

        #ack messages and check that the next set arrive ok:
        for msg in msgs:
            msg.ok()
        msgs.clear()    

        for i in range(6, 11):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)
            msgs.add(msg)

        for msg in msgs:
            msg.ok()
        msgs.clear()    

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected 11th message in original queue: " + extra.body)
        except Empty: None

        #make sure that a single oversized message still gets delivered
        large = "abcdefghijklmnopqrstuvwxyz"
        large = large + "-" + large;
        channel.message_transfer(routing_key="test-prefetch-size", body=large)
        msg = queue.get(timeout=1)
        self.assertEqual(large, msg.body)

    def test_get(self):
        """
        Test message_get method
        """
        channel = self.channel
        channel.queue_declare(queue="test-get", exclusive=True)
        
        #publish some messages (no_ack=True)
        for i in range(1, 11):
            channel.message_transfer(routing_key="test-get", body="Message %d" % i)

        #use message_get to read back the messages, and check that we get an empty at the end
        for i in range(1, 11):
            reply = channel.message_get(no_ack=True)
            self.assertEqual(reply.method.klass.name, "message")
            self.assertEqual(reply.method.name, "get-ok")
            self.assertEqual("Message %d" % i, reply.body)

        reply = channel.message_get(no_ack=True)
        self.assertEqual(reply.method.klass.name, "message")
        self.assertEqual(reply.method.name, "get-empty")

        #repeat for no_ack=False
        for i in range(11, 21):
            channel.message_transfer(routing_key="test-get", body="Message %d" % i)

        for i in range(11, 21):
            reply = channel.message_get(no_ack=False)
            self.assertEqual(reply.method.klass.name, "message")
            self.assertEqual(reply.method.name, "get-ok")
            self.assertEqual("Message %d" % i, reply.body)
            reply.ok()

            #todo: when batching is available, test ack multiple
            #if(i == 13):
            #    channel.message_ack(delivery_tag=reply.delivery_tag, multiple=True)
            #if(i in [15, 17, 19]):
            #    channel.message_ack(delivery_tag=reply.delivery_tag)

        reply = channel.message_get(no_ack=True)
        self.assertEqual(reply.method.klass.name, "message")
        self.assertEqual(reply.method.name, "get-empty")

        #recover(requeue=True)
        channel.message_recover(requeue=True)
        
        #get the unacked messages again (14, 16, 18, 20)
        for i in [14, 16, 18, 20]:
            reply = channel.message_get(no_ack=False)
            self.assertEqual(reply.method.klass.name, "message")
            self.assertEqual(reply.method.name, "get-ok")
            self.assertEqual("Message %d" % i, reply.body)
            reply.ok()
            #channel.message_ack(delivery_tag=reply.delivery_tag)

        reply = channel.message_get(no_ack=True)
        self.assertEqual(reply.method.klass.name, "message")
        self.assertEqual(reply.method.name, "get-empty")

        channel.message_recover(requeue=True)

        reply = channel.message_get(no_ack=True)
        self.assertEqual(reply.method.klass.name, "message")
        self.assertEqual(reply.method.name, "get-empty")
