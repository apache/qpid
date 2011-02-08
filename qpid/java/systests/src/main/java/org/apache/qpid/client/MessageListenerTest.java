/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.client;

import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.util.LogMonitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * QPID-293 Setting MessageListener after connection has started can cause messages to be "lost" on a internal delivery
 * queue <p/> The message delivery process: Mina puts a message on _queue in AMQSession and the dispatcher thread
 * take()s from here and dispatches to the _consumers. If the _consumer doesn't have a message listener set at
 * connection start then messages are stored on _synchronousQueue (which needs to be > 1 to pass JMS TCK as multiple
 * consumers on a session can run in any order and a synchronous put/poll will block the dispatcher). <p/> When setting
 * the message listener later the _synchronousQueue is just poll()'ed and the first message delivered the remaining
 * messages will be left on the queue and lost, subsequent messages on the session will arrive first.
 */
public class MessageListenerTest extends QpidBrokerTestCase implements MessageListener, ExceptionListener
{
    private static final Logger _logger = LoggerFactory.getLogger(MessageListenerTest.class);

    Context _context;

    private static final int MSG_COUNT = 5;
    private int _receivedCount = 0;
    private int _errorCount = 0;
    private MessageConsumer _consumer;
    private Connection _clientConnection;
    private CountDownLatch _awaitMessages = new CountDownLatch(MSG_COUNT);

    protected void setUp() throws Exception
    {
        super.setUp();

        // Create Client
        _clientConnection = getConnection("guest", "guest");

        _clientConnection.start();

        Session clientSession = _clientConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Queue queue =clientSession.createQueue("message-listener-test-queue");

        _consumer = clientSession.createConsumer(queue);

        // Create Producer

        Connection producerConnection = getConnection("guest", "guest");

        producerConnection.start();

        Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        MessageProducer producer = producerSession.createProducer(queue);

        for (int msg = 0; msg < MSG_COUNT; msg++)
        {
            producer.send(producerSession.createTextMessage("Message " + msg));
        }

        producerConnection.close();

    }

    protected void tearDown() throws Exception
    {
        if (_clientConnection != null)
        {
            _clientConnection.close();
        }
        super.tearDown();
    }

    public void testSynchronousReceive() throws Exception
    {
        for (int msg = 0; msg < MSG_COUNT; msg++)
        {
            assertTrue(_consumer.receive(2000) != null);
        }
    }

    public void testSynchronousReceiveNoWait() throws Exception
    {
        for (int msg = 0; msg < MSG_COUNT; msg++)
        {
            assertTrue("Failed to receive message " + msg, _consumer.receiveNoWait() != null);
        }
    }

    public void testAsynchronousReceive() throws Exception
    {
        _consumer.setMessageListener(this);

        _logger.info("Waiting 3 seconds for messages");

        try
        {
            _awaitMessages.await(3000, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            // do nothing
        }
        // Should have received all async messages
        assertEquals(MSG_COUNT, _receivedCount);

    }

    public void testReceiveThenUseMessageListener() throws Exception
    {
        _logger.error("Test disabled as initial receive is not called first");
        // Perform initial receive to start connection
        assertTrue(_consumer.receive(2000) != null);
        _receivedCount++;

        // Sleep to ensure remaining 4 msgs end up on _synchronousQueue
        Thread.sleep(1000);

        // Set the message listener and wait for the messages to come in.
        _consumer.setMessageListener(this);

        _logger.info("Waiting 3 seconds for messages");

        try
        {
            _awaitMessages.await(3000, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            // do nothing
        }
        // Should have received all async messages
        assertEquals(MSG_COUNT, _receivedCount);

        _clientConnection.close();

        Connection conn = getConnection("guest", "guest");
        Session clientSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = clientSession.createQueue("message-listener-test-queue");
        MessageConsumer cons = clientSession.createConsumer(queue);
        conn.start();

        // check that the messages were actually dequeued
        assertTrue(cons.receive(2000) == null);
    }

    /**
     * Tests the case where the message listener throws an java.lang.Error.
     *
     */
    public void testMessageListenerThrowsError() throws Exception
    {
        final String javaLangErrorMessageText = "MessageListener failed with java.lang.Error";
        _clientConnection.setExceptionListener(this);

        _awaitMessages = new CountDownLatch(1);

        _consumer.setMessageListener(new MessageListener()
        {
            public void onMessage(Message message)
            {
                try
                {
                    _logger.debug("onMessage called");
                    _receivedCount++;


                    throw new Error(javaLangErrorMessageText);
                }
                finally
                {
                    _awaitMessages.countDown();
                }
            }
        });


        _logger.info("Waiting 3 seconds for message");
        _awaitMessages.await(3000, TimeUnit.MILLISECONDS);

        assertEquals("onMessage should have been called", 1, _receivedCount);
        assertEquals("onException should NOT have been called", 0, _errorCount);

        // Check that Error has been written to the application log.

        LogMonitor _monitor = new LogMonitor(_outputFile);
        assertTrue("The expected message not written to log file.",
                _monitor.waitForMessage(javaLangErrorMessageText, LOGMONITOR_TIMEOUT));

        if (_clientConnection != null)
        {
            try
            {
                _clientConnection.close();
            }
            catch (JMSException e)
            {
                // Ignore connection close errors for this test.
            }
            finally
            {
                _clientConnection = null;
            }
        }
    }

    public void onMessage(Message message)
    {
        _logger.info("Received Message(" + _receivedCount + "):" + message);

        _receivedCount++;
        _awaitMessages.countDown();
    }

    @Override
    public void onException(JMSException e)
    {
        _logger.info("Exception received", e);
        _errorCount++;
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(MessageListenerTest.class);
    }
}
