/*
*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*
*/
package org.apache.qpid.client.prefetch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public class PrefetchBehaviourTest extends QpidBrokerTestCase
{
    protected static final Logger _logger = LoggerFactory.getLogger(PrefetchBehaviourTest.class);
    private Connection _normalConnection;
    private AtomicBoolean _exceptionCaught;
    private CountDownLatch _processingStarted;
    private CountDownLatch _processingCompleted;

    protected void setUp() throws Exception
    {
        super.setUp();
        _normalConnection = getConnection();
        _exceptionCaught = new AtomicBoolean();
        _processingStarted = new CountDownLatch(1);
        _processingCompleted = new CountDownLatch(1);
    }

    /**
     * Verifies that a slow processing asynchronous transacted consumer with prefetch=1 only
     * gets 1 of the messages sent, with the second consumer picking up the others while the
     * slow consumer is processing, i.e that prefetch=1 actually does what it says on the tin.
     */
    public void testPrefetchOneWithAsynchronousTransactedConsumer() throws Exception
    {
        final long processingTime = 5000;
        
        //create a second connection with prefetch set to 1
        setTestClientSystemProperty(ClientProperties.MAX_PREFETCH_PROP_NAME, new Integer(1).toString());
        Connection prefetch1Connection = getConnection();

        prefetch1Connection.start();
        _normalConnection.start();

        //create an asynchronous consumer with simulated slow processing
        final Session prefetch1session = prefetch1Connection.createSession(true, Session.SESSION_TRANSACTED);
        Queue queue = prefetch1session.createQueue(getTestQueueName());
        MessageConsumer prefetch1consumer = prefetch1session.createConsumer(queue);
        prefetch1consumer.setMessageListener(new MessageListener()
        {
            public void onMessage(Message message)
            {
                try
                {
                    _logger.debug("starting processing");
                    _processingStarted.countDown();
                    _logger.debug("processing started");

                    //simulate message processing
                    Thread.sleep(processingTime);

                    prefetch1session.commit();

                    _processingCompleted.countDown();
                }
                catch(Exception e)
                {
                    _logger.error("Exception caught in message listener");
                    _exceptionCaught.set(true);
                }
            }
        });

        //create producer and send 5 messages
        Session producerSession = _normalConnection.createSession(true, Session.SESSION_TRANSACTED);
        MessageProducer producer = producerSession.createProducer(queue);

        for (int i = 0; i < 5; i++)
        {
            producer.send(producerSession.createTextMessage("test"));
        }
        producerSession.commit();

        //wait for the first message to start being processed by the async consumer
        assertTrue("Async processing failed to start in allowed timeframe", _processingStarted.await(2000, TimeUnit.MILLISECONDS));
        _logger.debug("proceeding with test");

        //try to consumer the other messages with another consumer while the async procesisng occurs
        Session normalSession = _normalConnection.createSession(true, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer normalConsumer = normalSession.createConsumer(queue);        
        
        Message msg;
        // Check that other consumer gets the other 4 messages
        for (int i = 0; i < 4; i++)
        {
            msg = normalConsumer.receive(1500);
            assertNotNull("Consumer should receive 4 messages",msg);                
        }
        msg = normalConsumer.receive(250);
        assertNull("Consumer should not have received a 5th message",msg);

        //wait for the other consumer to finish to ensure it completes ok
        _logger.debug("waiting for async consumer to complete");
        assertTrue("Async processing failed to complete in allowed timeframe", _processingStarted.await(processingTime + 2000, TimeUnit.MILLISECONDS));
        assertFalse("Unexpected exception during async message processing",_exceptionCaught.get());
    }

    /**
     * This test was originally known as AMQConnectionTest#testPrefetchSystemProperty.
     *
     */
    public void testMessagesAreDistributedBetweenConsumersWithLowPrefetch() throws Exception
    {
        Queue queue = getTestQueue();

        setTestClientSystemProperty(ClientProperties.MAX_PREFETCH_PROP_NAME, new Integer(2).toString());

        Connection connection = getConnection();
        connection.start();
        // Create Consumer A
        Session consSessA = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumerA = consSessA.createConsumer(queue);

        // ensure message delivery to consumer A is started (required for 0-8..0-9-1)
        final Message msg = consumerA.receiveNoWait();
        assertNull(msg);

        Session producerSession = connection.createSession(true, Session.SESSION_TRANSACTED);
        sendMessage(producerSession, queue, 3);

        // Create Consumer B
        MessageConsumer consumerB = null;
        if (isBroker010())
        {
            // 0-10 prefetch is per consumer so we create Consumer B on the same session as Consumer A
            consumerB = consSessA.createConsumer(queue);
        }
        else
        {
            // 0-8..0-9-1 prefetch is per session so we create Consumer B on a separate session
            Session consSessB = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            consumerB = consSessB.createConsumer(queue);
        }

        // As message delivery to consumer A is already started, the first two messages should
        // now be with consumer A.  The last message will still be on the Broker as consumer A's
        // credit is exhausted and message delivery for consumer B is not yet running.

        // As described by QPID-3747, for 0-10 we *must* check Consumer B before Consumer A.
        // If we were to reverse the order, the SessionComplete will restore Consumer A's credit,
        // and the third message could be delivered to either Consumer A or Consumer B.

        // Check that consumer B gets the last (third) message.
        final Message msgConsumerB = consumerB.receive(1500);
        assertNotNull("Consumer B should have received a message", msgConsumerB);
        assertEquals("Consumer B received message with unexpected index", 2, msgConsumerB.getIntProperty(INDEX));

        // Now check that consumer A has indeed got the first two messages.
        for (int i = 0; i < 2; i++)
        {
            final Message msgConsumerA = consumerA.receive(1500);
            assertNotNull("Consumer A should have received a message " + i, msgConsumerA);
            assertEquals("Consumer A received message with unexpected index", i, msgConsumerA.getIntProperty(INDEX));
        }
    }

    /**
     * Test Goal: Verify if connection stop releases all messages in it's prefetch buffer.
     * Test Strategy: Send 10 messages to a queue. Create a consumer with maxprefetch of 5, but never consume them.
     *                Stop the connection. Create a new connection and a consumer with maxprefetch 10 on the same queue.
     *                Try to receive all 10 messages.
     */
    public void testConnectionStop() throws Exception
    {
        setTestClientSystemProperty(ClientProperties.MAX_PREFETCH_PROP_NAME, "10");
        Connection con = getConnection();
        con.start();
        Session ssn = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination queue = ssn.createQueue("ADDR:my-queue;{create: always}");

        MessageProducer prod = ssn.createProducer(queue);
        for (int i=0; i<10;i++)
        {
           prod.send(ssn.createTextMessage("Msg" + i));
        }

        MessageConsumer consumer = ssn.createConsumer(queue);
        // This is to ensure we get the first client to prefetch.
        Message msg = consumer.receive(1000);
        assertNotNull("The first consumer should get one message",msg);
        con.stop();

        Connection con2 = getConnection();
        con2.start();
        Session ssn2 = con2.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer2 = ssn2.createConsumer(queue);
        for (int i=0; i<9;i++)
        {
           TextMessage m = (TextMessage)consumer2.receive(1000);
           assertNotNull("The second consumer should get 9 messages, but received only " + i,m);
        }
    }
}

