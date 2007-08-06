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

import javax.jms.Message;
import javax.jms.JMSException;
import javax.jms.Destination;
import java.util.Enumeration;

/**
 * Implementation of javax.jms.Message
 */
public class MessageImpl extends QpidMessage implements Message
{
    private String _messageID;

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
     * @throws JMSException If getting the message Id fails due to internal JMS error.
     */
    public String getJMSMessageID() throws JMSException
    {
        // check if the message ID has been set
        if (_messageID == null)
        {
            _messageID = super.getJMSMessageID();
        }
        return _messageID;
    }

    /**
     * Set the message ID.
     * <p> The JMS spec says:
     * <P>Providers set this field when a message is sent. This operation
     * can be used to change the value of a message that's been received.
     *
     * @param messageID The ID of the message
     * @throws JMSException If setting the message Id fails due to internal JMS error.
     */
    public void setJMSMessageID(String messageID) throws JMSException
    {
        _messageID = messageID;
    }

    /**
     * Get the message timestamp.
     * <p> The JMS sepc says:  
     * <P>The JMSTimestamp header field contains the time a message was
     * handed off to a provider to be sent. It is not the time the
     * message was actually transmitted because the actual send may occur
     * later due to transactions or other client side queueing of messages.
     * <p/>
     * <P>When a message is sent, JMSTimestamp is ignored. When the send
     * method returns it contains a a time value somewhere in the interval
     * between the call and the return. It is in the format of a normal
     * Java millis time value.
     * <p/>
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
     * @throws JMSException if JMS fails to get the Timestamp
     *                      due to internal JMS error.
     * @see Message#setJMSTimestamp(long)
     */
    public long getJMSTimestamp() throws JMSException
    {
        // TODO
        return 0;
    }

    public void setJMSTimestamp(long l) throws JMSException
    {
        // TODO

    }

    public byte[] getJMSCorrelationIDAsBytes() throws JMSException
    {
        // TODO
        return new byte[0];
    }

    public void setJMSCorrelationIDAsBytes(byte[] bytes) throws JMSException
    {
        // TODO

    }

    public void setJMSCorrelationID(String string) throws JMSException
    {
        // TODO

    }

    public String getJMSCorrelationID() throws JMSException
    {
        // TODO
        return null;
    }

    public Destination getJMSReplyTo() throws JMSException
    {
        // TODO
        return null;
    }

    public void setJMSReplyTo(Destination destination) throws JMSException
    {
        // TODO

    }

    public Destination getJMSDestination() throws JMSException
    {
        // TODO
        return null;
    }

    public void setJMSDestination(Destination destination) throws JMSException
    {
        // TODO

    }

    public int getJMSDeliveryMode() throws JMSException
    {
        // TODO
        return 0;
    }

    public void setJMSDeliveryMode(int i) throws JMSException
    {
        // TODO

    }

    public boolean getJMSRedelivered() throws JMSException
    {
        // TODO
        return false;
    }

    public void setJMSRedelivered(boolean b) throws JMSException
    {
        // TODO

    }

    public String getJMSType() throws JMSException
    {
        // TODO
        return null;
    }

    public void setJMSType(String string) throws JMSException
    {
        // TODO

    }

    public long getJMSExpiration() throws JMSException
    {
        // TODO
        return 0;
    }

    public void setJMSExpiration(long l) throws JMSException
    {
        // TODO

    }

    public int getJMSPriority() throws JMSException
    {
        // TODO
        return 0;
    }

    public void setJMSPriority(int i) throws JMSException
    {
        // TODO

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
