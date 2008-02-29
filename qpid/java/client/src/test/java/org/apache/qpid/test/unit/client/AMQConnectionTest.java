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

import javax.jms.JMSException;
import javax.jms.QueueSession;
import javax.jms.TopicSession;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.testutil.QpidTestCase;

public class AMQConnectionTest extends QpidTestCase
{
    private static AMQConnection _connection;
    private static AMQTopic _topic;
    private static AMQQueue _queue;
    private static QueueSession _queueSession;
    private static TopicSession _topicSession;

    protected void setUp() throws Exception
    {
        super.setUp();
        _connection = (AMQConnection) getConnection("guest", "guest");
        _topic = new AMQTopic(_connection.getDefaultTopicExchangeName(), new AMQShortString("mytopic"));
        _queue = new AMQQueue(_connection.getDefaultQueueExchangeName(), new AMQShortString("myqueue"));
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
    }

    /**
     * Simple tests to check we can create TopicSession and QueueSession ok
     * And that they throw exceptions where appropriate as per JMS spec
     */

    public void testCreateQueueSession() throws JMSException
    {
        _queueSession = _connection.createQueueSession(false, AMQSession.NO_ACKNOWLEDGE);
    }

    public void testCreateTopicSession() throws JMSException
    {
        _topicSession = _connection.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
    }

    public void testTopicSessionCreateBrowser() throws JMSException
    {
        try
        {
            _topicSession.createBrowser(_queue);
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected javax.jms.IllegalStateException, got " + e);
        }
    }

    public void testTopicSessionCreateQueue() throws JMSException
    {
        try
        {
            _topicSession.createQueue("abc");
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected javax.jms.IllegalStateException, got " + e);
        }
    }

    public void testTopicSessionCreateTemporaryQueue() throws JMSException
    {
        try
        {
            _topicSession.createTemporaryQueue();
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected javax.jms.IllegalStateException, got " + e);
        }
    }

    public void testQueueSessionCreateTemporaryTopic() throws JMSException
    {
        try
        {
            _queueSession.createTemporaryTopic();
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected javax.jms.IllegalStateException, got " + e);
        }
    }

    public void testQueueSessionCreateTopic() throws JMSException
    {
        try
        {
            _queueSession.createTopic("abc");
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected javax.jms.IllegalStateException, got " + e);
        }
    }

    public void testQueueSessionDurableSubscriber() throws JMSException
    {
        try
        {
            _queueSession.createDurableSubscriber(_topic, "abc");
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected javax.jms.IllegalStateException, got " + e);
        }
    }

    public void testQueueSessionUnsubscribe() throws JMSException
    {
        try
        {
            _queueSession.unsubscribe("abc");
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected javax.jms.IllegalStateException, got " + e);
        }
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(AMQConnectionTest.class);
    }
}
