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
package org.apache.qpid.client;

import javax.jms.*;
import javax.jms.IllegalStateException;

class AMQTopicSessionAdaptor extends AMQSessionAdapter<TopicSession> implements TopicSession
{

    public AMQTopicSessionAdaptor(TopicSession session)
    {
        super(session);
    }

    public TopicSubscriber createSubscriber(Topic topic) throws JMSException
    {
        return getSession().createSubscriber(topic);
    }

    public TopicSubscriber createSubscriber(Topic topic, String string, boolean b) throws JMSException
    {
        return getSession().createSubscriber(topic, string, b);
    }

    public TopicPublisher createPublisher(Topic topic) throws JMSException
    {
        return getSession().createPublisher(topic);
    }

    //The following methods cannot be called from a TopicSession as per JMS spec
    public Queue createQueue(String string) throws JMSException
    {
        throw new IllegalStateException("Cannot call createQueue from TopicSession");
    }

    public QueueBrowser createBrowser(Queue queue) throws JMSException
    {
        throw new IllegalStateException("Cannot call createBrowser from TopicSession");
    }

    public QueueBrowser createBrowser(Queue queue, String string) throws JMSException
    {
        throw new IllegalStateException("Cannot call createBrowser from TopicSession");
    }

    public TemporaryQueue createTemporaryQueue() throws JMSException
    {
        throw new IllegalStateException("Cannot call createTemporaryQueue from TopicSession");
    }

}
