/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.topic;

import junit.framework.JUnit4TestAdapter;
import org.junit.Assert;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.transport.TransportConnection;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TopicSubscriber;

public class DurableSubscriptionTest
{

          @Before
    public void startVmBrokers()
    {
        try
        {
            TransportConnection.createVMBroker(1);
        }
        catch (AMQVMBrokerCreationException e)
        {
            Assert.fail("Unable to create VM Broker: " + e.getMessage());
        }
    }

    @After
    public void stopVmBrokers()
    {
        TransportConnection.killVMBroker(1);
    }

    @Test
    public void unsubscribe() throws AMQException, JMSException, URLSyntaxException
    {
        AMQTopic topic = new AMQTopic("MyTopic");
        AMQConnection con = new AMQConnection("vm://:1", "guest", "guest", "test", "/test");
        Session session1 = con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        MessageConsumer consumer1 = session1.createConsumer(topic);
        MessageProducer producer = session1.createProducer(topic);

        Session session2 = con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicSubscriber consumer2 = session2.createDurableSubscriber(topic, "MySubscription");

        con.start();

        producer.send(session1.createTextMessage("A"));

        Message msg;
        msg = consumer1.receive();
        Assert.assertEquals("A", ((TextMessage) msg).getText());
        msg = consumer1.receive(1000);
        Assert.assertEquals(null, msg);

        msg = consumer2.receive();
        Assert.assertEquals("A", ((TextMessage) msg).getText());
        msg = consumer2.receive(1000);
        Assert.assertEquals(null, msg);

        session2.unsubscribe("MySubscription");

        producer.send(session1.createTextMessage("B"));

        msg = consumer1.receive();
        Assert.assertEquals("B", ((TextMessage) msg).getText());
        msg = consumer1.receive(1000);
        Assert.assertEquals(null, msg);

        msg = consumer2.receive(1000);
        Assert.assertEquals(null, msg);

        con.close();
    }

    @Test
    public void durability() throws AMQException, JMSException, URLSyntaxException
    {
        AMQTopic topic = new AMQTopic("MyTopic");
        AMQConnection con = new AMQConnection("vm://:1", "guest", "guest", "test", "/test");
        Session session1 = con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        MessageConsumer consumer1 = session1.createConsumer(topic);
        MessageProducer producer = session1.createProducer(topic);

        Session session2 = con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicSubscriber consumer2 = session2.createDurableSubscriber(topic, "MySubscription");

        con.start();

        producer.send(session1.createTextMessage("A"));

        Message msg;
        msg = consumer1.receive();
        Assert.assertEquals("A", ((TextMessage) msg).getText());
        msg = consumer1.receive(1000);
        Assert.assertEquals(null, msg);

        msg = consumer2.receive();
        Assert.assertEquals("A", ((TextMessage) msg).getText());
        msg = consumer2.receive(1000);
        Assert.assertEquals(null, msg);

        consumer2.close();

        Session session3 = con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        MessageConsumer consumer3 = session3.createDurableSubscriber(topic, "MySubscription");

        producer.send(session1.createTextMessage("B"));

        msg = consumer1.receive();
        Assert.assertEquals("B", ((TextMessage) msg).getText());
        msg = consumer1.receive(1000);
        Assert.assertEquals(null, msg);

        msg = consumer3.receive();
        Assert.assertEquals("B", ((TextMessage) msg).getText());
        msg = consumer3.receive(1000);
        Assert.assertEquals(null, msg);

        con.close();
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(DurableSubscriptionTest.class);
    }

}
