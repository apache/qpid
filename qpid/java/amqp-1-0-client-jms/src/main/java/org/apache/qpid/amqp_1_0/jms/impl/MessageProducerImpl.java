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
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import java.util.UUID;

public class MessageProducerImpl implements MessageProducer
{
    private boolean _disableMessageID;
    private boolean _disableMessageTimestamp;
    private int _deliveryMode = Message.DEFAULT_DELIVERY_MODE;
    private int _priority = Message.DEFAULT_PRIORITY;
    private long _timeToLive;

    private DestinationImpl _destination;
    private SessionImpl _session;
    private Sender _sender;

    protected MessageProducerImpl(final Destination destination,
                               final SessionImpl session) throws JMSException
    {
        if(destination instanceof DestinationImpl)
        {
            _destination = (DestinationImpl) destination;
        }
        else if(destination != null)
        {
            // TODO - throw appropriate exception
        }
        _session = session;

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

    public boolean getDisableMessageID()
    {
        return _disableMessageID;
    }

    public void setDisableMessageID(final boolean disableMessageID)
    {
        _disableMessageID = disableMessageID;
    }

    public boolean getDisableMessageTimestamp()
    {
        return _disableMessageTimestamp;
    }

    public void setDisableMessageTimestamp(final boolean disableMessageTimestamp)
    {
        _disableMessageTimestamp = disableMessageTimestamp;
    }

    public int getDeliveryMode()
    {
        return _deliveryMode;
    }

    public void setDeliveryMode(final int deliveryMode)
    {
        _deliveryMode = deliveryMode;
    }

    public int getPriority()
    {
        return _priority;
    }

    public void setPriority(final int priority)
    {
        _priority = priority;
    }

    public long getTimeToLive()
    {
        return _timeToLive;
    }

    public void setTimeToLive(final long timeToLive)
    {
        _timeToLive = timeToLive;
    }

    public DestinationImpl getDestination() throws JMSException
    {
        return _destination;
    }

    public void close() throws JMSException
    {
        //TODO
    }

    public void send(final Message message) throws JMSException
    {
        send(message, getDeliveryMode(), getPriority(), getTimeToLive());
    }

    public void send(final Message message, final int deliveryMode, final int priority, final long ttl) throws JMSException
    {
        //TODO
        MessageImpl msg;
        if(message instanceof org.apache.qpid.amqp_1_0.jms.Message)
        {
            msg = (MessageImpl) message;
        }
        else
        {
            msg = convertMessage(message);
        }

        msg.setJMSDeliveryMode(deliveryMode);
        msg.setJMSPriority(priority);
        if(ttl != 0)
        {
            msg.setTtl(UnsignedInteger.valueOf(ttl));
        }
        else
        {
            msg.setTtl(null);
        }
        msg.setJMSDestination(_destination);
        if(!getDisableMessageTimestamp())
        {
            msg.setJMSTimestamp(System.currentTimeMillis());
        }
        if(!getDisableMessageID() && msg.getMessageId() == null)
        {
            msg.setMessageId(generateMessageId());
        }

        final org.apache.qpid.amqp_1_0.client.Message clientMessage = new org.apache.qpid.amqp_1_0.client.Message(msg.getSections());

        _sender.send(clientMessage);
    }

    private Binary generateMessageId()
    {
        UUID uuid = UUID.randomUUID();        
        return new Binary(uuid.toString().getBytes());
    }

    private MessageImpl convertMessage(final Message message)
    {
        return null;  //TODO
    }

    public void send(final Destination destination, final Message message) throws JMSException
    {
        send(destination, message, getDeliveryMode(), getPriority(), getTimeToLive());
    }

    public void send(final Destination destination, final Message message, final int deliveryMode, final int priority, final long ttl)
            throws JMSException
    {
        //TODO
    }
}
