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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.util.LogMonitor;

/**
 * Tests the behaviour of JMS asynchronous message listeners as provided by
 * {@link MessageListener#onMessage(Message)}.
 *
 */
public class AsynchMessageListenerTest extends QpidBrokerTestCase
{
    private static final int MSG_COUNT = 10;
    private static final long AWAIT_MESSAGE_TIMEOUT = 2000;
    private static final long AWAIT_MESSAGE_TIMEOUT_NEGATIVE = 250;
    private String _testQueueName;
    private Connection _consumerConnection;
    private Session _consumerSession;
    private MessageConsumer _consumer;
    private Queue _queue;

    protected void setUp() throws Exception
    {
        super.setUp();
        _testQueueName = getTestQueueName();
        _consumerConnection = getConnection();
        _consumerConnection.start();
        _consumerSession = _consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _queue = _consumerSession.createQueue(_testQueueName);
        _consumer = _consumerSession.createConsumer(_queue);

        // Populate queue
        Connection producerConnection = getConnection();
        Session producerSession = producerConnection.createSession(true, Session.SESSION_TRANSACTED);
        sendMessage(producerSession, _queue, MSG_COUNT);
        producerConnection.close();

    }




    public void testMessageListener() throws Exception
    {
        CountingMessageListener countingMessageListener = new CountingMessageListener(MSG_COUNT);
        _consumer.setMessageListener(countingMessageListener);
        countingMessageListener.awaitMessages(AWAIT_MESSAGE_TIMEOUT);

        assertEquals("Unexpected number of outstanding messages", 0, countingMessageListener.getOutstandingCount());
    }

    public void testSynchronousReceiveFollowedByMessageListener() throws Exception
    {
        // Receive initial message synchronously
        assertNotNull("Could not receive first message synchronously", _consumer.receive(AWAIT_MESSAGE_TIMEOUT) != null);
        final int numberOfMessagesToReceiveByMessageListener = MSG_COUNT - 1;

        // Consume remainder asynchronously
        CountingMessageListener countingMessageListener = new CountingMessageListener(numberOfMessagesToReceiveByMessageListener);
        _consumer.setMessageListener(countingMessageListener);
        countingMessageListener.awaitMessages(AWAIT_MESSAGE_TIMEOUT);

        assertEquals("Unexpected number of outstanding messages", 0, countingMessageListener.getOutstandingCount());
    }

    public void testMessageListenerSetDisallowsSynchronousReceive() throws Exception
    {
        CountingMessageListener countingMessageListener = new CountingMessageListener(MSG_COUNT);
        _consumer.setMessageListener(countingMessageListener);

        try
        {
            _consumer.receive();
            fail("Exception not thrown");
        }
        catch (JMSException e)
        {
            // PASS
            assertEquals("A listener has already been set.", e.getMessage());
        }
    }


    public void testConnectionStopThenStart() throws Exception
    {
        int messageToReceivedBeforeConnectionStop = 2;
        CountingMessageListener countingMessageListener = new CountingMessageListener(MSG_COUNT, messageToReceivedBeforeConnectionStop);

        // Consume at least two messages
        _consumer.setMessageListener(countingMessageListener);
        countingMessageListener.awaitMessages(AWAIT_MESSAGE_TIMEOUT);

        _consumerConnection.stop();

        assertTrue("Too few messages received afer Connection#stop()", countingMessageListener.getReceivedCount() >= messageToReceivedBeforeConnectionStop);
        countingMessageListener.resetLatch();

        // Restart connection
        _consumerConnection.start();

        // Consume the remainder
        countingMessageListener.awaitMessages(AWAIT_MESSAGE_TIMEOUT);

        assertEquals("Unexpected number of outstanding messages", 0, countingMessageListener.getOutstandingCount());
    }

    public void testConnectionStopAndMessageListenerChange() throws Exception
    {
        int messageToReceivedBeforeConnectionStop = 2;
        CountingMessageListener countingMessageListener1 = new CountingMessageListener(MSG_COUNT, messageToReceivedBeforeConnectionStop);

        // Consume remainder asynchronously
        _consumer.setMessageListener(countingMessageListener1);
        countingMessageListener1.awaitMessages(AWAIT_MESSAGE_TIMEOUT);

        _consumerConnection.stop();
        assertTrue("Too few messages received afer Connection#stop()", countingMessageListener1.getReceivedCount() >= messageToReceivedBeforeConnectionStop);

        CountingMessageListener countingMessageListener2 = new CountingMessageListener(countingMessageListener1.getOutstandingCount());

        // Reset Message Listener
        _consumer.setMessageListener(countingMessageListener2);

        _consumerConnection.start();

        // Consume the remainder
        countingMessageListener2.awaitMessages(AWAIT_MESSAGE_TIMEOUT);

        assertEquals("Unexpected number of outstanding messages", 0, countingMessageListener2.getOutstandingCount());

    }

    public void testConnectionStopHaltsDeliveryToListener() throws Exception
    {
        int messageToReceivedBeforeConnectionStop = 2;
        CountingMessageListener countingMessageListener = new CountingMessageListener(MSG_COUNT, messageToReceivedBeforeConnectionStop);

        // Consume at least two messages
        _consumer.setMessageListener(countingMessageListener);
        countingMessageListener.awaitMessages(AWAIT_MESSAGE_TIMEOUT);

        _consumerConnection.stop();

        // Connection should now be stopped and listener should receive no more
        final int outstandingCountAtStop = countingMessageListener.getOutstandingCount();
        countingMessageListener.resetLatch();
        countingMessageListener.awaitMessages(AWAIT_MESSAGE_TIMEOUT_NEGATIVE);

        assertEquals("Unexpected number of outstanding messages", outstandingCountAtStop, countingMessageListener.getOutstandingCount());
    }

    public void testSessionCloseHaltsDelivery() throws Exception
    {
        int messageToReceivedBeforeConnectionStop = 2;
        CountingMessageListener countingMessageListener = new CountingMessageListener(MSG_COUNT, messageToReceivedBeforeConnectionStop);

        // Consume at least two messages
        _consumer.setMessageListener(countingMessageListener);
        countingMessageListener.awaitMessages(AWAIT_MESSAGE_TIMEOUT);

        _consumerSession.close();

        // Once a session is closed, the listener should receive no more
        final int outstandingCountAtClose = countingMessageListener.getOutstandingCount();
        countingMessageListener.resetLatch();
        countingMessageListener.awaitMessages(AWAIT_MESSAGE_TIMEOUT_NEGATIVE);

        assertEquals("Unexpected number of outstanding messages", outstandingCountAtClose, countingMessageListener.getOutstandingCount());
    }

    public void testImmediatePrefetchWithMessageListener() throws Exception
    {
        // Close connection provided by setup so we can set IMMEDIATE_PREFETCH
        _consumerConnection.close();
        setTestClientSystemProperty(AMQSession.IMMEDIATE_PREFETCH, "true");

        _consumerConnection = getConnection();
        _consumerConnection.start();
        _consumerSession = _consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _consumer = _consumerSession.createConsumer(_queue);
        CountingMessageListener countingMessageListener = new CountingMessageListener(MSG_COUNT);
        _consumer.setMessageListener(countingMessageListener);

        countingMessageListener.awaitMessages(AWAIT_MESSAGE_TIMEOUT);

        assertEquals("Unexpected number of messages received", MSG_COUNT, countingMessageListener.getReceivedCount());
    }

    public void testReceiveTwoConsumers() throws Exception
    {
        Session consumerSession2 = _consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer _consumer2 = consumerSession2.createConsumer(_queue);

        CountingMessageListener countingMessageListener = new CountingMessageListener(MSG_COUNT);
        _consumer.setMessageListener(countingMessageListener);
        _consumer2.setMessageListener(countingMessageListener);

        countingMessageListener.awaitMessages(AWAIT_MESSAGE_TIMEOUT);
        assertEquals("Unexpected number of messages received", MSG_COUNT, countingMessageListener.getReceivedCount());
    }

    /**
     * Tests the case where the message listener throws an java.lang.Error.
     * TODO - a useful test?.
     */
    public void testMessageListenerThrowsError() throws Exception
    {
        int expectedMessages = 1;  // The error will kill the dispatcher so only one message will be delivered.
        final CountDownLatch awaitMessages = new CountDownLatch(expectedMessages);
        final AtomicInteger receivedCount = new AtomicInteger(0);
        final String javaLangErrorMessageText = "MessageListener failed with java.lang.Error";
        CountingExceptionListener countingExceptionListener = new CountingExceptionListener();
        _consumerConnection.setExceptionListener(countingExceptionListener);

        _consumer.setMessageListener(new MessageListener()
        {
            @Override
            public void onMessage(Message message)
            {
                try
                {
                    throw new Error(javaLangErrorMessageText);
                }
                finally
                {
                    receivedCount.incrementAndGet();
                    awaitMessages.countDown();
                }
            }
        });

        awaitMessages.await(AWAIT_MESSAGE_TIMEOUT, TimeUnit.MILLISECONDS);

        assertEquals("Unexpected number of messages received", expectedMessages, receivedCount.get());
        assertEquals("onException should NOT have been called", 0, countingExceptionListener.getErrorCount());

        // Check that Error has been written to the application log.

        LogMonitor _monitor = new LogMonitor(_outputFile);
        assertTrue("The expected message not written to log file.",
                _monitor.waitForMessage(javaLangErrorMessageText, LOGMONITOR_TIMEOUT));

        if (_consumerConnection != null)
        {
            try
            {
                _consumerConnection.close();
            }
            catch (JMSException e)
            {
                // Ignore connection close errors for this test.
            }
            finally
            {
                _consumerConnection = null;
            }
        }
    }

    private final class CountingExceptionListener implements ExceptionListener
    {
        private final AtomicInteger _errorCount = new AtomicInteger();

        @Override
        public void onException(JMSException arg0)
        {
            _errorCount.incrementAndGet();
        }

        public int getErrorCount()
        {
            return _errorCount.intValue();
        }
    }

    private final class CountingMessageListener implements MessageListener
    {
        private volatile CountDownLatch _awaitMessages;
        private final AtomicInteger _receivedCount;
        private final AtomicInteger _outstandingMessageCount;

        public CountingMessageListener(final int totalExpectedMessageCount)
        {
            this(totalExpectedMessageCount, totalExpectedMessageCount);
        }


        public CountingMessageListener(int totalExpectedMessageCount, int numberOfMessagesToAwait)
        {
            _receivedCount = new AtomicInteger(0);
            _outstandingMessageCount = new AtomicInteger(totalExpectedMessageCount);
            _awaitMessages = new CountDownLatch(numberOfMessagesToAwait);
        }

        public int getOutstandingCount()
        {
            return _outstandingMessageCount.get();
        }

        public int getReceivedCount()
        {
            return _receivedCount.get();
        }

        public void resetLatch()
        {
            _awaitMessages = new CountDownLatch(_outstandingMessageCount.get());
        }

        @Override
        public void onMessage(Message message)
        {
            _receivedCount.incrementAndGet();
            _outstandingMessageCount.decrementAndGet();
            _awaitMessages.countDown();
        }

        public boolean awaitMessages(long timeout)
        {
            try
            {
                return _awaitMessages.await(timeout, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

}
