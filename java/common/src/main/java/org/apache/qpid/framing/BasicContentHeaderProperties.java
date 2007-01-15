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
package org.apache.qpid.framing;

import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQPInvalidClassException;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import java.util.Enumeration;

public class BasicContentHeaderProperties implements ContentHeaderProperties
{
    private static final Logger _logger = Logger.getLogger(BasicContentHeaderProperties.class);

    private static final AMQShortString ZERO_STRING = null;

    /**
     * We store the encoded form when we decode the content header so that if we need to
     * write it out without modifying it we can do so without incurring the expense of
     * reencoding it
     */
    private byte[] _encodedForm;

    /**
     * Flag indicating whether the entire content header has been decoded yet
     */
    private boolean _decoded = true;

    /**
     * We have some optimisations for partial decoding for maximum performance. The headers are used in the broker
     * for routing in some cases so we can decode that separately.
     */
    private boolean _decodedHeaders = true;

    /**
     * We have some optimisations for partial decoding for maximum performance. The content type is used by all
     * clients to determine the message type
     */
    private boolean _decodedContentType = true;

    private AMQShortString _contentType;

    private AMQShortString _encoding;

    private FieldTable _headers;

    private byte _deliveryMode;

    private byte _priority;

    private AMQShortString _correlationId;

    private AMQShortString _replyTo;

    private long _expiration;

    private AMQShortString _messageId;

    private long _timestamp;

    private AMQShortString _type;

    private AMQShortString _userId;

    private AMQShortString _appId;

    private AMQShortString _clusterId;

    private int _propertyFlags = 0;

    public BasicContentHeaderProperties()
    {
    }

    public int getPropertyListSize()
    {
        if (_encodedForm != null)
        {
            return _encodedForm.length;
        }
        else
        {
            int size = 0;

            if ((_propertyFlags & (1 << 15)) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_contentType);
            }
            if ((_propertyFlags & (1 << 14)) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_encoding);
            }
            if ((_propertyFlags & (1 << 13)) > 0)
            {
                size += EncodingUtils.encodedFieldTableLength(_headers);
            }
            if ((_propertyFlags & (1 << 12)) > 0)
            {
                size += 1;
            }
            if ((_propertyFlags & (1 << 11)) > 0)
            {
                size += 1;
            }
            if ((_propertyFlags & (1 << 10)) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_correlationId);
            }
            if ((_propertyFlags & (1 << 9)) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_replyTo);
            }
            if ((_propertyFlags & (1 << 8)) > 0)
            {
                if(_expiration == 0L)
                {
                    size+=EncodingUtils.encodedShortStringLength(ZERO_STRING);
                }
                else
                {
                    size += EncodingUtils.encodedShortStringLength(_expiration);
                }
            }
            if ((_propertyFlags & (1 << 7)) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_messageId);
            }
            if ((_propertyFlags & (1 << 6)) > 0)
            {
                size += 8;
            }
            if ((_propertyFlags & (1 << 5)) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_type);
            }
            if ((_propertyFlags & (1 << 4)) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_userId);
            }
            if ((_propertyFlags & (1 << 3)) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_appId);
            }
            if ((_propertyFlags & (1 << 2)) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_clusterId);
            }
            return size;
        }
    }

    private void clearEncodedForm()
    {
        if (!_decoded && _encodedForm != null)
        {
            //decode();
        }
        _encodedForm = null;
    }

    public void setPropertyFlags(int propertyFlags)
    {
        clearEncodedForm();
        _propertyFlags = propertyFlags;
    }

    public int getPropertyFlags()
    {
        return _propertyFlags;
    }

    public void writePropertyListPayload(ByteBuffer buffer)
    {
        if (_encodedForm != null)
        {
            buffer.put(_encodedForm);
        }
        else
        {
            if ((_propertyFlags & (1 << 15)) > 0)
            {
                EncodingUtils.writeShortStringBytes(buffer, _contentType);
            }
            if ((_propertyFlags & (1 << 14)) > 0)
            {
                EncodingUtils.writeShortStringBytes(buffer, _encoding);
            }
            if ((_propertyFlags & (1 << 13)) > 0)
            {
                EncodingUtils.writeFieldTableBytes(buffer, _headers);
            }
            if ((_propertyFlags & (1 << 12)) > 0)
            {
                buffer.put(_deliveryMode);
            }
            if ((_propertyFlags & (1 << 11)) > 0)
            {
                buffer.put(_priority);
            }
            if ((_propertyFlags & (1 << 10)) > 0)
            {
                EncodingUtils.writeShortStringBytes(buffer, _correlationId);
            }
            if ((_propertyFlags & (1 << 9)) > 0)
            {
                EncodingUtils.writeShortStringBytes(buffer, _replyTo);
            }
            if ((_propertyFlags & (1 << 8)) > 0)
            {
                if(_expiration == 0L)
                {
                    EncodingUtils.writeShortStringBytes(buffer, ZERO_STRING);                    
                }
                else
                {
                    EncodingUtils.writeShortStringBytes(buffer, String.valueOf(_expiration));
                }
            }
            if ((_propertyFlags & (1 << 7)) > 0)
            {
                EncodingUtils.writeShortStringBytes(buffer, _messageId);
            }
            if ((_propertyFlags & (1 << 6)) > 0)
            {
                EncodingUtils.writeTimestamp(buffer, _timestamp);
            }
            if ((_propertyFlags & (1 << 5)) > 0)
            {
                EncodingUtils.writeShortStringBytes(buffer, _type);
            }
            if ((_propertyFlags & (1 << 4)) > 0)
            {
                EncodingUtils.writeShortStringBytes(buffer, _userId);
            }
            if ((_propertyFlags & (1 << 3)) > 0)
            {
                EncodingUtils.writeShortStringBytes(buffer, _appId);
            }
            if ((_propertyFlags & (1 << 2)) > 0)
            {
                EncodingUtils.writeShortStringBytes(buffer, _clusterId);
            }
        }
    }

    public void populatePropertiesFromBuffer(ByteBuffer buffer, int propertyFlags, int size)
        throws AMQFrameDecodingException
    {
        _propertyFlags = propertyFlags;

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Property flags: " + _propertyFlags);
        }
        decode(buffer);
        /*_encodedForm = new byte[size];
        buffer.get(_encodedForm, 0, size);
        _decoded = false;
        _decodedHeaders = false;
        _decodedContentType = false;*/
    }

    private void decode(ByteBuffer buffer)
    {
        //ByteBuffer buffer = ByteBuffer.wrap(_encodedForm);
        int pos = buffer.position();
        try
        {
            if ((_propertyFlags & (1 << 15)) > 0)
            {
                _contentType = EncodingUtils.readAMQShortString(buffer);
            }
            if ((_propertyFlags & (1 << 14)) > 0)
            {
                _encoding = EncodingUtils.readAMQShortString(buffer);
            }
            if ((_propertyFlags & (1 << 13)) > 0)
            {
                _headers = EncodingUtils.readFieldTable(buffer);
            }
            if ((_propertyFlags & (1 << 12)) > 0)
            {
                _deliveryMode = buffer.get();
            }
            if ((_propertyFlags & (1 << 11)) > 0)
            {
                _priority = buffer.get();
            }
            if ((_propertyFlags & (1 << 10)) > 0)
            {
                _correlationId = EncodingUtils.readAMQShortString(buffer);
            }
            if ((_propertyFlags & (1 << 9)) > 0)
            {
                _replyTo = EncodingUtils.readAMQShortString(buffer);
            }
            if ((_propertyFlags & (1 << 8)) > 0)
            {
                _expiration = EncodingUtils.readLongAsShortString(buffer);
            }
            if ((_propertyFlags & (1 << 7)) > 0)
            {
                _messageId = EncodingUtils.readAMQShortString(buffer);
            }
            if ((_propertyFlags & (1 << 6)) > 0)
            {
                _timestamp = EncodingUtils.readTimestamp(buffer);
            }
            if ((_propertyFlags & (1 << 5)) > 0)
            {
                _type = EncodingUtils.readAMQShortString(buffer);
            }
            if ((_propertyFlags & (1 << 4)) > 0)
            {
                _userId = EncodingUtils.readAMQShortString(buffer);
            }
            if ((_propertyFlags & (1 << 3)) > 0)
            {
                _appId = EncodingUtils.readAMQShortString(buffer);
            }
            if ((_propertyFlags & (1 << 2)) > 0)
            {
                _clusterId = EncodingUtils.readAMQShortString(buffer);
            }
        }
        catch (AMQFrameDecodingException e)
        {
            throw new RuntimeException("Error in content header data: " + e);
        }

        final int endPos = buffer.position();
        buffer.position(pos);
        final int len = endPos - pos;
        _encodedForm = new byte[len];
        final int limit = buffer.limit();
        buffer.limit(endPos);
        buffer.get(_encodedForm, 0, len);
        buffer.limit(limit);
        buffer.position(endPos);
        _decoded = true;
    }


    private void decodeUpToHeaders()
    {
        ByteBuffer buffer = ByteBuffer.wrap(_encodedForm);
        try
        {
            if ((_propertyFlags & (1 << 15)) > 0)
            {
                byte length = buffer.get();
                buffer.skip(length);
            }
            if ((_propertyFlags & (1 << 14)) > 0)
            {
                byte length = buffer.get();
                buffer.skip(length);
            }
            if ((_propertyFlags & (1 << 13)) > 0)
            {
                _headers = EncodingUtils.readFieldTable(buffer);

            }
            _decodedHeaders = true;
        }
        catch (AMQFrameDecodingException e)
        {
            throw new RuntimeException("Error in content header data: " + e);
        }
    }

    private void decodeUpToContentType()
    {
        ByteBuffer buffer = ByteBuffer.wrap(_encodedForm);

        if ((_propertyFlags & (1 << 15)) > 0)
        {
            _contentType = EncodingUtils.readAMQShortString(buffer);
        }

        _decodedContentType = true;
    }

    private void decodeIfNecessary()
    {
        if (!_decoded)
        {
            //decode();
        }
    }

    private void decodeHeadersIfNecessary()
    {
        if (!_decoded && !_decodedHeaders)
        {
            decodeUpToHeaders();
        }
    }

    private void decodeContentTypeIfNecessary()
    {
        if (!_decoded && !_decodedContentType)
        {
            decodeUpToContentType();
        }
    }

    public AMQShortString getContentTypeShortString()
    {
        decodeContentTypeIfNecessary();
        return _contentType;
    }


    public String getContentType()
    {
        decodeContentTypeIfNecessary();
        return _contentType == null ? null : _contentType.toString();
    }

    public void setContentType(AMQShortString contentType)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 15);
        _contentType = contentType;
    }


    public void setContentType(String contentType)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 15);
        _contentType = contentType == null ? null : new AMQShortString(contentType);
    }

    public String getEncoding()
    {
        decodeIfNecessary();
        return _encoding == null ? null : _encoding.toString();
    }

    public void setEncoding(String encoding)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 14);
        _encoding = encoding == null ? null : new AMQShortString(encoding);
    }

    public FieldTable getHeaders()
    {
        decodeHeadersIfNecessary();

        if (_headers == null)
        {
            setHeaders(FieldTableFactory.newFieldTable());
        }

        return _headers;
    }

    public void setHeaders(FieldTable headers)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 13);
        _headers = headers;
    }


    public byte getDeliveryMode()
    {
        decodeIfNecessary();
        return _deliveryMode;
    }

    public void setDeliveryMode(byte deliveryMode)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 12);
        _deliveryMode = deliveryMode;
    }

    public byte getPriority()
    {
        decodeIfNecessary();
        return _priority;
    }

    public void setPriority(byte priority)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 11);
        _priority = priority;
    }

    public String getCorrelationId()
    {
        decodeIfNecessary();
        return _correlationId == null ? null : _correlationId.toString();
    }

    public void setCorrelationId(String correlationId)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 10);
        _correlationId = correlationId == null ? null : new AMQShortString(correlationId);
    }

    public String getReplyTo()
    {
        decodeIfNecessary();
        return _replyTo == null ? null : _replyTo.toString();
    }

    public void setReplyTo(String replyTo)
    {
        setReplyTo(replyTo == null ? null : new AMQShortString(replyTo));
    }
    
    public void setReplyTo(AMQShortString replyTo)
    {

        clearEncodedForm();
        _propertyFlags |= (1 << 9);
        _replyTo = replyTo;
    }

    public long getExpiration()
    {
        decodeIfNecessary();
        return _expiration;
    }

    public void setExpiration(long expiration)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 8);
        _expiration = expiration;
    }


    public String getMessageId()
    {
        decodeIfNecessary();
        return _messageId == null ? null : _messageId.toString();
    }

    public void setMessageId(String messageId)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 7);
        _messageId = messageId == null ? null : new AMQShortString(messageId);
    }

    public long getTimestamp()
    {
        decodeIfNecessary();
        return _timestamp;
    }

    public void setTimestamp(long timestamp)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 6);
        _timestamp = timestamp;
    }

    public String getType()
    {
        decodeIfNecessary();
        return _type == null ? null : _type.toString();
    }

    public void setType(String type)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 5);
        _type = type == null ? null : new AMQShortString(type);
    }

    public String getUserId()
    {
        decodeIfNecessary();
        return _userId == null ? null : _userId.toString();
    }

    public void setUserId(String userId)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 4);
        _userId = userId == null ? null : new AMQShortString(userId);
    }

    public String getAppId()
    {
        decodeIfNecessary();
        return _appId == null ? null : _appId.toString();
    }

    public void setAppId(String appId)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 3);
        _appId = appId == null ? null : new AMQShortString(appId);
    }

    public String getClusterId()
    {
        decodeIfNecessary();
        return _clusterId == null ? null : _clusterId.toString();
    }

    public void setClusterId(String clusterId)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 2);
        _clusterId = clusterId == null ? null : new AMQShortString(clusterId);
    }

    public String toString()
    {
        return "reply-to = " + _replyTo +
               ",propertyFlags = " + _propertyFlags +
               ",ApplicationID = " + _appId +
               ",ClusterID = " + _clusterId +
               ",UserId = " + _userId +
               ",JMSMessageID = " + _messageId +
               ",JMSCorrelationID = " + _correlationId +
               ",JMSDeliveryMode = " + _deliveryMode +
               ",JMSExpiration = " + _expiration +
               ",JMSPriority = " + _priority +
               ",JMSTimestamp = " + _timestamp +
               ",JMSType = " + _type;
    }

    // MapMessage  Interface
    public boolean getBoolean(String string) throws JMSException
    {
        Boolean b = getHeaders().getBoolean(string);

        if (b == null)
        {
            if (getHeaders().containsKey(string))
            {
                Object str = getHeaders().getObject(string);

                if (str == null || !(str instanceof String))
                {
                    throw new MessageFormatException("getBoolean can't use " + string + " item.");
                }
                else
                {
                    return Boolean.valueOf((String) str);
                }
            }
            else
            {
                b = Boolean.valueOf(null);
            }
        }

        return b;
    }

    public boolean getBoolean(AMQShortString string) throws JMSException
    {
        Boolean b = getHeaders().getBoolean(string);

        if (b == null)
        {
            if (getHeaders().containsKey(string))
            {
                Object str = getHeaders().getObject(string);

                if (str == null || !(str instanceof String))
                {
                    throw new MessageFormatException("getBoolean can't use " + string + " item.");
                }
                else
                {
                    return Boolean.valueOf((String) str);
                }
            }
            else
            {
                b = Boolean.valueOf(null);
            }
        }

        return b;
    }

    public char getCharacter(String string) throws JMSException
    {
        Character c = getHeaders().getCharacter(string);

        if (c == null)
        {
            if (getHeaders().isNullStringValue(string))
            {
                throw new NullPointerException("Cannot convert null char");
            }
            else
            {
                throw new MessageFormatException("getChar can't use " + string + " item.");
            }
        }
        else
        {
            return (char) c;
        }
    }

    public byte[] getBytes(String string) throws JMSException
    {
        return getBytes(new AMQShortString(string));
    }

    public byte[] getBytes(AMQShortString string) throws JMSException
    {
        byte[] bs = getHeaders().getBytes(string);

        if (bs == null)
        {
            throw new MessageFormatException("getBytes can't use " + string + " item.");
        }
        else
        {
            return bs;
        }
    }

    public byte getByte(String string) throws JMSException
    {
            Byte b = getHeaders().getByte(string);
            if (b == null)
            {
                if (getHeaders().containsKey(string))
                {
                    Object str = getHeaders().getObject(string);

                    if (str == null || !(str instanceof String))
                    {
                        throw new MessageFormatException("getByte can't use " + string + " item.");
                    }
                    else
                    {
                        return Byte.valueOf((String) str);
                    }
                }
                else
                {
                    b = Byte.valueOf(null);
                }
            }

            return b;
    }

    public short getShort(String string) throws JMSException
    {
            Short s = getHeaders().getShort(string);

            if (s == null)
            {
                s = Short.valueOf(getByte(string));
            }

            return s;
    }

    public int getInteger(String string) throws JMSException
    {
            Integer i = getHeaders().getInteger(string);

            if (i == null)
            {
                i = Integer.valueOf(getShort(string));
            }

            return i;
    }

    public long getLong(String string) throws JMSException
    {
            Long l = getHeaders().getLong(string);

            if (l == null)
            {
                l = Long.valueOf(getInteger(string));
            }

            return l;
    }

    public float getFloat(String string) throws JMSException
    {
            Float f = getHeaders().getFloat(string);

            if (f == null)
            {
                if (getHeaders().containsKey(string))
                {
                    Object str = getHeaders().getObject(string);

                    if (str == null || !(str instanceof String))
                    {
                        throw new MessageFormatException("getFloat can't use " + string + " item.");
                    }
                    else
                    {
                        return Float.valueOf((String) str);
                    }
                }
                else
                {
                    f = Float.valueOf(null);
                }

            }

            return f;
    }

    public double getDouble(String string) throws JMSException
    {
            Double d = getHeaders().getDouble(string);

            if (d == null)
            {
                d = Double.valueOf(getFloat(string));
            }

            return d;
    }

    public String getString(String string) throws JMSException
    {
        String s = getHeaders().getString(string);

        if (s == null)
        {
            if (getHeaders().containsKey(string))
            {
                Object o = getHeaders().getObject(string);
                if (o instanceof byte[])
                {
                    throw new MessageFormatException("getObject couldn't find " + string + " item.");
                }
                else
                {
                    if (o == null)
                    {
                        return null;
                    }
                    else
                    {
                        s = String.valueOf(o);
                    }
                }
            }
        }

        return s;
    }

    public Object getObject(String string) throws JMSException
    {
        return getHeaders().getObject(string);
    }

    public void setBoolean(AMQShortString string, boolean b) throws JMSException
    {
        checkPropertyName(string);
        getHeaders().setBoolean(string, b);
    }

    public void setBoolean(String string, boolean b) throws JMSException
    {
        checkPropertyName(string);
        getHeaders().setBoolean(string, b);
    }

    public void setChar(String string, char c) throws JMSException
    {
        checkPropertyName(string);
        getHeaders().setChar(string, c);
    }

    public Object setBytes(AMQShortString string, byte[] bytes)
    {
        return getHeaders().setBytes(string, bytes);
    }

    public Object setBytes(String string, byte[] bytes)
    {
        return getHeaders().setBytes(string, bytes);
    }

    public Object setBytes(String string, byte[] bytes, int start, int length)
    {
        return getHeaders().setBytes(string, bytes, start, length);
    }

    public void setByte(String string, byte b) throws JMSException
    {
        checkPropertyName(string);
        getHeaders().setByte(string, b);
    }

    public void setShort(String string, short i) throws JMSException
    {
        checkPropertyName(string);
        getHeaders().setShort(string, i);
    }

    public void setInteger(String string, int i) throws JMSException
    {
        checkPropertyName(string);
        getHeaders().setInteger(string, i);
    }

    public void setLong(String string, long l) throws JMSException
    {
        checkPropertyName(string);
        getHeaders().setLong(string, l);
    }

    public void setFloat(String string, float v) throws JMSException
    {
        checkPropertyName(string);
        getHeaders().setFloat(string, v);
    }

    public void setDouble(String string, double v) throws JMSException
    {
        checkPropertyName(string);
        getHeaders().setDouble(string, v);
    }

    public void setString(String string, String string1) throws JMSException
    {
        checkPropertyName(string);
        getHeaders().setString(string, string1);
    }

    public void setString(AMQShortString string, String string1) throws JMSException
    {
        checkPropertyName(string);
        getHeaders().setString(string, string1);
    }

    public void setObject(String string, Object object) throws JMSException
    {
        checkPropertyName(string);
        try
        {
            getHeaders().setObject(string, object);
        }
        catch (AMQPInvalidClassException aice)
        {
            throw new MessageFormatException("Only primatives are allowed object is:" + object.getClass());
        }
    }

    public boolean itemExists(String string) throws JMSException
    {
        return getHeaders().containsKey(string);
    }

    public Enumeration getPropertyNames()
    {
        return getHeaders().getPropertyNames();
    }

    public void clear()
    {
        getHeaders().clear();
    }

    public boolean propertyExists(AMQShortString propertyName)
    {
        return getHeaders().propertyExists(propertyName);
    }

    public boolean propertyExists(String propertyName)
    {
        return getHeaders().propertyExists(propertyName);
    }

    public Object put(Object key, Object value)
    {
        return getHeaders().setObject(key.toString(), value);
    }

    public Object remove(AMQShortString propertyName)
    {
        return getHeaders().remove(propertyName);
    }

    public Object remove(String propertyName)
    {
        return getHeaders().remove(propertyName);
    }

    public boolean isEmpty()
    {
        return getHeaders().isEmpty();
    }

    public void writeToBuffer(ByteBuffer data)
    {
        getHeaders().writeToBuffer(data);
    }

    public Enumeration getMapNames()
    {
        return getPropertyNames();
    }

    protected static void checkPropertyName(CharSequence propertyName)
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

    protected static void checkIdentiferFormat(CharSequence propertyName)
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
//        – Identifiers cannot be NOT, AND, OR, BETWEEN, LIKE, IN, IS, or
//          ESCAPE.
//        – Identifiers are either header field references or property references. The
//          type of a property value in a message selector corresponds to the type
//          used to set the property. If a property that does not exist in a message is
//          referenced, its value is NULL. The semantics of evaluating NULL values
//          in a selector are described in Section 3.8.1.2, “Null Values.”
//        – The conversions that apply to the get methods for properties do not
//          apply when a property is used in a message selector expression. For
//          example, suppose you set a property as a string value, as in the
//          following:
//              myMessage.setStringProperty("NumberOfOrders", "2");
//          The following expression in a message selector would evaluate to false,
//          because a string cannot be used in an arithmetic expression:
//          "NumberOfOrders > 1"
//        – Identifiers are case sensitive.
//        – Message header field references are restricted to JMSDeliveryMode,
//          JMSPriority, JMSMessageID, JMSTimestamp, JMSCorrelationID, and
//          JMSType. JMSMessageID, JMSCorrelationID, and JMSType values may be
//          null and if so are treated as a NULL value.

        if (Boolean.getBoolean("strict-jms"))
        {
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

    }
}
