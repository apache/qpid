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
package org.apache.qpid.client.message;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQSession;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageNotWriteableException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.UUID;

public abstract class AbstractJMSMessage implements org.apache.qpid.jms.Message
{


    /** If the acknowledge mode is CLIENT_ACKNOWLEDGE the session is required */

    protected AMQMessageDelegate _delegate;
    private boolean _redelivered;
    private boolean _receivedFromServer;

    protected AbstractJMSMessage(AMQMessageDelegateFactory delegateFactory, boolean fromReceivedData)
    {
        _delegate = delegateFactory.createDelegate();
        setContentType(getMimeType());
    }

    protected AbstractJMSMessage(AMQMessageDelegate delegate, boolean fromReceivedData) throws AMQException
    {

        _delegate = delegate;
        setContentType(getMimeType());
    }

    public String getJMSMessageID() throws JMSException
    {
        return _delegate.getJMSMessageID();
    }

    public void setJMSMessageID(String messageId) throws JMSException
    {
        _delegate.setJMSMessageID(messageId);
    }

    public void setJMSMessageID(UUID messageId) throws JMSException
    {
        _delegate.setJMSMessageID(messageId);
    }


    public long getJMSTimestamp() throws JMSException
    {
        return _delegate.getJMSTimestamp();
    }

    public void setJMSTimestamp(long timestamp) throws JMSException
    {
        _delegate.setJMSTimestamp(timestamp);
    }

    public byte[] getJMSCorrelationIDAsBytes() throws JMSException
    {
        return _delegate.getJMSCorrelationIDAsBytes();
    }

    public void setJMSCorrelationIDAsBytes(byte[] bytes) throws JMSException
    {
        _delegate.setJMSCorrelationIDAsBytes(bytes);
    }

    public void setJMSCorrelationID(String correlationId) throws JMSException
    {
        _delegate.setJMSCorrelationID(correlationId);
    }

    public String getJMSCorrelationID() throws JMSException
    {
        return _delegate.getJMSCorrelationID();
    }

    public Destination getJMSReplyTo() throws JMSException
    {
        return _delegate.getJMSReplyTo();
    }

    public void setJMSReplyTo(Destination destination) throws JMSException
    {
        _delegate.setJMSReplyTo(destination);
    }

    public Destination getJMSDestination() throws JMSException
    {
        return _delegate.getJMSDestination();
    }

    public void setJMSDestination(Destination destination)
    {
        _delegate.setJMSDestination(destination);
    }

    public int getJMSDeliveryMode() throws JMSException
    {
        return _delegate.getJMSDeliveryMode();
    }

    public void setJMSDeliveryMode(int i) throws JMSException
    {
        _delegate.setJMSDeliveryMode(i);
    }


    public boolean getJMSRedelivered() throws JMSException
    {
        return _redelivered;
    }

    public void setJMSRedelivered(boolean b) throws JMSException
    {
        _redelivered = b;
    }


    public String getJMSType() throws JMSException
    {
        return _delegate.getJMSType();
    }

    public void setJMSType(String string) throws JMSException
    {
        _delegate.setJMSType(string);
    }

    public long getJMSExpiration() throws JMSException
    {
        return _delegate.getJMSExpiration();
    }

    public void setJMSExpiration(long l) throws JMSException
    {
        _delegate.setJMSExpiration(l);
    }

    public int getJMSPriority() throws JMSException
    {
        return _delegate.getJMSPriority();
    }

    public void setJMSPriority(int i) throws JMSException
    {
        _delegate.setJMSPriority(i);
    }


    public boolean propertyExists(String propertyName) throws JMSException
    {
        return _delegate.propertyExists(propertyName);
    }

    public boolean getBooleanProperty(final String s)
            throws JMSException
    {
        return _delegate.getBooleanProperty(s);
    }

    public byte getByteProperty(final String s)
            throws JMSException
    {
        return _delegate.getByteProperty(s);
    }

    public short getShortProperty(final String s)
            throws JMSException
    {
        return _delegate.getShortProperty(s);
    }

    public int getIntProperty(final String s)
            throws JMSException
    {
        return _delegate.getIntProperty(s);
    }

    public long getLongProperty(final String s)
            throws JMSException
    {
        return _delegate.getLongProperty(s);
    }

    public float getFloatProperty(final String s)
            throws JMSException
    {
        return _delegate.getFloatProperty(s);
    }

    public double getDoubleProperty(final String s)
            throws JMSException
    {
        return _delegate.getDoubleProperty(s);
    }

    public String getStringProperty(final String s)
            throws JMSException
    {
        return _delegate.getStringProperty(s);
    }

    public Object getObjectProperty(final String s)
            throws JMSException
    {
        return _delegate.getObjectProperty(s);
    }

    public Enumeration getPropertyNames()
            throws JMSException
    {
        return _delegate.getPropertyNames();
    }

    public void setBooleanProperty(final String s, final boolean b)
            throws JMSException
    {
        _delegate.setBooleanProperty(s, b);
    }

    public void setByteProperty(final String s, final byte b)
            throws JMSException
    {
        _delegate.setByteProperty(s, b);
    }

    public void setShortProperty(final String s, final short i)
            throws JMSException
    {
        _delegate.setShortProperty(s, i);
    }

    public void setIntProperty(final String s, final int i)
            throws JMSException
    {
        _delegate.setIntProperty(s, i);
    }

    public void setLongProperty(final String s, final long l)
            throws JMSException
    {
        _delegate.setLongProperty(s, l);
    }

    public void setFloatProperty(final String s, final float v)
            throws JMSException
    {
        _delegate.setFloatProperty(s, v);
    }

    public void setDoubleProperty(final String s, final double v)
            throws JMSException
    {
        _delegate.setDoubleProperty(s, v);
    }

    public void setStringProperty(final String s, final String s1)
            throws JMSException
    {
        _delegate.setStringProperty(s, s1);
    }

    public void setObjectProperty(final String s, final Object o)
            throws JMSException
    {
        _delegate.setObjectProperty(s, o);
    }



    public void clearProperties() throws JMSException
    {
        _delegate.clearProperties();
    }

    public void clearBody() throws JMSException
    {
        _receivedFromServer = false;
    }

    public void acknowledgeThis() throws JMSException
    {
        _delegate.acknowledgeThis();
    }

    public void acknowledge() throws JMSException
    {
        _delegate.acknowledge();
    }

    /*
     * Get a String representation of the body of the message. Used in the toString() method which outputs this before
     * message properties.
     */
    public abstract String toBodyString() throws JMSException;

    protected abstract String getMimeType();



    public String toString()
    {
        try
        {
            StringBuffer buf = new StringBuffer("Body:\n");
            
            buf.append(toBodyString());
            buf.append("\nJMS Correlation ID: ").append(getJMSCorrelationID());
            buf.append("\nJMS timestamp: ").append(getJMSTimestamp());
            buf.append("\nJMS expiration: ").append(getJMSExpiration());
            buf.append("\nJMS priority: ").append(getJMSPriority());
            buf.append("\nJMS delivery mode: ").append(getJMSDeliveryMode());
            buf.append("\nJMS reply to: ").append(getReplyToString());
            buf.append("\nJMS Redelivered: ").append(_redelivered);
            buf.append("\nJMS Destination: ").append(getJMSDestination());
            buf.append("\nJMS Type: ").append(getJMSType());
            buf.append("\nJMS MessageID: ").append(getJMSMessageID());
            buf.append("\nJMS Content-Type: ").append(getContentType());
            buf.append("\nAMQ message number: ").append(getDeliveryTag());

            buf.append("\nProperties:");
            final Enumeration propertyNames = getPropertyNames();
            if (!propertyNames.hasMoreElements())
            {
                buf.append("<NONE>");
            }
            else
            {
                buf.append('\n');
                while(propertyNames.hasMoreElements())
                {
                    String propertyName = (String) propertyNames.nextElement();
                    buf.append("\t").append(propertyName).append(" = ").append(getObjectProperty(propertyName)).append("\n");
                }

            }

            return buf.toString();
        }
        catch (JMSException e)
        {
            throw new RuntimeException(e);
        }
    }


    public AMQMessageDelegate getDelegate()
    {
        return _delegate;
    }

    abstract public ByteBuffer getData() throws JMSException;


    protected void checkWritable() throws MessageNotWriteableException
    {
        if (_receivedFromServer)
        {
            throw new MessageNotWriteableException("You need to call clearBody() to make the message writable");
        }
    }


    public void setReceivedFromServer()
    {
        _receivedFromServer = true;
    }



    /**
     * The session is set when CLIENT_ACKNOWLEDGE mode is used so that the CHANNEL ACK can be sent when the user calls
     * acknowledge()
     *
     * @param s the AMQ session that delivered this message
     */
    public void setAMQSession(AMQSession s)
    {
        _delegate.setAMQSession(s);
    }

    public AMQSession getAMQSession()
    {
        return _delegate.getAMQSession();
    }

    /**
     * Get the AMQ message number assigned to this message
     *
     * @return the message number
     */
    public long getDeliveryTag()
    {
        return _delegate.getDeliveryTag();
    }

    /** Invoked prior to sending the message. Allows the message to be modified if necessary before sending. */
    public void prepareForSending() throws JMSException
    {
    }


    public void setContentType(String contentType)
    {
        _delegate.setContentType(contentType);
    }

    public String getContentType()
    {
        return _delegate.getContentType();
    }

    public void setEncoding(String encoding)
    {
        _delegate.setEncoding(encoding);
    }

    public String getEncoding()
    {
        return _delegate.getEncoding();
    }

    public String getReplyToString()
    {
        return _delegate.getReplyToString();
    }

    protected void removeProperty(final String propertyName) throws JMSException
    {
        _delegate.removeProperty(propertyName);
    }

}
