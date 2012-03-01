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
import java.io.Serializable;

public abstract class AMQSessionAdapter<T extends Session> implements Session
{
    private final T _session;

    protected AMQSessionAdapter(final T session)
    {
        _session = session;
    }

    public T getSession()
    {
        return _session;
    }

    public BytesMessage createBytesMessage() throws JMSException
    {
        return _session.createBytesMessage();
    }

    public MapMessage createMapMessage() throws JMSException
    {
        return _session.createMapMessage();
    }

    public Message createMessage() throws JMSException
    {
        return _session.createMessage();
    }

    public ObjectMessage createObjectMessage() throws JMSException
    {
        return _session.createObjectMessage();
    }

    public ObjectMessage createObjectMessage(final Serializable serializable) throws JMSException
    {
        return _session.createObjectMessage(serializable);
    }

    public StreamMessage createStreamMessage() throws JMSException
    {
        return _session.createStreamMessage();
    }

    public TextMessage createTextMessage() throws JMSException
    {
        return _session.createTextMessage();
    }

    public TextMessage createTextMessage(final String s) throws JMSException
    {
        return _session.createTextMessage(s);
    }

    public boolean getTransacted() throws JMSException
    {
        return _session.getTransacted();
    }

    public int getAcknowledgeMode() throws JMSException
    {
        return _session.getAcknowledgeMode();
    }

    public void commit() throws JMSException
    {
        _session.commit();
    }

    public void rollback() throws JMSException
    {
        _session.rollback();
    }

    public void close() throws JMSException
    {
        _session.close();
    }

    public void recover() throws JMSException
    {
        _session.recover();
    }

    public MessageListener getMessageListener() throws JMSException
    {
        return _session.getMessageListener();
    }

    public void setMessageListener(final MessageListener messageListener) throws JMSException
    {
        _session.setMessageListener(messageListener);
    }

    public void run()
    {
        _session.run();
    }

    public MessageProducer createProducer(final Destination destination) throws JMSException
    {
        return _session.createProducer(destination);
    }

    public MessageConsumer createConsumer(final Destination destination) throws JMSException
    {
        return _session.createConsumer(destination);
    }

    public MessageConsumer createConsumer(final Destination destination, final String s) throws JMSException
    {
        return _session.createConsumer(destination, s);
    }

    public MessageConsumer createConsumer(final Destination destination, final String s, final boolean b)
            throws JMSException
    {
        return _session.createConsumer(destination, s, b);
    }

    public Queue createQueue(final String s) throws JMSException
    {
        return _session.createQueue(s);
    }

    public Topic createTopic(final String s) throws JMSException
    {
        return _session.createTopic(s);
    }

    public TopicSubscriber createDurableSubscriber(final Topic topic, final String s) throws JMSException
    {
        return _session.createDurableSubscriber(topic, s);
    }

    public TopicSubscriber createDurableSubscriber(final Topic topic, final String s, final String s1, final boolean b)
            throws JMSException
    {
        return _session.createDurableSubscriber(topic, s, s1, b);
    }

    public QueueBrowser createBrowser(final Queue queue) throws JMSException
    {
        return _session.createBrowser(queue);
    }

    public QueueBrowser createBrowser(final Queue queue, final String s) throws JMSException
    {
        return _session.createBrowser(queue, s);
    }

    public TemporaryQueue createTemporaryQueue() throws JMSException
    {
        return _session.createTemporaryQueue();
    }

    public TemporaryTopic createTemporaryTopic() throws JMSException
    {
        return _session.createTemporaryTopic();
    }

    public void unsubscribe(final String s) throws JMSException
    {
        _session.unsubscribe(s);
    }
}
