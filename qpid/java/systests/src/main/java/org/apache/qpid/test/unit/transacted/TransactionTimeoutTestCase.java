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
package org.apache.qpid.test.unit.transacted;

import junit.framework.TestCase;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.util.LogMonitor;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@link TestCase} for transaction timeout testing.
 */
public abstract class TransactionTimeoutTestCase extends QpidBrokerTestCase implements ExceptionListener
{
    private static final int ALERT_MESSAGE_TOLERANCE = 6;
    public static final String VIRTUALHOST = "test";
    public static final String TEXT = "0123456789abcdefghiforgettherest";
    public static final String CHN_OPEN_TXN = "CHN-1007";
    public static final String CHN_IDLE_TXN = "CHN-1008";
    public static final String IDLE = "Idle";
    public static final String OPEN = "Open";
    
    protected LogMonitor _monitor;
    protected Connection _con;
    protected Session _psession, _csession;
    protected Queue _queue;
    protected MessageConsumer _consumer;
    protected MessageProducer _producer;
    protected Exception _exception;

    private final CountDownLatch _exceptionListenerLatch = new CountDownLatch(1);
    private final AtomicInteger _exceptionCount = new AtomicInteger(0);
    private volatile AMQConstant _linkedExceptionCode;
    private volatile String _linkedExceptionMessage;

    /**
     * Subclasses must implement this to configure transaction timeout parameters.
     */
    protected abstract void configure() throws Exception;

    @Override
    protected void setUp() throws Exception
    {
        // Configure timeouts
        configure();
        
        // Monitor log file
        _monitor = new LogMonitor(_outputFile);
        
        // Start broker
        super.setUp();
        
        // Connect to broker
        setTestClientSystemProperty(ClientProperties.MAX_PREFETCH_PROP_NAME, String.valueOf(1));
        _con = getConnection();
        _con.setExceptionListener(this);
        _con.start();
        
        // Create queue
        Session qsession = _con.createSession(true, Session.SESSION_TRANSACTED);
        _queue = qsession.createQueue(getTestQueueName());
        qsession.close();
        
        // Create producer and consumer
        producer();
        consumer();
    }

    /**
     * Create a transacted persistent message producer session.
     */
    protected void producer() throws Exception
    {
        _psession = _con.createSession(true, Session.SESSION_TRANSACTED);
        _producer = _psession.createProducer(_queue);
        _producer.setDeliveryMode(DeliveryMode.PERSISTENT);
    }

    /**
     * Create a transacted message consumer session.
     */
    protected void consumer() throws Exception
    {
        _csession = _con.createSession(true, Session.SESSION_TRANSACTED);
        _consumer = _csession.createConsumer(_queue);
    }

    /**
     * Send a number of messages to the queue, optionally pausing after each.
     *
     * Need to sync to ensure that the Broker has received the message(s) in order
     * the test and broker start timing the idle transaction from the same point in time.
     */
    protected void send(int count, float delay) throws Exception
    {
        for (int i = 0; i < count; i++)
        {
            sleep(delay);
            Message msg = _psession.createTextMessage(TEXT);
            msg.setIntProperty("i", i);
            _producer.send(msg);
        }

        ((AMQSession<?, ?>)_psession).sync();
    }
    
    /**
     * Sleep for a number of seconds.
     */
    protected void sleep(float seconds) throws Exception
    {
        try
        {
            Thread.sleep((long) (seconds * 1000.0f));
        }
        catch (InterruptedException ie)
        {
            throw new RuntimeException("Interrupted");
        }
    }
    
    /**
     * Check for idle and open messages.
     * 
     * Either exactly zero messages, or +-2 error accepted around the specified number.
     */
    protected void monitor(int idle, int open) throws Exception
    {
        List<String> idleMsgs = _monitor.findMatches(CHN_IDLE_TXN);
        List<String> openMsgs = _monitor.findMatches(CHN_OPEN_TXN);
        
        String idleErr = "Expected " + idle + " but found " + idleMsgs.size() + " txn idle messages";
        String openErr = "Expected " + open + " but found " + openMsgs.size() + " txn open messages";
        
        if (idle == 0)
        {
            assertTrue(idleErr, idleMsgs.isEmpty());
        }
        else
        {
	        assertTrue(idleErr, idleMsgs.size() >= idle - ALERT_MESSAGE_TOLERANCE && idleMsgs.size() <= idle + ALERT_MESSAGE_TOLERANCE);
        }
        
        if (open == 0)
        {
            assertTrue(openErr, openMsgs.isEmpty());
        }
        else
        {
            assertTrue(openErr, openMsgs.size() >= open - ALERT_MESSAGE_TOLERANCE && openMsgs.size() <= open + ALERT_MESSAGE_TOLERANCE);
        }
    }

    /**
     * Receive a number of messages, optionally pausing after each.
     */
    protected void expect(int count, float delay) throws Exception
    {
        for (int i = 0; i < count; i++)
        {
	        sleep(delay);
            Message msg = _consumer.receive(1000);
	        assertNotNull("Message should not be null", msg);
	        assertTrue("Message should be a text message", msg instanceof TextMessage);
	        assertEquals("Message content does not match expected", TEXT, ((TextMessage) msg).getText());
	        assertEquals("Message order is incorrect", i, msg.getIntProperty("i"));
        }
    }
    
    /**
     * Checks that the correct exception was thrown and was received
     * by the listener with a 506 error code.
     */
    protected void check(String reason) throws InterruptedException
    {
        assertNotNull("Should have thrown exception to client", _exception);

        assertTrue("Should have caught exception in listener", _exceptionListenerLatch.await(1, TimeUnit.SECONDS));
        assertNotNull("Linked exception message should not be null", _linkedExceptionMessage);
        assertTrue("Linked exception message '" + _linkedExceptionMessage + "' should contain '" + reason + "'",
                   _linkedExceptionMessage.contains(reason + " transaction timed out"));
        assertNotNull("Linked exception should have an error code", _linkedExceptionCode);
        assertEquals("Linked exception error code should be 506", AMQConstant.RESOURCE_ERROR, _linkedExceptionCode);
    }

    /** @see javax.jms.ExceptionListener#onException(javax.jms.JMSException) */
    @Override
    public void onException(JMSException jmse)
    {
        if (jmse.getLinkedException() != null)
        {
            _linkedExceptionMessage = jmse.getLinkedException().getMessage();
        }

        if (jmse.getLinkedException() instanceof AMQException)
        {
            _linkedExceptionCode = ((AMQException) jmse.getLinkedException()).getErrorCode();
        }
        _exceptionCount.incrementAndGet();
        _exceptionListenerLatch.countDown();
    }

    protected int getNumberOfDeliveredExceptions()
    {
        return _exceptionCount.get();
    }
}
