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
package org.apache.qpid.test.unit.client;

import org.junit.*;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.AMQException;
import org.apache.qpid.url.URLSyntaxException;
import junit.framework.JUnit4TestAdapter;

import javax.jms.*;

public class AMQConnectionTest
{

    private static AMQConnection _connection;
    private static AMQTopic _topic;
    private static AMQQueue _queue;
    private static QueueSession _queueSession;
    private static TopicSession _topicSession;


    @Before
    public void setUp() throws AMQException, URLSyntaxException, JMSException
    {
        createVMBroker();
        //initialise the variables we need for testing
        _connection = new AMQConnection("vm://:1", "guest", "guest", "fred", "/test");
        _topic = new AMQTopic("mytopic");
        _queue = new AMQQueue("myqueue");
    }

    public void createVMBroker()
    {
        try
        {
            TransportConnection.createVMBroker(1);
        }
        catch (AMQVMBrokerCreationException e)
        {
            Assert.fail("Unable to create broker: " + e);
        }
    }

    @After
    public void stopVmBroker()
    {
        try
        {
            _connection.close();
        }
        catch (JMSException e)
        {
            //ignore 
        }
        TransportConnection.killVMBroker(1);
    }

    /**
     * Simple tests to check we can create TopicSession and QueueSession ok
     * And that they throw exceptions where appropriate as per JMS spec
     */

    @Test
    public void testCreateQueueSession() throws JMSException
    {
        _queueSession = _connection.createQueueSession(false, AMQSession.NO_ACKNOWLEDGE);
    }

    @Test
    public void testCreateTopicSession() throws JMSException
    {
        _topicSession = _connection.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
    }

    @Test(expected = javax.jms.IllegalStateException.class)
    public void testTopicSessionCreateBrowser() throws JMSException
    {
        _topicSession.createBrowser(_queue);
    }

    @Test(expected = javax.jms.IllegalStateException.class)
    public void testTopicSessionCreateQueue() throws JMSException
    {
        _topicSession.createQueue("abc");
    }

    @Test(expected = javax.jms.IllegalStateException.class)
    public void testTopicSessionCreateTemporaryQueue() throws JMSException
    {
        _topicSession.createTemporaryQueue();
    }

    @Test(expected = javax.jms.IllegalStateException.class)
    public void testQueueSessionCreateTemporaryTopic() throws JMSException
    {
        _queueSession.createTemporaryTopic();
    }

    @Test(expected = javax.jms.IllegalStateException.class)
    public void testQueueSessionCreateTopic() throws JMSException
    {
        _queueSession.createTopic("abc");
    }

    @Test(expected = javax.jms.IllegalStateException.class)
    public void testQueueSessionDurableSubscriber() throws JMSException
    {
        _queueSession.createDurableSubscriber(_topic, "abc");
    }

    @Test(expected = javax.jms.IllegalStateException.class)
    public void testQueueSessionUnsubscribe() throws JMSException
    {
        _queueSession.unsubscribe("abc");
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(AMQConnectionTest.class);
    }
}
