/*
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
package org.apache.qpid.test.unit.ack;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jms.Session;
import org.apache.qpid.test.utils.QpidTestCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.TextMessage;

import java.util.concurrent.atomic.AtomicInteger;

public class RecoverTest extends QpidTestCase
{
    private static final Logger _logger = LoggerFactory.getLogger(RecoverTest.class);

    private Exception _error;
    private AtomicInteger count;

    protected void setUp() throws Exception
    {
        super.setUp();
        _error = null;
        count = new AtomicInteger();
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        count = null;
    }

    public void testRecoverResendsMsgs() throws Exception
    {
        AMQConnection con = (AMQConnection) getConnection("guest", "guest");

        Session consumerSession = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue =
            new AMQQueue(consumerSession.getDefaultQueueExchangeName(), new AMQShortString("someQ"),
                new AMQShortString("someQ"), false, true);
        MessageConsumer consumer = consumerSession.createConsumer(queue);
        // force synch to ensure the consumer has resulted in a bound queue
        // ((AMQSession) consumerSession).declareExchangeSynch(ExchangeDefaults.DIRECT_EXCHANGE_NAME, ExchangeDefaults.DIRECT_EXCHANGE_CLASS);
        // This is the default now

        AMQConnection con2 = (AMQConnection) getConnection("guest", "guest");
        Session producerSession = con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        MessageProducer producer = producerSession.createProducer(queue);

        _logger.info("Sending four messages");
        producer.send(producerSession.createTextMessage("msg1"));
        producer.send(producerSession.createTextMessage("msg2"));
        producer.send(producerSession.createTextMessage("msg3"));
        producer.send(producerSession.createTextMessage("msg4"));

        con2.close();

        _logger.info("Starting connection");
        con.start();
        TextMessage tm = (TextMessage) consumer.receive();
        tm.acknowledge();
        _logger.info("Received and acknowledged first message");
        consumer.receive();
        consumer.receive();
        consumer.receive();
        _logger.info("Received all four messages. Calling recover with three outstanding messages");
        // no ack for last three messages so when I call recover I expect to get three messages back
        consumerSession.recover();
        tm = (TextMessage) consumer.receive(3000);
        assertEquals("msg2", tm.getText());

        tm = (TextMessage) consumer.receive(3000);
        assertEquals("msg3", tm.getText());

        tm = (TextMessage) consumer.receive(3000);
        assertEquals("msg4", tm.getText());

        _logger.info("Received redelivery of three messages. Acknowledging last message");
        tm.acknowledge();

        _logger.info("Calling acknowledge with no outstanding messages");
        // all acked so no messages to be delivered
        consumerSession.recover();

        tm = (TextMessage) consumer.receiveNoWait();
        assertNull(tm);
        _logger.info("No messages redelivered as is expected");

        con.close();
    }

    public void testRecoverResendsMsgsAckOnEarlier() throws Exception
    {
        AMQConnection con = (AMQConnection) getConnection("guest", "guest");

        Session consumerSession = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue =
            new AMQQueue(consumerSession.getDefaultQueueExchangeName(), new AMQShortString("someQ"),
                new AMQShortString("someQ"), false, true);
        MessageConsumer consumer = consumerSession.createConsumer(queue);
        // force synch to ensure the consumer has resulted in a bound queue
        // ((AMQSession) consumerSession).declareExchangeSynch(ExchangeDefaults.DIRECT_EXCHANGE_NAME, ExchangeDefaults.DIRECT_EXCHANGE_CLASS);
        // This is the default now

        AMQConnection con2 = (AMQConnection) getConnection("guest", "guest");
        Session producerSession = con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        MessageProducer producer = producerSession.createProducer(queue);

        _logger.info("Sending four messages");
        producer.send(producerSession.createTextMessage("msg1"));
        producer.send(producerSession.createTextMessage("msg2"));
        producer.send(producerSession.createTextMessage("msg3"));
        producer.send(producerSession.createTextMessage("msg4"));

        con2.close();

        _logger.info("Starting connection");
        con.start();
        TextMessage tm = (TextMessage) consumer.receive();
        consumer.receive();
        tm.acknowledge();
        _logger.info("Received 2 messages, acknowledge() first message, should acknowledge both");

        consumer.receive();
        consumer.receive();
        _logger.info("Received all four messages. Calling recover with two outstanding messages");
        // no ack for last three messages so when I call recover I expect to get three messages back
        consumerSession.recover();
        TextMessage tm3 = (TextMessage) consumer.receive(3000);
        assertEquals("msg3", tm3.getText());

        TextMessage tm4 = (TextMessage) consumer.receive(3000);
        assertEquals("msg4", tm4.getText());

        _logger.info("Received redelivery of two messages. calling acknolwedgeThis() first of those message");
        ((org.apache.qpid.jms.Message) tm3).acknowledgeThis();

        _logger.info("Calling recover");
        // all acked so no messages to be delivered
        consumerSession.recover();

        tm4 = (TextMessage) consumer.receive(3000);
        assertEquals("msg4", tm4.getText());
        ((org.apache.qpid.jms.Message) tm4).acknowledgeThis();

        _logger.info("Calling recover");
        // all acked so no messages to be delivered
        consumerSession.recover();

        tm = (TextMessage) consumer.receiveNoWait();
        assertNull(tm);
        _logger.info("No messages redelivered as is expected");

        con.close();
    }

    public void testAcknowledgePerConsumer() throws Exception
    {
        AMQConnection con = (AMQConnection) getConnection("guest", "guest");

        Session consumerSession = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue =
            new AMQQueue(consumerSession.getDefaultQueueExchangeName(), new AMQShortString("Q1"), new AMQShortString("Q1"),
                false, true);
        Queue queue2 =
            new AMQQueue(consumerSession.getDefaultQueueExchangeName(), new AMQShortString("Q2"), new AMQShortString("Q2"),
                false, true);
        MessageConsumer consumer = consumerSession.createConsumer(queue);
        MessageConsumer consumer2 = consumerSession.createConsumer(queue2);

        AMQConnection con2 = (AMQConnection) getConnection("guest", "guest");
        Session producerSession = con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        MessageProducer producer = producerSession.createProducer(queue);
        MessageProducer producer2 = producerSession.createProducer(queue2);

        producer.send(producerSession.createTextMessage("msg1"));
        producer2.send(producerSession.createTextMessage("msg2"));

        con2.close();

        _logger.info("Starting connection");
        con.start();

        TextMessage tm2 = (TextMessage) consumer2.receive(2000);
        assertNotNull(tm2);
        assertEquals("msg2", tm2.getText());

        tm2.acknowledge();

        consumerSession.recover();

        TextMessage tm1 = (TextMessage) consumer.receive(2000);
        assertNotNull(tm1);
        assertEquals("msg1", tm1.getText());

        con.close();

    }

    public void testRecoverInAutoAckListener() throws Exception
    {
        AMQConnection con = (AMQConnection) getConnection("guest", "guest");

        final Session consumerSession = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue =
            new AMQQueue(consumerSession.getDefaultQueueExchangeName(), new AMQShortString("Q3"), new AMQShortString("Q3"),
                false, true);
        MessageConsumer consumer = consumerSession.createConsumer(queue);
        MessageProducer producer = consumerSession.createProducer(queue);
        producer.send(consumerSession.createTextMessage("hello"));

        final Object lock = new Object();

        consumer.setMessageListener(new MessageListener()
            {

                public void onMessage(Message message)
                {
                    try
                    {
                        count.incrementAndGet();
                        if (count.get() == 1)
                        {
                            if (message.getJMSRedelivered())
                            {
                                setError(
                                    new Exception("Message marked as redilvered on what should be first delivery attempt"));
                            }

                            consumerSession.recover();
                        }
                        else if (count.get() == 2)
                        {
                            if (!message.getJMSRedelivered())
                            {
                                setError(
                                    new Exception(
                                        "Message not marked as redilvered on what should be second delivery attempt"));
                            }
                        }
                        else
                        {
                            System.err.println(message);
                            fail("Message delivered too many times!: " + count);
                        }
                    }
                    catch (JMSException e)
                    {
                        _logger.error("Error recovering session: " + e, e);
                        setError(e);
                    }

                    synchronized (lock)
                    {
                        lock.notify();
                    }
                }
            });

        con.start();

        long waitTime = 30000L;
        long waitUntilTime = System.currentTimeMillis() + waitTime;

        synchronized (lock)
        {
            while ((count.get() <= 1) && (waitTime > 0))
            {
                lock.wait(waitTime);
                if (count.get() <= 1)
                {
                    waitTime = waitUntilTime - System.currentTimeMillis();
                }
            }
        }

        Thread.sleep(1000);

        if (count.get() != 2)
        {
            System.err.println("Count != 2 : " + count);
        }

        assertTrue(count.get() == 2);

        con.close();

        if (_error != null)
        {
            throw _error;
        }
    }

    private void setError(Exception e)
    {
        _error = e;
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(RecoverTest.class);
    }
}
