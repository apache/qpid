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
package org.apache.qpidity.jms.message;

import org.apache.qpidity.ReplyTo;
import org.apache.qpidity.QpidException;

import javax.jms.Message;


public class QpidMessage
{
    /**
     * The underlying qpidity message
     */
    private org.apache.qpidity.api.Message _qpidityMessage;

    //--- This is required as AMQP delivery modes are different from the JMS ones
    public static final short DELIVERY_MODE_PERSISTENT = 2;
    public static final short DELIVERY_MODE_NON_PERSISTENT = 1;
    //--- This is the default message type
    public static final String MESSAGE_TYPE = "JMS Message";

    /**
     * The message properties
     */
    
    /**
     * Get the message ID.
     *
     * @return The message ID
     */
    public String getMessageID()
    {
        return _qpidityMessage.getMessageProperties().getMessageId();
    }

    /**
     * Set the message ID.
     *
     * @param messageID The ID of the message
     */
    protected void setMessageID(String messageID)
    {
        _qpidityMessage.getMessageProperties().setMessageId(messageID);
    }

    /**
     * Get the message timestamp.
     *
     * @return The message timestamp.
     */
    protected long getTimestamp()
    {
        return _qpidityMessage.getDeliveryProperties().getTimestamp();
    }

    /**
     * Set the message timestamp.
     *
     * @param timestamp the timestamp for this message
     */
    protected void setTimestamp(long timestamp)
    {
        _qpidityMessage.getDeliveryProperties().setTimestamp(timestamp);
    }

    /**
     * Set the JMS correlation ID
     *
     * @param correlationID The JMS correlation ID.
     */
    protected void setCorrelationID(String correlationID)
    {
        _qpidityMessage.getMessageProperties().setCorrelationId(correlationID);
    }

    /**
     * Get the JMS correlation ID
     *
     * @return The JMS correlation ID
     */
    protected String getCorrelationID()
    {
        return _qpidityMessage.getMessageProperties().getCorrelationId();
    }

    /**
     * Get the ReplyTo for this message.
     *
     * @return The ReplyTo for this message.
     */
    protected ReplyTo getReplyTo()
    {
        return _qpidityMessage.getMessageProperties().getReplyTo();
    }

    /**
     * Get this message  Delivery mode
     * The delivery mode may be non-persistent (1) or persistent (2)
     *
     * @return the delivery mode of this message.
     */
    protected short getdeliveryMode()
    {
        return _qpidityMessage.getDeliveryProperties().getDeliveryMode();
    }

    /**
     * Set the delivery mode for this message.
     *
     * @param deliveryMode the delivery mode for this message.
     * @throws QpidException If the delivery mode is not supported.
     */
    protected void setDeliveryMode(short deliveryMode) throws QpidException
    {
        if (deliveryMode != DELIVERY_MODE_PERSISTENT && deliveryMode != DELIVERY_MODE_NON_PERSISTENT)
        {
            throw new QpidException(
                    "Problem when setting message delivery mode, " + deliveryMode + " is not a valid mode",
                    "wrong delivery mode", null);
        }
        _qpidityMessage.getDeliveryProperties().setDeliveryMode(deliveryMode);
    }

    /**
     * Get an indication of whether this message is being redelivered.
     *
     * @return true if this message is redelivered, false otherwise.
     */
    protected boolean getRedelivered()
    {
        return _qpidityMessage.getDeliveryProperties().getRedelivered();
    }

    /**
     * Indicate whether this message is being redelivered.
     *
     * @param redelivered true indicates that the message is being redelivered.
     */
    protected void setRedelivered(boolean redelivered)
    {
        _qpidityMessage.getDeliveryProperties().setRedelivered(redelivered);
    }

    /**
     * Get this message type.
     * The default value is {@link QpidMessage#MESSAGE_TYPE}
     *
     * @return This message type.
     */
    protected String getMessageType()
    {
        return _qpidityMessage.getMessageProperties().getType();
    }

    /**
     * Set this message type.
     *
     * @param type The type of message.
     */
    protected void setMessageType(String type)
    {
        _qpidityMessage.getMessageProperties().setType(type);
    }

    /**
     * Get the message's expiration value.
     *
     * @return The message's expiration value.
     */
    protected long getExpiration()
    {
        return _qpidityMessage.getDeliveryProperties().getExpiration();
    }

    /**
     * Set the message's expiration value.
     *
     * @param expiration The message's expiration value.
     */
    protected void setExpiration(long expiration)
    {
        _qpidityMessage.getDeliveryProperties().setExpiration(expiration);
    }

    /**
     * Get the priority for this message.
     *
     * @return The priority for this message.
     */
    protected short getMessagePriority()
    {
        return _qpidityMessage.getDeliveryProperties().getPriority();
    }

    /**
     * Set the priority for this message.
     *
     * @param priority The priority for this message.
     */
    protected void setMessagePriority(short priority)
    {
        _qpidityMessage.getDeliveryProperties().setPriority(priority);
    }



    public Message getJMSMessage()
    {
        // todo
        return null;
    }

}


