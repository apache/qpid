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
package org.apache.qpid.test.unit.topic;

import javax.jms.*;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.Message;
import javax.jms.MessageProducer;

import junit.framework.TestCase;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.BasicMessageProducer;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQUndefinedDestination;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jms.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;


/** @author Apache Software Foundation */
public class TopicSessionTest extends TestCase
{
    private static final String BROKER = "vm://:1";
    private static final int THREADS = 20;
    private static final int MESSAGE_COUNT = 10000;
    private static final int MESSAGE_SIZE = 128;

    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        TransportConnection.killAllVMBrokers();
    }


    public void testTopicSubscriptionUnsubscription() throws Exception
    {

        AMQConnection con = new AMQConnection(BROKER+"?retries='0'", "guest", "guest", "test", "test");
        AMQTopic topic = new AMQTopic(con.getDefaultTopicExchangeName(), "MyTopic");
        TopicSession session1 = con.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicSubscriber sub = session1.createDurableSubscriber(topic, "subscription0");
        TopicPublisher publisher = session1.createPublisher(topic);

        con.start();

        TextMessage tm = session1.createTextMessage("Hello");
        publisher.publish(tm);

        tm = (TextMessage) sub.receive(2000);
        assertNotNull(tm);

        session1.unsubscribe("subscription0");

        try
        {
            session1.unsubscribe("not a subscription");
            fail("expected InvalidDestinationException when unsubscribing from unknown subscription");
        }
        catch (InvalidDestinationException e)
        {
            ; // PASS
        }
        catch (Exception e)
        {
            fail("expected InvalidDestinationException when unsubscribing from unknown subscription, got: " + e);
        }

        con.close();
    }

    public void testSubscriptionNameReuseForDifferentTopicSingleConnection() throws Exception
    {
        subscriptionNameReuseForDifferentTopic(false);
    }

    public void testSubscriptionNameReuseForDifferentTopicTwoConnections() throws Exception
    {
        subscriptionNameReuseForDifferentTopic(true);
    }

    public void notestSilly() throws Exception
    {


                final ExceptionListener listener = new ExceptionListener()
                {
                    public void onException(JMSException jmsException)
                    {
                        //To change body of implemented methods use File | Settings | File Templates.
                    }
                };


        Thread[] threads = new Thread[100];

        for(int j = 0; j < 20; j++)
        {
        threads[j] = new Thread(new Runnable() {
            public void run()
            {
                try
                {
                AMQConnection con = new AMQConnection("tcp://127.0.0.1:5672?retries='0'", "guest", "guest", "test", "test");
                AMQTopic topic = new AMQTopic(con, "MyTopic1" + UUID.randomUUID());


                TopicSession session1 = con.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);

                con.setExceptionListener(listener);

                TopicPublisher publisher = session1.createPublisher(topic);

                con.start();

                while(true)
                {
                    publisher.publish(session1.createTextMessage("hello"));
                    Thread.sleep(20);
                }
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
            }
        });
            threads[j].run();
        }

        threads[0].join();

    }


    private void subscriptionNameReuseForDifferentTopic(boolean shutdown) throws Exception
    {
        AMQConnection con = new AMQConnection("vm://:1?retries='0'", "guest", "guest", "test", "test");
        AMQTopic topic = new AMQTopic(con, "MyTopic1" + String.valueOf(shutdown));
        AMQTopic topic2 = new AMQTopic(con, "MyOtherTopic1" + String.valueOf(shutdown));

        TopicSession session1 = con.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicSubscriber sub = session1.createDurableSubscriber(topic, "subscription0");
        TopicPublisher publisher = session1.createPublisher(null);

        con.start();

        publisher.publish(topic, session1.createTextMessage("hello"));
        TextMessage m = (TextMessage) sub.receive(2000);
        assertNotNull(m);

        if (shutdown)
        {
            session1.close();
            con.close();
            con = new AMQConnection("vm://:1?retries='0'", "guest", "guest", "test", "test");
            con.start();
            session1 = con.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
            publisher = session1.createPublisher(null);
        }
        TopicSubscriber sub2 = session1.createDurableSubscriber(topic2, "subscription0");
        publisher.publish(topic, session1.createTextMessage("hello"));
        if (!shutdown)
        {
            m = (TextMessage) sub.receive(2000);
            assertNull(m);
        }
        publisher.publish(topic2, session1.createTextMessage("goodbye"));
        m = (TextMessage) sub2.receive(2000);
        assertNotNull(m);
        assertEquals("goodbye", m.getText());
        con.close();
    }

    public void testUnsubscriptionAfterConnectionClose() throws Exception
    {
        AMQConnection con1 = new AMQConnection("vm://:1?retries='0'", "guest", "guest", "test", "test");
        AMQTopic topic = new AMQTopic(con1, "MyTopic3");

        TopicSession session1 = con1.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicPublisher publisher = session1.createPublisher(topic);

        AMQConnection con2 = new AMQConnection("vm://:1?retries='0'", "guest", "guest", "test2", "test");
        TopicSession session2 = con2.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicSubscriber sub = session2.createDurableSubscriber(topic, "subscription0");

        con2.start();

        publisher.publish(session1.createTextMessage("Hello"));
        TextMessage tm = (TextMessage) sub.receive(2000);
        assertNotNull(tm);
        con2.close();
        publisher.publish(session1.createTextMessage("Hello2"));
        con2 = new AMQConnection("vm://:1?retries='0'", "guest", "guest", "test2", "test");
        session2 = con2.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
        sub = session2.createDurableSubscriber(topic, "subscription0");
        con2.start();
        tm = (TextMessage) sub.receive(2000);
        assertNotNull(tm);
        assertEquals("Hello2", tm.getText());
        con1.close();
        con2.close();
    }

    public void testTextMessageCreation() throws Exception
    {

        AMQConnection con = new AMQConnection("vm://:1?retries='0'", "guest", "guest", "test", "test");
        AMQTopic topic = new AMQTopic(con, "MyTopic4");
        TopicSession session1 = con.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicPublisher publisher = session1.createPublisher(topic);
        MessageConsumer consumer1 = session1.createConsumer(topic);
        con.start();
        TextMessage tm = session1.createTextMessage("Hello");
        publisher.publish(tm);
        tm = (TextMessage) consumer1.receive(200000L);
        assertNotNull(tm);
        String msgText = tm.getText();
        assertEquals("Hello", msgText);
        tm = session1.createTextMessage();
        msgText = tm.getText();
        assertNull(msgText);
        publisher.publish(tm);
        tm = (TextMessage) consumer1.receive(20000000L);
        assertNotNull(tm);
        msgText = tm.getText();
        assertNull(msgText);
        tm.clearBody();
        tm.setText("Now we are not null");
        publisher.publish(tm);
        tm = (TextMessage) consumer1.receive(2000);
        assertNotNull(tm);
        msgText = tm.getText();
        assertEquals("Now we are not null", msgText);

        tm = session1.createTextMessage("");
        msgText = tm.getText();
        assertEquals("Empty string not returned", "", msgText);
        publisher.publish(tm);
        tm = (TextMessage) consumer1.receive(2000);
        assertNotNull(tm);
        assertEquals("Empty string not returned", "", msgText);
        con.close();
    }

    public void testSendingSameMessage() throws Exception
    {
        AMQConnection conn = new AMQConnection("vm://:1?retries='0'", "guest", "guest", "test", "test");
        TopicSession session = conn.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        TemporaryTopic topic = session.createTemporaryTopic();
        assertNotNull(topic);
        TopicPublisher producer = session.createPublisher(topic);
        MessageConsumer consumer = session.createConsumer(topic);
        conn.start();
        TextMessage sentMessage = session.createTextMessage("Test Message");
        producer.send(sentMessage);
        TextMessage receivedMessage = (TextMessage) consumer.receive(2000);
        assertNotNull(receivedMessage);
        assertEquals(sentMessage.getText(), receivedMessage.getText());
        producer.send(sentMessage);
        receivedMessage = (TextMessage) consumer.receive(2000);
        assertNotNull(receivedMessage);
        assertEquals(sentMessage.getText(), receivedMessage.getText());

        conn.close();

    }

    public void testTemporaryTopic() throws Exception
    {
        AMQConnection conn = new AMQConnection("vm://:1?retries='0'", "guest", "guest", "test", "test");
        TopicSession session = conn.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        TemporaryTopic topic = session.createTemporaryTopic();
        assertNotNull(topic);
        TopicPublisher producer = session.createPublisher(topic);
        MessageConsumer consumer = session.createConsumer(topic);
        conn.start();
        producer.send(session.createTextMessage("hello"));
        TextMessage tm = (TextMessage) consumer.receive(2000);
        assertNotNull(tm);
        assertEquals("hello", tm.getText());

        try
        {
            topic.delete();
            fail("Expected JMSException : should not be able to delete while there are active consumers");
        }
        catch (JMSException je)
        {
            ; //pass
        }

        consumer.close();

        try
        {
            topic.delete();
        }
        catch (JMSException je)
        {
            fail("Unexpected Exception: " + je.getMessage());
        }

        TopicSession session2 = conn.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        try
        {
            session2.createConsumer(topic);
            fail("Expected a JMSException when subscribing to a temporary topic created on adifferent session");
        }
        catch (JMSException je)
        {
            ; // pass
        }


        conn.close();
    }


    public void testNoLocal() throws Exception
    {

        AMQConnection con = new AMQConnection(BROKER + "?retries='0'", "guest", "guest", "test", "test");

        AMQTopic topic = new AMQTopic(con, "testNoLocal");

        TopicSession session1 = con.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicSubscriber noLocal = session1.createDurableSubscriber(topic, "noLocal", "", true);
        TopicSubscriber select = session1.createDurableSubscriber(topic, "select", "Selector = 'select'", false);
        TopicSubscriber normal = session1.createDurableSubscriber(topic, "normal");

        TopicPublisher publisher = session1.createPublisher(topic);

        con.start();
        TextMessage m;
        TextMessage message;

        //send message to all consumers
        publisher.publish(session1.createTextMessage("hello-new2"));

        //test normal subscriber gets message
        m = (TextMessage) normal.receive(5000);
        assertNotNull(m);

        //test selector subscriber doesn't message
        m = (TextMessage) select.receive(2000);
        assertNull(m);

        //test nolocal subscriber doesn't message
        m = (TextMessage) noLocal.receive(2000);
        if (m != null)
        {
            System.out.println("Message:" + m.getText());
        }
        assertNull(m);

        //send message to all consumers
         message = session1.createTextMessage("hello2");
        message.setStringProperty("Selector", "select");

        publisher.publish(message);

        //test normal subscriber gets message
        m = (TextMessage) normal.receive(5000);
        assertNotNull(m);

        //test selector subscriber does get message
        m = (TextMessage) select.receive(2000);
        assertNotNull(m);

        //test nolocal subscriber doesn't message
        m = (TextMessage) noLocal.receive(1000);
        assertNull(m);

        AMQConnection con2 = new AMQConnection(BROKER + "?retries='0'", "guest", "guest", "test2", "test");
        TopicSession session2 = con2.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicPublisher publisher2 = session2.createPublisher(topic);


        message = session2.createTextMessage("hello2");
        message.setStringProperty("Selector", "select");

        publisher2.publish(message);

        //test normal subscriber gets message
        m = (TextMessage) normal.receive(2000);
        assertNotNull(m);

        //test selector subscriber does get message
        m = (TextMessage) select.receive(2000);
        assertNotNull(m);

        //test nolocal subscriber does message
        m = (TextMessage) noLocal.receive(2000);
        assertNotNull(m);


        con.close();
        con2.close();
    }


    public void noTestPublishToManyConsumers() throws Exception
    {


        final ExceptionListener exceptionListener = new ExceptionListener()
        {
            public void onException(JMSException jmsException)
            {
                jmsException.printStackTrace();
            }
        };



        SubscribingThread[] threads = new SubscribingThread[100];

        final String topicName = "MyTopic1" + UUID.randomUUID();
        for(int j = 0; j < 21; j++)
        {
            final int threadId = j;
            threads[threadId] = new SubscribingThread(threadId, topicName, exceptionListener);
            threads[j].start();
            Thread.sleep(100);
        }


        threads[1].join();

        int totalMessages = 0;

        for(int j = 1; j < 21; j++)
        {

            System.err.println("Thread " + j + ": " + threads[j].msgId);
            totalMessages += threads[j].msgId;
        }

        System.err.println("****** Total: " + totalMessages);


    }




    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(TopicSessionTest.class);
    }

    private static class SubscribingThread extends Thread
    {
        private final int _threadId;
        private final String _topicName;
        private final ExceptionListener _exceptionListener;
        int msgId = 0;

        public SubscribingThread(final int threadId, final String topicName, final ExceptionListener exceptionListener)
        {
            _threadId = threadId;
            _topicName = topicName;
            _exceptionListener = exceptionListener;
        }

        public void run()
        {
            try
            {
                System.err.println("Thread: " + _threadId);


                if(_threadId >0)
                {

                    AMQConnection con2 = new AMQConnection("tcp://127.0.0.1:5672?retries='0'", "guest", "guest", "test", "test");
                    //AMQConnection con2 = new AMQConnection(BROKER + "?retries='0'", "guest", "guest", "test", "test");
                    AMQTopic topic2 = new AMQTopic(con2, _topicName);
                    TopicSession session2 = con2.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
                    TopicSubscriber sub = session2.createSubscriber(topic2);
                    con2.setExceptionListener(_exceptionListener);



                    final MessageListener messageListener = new MessageListener()
                    {

                        public void onMessage(Message message)
                        {
                            try
                            {
                                msgId = message.getIntProperty("MessageId");
                                if(msgId % 1000 == 0)
                                {
                                    System.err.println("Thread: " + _threadId + ": " + msgId + "messages");
                                }
                            }
                            catch (JMSException e)
                            {
                                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                            }
                        }
                    };


                    sub.setMessageListener(messageListener);
                    con2.start();

                    Thread.sleep(125000);


//                    Thread.sleep(1200000);
                }
                else
                {
                    int messageId = 0;

                    AMQConnection con = new AMQConnection("tcp://127.0.0.1:5672?retries='0'", "guest", "guest", "test", "test");
                    //AMQConnection con = new AMQConnection(BROKER + "?retries='0'", "guest", "guest", "test", "test");


                    AMQTopic topic = new AMQTopic(con, _topicName);


                    TopicSession session1 = con.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);

                    con.setExceptionListener(_exceptionListener);

                    TopicPublisher publisher = session1.createPublisher(topic);
                    publisher.setDisableMessageID(true);
                    publisher.setDisableMessageTimestamp(true);
                    con.start();

                    Thread.sleep(5000);

                    publisher.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

                    while(messageId <= 240000)
                    //while(messageId <= 10000)
                    {
                        final TextMessage textMessage = session1.createTextMessage("hello");
                        textMessage.setIntProperty("MessageId", messageId++);


                        publisher.publish(textMessage);

                    }
                }

            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}
