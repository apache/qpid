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

import javax.jms.Queue;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;

/**
 * Ensures that topic specific session factory method {@link TopicConnection#createTopicSession()} create sessions
 * of type {@link TopicSession} and that those sessions correctly restrict the available JMS operations
 * operations to exclude those applicable to only queues.
 *
 * @see QueueSessionFactoryTest
 */
public class TopicSessionFactoryTest extends QpidBrokerTestCase
{
    public void testTopicSessionIsNotAQueueSession() throws Exception
    {
        TopicSession topicSession = getTopicSession();
        assertFalse(topicSession instanceof QueueSession);
    }

    public void testTopicSessionCannotCreateCreateBrowser() throws Exception
    {
        TopicSession topicSession = getTopicSession();
        Queue queue = getTestQueue();
        try
        {
            topicSession.createBrowser(queue);
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // PASS
            assertEquals("Cannot call createBrowser from TopicSession", s.getMessage());
        }
    }

    public void testTopicSessionCannotCreateQueues() throws Exception
    {
        TopicSession topicSession =  getTopicSession();
        try
        {
            topicSession.createQueue("abc");
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // PASS
            assertEquals("Cannot call createQueue from TopicSession", s.getMessage());
        }
    }

    public void testTopicSessionCannotCreateTemporaryQueues() throws Exception
    {
        TopicSession topicSession = getTopicSession();
        try
        {
            topicSession.createTemporaryQueue();
            fail("expected exception did not occur");
        }
        catch (javax.jms.IllegalStateException s)
        {
            // PASS
            assertEquals("Cannot call createTemporaryQueue from TopicSession", s.getMessage());
        }
    }

    private TopicSession getTopicSession() throws Exception
    {
        TopicConnection topicConnection = (TopicConnection)getConnection();
        return topicConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
    }

}
