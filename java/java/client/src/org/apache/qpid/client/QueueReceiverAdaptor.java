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

import javax.jms.*;

/**
 * Class that wraps a MessageConsumer for backwards JMS compatibility
 * Returned by methods in AMQSession etc
 */
public class QueueReceiverAdaptor implements QueueReceiver {

    protected MessageConsumer _consumer;
    protected Queue _queue;

    protected QueueReceiverAdaptor(Queue queue, MessageConsumer consumer)
    {
        _consumer = consumer;
        _queue = queue;
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

    /**
     * Return the queue associated with this receiver
     * @return
     * @throws JMSException
     */
    public Queue getQueue() throws JMSException
    {
       return _queue;
    }


}
