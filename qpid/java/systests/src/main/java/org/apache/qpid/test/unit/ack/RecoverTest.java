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
import org.apache.qpid.test.utils.FailoverBaseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.TextMessage;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RecoverTest extends FailoverBaseCase
{
    static final Logger _logger = LoggerFactory.getLogger(RecoverTest.class);

    private Exception _error;
    private AtomicInteger count;

    protected AMQConnection _connection;
    protected Session _consumerSession;
    protected MessageConsumer _consumer;
    static final int SENT_COUNT = 4;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _error = null;
        count = new AtomicInteger();
    }

    protected void initTest() throws Exception
    {
        _connection = (AMQConnection) getConnection("guest", "guest");

        _consumerSession = _connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue = _consumerSession.createQueue(getTestQueueName());

        _consumer = _consumerSession.createConsumer(queue);

        _logger.info("Sending four messages");
        sendMessage(_connection.createSession(false, Session.AUTO_ACKNOWLEDGE), queue, SENT_COUNT);
        _logger.info("Starting connection");
        _connection.start();
    }

    protected Message validateNextMessages(int nextCount, int startIndex) throws JMSException
    {
        Message message = null;
        for (int index = 0; index < nextCount; index++)
        {
            message = _consumer.receive(3000);
            assertEquals(startIndex + index, message.getIntProperty(INDEX));
        }
        return message;
    }

    protected void validateRemainingMessages(int remaining) throws JMSException
    {
        int index = SENT_COUNT - remaining;

        Message message = null;
        while (index != SENT_COUNT)
        {
            message =  _consumer.receive(3000);
            assertNotNull(message);
            assertEquals(index++, message.getIntProperty(INDEX));
        }

        if (message != null)
        {
            _logger.info("Received redelivery of three messages. Acknowledging last message");
            message.acknowledge();
        }

        _logger.info("Calling acknowledge with no outstanding messages");
        // all acked so no messages to be delivered
        _consumerSession.recover();

        message = _consumer.receiveNoWait();
        assertNull(message);
        _logger.info("No messages redelivered as is expected");
    }

    public void testRecoverResendsMsgs() throws Exception
    {
        initTest();

        Message message = validateNextMessages(1, 0);
        message.acknowledge();
        _logger.info("Received and acknowledged first message");

        _consumer.receive();
        _consumer.receive();
        _consumer.receive();
        _logger.info("Received all four messages. Calling recover with three outstanding messages");
        // no ack for last three messages so when I call recover I expect to get three messages back

        _consumerSession.recover();

        validateRemainingMessages(3);
    }

    public void testRecoverResendsMsgsAckOnEarlier() throws Exception
    {
        initTest();

        Message message = validateNextMessages(2, 0);
        message.acknowledge();
        _logger.info("Received 2 messages, acknowledge() first message, should acknowledge both");

        _consumer.receive();
        _consumer.receive();
        _logger.info("Received all four messages. Calling recover with two outstanding messages");
        // no ack for last three messages so when I call recover I expect to get three messages back
        _consumerSession.recover();

        Message message2 = _consumer.receive(3000);
        assertNotNull(message2);
        assertEquals(2, message2.getIntProperty(INDEX));

        Message message3 = _consumer.receive(3000);
        assertNotNull(message3);
        assertEquals(3, message3.getIntProperty(INDEX));

        _logger.info("Received redelivery of two messages. calling acknolwedgeThis() first of those message");
        ((org.apache.qpid.jms.Message) message2).acknowledgeThis();

        _logger.info("Calling recover");
        // all acked so no messages to be delivered
        _consumerSession.recover();

        message3 = _consumer.receive(3000);
        assertNotNull(message3);
        assertEquals(3, message3.getIntProperty(INDEX));
        ((org.apache.qpid.jms.Message) message3).acknowledgeThis();

        // all acked so no messages to be delivered
        validateRemainingMessages(0);
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
                            setError(new Exception("Message marked as redelivered on what should be first delivery attempt"));
                        }

                        consumerSession.recover();
                    }
                    else if (count.get() == 2)
                    {
                        if (!message.getJMSRedelivered())
                        {
                            setError(
                                    new Exception("Message not marked as redelivered on what should be second delivery attempt"));
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

        if (_error != null)
        {
            throw _error;
        }

        assertEquals("Message not received the correct number of times.",
                     2, count.get());
    }

    private void setError(Exception e)
    {
        _error = e;
    }
    
    private void sendMessages(javax.jms.Session session,Destination dest,int count) throws Exception
    {
        MessageProducer prod = session.createProducer(dest);
        for (int i=0; i<count; i++)
        {
             prod.send(session.createTextMessage("Msg" + i));
        }
        prod.close();
    }
    
    /**
     * Goal : Check if ordering is preserved when doing recovery under reasonable circumstances.
     *        Refer QPID-2471 for more details. 
     * Test strategy :
     * Send 8 messages to a topic.
     * The consumer will call recover until it sees a message 5 times,
     * at which point it will ack that message.
     * It will continue the above until it acks all the messages.
     * While doing so it will verify that the messages are not 
     * delivered out of order.
     */
    public void testOderingWithSyncConsumer() throws Exception
    {
        Connection con = (Connection) getConnection("guest", "guest");
        javax.jms.Session session = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Destination topic = session.createTopic("myTopic");
        MessageConsumer cons = session.createConsumer(topic);
        
        sendMessages(session,topic,8);
        con.start();
        
        int messageSeen = 0;
        int expectedMsg = 0;

        long startTime = System.currentTimeMillis();
        
        while(expectedMsg < 8)
        {
            // Based on historical data, on average the test takes about 6 secs to complete.
            if (System.currentTimeMillis() - startTime > 8000)
            {
                fail("Test did not complete on time. Received " + 
                     expectedMsg + " msgs so far. Please check the logs");
            }
            
            Message message = cons.receive(2000);            
            String text=((TextMessage) message).getText();            
            
            assertEquals("Received Message Out Of Order","Msg"+expectedMsg,text);
                        
            //don't ack the message until we receive it 5 times
            if( messageSeen < 5 ) 
            {
                _logger.debug("Ignoring message " + text + " and calling recover"); 
                session.recover();
                messageSeen++;
            }
            else
            {
                messageSeen = 0;
                expectedMsg++;
                message.acknowledge();
                _logger.debug("Acknowledging message " + text);    
            }
        }        
    }
    
    /**
     * Goal : Same as testOderingWithSyncConsumer
     * Test strategy :
     * Same as testOderingWithSyncConsumer but using a 
     * Message Listener instead of a sync receive().
     */
    public void testOderingWithAsyncConsumer() throws Exception
    {
        Connection con = (Connection) getConnection("guest", "guest");
        final javax.jms.Session session = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Destination topic = session.createTopic("myTopic");
        MessageConsumer cons = session.createConsumer(topic);
        
        sendMessages(session,topic,8);
        con.start();
        
        final Object lock = new Object();
        final AtomicBoolean pass = new AtomicBoolean(false); //used as work around for 'final'
        cons.setMessageListener(new MessageListener()
        {               
            int messageSeen = 0;
            int expectedMsg = 0;
            
            public void onMessage(Message message)
            {
                try
                {
                    String text = ((TextMessage) message).getText();
                    assertEquals("Received Message Out Of Order","Msg"+expectedMsg,text);
                                
                    //don't ack the message until we receive it 5 times
                    if( messageSeen < 5 ) 
                    {
                        _logger.debug("Ignoring message " + text + " and calling recover"); 
                        session.recover();
                        messageSeen++;
                    }
                    else
                    {
                        messageSeen = 0;
                        expectedMsg++;
                        message.acknowledge();
                        _logger.debug("Acknowledging message " + text);
                        if (expectedMsg == 8)
                        {
                            pass.set(true);
                            synchronized (lock) 
                            {
                                lock.notifyAll();
                            }      
                        }
                    }                    
                } 
                catch (JMSException e)
                {
                    fail("Exception : " + e.getMessage());
                    synchronized (lock) 
                    {
                        lock.notifyAll();
                    }  
                }
            }
        });
        
        synchronized(lock)
        {
            // Based on historical data, on average the test takes about 6 secs to complete.
            lock.wait(8000);
        }
        
        if (!pass.get())
        {
            fail("Test did not complete on time. Please check the logs");
        }
    }
}
