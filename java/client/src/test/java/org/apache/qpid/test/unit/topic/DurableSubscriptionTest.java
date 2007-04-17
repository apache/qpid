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

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TopicSubscriber;

import junit.framework.TestCase;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.url.URLSyntaxException;

public class DurableSubscriptionTest extends TestCase
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
    }

    public void testUnsubscribe() throws AMQException, JMSException, URLSyntaxException
    {
        AMQConnection con = new AMQConnection("vm://:1", "guest", "guest", "test", "test");
        AMQTopic topic = new AMQTopic(con,"MyTopic");
        Session session1 = con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        MessageConsumer consumer1 = session1.createConsumer(topic);
        MessageProducer producer = session1.createProducer(topic);

        Session session2 = con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicSubscriber consumer2 = session2.createDurableSubscriber(topic, "MySubscription");

        con.start();

        producer.send(session1.createTextMessage("A"));

        Message msg;
        msg = consumer1.receive();
        assertEquals("A", ((TextMessage) msg).getText());
        msg = consumer1.receive(1000);
        assertEquals(null, msg);

        msg = consumer2.receive();
        assertEquals("A", ((TextMessage) msg).getText());
        msg = consumer2.receive(1000);
        assertEquals(null, msg);

        session2.unsubscribe("MySubscription");

        producer.send(session1.createTextMessage("B"));

        msg = consumer1.receive();
        assertEquals("B", ((TextMessage) msg).getText());
        msg = consumer1.receive(1000);
        assertEquals(null, msg);

        msg = consumer2.receive(1000);
        assertEquals(null, msg);

        con.close();
    }

    public void testDurability() throws AMQException, JMSException, URLSyntaxException
    {

        AMQConnection con = new AMQConnection("vm://:1", "guest", "guest", "test", "test");
        AMQTopic topic = new AMQTopic(con,"MyTopic");
        Session session1 = con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        MessageConsumer consumer1 = session1.createConsumer(topic);

        Session sessionProd = con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        MessageProducer producer = sessionProd.createProducer(topic);

        Session session2 = con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicSubscriber consumer2 = session2.createDurableSubscriber(topic, "MySubscription");

        con.start();

        producer.send(session1.createTextMessage("A"));

        Message msg;
        msg = consumer1.receive();
        assertEquals("A", ((TextMessage) msg).getText());
        msg = consumer1.receive(100);
        assertEquals(null, msg);

        msg = consumer2.receive();
        assertEquals("A", ((TextMessage) msg).getText());
        msg = consumer2.receive(100);
        assertEquals(null, msg);

        consumer2.close();

        Session session3 = con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        MessageConsumer consumer3 = session3.createDurableSubscriber(topic, "MySubscription");

        producer.send(session1.createTextMessage("B"));

        msg = consumer1.receive(100);
        assertEquals("B", ((TextMessage) msg).getText());
        msg = consumer1.receive(100);
        assertEquals(null, msg);

        msg = consumer3.receive(100);
        assertEquals("B", ((TextMessage) msg).getText());
        msg = consumer3.receive(100);
        assertEquals(null, msg);

        con.close();
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(DurableSubscriptionTest.class);
    }
}
