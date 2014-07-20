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

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQSession_0_8;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.CustomJMSXProperty;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.BindingURL;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageNotWriteableException;
import javax.jms.Queue;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;


public class AMQMessageDelegate_0_8 extends AbstractAMQMessageDelegate
{
    private static final float DESTINATION_CACHE_LOAD_FACTOR = 0.75f;
    private static final int DESTINATION_CACHE_SIZE = 500;
    private static final int DESTINATION_CACHE_CAPACITY = (int) (DESTINATION_CACHE_SIZE / DESTINATION_CACHE_LOAD_FACTOR);

    private static final Map<String, Destination> _destinationCache =
            Collections.synchronizedMap(new LinkedHashMap<String,Destination>(DESTINATION_CACHE_CAPACITY,
                                                                              DESTINATION_CACHE_LOAD_FACTOR,
                                                                              true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Destination> eldest)
        {
            return size() >= DESTINATION_CACHE_SIZE;
        }
    });

    public static final String JMS_TYPE = "x-jms-type";


    private boolean _readableProperties = false;

    private Destination _destination;
    private JMSHeaderAdapter _headerAdapter;
    private static final boolean STRICT_AMQP_COMPLIANCE =
            Boolean.parseBoolean(System.getProperties().getProperty(AMQSession.STRICT_AMQP, AMQSession.STRICT_AMQP_DEFAULT));

    private BasicContentHeaderProperties _contentHeaderProperties;

    // The base set of items that needs to be set. 
    private AMQMessageDelegate_0_8(BasicContentHeaderProperties properties, long deliveryTag)
    {
        super(deliveryTag);
        _contentHeaderProperties = properties;
        _readableProperties = (_contentHeaderProperties != null);
        _headerAdapter = new JMSHeaderAdapter(_readableProperties ? _contentHeaderProperties.getHeaders()
                                                                  : (new BasicContentHeaderProperties()).getHeaders() );
    }

    // Used for the creation of new messages
    protected AMQMessageDelegate_0_8()
    {
        this(new BasicContentHeaderProperties(), -1);
        _readableProperties = false;
        _headerAdapter = new JMSHeaderAdapter(_contentHeaderProperties.getHeaders());

    }

    // Used when generating a received message object
    protected AMQMessageDelegate_0_8(long deliveryTag, BasicContentHeaderProperties contentHeader, AMQShortString exchange,
                                     AMQShortString routingKey, AMQSession_0_8.DestinationCache<AMQQueue> queueDestinationCache,
                                                         AMQSession_0_8.DestinationCache<AMQTopic> topicDestinationCache)
    {
        this(contentHeader, deliveryTag);

        Integer type = contentHeader.getHeaders().getInteger(CustomJMSXProperty.JMS_QPID_DESTTYPE.getShortStringName());

        AMQDestination dest = null;

        // If we have a type set the attempt to use that.
        if (type != null)
        {
            switch (type.intValue())
            {
                case AMQDestination.QUEUE_TYPE:
                    dest = queueDestinationCache.getDestination(exchange, routingKey);
                    break;
                case AMQDestination.TOPIC_TYPE:
                    dest = topicDestinationCache.getDestination(exchange, routingKey);
                    break;
                default:
                    // Use the generateDestination method
                    dest = null;
            }
        }

        if (dest == null)
        {
            dest = generateDestination(exchange, routingKey);
        }

        setJMSDestination(dest);
    }



    public String getJMSMessageID() throws JMSException
    {
        return getContentHeaderProperties().getMessageIdAsString();
    }

    public void setJMSMessageID(String messageId) throws JMSException
    {
        if (messageId != null)
        {
            getContentHeaderProperties().setMessageId(messageId);
        }
    }

    public void setJMSMessageID(UUID messageId) throws JMSException
    {
        if (messageId != null)
        {
            getContentHeaderProperties().setMessageId(asShortStringMsgId(messageId));
        }
    }

    private static final byte[] HEX_DIGITS = {0x30,0x31,0x32,0x33,0x34,0x35,0x36,0x37,0x38,0x39,
                                              0x61,0x62,0x63,0x64,0x65,0x66};

    private static AMQShortString asShortStringMsgId(UUID messageId)
    {
        long msb = messageId.getMostSignificantBits();
        long lsb = messageId.getLeastSignificantBits();

        byte[] messageIdBytes = new byte[39];
        messageIdBytes[0] = (byte) 'I';
        messageIdBytes[1] = (byte) 'D';
        messageIdBytes[2] = (byte) ':';

        messageIdBytes[3] = HEX_DIGITS[(int)((msb >> 60) & 0xFl)];
        messageIdBytes[4] = HEX_DIGITS[(int)((msb >> 56) & 0xFl)];
        messageIdBytes[5] = HEX_DIGITS[(int)((msb >> 52) & 0xFl)];
        messageIdBytes[6] = HEX_DIGITS[(int)((msb >> 48) & 0xFl)];
        messageIdBytes[7] = HEX_DIGITS[(int)((msb >> 44) & 0xFl)];
        messageIdBytes[8] = HEX_DIGITS[(int)((msb >> 40) & 0xFl)];
        messageIdBytes[9] = HEX_DIGITS[(int)((msb >> 36) & 0xFl)];
        messageIdBytes[10] = HEX_DIGITS[(int)((msb >> 32) & 0xFl)];

        messageIdBytes[11] = (byte) '-';
        messageIdBytes[12] = HEX_DIGITS[(int)((msb >> 28) & 0xFl)];
        messageIdBytes[13] = HEX_DIGITS[(int)((msb >> 24) & 0xFl)];
        messageIdBytes[14] = HEX_DIGITS[(int)((msb >> 20) & 0xFl)];
        messageIdBytes[15] = HEX_DIGITS[(int)((msb >> 16) & 0xFl)];
        messageIdBytes[16] = (byte) '-';
        messageIdBytes[17] = HEX_DIGITS[(int)((msb >> 12) & 0xFl)];
        messageIdBytes[18] = HEX_DIGITS[(int)((msb >> 8) & 0xFl)];
        messageIdBytes[19] = HEX_DIGITS[(int)((msb >> 4) & 0xFl)];
        messageIdBytes[20] = HEX_DIGITS[(int)(msb & 0xFl)];
        messageIdBytes[21] = (byte) '-';

        messageIdBytes[22] = HEX_DIGITS[(int)((lsb >> 60) & 0xFl)];
        messageIdBytes[23] = HEX_DIGITS[(int)((lsb >> 56) & 0xFl)];
        messageIdBytes[24] = HEX_DIGITS[(int)((lsb >> 52) & 0xFl)];
        messageIdBytes[25] = HEX_DIGITS[(int)((lsb >> 48) & 0xFl)];

        messageIdBytes[26] = (byte) '-';

        messageIdBytes[27] = HEX_DIGITS[(int)((lsb >> 44) & 0xFl)];
        messageIdBytes[28] = HEX_DIGITS[(int)((lsb >> 40) & 0xFl)];
        messageIdBytes[29] = HEX_DIGITS[(int)((lsb >> 36) & 0xFl)];
        messageIdBytes[30] = HEX_DIGITS[(int)((lsb >> 32) & 0xFl)];
        messageIdBytes[31] = HEX_DIGITS[(int)((lsb >> 28) & 0xFl)];
        messageIdBytes[32] = HEX_DIGITS[(int)((lsb >> 24) & 0xFl)];
        messageIdBytes[33] = HEX_DIGITS[(int)((lsb >> 20) & 0xFl)];
        messageIdBytes[34] = HEX_DIGITS[(int)((lsb >> 16) & 0xFl)];
        messageIdBytes[35] = HEX_DIGITS[(int)((lsb >> 12) & 0xFl)];
        messageIdBytes[36] = HEX_DIGITS[(int)((lsb >> 8) & 0xFl)];
        messageIdBytes[37] = HEX_DIGITS[(int)((lsb >> 4) & 0xFl)];
        messageIdBytes[38] = HEX_DIGITS[(int)(lsb & 0xFl)];

        return new AMQShortString(messageIdBytes,0,39);
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
            Destination dest = _destinationCache.get(replyToEncoding);
            if (dest == null)
            {
                try
                {
                    BindingURL binding = new AMQBindingURL(replyToEncoding);
                    dest = AMQDestination.createDestination(binding);
                }
                catch (URISyntaxException e)
                {
                    if(replyToEncoding.startsWith("/"))
                    {
                        dest = new DefaultRouterDestination(replyToEncoding);
                    }
                    else if(replyToEncoding.contains("/"))
                    {
                        String[] parts = replyToEncoding.split("/",2);
                        dest = new NonBURLReplyToDestination(parts[0], parts[1]);


                    }
                    else
                    {
                        if(getAMQSession().isQueueBound(AMQShortString.valueOf(replyToEncoding), null, null))
                        {
                            dest = new NonBURLReplyToDestination(replyToEncoding, "");
                        }
                        else
                        {
                            dest = new DefaultRouterDestination(replyToEncoding);
                        }
                    }
                    
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
            getContentHeaderProperties().setReplyTo((String) null);
            return; // We're done here
        }

        if (!(destination instanceof AMQDestination))
        {
            throw new IllegalArgumentException(
                "ReplyTo destination may only be an AMQDestination - passed argument was type " + destination.getClass());
        }

        final AMQDestination amqd = (AMQDestination) destination;

        final AMQShortString encodedDestination = amqd.getEncodedName();
        _destinationCache.put(encodedDestination.asString(), destination);
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

    public void setContentType(String contentType)
    {
        getContentHeaderProperties().setContentType(contentType);
    }

    public String getContentType()
    {
        return getContentHeaderProperties().getContentTypeAsString();
    }

    public void setEncoding(String encoding)
    {
        getContentHeaderProperties().setEncoding(encoding);
    }

    public String getEncoding()
    {
        return getContentHeaderProperties().getEncodingAsString();
    }

    public String getReplyToString()
    {
        return getContentHeaderProperties().getReplyToAsString();
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
        return _contentHeaderProperties;
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



    public boolean propertyExists(String propertyName) throws JMSException
    {
        return getJmsHeaders().propertyExists(propertyName);
    }

    public boolean getBooleanProperty(String propertyName) throws JMSException
    {
        if (STRICT_AMQP_COMPLIANCE)
        {
            throw new UnsupportedOperationException("JMS Properties not supported in AMQP");
        }

        return getJmsHeaders().getBoolean(propertyName);
    }

    public byte getByteProperty(String propertyName) throws JMSException
    {
        if (STRICT_AMQP_COMPLIANCE)
        {
            throw new UnsupportedOperationException("JMS Properties not supported in AMQP");
        }

        return getJmsHeaders().getByte(propertyName);
    }

    public short getShortProperty(String propertyName) throws JMSException
    {
        if (STRICT_AMQP_COMPLIANCE)
        {
            throw new UnsupportedOperationException("JMS Properties not supported in AMQP");
        }

        return getJmsHeaders().getShort(propertyName);
    }

    public int getIntProperty(String propertyName) throws JMSException
    {
        if (STRICT_AMQP_COMPLIANCE)
        {
            throw new UnsupportedOperationException("JMS Properties not supported in AMQP");
        }

        return getJmsHeaders().getInteger(propertyName);
    }

    public long getLongProperty(String propertyName) throws JMSException
    {
        if (STRICT_AMQP_COMPLIANCE)
        {
            throw new UnsupportedOperationException("JMS Properties not supported in AMQP");
        }

        return getJmsHeaders().getLong(propertyName);
    }

    public float getFloatProperty(String propertyName) throws JMSException
    {
        if (STRICT_AMQP_COMPLIANCE)
        {
            throw new UnsupportedOperationException("JMS Properties not supported in AMQP");
        }

        return getJmsHeaders().getFloat(propertyName);
    }

    public double getDoubleProperty(String propertyName) throws JMSException
    {
        if (STRICT_AMQP_COMPLIANCE)
        {
            throw new UnsupportedOperationException("JMS Properties not supported in AMQP");
        }

        return getJmsHeaders().getDouble(propertyName);
    }

    public String getStringProperty(String propertyName) throws JMSException
    {
        //NOTE: if the JMSX Property is a non AMQP property then we must check _strictAMQP and throw as below.
        if (propertyName.equals(CustomJMSXProperty.JMSXUserID.toString()))
        {
            return _contentHeaderProperties.getUserIdAsString();
        }
        else
        {
            if (STRICT_AMQP_COMPLIANCE)
            {
                throw new UnsupportedOperationException("JMS Properties not supported in AMQP");
            }

            return getJmsHeaders().getString(propertyName);
        }
    }

    public Object getObjectProperty(String propertyName) throws JMSException
    {
        return getJmsHeaders().getObject(propertyName);
    }

    public Enumeration getPropertyNames() throws JMSException
    {
        return getJmsHeaders().getPropertyNames();
    }

    public void setBooleanProperty(String propertyName, boolean b) throws JMSException
    {
        if (STRICT_AMQP_COMPLIANCE)
        {
            throw new UnsupportedOperationException("JMS Properties not supported in AMQP");
        }

        checkWritableProperties();
        getJmsHeaders().setBoolean(propertyName, b);
    }

    public void setByteProperty(String propertyName, byte b) throws JMSException
    {
        if (STRICT_AMQP_COMPLIANCE)
        {
            throw new UnsupportedOperationException("JMS Properties not supported in AMQP");
        }

        checkWritableProperties();
        getJmsHeaders().setByte(propertyName, b);
    }

    public void setShortProperty(String propertyName, short i) throws JMSException
    {
        if (STRICT_AMQP_COMPLIANCE)
        {
            throw new UnsupportedOperationException("JMS Properties not supported in AMQP");
        }

        checkWritableProperties();
        getJmsHeaders().setShort(propertyName, i);
    }

    public void setIntProperty(String propertyName, int i) throws JMSException
    {
        checkWritableProperties();
        getJmsHeaders().setInteger(propertyName, i);
    }

    public void setLongProperty(String propertyName, long l) throws JMSException
    {
        if (STRICT_AMQP_COMPLIANCE)
        {
            throw new UnsupportedOperationException("JMS Properties not supported in AMQP");
        }

        checkWritableProperties();
        getJmsHeaders().setLong(propertyName, l);
    }

    public void setFloatProperty(String propertyName, float f) throws JMSException
    {
        if (STRICT_AMQP_COMPLIANCE)
        {
            throw new UnsupportedOperationException("JMS Properties not supported in AMQP");
        }

        checkWritableProperties();
        getJmsHeaders().setFloat(propertyName, f);
    }

    public void setDoubleProperty(String propertyName, double v) throws JMSException
    {
        if (STRICT_AMQP_COMPLIANCE)
        {
            throw new UnsupportedOperationException("JMS Properties not supported in AMQP");
        }

        checkWritableProperties();
        getJmsHeaders().setDouble(propertyName, new Double(v));
    }

    public void setStringProperty(String propertyName, String value) throws JMSException
    {
        checkWritableProperties();
        getJmsHeaders().setString(propertyName, value);
    }

    public void setObjectProperty(String propertyName, Object object) throws JMSException
    {
        checkWritableProperties();
        getJmsHeaders().setObject(propertyName, object);
    }

    public void removeProperty(String propertyName) throws JMSException
    {
        getJmsHeaders().remove(propertyName);
    }


    private JMSHeaderAdapter getJmsHeaders()
    {
        return _headerAdapter;
    }

    protected void checkWritableProperties() throws MessageNotWriteableException
    {
        if (_readableProperties)
        {
            throw new MessageNotWriteableException("You need to call clearProperties() to make the message writable");
        }
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

    private static class DefaultRouterDestination extends AMQDestination implements Queue
    {
        private static final long serialVersionUID = -5042408431861384536L;

        public DefaultRouterDestination(final String replyToEncoding)
        {
            super(AMQShortString.EMPTY_STRING,
                  AMQShortString.valueOf("direct"),
                  AMQShortString.valueOf(replyToEncoding),
                  AMQShortString.valueOf(replyToEncoding));
        }

        @Override
        public boolean isNameRequired()
        {
            return false;
        }

        @Override
        public boolean neverDeclare()
        {
            return true;
        }
    }

    private static class NonBURLReplyToDestination extends AMQDestination implements Queue
    {
        private static final long serialVersionUID = 122897705932489259L;

        public NonBURLReplyToDestination(final String exchange, final String routingKey)
        {
            super(AMQShortString.valueOf(exchange),
                  null,
                  AMQShortString.valueOf(routingKey),
                  AMQShortString.valueOf(routingKey));
        }

        @Override
        public boolean isNameRequired()
        {
            return false;
        }

        @Override
        public boolean neverDeclare()
        {
            return true;
        }
    }
}
