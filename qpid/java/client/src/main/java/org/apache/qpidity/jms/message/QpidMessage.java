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
import java.util.Map;
import java.util.Enumeration;
import java.util.Vector;
import java.util.HashMap;
import java.nio.ByteBuffer;


public class QpidMessage
{
    /**
     * The underlying qpidity message
     */
    private org.apache.qpidity.api.Message _qpidityMessage;

    /**
     * This message specific properties.
     */
    private Map<String, Object> _messageProperties;

    /**
     * This message data
     */
    private ByteBuffer _messageData;


    //--- This is required as AMQP delivery modes are different from the JMS ones
    public static final short DELIVERY_MODE_PERSISTENT = 2;
    public static final short DELIVERY_MODE_NON_PERSISTENT = 1;


    //-- Constructors

    /**
     * Constructor used when JMS messages are created by SessionImpl.
     */
    protected QpidMessage()
    {
      // TODO we need an implementation class: _qpidityMessage
        _messageProperties = new HashMap<String, Object>();
    }

    /**
     * Constructor used when a Qpid message is received
     *
     * @param message The received message
     */
     protected QpidMessage(org.apache.qpidity.api.Message message)
    {
       _qpidityMessage = message;
        _messageProperties = (Map<String, Object>) message.getMessageProperties().getApplicationHeaders();
    }

    //---- getters and setters.
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

    /**
     * Clear this messasge specific properties.
     */
    protected void clearMessageProperties()
    {
        _messageProperties.clear();
    }

    /**
     * Access to a message specific property.
     *
     * @param name The property to access.
     * @return The value associated with this property, mull if the value is null or the property does not exist.
     */
    protected Object getProperty(String name)
    {
        return _messageProperties.get(name);
    }

    /**
     * Set a property for this message
     *
     * @param name  The name of the property to set.
     * @param value The value of the rpoperty.
     */
    protected void setProperty(String name, Object value)
    {
        _messageProperties.put(name, value);
    }

    /**
     * Get an Enumeration of all the property names
     *
     * @return An Enumeration of all the property names.
     */
    protected Enumeration<String> getAllPropertyNames()
    {
        Vector<String> vec = new Vector<String>(_messageProperties.keySet());
        return vec.elements();
    }

    /**
     * Set this message body
     *
     * @param messageBody The buffer containing this message data
     */
    protected void setMessageData(ByteBuffer messageBody)
    {
        _messageData = messageBody;
    }

    /**
     * Access this messaage data.
     *
     * @return This message data.
     */
    protected ByteBuffer getMessageData()
    {
        return _messageData;
    }

    /**
     * Clear this message data
     */
    protected void clearMessageData()
    {
        _messageData = ByteBuffer.allocate(1024);        
    }

}


