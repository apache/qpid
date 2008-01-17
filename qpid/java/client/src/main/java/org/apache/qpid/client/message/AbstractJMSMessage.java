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
import org.apache.qpid.client.*;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.BindingURL;
import org.apache.qpid.url.URLSyntaxException;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;

public abstract class AbstractJMSMessage extends AMQMessage implements org.apache.qpid.jms.Message
{
    private static final Map _destinationCache = Collections.synchronizedMap(new ReferenceMap());

    protected boolean _redelivered;

    protected ByteBuffer _data;
    private boolean _readableProperties = false;
    protected boolean _readableMessage = false;
    protected boolean _changedData = true;
    private Destination _destination;
    private JMSHeaderAdapter _headerAdapter;
    private BasicMessageConsumer _consumer;
    private boolean _strictAMQP;

    /**
     * This is 0_10 specific
     */
    private org.apache.qpidity.api.Message _010message = null;

    public void set010Message(org.apache.qpidity.api.Message m )
    {
        _010message = m;
    }

    public void dataChanged()
    {
        if (_010message != null)
        {
            _010message.clearData();
            try
            {
                if (_data != null)
                {
                    _010message.appendData(_data.buf());
                }
                else
                {
                    _010message.appendData(java.nio.ByteBuffer.allocate(0));
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * End 010 specific
     */

    public org.apache.qpidity.api.Message get010Message()
    {
        return _010message;
    }

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
        _headerAdapter = new JMSHeaderAdapter(((BasicContentHeaderProperties) _contentHeaderProperties).getHeaders());

        _strictAMQP =
            Boolean.parseBoolean(System.getProperties().getProperty(AMQSession.STRICT_AMQP, AMQSession.STRICT_AMQP_DEFAULT));
    }

    protected AbstractJMSMessage(long deliveryTag, BasicContentHeaderProperties contentHeader, AMQShortString exchange,
        AMQShortString routingKey, ByteBuffer data) throws AMQException
    {
        this(contentHeader, deliveryTag);

        Integer type = contentHeader.getHeaders().getInteger(CustomJMSXProperty.JMS_QPID_DESTTYPE.getShortStringName());

        AMQDestination dest;

        if (AMQDestination.QUEUE_TYPE.equals(type))
        {
            dest = new AMQQueue(exchange, routingKey, routingKey);
        }
        else if (AMQDestination.TOPIC_TYPE.equals(type))
        {
            dest = new AMQTopic(exchange, routingKey, null);
        }
        else
        {
            dest = new AMQUndefinedDestination(exchange, routingKey, null);
        }
        // Destination dest = AMQDestination.createDestination(url);
        setJMSDestination(dest);

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
        _headerAdapter = new JMSHeaderAdapter(((BasicContentHeaderProperties) _contentHeaderProperties).getHeaders());
    }

    public String getJMSMessageID() throws JMSException
    {
        if (getContentHeaderProperties().getMessageIdAsString() == null)
        {
            getContentHeaderProperties().setMessageId("ID:" + UUID.randomUUID());
        }

        return getContentHeaderProperties().getMessageIdAsString();
    }

    public void setJMSMessageID(String messageId) throws JMSException
    {
        getContentHeaderProperties().setMessageId(messageId);
    }

    public long getJMSTimestamp() throws JMSException
    {
        return getContentHeaderProperties().getTimestamp();
    }

    public void setJMSTimestamp(long timestamp) throws JMSException
    {
        getContentHeaderProperties().setTimestamp(timestamp);
    }

    public byte[] getJMSCorrelationIDAsBytes() throws JMSException
    {
        return getContentHeaderProperties().getCorrelationIdAsString().getBytes();
    }

    public void setJMSCorrelationIDAsBytes(byte[] bytes) throws JMSException
    {
        getContentHeaderProperties().setCorrelationId(new String(bytes));
    }

    public void setJMSCorrelationID(String correlationId) throws JMSException
    {
        getContentHeaderProperties().setCorrelationId(correlationId);
    }

    public String getJMSCorrelationID() throws JMSException
    {
        return getContentHeaderProperties().getCorrelationIdAsString();
    }

    public Destination getJMSReplyTo() throws JMSException
    {
        String replyToEncoding = getContentHeaderProperties().getReplyToAsString();
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
                    throw new JMSAMQException("Illegal value in JMS_ReplyTo property: " + replyToEncoding, e);
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
            throw new IllegalArgumentException(
                "ReplyTo destination may only be an AMQDestination - passed argument was type " + destination.getClass());
        }

        final AMQDestination amqd = (AMQDestination) destination;

        final AMQShortString encodedDestination = amqd.getEncodedName();
        _destinationCache.put(encodedDestination, destination);
        getContentHeaderProperties().setReplyTo(encodedDestination);
    }

    public Destination getJMSDestination() throws JMSException
    {
        return _destination;
    }

    public void setJMSDestination(Destination destination)
    {
        _destination = destination;
    }

    public int getJMSDeliveryMode() throws JMSException
    {
        return getContentHeaderProperties().getDeliveryMode();
    }

    public void setJMSDeliveryMode(int i) throws JMSException
    {
        getContentHeaderProperties().setDeliveryMode((byte) i);
    }

    public BasicContentHeaderProperties getContentHeaderProperties()
    {
        return (BasicContentHeaderProperties) _contentHeaderProperties;
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
        return getContentHeaderProperties().getTypeAsString();
    }

    public void setJMSType(String string) throws JMSException
    {
        getContentHeaderProperties().setType(string);
    }

    public long getJMSExpiration() throws JMSException
    {
        return getContentHeaderProperties().getExpiration();
    }

    public void setJMSExpiration(long l) throws JMSException
    {
        getContentHeaderProperties().setExpiration(l);
    }

    public int getJMSPriority() throws JMSException
    {
        return getContentHeaderProperties().getPriority();
    }

    public void setJMSPriority(int i) throws JMSException
    {
        getContentHeaderProperties().setPriority((byte) i);
    }

    public void clearProperties() throws JMSException
    {
        getJmsHeaders().clear();

        _readableProperties = false;
    }

    public void clearBody() throws JMSException
    {
        clearBodyImpl();
        _readableMessage = false;
    }

    public boolean propertyExists(AMQShortString propertyName) throws JMSException
    {
        return getJmsHeaders().propertyExists(propertyName);
    }

    public boolean propertyExists(String propertyName) throws JMSException
    {
        return getJmsHeaders().propertyExists(propertyName);
    }

    public boolean getBooleanProperty(AMQShortString propertyName) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        return getJmsHeaders().getBoolean(propertyName);
    }

    public boolean getBooleanProperty(String propertyName) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        return getJmsHeaders().getBoolean(propertyName);
    }

    public byte getByteProperty(String propertyName) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        return getJmsHeaders().getByte(propertyName);
    }

    public byte[] getBytesProperty(AMQShortString propertyName) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        return getJmsHeaders().getBytes(propertyName);
    }

    public short getShortProperty(String propertyName) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        return getJmsHeaders().getShort(propertyName);
    }

    public int getIntProperty(String propertyName) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        return getJmsHeaders().getInteger(propertyName);
    }

    public long getLongProperty(String propertyName) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        return getJmsHeaders().getLong(propertyName);
    }

    public float getFloatProperty(String propertyName) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        return getJmsHeaders().getFloat(propertyName);
    }

    public double getDoubleProperty(String propertyName) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        return getJmsHeaders().getDouble(propertyName);
    }

    public String getStringProperty(String propertyName) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        return getJmsHeaders().getString(propertyName);
    }

    public Object getObjectProperty(String propertyName) throws JMSException
    {
        return getJmsHeaders().getObject(propertyName);
    }

    public Enumeration getPropertyNames() throws JMSException
    {
        return getJmsHeaders().getPropertyNames();
    }

    public void setBooleanProperty(AMQShortString propertyName, boolean b) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        checkWritableProperties();
        getJmsHeaders().setBoolean(propertyName, b);
    }

    public void setBooleanProperty(String propertyName, boolean b) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        checkWritableProperties();
        getJmsHeaders().setBoolean(propertyName, b);
    }

    public void setByteProperty(String propertyName, byte b) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        checkWritableProperties();
        getJmsHeaders().setByte(propertyName, new Byte(b));
    }

    public void setBytesProperty(AMQShortString propertyName, byte[] bytes) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        checkWritableProperties();
        getJmsHeaders().setBytes(propertyName, bytes);
    }

    public void setShortProperty(String propertyName, short i) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        checkWritableProperties();
        getJmsHeaders().setShort(propertyName, new Short(i));
    }

    public void setIntProperty(String propertyName, int i) throws JMSException
    {
        checkWritableProperties();
        JMSHeaderAdapter.checkPropertyName(propertyName);
        super.setIntProperty(new AMQShortString(propertyName), new Integer(i));
    }

    public void setLongProperty(String propertyName, long l) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        checkWritableProperties();
        getJmsHeaders().setLong(propertyName, new Long(l));
    }

    public void setFloatProperty(String propertyName, float f) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        checkWritableProperties();
        getJmsHeaders().setFloat(propertyName, new Float(f));
    }

    public void setDoubleProperty(String propertyName, double v) throws JMSException
    {
        if (_strictAMQP)
        {
            throw new UnsupportedOperationException("JMS Proprerties not supported in AMQP");
        }

        checkWritableProperties();
        getJmsHeaders().setDouble(propertyName, new Double(v));
    }

    public void setStringProperty(String propertyName, String value) throws JMSException
    {
        checkWritableProperties();
        JMSHeaderAdapter.checkPropertyName(propertyName);
        super.setLongStringProperty(new AMQShortString(propertyName), value);
    }

    public void setObjectProperty(String propertyName, Object object) throws JMSException
    {
        checkWritableProperties();
        getJmsHeaders().setObject(propertyName, object);
    }

    protected void removeProperty(AMQShortString propertyName) throws JMSException
    {
        getJmsHeaders().remove(propertyName);
    }

    protected void removeProperty(String propertyName) throws JMSException
    {
        getJmsHeaders().remove(propertyName);
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
        if (_session != null)
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
     * Get a String representation of the body of the message. Used in the toString() method which outputs this before
     * message properties.
     */
    public abstract String toBodyString() throws JMSException;

    public String getMimeType()
    {
        return getMimeTypeAsShortString().toString();
    }

    public abstract AMQShortString getMimeTypeAsShortString();

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
            //buf.append("\nJMS reply to: ").append(String.valueOf(getJMSReplyTo()));
            buf.append("\nJMS Redelivered: ").append(_redelivered);
            buf.append("\nJMS Destination: ").append(getJMSDestination());
            buf.append("\nJMS Type: ").append(getJMSType());
            buf.append("\nJMS MessageID: ").append(getJMSMessageID());
            buf.append("\nAMQ message number: ").append(_deliveryTag);

            buf.append("\nProperties:");
            if (getJmsHeaders().isEmpty())
            {
                buf.append("<NONE>");
            }
            else
            {
                buf.append('\n').append(getJmsHeaders().getHeaders());
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
        getContentHeaderProperties().setHeaders(messageProperties);
    }

    public JMSHeaderAdapter getJmsHeaders()
    {
        return _headerAdapter;
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
        _contentHeaderProperties.updated();
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
            dataChanged();
            _changedData = false;
        }
    }

    public int getContentLength()
    {
        return _data.remaining();
    }

    public void setConsumer(BasicMessageConsumer basicMessageConsumer)
    {
        _consumer = basicMessageConsumer;
    }

}
