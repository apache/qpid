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

        msg1.ok(batchoffset=1)#One and Two
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

        msg1.ok(batchoffset=1)  #One and Two
        msg4.ok()  #Four

        channel.message_cancel(destination="consumer_tag")

        #publish a new message
        channel.message_transfer(routing_key="test-requeue", body="Six")
        #requeue unacked messages (Three and Five)
        channel.message_recover(requeue=True)

        channel.message_consume(queue="test-requeue", destination="consumer_tag")
        queue2 = self.client.queue("consumer_tag")
        
        msg3b = queue2.get(timeout=1)
        msg5b = queue2.get(timeout=1)
        
        self.assertEqual("Three", msg3b.body)
        self.assertEqual("Five", msg5b.body)

        self.assertEqual(True, msg3b.redelivered)
        self.assertEqual(True, msg5b.redelivered)

        self.assertEqual("Six", queue2.get(timeout=1).body)

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
        for i in range(1, 6):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected 6th message in original queue: " + extra.body)
        except Empty: None

        #ack messages and check that the next set arrive ok:
        #todo: once batching is implmented, send a single response for all messages
        msg.ok(batchoffset=-4)#1-5

        for i in range(6, 11):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)

        msg.ok(batchoffset=-4)#6-10

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
        for i in range(1, 6):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected 6th message in original queue: " + extra.body)
        except Empty: None

        #ack messages and check that the next set arrive ok:
        msg.ok(batchoffset=-4)#1-5

        for i in range(6, 11):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)

        msg.ok(batchoffset=-4)#6-10

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
            tag = "queue %d" % i
            reply = channel.message_get(no_ack=True, queue="test-get", destination=tag)
            self.assertEqual(reply.method.klass.name, "message")
            self.assertEqual(reply.method.name, "ok")            
            self.assertEqual("Message %d" % i, self.client.queue(tag).get(timeout=1).body)

        reply = channel.message_get(no_ack=True, queue="test-get")
        self.assertEqual(reply.method.klass.name, "message")
        self.assertEqual(reply.method.name, "empty")

        #repeat for no_ack=False
        for i in range(11, 21):
            channel.message_transfer(routing_key="test-get", body="Message %d" % i)

        for i in range(11, 21):
            tag = "queue %d" % i
            reply = channel.message_get(no_ack=False, queue="test-get", destination=tag)
            self.assertEqual(reply.method.klass.name, "message")
            self.assertEqual(reply.method.name, "ok")
            msg = self.client.queue(tag).get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)
            
            if (i==13):
              msg.ok(batchoffset=-2)#11, 12 & 13
            if(i in [15, 17, 19]):
              msg.ok()

        reply = channel.message_get(no_ack=True, queue="test-get")
        self.assertEqual(reply.method.klass.name, "message")
        self.assertEqual(reply.method.name, "empty")

        #recover(requeue=True)
        channel.message_recover(requeue=True)
        
        #get the unacked messages again (14, 16, 18, 20)
        for i in [14, 16, 18, 20]:
            tag = "queue %d" % i
            reply = channel.message_get(no_ack=False, queue="test-get", destination=tag)
            self.assertEqual(reply.method.klass.name, "message")
            self.assertEqual(reply.method.name, "ok")
            msg = self.client.queue(tag).get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)
            msg.ok()
            #channel.message_ack(delivery_tag=reply.delivery_tag)

        reply = channel.message_get(no_ack=True, queue="test-get")
        self.assertEqual(reply.method.klass.name, "message")
        self.assertEqual(reply.method.name, "empty")

        channel.message_recover(requeue=True)

        reply = channel.message_get(no_ack=True, queue="test-get")
        self.assertEqual(reply.method.klass.name, "message")
        self.assertEqual(reply.method.name, "empty")

    def test_reference_simple(self):
        """
        Test basic ability to handle references
        """
        channel = self.channel
        channel.queue_declare(queue="ref_queue", exclusive=True)
        channel.message_consume(queue="ref_queue", destination="c1")
        queue = self.client.queue("c1")

        refId = "myref"
        channel.message_open(reference=refId)
        channel.message_append(reference=refId, bytes="abcd")
        channel.synchronous = False
        ack = channel.message_transfer(routing_key="ref_queue", body=ReferenceId(refId))
        channel.synchronous = True

        channel.message_append(reference=refId, bytes="efgh")
        channel.message_append(reference=refId, bytes="ijkl")
        channel.message_close(reference=refId)

        #first, wait for the ok for the transfer
        ack.get_response(timeout=1)
        
        self.assertDataEquals(channel, queue.get(timeout=1), "abcdefghijkl")


    def test_reference_large(self):
        """
        Test basic ability to handle references whose content exceeds max frame size
        """
        channel = self.channel
        self.queue_declare(queue="ref_queue")

        #generate a big data string (> max frame size of consumer):
        data = "0123456789"
        for i in range(0, 10):
            data += data
        #send it inline    
        channel.synchronous = False
        ack = channel.message_transfer(routing_key="ref_queue", body=data)
        channel.synchronous = True
        #first, wait for the ok for the transfer
        ack.get_response(timeout=1)

        #create a new connection for consumer, with specific max frame size (< data)
        other = self.connect(tune_params={"channel_max":10, "frame_max":5120, "heartbeat":0})
        ch2 = other.channel(1)
        ch2.channel_open()
        ch2.message_consume(queue="ref_queue", destination="c1")
        queue = other.queue("c1")
        
        msg = queue.get(timeout=1)
        self.assertTrue(isinstance(msg.body, ReferenceId))
        self.assertTrue(msg.reference)
        self.assertEquals(data, msg.reference.get_complete())

    def test_reference_completion(self):
        """
        Test that reference transfer are not deemed complete until
        closed (therefore are not acked or routed until that point)
        """
        channel = self.channel
        channel.queue_declare(queue="ref_queue", exclusive=True)
        channel.message_consume(queue="ref_queue", destination="c1")
        queue = self.client.queue("c1")

        refId = "myref"
        channel.message_open(reference=refId)
        channel.message_append(reference=refId, bytes="abcd")
        channel.synchronous = False
        ack = channel.message_transfer(routing_key="ref_queue", body=ReferenceId(refId))
        channel.synchronous = True

        try:
            msg = queue.get(timeout=1)            
            self.fail("Got unexpected message on queue: " + msg)
        except Empty: None
        
        self.assertTrue(not ack.is_complete())

        channel.message_close(reference=refId)

        #first, wait for the ok for the transfer
        ack.get_response(timeout=1)
        
        self.assertDataEquals(channel, queue.get(timeout=1), "abcd")

    def test_reference_multi_transfer(self):
        """
        Test that multiple transfer requests for the same reference are
        correctly handled.
        """
        channel = self.channel
        #declare and consume from two queues
        channel.queue_declare(queue="q-one", exclusive=True)
        channel.queue_declare(queue="q-two", exclusive=True)
        channel.message_consume(queue="q-one", destination="q-one")
        channel.message_consume(queue="q-two", destination="q-two")
        queue1 = self.client.queue("q-one")
        queue2 = self.client.queue("q-two")

        #transfer a single ref to both queues (in separate commands)
        channel.message_open(reference="my-ref")
        channel.synchronous = False
        ack1 = channel.message_transfer(routing_key="q-one", body=ReferenceId("my-ref"))
        channel.message_append(reference="my-ref", bytes="my data")
        ack2 = channel.message_transfer(routing_key="q-two", body=ReferenceId("my-ref"))
        channel.synchronous = True
        channel.message_close(reference="my-ref")

        #check that both queues have the message
        self.assertDataEquals(channel, queue1.get(timeout=1), "my data")
        self.assertDataEquals(channel, queue2.get(timeout=1), "my data")
        self.assertEmpty(queue1)
        self.assertEmpty(queue2)

        #transfer a single ref to the same queue twice (in separate commands)
        channel.message_open(reference="my-ref")
        channel.synchronous = False
        ack1 = channel.message_transfer(routing_key="q-one", message_id="abc", body=ReferenceId("my-ref"))
        channel.message_append(reference="my-ref", bytes="second message")
        ack2 = channel.message_transfer(routing_key="q-one", message_id="xyz", body=ReferenceId("my-ref"))
        channel.synchronous = True
        channel.message_close(reference="my-ref")

        msg1 = queue1.get(timeout=1)
        msg2 = queue1.get(timeout=1)
        #order is undefined
        if msg1.message_id == "abc":
            self.assertEquals(msg2.message_id, "xyz")
        else:
            self.assertEquals(msg1.message_id, "xyz")
            self.assertEquals(msg2.message_id, "abc")
            
        #would be legal for the incoming messages to be transfered
        #inline or by reference in any combination
        
        if isinstance(msg1.body, ReferenceId):            
            self.assertEquals("second message", msg1.reference.get_complete())
            if isinstance(msg2.body, ReferenceId):
                if msg1.body != msg2.body:
                    self.assertEquals("second message", msg2.reference.get_complete())
                #else ok, as same ref as msg1    
        else:
            self.assertEquals("second message", msg1.body)
            if isinstance(msg2.body, ReferenceId):
                self.assertEquals("second message", msg2.reference.get_complete())
            else:
                self.assertEquals("second message", msg2.body)                

        self.assertEmpty(queue1)

    def test_reference_unopened_on_append_error(self):
        channel = self.channel
        try:
            channel.message_append(reference="unopened")
        except Closed, e:
            self.assertConnectionException(503, e.args[0])

    def test_reference_unopened_on_close_error(self):
        channel = self.channel
        try:
            channel.message_close(reference="unopened")
        except Closed, e:
            self.assertConnectionException(503, e.args[0])

    def test_reference_unopened_on_transfer_error(self):
        channel = self.channel
        try:
            channel.message_transfer(body=ReferenceId("unopened"))
        except Closed, e:
            self.assertConnectionException(503, e.args[0])

    def test_reference_already_opened_error(self):
        channel = self.channel
        channel.message_open(reference="a")
        try:
            channel.message_open(reference="a")            
        except Closed, e:
            self.assertConnectionException(503, e.args[0])

    def test_empty_reference(self):
        channel = self.channel
        channel.queue_declare(queue="ref_queue", exclusive=True)
        channel.message_consume(queue="ref_queue", destination="c1")
        queue = self.client.queue("c1")

        refId = "myref"
        channel.message_open(reference=refId)
        channel.synchronous = False
        ack = channel.message_transfer(routing_key="ref_queue", message_id="empty-msg", body=ReferenceId(refId))
        channel.synchronous = True
        channel.message_close(reference=refId)

        #first, wait for the ok for the transfer
        ack.get_response(timeout=1)

        msg = queue.get(timeout=1)
        self.assertEquals(msg.message_id, "empty-msg")
        self.assertDataEquals(channel, msg, "")

    def test_reject(self):
        channel = self.channel
        channel.queue_declare(queue = "q", exclusive=True)

        channel.message_consume(queue = "q", destination = "consumer")
        channel.message_transfer(routing_key = "q", body="blah, blah")
        msg = self.client.queue("consumer").get(timeout = 1)
        self.assertEquals(msg.body, "blah, blah")
        channel.message_cancel(destination = "consumer")
        msg.reject()

        channel.message_consume(queue = "q", destination = "checker")
        msg = self.client.queue("checker").get(timeout = 1)
        self.assertEquals(msg.body, "blah, blah")

    def test_checkpoint(self):
        channel = self.channel
        channel.queue_declare(queue = "q", exclusive=True)

        channel.message_open(reference="my-ref")
        channel.message_append(reference="my-ref", bytes="abcdefgh")
        channel.message_append(reference="my-ref", bytes="ijklmnop")
        channel.message_checkpoint(reference="my-ref", identifier="my-checkpoint")
        channel.channel_close()

        channel = self.client.channel(2)
        channel.channel_open()
        channel.message_consume(queue = "q", destination = "consumer")
        offset = channel.message_resume(reference="my-ref", identifier="my-checkpoint").value
        self.assertTrue(offset<=16)
        channel.message_append(reference="my-ref", bytes="qrstuvwxyz")
        channel.synchronous = False
        channel.message_transfer(routing_key="q-one", message_id="abcd", body=ReferenceId("my-ref"))
        channel.synchronous = True
        channel.message_close(reference="my-ref")

        self.assertDataEquals(channel, self.client.queue("consumer").get(timeout = 1), "abcdefghijklmnopqrstuvwxyz")
        self.assertEmpty(self.client.queue("consumer"))
        
        
    def assertDataEquals(self, channel, msg, expected):
        if isinstance(msg.body, ReferenceId):
            data = msg.reference.get_complete()
        else:
            data = msg.body
        self.assertEquals(expected, data)
