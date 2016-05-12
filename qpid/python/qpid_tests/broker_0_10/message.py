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
from time import sleep

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
        myqueue = session.incoming("my-consumer")
        session.message_flow(destination="my-consumer", unit=session.credit_unit.message, value=0xFFFFFFFFL)
        session.message_flow(destination="my-consumer", unit=session.credit_unit.byte, value=0xFFFFFFFFL)

        #should flush here

        #cancel should stop messages being delivered
        session.message_cancel(destination="my-consumer")
        session.message_transfer(message=Message(session.delivery_properties(routing_key="test-queue-4"), "Two"))
        msg = myqueue.get(timeout=1)
        self.assertEqual("One", msg.body)
        try:
            msg = myqueue.get(timeout=1)
            self.fail("Got message after cancellation: " + msg)
        except Empty: None

        #cancellation of non-existant consumers should be result in 404s
        try:
            session.message_cancel(destination="my-consumer")
            self.fail("Expected 404 for recancellation of subscription.")
        except SessionException, e:
            self.assertEquals(404, e.args[0].error_code)

        session = self.conn.session("alternate-session", timeout=10)
        try:
            session.message_cancel(destination="this-never-existed")
            self.fail("Expected 404 for cancellation of unknown subscription.")
        except SessionException, e:
            self.assertEquals(404, e.args[0].error_code)


    def test_ack(self):
        """
        Test basic ack/recover behaviour using a combination of implicit and
        explicit accept subscriptions.
        """
        self.startQmf()
        session1 = self.conn.session("alternate-session", timeout=10)
        session1.queue_declare(queue="test-ack-queue", auto_delete=True)

        delivery_properties = session1.delivery_properties(routing_key="test-ack-queue")
        for i in ["One", "Two", "Three", "Four", "Five"]:
            session1.message_transfer(message=Message(delivery_properties, i))

        # verify enqueued message count, use both QMF and session query to verify consistency
        self.assertEqual(5, session1.queue_query(queue="test-ack-queue").message_count)
        queueObj = self.qmf.getObjects(_class="queue", name="test-ack-queue")[0]
        self.assertEquals(queueObj.msgDepth, 5)
        self.assertEquals(queueObj.msgTotalEnqueues, 5)
        self.assertEquals(queueObj.msgTotalDequeues, 0)

        # subscribe with implied acquire, explicit accept:
        session1.message_subscribe(queue = "test-ack-queue", destination = "consumer")
        session1.message_flow(destination="consumer", unit=session1.credit_unit.message, value=0xFFFFFFFFL)
        session1.message_flow(destination="consumer", unit=session1.credit_unit.byte, value=0xFFFFFFFFL)
        queue = session1.incoming("consumer")

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

        # messages should not be on the queue:
        self.assertEqual(0, session1.queue_query(queue="test-ack-queue").message_count)
        # QMF shows the dequeues as not having happened yet, since they are have
        # not been accepted
        queueObj.update()
        self.assertEquals(queueObj.msgDepth, 5)
        self.assertEquals(queueObj.msgTotalEnqueues, 5)
        self.assertEquals(queueObj.msgTotalDequeues, 0)

        session1.message_accept(RangedSet(msg1.id, msg2.id, msg4.id))#One, Two and Four

        # QMF should now reflect the accepted messages as being dequeued
        self.assertEqual(0, session1.queue_query(queue="test-ack-queue").message_count)
        queueObj.update()
        self.assertEquals(queueObj.msgDepth, 2)
        self.assertEquals(queueObj.msgTotalEnqueues, 5)
        self.assertEquals(queueObj.msgTotalDequeues, 3)

        #subscribe from second session here to ensure queue is not auto-deleted
        #when alternate session closes.  Use implicit accept mode to test that
        #we don't need to explicitly accept
        session2 = self.conn.session("alternate-session-2", timeout=10)
        session2.message_subscribe(queue = "test-ack-queue", destination = "checker", accept_mode=1)

        #now close the first session, and see that the unaccepted messages are
        #then redelivered to another subscriber:
        session1.close(timeout=10)

        # check the statistics - the queue_query will show the non-accepted
        # messages have been released. QMF never considered them dequeued, so
        # those counts won't change
        self.assertEqual(2, session2.queue_query(queue="test-ack-queue").message_count)
        queueObj.update()
        self.assertEquals(queueObj.msgDepth, 2)
        self.assertEquals(queueObj.msgTotalEnqueues, 5)
        self.assertEquals(queueObj.msgTotalDequeues, 3)

        session2.message_flow(destination="checker", unit=session2.credit_unit.message, value=0xFFFFFFFFL)
        session2.message_flow(destination="checker", unit=session2.credit_unit.byte, value=0xFFFFFFFFL)
        queue = session2.incoming("checker")

        msg3b = queue.get(timeout=1)
        msg5b = queue.get(timeout=1)

        self.assertEqual("Three", msg3b.body)
        self.assertEqual("Five", msg5b.body)

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message: " + extra.body)
        except Empty: None

        self.assertEqual(0, session2.queue_query(queue="test-ack-queue").message_count)
        queueObj.update()
        self.assertEquals(queueObj.msgDepth, 0)
        self.assertEquals(queueObj.msgTotalEnqueues, 5)
        self.assertEquals(queueObj.msgTotalDequeues, 5)

        # Subscribe one last time to keep the queue available, and to verify
        # that the implied accept worked by verifying no messages have been
        # returned when session2 is closed.
        self.session.message_subscribe(queue = "test-ack-queue", destination = "final-checker")

        session2.close(timeout=10)

        # check the statistics - they should not have changed
        self.assertEqual(0, self.session.queue_query(queue="test-ack-queue").message_count)
        queueObj.update()
        self.assertEquals(queueObj.msgDepth, 0)
        self.assertEquals(queueObj.msgTotalEnqueues, 5)
        self.assertEquals(queueObj.msgTotalDequeues, 5)

        self.session.message_flow(destination="final-checker", unit=self.session.credit_unit.message, value=0xFFFFFFFFL)
        self.session.message_flow(destination="final-checker", unit=self.session.credit_unit.byte, value=0xFFFFFFFFL)
        try:
            extra = self.session.incoming("final-checker").get(timeout=1)
            self.fail("Got unexpected message: " + extra.body)
        except Empty: None

    def test_reject(self):
        session = self.session
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True, alternate_exchange="amq.fanout")
        session.queue_declare(queue = "r", exclusive=True, auto_delete=True)
        session.exchange_bind(queue = "r", exchange = "amq.fanout")

        session.message_subscribe(queue = "q", destination = "consumer")
        session.message_flow(destination="consumer", unit=session.credit_unit.message, value=0xFFFFFFFFL)
        session.message_flow(destination="consumer", unit=session.credit_unit.byte, value=0xFFFFFFFFL)
        session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "blah, blah"))
        msg = session.incoming("consumer").get(timeout = 1)
        self.assertEquals(msg.body, "blah, blah")
        session.message_reject(RangedSet(msg.id))

        session.message_subscribe(queue = "r", destination = "checker")
        session.message_flow(destination="checker", unit=session.credit_unit.message, value=0xFFFFFFFFL)
        session.message_flow(destination="checker", unit=session.credit_unit.byte, value=0xFFFFFFFFL)
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
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "c")
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
        msg_size = 19

        #set byte credit to finite amount (less than enough for all messages)
        session.message_flow(unit = session.credit_unit.byte, value = msg_size*5, destination = "c")
        #set infinite message credit
        session.message_flow(unit = session.credit_unit.message, value = 0xFFFFFFFFL, destination = "c")
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
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "c")
        #check that expected number were received
        q = session.incoming("c")
        ids = []
        for i in range(1, 6):            
            msg = q.get(timeout = 1)
            ids.append(msg.id)
            self.assertDataEquals(session, msg, "Message %d" % i)
        self.assertEmpty(q)

        #acknowledge messages and check more are received
        #TODO: there may be a nicer way of doing this
        for i in ids:
           session.receiver._completed.add(i)
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
        session.message_flow(unit = session.credit_unit.message, value = 0xFFFFFFFFL, destination = "c")
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

    def test_window_flush_ack_flow(self):
        """
        Test basic window based flow control with unit = bytes
        """
        #declare an exclusive queue
        ssn = self.session
        ssn.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        #create consumer
        ssn.message_subscribe(queue = "q", destination = "c",
                              accept_mode=ssn.accept_mode.explicit)
        ssn.message_set_flow_mode(flow_mode = ssn.flow_mode.window, destination = "c")

        #send message A
        ssn.message_transfer(message=Message(ssn.delivery_properties(routing_key="q"), "A"))

        for unit in ssn.credit_unit.VALUES:
            ssn.message_flow("c", unit, 0xFFFFFFFFL)

        q = ssn.incoming("c")
        msgA = q.get(timeout=10)

        ssn.message_flush(destination="c")

        # XXX
        ssn.receiver._completed.add(msgA.id)
        ssn.channel.session_completed(ssn.receiver._completed)
        ssn.message_accept(RangedSet(msgA.id))

        for unit in ssn.credit_unit.VALUES:
            ssn.message_flow("c", unit, 0xFFFFFFFFL)

        #send message B
        ssn.message_transfer(message=Message(ssn.delivery_properties(routing_key="q"), "B"))

        msgB = q.get(timeout=10)

    def test_window_stop(self):
        """
        Ensure window based flow control reacts to stop correctly
        """
        session = self.session
        #setup subscriber on a test queue
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        session.message_subscribe(queue = "q", destination = "c")
        session.message_set_flow_mode(flow_mode = 1, destination = "c")
        session.message_flow(unit = session.credit_unit.message, value = 5, destination = "c")
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "c")


        #send batch of messages to queue
        for i in range(0, 10):
            session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "Message %d" % (i+1)))

        #retrieve all delivered messages
        q = session.incoming("c")
        for i in range(0, 5):
            msg = q.get(timeout = 1)
            session.receiver._completed.add(msg.id)#TODO: this may be done automatically
            self.assertDataEquals(session, msg, "Message %d" % (i+1))

        session.message_stop(destination = "c")

        #now send completions, normally used to move window forward,
        #but after a stop should not do so
        session.channel.session_completed(session.receiver._completed)

        #check no more messages are sent
        self.assertEmpty(q)

        #re-establish window and check remaining messages
        session.message_flow(unit = session.credit_unit.message, value = 5, destination = "c")
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "c")
        for i in range(0, 5):
            msg = q.get(timeout = 1)
            self.assertDataEquals(session, msg, "Message %d" % (i+6))

    def test_credit_window_after_messagestop(self):
        """
        Tests that the broker's credit window size doesnt exceed the requested value when completing
        previous messageTransfer commands after a message_stop and message_flow.
        """

        session = self.session

        #create queue
        session.queue_declare(queue = self.test_queue_name, exclusive=True, auto_delete=True)

        #send 11 messages
        for i in range(1, 12):
            session.message_transfer(message=Message(session.delivery_properties(routing_key=self.test_queue_name), "message-%d" % (i)))


        #subscribe:
        session.message_subscribe(queue=self.test_queue_name, destination="a")
        a = session.incoming("a")
        session.message_set_flow_mode(flow_mode = 1, destination = "a")
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "a")
        # issue 5 message credits
        session.message_flow(unit = session.credit_unit.message, value = 5, destination = "a")

        # get 5 messages
        ids = RangedSet()
        for i in range(1, 6):
            msg = a.get(timeout = 1)
            self.assertEquals("message-%d" % (i), msg.body)
            ids.add(msg.id)

        # now try and read a 6th message. we expect this to fail due to exhausted message credit.
        try:
            extra = a.get(timeout=1)
            self.fail("Got unexpected message: " + extra.body)
        except Empty: None

        session.message_stop(destination = "a")

        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "a")
        session.message_flow(unit = session.credit_unit.message, value = 5, destination = "a")

        # complete earlier messages after setting the window to 5 message credits
        session.channel.session_completed(ids)

        # Now continue to read the next 5 messages
        for i in range(6, 11):
            msg = a.get(timeout = 1)
            self.assertEquals("message-%d" % (i), msg.body)

        # now try and read the 11th message. we expect this to fail due to exhausted message credit.  If we receive an
        # 11th this indicates the broker is not respecting the client's requested window size.
        try:
            extra = a.get(timeout=1)
            self.fail("Got unexpected message: " + extra.body)
        except Empty: None

    def test_no_credit_wrap(self):
        """
        Ensure that adding credit does not result in wrapround, lowering the balance.
        """
        session = self.session

        session.queue_declare(queue = self.test_queue_name, exclusive=True, auto_delete=True)
        session.message_subscribe(queue=self.test_queue_name, destination="a")
        a = session.incoming("a")
        session.message_set_flow_mode(flow_mode = session.flow_mode.credit, destination = "a")
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "a")
        session.message_flow(unit = session.credit_unit.message, value = 0xFFFFFFFAL, destination = "a")
        #test wraparound of credit balance does not occur
        session.message_flow(unit = session.credit_unit.message, value = 10, destination = "a")
        for i in range(1, 50):
            session.message_transfer(message=Message(session.delivery_properties(routing_key=self.test_queue_name), "message-%d" % (i)))
        session.message_flush(destination = "a")
        for i in range(1, 50):
            msg = a.get(timeout = 1)
            self.assertEquals("message-%d" % (i), msg.body)


    def test_subscribe_not_acquired(self):
        """
        Test the not-acquired modes works as expected for a simple case
        """
        session = self.session
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)
        for i in range(1, 6):
            session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "Message %s" % i))

        session.message_subscribe(queue = "q", destination = "a", acquire_mode = 1)
        session.message_flow(unit = session.credit_unit.message, value = 0xFFFFFFFFL, destination = "a")
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "a")
        session.message_subscribe(queue = "q", destination = "b", acquire_mode = 1)
        session.message_flow(unit = session.credit_unit.message, value = 0xFFFFFFFFL, destination = "b")
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "b")

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

    def test_acquire_with_no_accept_and_credit_flow(self):
        """
        Test that messages recieved unacquired, with accept not
        required in windowing mode can be acquired.
        """
        session = self.session
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)

        session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "acquire me"))

        session.message_subscribe(queue = "q", destination = "a", acquire_mode = 1, accept_mode = 1)
        session.message_set_flow_mode(flow_mode = session.flow_mode.credit, destination = "a")
        session.message_flow(unit = session.credit_unit.message, value = 0xFFFFFFFFL, destination = "a")
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "a")
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

    def test_acquire(self):
        """
        Test explicit acquire function
        """
        session = self.session
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)

        session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "acquire me"))

        session.message_subscribe(queue = "q", destination = "a", acquire_mode = 1)
        session.message_flow(destination="a", unit=session.credit_unit.message, value=0xFFFFFFFFL)
        session.message_flow(destination="a", unit=session.credit_unit.byte, value=0xFFFFFFFFL)
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
        session.message_flow(destination="a", unit=session.credit_unit.message, value=0xFFFFFFFFL)
        session.message_flow(destination="a", unit=session.credit_unit.byte, value=0xFFFFFFFFL)
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
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "a")
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
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "a")
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
        session.message_flow(destination="checker", unit=session.credit_unit.message, value=0xFFFFFFFFL)
        session.message_flow(destination="checker", unit=session.credit_unit.byte, value=0xFFFFFFFFL)
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
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "a")

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
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "b")

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
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "a")
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
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "b")
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
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "a")
        session.message_flow(unit = session.credit_unit.message, value = 10, destination = "a")
        queueA = session.incoming("a")

        session.message_subscribe(queue = "q", destination = "b", acquire_mode=1)
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "b")
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
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "c")
        session.message_flow(unit = session.credit_unit.message, value = 10, destination = "c")
        queueC = session.incoming("c")
        #consume the message then ack it
        msgC = queueC.get(timeout = 1)
        session.message_accept(RangedSet(msgC.id))
        #ensure there are no other messages
        self.assertEmpty(queueC)

    def test_release_order(self):
        session = self.session

        #create queue
        session.queue_declare(queue = "q", exclusive=True, auto_delete=True)

        #send messages
        for i in range(1, 11):
            session.message_transfer(message=Message(session.delivery_properties(routing_key="q"), "message-%d" % (i)))

        #subscribe:
        session.message_subscribe(queue="q", destination="a")
        a = session.incoming("a")
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = "a")
        session.message_flow(unit = session.credit_unit.message, value = 10, destination = "a")

        # receive all messages into list
        messages = [];
        for i in range(1, 11):
            msg = a.get(timeout = 1)
            self.assertEquals("message-%d" % (i), msg.body)
            messages.append(msg)

        # accept/release received messages
        for i, msg in enumerate(messages, start=1):
            if (i % 2):
                #accept all odd messages
                session.message_accept(RangedSet(msg.id))
            else:
                #release all even messages
                session.message_release(RangedSet(msg.id))

        session.message_subscribe(queue="q", destination="b", acquire_mode=0)
        b = session.incoming("b")
        b.start()
        for i in [2, 4, 6, 8, 10]:
            msg = b.get(timeout = 1)
            self.assertEquals("message-%d" % (i), msg.body)


    def test_empty_body(self):
        session = self.session
        session.queue_declare(queue="xyz", exclusive=True, auto_delete=True)
        props = session.delivery_properties(routing_key="xyz")
        session.message_transfer(message=Message(props, ""))

        consumer_tag = "tag1"
        session.message_subscribe(queue="xyz", destination=consumer_tag)
        session.message_flow(unit = session.credit_unit.message, value = 0xFFFFFFFFL, destination = consumer_tag)
        session.message_flow(unit = session.credit_unit.byte, value = 0xFFFFFFFFL, destination = consumer_tag)
        queue = session.incoming(consumer_tag)
        msg = queue.get(timeout=1)
        self.assertEquals("", msg.body)
        session.message_accept(RangedSet(msg.id))

    def test_incoming_start(self):
        q = "test_incoming_start"
        session = self.session

        session.queue_declare(queue=q, exclusive=True, auto_delete=True)
        session.message_subscribe(queue=q, destination="msgs")
        messages = session.incoming("msgs")
        assert messages.destination == "msgs"

        dp = session.delivery_properties(routing_key=q)
        session.message_transfer(message=Message(dp, "test"))

        messages.start()
        msg = messages.get()
        assert msg.body == "test"

    def test_ttl(self):
        q = "test_ttl"
        session = self.session

        session.queue_declare(queue=q, exclusive=True, auto_delete=True)

        dp = session.delivery_properties(routing_key=q, ttl=500)#expire in half a second
        session.message_transfer(message=Message(dp, "first"))

        dp = session.delivery_properties(routing_key=q, ttl=300000)#expire in fives minutes
        session.message_transfer(message=Message(dp, "second"))

        d = "msgs"
        session.message_subscribe(queue=q, destination=d)
        messages = session.incoming(d)
        sleep(1)
        session.message_flow(unit = session.credit_unit.message, value=2, destination=d)
        session.message_flow(unit = session.credit_unit.byte, value=0xFFFFFFFFL, destination=d)
        assert messages.get(timeout=1).body == "second"
        self.assertEmpty(messages)

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
