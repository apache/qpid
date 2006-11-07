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
package org.apache.qpid.client;

import org.junit.*;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.url.URLSyntaxException;

import javax.jms.JMSException;
import javax.jms.TopicSubscriber;
import javax.jms.QueueReceiver;

import junit.framework.JUnit4TestAdapter;

/**
 * Tests for QueueReceiver and TopicSubscriber creation methods on AMQSession
 */
public class TestAMQSession {

    private static AMQSession _session;
    private static AMQTopic _topic;
    private static AMQQueue _queue;

    @BeforeClass
    public static void setUp() throws AMQException, URLSyntaxException, JMSException {
        //initialise the variables we need for testing
        startVmBrokers();
        AMQConnection connection = new AMQConnection("vm://:1", "guest", "guest", "fred", "/test");
        _topic = new AMQTopic("mytopic");
        _queue = new AMQQueue("myqueue");
        _session = (AMQSession) connection.createSession(false, AMQSession.NO_ACKNOWLEDGE);
    }

    @Test
    public void testCreateSubscriber() throws JMSException {
        TopicSubscriber subscriber = _session.createSubscriber(_topic);
        Assert.assertEquals("Topic names should match from TopicSubscriber",_topic.getTopicName(),subscriber.getTopic().getTopicName());

        subscriber = _session.createSubscriber(_topic,"abc",false);
        Assert.assertEquals("Topic names should match from TopicSubscriber with selector",_topic.getTopicName(),subscriber.getTopic().getTopicName());
    }

    @Test
    public void testCreateDurableSubscriber() throws JMSException {
        TopicSubscriber subscriber = _session.createDurableSubscriber(_topic, "mysubname");
        Assert.assertEquals("Topic names should match from durable TopicSubscriber",_topic.getTopicName(),subscriber.getTopic().getTopicName());

        subscriber = _session.createDurableSubscriber(_topic,"mysubname","abc",false);
        Assert.assertEquals("Topic names should match from durable TopicSubscriber with selector",_topic.getTopicName(),subscriber.getTopic().getTopicName());
    }

    @Test
    public void testCreateQueueReceiver() throws JMSException {
        QueueReceiver receiver = _session.createQueueReceiver(_queue);
        Assert.assertEquals("Queue names should match from QueueReceiver",_queue.getQueueName(),receiver.getQueue().getQueueName());

        receiver = _session.createQueueReceiver(_queue, "abc");
        Assert.assertEquals("Queue names should match from QueueReceiver with selector",_queue.getQueueName(),receiver.getQueue().getQueueName());
    }

    @Test
     public void testCreateReceiver() throws JMSException {
        QueueReceiver receiver = _session.createReceiver(_queue);
        Assert.assertEquals("Queue names should match from QueueReceiver",_queue.getQueueName(),receiver.getQueue().getQueueName());

        receiver = _session.createReceiver(_queue, "abc");
        Assert.assertEquals("Queue names should match from QueueReceiver with selector",_queue.getQueueName(),receiver.getQueue().getQueueName());
    }

    @AfterClass
    public static void stopVmBrokers()
    {
        TransportConnection.killVMBroker(1);
        _queue = null;
        _topic = null;
        _session = null;
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(TestAMQSession.class);
    }

    private static void startVmBrokers()
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
}
