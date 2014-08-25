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

import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSession;

/**
 * Ensures that queue specific session factory method {@link QueueConnection#createQueueSession()} create sessions
 * of type {@link QueueSession} and that those sessions correctly restrict the available JMS operations
 * operations to exclude those applicable to only topics.
 *
 * @see TopicSessionFactoryTest
 */
public class QueueSessionFactoryTest extends QpidBrokerTestCase
{
    public void testQueueSessionIsNotATopicSession() throws Exception
    {
        QueueSession queueSession = getQueueSession();
        assertFalse(queueSession instanceof TopicSession);
    }

    public void testQueueSessionCannotCreateTemporaryTopics() throws Exception
    {
        QueueSession queueSession = getQueueSession();
        try
        {
            queueSession.createTemporaryTopic();
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // PASS
            assertEquals("Cannot call createTemporaryTopic from QueueSession", s.getMessage());
        }
    }

    public void testQueueSessionCannotCreateTopics() throws Exception
    {
        QueueSession queueSession = getQueueSession();
        try
        {
            queueSession.createTopic("abc");
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // PASS
            assertEquals("Cannot call createTopic from QueueSession", s.getMessage());
        }
    }

    public void testQueueSessionCannotCreateDurableSubscriber() throws Exception
    {
        QueueSession queueSession = getQueueSession();
        Topic topic = getTestTopic();

        try
        {
            queueSession.createDurableSubscriber(topic, "abc");
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // PASS
            assertEquals("Cannot call createDurableSubscriber from QueueSession", s.getMessage());
        }
    }

    public void testQueueSessionCannoutUnsubscribe() throws Exception
    {
        QueueSession queueSession = getQueueSession();
        try
        {
            queueSession.unsubscribe("abc");
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // PASS
            assertEquals("Cannot call unsubscribe from QueueSession", s.getMessage());
        }
    }

    private QueueSession getQueueSession() throws Exception
    {
        QueueConnection queueConnection = (QueueConnection)getConnection();
        return queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
    }
}
