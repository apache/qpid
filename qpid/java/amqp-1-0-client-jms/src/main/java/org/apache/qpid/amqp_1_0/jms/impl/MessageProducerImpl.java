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

import org.apache.qpid.amqp_1_0.client.Sender;
import org.apache.qpid.amqp_1_0.jms.MessageProducer;
import org.apache.qpid.amqp_1_0.jms.Queue;
import org.apache.qpid.amqp_1_0.jms.QueueSender;
import org.apache.qpid.amqp_1_0.jms.TopicPublisher;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;

import javax.jms.*;
import javax.jms.IllegalStateException;
import java.util.UUID;

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

        if(_destination != null)
        {
            try
            {
                _sender = _session.getClientSession().createSender(_destination.getAddress());
            }
            catch (Sender.SenderCreationException e)
            {
                // TODO - refine exception
                JMSException jmsEx = new JMSException(e.getMessage());
                jmsEx.initCause(e);
                jmsEx.setLinkedException(e);
                throw jmsEx;
            }
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

        if(!getDisableMessageTimestamp() && ttl==0)
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
            final Binary messageId = generateMessageId();
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

        _sender.send(clientMessage);

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

    private Binary generateMessageId()
    {
        UUID uuid = UUID.randomUUID();
        return new Binary(uuid.toString().getBytes());
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
            try
            {
                _destination = (DestinationImpl) destination;
                _sender = _session.getClientSession().createSender(_destination.getAddress());

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
}
