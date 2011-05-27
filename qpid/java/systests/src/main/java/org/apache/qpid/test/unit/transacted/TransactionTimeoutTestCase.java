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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.DeliveryMode;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.TextMessage;

import junit.framework.TestCase;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.jms.Session;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.util.LogMonitor;

/**
 * The {@link TestCase} for transaction timeout testing.
 */
public class TransactionTimeoutTestCase extends QpidBrokerTestCase implements ExceptionListener
{
    public static final String VIRTUALHOST = "test";
    public static final String TEXT = "0123456789abcdefghiforgettherest";
    public static final String CHN_OPEN_TXN = "CHN-1007";
    public static final String CHN_IDLE_TXN = "CHN-1008";
    public static final String IDLE = "Idle";
    public static final String OPEN = "Open";
    
    protected LogMonitor _monitor;
    protected AMQConnection _con;
    protected Session _psession, _csession;
    protected Queue _queue;
    protected MessageConsumer _consumer;
    protected MessageProducer _producer;
    protected CountDownLatch _caught = new CountDownLatch(1);
    protected String _message;
    protected Exception _exception;
    protected AMQConstant _code;
    
    protected void configure() throws Exception
    {
        // Setup housekeeping every second
        setConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".housekeeping.expiredMessageCheckPeriod", "100");
        
        /*
         * Set transaction timout properties. The XML in the virtualhosts configuration is as follows:
         * 
         *  <transactionTimeout>
         *      <openWarn>1000</openWarn>
         *      <openClose>2000</openClose>
         *      <idleWarn>500</idleWarn>
         *      <idleClose>1500</idleClose>
         *  </transactionTimeout>
         */
        setConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.openWarn", "1000");
        setConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.openClose", "2000");
        setConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.idleWarn", "500");
        setConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.idleClose", "1000");
    }
        
    protected void setUp() throws Exception
    {
        // Configure timeouts
        configure();
        
        // Monitor log file
        _monitor = new LogMonitor(_outputFile);
        
        // Start broker
        super.setUp();
        
        // Connect to broker
        String broker = _broker.equals(VM) ? ("vm://:" + DEFAULT_VM_PORT) : ("tcp://localhost:" + DEFAULT_PORT);
        ConnectionURL url = new AMQConnectionURL("amqp://guest:guest@clientid/test?brokerlist='" + broker + "'&maxprefetch='1'");
        _con = (AMQConnection) getConnection(url);
        _con.setExceptionListener(this);
        _con.start();
        
        // Create queue
        Session qsession = _con.createSession(true, Session.SESSION_TRANSACTED);
        AMQShortString queueName = new AMQShortString("test");
        _queue = new AMQQueue(qsession.getDefaultQueueExchangeName(), queueName, queueName, false, true);
        qsession.close();
        
        // Create producer and consumer
        producer();
        consumer();
    }
    
    protected void tearDown() throws Exception
    {
        try
        {
            _con.close();
        }
        finally
        {
            super.tearDown();
        }
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
	        assertTrue(idleErr, idleMsgs.size() >= idle - 2 && idleMsgs.size() <= idle + 2);
        }
        
        if (open == 0)
        {
            assertTrue(openErr, openMsgs.isEmpty());
        }
        else
        {
            assertTrue(openErr, openMsgs.size() >= open - 2 && openMsgs.size() <= open + 2);
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
    protected void check(String reason)throws InterruptedException
    {
        assertTrue("Should have caught exception in listener", _caught.await(1, TimeUnit.SECONDS));
        assertNotNull("Should have thrown exception to client", _exception);
        assertTrue("Exception message should contain '" + reason + "': " + _message, _message.contains(reason + " transaction timed out"));
        assertNotNull("Exception should have an error code", _code);
        assertEquals("Error code should be 506", AMQConstant.RESOURCE_ERROR, _code);
    }

    /** @see javax.jms.ExceptionListener#onException(javax.jms.JMSException) */
    public void onException(JMSException jmse)
    {
        _caught.countDown();
        _message = jmse.getLinkedException().getMessage();
        if (jmse.getLinkedException() instanceof AMQException)
        {
            _code = ((AMQException) jmse.getLinkedException()).getErrorCode();
        }
    }
}
