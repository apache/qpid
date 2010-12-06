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

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AMQQueueDeferredOrderingTest extends QpidBrokerTestCase
{

    private static final int NUM_MESSAGES = 1000;

    private Connection _con;
    private Session _session;
    private AMQQueue _queue;
    private MessageConsumer _consumer;

    private static final Logger _logger = LoggerFactory.getLogger(AMQQueueDeferredOrderingTest.class);

    private ExecutorService _exec = Executors.newCachedThreadPool();

    private class ASyncProducer implements Callable<Void>
    {

        private MessageProducer producer;
        private final Logger logger = LoggerFactory.getLogger(ASyncProducer.class);
        private Session session;
        private int start;
        private int end;

        public ASyncProducer(AMQQueue q, int start, int end) throws Exception
        {
            this.logger.info("Create Producer for " + q.getQueueName());
            this.session = _con.createSession(false, Session.AUTO_ACKNOWLEDGE);
            this.producer = this.session.createProducer(q);
            this.start = start;
            this.end = end;
        }

        public Void call() throws Exception
        {
            try
            {
                this.logger.info("Starting to send messages");
                for (int i = start; i < end && !Thread.currentThread().isInterrupted(); i++)
                {
                    producer.send(session.createTextMessage(Integer.toString(i)));
                }
                this.logger.info("Sent " + (end - start) + " messages");
                
                return null;
            }
            catch (JMSException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    protected void setUp() throws Exception
    {
        super.setUp();

        _logger.info("Create Connection");
        _con = getConnection();
        
        _logger.info("Create Session");
        _session = _con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        
        _logger.info("Create Q");
        _queue = new AMQQueue(new AMQShortString("amq.direct"), new AMQShortString("Q"), new AMQShortString("Q"), false, true);
        
        _logger.info("Create Consumer of Q");
        _consumer = _session.createConsumer(_queue);
        
        _logger.info("Start Connection");
        _con.start();
    }

    public void testPausedOrder() throws Exception
    {
        ASyncProducer producer;

        // Setup initial messages
        _logger.info("Creating first producer thread");
        producer = new ASyncProducer(_queue, 0, NUM_MESSAGES / 2);
        Future<Void> initial = _exec.submit(producer);
        
        // Wait for them to be done
        initial.get();

        // Setup second set of messages to produce while we consume
        _logger.info("Creating second producer thread");
        producer = new ASyncProducer(_queue, NUM_MESSAGES / 2, NUM_MESSAGES);
        _exec.submit(producer);

        // Start consuming and checking they're in order
        _logger.info("Consuming messages");
        for (int i = 0; i < NUM_MESSAGES; i++)
        {
            Message msg = _consumer.receive(3000);
            assertNotNull("Message should not be null", msg);
            assertTrue("Message should be a text message", msg instanceof TextMessage);
            _logger.error("== " + Integer.toString(i) + " == " + ((TextMessage) msg).getText());
            assertEquals("Message content does not match", Integer.toString(i), ((TextMessage) msg).getText());
        }
    }

    protected void tearDown() throws Exception
    {
        _logger.info("Interuptting producer thread");
        _exec.shutdownNow();
        
        _logger.info("Closing connection");
        _con.close();

        super.tearDown();
    }

}
