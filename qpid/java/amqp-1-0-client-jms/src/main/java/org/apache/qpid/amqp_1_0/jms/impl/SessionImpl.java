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
import org.apache.qpid.amqp_1_0.client.Message;
import org.apache.qpid.amqp_1_0.client.Sender;
import org.apache.qpid.amqp_1_0.jms.QueueReceiver;
import org.apache.qpid.amqp_1_0.jms.QueueSender;
import org.apache.qpid.amqp_1_0.jms.QueueSession;
import org.apache.qpid.amqp_1_0.jms.Session;
import org.apache.qpid.amqp_1_0.jms.TopicPublisher;
import org.apache.qpid.amqp_1_0.jms.TopicSession;
import org.apache.qpid.amqp_1_0.jms.TopicSubscriber;
import org.apache.qpid.amqp_1_0.type.messaging.Target;

import javax.jms.*;
import javax.jms.IllegalStateException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class SessionImpl implements Session, QueueSession, TopicSession
{
    private ConnectionImpl _connection;
    private AcknowledgeMode _acknowledgeMode;
    private org.apache.qpid.amqp_1_0.client.Session _session;
    private MessageFactory _messageFactory;
    private List<MessageConsumerImpl> _consumers = new ArrayList<MessageConsumerImpl>();
    private List<MessageProducerImpl> _producers = new ArrayList<MessageProducerImpl>();

    private MessageListener _messageListener;
    private Dispatcher _dispatcher = new Dispatcher();
    private Thread _dispatcherThread;

    private boolean _closed;

    private boolean _isQueueSession;
    private boolean _isTopicSession;

    protected SessionImpl(final ConnectionImpl connection, final AcknowledgeMode acknowledgeMode)
    {
        _connection = connection;
        _acknowledgeMode = acknowledgeMode;
        Connection clientConn = _connection.getClientConnection();
        _session = clientConn.createSession();
        _messageFactory = new MessageFactory(this);

        _dispatcherThread = new Thread(_dispatcher);
        _dispatcherThread.start();
    }

    public BytesMessageImpl createBytesMessage() throws IllegalStateException
    {
        checkClosed();
        return new BytesMessageImpl(this);

    }

    public MapMessageImpl createMapMessage() throws JMSException
    {
        checkClosed();
        return new MapMessageImpl(this);
    }

    public MessageImpl createMessage() throws IllegalStateException
    {
        return createAmqpMessage();
    }

    public ObjectMessageImpl createObjectMessage() throws JMSException
    {
        checkClosed();
        return new ObjectMessageImpl(this);
    }

    public ObjectMessageImpl createObjectMessage(final Serializable serializable) throws JMSException
    {
        checkClosed();
        ObjectMessageImpl msg = new ObjectMessageImpl(this);
        msg.setObject(serializable);
        return msg;
    }

    public StreamMessageImpl createStreamMessage() throws JMSException
    {
        checkClosed();
        return new StreamMessageImpl(this);
    }

    public TextMessageImpl createTextMessage() throws JMSException
    {
        return createTextMessage("");
    }

    public TextMessageImpl createTextMessage(final String s) throws JMSException
    {
        checkClosed();
        TextMessageImpl msg = new TextMessageImpl(this);
        msg.setText(s);
        return msg;
    }

    public AmqpMessageImpl createAmqpMessage() throws IllegalStateException
    {
        checkClosed();
        return new AmqpMessageImpl(this);
    }

    public boolean getTransacted() throws JMSException
    {
        checkClosed();
        return _acknowledgeMode == AcknowledgeMode.SESSION_TRANSACTED;
    }

    public int getAcknowledgeMode() throws IllegalStateException
    {
        checkClosed();
        return _acknowledgeMode.ordinal();
    }

    public void commit() throws JMSException
    {
        checkClosed();
        //TODO
    }

    public void rollback() throws JMSException
    {
        checkClosed();
        //TODO
    }

    public void close() throws JMSException
    {
        if(!_closed)
        {
            _closed = true;
            _dispatcher.close();
            for(MessageConsumerImpl consumer : _consumers)
            {
                consumer.close();
            }
            for(MessageProducerImpl producer : _producers)
            {
                producer.close();
            }
            _session.close();
        }
    }

    private void checkClosed() throws IllegalStateException
    {
        if(_closed)
            throw new IllegalStateException("Closed");
    }

    public void recover() throws JMSException
    {
        checkClosed();
        //TODO
    }

    public MessageListener getMessageListener() throws JMSException
    {
        return _messageListener;
    }

    public void setMessageListener(final MessageListener messageListener) throws JMSException
    {
        if(_messageListener != null)
        {

        }
        else
        {
            _messageListener = messageListener;
        }
    }

    public void run()
    {
        //TODO
    }

    public MessageProducerImpl createProducer(final Destination destination) throws JMSException
    {
        checkClosed();

        final MessageProducerImpl messageProducer = new MessageProducerImpl(destination, this);

        _producers.add(messageProducer);

        return messageProducer;
    }

    public MessageConsumerImpl createConsumer(final Destination destination) throws JMSException
    {
        checkClosed();
        return createConsumer(destination, null, false);
    }

    public MessageConsumerImpl createConsumer(final Destination destination, final String selector) throws JMSException
    {
        checkClosed();
        return createConsumer(destination, selector, false);
    }

    public MessageConsumerImpl createConsumer(final Destination destination, final String selector, final boolean noLocal)
            throws JMSException
    {
        checkClosed();
        final MessageConsumerImpl messageConsumer;
        synchronized(_session.getEndpoint().getLock())
        {
            messageConsumer = new MessageConsumerImpl(destination, this, selector, noLocal);
            addConsumer(messageConsumer);
            if(_connection.isStarted())
            {
                messageConsumer.start();
            }
        }
        return messageConsumer;
    }

    protected void addConsumer(final MessageConsumerImpl messageConsumer)
    {
        _consumers.add(messageConsumer);
    }

    public QueueImpl createQueue(final String s) throws JMSException
    {
        checkClosed();
        return new QueueImpl(s);
    }

    public QueueReceiver createReceiver(final Queue queue) throws JMSException
    {
        checkClosed();
        return createConsumer(queue);
    }

    public QueueReceiver createReceiver(final Queue queue, final String selector) throws JMSException
    {
        checkClosed();
        return createConsumer(queue, selector);
    }

    public QueueSender createSender(final Queue queue) throws JMSException
    {
        checkClosed();
        return createProducer(queue);
    }

    public TopicImpl createTopic(final String s) throws JMSException
    {
        checkClosed();
        return new TopicImpl(s);
    }

    public TopicSubscriber createSubscriber(final Topic topic) throws JMSException
    {
        checkClosed();
        return createConsumer(topic);
    }

    public TopicSubscriber createSubscriber(final Topic topic, final String selector, final boolean noLocal) throws JMSException
    {
        checkClosed();
        return createConsumer(topic, selector, noLocal);
    }

    public TopicSubscriberImpl createDurableSubscriber(final Topic topic, final String name) throws JMSException
    {
        checkClosed();
        return createDurableSubscriber(topic, name, null, false);
    }

    public TopicSubscriberImpl createDurableSubscriber(final Topic topic, final String name, final String selector, final boolean noLocal)
            throws JMSException
    {
        checkClosed();
        if(!(topic instanceof TopicImpl))
        {
            throw new InvalidDestinationException("invalid destination " + topic);
        }
        return null;  //TODO
    }

    public TopicPublisher createPublisher(final Topic topic) throws JMSException
    {
        checkClosed();
        return createProducer(topic);
    }

    public QueueBrowserImpl createBrowser(final Queue queue) throws JMSException
    {
        checkClosed();
        return createBrowser(queue, null);
    }

    public QueueBrowserImpl createBrowser(final Queue queue, final String selector) throws JMSException
    {
        checkClosed();
        return null;  //TODO
    }

    public TemporaryQueueImpl createTemporaryQueue() throws JMSException
    {
        checkClosed();
        try
        {
            Sender send = _session.createTemporaryQueueSender();

            TemporaryQueueImpl tempQ = new TemporaryQueueImpl(((Target)send.getTarget()).getAddress(), send);
            return tempQ;
        }
        catch (Sender.SenderCreationException e)
        {
            throw new JMSException("Unable to create temporary queue");
        }
    }

    public TemporaryTopicImpl createTemporaryTopic() throws JMSException
    {
        checkClosed();
        try
        {
            Sender send = _session.createTemporaryQueueSender();

            TemporaryTopicImpl tempQ = new TemporaryTopicImpl(((Target)send.getTarget()).getAddress(), send);
            return tempQ;
        }
        catch (Sender.SenderCreationException e)
        {
            throw new JMSException("Unable to create temporary queue");
        }
    }

    public void unsubscribe(final String s) throws JMSException
    {
        checkClosed();

        //TODO
    }

    void stop()
    {
        //TODO
    }

    void start()
    {
        _dispatcher.start();
        for(MessageConsumerImpl consumer : _consumers)
        {
            consumer.start();
        }
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

    void messageListenerSet(final MessageConsumerImpl messageConsumer)
    {
        _dispatcher.updateMessageListener(messageConsumer);
    }

    public void messageArrived(final MessageConsumerImpl messageConsumer)
    {
        _dispatcher.messageArrivedAtConsumer(messageConsumer);
    }

    MessageImpl convertMessage(final javax.jms.Message message) throws JMSException
    {
        MessageImpl replacementMessage;

        if(message instanceof BytesMessage)
        {
            replacementMessage = convertBytesMessage((BytesMessage) message);
        }
        else if(message instanceof MapMessage)
        {
            replacementMessage = convertMapMessage((MapMessage) message);
        }
        else if(message instanceof ObjectMessage)
        {
            replacementMessage = convertObjectMessage((ObjectMessage) message);
        }
        else if(message instanceof StreamMessage)
        {
            replacementMessage = convertStreamMessage((StreamMessage) message);
        }
        else if(message instanceof TextMessage)
        {
            replacementMessage = convertTextMessage((TextMessage) message);
        }
        else
        {
            replacementMessage = createMessage();
        }

        convertMessageProperties(message, replacementMessage);

        return replacementMessage;
    }


    private void convertMessageProperties(final javax.jms.Message message, final MessageImpl replacementMessage)
            throws JMSException
    {
        Enumeration propertyNames = message.getPropertyNames();
        while (propertyNames.hasMoreElements())
        {
            String propertyName = String.valueOf(propertyNames.nextElement());
            // TODO: Shouldn't need to check for JMS properties here as don't think getPropertyNames() should return them
            if (!propertyName.startsWith("JMSX_"))
            {
                Object value = message.getObjectProperty(propertyName);
                replacementMessage.setObjectProperty(propertyName, value);
            }
        }


        replacementMessage.setJMSDeliveryMode(message.getJMSDeliveryMode());

        if (message.getJMSReplyTo() != null)
        {
            replacementMessage.setJMSReplyTo(message.getJMSReplyTo());
        }

        replacementMessage.setJMSType(message.getJMSType());

        replacementMessage.setJMSCorrelationID(message.getJMSCorrelationID());
    }

    private MessageImpl convertMapMessage(final MapMessage message) throws JMSException
    {
        MapMessageImpl mapMessage = createMapMessage();

        Enumeration mapNames = message.getMapNames();
        while (mapNames.hasMoreElements())
        {
            String name = (String) mapNames.nextElement();
            mapMessage.setObject(name, message.getObject(name));
        }

        return mapMessage;
    }

    private MessageImpl convertBytesMessage(final BytesMessage message) throws JMSException
    {
        BytesMessageImpl bytesMessage = createBytesMessage();

        message.reset();

        byte[] buf = new byte[1024];

        int len;

        while ((len = message.readBytes(buf)) != -1)
        {
            bytesMessage.writeBytes(buf, 0, len);
        }

        return bytesMessage;
    }

    private MessageImpl convertObjectMessage(final ObjectMessage message) throws JMSException
    {
        ObjectMessageImpl objectMessage = createObjectMessage();
        objectMessage.setObject(message.getObject());
        return objectMessage;
    }

    private MessageImpl convertStreamMessage(final StreamMessage message) throws JMSException
    {
        StreamMessageImpl streamMessage = createStreamMessage();

        try
        {
            message.reset();
            while (true)
            {
                streamMessage.writeObject(message.readObject());
            }
        }
        catch (MessageEOFException e)
        {
            // we're at the end so don't mind the exception
        }

        return streamMessage;
    }

    private MessageImpl convertTextMessage(final TextMessage message) throws JMSException
    {
        return createTextMessage(message.getText());
    }


    private class Dispatcher implements Runnable
    {

        private final List<MessageConsumerImpl> _messageConsumerList = new ArrayList<MessageConsumerImpl>();

        private boolean _closed;
        private boolean _started;

        public void run()
        {
            synchronized(getLock())
            {
                while(!_closed)
                {
                    while(!_started || _messageConsumerList.isEmpty())
                    {
                        try
                        {
                            getLock().wait();
                        }
                        catch (InterruptedException e)
                        {
                            return;
                        }
                    }
                    while(_started && !_messageConsumerList.isEmpty())
                    {
                        MessageConsumerImpl consumer = _messageConsumerList.remove(0);
                        MessageListener listener = consumer._messageListener;
                        Message msg = consumer.receive0(0L);

                        MessageImpl message = consumer.createJMSMessage(msg);

                        if(message != null)
                        {
                            listener.onMessage(message);
                            if(_acknowledgeMode == AcknowledgeMode.AUTO_ACKNOWLEDGE
                               || _acknowledgeMode == AcknowledgeMode.DUPS_OK_ACKNOWLEDGE)
                            {
                                consumer.acknowledge(msg);
                            }
                        }

                    }
                }


            }

        }

        private Object getLock()
        {
            return _session.getEndpoint().getLock();
        }

        public void messageArrivedAtConsumer(MessageConsumerImpl impl)
        {
            synchronized (getLock())
            {
                _messageConsumerList.add(impl);
                getLock().notifyAll();
            }
        }

        public void close()
        {
            synchronized (getLock())
            {
                _closed = true;
                getLock().notifyAll();
            }
        }

        public void updateMessageListener(final MessageConsumerImpl messageConsumer)
        {
            synchronized (getLock())
            {
                getLock().notifyAll();
            }
        }

        public void start()
        {
            synchronized (getLock())
            {
                _started = true;
                getLock().notifyAll();
            }
        }

        public void stop()
        {
            synchronized (getLock())
            {
                _started = false;
                getLock().notifyAll();
            }
        }
    }

    void setQueueSession(final boolean queueSession)
    {
        _isQueueSession = queueSession;
    }

    void setTopicSession(final boolean topicSession)
    {
        _isTopicSession = topicSession;
    }
}
