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

import org.apache.qpid.amqp_1_0.client.*;
import org.apache.qpid.amqp_1_0.client.Session;
import org.apache.qpid.amqp_1_0.jms.MessageProducer;
import org.apache.qpid.amqp_1_0.jms.MessageRejectedException;
import org.apache.qpid.amqp_1_0.jms.QueueSender;
import org.apache.qpid.amqp_1_0.jms.TemporaryDestination;
import org.apache.qpid.amqp_1_0.jms.TopicPublisher;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Outcome;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;

import javax.jms.*;
import javax.jms.IllegalStateException;
import javax.jms.Message;
import java.util.UUID;
import org.apache.qpid.amqp_1_0.type.messaging.Accepted;
import org.apache.qpid.amqp_1_0.type.messaging.Rejected;
import org.apache.qpid.amqp_1_0.type.messaging.Source;
import org.apache.qpid.amqp_1_0.type.messaging.codec.AcceptedConstructor;
import org.apache.qpid.amqp_1_0.type.messaging.codec.RejectedConstructor;
import org.apache.qpid.amqp_1_0.type.transport.Error;

public class MessageProducerImpl implements MessageProducer, QueueSender, TopicPublisher
{
    private boolean _disableMessageID;
    private boolean _disableMessageTimestamp;
    private int _deliveryMode = Message.DEFAULT_DELIVERY_MODE;
    private int _priority = Message.DEFAULT_PRIORITY;
    private long _timeToLive;

    private DestinationImpl _destination;
    private SessionImpl _session;
    private Sender _sender;
    private boolean _closed;
    private boolean _syncPublish = Boolean.getBoolean("qpid.sync_publish");
    private long _syncPublishTimeout = Long.getLong("qpid.sync_publish_timeout", 30000l);

    protected MessageProducerImpl(final Destination destination,
                               final SessionImpl session) throws JMSException
    {
        if(destination instanceof DestinationImpl)
        {
            _destination = (DestinationImpl) destination;
        }
        else if(destination != null)
        {
            throw new InvalidDestinationException("Invalid Destination Class" + destination.getClass().getName());
        }

        _session = session;
        _syncPublish = session.getConnection().syncPublish();

        if(_destination != null)
        {
            try
            {
                _sender = _session.getClientSession().createSender(_session.toAddress(_destination), new Session.SourceConfigurator()
                {
                    public void configureSource(final Source source)
                    {
                        source.setDefaultOutcome(new Accepted());
                        source.setOutcomes(AcceptedConstructor.SYMBOL_CONSTRUCTOR, RejectedConstructor.SYMBOL_CONSTRUCTOR);
                    }
                });
            }
            catch (Sender.SenderCreationException e)
            {
                // TODO - refine exception
                JMSException jmsEx = new JMSException(e.getMessage());
                jmsEx.initCause(e);
                jmsEx.setLinkedException(e);
                throw jmsEx;
            }
            catch (ConnectionClosedException e)
            {

                // TODO - refine exception
                JMSException jmsEx = new JMSException(e.getMessage());
                jmsEx.initCause(e);
                jmsEx.setLinkedException(e);
                throw jmsEx;
            }
            _sender.setRemoteErrorListener(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            try
                            {
                                final ExceptionListener exceptionListener = _session.getConnection().getExceptionListener();

                                if(exceptionListener != null)
                                {
                                    final org.apache.qpid.amqp_1_0.type.transport.Error receiverError = _sender.getError();
                                    exceptionListener.onException(new JMSException(receiverError.getDescription(),
                                            receiverError.getCondition().getValue().toString()));

                                }
                            }
                            catch (JMSException e)
                            {

                            }
                        }
                    });
        }
    }

    private void checkClosed() throws IllegalStateException
    {
        if(_closed)
        {
            throw new javax.jms.IllegalStateException("Producer closed");
        }
    }

    public boolean getDisableMessageID() throws IllegalStateException
    {
        checkClosed();
        return _disableMessageID;
    }

    public void setDisableMessageID(final boolean disableMessageID) throws IllegalStateException
    {
        checkClosed();
        _disableMessageID = disableMessageID;
    }

    public boolean getDisableMessageTimestamp() throws IllegalStateException
    {
        checkClosed();
        return _disableMessageTimestamp;
    }

    public void setDisableMessageTimestamp(final boolean disableMessageTimestamp) throws IllegalStateException
    {
        checkClosed();
        _disableMessageTimestamp = disableMessageTimestamp;
    }

    public int getDeliveryMode() throws IllegalStateException
    {
        checkClosed();
        return _deliveryMode;
    }

    public void setDeliveryMode(final int deliveryMode) throws IllegalStateException
    {
        checkClosed();
        _deliveryMode = deliveryMode;
    }

    public int getPriority() throws IllegalStateException
    {
        checkClosed();
        return _priority;
    }

    public void setPriority(final int priority) throws IllegalStateException
    {
        checkClosed();
        _priority = priority;
    }

    public long getTimeToLive() throws IllegalStateException
    {
        checkClosed();
        return _timeToLive;
    }

    public void setTimeToLive(final long timeToLive) throws IllegalStateException
    {
        checkClosed();
        _timeToLive = timeToLive;
    }

    public DestinationImpl getDestination() throws JMSException
    {
        checkClosed();
        return _destination;
    }

    public void close() throws JMSException
    {
        try
        {
            if(!_closed)
            {
                _closed = true;
                if(_sender != null)
                {
                    _sender.close();
                }
            }

        }
        catch (Sender.SenderClosingException e)
        {
            final JMSException jmsException = new JMSException("error closing");
            jmsException.setLinkedException(e);
            throw jmsException;
        }
    }

    public void send(final Message message) throws JMSException
    {
        send(message, getDeliveryMode(), getPriority(), getTimeToLive());
    }

    public void send(final Message message, final int deliveryMode, final int priority, final long ttl) throws JMSException
    {
        if(_sender == null)
        {
            throw new UnsupportedOperationException("No Destination provided");
        }
        if(_destination instanceof TemporaryDestination && ((TemporaryDestination)_destination).isDeleted())
        {
            throw new IllegalStateException("Destination is deleted");
        }


        //TODO
        MessageImpl msg;
        if(message instanceof org.apache.qpid.amqp_1_0.jms.Message)
        {
            msg = (MessageImpl) message;
        }
        else
        {
            msg = _session.convertMessage(message);
        }



        msg.setJMSDeliveryMode(deliveryMode);
        msg.setJMSPriority(priority);

        msg.setJMSDestination(_destination);

        long timestamp = 0l;

        if(!getDisableMessageTimestamp() || ttl != 0)
        {
            timestamp = System.currentTimeMillis();
            msg.setJMSTimestamp(timestamp);

        }
        if(ttl != 0)
        {
            msg.setTtl(UnsignedInteger.valueOf(ttl));
        }
        else
        {
            msg.setTtl(null);
        }

        if(!getDisableMessageID() && msg.getMessageId() == null)
        {
            final Object messageId = generateMessageId();
            msg.setMessageId(messageId);

        }

        if(message != msg)
        {
            message.setJMSTimestamp(msg.getJMSTimestamp());
            message.setJMSMessageID(msg.getJMSMessageID());
            message.setJMSDeliveryMode(msg.getJMSDeliveryMode());
            message.setJMSPriority(msg.getJMSPriority());
            message.setJMSExpiration(msg.getJMSExpiration());
        }


        final org.apache.qpid.amqp_1_0.client.Message clientMessage = new org.apache.qpid.amqp_1_0.client.Message(msg.getSections());

        DispositionAction action = null;

        if(_syncPublish)
        {
            action = new DispositionAction(_sender);
        }

        try
        {
            _sender.send(clientMessage, _session.getTxn(), action);
        }
        catch (LinkDetachedException e)
        {
            JMSException jmsException = new InvalidDestinationException("Sender has been closed");
            jmsException.setLinkedException(e);
            throw jmsException;
        }

        if(_syncPublish && !action.wasAccepted(_syncPublishTimeout))
        {
            if (action.getOutcome() instanceof Rejected)
            {
                Error err = ((Rejected) action.getOutcome()).getError();
                if(err != null)
                {
                    throw new MessageRejectedException(err.getDescription(), err.getCondition().toString());
                }
                else
                {
                    throw new MessageRejectedException("Message was rejected: " + action.getOutcome());
                }
            }
            else
            {
                throw new MessageRejectedException("Message was not accepted.  Outcome was: " + action.getOutcome());
            }
        }

        if(getDestination() != null)
        {
            message.setJMSDestination(getDestination());
        }
    }

    public void send(final javax.jms.Queue queue, final Message message) throws JMSException
    {
        send((Destination)queue, message);
    }

    public void send(final javax.jms.Queue queue, final Message message, final int deliveryMode, final int priority, final long ttl)
            throws JMSException
    {
        send((Destination)queue, message, deliveryMode, priority, ttl);
    }

    private Object generateMessageId()
    {
        UUID uuid = UUID.randomUUID();
        final String messageIdString = uuid.toString();
        return _session.getConnection().useBinaryMessageId() ? new Binary(messageIdString.getBytes()) : messageIdString;
    }

    public void send(final Destination destination, final Message message) throws JMSException
    {
        send(destination, message, getDeliveryMode(), getPriority(), getTimeToLive());
    }

    public void send(final Destination destination, final Message message, final int deliveryMode, final int priority, final long ttl)
            throws JMSException
    {

        checkClosed();
        if(destination == null)
        {
            send(message, deliveryMode, priority, ttl);
        }
        else
        {
            if(_destination != null)
            {
                throw new UnsupportedOperationException("Cannot use explicit destination pon non-anonymous producer");
            }
            else if(!(destination instanceof DestinationImpl))
            {
                throw new InvalidDestinationException("Invalid Destination Class" + destination.getClass().getName());
            }
            else if(destination instanceof TemporaryDestination && ((TemporaryDestination)destination).isDeleted())
            {
                throw new IllegalStateException("Destination has been deleted");
            }
            try
            {
                _destination = (DestinationImpl) destination;
                _sender = _session.getClientSession().createSender(_session.toAddress(_destination));

                send(message, deliveryMode, priority, ttl);

                _sender.close();



            }
            catch (Sender.SenderCreationException e)
            {
                // TODO - refine exception
                JMSException jmsEx = new JMSException(e.getMessage());
                jmsEx.initCause(e);
                jmsEx.setLinkedException(e);
                throw jmsEx;
            }
            catch (Sender.SenderClosingException e)
            {
                JMSException jmsEx = new JMSException(e.getMessage());
                jmsEx.initCause(e);
                jmsEx.setLinkedException(e);
                throw jmsEx;
            }
            catch (ConnectionClosedException e)
            {

                JMSException jmsEx = new JMSException(e.getMessage());
                jmsEx.initCause(e);
                jmsEx.setLinkedException(e);
                throw jmsEx;
            }
            finally
            {
                _sender = null;
                _destination = null;
            }
        }
    }

    public QueueImpl getQueue() throws JMSException
    {
        return (QueueImpl) getDestination();
    }

    public TopicImpl getTopic() throws JMSException
    {
        return (TopicImpl) getDestination();
    }

    public void publish(final Message message) throws JMSException
    {
        send(message);
    }

    public void publish(final Message message, final int deliveryMode, final int priority, final long ttl) throws JMSException
    {
        send(message, deliveryMode, priority, ttl);
    }

    public void publish(final Topic topic, final Message message) throws JMSException
    {
        send(topic, message);
    }

    public void publish(final Topic topic, final Message message, final int deliveryMode, final int priority, final long ttl)
            throws JMSException
    {
        send(topic, message, deliveryMode, priority, ttl);
    }

    private static class DispositionAction implements Sender.OutcomeAction
    {
        private final Sender _sender;
        private final Object _lock;
        private Outcome _outcome;

        public DispositionAction(Sender sender)
        {
            _sender = sender;
            _lock = sender.getEndpoint().getLock();
        }

        @Override
        public void onOutcome(Binary deliveryTag, Outcome outcome)
        {
            synchronized (_lock)
            {
                _outcome = outcome;
                _lock.notifyAll();
            }
        }

        public boolean wasAccepted(long timeout) throws JMSException
        {
            synchronized(_lock)
            {
                while(_outcome == null && !_sender.getEndpoint().isDetached())
                {
                    try
                    {
                        _lock.wait(timeout);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    }
                }
                if(_outcome == null)
                {

                    if(_sender.getEndpoint().isDetached())
                    {
                        throw new JMSException("Link was detached");
                    }
                    else
                    {
                        throw new JMSException("Timed out waiting for message acceptance");
                    }
                }
                else
                {
                    return _outcome instanceof Accepted;
                }
            }
        }

        Outcome getOutcome()
        {
            return _outcome;
        }
    }
}
