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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageEOFException;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.qpid.amqp_1_0.client.*;
import org.apache.qpid.amqp_1_0.jms.QueueReceiver;
import org.apache.qpid.amqp_1_0.jms.QueueSender;
import org.apache.qpid.amqp_1_0.jms.QueueSession;
import org.apache.qpid.amqp_1_0.jms.Session;
import org.apache.qpid.amqp_1_0.jms.TemporaryDestination;
import org.apache.qpid.amqp_1_0.jms.TopicPublisher;
import org.apache.qpid.amqp_1_0.jms.TopicSession;
import org.apache.qpid.amqp_1_0.jms.TopicSubscriber;
import org.apache.qpid.amqp_1_0.jms.ErrorCodes;
import org.apache.qpid.amqp_1_0.jms.SessionException;
import org.apache.qpid.amqp_1_0.transport.SessionEventListener;
import org.apache.qpid.amqp_1_0.type.messaging.Source;
import org.apache.qpid.amqp_1_0.type.messaging.Target;
import org.apache.qpid.amqp_1_0.type.transport.*;
import org.apache.qpid.amqp_1_0.type.transport.Error;

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
    private Transaction _txn;
    private int _maxPrefetch;

    protected SessionImpl(final ConnectionImpl connection, final AcknowledgeMode acknowledgeMode) throws JMSException
    {
        _connection = connection;
        _acknowledgeMode = acknowledgeMode;
        Connection clientConn = _connection.getClientConnection();
        try
        {
            _session = clientConn.createSession();
        }
        catch (ConnectionException e)
        {
            JMSException jmsException;
            if (e instanceof ChannelsExhaustedException)
            {
                jmsException = new JMSException(e.getMessage(), ErrorCodes.CHANNELS_EXHAUSTED);
            }
            else
            {
                jmsException = new JMSException(e.getMessage());
            }
            jmsException.setLinkedException(e);
            throw jmsException;
        }
        _session.getEndpoint().setSessionEventListener(new SessionEventListener.DefaultSessionEventListener()
        {
            @Override
            public void remoteEnd(End end)
            {
                if(!_closed)
                {
                    try
                    {
                        close();
                    }
                    catch (JMSException e)
                    {
                    }
                    try
                    {
                        final Error error = end == null ? null : end.getError();
                        final ExceptionListener exceptionListener = _connection.getExceptionListener();
                        if(exceptionListener != null)
                        {
                            if(error != null)
                            {
                                SessionException se = new SessionException(
                                        error.getDescription(),
                                        error.getCondition().getValue().toString());

                                exceptionListener.onException(se);
                            }
                            else
                            {
                                exceptionListener.onException(new SessionException("Session remotely closed"));
                            }
                        }
                    }
                    catch (JMSException e)
                    {

                    }

                }
            }
        });
        if(_acknowledgeMode == AcknowledgeMode.SESSION_TRANSACTED)
        {
            try
            {
                _txn = _session.createSessionLocalTransaction();
            }
            catch (LinkDetachedException e)
            {
                JMSException jmsException = new JMSException("Unable to create transactional session");
                jmsException.setLinkedException(e);
                jmsException.initCause(e);
                throw jmsException;
            }
        }

        _messageFactory = new MessageFactory(this);

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

    AcknowledgeMode getAckModeEnum()
    {
        return _acknowledgeMode;
    }

    public void commit() throws JMSException
    {
        checkClosed();
        checkTransactional();

        try
        {
            _txn.commit();
            for(MessageConsumerImpl consumer : _consumers)
            {
                consumer.postCommit();
            }

            _txn = _session.createSessionLocalTransaction();
        }
        catch (LinkDetachedException e)
        {
            final JMSException jmsException = new JMSException("Unable to commit transaction");
            jmsException.setLinkedException(e);
            jmsException.initCause(e);
            throw jmsException;
        }
    }

    public void rollback() throws JMSException
    {
        checkClosed();
        checkTransactional();

        try
        {
            _txn.rollback();

            for(MessageConsumerImpl consumer : _consumers)
            {
                consumer.postRollback();
            }

            _txn = _session.createSessionLocalTransaction();
        }
        catch (LinkDetachedException e)
        {
            final JMSException jmsException = new JMSException("Unable to rollback transaction");
            jmsException.setLinkedException(e);
            jmsException.initCause(e);
            throw jmsException;
        }
    }

    private void checkTransactional() throws JMSException
    {
        if(!getTransacted())
        {
            throw new IllegalStateException("Session must be transacted in order to perform this operation");
        }
    }

    public void close() throws JMSException
    {
        if(!_closed)
        {
            _closed = true;
            _dispatcher.close();
            
            List<MessageConsumerImpl> consumers = null;
            List<MessageProducerImpl> producers = null;
            synchronized (_session.getEndpoint().getLock())
            {
                consumers = new ArrayList<MessageConsumerImpl>(_consumers);
                producers = new ArrayList<MessageProducerImpl>(_producers);
            }
            for(MessageConsumerImpl consumer : consumers)
            {
                consumer.close();
            }
            for(MessageProducerImpl producer : producers)
            {
                producer.close();
            }
            
            _session.close();
            _connection.removeSession(this);
        }
    }

    private void checkClosed() throws IllegalStateException
    {
        if(_closed)
        {
            throw new IllegalStateException("Closed");
        }
    }

    public void recover() throws JMSException
    {
        checkClosed();
        checkNotTransactional();

        if(_acknowledgeMode == AcknowledgeMode.CLIENT_ACKNOWLEDGE)
        {
            synchronized(_session.getEndpoint().getLock())
            {
                for(MessageConsumerImpl consumer : _consumers)
                {
                    consumer.doRecover();
                }
            }
        }
        else
        {
            if(Thread.currentThread() == _dispatcherThread)
            {
                _dispatcher.doRecover();
            }
        }

    }

    private void checkNotTransactional() throws JMSException
    {

        if(getTransacted())
        {
            throw new IllegalStateException("This operation cannot be carried out on a transacted session");
        }
    }

    public MessageListener getMessageListener() throws JMSException
    {
        return _messageListener;
    }

    public void setMessageListener(final MessageListener messageListener) throws JMSException
    {
        if(_messageListener != null)
        {
            // TODO
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
        checkValidDestination(destination);
        if(destination instanceof TemporaryDestination)
        {
            TemporaryDestination temporaryDestination = (TemporaryDestination) destination;
            if(temporaryDestination.getSession() != this)
            {
                throw new JMSException("Cannot consume from a temporary destination created on another session");
            }
            if(temporaryDestination.isDeleted())
            {
                throw new IllegalStateException("Destination is deleted");
            }
        }
        final MessageConsumerImpl messageConsumer;
        synchronized(_session.getEndpoint().getLock())
        {
            if(_dispatcherThread == null)
            {
                _dispatcherThread = new Thread(_dispatcher);
                _dispatcherThread.start();
            }

            messageConsumer = new MessageConsumerImpl(destination, this, selector, noLocal);
            addConsumer(messageConsumer);
            if(_connection.isStarted())
            {
                messageConsumer.start();
            }
        }
        return messageConsumer;
    }

    private void checkValidDestination(Destination destination) throws InvalidDestinationException
    {
        if (destination == null || !(destination instanceof DestinationImpl))
        {
            throw new InvalidDestinationException("Invalid Destination");
        }
    }


    protected void addConsumer(final MessageConsumerImpl messageConsumer)
    {
        _consumers.add(messageConsumer);
    }

    public QueueImpl createQueue(final String s) throws JMSException
    {
        checkClosed();
        checkNotTopicSession();
        return QueueImpl.valueOf(s);
    }

    public QueueReceiver createReceiver(final Queue queue) throws JMSException
    {
        checkClosed();
        checkNotTopicSession();
        return createConsumer(queue);
    }

    public QueueReceiver createReceiver(final Queue queue, final String selector) throws JMSException
    {
        checkClosed();
        checkNotTopicSession();
        return createConsumer(queue, selector);
    }

    public QueueSender createSender(final Queue queue) throws JMSException
    {
        checkClosed();
        checkNotTopicSession();
        return createProducer(queue);
    }

    public TopicImpl createTopic(final String s) throws JMSException
    {
        checkClosed();
        checkNotQueueSession();
        return TopicImpl.valueOf(s);
    }

    public TopicSubscriber createSubscriber(final Topic topic) throws JMSException
    {
        checkClosed();
        checkNotQueueSession();
        return createConsumer(topic);
    }

    public TopicSubscriber createSubscriber(final Topic topic, final String selector, final boolean noLocal) throws JMSException
    {
        checkClosed();
        checkNotQueueSession();
        return createConsumer(topic, selector, noLocal);
    }

    public TopicSubscriberImpl createDurableSubscriber(final Topic topic, final String name) throws JMSException
    {
        checkClosed();
        checkNotQueueSession();
        return createDurableSubscriber(topic, name, null, false);
    }

    private void checkNotQueueSession() throws IllegalStateException
    {
        if(_isQueueSession)
        {
            throw new IllegalStateException("Cannot perform this operation on a QueueSession");
        }
    }


    private void checkNotTopicSession() throws IllegalStateException
    {
        if(_isTopicSession)
        {
            throw new IllegalStateException("Cannot perform this operation on a TopicSession");
        }
    }

    public TopicSubscriberImpl createDurableSubscriber(final Topic topic, final String name, final String selector, final boolean noLocal)
            throws JMSException
    {
        checkClosed();
        checkNotQueueSession();
        if(!(topic instanceof TopicImpl))
        {
            throw new InvalidDestinationException("invalid destination " + topic);
        }
        final TopicSubscriberImpl messageConsumer;
        synchronized(_session.getEndpoint().getLock())
        {
            messageConsumer = new TopicSubscriberImpl(name, true, (org.apache.qpid.amqp_1_0.jms.Topic) topic, this,
                                                      selector,
                                                      noLocal);

            if(_dispatcherThread == null)
            {
                _dispatcherThread = new Thread(_dispatcher);
                _dispatcherThread.start();
            }

            addConsumer(messageConsumer);
            if(_connection.isStarted())
            {
                messageConsumer.start();
            }
        }
        return messageConsumer;
    }

    public TopicPublisher createPublisher(final Topic topic) throws JMSException
    {
        checkClosed();
        checkNotQueueSession();
        return createProducer(topic);
    }

    public QueueBrowserImpl createBrowser(final Queue queue) throws JMSException
    {
        checkClosed();
        checkNotTopicSession();
        checkValidDestination(queue);
        return createBrowser(queue, null);
    }

    public QueueBrowserImpl createBrowser(final Queue queue, final String selector) throws JMSException
    {
        checkClosed();
        checkNotTopicSession();
        checkValidDestination(queue);

        return new QueueBrowserImpl((QueueImpl) queue, selector, this);

    }

    public TemporaryQueueImpl createTemporaryQueue() throws JMSException
    {
        checkClosed();
        checkNotTopicSession();
        try
        {
            Sender send = _session.createTemporaryQueueSender();

            TemporaryQueueImpl tempQ = new TemporaryQueueImpl(((Target)send.getTarget()).getAddress(), send, this);
            return tempQ;
        }
        catch (Sender.SenderCreationException e)
        {
            throw new JMSException("Unable to create temporary queue");
        }
        catch (ConnectionClosedException e)
        {
            throw new JMSException("Unable to create temporary queue");
        }
    }

    public TemporaryTopicImpl createTemporaryTopic() throws JMSException
    {
        checkClosed();
        checkNotQueueSession();
        try
        {
            Sender send = _session.createTemporaryQueueSender();

            TemporaryTopicImpl tempQ = new TemporaryTopicImpl(((Target)send.getTarget()).getAddress(), send, this);
            return tempQ;
        }
        catch (Sender.SenderCreationException e)
        {
            throw new JMSException("Unable to create temporary queue");
        }
        catch (ConnectionClosedException e)
        {
            throw new JMSException("Unable to create temporary queue");
        }
    }

    public void unsubscribe(final String s) throws JMSException
    {
        checkClosed();

        checkNotQueueSession();

        Target target = new Target();
        target.setAddress(UUID.randomUUID().toString());

        try
        {
            Receiver receiver = new Receiver(getClientSession(), s, target, null,
                                             org.apache.qpid.amqp_1_0.client.AcknowledgeMode.ALO, false);

            final org.apache.qpid.amqp_1_0.type.Source receiverSource = receiver.getSource();
            if(receiverSource instanceof Source)
            {
                Source source = (Source) receiverSource;
                receiver.close();
                receiver = new Receiver(getClientSession(), s, target, source,
                        org.apache.qpid.amqp_1_0.client.AcknowledgeMode.ALO, false);

            }
            receiver.close();
        }
        catch(ConnectionErrorException  e)
        {
            if(e.getRemoteError().getCondition() == AmqpError.NOT_FOUND)
            {
                throw new InvalidDestinationException(s);
            }
            else
            {
                JMSException jmsException = new JMSException(e.getMessage());
                jmsException.setLinkedException(e);
                throw jmsException;
            }
        }

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

    void acknowledgeAll() throws IllegalStateException
    {
        synchronized(_session.getEndpoint().getLock())
        {
            checkClosed();
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
        else
        {
            if(message instanceof MapMessage)
            {
                replacementMessage = convertMapMessage((MapMessage) message);
            }
            else
            {
                if(message instanceof ObjectMessage)
                {
                    replacementMessage = convertObjectMessage((ObjectMessage) message);
                }
                else
                {
                    if(message instanceof StreamMessage)
                    {
                        replacementMessage = convertStreamMessage((StreamMessage) message);
                    }
                    else
                    {
                        if(message instanceof TextMessage)
                        {
                            replacementMessage = convertTextMessage((TextMessage) message);
                        }
                        else
                        {
                            replacementMessage = createMessage();
                        }
                    }
                }
            }
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

    ConnectionImpl getConnection()
    {
        return _connection;
    }

    Transaction getTxn()
    {
        return _txn;
    }

    public void setMaxPrefetch(final int maxPrefetch)
    {
        _maxPrefetch = maxPrefetch;
    }

    public int getMaxPrefetch()
    {
        return _maxPrefetch;
    }

    private class Dispatcher implements Runnable
    {

        private final List<MessageConsumerImpl> _messageConsumerList = new ArrayList<MessageConsumerImpl>();

        private boolean _closed;
        private boolean _started;

        private Message _recoveredMessage;
        private MessageConsumerImpl _recoveredConsumer;
        private MessageConsumerImpl _currentConsumer;
        private Message _currentMessage;

        public void run()
        {
            synchronized(getLock())
            {

                while(!(_closed || getClientSession().getEndpoint().isEnded()))
                {
                    while(!(_closed || getClientSession().getEndpoint().isEnded()) && (!_started || (_recoveredMessage == null && _messageConsumerList.isEmpty())))
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
                    while(!(_closed || getClientSession().getEndpoint().isEnded()) && (_started && (_recoveredMessage != null || !_messageConsumerList.isEmpty())))
                    {
                        Message msg;

                        MessageConsumerImpl consumer;

                        boolean recoveredMessage =  _recoveredMessage != null;
                        if(recoveredMessage)
                        {
                            consumer = _recoveredConsumer;
                            msg = _recoveredMessage;
                            _recoveredMessage = null;
                            _recoveredConsumer = null;
                        }
                        else
                        {
                            consumer = _messageConsumerList.remove(0);
                            msg = consumer.receiveRecoveredMessage();
                            if(msg == null)
                            {
                                msg = consumer.receive0(0L);
                            }
                            else
                            {
                                recoveredMessage = true;
                            }
                        }

                        MessageListener listener = consumer._messageListener;

                        MessageImpl message = consumer.createJMSMessage(msg, recoveredMessage);

                        if(message != null)
                        {
                            if(_acknowledgeMode == AcknowledgeMode.CLIENT_ACKNOWLEDGE)
                            {
                                consumer.setLastUnackedMessage(msg.getDeliveryTag());
                            }
                            _currentConsumer = consumer;
                            _currentMessage = msg;
                            try
                            {
                                listener.onMessage(message);
                            }
                            finally
                            {
                                _currentConsumer = null;
                                _currentMessage = null;
                            }

                            if(_recoveredMessage == null)
                            {
                                consumer.preReceiveAction(msg);
                            }

                        }

                    }
                    Iterator<MessageConsumerImpl> consumers = _consumers.iterator();
                    while(consumers.hasNext())
                    {
                        MessageConsumerImpl consumer = consumers.next();
                        try
                        {
                            consumer.checkReceiverError();
                        }
                        catch (JMSException e)
                        {

                            consumers.remove();
                            try
                            {
                                ExceptionListener expListener = _connection.getExceptionListener();
                                if (expListener != null)
                                    expListener.onException(e);
                                consumer.close();
                            }
                            catch (JMSException e1)
                            {
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

        public void doRecover()
        {
            _recoveredConsumer = _currentConsumer;
            _recoveredMessage = _currentMessage;
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

    String toAddress(DestinationImpl dest)
    {
        return _connection.toDecodedDestination(dest).getAddress();
    }

}
