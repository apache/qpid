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
import java.io.Serializable;

/**
 * Need this adaptor class to conform to JMS spec and throw IllegalStateException
 * from createDurableSubscriber, unsubscribe, createTopic & createTemporaryTopic
 */
class AMQQueueSessionAdaptor extends AMQSessionAdapter<QueueSession> implements QueueSession
{
    /**
     * Construct an adaptor with a session to wrap
     * @param session
     */
    protected AMQQueueSessionAdaptor(QueueSession session)
    {
        super(session);
    }

    public QueueReceiver createReceiver(Queue queue) throws JMSException
    {
        return getSession().createReceiver(queue);
    }

    public QueueReceiver createReceiver(Queue queue, String string) throws JMSException
    {
        return getSession().createReceiver(queue, string);
    }

    public QueueSender createSender(Queue queue) throws JMSException
    {
        return getSession().createSender(queue);
    }

    //The following methods cannot be called from a QueueSession as per JMS spec

    public Topic createTopic(String string) throws JMSException
    {
        throw new IllegalStateException("Cannot call createTopic from QueueSession");
    }

    public TopicSubscriber createDurableSubscriber(Topic topic, String string) throws JMSException
    {
        throw new IllegalStateException("Cannot call createDurableSubscriber from QueueSession");
    }

    public TopicSubscriber createDurableSubscriber(Topic topic, String string, String string1, boolean b) throws JMSException
    {
         throw new IllegalStateException("Cannot call createDurableSubscriber from QueueSession");
    }

    public TemporaryTopic createTemporaryTopic() throws JMSException
    {
        throw new IllegalStateException("Cannot call createTemporaryTopic from QueueSession");
    }

    public void unsubscribe(String string) throws JMSException
    {
        throw new IllegalStateException("Cannot call unsubscribe from QueueSession");
    }

}
