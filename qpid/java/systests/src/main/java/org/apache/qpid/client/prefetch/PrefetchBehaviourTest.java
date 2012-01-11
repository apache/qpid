package org.apache.qpid.client.prefetch;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        assertFalse("Unexpecte exception during async message processing",_exceptionCaught.get());
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
