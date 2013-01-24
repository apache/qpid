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
package org.apache.qpid.server.store;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class StoreOverfullTest extends QpidBrokerTestCase
{
    /** Number of messages to send*/
    public static final int TEST_SIZE = 15;

    /** Message payload*/
    private static final byte[] BYTE_32K = new byte[32*1024];

    private Connection _producerConnection;
    private Connection _consumerConnection;
    private Session _producerSession;
    private Session _consumerSession;
    private MessageProducer _producer;
    private MessageConsumer _consumer;
    private Queue _queue;

    private static final int OVERFULL_SIZE = 400000;
    private static final int UNDERFULL_SIZE = 350000;

    public void setUp() throws Exception
    {
        setVirtualHostConfigurationProperty("virtualhosts.virtualhost.test.store.class", QuotaMessageStore.class.getName());
        setVirtualHostConfigurationProperty("virtualhosts.virtualhost.test.store.overfull-size", String.valueOf(OVERFULL_SIZE));
        setVirtualHostConfigurationProperty("virtualhosts.virtualhost.test.store.underfull-size", String.valueOf(UNDERFULL_SIZE));

        super.setUp();

        _producerConnection = getConnection();
        _producerSession = _producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        _producerConnection.start();

        _consumerConnection = getConnection();
        _consumerSession = _consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    }

    public void tearDown() throws Exception
    {
        try
        {
            _producerConnection.close();
            _consumerConnection.close();
        }
        finally
        {
            super.tearDown();
        }
    }

    /**
     * Test:
     *
     * Send > threshold amount of data : Sender is blocked
     * Remove 90% of data : Sender is unblocked
     *
     */
    public void testCapacityExceededCausesBlock() throws Exception
    {
        AtomicInteger sentMessages = new AtomicInteger(0);
        _queue = getTestQueue();
        ((AMQSession<?,?>) _producerSession).declareAndBind((AMQDestination)_queue);

        _producer = _producerSession.createProducer(_queue);

        MessageSender sender = sendMessagesAsync(_producer, _producerSession, TEST_SIZE, 50L, sentMessages);

        while(!((AMQSession<?,?>)_producerSession).isFlowBlocked())
        {
            Thread.sleep(100l);
        }
        int sentCount = sentMessages.get();
        assertFalse("Did not block before sending all messages", TEST_SIZE == sentCount);

        _consumer = _consumerSession.createConsumer(_queue);
        _consumerConnection.start();

        int mostMessages = (int) (0.9 * sentCount);
        for(int i = 0; i < mostMessages; i++)
        {
            if(_consumer.receive(1000l) == null)
            {
                break;
            }
        }

        long targetTime = System.currentTimeMillis() + 5000l;
        while(sentMessages.get() == sentCount && System.currentTimeMillis() < targetTime)
        {
            Thread.sleep(100l);
        }

        assertFalse("Did not unblock on consuming messages", sentMessages.get() == sentCount);

        for(int i = mostMessages; i < TEST_SIZE; i++)
        {
            if(_consumer.receive(1000l) == null)
            {
                break;
            }
        }

        assertTrue("Not all messages were sent", sentMessages.get() == TEST_SIZE);
        assertNull("Unexpected exception on message sending:" + sender.getException(), sender.getException());
    }

    /**
     *  Two producers on different queues
     */
    public void testCapacityExceededCausesBlockTwoConnections() throws Exception
    {
        AtomicInteger sentMessages = new AtomicInteger(0);
        AtomicInteger sentMessages2 = new AtomicInteger(0);

        _queue = getTestQueue();
        AMQQueue queue2 = new AMQQueue(ExchangeDefaults.DIRECT_EXCHANGE_NAME, getTestQueueName() + "_2");

        ((AMQSession<?,?>) _producerSession).declareAndBind((AMQDestination)_queue);
        ((AMQSession<?,?>) _producerSession).declareAndBind((AMQDestination)queue2);

        _producer = _producerSession.createProducer(_queue);

        Connection secondProducerConnection = getConnection();
        Session secondProducerSession = secondProducerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer secondProducer = secondProducerSession.createProducer(queue2);

        sendMessagesAsync(_producer, _producerSession, TEST_SIZE, 50L, sentMessages);
        sendMessagesAsync(secondProducer, secondProducerSession, TEST_SIZE, 50L, sentMessages2);

        while(!((AMQSession<?,?>)_producerSession).isFlowBlocked())
        {
            Thread.sleep(100l);
        }
        int sentCount = sentMessages.get();
        assertFalse("Did not block before sending all messages", TEST_SIZE == sentCount);


        while(!((AMQSession<?,?>)secondProducerSession).isFlowBlocked())
        {
            Thread.sleep(100l);
        }
        int sentCount2 = sentMessages2.get();
        assertFalse("Did not block before sending all messages", TEST_SIZE == sentCount2);

        _consumer = _consumerSession.createConsumer(_queue);
        MessageConsumer consumer2 = _consumerSession.createConsumer(queue2);
        _consumerConnection.start();

        for(int i = 0; i < 2*TEST_SIZE; i++)
        {
            if(_consumer.receive(1000l) == null
                && consumer2.receive(1000l) == null)
            {
                break;
            }
        }

        assertEquals("Not all messages were sent from the first sender", TEST_SIZE, sentMessages.get());
        assertEquals("Not all messages were sent from the second sender", TEST_SIZE, sentMessages2.get());
    }

    /**
     * New producers are blocked
     */
    public void testCapacityExceededCausesBlockNewConnection() throws Exception
    {
        AtomicInteger sentMessages = new AtomicInteger(0);
        AtomicInteger sentMessages2 = new AtomicInteger(0);

        _queue = getTestQueue();

        ((AMQSession<?,?>) _producerSession).declareAndBind((AMQDestination)_queue);

        _producer = _producerSession.createProducer(_queue);

        Connection secondProducerConnection = getConnection();
        Session secondProducerSession = secondProducerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer secondProducer = secondProducerSession.createProducer(_queue);

        sendMessagesAsync(_producer, _producerSession, TEST_SIZE, 50L, sentMessages);

        while(!((AMQSession<?,?>)_producerSession).isFlowBlocked())
        {
            Thread.sleep(100l);
        }
        int sentCount = sentMessages.get();
        assertFalse("Did not block before sending all messages", TEST_SIZE == sentCount);

        sendMessagesAsync(secondProducer, secondProducerSession, TEST_SIZE, 50L, sentMessages2);

        while(!((AMQSession<?,?>)_producerSession).isFlowBlocked())
        {
            Thread.sleep(100l);
        }
        int sentCount2 = sentMessages2.get();
        assertFalse("Did not block before sending all messages", TEST_SIZE == sentCount2);

        _consumer = _consumerSession.createConsumer(_queue);
        _consumerConnection.start();

        for(int i = 0; i < 2*TEST_SIZE; i++)
        {
            if(_consumer.receive(2000l) == null)
            {
                break;
            }
        }

        assertEquals("Not all messages were sent from the first sender", TEST_SIZE, sentMessages.get());
        assertEquals("Not all messages were sent from the second sender", TEST_SIZE, sentMessages2.get());

    }

    private MessageSender sendMessagesAsync(final MessageProducer producer,
                                            final Session producerSession,
                                            final int numMessages,
                                            long sleepPeriod,
                                            AtomicInteger sentMessages)
    {
        MessageSender sender = new MessageSender(producer, producerSession, numMessages,sleepPeriod, sentMessages);
        new Thread(sender).start();
        return sender;
    }

    private class MessageSender implements Runnable
    {
        private final MessageProducer _senderProducer;
        private final Session _senderSession;
        private final int _numMessages;
        private volatile JMSException _exception;
        private CountDownLatch _exceptionThrownLatch = new CountDownLatch(1);
        private long _sleepPeriod;
        private final AtomicInteger _sentMessages;

        public MessageSender(MessageProducer producer, Session producerSession, int numMessages, long sleepPeriod, AtomicInteger sentMessages)
        {
            _senderProducer = producer;
            _senderSession = producerSession;
            _numMessages = numMessages;
            _sleepPeriod = sleepPeriod;
            _sentMessages = sentMessages;
        }

        public void run()
        {
            try
            {
                sendMessages(_senderProducer, _senderSession, _numMessages, _sleepPeriod, _sentMessages);
            }
            catch (JMSException e)
            {
                _exception = e;
                _exceptionThrownLatch.countDown();
            }
        }

        public Exception getException()
        {
            return _exception;
        }

    }

    private void sendMessages(MessageProducer producer, Session producerSession, int numMessages, long sleepPeriod, AtomicInteger sentMessages)
            throws JMSException
    {

        for (int msg = 0; msg < numMessages; msg++)
        {
            producer.send(nextMessage(msg, producerSession));
            sentMessages.incrementAndGet();

            try
            {
                ((AMQSession<?,?>)producerSession).sync();
            }
            catch (AMQException e)
            {
                e.printStackTrace();
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

    private Message nextMessage(int msg, Session producerSession) throws JMSException
    {
        BytesMessage send = producerSession.createBytesMessage();
        send.writeBytes(BYTE_32K);
        send.setIntProperty("msg", msg);
        return send;
    }

}
