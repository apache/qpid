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

import org.apache.qpid.amqp_1_0.client.Connection;
import org.apache.qpid.amqp_1_0.jms.Session;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Topic;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SessionImpl implements Session
{
    private ConnectionImpl _connection;
    private AcknowledgeMode _acknowledgeMode;
    private org.apache.qpid.amqp_1_0.client.Session _session;
    private MessageFactory _messageFactory;
    private List<MessageConsumerImpl> _consumers = new ArrayList<MessageConsumerImpl>();

    protected SessionImpl(final ConnectionImpl connection, final AcknowledgeMode acknowledgeMode)
    {
        _connection = connection;
        _acknowledgeMode = acknowledgeMode;
        Connection clientConn = _connection.getClientConnection();
        _session = clientConn.createSession();
        _messageFactory = new MessageFactory(this);
    }

    public BytesMessageImpl createBytesMessage() throws JMSException
    {
        return new BytesMessageImpl(this);

    }

    public MapMessageImpl createMapMessage() throws JMSException
    {
        return new MapMessageImpl(this);
    }

    public MessageImpl createMessage() throws JMSException
    {
        return createAmqpMessage();
    }

    public ObjectMessageImpl createObjectMessage() throws JMSException
    {
        return new ObjectMessageImpl(this);
    }

    public ObjectMessageImpl createObjectMessage(final Serializable serializable) throws JMSException
    {
        ObjectMessageImpl msg = new ObjectMessageImpl(this);
        msg.setObject(serializable);
        return msg;
    }

    public StreamMessageImpl createStreamMessage() throws JMSException
    {
        return new StreamMessageImpl(this);
    }

    public TextMessageImpl createTextMessage() throws JMSException
    {
        return new TextMessageImpl(this);
    }

    public TextMessageImpl createTextMessage(final String s) throws JMSException
    {
        TextMessageImpl msg = new TextMessageImpl(this);
        msg.setText(s);
        return msg;
    }

    public AmqpMessageImpl createAmqpMessage() throws JMSException
    {
        return new AmqpMessageImpl(this);
    }

    public boolean getTransacted() throws JMSException
    {
        return _acknowledgeMode == AcknowledgeMode.SESSION_TRANSACTED;
    }

    public int getAcknowledgeMode()
    {
        return _acknowledgeMode.ordinal();
    }

    public void commit() throws JMSException
    {
        //TODO
    }

    public void rollback() throws JMSException
    {
        //TODO
    }

    public void close() throws JMSException
    {
        //TODO
    }

    public void recover() throws JMSException
    {
        //TODO
    }

    public MessageListener getMessageListener() throws JMSException
    {
        return null;  //TODO
    }

    public void setMessageListener(final MessageListener messageListener) throws JMSException
    {
        //TODO
    }

    public void run()
    {
        //TODO
    }

    public MessageProducerImpl createProducer(final Destination destination) throws JMSException
    {
        return new MessageProducerImpl(destination, this);
    }

    public MessageConsumerImpl createConsumer(final Destination destination) throws JMSException
    {
        return createConsumer(destination, null, false);
    }

    public MessageConsumerImpl createConsumer(final Destination destination, final String selector) throws JMSException
    {
        return createConsumer(destination, selector, false);
    }

    public MessageConsumerImpl createConsumer(final Destination destination, final String selector, final boolean noLocal)
            throws JMSException
    {
        final MessageConsumerImpl messageConsumer;
        synchronized(_session.getEndpoint().getLock())
        {
            messageConsumer = new MessageConsumerImpl(destination, this, selector, noLocal);
            addConsumer(messageConsumer);
        }
        return messageConsumer;
    }

    protected void addConsumer(final MessageConsumerImpl messageConsumer)
    {
        _consumers.add(messageConsumer);
    }

    public QueueImpl createQueue(final String s) throws JMSException
    {
        return new QueueImpl(s);
    }

    public TopicImpl createTopic(final String s) throws JMSException
    {
        return new TopicImpl(s);
    }

    public TopicSubscriberImpl createDurableSubscriber(final Topic topic, final String name) throws JMSException
    {
        return createDurableSubscriber(topic, name, null, false);
    }

    public TopicSubscriberImpl createDurableSubscriber(final Topic topic, final String name, final String selector, final boolean noLocal)
            throws JMSException
    {
        return null;  //TODO
    }

    public QueueBrowserImpl createBrowser(final Queue queue) throws JMSException
    {
        return createBrowser(queue, null);
    }

    public QueueBrowserImpl createBrowser(final Queue queue, final String selector) throws JMSException
    {
        return null;  //TODO
    }

    public TemporaryQueueImpl createTemporaryQueue() throws JMSException
    {
        return null;  //TODO
    }

    public TemporaryTopicImpl createTemporaryTopic() throws JMSException
    {
        return null;  //TODO
    }

    public void unsubscribe(final String s) throws JMSException
    {
        //TODO
    }

    void stop()
    {
        //TODO
    }

    void start()
    {
        //TODO
    }

    org.apache.qpid.amqp_1_0.client.Session getClientSession()
    {
        return _session;
    }

    public MessageFactory getMessageFactory()
    {
        return _messageFactory;
    }

    void acknowledgeAll()
    {
        synchronized(_session.getEndpoint().getLock())
        {
            for(MessageConsumerImpl consumer : _consumers)
            {
                consumer.acknowledgeAll();
            }
        }
    }
}
