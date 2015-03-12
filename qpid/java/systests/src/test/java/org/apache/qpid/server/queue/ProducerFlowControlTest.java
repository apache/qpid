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
package org.apache.qpid.server.queue;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.server.logging.AbstractTestLogging;
import org.apache.qpid.test.utils.JMXTestUtils;

public class ProducerFlowControlTest extends AbstractTestLogging
{
    private static final Logger _logger = LoggerFactory.getLogger(ProducerFlowControlTest.class);

    private static final int TIMEOUT = 10000;

    private Connection producerConnection;
    private Connection consumerConnection;
    private Session producerSession;
    private Session consumerSession;
    private MessageProducer producer;
    private MessageConsumer consumer;
    private Queue queue;

    private final AtomicInteger _sentMessages = new AtomicInteger(0);

    private JMXTestUtils _jmxUtils;
    private boolean _jmxUtilConnected;

    public void setUp() throws Exception
    {
        getBrokerConfiguration().addJmxManagementConfiguration();

        _jmxUtils = new JMXTestUtils(this);
        _jmxUtilConnected=false;
        super.setUp();

        _monitor.markDiscardPoint();

        producerConnection = getConnection();
        producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        producerConnection.start();

        consumerConnection = getConnection();
        consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    }

    public void tearDown() throws Exception
    {
        try
        {
            if(_jmxUtilConnected)
            {
                try
                {
                    _jmxUtils.close();
                }
                catch (IOException e)
                {
                    _logger.error("Error closing jmxUtils", e);
                }
            }
            producerConnection.close();
            consumerConnection.close();
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testCapacityExceededCausesBlock() throws Exception
    {
        String queueName = getTestQueueName();

        createAndBindQueueWithFlowControlEnabled(producerSession, queueName, 1000, 800);
        producer = producerSession.createProducer(queue);

        // try to send 5 messages (should block after 4)
        sendMessagesAsync(producer, producerSession, 5, 50L);

        Thread.sleep(5000);

        assertEquals("Incorrect number of message sent before blocking", 4, _sentMessages.get());

        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();


        consumer.receive();

        Thread.sleep(1000);

        assertEquals("Message incorrectly sent after one message received", 4, _sentMessages.get());


        consumer.receive();

        Thread.sleep(1000);

        assertEquals("Message not sent after two messages received", 5, _sentMessages.get());

    }


    public void testBrokerLogMessages() throws Exception
    {
        String queueName = getTestQueueName();
        
        createAndBindQueueWithFlowControlEnabled(producerSession, queueName, 1000, 800);
        producer = producerSession.createProducer(queue);

        // try to send 5 messages (should block after 4)
        sendMessagesAsync(producer, producerSession, 5, 50L);

        List<String> results = waitAndFindMatches("QUE-1003", 7000);

        assertEquals("Did not find correct number of QUE-1003 queue overfull messages", 1, results.size());

        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();


        while(consumer.receive(1000) != null) {};

        results = waitAndFindMatches("QUE-1004");

        assertEquals("Did not find correct number of UNDERFULL queue underfull messages", 1, results.size());
    }


    public void testClientLogMessages() throws Exception
    {
        String queueName = getTestQueueName();

        setTestClientSystemProperty("qpid.flow_control_wait_failure","3000");
        setTestClientSystemProperty("qpid.flow_control_wait_notify_period","1000");

        Session session = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        createAndBindQueueWithFlowControlEnabled(session, queueName, 1000, 800);
        producer = session.createProducer(queue);

        // try to send 5 messages (should block after 4)
        MessageSender sender = sendMessagesAsync(producer, session, 5, 50L);

        List<String> results = waitAndFindMatches("Message send delayed by", TIMEOUT);
        assertTrue("No delay messages logged by client",results.size()!=0);

        List<String> failedMessages = waitAndFindMatches("Message send failed due to timeout waiting on broker enforced"
                                                  + " flow control", TIMEOUT);
        assertEquals("Incorrect number of send failure messages logged by client (got " + results.size() + " delay "
                     + "messages)",1,failedMessages.size());
    }


    public void testFlowControlOnCapacityResumeEqual() throws Exception
    {
        String queueName = getTestQueueName();
        
        createAndBindQueueWithFlowControlEnabled(producerSession, queueName, 1000, 1000);
        producer = producerSession.createProducer(queue);


        // try to send 5 messages (should block after 4)
        sendMessagesAsync(producer, producerSession, 5, 50L);

        Thread.sleep(5000);

        assertEquals("Incorrect number of message sent before blocking", 4, _sentMessages.get());

        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();

        consumer.receive();

        Thread.sleep(1000);

        assertEquals("Message incorrectly sent after one message received", 5, _sentMessages.get());
        

    }


    public void testFlowControlSoak() throws Exception
    {
        String queueName = getTestQueueName();
        

        final int numProducers = 10;
        final int numMessages = 100;

        createAndBindQueueWithFlowControlEnabled(producerSession, queueName, 6000, 3000);

        consumerConnection.start();

        Connection[] producers = new Connection[numProducers];
        for(int i = 0 ; i < numProducers; i ++)
        {

            producers[i] = getConnection();
            producers[i].start();
            Session session = producers[i].createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageProducer myproducer = session.createProducer(queue);
            MessageSender sender = sendMessagesAsync(myproducer, session, numMessages, 50L);
        }

        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();

        for(int j = 0; j < numProducers * numMessages; j++)
        {
        
            Message msg = consumer.receive(5000);
            Thread.sleep(50L);
            assertNotNull("Message not received("+j+"), sent: "+_sentMessages.get(), msg);

        }



        Message msg = consumer.receive(500);
        assertNull("extra message received", msg);


        for(int i = 0; i < numProducers; i++)
        {
            producers[i].close();
        }

    }

    public void testSendTimeout() throws Exception
    {
        String queueName = getTestQueueName();
        final String expectedMsg = isBroker010() ? "Exception when sending message:timed out waiting for message credit"
                : "Unable to send message for 3 seconds due to broker enforced flow control";

        setTestClientSystemProperty("qpid.flow_control_wait_failure","3000");
        Session session = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        createAndBindQueueWithFlowControlEnabled(producerSession, queueName, 1000, 800);
        producer = session.createProducer(queue);

        // try to send 5 messages (should block after 4)
        MessageSender sender = sendMessagesAsync(producer, session, 5, 100L);

        Exception e = sender.awaitSenderException(10000);

        assertNotNull("No timeout exception on sending", e);


        assertEquals("Unexpected exception reason", expectedMsg, e.getMessage());

    }

    public void testFlowControlAttributeModificationViaJMX() throws Exception
    {
        _jmxUtils.open();
        _jmxUtilConnected = true;
        
        String queueName = getTestQueueName();

        createAndBindQueueWithFlowControlEnabled(producerSession, queueName, 0, 0);
        producer = producerSession.createProducer(queue);
        
        Thread.sleep(1000);
        
        //Create a JMX MBean proxy for the queue
        ManagedQueue queueMBean = _jmxUtils.getManagedObject(ManagedQueue.class, _jmxUtils.getQueueObjectName("test", queueName));
        assertNotNull(queueMBean);
        
        //check current attribute values are 0 as expected
        assertTrue("Capacity was not the expected value", queueMBean.getCapacity() == 0L);
        assertTrue("FlowResumeCapacity was not the expected value", queueMBean.getFlowResumeCapacity() == 0L);
        
        //set new values that will cause flow control to be active, and the queue to become overfull after 1 message is sent
        queueMBean.setCapacity(250L);
        queueMBean.setFlowResumeCapacity(250L);
        assertTrue("Capacity was not the expected value", queueMBean.getCapacity() == 250L);
        assertTrue("FlowResumeCapacity was not the expected value", queueMBean.getFlowResumeCapacity() == 250L);
        assertFalse("Queue should not be overfull", queueMBean.isFlowOverfull());
        
        // try to send 2 messages (should block after 1)

        sendMessagesAsync(producer, producerSession, 2, 50L);

        Thread.sleep(2000);

        //check only 1 message was sent, and queue is overfull
        assertEquals("Incorrect number of message sent before blocking", 1, _sentMessages.get());
        assertTrue("Queue should be overfull", queueMBean.isFlowOverfull());
        
        //raise the attribute values, causing the queue to become underfull and allow the second message to be sent.
        queueMBean.setCapacity(300L);
        queueMBean.setFlowResumeCapacity(300L);
        
        Thread.sleep(2000);

        //check second message was sent, and caused the queue to become overfull again
        assertEquals("Second message was not sent after lifting FlowResumeCapacity", 2, _sentMessages.get());
        assertTrue("Queue should be overfull", queueMBean.isFlowOverfull());
        
        //raise capacity above queue depth, check queue remains overfull as FlowResumeCapacity still exceeded
        queueMBean.setCapacity(700L);
        assertTrue("Queue should be overfull", queueMBean.isFlowOverfull());
        
        //receive a message, check queue becomes underfull
        
        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();
        
        consumer.receive();
        
        //perform a synchronous op on the connection
        ((AMQSession<?,?>) consumerSession).sync();
        
        assertFalse("Queue should not be overfull", queueMBean.isFlowOverfull());
        
        consumer.receive();
    }

    public void testQueueDeleteWithBlockedFlow() throws Exception
    {
        String queueName = getTestQueueName();
        createAndBindQueueWithFlowControlEnabled(producerSession, queueName, 1000, 800, true, false);

        producer = producerSession.createProducer(queue);

        // try to send 5 messages (should block after 4)
        sendMessagesAsync(producer, producerSession, 5, 50L);

        Thread.sleep(5000);

        assertEquals("Incorrect number of message sent before blocking", 4, _sentMessages.get());

        // close blocked producer session and connection
        producerConnection.close();

        // delete queue with a consumer session
        ((AMQSession<?,?>) consumerSession).sendQueueDelete(new AMQShortString(queueName));

        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();

        Message message = consumer.receive(1000l);
        assertNull("Unexpected message", message);
    }

    private void createAndBindQueueWithFlowControlEnabled(Session session, String queueName, int capacity, int resumeCapacity) throws Exception
    {
        createAndBindQueueWithFlowControlEnabled(session, queueName, capacity, resumeCapacity, false, true);
    }

    private void createAndBindQueueWithFlowControlEnabled(Session session, String queueName, int capacity, int resumeCapacity, boolean durable, boolean autoDelete) throws Exception
    {
        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("x-qpid-capacity",capacity);
        arguments.put("x-qpid-flow-resume-capacity",resumeCapacity);
        ((AMQSession<?,?>) session).createQueue(new AMQShortString(queueName), autoDelete, durable, false, arguments);
        queue = session.createQueue("direct://amq.direct/"+queueName+"/"+queueName+"?durable='" + durable + "'&autodelete='" + autoDelete + "'");
        ((AMQSession<?,?>) session).declareAndBind((AMQDestination)queue);
    }

    private MessageSender sendMessagesAsync(final MessageProducer producer,
                                            final Session producerSession,
                                            final int numMessages,
                                            long sleepPeriod)
    {
        MessageSender sender = new MessageSender(producer, producerSession, numMessages,sleepPeriod);
        new Thread(sender).start();
        return sender;
    }

    private void sendMessages(MessageProducer producer, Session producerSession, int numMessages, long sleepPeriod)
            throws JMSException
    {

        for (int msg = 0; msg < numMessages; msg++)
        {
            producer.send(nextMessage(msg, producerSession));
            _sentMessages.incrementAndGet();


            try
            {
                ((AMQSession<?,?>)producerSession).sync();
            }
            catch (AMQException e)
            {
                _logger.error("Error performing sync", e);
                throw new RuntimeException(e);
            }

            try
            {
                Thread.sleep(sleepPeriod);
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private static final byte[] BYTE_300 = new byte[300];

    private Message nextMessage(int msg, Session producerSession) throws JMSException
    {
        BytesMessage send = producerSession.createBytesMessage();
        send.writeBytes(BYTE_300);
        send.setIntProperty("msg", msg);

        return send;
    }

    private class MessageSender implements Runnable
    {
        private final MessageProducer _senderProducer;
        private final Session _senderSession;
        private final int _numMessages;
        private volatile JMSException _exception;
        private CountDownLatch _exceptionThrownLatch = new CountDownLatch(1);
        private long _sleepPeriod;

        public MessageSender(MessageProducer producer, Session producerSession, int numMessages, long sleepPeriod)
        {
            _senderProducer = producer;
            _senderSession = producerSession;
            _numMessages = numMessages;
            _sleepPeriod = sleepPeriod;
        }

        public void run()
        {
            try
            {
                sendMessages(_senderProducer, _senderSession, _numMessages, _sleepPeriod);
            }
            catch (JMSException e)
            {
                _exception = e;
                _exceptionThrownLatch.countDown();
            }
        }

        public Exception awaitSenderException(long timeout) throws InterruptedException
        {
            _exceptionThrownLatch.await(timeout, TimeUnit.MILLISECONDS);
            return _exception;
        }
    }
}
