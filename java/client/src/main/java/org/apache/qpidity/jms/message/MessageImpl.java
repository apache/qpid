/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpidity.jms.message;

import org.apache.qpidity.jms.ExceptionHelper;
import org.apache.qpidity.jms.MessageConsumerImpl;
import org.apache.qpidity.QpidException;

import javax.jms.*;
import java.util.Enumeration;

/**
 * Implementation of javax.jms.Message
 */
public class MessageImpl extends QpidMessage implements Message
{
    /**
     * name used to store JMSType.
     */
    private static final String JMS_MESSAGE_TYPE = "JMSType";

    /**
     * The ReplyTo destination for this message
     */
    private Destination _replyTo;

    /**
     * The destination to which the message has been sent.
     * <p>When a message is sent this value is ignored. After completion
     * of the send method it holds the destination specified by the send.
     * <p>When a message is received, its destination value must be
     * equivalent to the value assigned when it was sent.
     */
    private Destination _destination;

    /**
     * Indicates whether the message properties are in writeable status.
     */
    protected boolean _readOnly = false;

    /**
     * Indicate whether the message properties are in writeable status.
     */
    protected boolean _proertiesReadOnly = false;

    /**
     * The message consumer through which this message was received.
     */
    private MessageConsumerImpl _messageConsumer;

    //--- Constructor
    /**
     * Constructor used by SessionImpl.
     */
    public MessageImpl()
    {
        super();
        setMessageType(String.valueOf(MessageFactory.JAVAX_JMS_MESSAGE));
    }

    /**
     * Constructor used by MessageFactory
     *
     * @param message The new qpid message.
     * @throws QpidException In case of IO problem when reading the received message.
     */
    protected MessageImpl(org.apache.qpidity.api.Message message) throws QpidException
    {
        super(message);
    }

    //---- javax.jms.Message interface
    /**
     * Get the message ID.
     * <p> The JMS sprec says:
     * <p>The messageID header field contains a value that uniquely
     * identifies each message sent by a provider.
     * <p>When a message is sent, messageID can be ignored. When
     * the send method returns it contains a provider-assigned value.
     * <P>All JMSMessageID values must start with the prefix `ID:'.
     * Uniqueness of message ID values across different providers is
     * not required.
     *
     * @return The message ID
     * @throws JMSException If getting the message Id fails due to internal error.
     */
    public String getJMSMessageID() throws JMSException
    {
        String messageID = super.getMessageID();

        if (messageID != null)
        {
            messageID = "ID:" + messageID;
        }
        return messageID;
    }

    /**
     * Set the message ID.
     * <p> The JMS spec says:
     * <P>Providers set this field when a message is sent. This operation
     * can be used to change the value of a message that's been received.
     *
     * @param messageID The ID of the message
     * @throws JMSException If setting the message Id fails due to internal error.
     */
    public void setJMSMessageID(String messageID) throws JMSException
    {
        String qpidmessageID = null;
        if (messageID != null)
        {
            if (messageID.substring(0, 3).equals("ID:"))
            {
                qpidmessageID = messageID.substring(3, messageID.length());
            }
        }
        super.setMessageID(qpidmessageID);
    }

    /**
     * Get the message timestamp.
     * <p> The JMS sepc says:
     * <P>The JMSTimestamp header field contains the time a message was
     * handed off to a provider to be sent. It is not the time the
     * message was actually transmitted because the actual send may occur
     * later due to transactions or other client side queueing of messages.
     * <P>When a message is sent, JMSTimestamp is ignored. When the send
     * method returns it contains a a time value somewhere in the interval
     * between the call and the return. It is in the format of a normal
     * Java millis time value.
     * <P>Since timestamps take some effort to create and increase a
     * message's size, some JMS providers may be able to optimize message
     * overhead if they are given a hint that timestamp is not used by an
     * application. JMS message Producers provide a hint to disable
     * timestamps. When a client sets a producer to disable timestamps
     * they are saying that they do not depend on the value of timestamp
     * for the messages it produces. These messages must either have
     * timestamp set to null or, if the hint is ignored, timestamp must
     * be set to its normal value.
     *
     * @return the message timestamp
     * @throws JMSException If getting the Timestamp fails due to internal error.
     */
    public long getJMSTimestamp() throws JMSException
    {
        return super.getTimestamp();
    }

    /**
     * Set the message timestamp.
     * <p> The JMS spec says:
     * <P>Providers set this field when a message is sent. This operation
     * can be used to change the value of a message that's been received.
     *
     * @param timestamp The timestamp for this message
     * @throws JMSException If setting the timestamp fails due to some internal error.
     */
    public void setJMSTimestamp(long timestamp) throws JMSException
    {
        super.setTimestamp(timestamp);
    }

    /**
     * Get the correlation ID as an array of bytes for the message.
     * <p> JMS spec says:
     * <P>The use of a byte[] value for JMSCorrelationID is non-portable.
     *
     * @return the correlation ID of a message as an array of bytes.
     * @throws JMSException If getting correlationId fails due to some internal error.
     */
    public byte[] getJMSCorrelationIDAsBytes() throws JMSException
    {
        String correlationID = getJMSCorrelationID();
        if (correlationID != null)
        {
            return correlationID.getBytes();
        }
        return null;
    }

    /**
     * Set the correlation ID as an array of bytes for the message.
     * <p> JMS spec says:
     * <P>If a provider supports the native concept of correlation id, a
     * JMS client may need to assign specific JMSCorrelationID values to
     * match those expected by non-JMS clients. JMS providers without native
     * correlation id values are not required to support this (and the
     * corresponding get) method; their implementation may throw
     * java.lang.UnsupportedOperationException).
     * <P>The use of a byte[] value for JMSCorrelationID is non-portable.
     *
     * @param correlationID The correlation ID value as an array of bytes.
     * @throws JMSException If setting correlationId fails due to some internal error.
     */
    public void setJMSCorrelationIDAsBytes(byte[] correlationID) throws JMSException
    {
        setJMSCorrelationID(new String(correlationID));
    }

    /**
     * Set the correlation ID for the message.
     * <p> JMS spec says:
     * <P>A client can use the JMSCorrelationID header field to link one
     * message with another. A typically use is to link a response message
     * with its request message.
     * <P>Since each message sent by a JMS provider is assigned a message ID
     * value it is convenient to link messages via message ID. All message ID
     * values must start with the `ID:' prefix.
     * <P>In some cases, an application (made up of several clients) needs to
     * use an application specific value for linking messages. For instance,
     * an application may use JMSCorrelationID to hold a value referencing
     * some external information. Application specified values must not start
     * with the `ID:' prefix; this is reserved for provider-generated message
     * ID values.
     *
     * @param correlationID The message ID of a message being referred to.
     * @throws JMSException If setting the correlationId fails due to some internal error.
     */
    public void setJMSCorrelationID(String correlationID) throws JMSException
    {
        super.setCorrelationID(correlationID);

    }

    /**
     * Get the correlation ID for the message.
     *
     * @return The correlation ID of a message as a String.
     * @throws JMSException If getting the correlationId fails due to some internal error.
     */
    public String getJMSCorrelationID() throws JMSException
    {
        return super.getCorrelationID();
    }

    /**
     * Get where a reply to this message should be sent.
     *
     * @return The destination where a reply to this message should be sent.
     * @throws JMSException If getting the ReplyTo Destination fails due to some internal error.
     */
    public Destination getJMSReplyTo() throws JMSException
    {
        return _replyTo;
    }

    /**
     * Set where a reply to this message should be sent.
     * <p> The JMS spec says:
     * <P>The replyTo header field contains the destination where a reply
     * to the current message should be sent. If it is null no reply is
     * expected. The destination may be either a Queue or a Topic.
     * <P>Messages with a null replyTo value are called JMS datagrams.
     * Datagrams may be a notification of some change in the sender (i.e.
     * they signal a sender event) or they may just be some data the sender
     * thinks is of interest.
     * <p> Messages with a replyTo value are typically expecting a response.
     * A response may be optional, it is up to the client to decide. These
     * messages are called JMS requests. A message sent in response to a
     * request is called a reply.
     *
     * @param destination The destination where a reply to this message should be sent.
     * @throws JMSException If setting the ReplyTo Destination fails due to some internal error.
     */
    public void setJMSReplyTo(Destination destination) throws JMSException
    {
        _replyTo = destination;
    }

    /**
     * Get the destination for this message.
     * <p> The JMS spec says:
     * <p>The destination field contains the destination to which the
     * message is being sent.
     * <p>When a message is sent this value is ignored. After completion
     * of the send method it holds the destination specified by the send.
     * <p>When a message is received, its destination value must be
     * equivalent to the value assigned when it was sent.
     *
     * @return The destination of this message.
     * @throws JMSException If getting the JMS Destination fails due to some internal error.
     */
    public Destination getJMSDestination() throws JMSException
    {
        return _destination;
    }

    /**
     * Set the destination for this message.
     * <p: JMS spec says:
     * <p>Providers set this field when a message is sent. This operation
     * can be used to change the value of a message that's been received.
     *
     * @param destination The destination this message has been sent.
     * @throws JMSException If setting the JMS Destination fails due to some internal error.
     */
    public void setJMSDestination(Destination destination) throws JMSException
    {
        _destination = destination;
    }

    /**
     * Get the delivery mode for this message.
     *
     * @return the delivery mode of this message.
     * @throws JMSException If getting the JMS DeliveryMode fails due to some internal error.
     */
    public int getJMSDeliveryMode() throws JMSException
    {
        int result = DeliveryMode.NON_PERSISTENT;
        short amqpDeliveryMode = super.getdeliveryMode();
        if (amqpDeliveryMode == QpidMessage.DELIVERY_MODE_PERSISTENT)
        {
            result = DeliveryMode.PERSISTENT;
        }
        else if (amqpDeliveryMode != DELIVERY_MODE_NON_PERSISTENT)
        {
            throw new JMSException("Problem when accessing message delivery mode");
        }
        return result;
    }

    /**
     * Set the delivery mode for this message.
     * <p> The JMS spec says:
     * <p>Providers set this field when a message is sent. This operation
     * can be used to change the value of a message that's been received.
     *
     * @param deliveryMode the delivery mode for this message.
     * @throws JMSException If setting the JMS DeliveryMode fails due to some internal error.
     */
    public void setJMSDeliveryMode(int deliveryMode) throws JMSException
    {
        short amqpDeliveryMode = DELIVERY_MODE_PERSISTENT;
        if (deliveryMode == DeliveryMode.NON_PERSISTENT)
        {
            amqpDeliveryMode = DELIVERY_MODE_NON_PERSISTENT;
        }
        else if (deliveryMode != DeliveryMode.PERSISTENT)
        {
            throw new JMSException(
                    "Problem when setting message delivery mode, " + deliveryMode + " is not a valid mode");
        }
        try
        {
            super.setDeliveryMode(amqpDeliveryMode);
        }
        catch (QpidException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    /**
     * Get an indication of whether this message is being redelivered.
     * <p> The JMS spec says:
     * <p>If a client receives a message with the redelivered indicator set,
     * it is likely, but not guaranteed, that this message was delivered to
     * the client earlier but the client did not acknowledge its receipt at
     * that earlier time.
     *
     * @return true if this message is being redelivered, false otherwise
     * @throws JMSException If getting the JMS Redelivered fails due to some internal error.
     */
    public boolean getJMSRedelivered() throws JMSException
    {
        return super.getRedelivered();
    }

    /**
     * Indicate whether this message is being redelivered.
     * <p> The JMS spec says:
     * <p>This field is set at the time the message is delivered. This
     * operation can be used to change the value of a message that's
     * been received.
     *
     * @param redelivered true indicates that the message is being redelivered.
     * @throws JMSException If setting the JMS Redelivered fails due to some internal error.
     */
    public void setJMSRedelivered(boolean redelivered) throws JMSException
    {
        super.setRedelivered(redelivered);
    }

    /**
     * Get the message type.
     * <p> The JMS spec says:
     * <p>Some JMS providers use a message repository that contains the
     * definition of messages sent by applications. The type header field
     * contains the name of a message's definition.
     * <p>JMS does not define a standard message definition repository nor
     * does it define a naming policy for the definitions it contains. JMS
     * clients should use symbolic values for type that can be configured
     * at installation time to the values defined in the current providers
     * message repository.
     * <p>JMS clients should assign a value to type whether the application
     * makes use of it or not. This insures that it is properly set for
     * those providers that require it.
     *
     * @return The message type
     * @throws JMSException If getting the  JMS message type fails due to some internal error.
     */
    public String getJMSType() throws JMSException
    {
        return getStringProperty(JMS_MESSAGE_TYPE);
    }

    /**
     * Set this message type.
     *
     * @param type The type of message.
     * @throws JMSException If setting the JMS message type fails due to some internal error.
     */
    public void setJMSType(String type) throws JMSException
    {
        if (type == null)
        {
            throw new JMSException("Invalid message type null");
        }
        else
        {
            super.setProperty(JMS_MESSAGE_TYPE, type);
        }
    }

    /**
     * Get the message's expiration value.
     * <p> The JMS spec says:
     * <p>When a message is sent, expiration is left unassigned. After
     * completion of the send method, it holds the expiration time of the
     * message. This is the sum of the time-to-live value specified by the
     * client and the GMT at the time of the send.
     * <p>If the time-to-live is specified as zero, expiration is set to
     * zero which indicates the message does not expire.
     *
     * @return The time the message expires.
     * @throws JMSException If getting the JMS message expiration fails due to some internal error.
     */
    public long getJMSExpiration() throws JMSException
    {
        return super.getExpiration();
    }

    /**
     * Set the message's expiration value.
     *
     * @param expiration the message's expiration time
     * @throws JMSException If setting the JMS message expiration fails due to some internal error.
     */
    public void setJMSExpiration(long expiration) throws JMSException
    {
        super.setExpiration(expiration);
    }


    /**
     * Get the message priority.
     * <p> The JMS spec says:
     * <p>JMS defines a ten level priority value with 0 as the lowest
     * priority and 9 as the highest. In addition, clients should consider
     * priorities 0-4 as gradations of normal priority and priorities 5-9
     * as gradations of expedited priority.
     *
     * @return The message priority.
     * @throws JMSException If getting the JMS message priority fails due to some internal error.
     */
    public int getJMSPriority() throws JMSException
    {
        return super.getMessagePriority();
    }

    /**
     * Set the priority for this message.
     *
     * @param priority The priority of this message.
     * @throws JMSException If setting the JMS message priority fails due to some internal error.
     */
    public void setJMSPriority(int priority) throws JMSException
    {
        super.setMessagePriority((short) priority);
    }

    /**
     * Clear the message's properties.
     * <p/>
     * The message header fields and body are not cleared.
     *
     * @throws JMSException if clearing  JMS message properties fails due to some internal error.
     */
    public void clearProperties() throws JMSException
    {
        // The properties can now be written
        // Properties are read only when the message is received.
        _proertiesReadOnly = false;
        super.clearMessageProperties();
    }

    /**
     * Indicates whether a property value exists.
     *
     * @param name The name of the property to test the existence
     * @return True if the property exists, false otherwise.
     * @throws JMSException if checking if the property exists fails due to some internal error.
     */
    public boolean propertyExists(String name) throws JMSException
    {
        // Access the property; if the result is null,
        // then the property value does not exist
        return (super.getProperty(name) != null);
    }

    /**
     * Access a boolean property value with the given name.
     *
     * @param name The name of the boolean property.
     * @return The boolean property value with the given name.
     * @throws JMSException           if getting the boolean property fails due to some internal error.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public boolean getBooleanProperty(String name) throws JMSException
    {
        Object booleanProperty = getObjectProperty(name);
        return booleanProperty != null && MessageHelper.convertToBoolean(booleanProperty);
    }

    /**
     * Access a byte property value with the given name.
     *
     * @param name The name of the byte property.
     * @return The byte property value with the given name.
     * @throws JMSException           if getting the byte property fails due to some internal error.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public byte getByteProperty(String name) throws JMSException
    {
        Object byteProperty = getObjectProperty(name);
        if (byteProperty == null)
        {
            throw new NumberFormatException("Proerty " + name + " is null");
        }
        else
        {
            return MessageHelper.convertToByte(byteProperty);
        }
    }

    /**
     * Access a short property value with the given name.
     *
     * @param name The name of the short property.
     * @return The short property value with the given name.
     * @throws JMSException           if getting the short property fails due to some internal error.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public short getShortProperty(String name) throws JMSException
    {
        Object shortProperty = getObjectProperty(name);
        if (shortProperty == null)
        {
            throw new NumberFormatException("Proerty " + name + " is null");
        }
        else
        {
            return MessageHelper.convertToShort(shortProperty);
        }
    }

    /**
     * Access a int property value with the given name.
     *
     * @param name The name of the int property.
     * @return The int property value with the given name.
     * @throws JMSException           if getting the int property fails due to some internal error.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public int getIntProperty(String name) throws JMSException
    {
        Object intProperty = getObjectProperty(name);
        if (intProperty == null)
        {
            throw new NumberFormatException("Proerty " + name + " is null");
        }
        else
        {
            return MessageHelper.convertToInt(intProperty);
        }
    }

    /**
     * Access a long property value with the given name.
     *
     * @param name The name of the long property.
     * @return The long property value with the given name.
     * @throws JMSException           if getting the long property fails due to some internal error.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public long getLongProperty(String name) throws JMSException
    {
        Object longProperty = getObjectProperty(name);
        if (longProperty == null)
        {
            throw new NumberFormatException("Proerty " + name + " is null");
        }
        else
        {
            return MessageHelper.convertToLong(longProperty);
        }
    }

    /**
     * Access a long property value with the given name.
     *
     * @param name The name of the long property.
     * @return The long property value with the given name.
     * @throws JMSException           if getting the long property fails due to some internal error.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public float getFloatProperty(String name) throws JMSException
    {
        Object floatProperty = getObjectProperty(name);
        if (floatProperty == null)
        {
            throw new NumberFormatException("Proerty " + name + " is null");
        }
        else
        {
            return MessageHelper.convertToFloat(floatProperty);
        }
    }

    /**
     * Access a double property value with the given name.
     *
     * @param name The name of the double property.
     * @return The double property value with the given name.
     * @throws JMSException           if getting the double property fails due to some internal error.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public double getDoubleProperty(String name) throws JMSException
    {
        Object doubleProperty = getObjectProperty(name);
        if (doubleProperty == null)
        {
            throw new NumberFormatException("Proerty " + name + " is null");
        }
        else
        {
            return MessageHelper.convertToDouble(doubleProperty);
        }
    }

    /**
     * Access a String property value with the given name.
     *
     * @param name The name of the String property.
     * @return The String property value with the given name.
     * @throws JMSException           if getting the String property fails due to some internal error.
     * @throws MessageFormatException If this type conversion is invalid.
     */
    public String getStringProperty(String name) throws JMSException
    {
        Object stringProperty = getObjectProperty(name);
        String result = null;
        if (stringProperty != null)
        {
            result = MessageHelper.convertToString(stringProperty);
        }
        return result;
    }

    /**
     * Return the object property value with the given name.
     *
     * @param name the name of the Java object property
     * @return the Java object property value with the given name, in
     *         objectified format (ie. if it set as an int, then a Integer is
     *         returned). If there is no property by this name, a null value
     *         is returned.
     * @throws JMSException If getting the object property fails due to some internal error.
     */
    public Object getObjectProperty(String name) throws JMSException
    {
        return super.getProperty(name);
    }

    /**
     * Get an Enumeration of all the property names.
     *
     * @return An enumeration of all the names of property values.
     * @throws JMSException If getting the property names fails due to some internal JMS error.
     */
    public Enumeration getPropertyNames() throws JMSException
    {
        return super.getAllPropertyNames();
    }

    /**
     * Set a boolean property value with the given name.
     *
     * @param name  The name of the boolean property
     * @param value The boolean property value to set.
     * @throws JMSException                 If setting the property fails due to some internal JMS error.
     * @throws MessageNotWriteableException If the message properties are read-only.
     */
    public void setBooleanProperty(String name, boolean value) throws JMSException
    {
        setObjectProperty(name, value);
    }

    /**
     * Set a byte property value with the given name.
     *
     * @param name  The name of the byte property
     * @param value The byte property value to set.
     * @throws JMSException                 If setting the property fails due to some internal JMS error.
     * @throws MessageNotWriteableException If the message properties are read-only.
     */
    public void setByteProperty(String name, byte value) throws JMSException
    {
        setObjectProperty(name, value);
    }

    /**
     * Set a short property value with the given name.
     *
     * @param name  The name of the short property
     * @param value The short property value to set.
     * @throws JMSException                 If setting the property fails due to some internal JMS error.
     * @throws MessageNotWriteableException If the message properties are read-only.
     */
    public void setShortProperty(String name, short value) throws JMSException
    {
        setObjectProperty(name, value);
    }

    /**
     * Set an int property value with the given name.
     *
     * @param name  The name of the int property
     * @param value The int property value to set.
     * @throws JMSException                 If setting the property fails due to some internal JMS error.
     * @throws MessageNotWriteableException If the message properties are read-only.
     */
    public void setIntProperty(String name, int value) throws JMSException
    {
        setObjectProperty(name, value);
    }

    /**
     * Set a long property value with the given name.
     *
     * @param name  The name of the long property
     * @param value The long property value to set.
     * @throws JMSException                 If setting the property fails due to some internal JMS error.
     * @throws MessageNotWriteableException If the message properties are read-only.
     */
    public void setLongProperty(String name, long value) throws JMSException
    {
        setObjectProperty(name, value);
    }

    /**
     * Set a float property value with the given name.
     *
     * @param name  The name of the float property
     * @param value The float property value to set.
     * @throws JMSException                 If setting the property fails due to some internal JMS error.
     * @throws MessageNotWriteableException If the message properties are read-only.
     */
    public void setFloatProperty(String name, float value) throws JMSException
    {
        setObjectProperty(name, value);
    }

    /**
     * Set a double property value with the given name.
     *
     * @param name  The name of the double property
     * @param value The double property value to set.
     * @throws JMSException                 If setting the property fails due to some internal JMS error.
     * @throws MessageNotWriteableException If the message properties are read-only.
     */
    public void setDoubleProperty(String name, double value) throws JMSException
    {
        setObjectProperty(name, value);
    }

    /**
     * Set a string property value with the given name.
     *
     * @param name  The name of the string property
     * @param value The string property value to set.
     * @throws JMSException                 If setting the property fails due to some internal JMS error.
     * @throws MessageNotWriteableException If the message properties are read-only.
     */
    public void setStringProperty(String name, String value) throws JMSException
    {
        setObjectProperty(name, value);
    }

    /**
     * Set a Java object property value with the given name.
     * <p> The JMS spec says:
     * <p> The setObjectProperty method accepts values of class Boolean, Byte, Short, Integer,
     * Long, Float, Double, and String. An attempt to use any other class must throw a JMSException.
     *
     * @param name  the name of the Java object property.
     * @param value the Java object property value to set in the Message.
     * @throws JMSException                 If setting the property fails due to some internal JMS error.
     * @throws MessageFormatException       If the object is invalid
     * @throws MessageNotWriteableException If the message properties are read-only.
     */
    public void setObjectProperty(String name, Object value) throws JMSException
    {
        if (_proertiesReadOnly)
        {
            throw new MessageNotWriteableException("Error the message properties are read only");
        }
        if (!(value instanceof String || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double || value instanceof Boolean || value == null))
        {
            throw new MessageFormatException("Format of object " + value + " is not supported");
        }
        super.setProperty(name, value);
    }

    /**
     * Acknowledgment of a message automatically acknowledges all
     * messages previously received by the session. Clients may
     * individually acknowledge messages or they may choose to acknowledge
     * messages in application defined groups (which is done by acknowledging
     * the last received message in the group).
     *
     * @throws JMSException If this method is called on a closed session.
     */
    public void acknowledge() throws JMSException
    {
        _messageConsumer.getSession().acknowledge();
    }

    /**
     * Clear out the message body. Clearing a message's body does not clear
     * its header values or property entries.
     * <P>If this message body was read-only, calling this method leaves
     * the message body in the same state as an empty body in a newly
     * created message.
     *
     * @throws JMSException If clearing this message body fails to due to some error.
     */
    public void clearBody() throws JMSException
    {
        super.clearMessageData();
        _readOnly = false;
    }

    //--- Additional public methods 
    /**
     * This method is invoked before a message dispatch operation.
     *
     * @throws QpidException If the destination is not set
     */
    public void beforeMessageDispatch() throws QpidException
    {
        if (_destination == null)
        {
            throw new QpidException("Invalid destination null", null, null);
        }
        super.beforeMessageDispatch();
    }

    /**
     * This method is invoked after this message is received.
     *
     * @throws QpidException If there is an internal error when procesing this message.
     */
    public void afterMessageReceive() throws QpidException
    {
        // recreate a destination object for the encoded destination
        // _destination = // todo
        // recreate a destination object for the encoded ReplyTo destination (if it exists)
        //          _replyTo = // todo

        _proertiesReadOnly = true;
        _readOnly = true;
    }

    /**
     * Test whether this message is readonly by throwing a MessageNotWriteableException if this
     * message is readonly
     *
     * @throws MessageNotWriteableException If this message is readonly
     */
    protected void isWriteable() throws MessageNotWriteableException
    {
        if (_readOnly)
        {
            throw new MessageNotWriteableException("Cannot update message");
        }
    }

    /**
     * Set the MessageConsumerImpl through which this message was received.
     * <p> This method is called after a message is received.
     *
     * @param messageConsumer the MessageConsumerImpl reference through which this message was received.
     */
    public void setMessageConsumer(MessageConsumerImpl messageConsumer)
    {
        _messageConsumer = messageConsumer;
    }

}
