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

import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.io.IOException;

import org.apache.qpidity.ErrorCode;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.ReplyTo;
import org.apache.qpidity.client.util.ByteBufferMessage;


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
        // We us a byteBufferMessage as default
        _qpidityMessage = new ByteBufferMessage();
        System.out.println("Creating a bytes message");
        _messageProperties = new HashMap<String, Object>();
        // This is a newly created messsage so the data is empty
        _messageData = ByteBuffer.allocate(1024);
    }

    /**
     * Constructor used when a Qpid message is received
     *
     * @param message The received message.
     * @throws QpidException In case of problem when receiving the message body.
     */
    protected QpidMessage(org.apache.qpidity.api.Message message) throws QpidException
    {
        try
        {
            _qpidityMessage = message;
            _messageProperties = (Map<String, Object>) message.getMessageProperties().getApplicationHeaders();
            _messageData = _qpidityMessage.readData();
        }
        catch (IOException ioe)
        {
            throw new QpidException("IO problem when creating message", ErrorCode.UNDEFINED, ioe);
        }
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
     * Set the ReplyTo for this message.
     *
     * @param replyTo The ReplyTo for this message.
     */
    protected void setReplyTo(ReplyTo replyTo)
    {
        _qpidityMessage.getMessageProperties().setReplyTo(replyTo);
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
                    ErrorCode.UNDEFINED, null);
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
        _messageData = messageBody.duplicate();
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

    /**
     * Set this message AMQP routingkey
     *
     * @param routingKey This message AMQP routingkey
     */
    public void setRoutingKey(String routingKey)
    {
        _qpidityMessage.getDeliveryProperties().setRoutingKey(routingKey);
    }

    /**
     * Set this message AMQP exchange name.
     *
     * @param exchangeName This message AMQP exchange name.
     */
    public void setExchangeName(String exchangeName)
    {
        _qpidityMessage.getDeliveryProperties().setExchange(exchangeName);
    }

    /**
     * Get this message excahgne name
     *
     * @return this message excahgne name
     */
    public String getExchangeName()
    {
        return _qpidityMessage.getDeliveryProperties().getExchange();
    }

    /**
     * This method is invoked before a message dispatch operation.
     *
     * @throws QpidException If the destination is not set
     */
    public void beforeMessageDispatch() throws QpidException
    {
        try
        {
            // set the message data
            _qpidityMessage.clearData();
            // we need to do a flip
            //_messageData.flip();
            
            System.out.println("_messageData POS " + _messageData.position());
            System.out.println("_messageData limit " + _messageData.limit());
            
            _qpidityMessage.appendData(_messageData);
            _qpidityMessage.getMessageProperties().setApplicationHeaders(_messageProperties);
        }
        catch (IOException e)
        {
            throw new QpidException("IO exception when sending message", ErrorCode.UNDEFINED, e);
        }
    }

    /**
     * Get the underlying qpidity message
     *
     * @return The underlying qpidity message.
     */
    public org.apache.qpidity.api.Message getQpidityMessage()
    {
        return _qpidityMessage;
    }

    /**
     * Get this message transfer ID.
     *
     * @return This message transfer ID.
     */
    public long getMessageTransferId()
    {
        return _qpidityMessage.getMessageTransferId();
    }
}


