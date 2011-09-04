/*
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
 */
package org.apache.qpid.amqp_1_0.jms.impl;

import org.apache.qpid.amqp_1_0.jms.TopicSession;

import javax.jms.JMSException;
import javax.jms.Topic;

public class TopicSessionImpl extends SessionImpl implements TopicSession
{
    protected TopicSessionImpl(final ConnectionImpl connection, final AcknowledgeMode acknowledgeMode)
    {
        super(connection, acknowledgeMode);
        setTopicSession(true);
    }

    public TopicSubscriberImpl createSubscriber(final Topic topic) throws JMSException
    {
        return createSubscriber(topic,null, false);
    }

    public TopicSubscriberImpl createSubscriber(final Topic topic, final String selector, final boolean noLocal) throws JMSException
    {

        final TopicSubscriberImpl messageConsumer;
        synchronized(getClientSession().getEndpoint().getLock())
        {
            messageConsumer = new TopicSubscriberImpl((TopicImpl) topic, this, selector, noLocal);
            addConsumer(messageConsumer);
        }
        return messageConsumer;
    }

    public TopicPublisherImpl createPublisher(final Topic topic) throws JMSException
    {
        return null;  //TODO
    }
}
