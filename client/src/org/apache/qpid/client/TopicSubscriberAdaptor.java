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

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

/**
 * Wraps a MessageConsumer to fulfill the extended TopicSubscriber contract
 *
 */
class TopicSubscriberAdaptor implements TopicSubscriber
{
    private final Topic _topic;
    private final MessageConsumer _consumer;
    private final boolean _noLocal;

    TopicSubscriberAdaptor(Topic topic, MessageConsumer consumer, boolean noLocal)
    {
        _topic = topic;
        _consumer = consumer;
        _noLocal = noLocal;
    }
    TopicSubscriberAdaptor(Topic topic, BasicMessageConsumer consumer)
    {
        this(topic, consumer, consumer.isNoLocal());
    }
    public Topic getTopic() throws JMSException
    {
        return _topic;
    }

    public boolean getNoLocal() throws JMSException
    {
        return _noLocal;
    }

    public String getMessageSelector() throws JMSException
    {
        return _consumer.getMessageSelector();
    }

    public MessageListener getMessageListener() throws JMSException
    {
        return _consumer.getMessageListener();
    }

    public void setMessageListener(MessageListener messageListener) throws JMSException
    {
        _consumer.setMessageListener(messageListener);
    }

    public Message receive() throws JMSException
    {
        return _consumer.receive();
    }

    public Message receive(long l) throws JMSException
    {
        return _consumer.receive(l);
    }

    public Message receiveNoWait() throws JMSException
    {
        return _consumer.receiveNoWait();
    }

    public void close() throws JMSException
    {
        _consumer.close();
    }
}
