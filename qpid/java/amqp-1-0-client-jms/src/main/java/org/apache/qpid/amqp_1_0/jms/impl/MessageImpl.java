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

package org.apache.qpid.amqp_1_0.jms.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;

import org.apache.qpid.amqp_1_0.jms.Message;
import org.apache.qpid.amqp_1_0.jms.impl.util.AnnotationDecoder;
import org.apache.qpid.amqp_1_0.jms.impl.util.AnnotationEncoder;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedByte;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.qpid.amqp_1_0.type.UnsignedShort;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.DeliveryAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Footer;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.MessageAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

public abstract class MessageImpl implements Message
{
    static final Set<Class> _supportedClasses =
                new HashSet<Class>(Arrays.asList(Boolean.class, Byte.class, Short.class, Integer.class, Long.class,
                                                 Float.class, Double.class, Character.class, String.class, byte[].class));

    static final Symbol JMS_TYPE = Symbol.valueOf("x-opt-jms-type");
    static final Symbol TO_TYPE = Symbol.valueOf("x-opt-to-type");
    static final Symbol REPLY_TO_TYPE = Symbol.valueOf("x-opt-reply-type");

    static final Collection<Symbol> SYSTEM_MESSAGE_ANNOTATIONS = Arrays.asList(JMS_TYPE, TO_TYPE, REPLY_TO_TYPE);

    static final String QUEUE_ATTRIBUTE = "queue";
    static final String TOPIC_ATTRIBUTE = "topic";
    static final String TEMPORARY_ATTRIBUTE = "temporary";

    static final Set<String> JMS_QUEUE_ATTRIBUTES = set(QUEUE_ATTRIBUTE);
    static final Set<String> JMS_TOPIC_ATTRIBUTES = set(TOPIC_ATTRIBUTE);
    static final Set<String> JMS_TEMP_QUEUE_ATTRIBUTES = set(QUEUE_ATTRIBUTE, TEMPORARY_ATTRIBUTE);
    static final Set<String> JMS_TEMP_TOPIC_ATTRIBUTES = set(TOPIC_ATTRIBUTE, TEMPORARY_ATTRIBUTE);

    private static final String JMSXGROUP_ID = "JMSXGroupID";
    public static final String JMS_AMQP_MESSAGE_ANNOTATIONS = "JMS_AMQP_MESSAGE_ANNOTATIONS";
    public static final String JMS_AMQP_DELIVERY_ANNOTATIONS = "JMS_AMQP_DELIVERY_ANNOTATIONS";

    private Header _header;
    private Properties _properties;
    private ApplicationProperties _applicationProperties;
    private Footer _footer;
    private final SessionImpl _sessionImpl;
    private boolean _readOnly;
    private DeliveryAnnotations _deliveryAnnotations;
    private MessageAnnotations _messageAnnotations;

    private boolean _isFromQueue;
    private boolean _isFromTopic;
    private long _expiration;
    private DestinationImpl _replyTo;

    protected MessageImpl(Header header,
                          DeliveryAnnotations deliveryAnnotations,
                          MessageAnnotations messageAnnotations,
                          Properties properties,
                          ApplicationProperties appProperties,
                          Footer footer,
                          SessionImpl session)
    {
        _header = header == null ? new Header() : header;
        _properties = properties == null ? new Properties() : properties;
        _messageAnnotations = messageAnnotations == null ? new MessageAnnotations(new HashMap()) : messageAnnotations;
        _deliveryAnnotations = deliveryAnnotations == null ? new DeliveryAnnotations(new HashMap()) : deliveryAnnotations;

        _footer = footer == null ? new Footer(Collections.EMPTY_MAP) : footer;
        _applicationProperties = appProperties == null ? new ApplicationProperties(new HashMap()) : appProperties;
        _sessionImpl = session;
    }

    public String getJMSMessageID() throws JMSException
    {
        Object messageId = getMessageId();

        return messageId == null ? null : "ID:"+messageId.toString();
    }

    public void setJMSMessageID(String messageId) throws InvalidJMSMEssageIdException
    {
        if(messageId == null)
        {
            setMessageId(null);
        }
        else if(messageId.startsWith("ID:"))
        {
            setMessageId(messageId.substring(3));
        }
        else
        {
            throw new InvalidJMSMEssageIdException(messageId);
        }
    }

    public long getJMSTimestamp() throws JMSException
    {
        Date transmitTime = getTransmitTime();
        return transmitTime == null ? 0 : transmitTime.getTime();
    }

    public void setJMSTimestamp(long l) throws JMSException
    {
        setTransmitTime(new Date(l));
        if(_expiration != 0l)
        {
            setTtl(UnsignedInteger.valueOf(_expiration-getTransmitTime().getTime()));
        }
    }

    public byte[] getJMSCorrelationIDAsBytes() throws JMSException
    {

        Object o = getCorrelationId();
        if(o instanceof Binary)
        {
            Binary correlationIdBinary = (Binary) o;
            byte[] correlationId = new byte[correlationIdBinary.getLength()];
            correlationIdBinary.asByteBuffer().get(correlationId);
            return correlationId;
        }
        else
        {
            return o == null ? null : o.toString().getBytes();
        }

    }

    public void setJMSCorrelationIDAsBytes(byte[] correlationId) throws JMSException
    {
        if(correlationId == null)
        {
            setCorrelationId(null);
        }
        else
        {
            byte[] dup = new byte[correlationId.length];
            System.arraycopy(correlationId,0,dup,0,correlationId.length);
            setCorrelationId(new Binary(dup));
        }
    }

    public void setJMSCorrelationID(String s) throws JMSException
    {
        getProperties().setCorrelationId(s);
    }

    public String getJMSCorrelationID() throws JMSException
    {
        Object o = getProperties().getCorrelationId();
        if(o instanceof Binary)
        {
            Binary id = (Binary) o;
            return new String(id.getArray(), id.getArrayOffset(), id.getLength());
        }
        else
        {
            return o == null ? null : o.toString();
        }
    }

    public DestinationImpl getJMSReplyTo() throws JMSException
    {
        return _replyTo != null ? _replyTo : toDestination(getReplyTo(), splitCommaSeparateSet((String) getMessageAnnotation(REPLY_TO_TYPE)));
    }

    public void setJMSReplyTo(Destination destination) throws NonAMQPDestinationException
    {
        _replyTo = (DestinationImpl) destination;
        if( destination==null )
        {
            setReplyTo(null);
            messageAnnotationMap().remove(REPLY_TO_TYPE);
        }
        else
        {
            if(_replyTo.getLocalTerminus() != null)
            {
                setReplyTo(_replyTo.getLocalTerminus());
            }
            else
            {
                DecodedDestination dd = toDecodedDestination(destination);
                setReplyTo(dd.getAddress());
                messageAnnotationMap().put(REPLY_TO_TYPE, join(",", dd.getAttributes()));
            }
        }
    }

    public DestinationImpl getJMSDestination() throws JMSException
    {
        Set<String> type = splitCommaSeparateSet((String) getMessageAnnotation(TO_TYPE));
        if( type==null )
        {
            if( _isFromQueue )
            {
                type = JMS_QUEUE_ATTRIBUTES;
            }
            else if( _isFromTopic )
            {
                type = JMS_TOPIC_ATTRIBUTES;
            }
        }
        return toDestination(getTo(), type);
    }

    public void setJMSDestination(Destination destination) throws NonAMQPDestinationException
    {
        if( destination==null )
        {
            setTo(null);
            messageAnnotationMap().remove(TO_TYPE);
        }
        else
        {
            DecodedDestination dd = toDecodedDestination(destination);
            setTo(dd.getAddress());
            messageAnnotationMap().put(TO_TYPE, join(",", dd.getAttributes()));
        }
    }

    public int getJMSDeliveryMode() throws JMSException
    {
        if(Boolean.FALSE.equals(getDurable()))
        {
            return DeliveryMode.NON_PERSISTENT;
        }
        else
        {
            return DeliveryMode.PERSISTENT;
        }
    }

    public void setJMSDeliveryMode(int deliveryMode) throws JMSException
    {
        switch(deliveryMode)
        {
            case DeliveryMode.NON_PERSISTENT:
                setDurable(false);
                break;
            case DeliveryMode.PERSISTENT:
                setDurable(true);
                break;
            default:
                //TODO
        }
    }

    public boolean getJMSRedelivered()
    {
        UnsignedInteger failures = getDeliveryFailures();
        return failures != null && (failures.intValue() != 0);
    }

    public void setJMSRedelivered(boolean redelivered)
    {
        UnsignedInteger failures = getDeliveryFailures();
        if(redelivered)
        {
            if(failures == null || UnsignedInteger.ZERO.equals(failures))
            {
                setDeliveryFailures(UnsignedInteger.ONE);
            }
        }
        else
        {
            setDeliveryFailures(null);
        }
    }

    public String getJMSType() throws JMSException
    {
        final Object attrValue = getMessageAnnotation(JMS_TYPE);
        return attrValue instanceof String ? attrValue.toString() : null;
    }

    public void setJMSType(String s) throws JMSException
    {
        messageAnnotationMap().put(JMS_TYPE, s);
    }

    public long getJMSExpiration() throws JMSException
    {
        final UnsignedInteger ttl = getTtl();
        return ttl == null || ttl.longValue() == 0 ? 0 : getJMSTimestamp() + ttl.longValue();
    }

    public void setJMSExpiration(long l) throws JMSException
    {
        _expiration = l;
        if(l == 0)
        {
            setTtl(UnsignedInteger.ZERO);
        }
        else
        {
            if(getTransmitTime() == null)
            {
                setTransmitTime(new Date());
            }
            setTtl(UnsignedInteger.valueOf(l - getTransmitTime().getTime()));
        }
    }

    public int getJMSPriority() throws JMSException
    {
        UnsignedByte priority = getPriority();
        return priority == null ? DEFAULT_PRIORITY : priority.intValue();
    }

    public void setJMSPriority(int i) throws InvalidJMSPriorityException
    {
        if(i >= 0 && i <= 255)
        {
            setPriority(UnsignedByte.valueOf((byte)i));
        }
        else
        {
            throw new InvalidJMSPriorityException(i);
        }
    }

    public void clearProperties() throws JMSException
    {
        _applicationProperties.getValue().clear();
    }

    public boolean propertyExists(final String s) throws JMSException
    {
        return propertyExists((Object) s);
    }

    public boolean getBooleanProperty(final String s) throws JMSException
    {
        return getBooleanProperty((Object) s);
    }

    public byte getByteProperty(final String s) throws JMSException
    {
        return getByteProperty((Object)s);
    }

    public short getShortProperty(final String s) throws JMSException
    {
        return getShortProperty((Object)s);
    }

    public int getIntProperty(final String s) throws JMSException
    {
        return getIntProperty((Object)s);
    }

    public long getLongProperty(final String s) throws JMSException
    {
        return getLongProperty((Object)s);
    }

    public float getFloatProperty(final String s) throws JMSException
    {
        return getFloatProperty((Object)s);
    }

    public double getDoubleProperty(final String s) throws JMSException
    {
        return getDoubleProperty((Object)s);
    }

    public String getStringProperty(final String s) throws JMSException
    {
        return getStringProperty((Object)s);
    }

    public Object getObjectProperty(final String s) throws JMSException
    {
        return getObjectProperty((Object)s);
    }

    public boolean propertyExists(Object name) throws JMSException
    {
        return _applicationProperties.getValue().containsKey(name);
    }

    public boolean getBooleanProperty(Object name) throws JMSException
    {

        Object value = getProperty(name);

        if (value instanceof Boolean)
        {
            return ((Boolean) value).booleanValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            return Boolean.valueOf((String) value);
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to boolean.");
        }
    }

    public byte getByteProperty(Object name) throws JMSException
    {
        Object value = getProperty(name);

        if (value instanceof Byte)
        {
            return ((Byte) value).byteValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            return Byte.valueOf((String) value).byteValue();
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to byte.");
        }
    }

    public short getShortProperty(Object name) throws JMSException
    {
        Object value = getProperty(name);

        if (value instanceof Short)
        {
            return ((Short) value).shortValue();
        }
        else if (value instanceof Byte)
        {
            return ((Byte) value).shortValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            return Short.valueOf((String) value).shortValue();
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to short.");
        }
    }

    private Object getProperty(final Object name)
    {
        if(JMSXGROUP_ID.equals(name))
        {
            return _properties.getGroupId();
        }
        else if(JMS_AMQP_DELIVERY_ANNOTATIONS.equals(name))
        {
            Map annotationsMap = deliveryAnnotationsMap();
            if(annotationsMap.isEmpty())
            {
                return null;
            }
            else
            {
                try
                {
                    return new AnnotationEncoder().encode(annotationsMap);
                }
                catch (IOException e)
                {
                    throw new IllegalArgumentException(e);
                }
            }
        }
        else if(JMS_AMQP_MESSAGE_ANNOTATIONS.equals(name))
        {
            Map annotationsMap = new LinkedHashMap(messageAnnotationMap());
            for(Symbol s : SYSTEM_MESSAGE_ANNOTATIONS)
            {
                annotationsMap.remove(s);
            }

            if(annotationsMap.isEmpty())
            {
                return null;
            }
            else
            {
                try
                {
                    return new AnnotationEncoder().encode(annotationsMap);
                }
                catch (IOException e)
                {
                    throw new IllegalArgumentException(e);
                }
            }
        }
        return _applicationProperties.getValue().get(name);
    }

    public int getIntProperty(Object name) throws JMSException
    {
        Object value = getProperty(name);

        if (value instanceof Integer)
        {
            return ((Integer) value).intValue();
        }
        else if (value instanceof Short)
        {
            return ((Short) value).intValue();
        }
        else if (value instanceof Byte)
        {
            return ((Byte) value).intValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            return Integer.valueOf((String) value).intValue();
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to int.");
        }
    }

    public long getLongProperty(Object name) throws JMSException
    {
        Object value = getProperty(name);

        if (value instanceof Long)
        {
            return ((Long) value).longValue();
        }
        else if (value instanceof Integer)
        {
            return ((Integer) value).longValue();
        }

        if (value instanceof Short)
        {
            return ((Short) value).longValue();
        }

        if (value instanceof Byte)
        {
            return ((Byte) value).longValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            return Long.valueOf((String) value).longValue();
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to long.");
        }
    }

    public float getFloatProperty(Object name) throws JMSException
    {
        Object value = getProperty(name);

        if (value instanceof Float)
        {
            return ((Float) value).floatValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            return Float.valueOf((String) value).floatValue();
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to float.");
        }
    }

    public double getDoubleProperty(Object name) throws JMSException
    {
        Object value = getProperty(name);

        if (value instanceof Double)
        {
            return ((Double) value).doubleValue();
        }
        else if (value instanceof Float)
        {
            return ((Float) value).doubleValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            return Double.valueOf((String) value).doubleValue();
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to double.");
        }
    }

    public String getStringProperty(Object name) throws JMSException
    {
        Object value = getProperty(name);

        if ((value instanceof String) || (value == null))
        {
            return (String) value;
        }
        else if (value instanceof byte[])
        {
            throw new MessageFormatException("Property " + name + " of type byte[] " + "cannot be converted to String.");
        }
        else
        {
            return value.toString();
        }
    }

    public Object getObjectProperty(Object name) throws JMSException
    {
        return getProperty(name);
    }

    public List<Object> getListProperty(final Object name) throws JMSException
    {
        Object value = getProperty(name);
        if(value instanceof List || value == null)
        {
            return (List<Object>)value;
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to List.");
        }
    }

    public Map<Object, Object> getMapProperty(final Object name) throws JMSException
    {
        Object value = getProperty(name);
        if(value instanceof Map || value == null)
        {
            return (Map<Object,Object>)value;
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to Map.");
        }
    }

    public UnsignedByte getUnsignedByteProperty(final Object name) throws JMSException
    {
        Object value = getProperty(name);

        if (value instanceof UnsignedByte)
        {
            return (UnsignedByte) value;
        }
        else if ((value instanceof String) || (value == null))
        {
            return UnsignedByte.valueOf((String) value);
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to UnsignedByte.");
        }
    }

    public UnsignedShort getUnsignedShortProperty(final Object name) throws JMSException
    {
        Object value = getProperty(name);

        if (value instanceof UnsignedShort)
        {
            return (UnsignedShort) value;
        }
        else if (value instanceof UnsignedByte)
        {
            return UnsignedShort.valueOf(((UnsignedByte)value).shortValue());
        }
        else if ((value instanceof String) || (value == null))
        {
            return UnsignedShort.valueOf((String) value);
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to UnsignedShort.");
        }
    }

    public UnsignedInteger getUnsignedIntProperty(final Object name) throws JMSException
    {
        Object value = getProperty(name);

        if (value instanceof UnsignedInteger)
        {
            return (UnsignedInteger) value;
        }
        else if (value instanceof UnsignedByte)
        {
            return UnsignedInteger.valueOf(((UnsignedByte)value).intValue());
        }
        else if (value instanceof UnsignedShort)
        {
            return UnsignedInteger.valueOf(((UnsignedShort)value).intValue());
        }
        else if ((value instanceof String) || (value == null))
        {
            return UnsignedInteger.valueOf((String) value);
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to UnsignedShort.");
        }
    }

    public UnsignedLong getUnsignedLongProperty(final Object name) throws JMSException
    {
        Object value = getProperty(name);

        if (value instanceof UnsignedLong)
        {
            return (UnsignedLong) value;
        }
        else if (value instanceof UnsignedByte)
        {
            return UnsignedLong.valueOf(((UnsignedByte)value).longValue());
        }
        else if (value instanceof UnsignedShort)
        {
            return UnsignedLong.valueOf(((UnsignedShort)value).longValue());
        }
        else if (value instanceof UnsignedInteger)
        {
            return UnsignedLong.valueOf(((UnsignedInteger)value).longValue());
        }
        else if ((value instanceof String) || (value == null))
        {
            return UnsignedLong.valueOf((String) value);
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to UnsignedShort.");
        }
    }

    public Enumeration getPropertyNames() throws JMSException
    {
        final Collection<String> names = new ArrayList<String>();
        for(Object key : _applicationProperties.getValue().keySet())
        {
            if(key instanceof String)
            {
                names.add((String)key);
            }
        }
        if(_properties.getGroupId() != null)
        {
            names.add(JMSXGROUP_ID);
        }
        if(!deliveryAnnotationsMap().isEmpty())
        {
            names.add(JMS_AMQP_DELIVERY_ANNOTATIONS);
        }
        if(!messageAnnotationMap().isEmpty())
        {
            boolean nonDefaultAnnotation = false;
            for(Object key : messageAnnotationMap().keySet())
            {
                if(!SYSTEM_MESSAGE_ANNOTATIONS.contains(key))
                {
                    nonDefaultAnnotation = true;
                    break;
                }
            }

            if(nonDefaultAnnotation)
            {
                names.add(JMS_AMQP_MESSAGE_ANNOTATIONS);
            }
        }
        return Collections.enumeration(names);
    }

    public void setBooleanProperty(final String s, final boolean b) throws JMSException
    {
        checkWritable();
        checkPropertyName(s);
        setBooleanProperty((Object)s, b);
    }

    protected void checkPropertyName(CharSequence propertyName)
    {
        if (propertyName == null)
        {
            throw new IllegalArgumentException("Property name must not be null");
        }
        else if (propertyName.length() == 0)
        {
            throw new IllegalArgumentException("Property name must not be the empty string");
        }

        checkIdentiferFormat(propertyName);
    }

    protected void checkIdentiferFormat(CharSequence propertyName)
    {
//        JMS requirements 3.5.1 Property Names
//        Identifiers:
//        - An identifier is an unlimited-length character sequence that must begin
//          with a Java identifier start character; all following characters must be Java
//          identifier part characters. An identifier start character is any character for
//          which the method Character.isJavaIdentifierStart returns true. This includes
//          '_' and '$'. An identifier part character is any character for which the
//          method Character.isJavaIdentifierPart returns true.
//        - Identifiers cannot be the names NULL, TRUE, or FALSE.
//          Identifiers cannot be NOT, AND, OR, BETWEEN, LIKE, IN, IS, or
//          ESCAPE.
//          Identifiers are either header field references or property references. The
//          type of a property value in a message selector corresponds to the type
//          used to set the property. If a property that does not exist in a message is
//          referenced, its value is NULL. The semantics of evaluating NULL values
//          in a selector are described in Section 3.8.1.2, Null Values.
//          The conversions that apply to the get methods for properties do not
//          apply when a property is used in a message selector expression. For
//          example, suppose you set a property as a string value, as in the
//          following:
//              myMessage.setStringProperty("NumberOfOrders", "2")
//          The following expression in a message selector would evaluate to false,
//          because a string cannot be used in an arithmetic expression:
//          "NumberOfOrders > 1"
//          Identifiers are case sensitive.
//          Message header field references are restricted to JMSDeliveryMode,
//          JMSPriority, JMSMessageID, JMSTimestamp, JMSCorrelationID, and
//          JMSType. JMSMessageID, JMSCorrelationID, and JMSType values may be
//          null and if so are treated as a NULL value.

        // JMS start character
        if (!(Character.isJavaIdentifierStart(propertyName.charAt(0))))
        {
            throw new IllegalArgumentException("Identifier '" + propertyName + "' does not start with a valid JMS identifier start character");
        }

        // JMS part character
        int length = propertyName.length();
        for (int c = 1; c < length; c++)
        {
            if (!(Character.isJavaIdentifierPart(propertyName.charAt(c))))
            {
                throw new IllegalArgumentException("Identifier '" + propertyName + "' contains an invalid JMS identifier character");
            }
        }

        // JMS invalid names
        if ((propertyName.equals("NULL")
             || propertyName.equals("TRUE")
             || propertyName.equals("FALSE")
             || propertyName.equals("NOT")
             || propertyName.equals("AND")
             || propertyName.equals("OR")
             || propertyName.equals("BETWEEN")
             || propertyName.equals("LIKE")
             || propertyName.equals("IN")
             || propertyName.equals("IS")
             || propertyName.equals("ESCAPE")))
        {
            throw new IllegalArgumentException("Identifier '" + propertyName + "' is not allowed in JMS");
        }

    }

    public void setByteProperty(final String s, final byte b) throws JMSException
    {
        checkWritable();
        checkPropertyName(s);

        setByteProperty((Object)s, b);
    }

    public void setShortProperty(final String s, final short i) throws JMSException
    {
        checkWritable();
        checkPropertyName(s);

        setShortProperty((Object)s, i);
    }

    public void setIntProperty(final String s, final int i) throws JMSException
    {
        checkWritable();
        checkPropertyName(s);

        setIntProperty((Object)s, i);
    }

    public void setLongProperty(final String s, final long l) throws JMSException
    {
        checkWritable();
        checkPropertyName(s);

        setLongProperty((Object)s, l);
    }

    public void setFloatProperty(final String s, final float v) throws JMSException
    {
        checkWritable();
        checkPropertyName(s);

        setFloatProperty((Object) s, v);
    }

    public void setDoubleProperty(final String s, final double v) throws JMSException
    {
        checkWritable();
        checkPropertyName(s);

        setDoubleProperty((Object)s, v);
    }

    public void setStringProperty(final String s, final String s1) throws JMSException
    {
        checkWritable();
        checkPropertyName(s);

        setStringProperty((Object)s, s1);
    }

    public void setObjectProperty(final String s, final Object o) throws JMSException
    {
        checkWritable();
        checkPropertyName(s);

        if(o != null && (_supportedClasses.contains(o.getClass())))
        {
            setObjectProperty((Object)s, o);
        }
        else
        {
            throw new MessageFormatException("Cannot call setObjectProperty with a value of " + ((o == null) ? "null" : " class "+o.getClass().getName()) + ".");
        }
    }

    public void setBooleanProperty(Object name, boolean b)
    {
        setProperty(name, b);
    }

    public void setByteProperty(Object name, byte b)
    {
        setProperty(name, b);
    }

    public void setShortProperty(Object name, short i)
    {
        setProperty(name, i);
    }

    public void setIntProperty(Object name, int i)
    {
        setProperty(name, i);
    }

    public void setLongProperty(Object name, long l)
    {
        setProperty(name, l);
    }

    public void setFloatProperty(Object name, float v)
    {
        setProperty(name, v);
    }

    public void setDoubleProperty(Object name, double v)
    {
        setProperty(name, v);
    }

    public void setStringProperty(Object name, String value)
    {
        setProperty(name, value);
    }

    public void setObjectProperty(Object name, Object value)
    {
        setProperty(name, value);
    }

    private void setProperty(Object name, Object value)
    {
        if(JMSXGROUP_ID.equals(name))
        {
            _properties.setGroupId(value == null ? null : value.toString());
        }
        else if(JMS_AMQP_MESSAGE_ANNOTATIONS.equals(name))
        {
            try
            {
                Map<Symbol, Object> annotationMap = new AnnotationDecoder().decode((String) value);
                Map messageAnnotations = messageAnnotationMap();
                Map tmp = new LinkedHashMap();
                for(Symbol key : SYSTEM_MESSAGE_ANNOTATIONS)
                {
                    if(messageAnnotations.containsKey(key))
                    {
                        tmp.put(key, messageAnnotations.get(key));
                    }
                }
                messageAnnotations.clear();
                messageAnnotations.putAll(annotationMap);
                messageAnnotations.putAll(tmp);
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException(e);
            }

        }
        else if(JMS_AMQP_DELIVERY_ANNOTATIONS.equals(name))
        {
            try
            {
                Map<Symbol, Object> annotationMap = new AnnotationDecoder().decode((String) value);
                Map deliveryAnnotations = deliveryAnnotationsMap();
                deliveryAnnotations.clear();
                deliveryAnnotations.putAll(annotationMap);
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException(e);
            }
        }
        else
        {
            _applicationProperties.getValue().put(name, value);
        }
    }

    public void setListProperty(final Object name, final List<Object> list)
    {
        setProperty(name, list);
    }

    public void setMapProperty(final Object name, final Map<Object, Object> map)
    {
        setProperty(name, map);
    }

    public void setUnsignedByteProperty(final Object name, final UnsignedByte b)
    {
        setProperty(name, b);
    }

    public void setUnsignedShortProperty(final Object name, final UnsignedShort s)
    {
        setProperty(name, s);
    }

    public void setUnsignedIntProperty(final Object name, final UnsignedInteger i)
    {
        setProperty(name, i);
    }

    public void setUnsignedLongProperty(final Object name, final UnsignedLong l)
    {
        setProperty(name, l);
    }

    public UnsignedInteger getDeliveryFailures()
    {
        return _header.getDeliveryCount();
    }

    public void setDeliveryFailures(UnsignedInteger failures)
    {
        _header.setDeliveryCount(failures);
    }

    public Boolean getDurable()
    {
        return _header.getDurable();
    }

    public void setDurable(final Boolean durable)
    {
        _header.setDurable(durable);
    }

    public UnsignedByte getPriority()
    {
        return _header.getPriority();
    }

    public void setPriority(final UnsignedByte priority)
    {
        _header.setPriority(priority);
    }

    public Date getTransmitTime()
    {
        return _properties.getCreationTime();
    }

    public void setTransmitTime(final Date transmitTime)
    {
        _properties.setCreationTime(transmitTime);
    }

    public UnsignedInteger getTtl()
    {
        return _header.getTtl();
    }

    public void setTtl(final UnsignedInteger ttl)
    {
        _header.setTtl(ttl);
    }

    public UnsignedInteger getFormerAcquirers()
    {
        return _header.getDeliveryCount();
    }

    public void setFormerAcquirers(final UnsignedInteger formerAcquirers)
    {
        _header.setDeliveryCount(formerAcquirers);
    }

    public Object getMessageId()
    {
        return _properties.getMessageId();
    }

    public void setMessageId(final Object messageId)
    {
        _properties.setMessageId(messageId);
    }

    public Binary getUserId()
    {
        return _properties.getUserId();
    }

    public void setUserId(final Binary userId)
    {
        _properties.setUserId(userId);
    }

    public String getTo()
    {
        return _properties.getTo();
    }

    public void setTo(final String to)
    {
        _properties.setTo(to);
    }

    public String getSubject()
    {
        return _properties.getSubject();
    }

    public void setSubject(final String subject)
    {
        _properties.setSubject(subject);
    }

    public String getReplyTo()
    {
        return _properties.getReplyTo();
    }

    public void setReplyTo(final String replyTo)
    {
        _properties.setReplyTo(replyTo);
    }

    public Object getCorrelationId()
    {
        return _properties.getCorrelationId();
    }

    public void setCorrelationId(final Binary correlationId)
    {
        _properties.setCorrelationId(correlationId);
    }

    public Symbol getContentType()
    {
        return _properties.getContentType();
    }

    public void setContentType(final Symbol contentType)
    {
        _properties.setContentType(contentType);
    }

    public void acknowledge() throws JMSException
    {
        _sessionImpl.acknowledgeAll();
    }

    public void clearBody() throws JMSException
    {
        _readOnly = false;
    }

    protected boolean isReadOnly()
    {
        return _readOnly;
    }

    protected void checkReadable() throws MessageNotReadableException
    {
        if (!isReadOnly())
        {
            throw new MessageNotReadableException("You need to call reset() to make the message readable");
        }
    }

    protected void checkWritable() throws MessageNotWriteableException
    {
        if (isReadOnly())
        {
            throw new MessageNotWriteableException("You need to call clearBody() to make the message writable");
        }
    }

    public void setReadOnly()
    {
        _readOnly = true;
    }

    private static class InvalidJMSMEssageIdException extends JMSException
    {
        public InvalidJMSMEssageIdException(String messageId)
        {
            super("Invalid JMSMessageID: '" + messageId + "', JMSMessageID MUST start with 'ID:'");
        }
    }

    private class NonAMQPDestinationException extends JMSException
    {
        public NonAMQPDestinationException(Destination destination)
        {
            super("Destinations not a valid AMQP Destination, class of type: '"
                    + destination.getClass().getName()
                    + "', require '"
                    + org.apache.qpid.amqp_1_0.jms.Destination.class.getName() + "'.");
        }
    }

    private class InvalidJMSPriorityException extends JMSException
    {
        public InvalidJMSPriorityException(int priority)
        {
            super("The provided priority: " + priority + " is not valid in AMQP, valid values are from 0 to 255");
        }
    }

    Header getHeader()
    {
        return _header;
    }

    Properties getProperties()
    {
        return _properties;
    }


    Footer getFooter()
    {
        return _footer;
    }

    MessageAnnotations getMessageAnnotations()
    {
        return _messageAnnotations;
    }


    DeliveryAnnotations getDeliveryAnnotations()
    {
        return _deliveryAnnotations;
    }

    public ApplicationProperties getApplicationProperties()
    {
        return _applicationProperties;
    }

    public void reset() throws JMSException
    {
        _readOnly = true;
    }

    void setFromQueue(final boolean fromQueue)
    {
        _isFromQueue = fromQueue;
    }

    void setFromTopic(final boolean fromTopic)
    {
        _isFromTopic = fromTopic;
    }

    abstract Collection<Section> getSections();

    DecodedDestination toDecodedDestination(Destination destination) throws NonAMQPDestinationException
    {
        if(destination == null)
        {
            return null;
        }
        if (destination instanceof DestinationImpl)
        {
            return _sessionImpl.getConnection().toDecodedDestination((DestinationImpl) destination);
        }
        throw new NonAMQPDestinationException(destination);
    }

    DestinationImpl toDestination(String address, Set<String> kind)
    {
        if( address == null )
        {
            return null;
        }

        // If destination prefixes are in play, we have to strip the the prefix, and we might
        // be able to infer the kind, if we don't know it yet.
        DecodedDestination decoded = _sessionImpl.getConnection().toDecodedDestination(address, kind);
        address = decoded.getAddress();
        kind = decoded.getAttributes();

        if( kind == null )
        {
            return DestinationImpl.valueOf(address);
        }
        if( kind.contains(QUEUE_ATTRIBUTE) )
        {
            if( kind.contains(TEMPORARY_ATTRIBUTE) )
            {
                return new TemporaryQueueImpl(address, null, _sessionImpl);
            }
            else
            {
                return QueueImpl.valueOf(address);
            }
        }
        else if ( kind.contains(TOPIC_ATTRIBUTE) )
        {
            if( kind.contains(TEMPORARY_ATTRIBUTE) )
            {
                return new TemporaryTopicImpl(address, null, _sessionImpl);
            }
            else
            {
                return TopicImpl.valueOf(address);
            }
        }

        return DestinationImpl.valueOf(address);
    }

    private Object getMessageAnnotation(Symbol key)
    {
        Map messageAttrs = _messageAnnotations == null ? null : _messageAnnotations.getValue();
        return messageAttrs == null ? null : messageAttrs.get(key);
    }

    private Map messageAnnotationMap()
    {
        Map messageAttrs = _messageAnnotations == null ? null : _messageAnnotations.getValue();
        if(messageAttrs == null)
        {
            messageAttrs = new HashMap();
            _messageAnnotations = new MessageAnnotations(messageAttrs);
        }
        return messageAttrs;
    }

    private Map deliveryAnnotationsMap()
    {
        Map deliveryAttrs = _deliveryAnnotations == null ? null : _deliveryAnnotations.getValue();
        if(deliveryAttrs == null)
        {
            deliveryAttrs = new HashMap();
            _deliveryAnnotations = new DeliveryAnnotations(deliveryAttrs);
        }
        return deliveryAttrs;
    }

    Set<String> splitCommaSeparateSet(String value)
    {
        if( value == null )
        {
            return null;
        }
        HashSet<String> rc = new HashSet<String>();
        for( String x: value.split("\\s*,\\s*") )
        {
            rc.add(x);
        }
        return rc;
    }

    private static Set<String> set(String ...args)
    {
        HashSet<String> s = new HashSet<String>();
        for (String arg : args)
        {
            s.add(arg);
        }
        return Collections.unmodifiableSet(s);
    }

    static final String join(String sep, Iterable items)
    {
        StringBuilder result = new StringBuilder();

        if (items != null)
        {
            for (Object o : items)
            {
                if (result.length() > 0)
                {
                    result.append(sep);
                }
                result.append(o.toString());
            }
        }

        return result.toString();
    }

}
