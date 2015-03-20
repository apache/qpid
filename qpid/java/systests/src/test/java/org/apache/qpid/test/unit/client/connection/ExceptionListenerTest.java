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
package org.apache.qpid.test.unit.client.connection;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.qpid.AMQConnectionClosedException;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.transport.ConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionListenerTest extends QpidBrokerTestCase
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionListenerTest.class);

    private volatile Throwable _lastExceptionListenerException = null;

    public void testExceptionListenerHearsBrokerShutdown() throws  Exception
    {
        final CountDownLatch exceptionReceivedLatch  = new CountDownLatch(1);
        final AtomicInteger exceptionCounter = new AtomicInteger(0);
        final ExceptionListener listener = new ExceptionListener()
        {
            public void onException(JMSException exception)
            {
                exceptionCounter.incrementAndGet();
                _lastExceptionListenerException = exception;
                exceptionReceivedLatch.countDown();
            }
        };

        Connection connection = getConnection();
        connection.setExceptionListener(listener);

        stopBroker();

        exceptionReceivedLatch.await(10, TimeUnit.SECONDS);

        assertEquals("Unexpected number of exceptions received", 1, exceptionCounter.intValue());
        LOGGER.debug("exception was", _lastExceptionListenerException);
        assertNotNull("Exception should have cause", _lastExceptionListenerException.getCause());
        Class<? extends Exception> expectedExceptionClass = isBroker010() ? ConnectionException.class : AMQConnectionClosedException.class;
        assertEquals(expectedExceptionClass, _lastExceptionListenerException.getCause().getClass());
    }

    /**
     * It is reasonable for an application to perform Connection#close within the exception
     * listener.  This test verifies that close is allowed, and proceeds without generating
     * further exceptions.
     */
    public void testExceptionListenerClosesConnection_IsAllowed() throws  Exception
    {
        final CountDownLatch exceptionReceivedLatch  = new CountDownLatch(1);
        final Connection connection = getConnection();
        final ExceptionListener listener = new ExceptionListener()
        {
            public void onException(JMSException exception)
            {
                try
                {
                    connection.close();
                    // PASS
                }
                catch (Throwable t)
                {
                    _lastExceptionListenerException = t;
                }
                finally
                {
                    exceptionReceivedLatch.countDown();
                }
            }
        };
        connection.setExceptionListener(listener);


        stopBroker();

        boolean exceptionReceived = exceptionReceivedLatch.await(10, TimeUnit.SECONDS);
        assertTrue("Exception listener did not hear exception within timeout", exceptionReceived);
        assertNull("Connection#close() should not have thrown exception", _lastExceptionListenerException);
    }

    /**
     * Spring's SingleConnectionFactory installs an ExceptionListener that calls stop()
     * and ignores any IllegalStateException that result.  This test serves to test this
     * scenario.
     */
    public void testExceptionListenerStopsConnection_ThrowsIllegalStateException() throws  Exception
    {
        final CountDownLatch exceptionReceivedLatch  = new CountDownLatch(1);
        final Connection connection = getConnection();
        final ExceptionListener listener = new ExceptionListener()
        {
            public void onException(JMSException exception)
            {
                try
                {
                    connection.stop();
                    fail("Exception not thrown");
                }
                catch (IllegalStateException ise)
                {
                    // PASS
                }
                catch (Throwable t)
                {
                    _lastExceptionListenerException = t;
                }
                finally
                {
                    exceptionReceivedLatch.countDown();
                }
            }
        };
        connection.setExceptionListener(listener);

        stopBroker();

        boolean exceptionReceived = exceptionReceivedLatch.await(10, TimeUnit.SECONDS);
        assertTrue("Exception listener did not hear exception within timeout", exceptionReceived);
        assertNull("Connection#stop() should not have thrown unexpected exception", _lastExceptionListenerException);
    }

    /**
     * This test reproduces a deadlock that was the subject of a support call. A Spring based
     * application was using SingleConnectionFactory.  It installed an ExceptionListener that
     * stops and closes the connection in response to any exception.  On receipt of a message
     * the application would create a new session then send a response message (within onMessage).
     * It appears that a misconfiguration in the application meant that some of these messages
     * were bounced (no-route). Bounces are treated like connection exceptions and are passed
     * back to the application via the ExceptionListener.  The deadlock occurred between the
     * ExceptionListener's call to stop() and the MessageListener's attempt to create a new
     * session.
     */
    public void testExceptionListenerConnectionStopDeadlock() throws  Exception
    {
        Queue messageQueue = getTestQueue();

        Map<String, String> options = new HashMap<String, String>();
        options.put(ConnectionURL.OPTIONS_CLOSE_WHEN_NO_ROUTE, Boolean.toString(false));

        final Connection connection = getConnectionWithOptions(options);

        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        session.createConsumer(messageQueue).close(); // Create queue by side-effect

        // Put 10 messages onto messageQueue
        sendMessage(session, messageQueue, 10);

        // Install an exception listener that stops/closes the connection on receipt of 2nd AMQNoRouteException.
        // (Triggering on the 2nd (rather than 1st) seems to increase the probability that the test ends in deadlock,
        // at least on my machine).
        final CountDownLatch exceptionReceivedLatch = new CountDownLatch(2);
        final AtomicBoolean doneClosed = new AtomicBoolean();
        final CountDownLatch connectionClosedAttemptLatch = new CountDownLatch(1);
        final AtomicReference<Exception> connectionCloseException = new AtomicReference<>();
        final ExceptionListener listener = new ExceptionListener()
        {
            public void onException(JMSException exception)
            {
                exceptionReceivedLatch.countDown();
                if (exceptionReceivedLatch.getCount() == 0)
                {
                    try
                    {
                        if (doneClosed.compareAndSet(false, true))
                        {
                            connection.stop();
                            connection.close();
                        }
                    }
                    catch (Exception e)
                    {
                        // We expect no exception to be caught
                        connectionCloseException.set(e);
                    }
                    finally
                    {
                        connectionClosedAttemptLatch.countDown();
                    }

                }
            }
        };
        connection.setExceptionListener(listener);

        // Create a message listener that receives from testQueue and tries to forward them to unknown queue (thus
        // provoking AMQNoRouteException exceptions to be delivered to the ExceptionListener).
        final Queue unknownQueue = session.createQueue(getTestQueueName() + "_unknown");
        MessageListener redirectingMessageListener = new MessageListener()
        {
            @Override
            public void onMessage(Message msg)
            {
                try
                {
                    Session mlSession = connection.createSession(true, Session.SESSION_TRANSACTED);  // ** Deadlock
                    mlSession.createProducer(unknownQueue).send(msg);  // will cause async AMQNoRouteException;
                    mlSession.commit();
                }
                catch (JMSException je)
                {
                    // Connection is closed by the listener, so exceptions here are expected.
                    LOGGER.debug("Expected exception - message listener got exception", je);
                }
            }
        };

        MessageConsumer consumer = session.createConsumer(messageQueue);
        consumer.setMessageListener(redirectingMessageListener);
        connection.start();

        // Await an exception
        boolean exceptionReceived = exceptionReceivedLatch.await(10, TimeUnit.SECONDS);
        assertTrue("Exception listener did not hear at least one exception within timeout", exceptionReceived);

        // Await the connection listener to close the connection
        boolean closeAttemptedReceived = connectionClosedAttemptLatch.await(10, TimeUnit.SECONDS);
        assertTrue("Exception listener did not try to close the exception within timeout", closeAttemptedReceived);
        assertNull("Exception listener should not have had experienced an exception : " + connectionCloseException.get(), connectionCloseException.get());
    }


}
