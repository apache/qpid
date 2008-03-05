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

class AlternateExchangeTests(TestBase):
    """
    Tests for the new mechanism for message returns introduced in 0-10
    and available in 0-9 for preview
    """

    def test_unroutable(self):
        """
        Test that unroutable messages are delivered to the alternate-exchange if specified
        """
        channel = self.channel
        #create an exchange with an alternate defined
        channel.exchange_declare(exchange="secondary", type="fanout")
        channel.exchange_declare(exchange="primary", type="direct", alternate_exchange="secondary")

        #declare, bind (to the alternate exchange) and consume from a queue for 'returned' messages
        channel.queue_declare(queue="returns", exclusive=True, auto_delete=True)
        channel.queue_bind(queue="returns", exchange="secondary")
        self.subscribe(destination="a", queue="returns")
        returned = self.client.queue("a")

        #declare, bind (to the primary exchange) and consume from a queue for 'processed' messages
        channel.queue_declare(queue="processed", exclusive=True, auto_delete=True)
        channel.queue_bind(queue="processed", exchange="primary", routing_key="my-key")
        self.subscribe(destination="b", queue="processed")
        processed = self.client.queue("b")

        #publish to the primary exchange
        #...one message that makes it to the 'processed' queue:
        channel.message_transfer(destination="primary", content=Content("Good", properties={'routing_key':"my-key"}))
        #...and one that does not:
        channel.message_transfer(destination="primary", content=Content("Bad", properties={'routing_key':"unused-key"}))

        #delete the exchanges
        channel.exchange_delete(exchange="primary")
        channel.exchange_delete(exchange="secondary")

        #verify behaviour
        self.assertEqual("Good", processed.get(timeout=1).content.body)
        self.assertEqual("Bad", returned.get(timeout=1).content.body)
        self.assertEmpty(processed)
        self.assertEmpty(returned)

    def test_queue_delete(self):
        """
        Test that messages in a queue being deleted are delivered to the alternate-exchange if specified
        """
        channel = self.channel
        #set up a 'dead letter queue':
        channel.exchange_declare(exchange="dlq", type="fanout")
        channel.queue_declare(queue="deleted", exclusive=True, auto_delete=True)
        channel.queue_bind(exchange="dlq", queue="deleted")
        self.subscribe(destination="dlq", queue="deleted")
        dlq = self.client.queue("dlq")

        #create a queue using the dlq as its alternate exchange:
        channel.queue_declare(queue="delete-me", alternate_exchange="dlq")
        #send it some messages:
        channel.message_transfer(content=Content("One", properties={'routing_key':"delete-me"}))
        channel.message_transfer(content=Content("Two", properties={'routing_key':"delete-me"}))
        channel.message_transfer(content=Content("Three", properties={'routing_key':"delete-me"}))
        #delete it:
        channel.queue_delete(queue="delete-me")
        #delete the dlq exchange:
        channel.exchange_delete(exchange="dlq")

        #check the messages were delivered to the dlq:
        self.assertEqual("One", dlq.get(timeout=1).content.body)
        self.assertEqual("Two", dlq.get(timeout=1).content.body)
        self.assertEqual("Three", dlq.get(timeout=1).content.body)
        self.assertEmpty(dlq)


    def test_immediate(self):
        """
        Test that messages in a queue being deleted are delivered to the alternate-exchange if specified
        """
        channel = self.channel
        #set up a 'dead letter queue':
        channel.exchange_declare(exchange="dlq", type="fanout")
        channel.queue_declare(queue="immediate", exclusive=True, auto_delete=True)
        channel.queue_bind(exchange="dlq", queue="immediate")
        self.subscribe(destination="dlq", queue="immediate")
        dlq = self.client.queue("dlq")

        #create a queue using the dlq as its alternate exchange:
        channel.queue_declare(queue="no-consumers", alternate_exchange="dlq", exclusive=True, auto_delete=True)
        #send it some messages:
        #TODO: WE HAVE LOST THE IMMEDIATE FLAG; FIX THIS ONCE ITS BACK
        channel.message_transfer(content=Content("no one wants me", properties={'routing_key':"no-consumers"}))

        #check the messages were delivered to the dlq:
        self.assertEqual("no one wants me", dlq.get(timeout=1).content.body)
        self.assertEmpty(dlq)

        #cleanup:
        channel.queue_delete(queue="no-consumers")
        channel.exchange_delete(exchange="dlq")


    def test_delete_while_used_by_queue(self):
        """
        Ensure an exchange still in use as an alternate-exchange for a
        queue can't be deleted
        """
        channel = self.channel
        channel.exchange_declare(exchange="alternate", type="fanout")
        channel.queue_declare(queue="q", exclusive=True, auto_delete=True, alternate_exchange="alternate")
        try:
            channel.exchange_delete(exchange="alternate")
            self.fail("Expected deletion of in-use alternate-exchange to fail")
        except Closed, e:
            #cleanup:
            other = self.connect()
            channel = other.channel(1)
            channel.session_open()
            channel.exchange_delete(exchange="alternate")
            channel.session_close()
            other.close()
            
            self.assertConnectionException(530, e.args[0])            



    def test_delete_while_used_by_exchange(self):
        """
        Ensure an exchange still in use as an alternate-exchange for 
        another exchange can't be deleted
        """
        channel = self.channel
        channel.exchange_declare(exchange="alternate", type="fanout")
        channel.exchange_declare(exchange="e", type="fanout", alternate_exchange="alternate")
        try:
            channel.exchange_delete(exchange="alternate")
            #cleanup:
            channel.exchange_delete(exchange="e")
            self.fail("Expected deletion of in-use alternate-exchange to fail")
        except Closed, e:
            #cleanup:
            other = self.connect()
            channel = other.channel(1)
            channel.session_open()
            channel.exchange_delete(exchange="e")
            channel.exchange_delete(exchange="alternate")
            channel.session_close()
            other.close()

            self.assertConnectionException(530, e.args[0])
            

    def assertEmpty(self, queue):
        try:
            msg = queue.get(timeout=1) 
            self.fail("Queue not empty: " + msg)
        except Empty: None

