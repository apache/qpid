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
package org.apache.qpid.client;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class AMQQueueDeferredOrderingTest extends QpidBrokerTestCase
{
    private Connection con;
    private Session session;
    private AMQQueue queue;
    private MessageConsumer consumer;
    private int _numMessages;

    private static final Logger _logger = LoggerFactory.getLogger(AMQQueueDeferredOrderingTest.class);

    private ASyncProducer producerThread;

    private class ASyncProducer extends Thread
    {

        private MessageProducer producer;
        private final Logger _logger = LoggerFactory.getLogger(ASyncProducer.class);
        private Session session;
        private int start;
        private int end;

        public ASyncProducer(AMQQueue q, int start, int end) throws Exception
        {
            this.session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
            this._logger.info("Create Consumer of Q1");
            this.producer = this.session.createProducer(q);
            this.start = start;
            this.end = end;
        }

        public void run()
        {
            try
            {
                this._logger.info("Starting to send messages");
                for (int i = start; i < end && !interrupted(); i++)
                {
                    producer.send(session.createTextMessage(Integer.toString(i)));
                    ((AMQSession<?, ?>)session).sync();
                }
                this._logger.info("Sent " + (end - start) + " messages");
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    protected void setUp() throws Exception
    {
        super.setUp();

        _numMessages = isBrokerStorePersistent() ? 300 : 1000;

        _logger.info("Create Connection");
        con = getConnection();
        _logger.info("Create Session");
        session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _logger.info("Create Q");
        queue = new AMQQueue(new AMQShortString("amq.direct"), new AMQShortString("Q"), new AMQShortString("Q"),
                false, true);
        _logger.info("Create Consumer of Q");
        consumer = session.createConsumer(queue);
        _logger.info("Start Connection");
        con.start();
    }

    public void testMessagesSentByTwoThreadsAreDeliveredInOrder() throws Exception
    {

        // Setup initial messages
        _logger.info("Creating first producer thread");
        producerThread = new ASyncProducer(queue, 0, _numMessages / 2);
        producerThread.start();
        // Wait for them to be done
        producerThread.join();

        // Setup second set of messages to produce while we consume
        _logger.info("Creating second producer thread");
        producerThread = new ASyncProducer(queue, _numMessages / 2, _numMessages);
        producerThread.start();

        // Start consuming and checking they're in order
        _logger.info("Consuming messages");
        for (int i = 0; i < _numMessages; i++)
        {
            Message msg = consumer.receive(3000);

            assertNotNull("Message " + i + " should not be null", msg);
            assertTrue("Message " + i + " should be a text message", msg instanceof TextMessage);
            assertEquals("Message content " + i + " does not match expected", Integer.toString(i), ((TextMessage) msg).getText());
        }
    }

    protected void tearDown() throws Exception
    {
        _logger.info("Interrupting producer thread");
        producerThread.interrupt();
        _logger.info("Closing connection");
        con.close();

        super.tearDown();
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(AMQQueueDeferredOrderingTest.class);
    }

}
