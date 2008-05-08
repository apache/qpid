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
from qpid.testlib import TestBase010
from qpid.datatypes import Message, RangedSet
from qpid.session import SessionException

from qpid.content import Content


class MessageTests(TestBase010):
    """Tests for 'methods' on the amqp message 'class'"""

    def test_no_local(self):
        """
        NOTE: this is a test of a QPID specific feature
        
        Test that the qpid specific no_local arg is honoured.
        """
        session = self.session
        #setup, declare two queues one of which excludes delivery of locally sent messages
        session.queue_declare(queue="test-queue-1a", exclusive=True, auto_delete=True)
        session.queue_declare(queue="test-queue-1b", exclusive=True, auto_delete=True, arguments={'no-local':'true'})
        #establish two consumers 
        self.subscribe(destination="local_included", queue="test-queue-1a")
        self.subscribe(destination="local_excluded", queue="test-queue-1b")

        #send a message
        session.message_transfer(message=Message(session.delivery_properties(routing_key="test-queue-1a"), "deliver-me"))
        session.message_transfer(message=Message(session.delivery_properties(routing_key="test-queue-1b"), "dont-deliver-me"))

        #send a message from another session on the same connection to each queue
        session2 = self.conn.session("my-local-session")
        session2.message_transfer(message=Message(session2.delivery_properties(routing_key="test-queue-1a"), "deliver-me-as-well"))
        session2.message_transfer(message=Message(session2.delivery_properties(routing_key="test-queue-1b"), "dont-deliver-me-either"))

        #send a message from a session on another connection to each queue
        for q in ["test-queue-1a", "test-queue-1b"]:
            session.exchange_bind(queue=q, exchange="amq.fanout", binding_key="my-key")
        other = self.connect()
        session3 = other.session("my-other-session")
        session3.message_transfer(destination="amq.fanout", message=Message("i-am-not-local"))
        other.close()

        #check the queues of the two consumers
        excluded = session.incoming("local_excluded")
        included = session.incoming("local_included")
        for b in ["deliver-me", "deliver-me-as-well", "i-am-not-local"]:
            msg = included.get(timeout=1)
            self.assertEqual(b, msg.body)
        msg = excluded.get(timeout=1)
        self.assertEqual("i-am-not-local", msg.body)
        try:
            excluded.get(timeout=1)
            self.fail("Received locally published message though no_local=true")
        except Empty: None

    def test_no_local_awkward(self):

        """
        NOTE: this is a test of a QPID specific feature
        
        Check that messages which will be excluded through no-local
        processing will not block subsequent deliveries
        """

        session = self.session
        #setup:
        session.queue_declare(queue="test-queue", exclusive=True, auto_delete=True, arguments={'no-local':'true'})
        #establish consumer which excludes delivery of locally sent messages
        self.subscribe(destination="local_excluded", queue="test-queue")

        #send a 'local' message
        session.message_transfer(message=Message(session.delivery_properties(routing_key="test-queue"), "local"))

        #send a non local message
        other = self.connect()
        session2 = other.session("my-session", 1)
        session2.message_transfer(message=Message(session2.delivery_properties(routing_key="test-queue"), "foreign"))
        session2.close()
        other.close()

        #check that the second message only is delivered
        excluded = session.incoming("local_excluded")
        msg = excluded.get(timeout=1)
        self.assertEqual("foreign", msg.body)
        try:
            excluded.get(timeout=1)
            self.fail("Received extra message")
        except Empty: None
        #check queue is empty
        self.assertEqual(0, session.queue_query(queue="test-queue").message_count)

    def test_no_local_exclusive_subscribe(self):
        """
        NOTE: this is a test of a QPID specific feature

        Test that the no_local processing works on queues not declared
        as exclusive, but with an exclusive subscription
        """
        session = self.session

        #setup, declare two queues one of which excludes delivery of
        #locally sent messages but is not declared as exclusive
        session.queue_declare(queue="test-queue-1a", exclusive=True, auto_delete=True)
        session.queue_declare(queue="test-queue-1b", auto_delete=True, arguments={'no-local':'true'})
        #establish two consumers 
        self.subscribe(destination="local_included", queue="test-queue-1a")
        self.subscribe(destination="local_excluded", queue="test-queue-1b", exclusive=True)

        #send a message from the same session to each queue
        session.message_transfer(message=Message(session.delivery_properties(routing_key="test-queue-1a"), "deliver-me"))
        session.message_transfer(message=Message(session.delivery_properties(routing_key="test-queue-1b"), "dont-deliver-me"))

        #send a message from another session on the same connection to each queue
        session2 = self.conn.session("my-session")
        session2.message_transfer(message=Message(session2.delivery_properties(routing_key="test-queue-1a"), "deliver-me-as-well"))
        session2.message_transfer(message=Message(session2.delivery_properties(routing_key="test-queue-1b"), "dont-deliver-me-either"))

        #send a message from a session on another connection to each queue
        for q in ["test-queue-1a", "test-queue-1b"]:
            session.exchange_bind(queue=q, exchange="amq.fanout", binding_key="my-key")
        other = self.connect()
        session3 = other.session("my-other-session")
        session3.message_transfer(destination="amq.fanout", message=Message("i-am-not-local"))
        other.close()

        #check the queues of the two consumers
        excluded = session.incoming("local_excluded")
        included = session.incoming("local_included")
        for b in ["deliver-me", "deliver-me-as-well", "i-am-not-local"]:
            msg = included.get(timeout=1)
            self.assertEqual(b, msg.body)
        msg = excluded.get(timeout=1)
        self.assertEqual("i-am-not-local", msg.body)
        try:
            excluded.get(timeout=1)
            self.fail("Received locally published message though no_local=true")
        except Empty: None


    def test_consume_exclusive(self):
        """
        Test an exclusive consumer prevents other consumer being created
        """
        session = self.session
        session.queue_declare(queue="test-queue-2", exclusive=True, auto_delete=True)
        session.message_subscribe(destination="first", queue="test-queue-2", exclusive=True)
        try:
            session.message_subscribe(destination="second", queue="test-queue-2")
            self.fail("Expected consume request to fail due to previous exclusive consumer")
        except SessionException, e:
            self.assertEquals(405, e.args[0].error_code)

    def test_consume_exclusive2(self):
        """
        Check that an exclusive consumer cannot be created if a consumer already exists:
        """
        session = self.session
        session.queue_declare(queue="test-queue-2", exclusive=True, auto_delete=True)
        session.message_subscribe(destination="first", queue="test-queue-2")
        try:
            session.message_subscribe(destination="second", queue="test-queue-2", exclusive=True)
            self.fail("Expected exclusive consume request to fail due to previous consumer")
        except SessionException, e:
            self.assertEquals(405, e.args[0].error_code)

    def test_consume_queue_not_found(self):
        """
        Test error conditions associated with the queue field of the consume method:
        """
        session = self.session
        try:
            #queue specified but doesn't exist:
            session.message_subscribe(queue="invalid-queue", destination="a")
            self.fail("Expected failure when consuming from non-existent queue")
        except SessionException, e:
            self.assertEquals(404, e.args[0].error_code)

    def test_consume_queue_not_specified(self):
        session = self.session
        try:
            #queue not specified and none previously declared for channel:
            session.message_subscribe(destination="a")
            self.fail("Expected failure when consuming from unspecified queue")
        except SessionException, e:
            self.assertEquals(531, e.args[0].error_code)

    def test_consume_unique_consumers(self):
        """
        Ensure unique consumer tags are enforced
        """
        session = self.session
        #setup, declare a queue:
        session.queue_declare(queue="test-queue-3", exclusive=True, auto_delete=True)

        #check that attempts to use duplicate tags are detected and prevented:
        session.message_subscribe(destination="first", queue="test-queue-3")
        try:
            session.message_subscribe(destination="first", queue="test-queue-3")
            self.fail("Expected consume request to fail due to non-unique tag")
        except SessionException, e:
            self.assertEquals(530, e.args[0].error_code)

    def test_cancel(self):
        """
        Test compliance of the basic.cancel method
        """
        session = self.session
        #setup, declare a queue:
        session.queue_declare(queue="test-queue-4", exclusive=True, auto_delete=True)
        session.message_transfer(message=Message(session.delivery_properties(routing_key="test-queue-4"), "One"))

        session.message_subscribe(destination="my-consumer", queue="test-queue-4")
        session.message_flow(destination="my-consumer", unit=session.credit_unit.message, value=0xFFFFFFFF)
        session.message_flow(destination="my-consumer", unit=session.credit_unit.byte, value=0xFFFFFFFF)

        #should flush here

        #cancel should stop messages being delivered
        session.message_cancel(destination="my-consumer")
        session.message_transfer(message=Message(session.delivery_properties(routing_key="test-queue-4"), "Two"))
        myqueue = session.incoming("my-consumer")
        msg = myqueue.get(timeout=1)
        self.assertEqual("One", msg.body)
        try:
            msg = myqueue.get(timeout=1)
            self.fail("Got message after cancellation: " + msg)
        except Empty: None

        #cancellation of non-existant consumers should be handled without error
        session.message_cancel(destination="my-consumer")
        session.message_cancel(destination="this-never-existed")


    def test_ack(self):
        """
        Test basic ack/recover behaviour
        """
        session = self.conn.session("alternate-session", timeout=10)
        session.queue_declare(queue="test-ack-queue", auto_delete=True)

        session.message_subscribe(queue = "test-ack-queue", destination = "consumer")
        session.message_flow(destination="consumer", unit=session.credit_unit.message, value=0xFFFFFFFF)
        session.message_flow(destination="consumer", unit=session.credit_unit.byte, value=0xFFFFFFFF)
        queue = session.incoming("consumer")

        delivery_properties = session.delivery_properties(routing_key="test-ack-queue")
        for i in ["One", "Two", "Three", "Four", "Five"]:
            session.message_transfer(message=Message(delivery_properties, i))

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

        session.message_accept(RangedSet(msg1.id, msg2.id, msg4.id))#One, Two and Four

        #subscribe from second session here to ensure queue is not
        #auto-deleted when alternate session closes (no need to ack on these):
        self.session.message_subscribe(queue = "test-ack-queue", destination = "checker", accept_mode=1)

        #now close the session, and see that the unacked messages are
        #then redelivered to another subscriber:
        session.close(timeout=10)

        session = self.session
        session.message_flow(destination="checker", unit=session.credit_unit.message, value=0xFFFFFFFF)
        session.message_flow(destination="checker", unit=session.credit_unit.byte, value=0xFFFFFFFF)
        queue = session.incoming("checker")

        msg3b = queue.get(timeout=1)
        msg5b = queue.get(timeout=1)

        self.assertEqual("Three", msg3b.body)
        self.assertEqual("Five", msg5b.body)

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message: " + extra.body)
        except Empty: None


    def test_recover(self):
        """
        Test recover behaviour
        """
        session = self.session
        session.queue_declare(queue="queue-a", exclusive=True, auto_delete=True)
        session.queue_bind(exchange="amq.fanout", queue="queue-a")
        session.queue_declare(queue="queue-b", exclusive=True, auto_delete=True)
        session.queue_bind(exchange="amq.fanout", queue="queue-b")

        self.subscribe(queue="queue-a", destination="unconfirmed", confirm_mode=1)
        self.subscribe(queue="queue-b", destination="confirmed", confirm_mode=0)
        confirmed = session.incoming("confirmed")
        unconfirmed = session.incoming("unconfirmed")

        data = ["One", "Two", "Three", "Four", "Five"]
        for d in data:
            session.message_transfer(destination="amq.fanout", content=Content(body=d))

        for q in [confirmed, unconfirmed]:
            for d in data:
                self.assertEqual(d, q.get(timeout=1).content.body)
            self.assertEmpty(q)

        session.message_recover(requeue=False)

        self.assertEmpty(confirmed)

        while len(data):
            msg = None
            for d in data:
                msg = unconfirmed.get(timeout=1)
                self.assertEqual(d, msg.body)
                self.assertEqual(True, msg.content['redelivered'])
            self.assertEmpty(unconfirmed)
            data.remove(msg.body)
            msg.complete(cumulative=False)
            session.message_recover(requeue=False)


    def test_recover_requeue(self):
        """
        Test requeing on recovery
        """
        session = self.session
        session.queue_declare(queue="test-requeue", exclusive=True, auto_delete=True)

        self.subscribe(queue="test-requeue", destination="consumer_tag", confirm_mode=1)
        queue = session.incoming("consumer_tag")

        session.message_transfer(content=Content(properties={'routing_key' : "test-requeue"}, body="One"))
        session.message_transfer(content=Content(properties={'routing_key' : "test-requeue"}, body="Two"))
        session.message_transfer(content=Content(properties={'routing_key' : "test-requeue"}, body="Three"))
        session.message_transfer(content=Content(properties={'routing_key' : "test-requeue"}, body="Four"))
        session.message_transfer(content=Content(properties={'routing_key' : "test-requeue"}, body="Five"))

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

        session.message_cancel(destination="consumer_tag")

        #publish a new message
        session.message_transfer(content=Content(properties={'routing_key' : "test-requeue"}, body="Six"))
        #requeue unacked messages (Three and Five)
        session.message_recover(requeue=True)

        self.subscribe(queue="test-requeue", destination="consumer_tag")
        queue2 = session.incoming("consumer_tag")

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
        session = self.session
        session.queue_declare(queue="test-prefetch-count", exclusive=True, auto_delete=True)
        subscription = self.subscribe(queue="test-prefetch-count", destination="consumer_tag", confirm_mode=1)
        queue = session.incoming("consumer_tag")

        #set prefetch to 5:
        session.message_qos(prefetch_count=5)

        #publish 10 messages:
        for i in range(1, 11):
            session.message_transfer(content=Content(properties={'routing_key' : "test-prefetch-count"}, body="Message %d" % i))

        #only 5 messages should have been delivered:
        for i in range(1, 6):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected 6th message in original queue: " + extra.content.body)
        except Empty: None

        #ack messages and check that the next set arrive ok:
        msg.complete()

        for i in range(6, 11):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)

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
        session = self.session
        session.queue_declare(queue="test-prefetch-size", exclusive=True, auto_delete=True)
        subscription = self.subscribe(queue="test-prefetch-size", destination="consumer_tag", confirm_mode=1)
        queue = session.incoming("consumer_tag")

        #set prefetch to 50 bytes (each message is 9 or 10 bytes):
        session.message_qos(prefetch_size=50)

        #publish 10 messages:
        for i in range(1, 11):
            session.message_transfer(content=Content(properties={'routing_key' : "test-prefetch-size"}, body="Message %d" % i))

        #only 5 messages should have been delivered (i.e. 45 bytes worth):
        for i in range(1, 6):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected 6th message in original queue: " + extra.content.body)
        except Empty: None

        #ack messages and check that the next set arrive ok:
        msg.complete()

        for i in range(6, 11):
            msg = queue.get(timeout=1)
            self.assertEqual("Message %d" % i, msg.body)

        msg.complete()

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected 11th message in original queue: " + extra.content.body)
        except Empty: None

        #make sure that a single oversized message still gets delivered
        large = "abcdefghijklmnopqrstuvwxyz"
        large = large + "-" + large;
        session.message_transfer(content=Content(properties={'routing_key' : "test-prefetch-size"}, body=large))
        msg = queue.get(timeout=1)
        self.assertEqual(large, msg.body)

    def test_reject(self):
        session = self.session
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True, alternate_exchange="amq.fanout")
        session.queue_declare(queue = "r", exclusive=True, auto_delete=True)
        session.exchange_bind(queue = "r", exchange = "amq.fanout")

        session.message_subscribe(queue = "q", destination = "consumer")
        session.message_flow(destination="consumer", unit=session.credit_unit.message, value=0xFFFFFFFF)
        session.message_flow(destination="consumer", unit=session.credit_unit.byte, value=0xFFFFFFFF)
        session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "blah, blah"))
        msg = session.incoming("consumer").get(timeout = 1)
        self.assertEquals(msg.body, "blah, blah")
        session.message_reject(RangedSet(msg.id))

        session.message_subscribe(queue = "r", destination = "checker")
        session.message_flow(destination="checker", unit=session.credit_unit.message, value=0xFFFFFFFF)
        session.message_flow(destination="checker", unit=session.credit_unit.byte, value=0xFFFFFFFF)
        msg = session.incoming("checker").get(timeout = 1)
        self.assertEquals(msg.body, "blah, blah")

    def test_credit_flow_messages(self):
        """
        Test basic credit based flow control with unit = message
        """
        #declare an exclusive queue
        session = self.session
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        #create consumer (for now that defaults to infinite credit)
        session.message_subscribe(queue = "q", destination = "c")
        session.message_set_flow_mode(flow_mode = 0, destination = "c")
        #send batch of messages to queue
        for i in range(1, 11):
            session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "Message %d" % i))

        #set message credit to finite amount (less than enough for all messages)
        session.message_flow(unit = session.credit_unit.message, value = 5, destination = "c")
        #set infinite byte credit
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFF, destination = "c")
        #check that expected number were received
        q = session.incoming("c")
        for i in range(1, 6):
            self.assertDataEquals(session, q.get(timeout = 1), "Message %d" % i)
        self.assertEmpty(q)

        #increase credit again and check more are received
        for i in range(6, 11):
            session.message_flow(unit = session.credit_unit.message, value = 1, destination = "c")
            self.assertDataEquals(session, q.get(timeout = 1), "Message %d" % i)
            self.assertEmpty(q)

    def test_credit_flow_bytes(self):
        """
        Test basic credit based flow control with unit = bytes
        """
        #declare an exclusive queue
        session = self.session
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        #create consumer (for now that defaults to infinite credit)
        session.message_subscribe(queue = "q", destination = "c")
        session.message_set_flow_mode(flow_mode = 0, destination = "c")
        #send batch of messages to queue
        for i in range(10):
            session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "abcdefgh"))

        #each message is currently interpreted as requiring msg_size bytes of credit
        msg_size = 21

        #set byte credit to finite amount (less than enough for all messages)
        session.message_flow(unit = session.credit_unit.byte, value = msg_size*5, destination = "c")
        #set infinite message credit
        session.message_flow(unit = session.credit_unit.message, value = 0xFFFFFFFF, destination = "c")
        #check that expected number were received
        q = session.incoming("c")
        for i in range(5):
            self.assertDataEquals(session, q.get(timeout = 1), "abcdefgh")
        self.assertEmpty(q)

        #increase credit again and check more are received
        for i in range(5):
            session.message_flow(unit = session.credit_unit.byte, value = msg_size, destination = "c")
            self.assertDataEquals(session, q.get(timeout = 1), "abcdefgh")
            self.assertEmpty(q)


    def test_window_flow_messages(self):
        """
        Test basic window based flow control with unit = message
        """
        #declare an exclusive queue
        session = self.session
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        #create consumer (for now that defaults to infinite credit)
        session.message_subscribe(queue = "q", destination = "c")
        session.message_set_flow_mode(flow_mode = 1, destination = "c")
        #send batch of messages to queue
        for i in range(1, 11):
            session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "Message %d" % i))

        #set message credit to finite amount (less than enough for all messages)
        session.message_flow(unit = session.credit_unit.message, value = 5, destination = "c")
        #set infinite byte credit
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFF, destination = "c")
        #check that expected number were received
        q = session.incoming("c")
        for i in range(1, 6):            
            msg = q.get(timeout = 1)
            session.receiver._completed.add(msg.id)#TODO: this may be done automatically
            self.assertDataEquals(session, msg, "Message %d" % i)
        self.assertEmpty(q)

        #acknowledge messages and check more are received
        #TODO: there may be a nicer way of doing this
        session.channel.session_completed(session.receiver._completed)

        for i in range(6, 11):
            self.assertDataEquals(session, q.get(timeout = 1), "Message %d" % i)
        self.assertEmpty(q)


    def test_window_flow_bytes(self):
        """
        Test basic window based flow control with unit = bytes
        """
        #declare an exclusive queue
        session = self.session
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        #create consumer (for now that defaults to infinite credit)
        session.message_subscribe(queue = "q", destination = "c")
        session.message_set_flow_mode(flow_mode = 1, destination = "c")
        #send batch of messages to queue
        for i in range(10):
            session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "abcdefgh"))

        #each message is currently interpreted as requiring msg_size bytes of credit
        msg_size = 19

        #set byte credit to finite amount (less than enough for all messages)
        session.message_flow(unit = session.credit_unit.byte, value = msg_size*5, destination = "c")
        #set infinite message credit
        session.message_flow(unit = session.credit_unit.message, value = 0xFFFFFFFF, destination = "c")
        #check that expected number were received
        q = session.incoming("c")
        msgs = []
        for i in range(5):
            msg = q.get(timeout = 1)
            msgs.append(msg)
            self.assertDataEquals(session, msg, "abcdefgh")
        self.assertEmpty(q)

        #ack each message individually and check more are received
        for i in range(5):
            msg = msgs.pop()
            #TODO: there may be a nicer way of doing this
            session.receiver._completed.add(msg.id)
            session.channel.session_completed(session.receiver._completed)
            self.assertDataEquals(session, q.get(timeout = 1), "abcdefgh")
            self.assertEmpty(q)

    def test_subscribe_not_acquired(self):
        """
        Test the not-acquired modes works as expected for a simple case
        """
        session = self.session
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        for i in range(1, 6):
            session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "Message %s" % i))

        session.message_subscribe(queue = "q", destination = "a", acquire_mode = 1)
        session.message_flow(unit = session.credit_unit.message, value = 0xFFFFFFFF, destination = "a")
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFF, destination = "a")
        session.message_subscribe(queue = "q", destination = "b", acquire_mode = 1)
        session.message_flow(unit = session.credit_unit.message, value = 0xFFFFFFFF, destination = "b")
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFF, destination = "b")

        for i in range(6, 11):
            session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "Message %s" % i))

        #both subscribers should see all messages
        qA = session.incoming("a")
        qB = session.incoming("b")
        for i in range(1, 11):
            for q in [qA, qB]:
                msg = q.get(timeout = 1)
                self.assertEquals("Message %s" % i, msg.body)
                #TODO: tidy up completion
                session.receiver._completed.add(msg.id)

        #TODO: tidy up completion
        session.channel.session_completed(session.receiver._completed)
        #messages should still be on the queue:
        self.assertEquals(10, session.queue_query(queue = "q").message_count)

    def test_acquire(self):
        """
        Test explicit acquire function
        """
        session = self.session
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)

        #use fanout for now:
        session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "acquire me"))

        session.message_subscribe(queue = "q", destination = "a", acquire_mode = 1)
        session.message_flow(destination="a", unit=session.credit_unit.message, value=0xFFFFFFFF)
        session.message_flow(destination="a", unit=session.credit_unit.byte, value=0xFFFFFFFF)
        msg = session.incoming("a").get(timeout = 1)
        self.assertEquals("acquire me", msg.body)
        #message should still be on the queue:
        self.assertEquals(1, session.queue_query(queue = "q").message_count)

        transfers = RangedSet(msg.id)
        response = session.message_acquire(transfers)
        #check that we get notification (i.e. message_acquired)
        self.assert_(msg.id in response.transfers)
        #message should have been removed from the queue:
        self.assertEquals(0, session.queue_query(queue = "q").message_count)
        session.message_accept(transfers)


    def test_release(self):
        """
        Test explicit release function
        """
        session = self.session
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)

        session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "release me"))

        session.message_subscribe(queue = "q", destination = "a")
        session.message_flow(destination="a", unit=session.credit_unit.message, value=0xFFFFFFFF)
        session.message_flow(destination="a", unit=session.credit_unit.byte, value=0xFFFFFFFF)
        msg = session.incoming("a").get(timeout = 1)
        self.assertEquals("release me", msg.body)
        session.message_cancel(destination = "a")
        session.message_release(RangedSet(msg.id))

        #message should not have been removed from the queue:
        self.assertEquals(1, session.queue_query(queue = "q").message_count)

    def test_release_ordering(self):
        """
        Test order of released messages is as expected
        """
        session = self.session
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        for i in range (1, 11):
            session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "released message %s" % (i)))

        session.message_subscribe(queue = "q", destination = "a")
        session.message_flow(unit = session.credit_unit.message, value = 10, destination = "a")
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFF, destination = "a")
        queue = session.incoming("a")
        first = queue.get(timeout = 1)
        for i in range(2, 10):
            msg = queue.get(timeout = 1)
            self.assertEquals("released message %s" % (i), msg.body)
            
        last = queue.get(timeout = 1)
        self.assertEmpty(queue)
        released = RangedSet()
        released.add(first.id, last.id)
        session.message_release(released)

        #TODO: may want to clean this up...
        session.receiver._completed.add(first.id, last.id)
        session.channel.session_completed(session.receiver._completed)
        
        for i in range(1, 11):
            self.assertEquals("released message %s" % (i), queue.get(timeout = 1).body)

    def test_ranged_ack(self):
        """
        Test acking of messages ranges
        """
        session = self.conn.session("alternate-session", timeout=10)

        session.queue_declare(queue = "q", auto_delete=True)
        delivery_properties = session.delivery_properties(routing_key="q")
        for i in range (1, 11):
            session.message_transfer(message=Message(delivery_properties, "message %s" % (i)))

        session.message_subscribe(queue = "q", destination = "a")
        session.message_flow(unit = session.credit_unit.message, value = 10, destination = "a")
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFF, destination = "a")
        queue = session.incoming("a")
        ids = []
        for i in range (1, 11):
            msg = queue.get(timeout = 1)
            self.assertEquals("message %s" % (i), msg.body)
            ids.append(msg.id)
            
        self.assertEmpty(queue)

        #ack all but the fourth message (command id 2)
        accepted = RangedSet()
        accepted.add(ids[0], ids[2])
        accepted.add(ids[4], ids[9])
        session.message_accept(accepted)

        #subscribe from second session here to ensure queue is not
        #auto-deleted when alternate session closes (no need to ack on these):
        self.session.message_subscribe(queue = "q", destination = "checker")

        #now close the session, and see that the unacked messages are
        #then redelivered to another subscriber:
        session.close(timeout=10)

        session = self.session
        session.message_flow(destination="checker", unit=session.credit_unit.message, value=0xFFFFFFFF)
        session.message_flow(destination="checker", unit=session.credit_unit.byte, value=0xFFFFFFFF)
        queue = session.incoming("checker")

        self.assertEquals("message 4", queue.get(timeout = 1).body)
        self.assertEmpty(queue)

    def test_subscribe_not_acquired_2(self):
        session = self.session

        #publish some messages
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        for i in range(1, 11):
            session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "message-%d" % (i)))

        #consume some of them
        session.message_subscribe(queue = "q", destination = "a")
        session.message_set_flow_mode(flow_mode = 0, destination = "a")
        session.message_flow(unit = session.credit_unit.message, value = 5, destination = "a")
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFF, destination = "a")

        queue = session.incoming("a")
        for i in range(1, 6):
            msg = queue.get(timeout = 1)
            self.assertEquals("message-%d" % (i), msg.body)
            #complete and accept
            session.message_accept(RangedSet(msg.id))
            #TODO: tidy up completion
            session.receiver._completed.add(msg.id)
            session.channel.session_completed(session.receiver._completed)
        self.assertEmpty(queue)

        #now create a not-acquired subscriber
        session.message_subscribe(queue = "q", destination = "b", acquire_mode=1)
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFF, destination = "b")

        #check it gets those not consumed
        queue = session.incoming("b")
        session.message_flow(unit = session.credit_unit.message, value = 1, destination = "b")
        for i in range(6, 11):
            msg = queue.get(timeout = 1)
            self.assertEquals("message-%d" % (i), msg.body)
            session.message_release(RangedSet(msg.id))
            #TODO: tidy up completion
            session.receiver._completed.add(msg.id)
            session.channel.session_completed(session.receiver._completed)
        session.message_flow(unit = session.credit_unit.message, value = 1, destination = "b")
        self.assertEmpty(queue)

        #check all 'browsed' messages are still on the queue
        self.assertEqual(5, session.queue_query(queue="q").message_count)

    def test_subscribe_not_acquired_3(self):
        session = self.session

        #publish some messages
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        for i in range(1, 11):
            session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "message-%d" % (i)))

        #create a not-acquired subscriber
        session.message_subscribe(queue = "q", destination = "a", acquire_mode=1)
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFF, destination = "a")
        session.message_flow(unit = session.credit_unit.message, value = 10, destination = "a")

        #browse through messages
        queue = session.incoming("a")
        for i in range(1, 11):
            msg = queue.get(timeout = 1)
            self.assertEquals("message-%d" % (i), msg.body)
            if (i % 2):
                #try to acquire every second message
                response = session.message_acquire(RangedSet(msg.id))
                #check that acquire succeeds
                self.assert_(msg.id in response.transfers)
                session.message_accept(RangedSet(msg.id))
            else:
                session.message_release(RangedSet(msg.id))
            session.receiver._completed.add(msg.id)
            session.channel.session_completed(session.receiver._completed)
        self.assertEmpty(queue)

        #create a second not-acquired subscriber
        session.message_subscribe(queue = "q", destination = "b", acquire_mode=1)
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFF, destination = "b")
        session.message_flow(unit = session.credit_unit.message, value = 1, destination = "b")
        #check it gets those not consumed
        queue = session.incoming("b")
        for i in [2,4,6,8,10]:
            msg = queue.get(timeout = 1)
            self.assertEquals("message-%d" % (i), msg.body)
            session.message_release(RangedSet(msg.id))
            session.receiver._completed.add(msg.id)
            session.channel.session_completed(session.receiver._completed)
        session.message_flow(unit = session.credit_unit.message, value = 1, destination = "b")
        self.assertEmpty(queue)

        #check all 'browsed' messages are still on the queue
        self.assertEqual(5, session.queue_query(queue="q").message_count)

    def test_release_unacquired(self):
        session = self.session

        #create queue
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)

        #send message
        session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "my-message"))

        #create two 'browsers'
        session.message_subscribe(queue = "q", destination = "a", acquire_mode=1)
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFF, destination = "a")
        session.message_flow(unit = session.credit_unit.message, value = 10, destination = "a")
        queueA = session.incoming("a")

        session.message_subscribe(queue = "q", destination = "b", acquire_mode=1)
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFF, destination = "b")
        session.message_flow(unit = session.credit_unit.message, value = 10, destination = "b")
        queueB = session.incoming("b")
        
        #have each browser release the message
        msgA = queueA.get(timeout = 1)
        session.message_release(RangedSet(msgA.id))

        msgB = queueB.get(timeout = 1)
        session.message_release(RangedSet(msgB.id))
        
        #cancel browsers
        session.message_cancel(destination = "a")
        session.message_cancel(destination = "b")
        
        #create consumer
        session.message_subscribe(queue = "q", destination = "c")
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFF, destination = "c")
        session.message_flow(unit = session.credit_unit.message, value = 10, destination = "c")
        queueC = session.incoming("c")
        #consume the message then ack it
        msgC = queueC.get(timeout = 1)
        session.message_accept(RangedSet(msgC.id))
        #ensure there are no other messages
        self.assertEmpty(queueC)

    def test_no_size(self):
        self.queue_declare(queue = "q", exclusive=True, auto_delete=True)

        ssn = self.session
        ssn.message_transfer(content=SizelessContent(properties={'routing_key' : "q"}, body="message-body"))

        ssn.message_subscribe(queue = "q", destination="d", confirm_mode = 0)
        ssn.message_flow(unit = ssn.credit_unit.message, value = 0xFFFFFFFF, destination = "d")
        ssn.message_flow(unit = ssn.credit_unit.byte, value = 0xFFFFFFFF, destination = "d")

        queue = session.incoming("d")
        msg = queue.get(timeout = 3)
        self.assertEquals("message-body", msg.body)

    def test_empty_body(self):
        session = self.session
        session.queue_declare(queue="xyz", exclusive=True, auto_delete=True)
        props = session.delivery_properties(routing_key="xyz")
        session.message_transfer(message=Message(props, ""))

        consumer_tag = "tag1"
        session.message_subscribe(queue="xyz", destination=consumer_tag)
        session.message_flow(unit = session.credit_unit.message, value = 0xFFFFFFFF, destination = consumer_tag)
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFF, destination = consumer_tag)
        queue = session.incoming(consumer_tag)
        msg = queue.get(timeout=1)
        self.assertEquals("", msg.body)
        session.message_accept(RangedSet(msg.id))

    def assertDataEquals(self, session, msg, expected):
        self.assertEquals(expected, msg.body)

    def assertEmpty(self, queue):
        try:
            extra = queue.get(timeout=1)
            self.fail("Queue not empty, contains: " + extra.body)
        except Empty: None

class SizelessContent(Content):

    def size(self):
        return None
