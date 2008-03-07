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
from qpid.datatypes import Message

class QueueTests(TestBase010):
    """Tests for 'methods' on the amqp queue 'class'"""

    def test_purge(self):
        """
        Test that the purge method removes messages from the queue
        """
        session = self.session
        #setup, declare a queue and add some messages to it:
        session.queue_declare(queue="test-queue", exclusive=True, auto_delete=True)
        session.message_transfer(message=Message(session.delivery_properties(routing_key="test-queue"), "one"))
        session.message_transfer(message=Message(session.delivery_properties(routing_key="test-queue"), "two"))
        session.message_transfer(message=Message(session.delivery_properties(routing_key="test-queue"), "three"))

        #check that the queue now reports 3 messages:
        session.queue_declare(queue="test-queue")
        reply = session.queue_query(queue="test-queue")
        self.assertEqual(3, reply.message_count)

        #now do the purge, then test that three messages are purged and the count drops to 0
        session.queue_purge(queue="test-queue");
        reply = session.queue_query(queue="test-queue")
        self.assertEqual(0, reply.message_count)        

        #send a further message and consume it, ensuring that the other messages are really gone
        session.message_transfer(message=Message(session.delivery_properties(routing_key="test-queue"), "four"))
        session.message_subscribe(queue="test-queue", destination="tag")
        session.message_flow(destination="tag", unit=0, value=0xFFFFFFFF)
        session.message_flow(destination="tag", unit=1, value=0xFFFFFFFF)
        queue = session.incoming("tag")
        msg = queue.get(timeout=1)
        self.assertEqual("four", msg.body)

        #check error conditions (use new sessions): 
        session = self.conn.session("error-checker")
        try:
            #queue specified but doesn't exist:
            session.queue_purge(queue="invalid-queue")            
            self.fail("Expected failure when purging non-existent queue")
        except Closed, e:
            self.assertChannelException(404, e.args[0])

        session = self.conn.session("error-checker")
        try:
            #queue not specified and none previously declared for channel:
            session.queue_purge()
            self.fail("Expected failure when purging unspecified queue")
        except Closed, e:
            self.assertConnectionException(530, e.args[0])

    def test_declare_exclusive(self):
        """
        Test that the exclusive field is honoured in queue.declare
        """
        # TestBase.setUp has already opened session(1)
        c1 = self.session
        # Here we open a second separate connection:
        other = self.connect()
        c2 = other.session(1)
        c2.session_open()

        #declare an exclusive queue:
        c1.queue_declare(queue="exclusive-queue", exclusive=True, auto_delete=True)
        try:
            #other connection should not be allowed to declare this:
            c2.queue_declare(queue="exclusive-queue", exclusive=True, auto_delete=True)
            self.fail("Expected second exclusive queue_declare to raise a channel exception")
        except Closed, e:
            self.assertChannelException(405, e.args[0])


    def test_declare_passive(self):
        """
        Test that the passive field is honoured in queue.declare
        """
        session = self.session
        #declare an exclusive queue:
        session.queue_declare(queue="passive-queue-1", exclusive=True, auto_delete=True)
        session.queue_declare(queue="passive-queue-1", passive=True)
        try:
            #other connection should not be allowed to declare this:
            session.queue_declare(queue="passive-queue-2", passive=True)
            self.fail("Expected passive declaration of non-existant queue to raise a channel exception")
        except Closed, e:
            self.assertChannelException(404, e.args[0])


    def test_bind(self):
        """
        Test various permutations of the queue.bind method
        """
        session = self.session
        session.queue_declare(queue="queue-1", exclusive=True, auto_delete=True)

        #straightforward case, both exchange & queue exist so no errors expected:
        session.queue_bind(queue="queue-1", exchange="amq.direct", routing_key="key1")

        #use the queue name where the routing key is not specified:
        session.exchange_bind(queue="queue-1", exchange="amq.direct")

        #try and bind to non-existant exchange
        try:
            session.queue_bind(queue="queue-1", exchange="an-invalid-exchange", routing_key="key1")
            self.fail("Expected bind to non-existant exchange to fail")
        except Closed, e:
            self.assertChannelException(404, e.args[0])

        #need to reopen a session:    
        session = self.client.session(2)
        session.session_open()

        #try and bind non-existant queue:
        try:
            session.queue_bind(queue="queue-2", exchange="amq.direct", routing_key="key1")
            self.fail("Expected bind of non-existant queue to fail")
        except Closed, e:
            self.assertChannelException(404, e.args[0])

    def test_unbind_direct(self):
        self.unbind_test(exchange="amq.direct", routing_key="key")

    def test_unbind_topic(self):
        self.unbind_test(exchange="amq.topic", routing_key="key")

    def test_unbind_fanout(self):
        self.unbind_test(exchange="amq.fanout")

    def test_unbind_headers(self):
        self.unbind_test(exchange="amq.match", args={ "x-match":"all", "a":"b"}, headers={"a":"b"})

    def unbind_test(self, exchange, routing_key="", args=None, headers=None):
        #bind two queues and consume from them
        session = self.session
        
        session.queue_declare(queue="queue-1", exclusive=True, auto_delete=True)
        session.queue_declare(queue="queue-2", exclusive=True, auto_delete=True)

        session.message_subscribe(queue="queue-1", destination="queue-1")
        session.message_flow(destination="queue-1", unit=0, value=0xFFFFFFFF)
        session.message_flow(destination="queue-1", unit=1, value=0xFFFFFFFF)
        session.message_subscribe(queue="queue-2", destination="queue-2")
        session.message_flow(destination="queue-2", unit=0, value=0xFFFFFFFF)
        session.message_flow(destination="queue-2", unit=1, value=0xFFFFFFFF)

        queue1 = session.incoming("queue-1")
        queue2 = session.incoming("queue-2")

        session.exchange_bind(exchange=exchange, queue="queue-1", binding_key=routing_key, arguments=args)
        session.exchange_bind(exchange=exchange, queue="queue-2", binding_key=routing_key, arguments=args)

        dp = session.delivery_properties(routing_key=routing_key)
        if (headers):
            mp = session.message_properties(application_headers=headers)
            msg1 = Message(dp, mp, "one")
            msg2 = Message(dp, mp, "two")
        else:
            msg1 = Message(dp, "one")
            msg2 = Message(dp, "two")
            
        #send a message that will match both bindings
        session.message_transfer(destination=exchange, message=msg1)
        
        #unbind first queue
        session.exchange_unbind(exchange=exchange, queue="queue-1", binding_key=routing_key)
        
        #send another message
        session.message_transfer(destination=exchange, message=msg2)

        #check one queue has both messages and the other has only one
        self.assertEquals("one", queue1.get(timeout=1).body)
        try:
            msg = queue1.get(timeout=1)
            self.fail("Got extra message: %s" % msg.body)
        except Empty: pass

        self.assertEquals("one", queue2.get(timeout=1).body)
        self.assertEquals("two", queue2.get(timeout=1).body)
        try:
            msg = queue2.get(timeout=1)
            self.fail("Got extra message: " + msg)
        except Empty: pass        


    def test_delete_simple(self):
        """
        Test core queue deletion behaviour
        """
        session = self.session

        #straight-forward case:
        session.queue_declare(queue="delete-me")
        session.message_transfer(message=Message("a", session.delivery_properties(routing_key="delete-me")))
        session.message_transfer(message=Message("b", session.delivery_properties(routing_key="delete-me")))
        session.message_transfer(message=Message("c", session.delivery_properties(routing_key="delete-me")))
        session.queue_delete(queue="delete-me")
        #check that it has gone be declaring passively
        try:
            session.queue_declare(queue="delete-me", passive=True)
            self.fail("Queue has not been deleted")
        except Closed, e:
            self.assertChannelException(404, e.args[0])

        #check attempted deletion of non-existant queue is handled correctly:    
        session = self.client.session(2)
        session.session_open()
        try:
            session.queue_delete(queue="i-dont-exist", if_empty=True)
            self.fail("Expected delete of non-existant queue to fail")
        except Closed, e:
            self.assertChannelException(404, e.args[0])

        

    def test_delete_ifempty(self):
        """
        Test that if_empty field of queue_delete is honoured
        """
        session = self.session

        #create a queue and add a message to it (use default binding):
        session.queue_declare(queue="delete-me-2")
        session.queue_declare(queue="delete-me-2", passive=True)
        session.message_transfer(message=Message("message", session.delivery_properties(routing_key="delete-me-2")))

        #try to delete, but only if empty:
        try:
            session.queue_delete(queue="delete-me-2", if_empty=True)
            self.fail("Expected delete if_empty to fail for non-empty queue")
        except Closed, e:
            self.assertChannelException(406, e.args[0])

        #need new channel now:    
        session = self.client.session(2)
        session.session_open()

        #empty queue:
        self.subscribe(session, destination="consumer_tag", queue="delete-me-2")
        queue = session.incoming("consumer_tag")
        msg = queue.get(timeout=1)
        self.assertEqual("message", msg.body)
        session.message_cancel(destination="consumer_tag")

        #retry deletion on empty queue:
        session.queue_delete(queue="delete-me-2", if_empty=True)

        #check that it has gone by declaring passively:
        try:
            session.queue_declare(queue="delete-me-2", passive=True)
            self.fail("Queue has not been deleted")
        except Closed, e:
            self.assertChannelException(404, e.args[0])
        
    def test_delete_ifunused(self):
        """
        Test that if_unused field of queue_delete is honoured
        """
        session = self.channel

        #create a queue and register a consumer:
        session.queue_declare(queue="delete-me-3")
        session.queue_declare(queue="delete-me-3", passive=True)
        self.subscribe(destination="consumer_tag", queue="delete-me-3")

        #need new session now:    
        session2 = self.client.session(2)
        session2.session_open()
        #try to delete, but only if empty:
        try:
            session2.queue_delete(queue="delete-me-3", if_unused=True)
            self.fail("Expected delete if_unused to fail for queue with existing consumer")
        except Closed, e:
            self.assertChannelException(406, e.args[0])


        session.message_cancel(destination="consumer_tag")    
        session.queue_delete(queue="delete-me-3", if_unused=True)
        #check that it has gone by declaring passively:
        try:
            session.queue_declare(queue="delete-me-3", passive=True)
            self.fail("Queue has not been deleted")
        except Closed, e:
            self.assertChannelException(404, e.args[0])


    def test_autodelete_shared(self):
        """
        Test auto-deletion (of non-exclusive queues)
        """
        session = self.session
        other = self.connect()
        session2 = other.session(1)
        session2.session_open()

        session.queue_declare(queue="auto-delete-me", auto_delete=True)

        #consume from both sessions
        reply = session.basic_consume(queue="auto-delete-me")
        session2.basic_consume(queue="auto-delete-me")

        #implicit cancel
        session2.session_close()

        #check it is still there
        session.queue_declare(queue="auto-delete-me", passive=True)

        #explicit cancel => queue is now unused again:
        session.basic_cancel(consumer_tag=reply.consumer_tag)

        #NOTE: this assumes there is no timeout in use

        #check that it has gone by declaring it passively
        try:
            session.queue_declare(queue="auto-delete-me", passive=True)
            self.fail("Expected queue to have been deleted")
        except Closed, e:
            self.assertChannelException(404, e.args[0])


