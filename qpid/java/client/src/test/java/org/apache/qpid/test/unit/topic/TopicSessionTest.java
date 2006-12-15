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

import junit.framework.TestCase;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.transport.TransportConnection;

import javax.jms.*;


/**
 * @author Apache Software Foundation
 */
public class TopicSessionTest extends TestCase
{
    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        TransportConnection.killAllVMBrokers();
        //Thread.sleep(2000);
    }


    public void testTopicSubscriptionUnsubscription() throws Exception
    {
        AMQTopic topic = new AMQTopic("MyTopic");
        AMQConnection con = new AMQConnection("vm://:1?retries='0'", "guest", "guest", "test", "/test");
        TopicSession session1 = con.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicSubscriber sub = session1.createDurableSubscriber(topic,"subscription0");
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
        catch(InvalidDestinationException e)
        {
            ; // PASS
        }
        catch(Exception e)
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

    private void subscriptionNameReuseForDifferentTopic(boolean shutdown) throws Exception
    {
        AMQTopic topic = new AMQTopic("MyTopic1" + String.valueOf(shutdown));
        AMQTopic topic2 = new AMQTopic("MyOtherTopic1" + String.valueOf(shutdown));
        AMQConnection con = new AMQConnection("vm://:1?retries='0'", "guest", "guest", "test", "/test");
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
            con = new AMQConnection("vm://:1?retries='0'", "guest", "guest", "test", "/test");
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
        AMQTopic topic = new AMQTopic("MyTopic3");
        AMQConnection con1 = new AMQConnection("vm://:1?retries='0'", "guest", "guest", "test", "/test");
        TopicSession session1 = con1.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicPublisher publisher = session1.createPublisher(topic);

        AMQConnection con2 = new AMQConnection("vm://:1?retries='0'", "guest", "guest", "test2", "/test");
        TopicSession session2 = con2.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicSubscriber sub = session2.createDurableSubscriber(topic, "subscription0");

        con2.start();

        publisher.publish(session1.createTextMessage("Hello"));
        TextMessage tm = (TextMessage) sub.receive(2000);
        assertNotNull(tm);
        con2.close();
        publisher.publish(session1.createTextMessage("Hello2"));
        con2 = new AMQConnection("vm://:1?retries='0'", "guest", "guest", "test2", "/test");
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
        AMQTopic topic = new AMQTopic("MyTopic4");
        AMQConnection con = new AMQConnection("vm://:1?retries='0'", "guest", "guest", "test", "/test");
        TopicSession session1 = con.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicPublisher publisher = session1.createPublisher(topic);
        MessageConsumer consumer1 = session1.createConsumer(topic);
        con.start();
        TextMessage tm = session1.createTextMessage("Hello");
        publisher.publish(tm);
        tm = (TextMessage) consumer1.receive(2000);
        assertNotNull(tm);
        String msgText = tm.getText();
        assertEquals("Hello", msgText);
        tm = session1.createTextMessage();
        msgText = tm.getText();
        assertNull(msgText);
        publisher.publish(tm);
        tm = (TextMessage) consumer1.receive(2000);
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

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(TopicSessionTest.class);
    }
}
