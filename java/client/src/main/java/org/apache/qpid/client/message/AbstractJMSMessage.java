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

import org.apache.commons.collections.map.ReferenceMap;
import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQException;
import org.apache.qpid.url.BindingURL;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.BasicMessageConsumer;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.FieldTable;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

public abstract class AbstractJMSMessage extends AMQMessage implements org.apache.qpid.jms.Message
{
    private static final Map _destinationCache = Collections.synchronizedMap(new ReferenceMap());

    protected boolean _redelivered;

    protected ByteBuffer _data;
    private boolean _readableProperties = false;
    protected boolean _readableMessage = false;
    protected boolean _changedData;
    private Destination _destination;
    private BasicMessageConsumer _consumer;

    protected AbstractJMSMessage(ByteBuffer data)
    {
        super(new BasicContentHeaderProperties());
        _data = data;
        if (_data != null)
        {
            _data.acquire();
        }
        _readableProperties = false;
        _readableMessage = (data != null);
        _changedData = (data == null);
    }

    protected AbstractJMSMessage(long deliveryTag, BasicContentHeaderProperties contentHeader, ByteBuffer data) throws AMQException
    {
        this(contentHeader, deliveryTag);
        _data = data;
        if (_data != null)
        {
            _data.acquire();
        }

        _readableMessage = data != null;
    }

    protected AbstractJMSMessage(BasicContentHeaderProperties contentHeader, long deliveryTag)
    {
        super(contentHeader, deliveryTag);
        _readableProperties = (_contentHeaderProperties != null);
    }

    public String getJMSMessageID() throws JMSException
    {
        if (getJmsContentHeaderProperties().getMessageId() == null)
        {
            getJmsContentHeaderProperties().setMessageId("ID:" + _deliveryTag);
        }
        return getJmsContentHeaderProperties().getMessageId();
    }

    public void setJMSMessageID(String messageId) throws JMSException
    {
        getJmsContentHeaderProperties().setMessageId(messageId);
    }

    public long getJMSTimestamp() throws JMSException
    {
        return new Long(getJmsContentHeaderProperties().getTimestamp()).longValue();
    }

    public void setJMSTimestamp(long timestamp) throws JMSException
    {
        getJmsContentHeaderProperties().setTimestamp(timestamp);
    }

    public byte[] getJMSCorrelationIDAsBytes() throws JMSException
    {
        return getJmsContentHeaderProperties().getCorrelationId().getBytes();
    }

    public void setJMSCorrelationIDAsBytes(byte[] bytes) throws JMSException
    {
        getJmsContentHeaderProperties().setCorrelationId(new String(bytes));
    }

    public void setJMSCorrelationID(String correlationId) throws JMSException
    {
        getJmsContentHeaderProperties().setCorrelationId(correlationId);
    }

    public String getJMSCorrelationID() throws JMSException
    {
        return getJmsContentHeaderProperties().getCorrelationId();
    }

    public Destination getJMSReplyTo() throws JMSException
    {
        String replyToEncoding = getJmsContentHeaderProperties().getReplyTo();
        if (replyToEncoding == null)
        {
            return null;
        }
        else
        {
            Destination dest = (Destination) _destinationCache.get(replyToEncoding);
            if (dest == null)
            {
                try
                {
                    BindingURL binding = new AMQBindingURL(replyToEncoding);
                    dest = AMQDestination.createDestination(binding);
                }
                catch (URLSyntaxException e)
                {
                    throw new JMSException("Illegal value in JMS_ReplyTo property: " + replyToEncoding);
                }

                _destinationCache.put(replyToEncoding, dest);
            }
            return dest;
        }
    }

    public void setJMSReplyTo(Destination destination) throws JMSException
    {
        if (destination == null)
        {
            throw new IllegalArgumentException("Null destination not allowed");
        }
        if (!(destination instanceof AMQDestination))
        {
            throw new IllegalArgumentException("ReplyTo destination may only be an AMQDestination - passed argument was type " +
                                               destination.getClass());
        }
        final AMQDestination amqd = (AMQDestination) destination;

        final String encodedDestination = amqd.getEncodedName();
        _destinationCache.put(encodedDestination, destination);
        getJmsContentHeaderProperties().setReplyTo(encodedDestination);
    }

    public Destination getJMSDestination() throws JMSException
    {
        return _destination;
    }

    public void setJMSDestination(Destination destination) throws JMSException
    {
        _destination = destination;
    }

    public int getJMSDeliveryMode() throws JMSException
    {
        return getJmsContentHeaderProperties().getDeliveryMode();
    }

    public void setJMSDeliveryMode(int i) throws JMSException
    {
        getJmsContentHeaderProperties().setDeliveryMode((byte) i);
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
        return getJmsContentHeaderProperties().getType();
    }

    public void setJMSType(String string) throws JMSException
    {
        getJmsContentHeaderProperties().setType(string);
    }

    public long getJMSExpiration() throws JMSException
    {
        return new Long(getJmsContentHeaderProperties().getExpiration()).longValue();
    }

    public void setJMSExpiration(long l) throws JMSException
    {
        getJmsContentHeaderProperties().setExpiration(l);
    }

    public int getJMSPriority() throws JMSException
    {
        return getJmsContentHeaderProperties().getPriority();
    }

    public void setJMSPriority(int i) throws JMSException
    {
        getJmsContentHeaderProperties().setPriority((byte) i);
    }

    public void clearProperties() throws JMSException
    {
        getJmsContentHeaderProperties().getJMSHeaders().clear();

        _readableProperties = false;
    }

    public void clearBody() throws JMSException
    {
        clearBodyImpl();
        _readableMessage = false;
    }


    public boolean propertyExists(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        return getJmsContentHeaderProperties().getJMSHeaders().propertyExists(propertyName);
    }

    public boolean getBooleanProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);

        return getJmsContentHeaderProperties().getJMSHeaders().getBoolean(propertyName);
    }

    public byte getByteProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        return getJmsContentHeaderProperties().getJMSHeaders().getByte(propertyName);
    }

    public short getShortProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        return getJmsContentHeaderProperties().getJMSHeaders().getShort(propertyName);
    }

    public int getIntProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        return getJmsContentHeaderProperties().getJMSHeaders().getInteger(propertyName);
    }

    public long getLongProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        return getJmsContentHeaderProperties().getJMSHeaders().getLong(propertyName);
    }

    public float getFloatProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        return getJmsContentHeaderProperties().getJMSHeaders().getFloat(propertyName);
    }

    public double getDoubleProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        return getJmsContentHeaderProperties().getJMSHeaders().getDouble(propertyName);
    }

    public String getStringProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        return getJmsContentHeaderProperties().getJMSHeaders().getString(propertyName);
    }

    public Object getObjectProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        return getJmsContentHeaderProperties().getJMSHeaders().getObject(propertyName);
    }

    public Enumeration getPropertyNames() throws JMSException
    {
        return getJmsContentHeaderProperties().getJMSHeaders().getPropertyNames();
    }

    public void setBooleanProperty(String propertyName, boolean b) throws JMSException
    {
        checkWritableProperties();
        checkPropertyName(propertyName);
        getJmsContentHeaderProperties().getJMSHeaders().setBoolean(propertyName, b);
    }

    public void setByteProperty(String propertyName, byte b) throws JMSException
    {
        checkWritableProperties();
        checkPropertyName(propertyName);
        getJmsContentHeaderProperties().getJMSHeaders().setByte(propertyName, new Byte(b));
    }

    public void setShortProperty(String propertyName, short i) throws JMSException
    {
        checkWritableProperties();
        checkPropertyName(propertyName);
        getJmsContentHeaderProperties().getJMSHeaders().setShort(propertyName, new Short(i));
    }

    public void setIntProperty(String propertyName, int i) throws JMSException
    {
        checkWritableProperties();
        checkPropertyName(propertyName);
        getJmsContentHeaderProperties().getJMSHeaders().setInteger(propertyName, new Integer(i));
    }

    public void setLongProperty(String propertyName, long l) throws JMSException
    {
        checkWritableProperties();
        checkPropertyName(propertyName);
        getJmsContentHeaderProperties().getJMSHeaders().setLong(propertyName, new Long(l));
    }

    public void setFloatProperty(String propertyName, float f) throws JMSException
    {
        checkWritableProperties();
        checkPropertyName(propertyName);
        getJmsContentHeaderProperties().getJMSHeaders().setFloat(propertyName, new Float(f));
    }

    public void setDoubleProperty(String propertyName, double v) throws JMSException
    {
        checkWritableProperties();
        checkPropertyName(propertyName);
        getJmsContentHeaderProperties().getJMSHeaders().setDouble(propertyName, new Double(v));
    }

    public void setStringProperty(String propertyName, String value) throws JMSException
    {
        checkWritableProperties();
        checkPropertyName(propertyName);
        getJmsContentHeaderProperties().getJMSHeaders().setString(propertyName, value);
    }

    public void setObjectProperty(String propertyName, Object object) throws JMSException
    {
        checkWritableProperties();
        checkPropertyName(propertyName);
        getJmsContentHeaderProperties().getJMSHeaders().setObject(propertyName, object);
    }

    protected void removeProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        getJmsContentHeaderProperties().getJMSHeaders().remove(propertyName);
    }

    public void acknowledgeThis() throws JMSException
    {
        // the JMS 1.1 spec says in section 3.6 that calls to acknowledge are ignored when client acknowledge
        // is not specified. In our case, we only set the session field where client acknowledge mode is specified.
        if (_session != null)
        {
            if (_session.getAMQConnection().isClosed())
            {
                throw new javax.jms.IllegalStateException("Connection is already closed");
            }

            // we set multiple to true here since acknowledgement implies acknowledge of all previous messages
            // received on the session
            _session.acknowledgeMessage(_deliveryTag, true);
        }
    }

    public void acknowledge() throws JMSException
    {
        if(_session != null)
        {
            _session.acknowledge();
        }
    }


    /**
     * This forces concrete classes to implement clearBody()
     *
     * @throws JMSException
     */
    public abstract void clearBodyImpl() throws JMSException;

    /**
     * Get a String representation of the body of the message. Used in the
     * toString() method which outputs this before message properties.
     */
    public abstract String toBodyString() throws JMSException;

    public abstract String getMimeType();

    public String toString()
    {
        try
        {
            StringBuffer buf = new StringBuffer("Body:\n");
            buf.append(toBodyString());
            buf.append("\nJMS timestamp: ").append(getJMSTimestamp());
            buf.append("\nJMS expiration: ").append(getJMSExpiration());
            buf.append("\nJMS priority: ").append(getJMSPriority());
            buf.append("\nJMS delivery mode: ").append(getJMSDeliveryMode());
            buf.append("\nJMS reply to: ").append(String.valueOf(getJMSReplyTo()));
            buf.append("\nAMQ message number: ").append(_deliveryTag);
            buf.append("\nProperties:");
            if (getJmsContentHeaderProperties().getJMSHeaders().isEmpty())
            {
                buf.append("<NONE>");
            }
            else
            {
                buf.append('\n').append(getJmsContentHeaderProperties().getJMSHeaders());
            }
            return buf.toString();
        }
        catch (JMSException e)
        {
            return e.toString();
        }
    }


    public void setUnderlyingMessagePropertiesMap(FieldTable messageProperties)
    {
        getJmsContentHeaderProperties().setHeaders(messageProperties);
    }

    private void checkPropertyName(String propertyName)
    {
        if (propertyName == null)
        {
            throw new IllegalArgumentException("Property name must not be null");
        }
        else if ("".equals(propertyName))
        {
            throw new IllegalArgumentException("Property name must not be the empty string");
        }
    }

    public BasicContentHeaderProperties getJmsContentHeaderProperties()
    {
        return (BasicContentHeaderProperties) _contentHeaderProperties;
    }

    public ByteBuffer getData()
    {
        // make sure we rewind the data just in case any method has moved the
        // position beyond the start
        if (_data != null)
        {
            reset();
        }
        return _data;
    }

    protected void checkReadable() throws MessageNotReadableException
    {
        if (!_readableMessage)
        {
            throw new MessageNotReadableException("You need to call reset() to make the message readable");
        }
    }

    protected void checkWritable() throws MessageNotWriteableException
    {
        if (_readableMessage)
        {
            throw new MessageNotWriteableException("You need to call clearBody() to make the message writable");
        }
    }

    protected void checkWritableProperties() throws MessageNotWriteableException
    {
        if (_readableProperties)
        {
            throw new MessageNotWriteableException("You need to call clearProperties() to make the message writable");
        }
    }

    public boolean isReadable()
    {
        return _readableMessage;
    }

    public boolean isWritable()
    {
        return !_readableMessage;
    }

    public void reset()
    {
        if (!_changedData)
        {
            _data.rewind();
        }
        else
        {
            _data.flip();
            _changedData = false;
        }
    }

    public void setConsumer(BasicMessageConsumer basicMessageConsumer)
    {
        _consumer = basicMessageConsumer;
    }
}
