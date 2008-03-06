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

import org.apache.qpid.AMQException;
import org.apache.qpid.testutil.QpidTestCase;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.url.URLSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TopicSubscriber;

public class DurableSubscriptionTest extends QpidTestCase
{
    private static final Logger _logger = LoggerFactory.getLogger(DurableSubscriptionTest.class);

    protected void setUp() throws Exception
    {
        super.setUp();
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
    }

    public void testUnsubscribe() throws Exception
    {
        AMQConnection con = (AMQConnection) getConnection("guest", "guest");
        AMQTopic topic = new AMQTopic(con, "MyDurableSubscriptionTestTopic");
        _logger.info("Create Session 1");
        Session session1 = con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        _logger.info("Create Consumer on Session 1");
        MessageConsumer consumer1 = session1.createConsumer(topic);
        _logger.info("Create Producer on Session 1");
        MessageProducer producer = session1.createProducer(topic);

        _logger.info("Create Session 2");
        Session session2 = con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        _logger.info("Create Durable Subscriber on Session 2");
        TopicSubscriber consumer2 = session2.createDurableSubscriber(topic, "MySubscription");

        _logger.info("Starting connection");
        con.start();

        _logger.info("Producer sending message A");
        producer.send(session1.createTextMessage("A"));

        Message msg;
        _logger.info("Receive message on consumer 1:expecting A");
        msg = consumer1.receive();
        assertEquals("A", ((TextMessage) msg).getText());
        _logger.info("Receive message on consumer 1 :expecting null");
        msg = consumer1.receive(1000);
        assertEquals(null, msg);

        _logger.info("Receive message on consumer 1:expecting A");
        msg = consumer2.receive();
        assertEquals("A", ((TextMessage) msg).getText());
        msg = consumer2.receive(1000);
        _logger.info("Receive message on consumer 1 :expecting null");
        assertEquals(null, msg);

        _logger.info("Unsubscribe session2/consumer2");
        session2.unsubscribe("MySubscription");

        _logger.info("Producer sending message B");
        producer.send(session1.createTextMessage("B"));

        _logger.info("Receive message on consumer 1 :expecting B");
        msg = consumer1.receive();
        assertEquals("B", ((TextMessage) msg).getText());
        _logger.info("Receive message on consumer 1 :expecting null");
        msg = consumer1.receive(1000);
        assertEquals(null, msg);

        _logger.info("Receive message on consumer 2 :expecting null");
        msg = consumer2.receive(1000);
        assertEquals(null, msg);

        _logger.info("Close connection");
        con.close();
    }

    public void testDurability() throws Exception
    {

        AMQConnection con = (AMQConnection) getConnection("guest", "guest");
        AMQTopic topic = new AMQTopic(con, "MyDurableSubscriptionTestTopic");
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

        _logger.info("Receive message on consumer 1 :expecting B");
        msg = consumer1.receive(100);
        assertEquals("B", ((TextMessage) msg).getText());
        _logger.info("Receive message on consumer 1 :expecting null");
        msg = consumer1.receive(100);
        assertEquals(null, msg);

        _logger.info("Receive message on consumer 3 :expecting B");
        msg = consumer3.receive(100);
        assertEquals("B", ((TextMessage) msg).getText());
        _logger.info("Receive message on consumer 3 :expecting null");
        msg = consumer3.receive(100);
        assertEquals(null, msg);
        // we need to unsubscribe as the session is NO_ACKNOWLEDGE
        // messages for the durable subscriber are not deleted so the test cannot
        // be run twice in a row
        session2.unsubscribe("MySubscription");
        con.close();
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(DurableSubscriptionTest.class);
    }
}
