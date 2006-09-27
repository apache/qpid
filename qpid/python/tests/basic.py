#
# Copyright (c) 2006 The Apache Software Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
from qpid.client import Client, Closed
from qpid.queue import Empty
from qpid.content import Content
from qpid.testlib import testrunner, TestBase

class BasicTests(TestBase):
    """Tests for 'methods' on the amqp basic 'class'"""

    def test_consume_no_local(self):
        """
        Test that the no_local flag is honoured in the consume method
        """
        channel = self.channel
        #setup, declare two queues:
        channel.queue_declare(queue="test-queue-1a", exclusive=True)
        channel.queue_declare(queue="test-queue-1b", exclusive=True)
        #establish two consumers one of which excludes delivery of locally sent messages
        channel.basic_consume(consumer_tag="local_included", queue="test-queue-1a")
        channel.basic_consume(consumer_tag="local_excluded", queue="test-queue-1b", no_local=True)

        #send a message
        channel.basic_publish(routing_key="test-queue-1a", content=Content("consume_no_local"))
        channel.basic_publish(routing_key="test-queue-1b", content=Content("consume_no_local"))

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
        channel.basic_consume(consumer_tag="first", queue="test-queue-2", exclusive=True)
        try:
            channel.basic_consume(consumer_tag="second", queue="test-queue-2")
            self.fail("Expected consume request to fail due to previous exclusive consumer")
        except Closed, e:
            self.assertChannelException(403, e.args[0])

        #open new channel and cleanup last consumer:    
        channel = self.client.channel(2)
        channel.channel_open()

        #check that an exclusive consumer cannot be created if a consumer already exists:
        channel.basic_consume(consumer_tag="first", queue="test-queue-2")
        try:
            channel.basic_consume(consumer_tag="second", queue="test-queue-2", exclusive=True)
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
            channel.basic_consume(queue="invalid-queue")
            self.fail("Expected failure when consuming from non-existent queue")
        except Closed, e:
            self.assertChannelException(404, e.args[0])

        channel = self.client.channel(2)
        channel.channel_open()
        try:
            #queue not specified and none previously declared for channel:
            channel.basic_consume(queue="")
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
        channel.basic_consume(consumer_tag="first", queue="test-queue-3")
        try:
            channel.basic_consume(consumer_tag="first", queue="test-queue-3")
            self.fail("Expected consume request to fail due to non-unique tag")
        except Closed, e:
            self.assertConnectionException(530, e.args[0])

    def test_basic_cancel(self):
        """
        Test compliance of the basic.cancel method
        """
        channel = self.channel
        #setup, declare a queue:
        channel.queue_declare(queue="test-queue-4", exclusive=True)
        channel.basic_consume(consumer_tag="my-consumer", queue="test-queue-4")
        channel.basic_publish(routing_key="test-queue-4", content=Content("One"))

        #cancel should stop messages being delivered
        channel.basic_cancel(consumer_tag="my-consumer")
        channel.basic_publish(routing_key="test-queue-4", content=Content("Two"))
        myqueue = self.client.queue("my-consumer")
        msg = myqueue.get(timeout=1)
        self.assertEqual("One", msg.content.body)
        try:
            msg = myqueue.get(timeout=1) 
            self.fail("Got message after cancellation: " + msg)
        except Empty: None

        #cancellation of non-existant consumers should be handled without error
        channel.basic_cancel(consumer_tag="my-consumer")
        channel.basic_cancel(consumer_tag="this-never-existed")


    def test_basic_ack(self):
        """
        Test basic ack/recover behaviour
        """
        channel = self.channel
        channel.queue_declare(queue="test-ack-queue")
        
        reply = channel.basic_consume(queue="test-ack-queue", no_ack=False)
        queue = self.client.queue(reply.consumer_tag)

        channel.basic_publish(routing_key="test-ack-queue", content=Content("One"))
        channel.basic_publish(routing_key="test-ack-queue", content=Content("Two"))
        channel.basic_publish(routing_key="test-ack-queue", content=Content("Three"))
        channel.basic_publish(routing_key="test-ack-queue", content=Content("Four"))
        channel.basic_publish(routing_key="test-ack-queue", content=Content("Five"))
                
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

        channel.basic_ack(delivery_tag=msg2.delivery_tag, multiple=True)  #One & Two
        channel.basic_ack(delivery_tag=msg4.delivery_tag, multiple=False) #Four

        channel.basic_recover(requeue=False)
        
        msg3b = queue.get(timeout=1)
        msg5b = queue.get(timeout=1)
        
        self.assertEqual("Three", msg3b.content.body)
        self.assertEqual("Five", msg5b.content.body)

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message: " + extra.content.body)
        except Empty: None

    def test_basic_recover_requeue(self):
        """
        Test requeing on recovery
        """
        channel = self.channel
        channel.queue_declare(queue="test-requeue")
        
        subscription = channel.basic_consume(queue="test-requeue", no_ack=False)
        queue = self.client.queue(subscription.consumer_tag)

        channel.basic_publish(routing_key="test-requeue", content=Content("One"))
        channel.basic_publish(routing_key="test-requeue", content=Content("Two"))
        channel.basic_publish(routing_key="test-requeue", content=Content("Three"))
        channel.basic_publish(routing_key="test-requeue", content=Content("Four"))
        channel.basic_publish(routing_key="test-requeue", content=Content("Five"))
                
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

        channel.basic_ack(delivery_tag=msg2.delivery_tag, multiple=True)  #One & Two
        channel.basic_ack(delivery_tag=msg4.delivery_tag, multiple=False) #Four

        channel.basic_cancel(consumer_tag=subscription.consumer_tag)
        subscription2 = channel.basic_consume(queue="test-requeue")
        queue2 = self.client.queue(subscription2.consumer_tag)

        channel.basic_recover(requeue=True)
        
        msg3b = queue2.get(timeout=1)
        msg5b = queue2.get(timeout=1)
        
        self.assertEqual("Three", msg3b.content.body)
        self.assertEqual("Five", msg5b.content.body)

        self.assertTrue(msg3b.redelivered)
        self.assertTrue(msg5b.redelivered)

        try:
            extra = queue2.get(timeout=1)
            self.fail("Got unexpected message in second queue: " + extra.content.body)
        except Empty: None
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in original queue: " + extra.content.body)
        except Empty: None
        
