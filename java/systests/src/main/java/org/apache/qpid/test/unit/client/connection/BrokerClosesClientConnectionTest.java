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
package org.apache.qpid.test.unit.client.connection;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.AMQConnectionClosedException;
import org.apache.qpid.AMQDisconnectedException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.management.jmx.ManagedConnectionMBeanTest;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.transport.ConnectionException;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Session;

/**
 * Tests the behaviour of the client when the Broker terminates client connection
 * by the Broker being shutdown gracefully or otherwise.
 *
 * @see ManagedConnectionMBeanTest
 */
public class BrokerClosesClientConnectionTest extends QpidBrokerTestCase
{
    private Connection _connection;
    private boolean _isExternalBroker;
    private final RecordingExceptionListener _recordingExceptionListener = new RecordingExceptionListener();

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _connection = getConnection();
        _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _connection.setExceptionListener(_recordingExceptionListener);

        _isExternalBroker = isExternalBroker();
    }

    public void testClientCloseOnNormalBrokerShutdown() throws Exception
    {
        final Class<? extends Exception> expectedLinkedException = isBroker010() ? ConnectionException.class : AMQConnectionClosedException.class;

        assertConnectionOpen();

        stopBroker();

        JMSException exception = _recordingExceptionListener.awaitException(10000);
        assertConnectionCloseWasReported(exception, expectedLinkedException);
        assertConnectionClosed();

        ensureCanCloseWithoutException();
    }

    public void testClientCloseOnBrokerKill() throws Exception
    {
        final Class<? extends Exception> expectedLinkedException = isBroker010() ? ConnectionException.class : AMQDisconnectedException.class;

        if (!_isExternalBroker)
        {
            return;
        }

        assertConnectionOpen();

        killBroker();

        JMSException exception = _recordingExceptionListener.awaitException(10000);
        assertConnectionCloseWasReported(exception, expectedLinkedException);
        assertConnectionClosed();

        ensureCanCloseWithoutException();
    }

    private void ensureCanCloseWithoutException()
    {
        try
        {
            _connection.close();
        }
        catch (JMSException e)
        {
            fail("Connection should close without exception" + e.getMessage());
        }
    }

    private void assertConnectionCloseWasReported(JMSException exception, Class<? extends Exception> linkedExceptionClass)
    {
        assertNotNull("Broker shutdown should be reported to the client via the ExceptionListener", exception);
        assertNotNull("JMXException should have linked exception", exception.getLinkedException());

        assertEquals("Unexpected linked exception", linkedExceptionClass, exception.getLinkedException().getClass());
    }

    private void assertConnectionClosed()
    {
        assertTrue("Connection should be marked as closed", ((AMQConnection)_connection).isClosed());
    }

    private void assertConnectionOpen()
    {
        assertFalse("Connection should not be marked as closed", ((AMQConnection)_connection).isClosed());
    }

    private final class RecordingExceptionListener implements ExceptionListener
    {
        private final CountDownLatch _exceptionReceivedLatch = new CountDownLatch(1);
        private volatile JMSException _exception;

        @Override
        public void onException(JMSException exception)
        {
            _exception = exception;
        }

        public JMSException awaitException(long timeoutInMillis) throws InterruptedException
        {
            _exceptionReceivedLatch.await(timeoutInMillis, TimeUnit.MILLISECONDS);
            return _exception;
        }
    }
}
