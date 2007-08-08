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
import org.apache.qpidity.QpidException;

import javax.jms.Message;
import javax.jms.JMSException;
import javax.jms.Destination;
import javax.jms.DeliveryMode;
import java.util.Enumeration;

/**
 * Implementation of javax.jms.Message
 */
public class MessageImpl extends QpidMessage implements Message
{
    /**
     * The ReplyTo destination for this message
     * TODO set it when the message is received
     */
    private Destination _replyTo;

    /**
     * The destination to which the message has been sent.
     * <p>When a message is sent this value is ignored. After completion
     * of the send method it holds the destination specified by the send.
     * <p>When a message is received, its destination value must be
     * equivalent to the value assigned when it was sent.  --> TODO
     */
    private Destination _destination;

    /**
     * Indicates whether the message properties are in writeable status.
     */
    private boolean _readOnly = false;

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
        return super.getMessageType();
    }

    /**
     * Set this message type.
     *
     * @param type The type of message.
     * @throws JMSException If setting the JMS message type fails due to some internal error.
     */
    public void setJMSType(String type) throws JMSException
    {
        super.setMessageType(type);
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


    public void clearProperties() throws JMSException
    {
        // TODO

    }

    public boolean propertyExists(String string) throws JMSException
    {
        // TODO
        return false;
    }

    public boolean getBooleanProperty(String string) throws JMSException
    {
        // TODO
        return false;
    }

    public byte getByteProperty(String string) throws JMSException
    {
        // TODO
        return 0;
    }

    public short getShortProperty(String string) throws JMSException
    {
        // TODO
        return 0;
    }

    public int getIntProperty(String string) throws JMSException
    {
        // TODO
        return 0;
    }

    public long getLongProperty(String string) throws JMSException
    {
        // TODO
        return 0;
    }

    public float getFloatProperty(String string) throws JMSException
    {
        // TODO
        return 0;
    }

    public double getDoubleProperty(String string) throws JMSException
    {
        // TODO
        return 0;
    }

    public String getStringProperty(String string) throws JMSException
    {
        // TODO
        return null;
    }

    public Object getObjectProperty(String string) throws JMSException
    {
        // TODO
        return null;
    }

    public Enumeration getPropertyNames() throws JMSException
    {
        // TODO
        return null;
    }

    public void setBooleanProperty(String string, boolean b) throws JMSException
    {
        // TODO

    }

    public void setByteProperty(String string, byte b) throws JMSException
    {
        // TODO

    }

    public void setShortProperty(String string, short i) throws JMSException
    {
        // TODO

    }

    public void setIntProperty(String string, int i) throws JMSException
    {
        // TODO

    }

    public void setLongProperty(String string, long l) throws JMSException
    {
        // TODO

    }

    public void setFloatProperty(String string, float v) throws JMSException
    {
        // TODO

    }

    public void setDoubleProperty(String string, double v) throws JMSException
    {
        // TODO

    }

    public void setStringProperty(String string, String string1) throws JMSException
    {
        // TODO

    }

    public void setObjectProperty(String string, Object object) throws JMSException
    {
        // TODO

    }

    public void acknowledge() throws JMSException
    {
        // TODO

    }

    public void clearBody() throws JMSException
    {
        // TODO

    }
}
