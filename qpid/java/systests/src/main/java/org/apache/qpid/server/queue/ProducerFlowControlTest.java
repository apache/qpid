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

import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;

import javax.jms.*;
import javax.naming.NamingException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ProducerFlowControlTest extends QpidTestCase
{
    private static final int TIMEOUT = 1500;


    private static final Logger _logger = Logger.getLogger(ProducerFlowControlTest.class);

    protected final String QUEUE = "ProducerFlowControl";

    private static final int MSG_COUNT = 50;

    private Connection producerConnection;
    private MessageProducer producer;
    private Session producerSession;
    private Queue queue;
    private Connection consumerConnection;
    private Session consumerSession;


    private MessageConsumer consumer;
    private final AtomicInteger _sentMessages = new AtomicInteger();

    protected void setUp() throws Exception
    {
        super.setUp();

        producerConnection = getConnection();
        producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        producerConnection.start();

        consumerConnection = getConnection();
        consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    }

    protected void tearDown() throws Exception
    {
        producerConnection.close();
        consumerConnection.close();
        super.tearDown();
    }

    public void testCapacityExceededCausesBlock()
            throws JMSException, NamingException, AMQException, InterruptedException
    {
        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("x-qpid-capacity",1000);
        arguments.put("x-qpid-flow-resume-capacity",800);
        ((AMQSession) producerSession).createQueue(new AMQShortString(QUEUE), true, false, false, arguments);
        queue = new AMQQueue("amq.direct",QUEUE);
        ((AMQSession) producerSession).declareAndBind((AMQDestination)queue);
        producer = producerSession.createProducer(queue);

        _sentMessages.set(0);


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


    public void testFlowControlOnCapacityResumeEqual()
            throws JMSException, NamingException, AMQException, InterruptedException
    {
        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("x-qpid-capacity",1000);
        arguments.put("x-qpid-flow-resume-capacity",1000);
        ((AMQSession) producerSession).createQueue(new AMQShortString(QUEUE), true, false, false, arguments);
        queue = new AMQQueue("amq.direct",QUEUE);
        ((AMQSession) producerSession).declareAndBind((AMQDestination)queue);
        producer = producerSession.createProducer(queue);

        _sentMessages.set(0);


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


    public void testFlowControlSoak()
            throws Exception, NamingException, AMQException, InterruptedException
    {
        _sentMessages.set(0);
        final int numProducers = 10;
        final int numMessages = 100;

        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("x-qpid-capacity",6000);
        arguments.put("x-qpid-flow-resume-capacity",3000);

        ((AMQSession) consumerSession).createQueue(new AMQShortString(QUEUE), false, false, false, arguments);

        queue = new AMQQueue("amq.direct",QUEUE);
        ((AMQSession) consumerSession).declareAndBind((AMQDestination)queue);
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



    public void testSendTimeout()
            throws JMSException, NamingException, AMQException, InterruptedException
    {
        long origTimeoutValue = Long.getLong("qpid.flow_control_wait_failure",AMQSession.DEFAULT_FLOW_CONTROL_WAIT_FAILURE);
        System.setProperty("qpid.flow_control_wait_failure","3000");
        Session session = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);


        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("x-qpid-capacity",1000);
        arguments.put("x-qpid-flow-resume-capacity",800);
        ((AMQSession) session).createQueue(new AMQShortString(QUEUE), true, false, false, arguments);
        queue = new AMQQueue("amq.direct",QUEUE);
        ((AMQSession) session).declareAndBind((AMQDestination)queue);
        producer = session.createProducer(queue);

        _sentMessages.set(0);


        // try to send 5 messages (should block after 4)
        MessageSender sender = sendMessagesAsync(producer, producerSession, 5, 50L);

        Thread.sleep(10000);

        Exception e = sender.getException();

        assertNotNull("No timeout exception on sending", e);

        System.setProperty("qpid.flow_control_wait_failure",String.valueOf(origTimeoutValue));
        


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
                Thread.sleep(sleepPeriod);
            }
            catch (InterruptedException e)
            {
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
        private final MessageProducer _producer;
        private final Session _producerSession;
        private final int _numMessages;



        private JMSException _exception;
        private long _sleepPeriod;

        public MessageSender(MessageProducer producer, Session producerSession, int numMessages, long sleepPeriod)
        {
            _producer = producer;
            _producerSession = producerSession;
            _numMessages = numMessages;
            _sleepPeriod = sleepPeriod;
        }

        public void run()
        {
            try
            {
                sendMessages(_producer, _producerSession, _numMessages, _sleepPeriod);
            }
            catch (JMSException e)
            {
                _exception = e;
            }
        }

        public JMSException getException()
        {
            return _exception;
        }
    }
}