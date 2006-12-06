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

public class BasicContentHeaderProperties implements ContentHeaderProperties
{
    private static final Logger _logger = Logger.getLogger(BasicContentHeaderProperties.class);

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

    private String _contentType;

    private String _encoding;

    private FieldTable _headers;

    private byte _deliveryMode;

    private byte _priority;

    private String _correlationId;

    private String _replyTo;

    private long _expiration;

    private String _messageId;

    private long _timestamp;

    private String _type;

    private String _userId;

    private String _appId;

    private String _clusterId;

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
                size += EncodingUtils.encodedShortStringLength(String.valueOf(_expiration));
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
                EncodingUtils.writeShortStringBytes(buffer, String.valueOf(_expiration));
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
                _contentType = EncodingUtils.readShortString(buffer);
            }
            if ((_propertyFlags & (1 << 14)) > 0)
            {
                _encoding = EncodingUtils.readShortString(buffer);
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
                _correlationId = EncodingUtils.readShortString(buffer);
            }
            if ((_propertyFlags & (1 << 9)) > 0)
            {
                _replyTo = EncodingUtils.readShortString(buffer);
            }
            if ((_propertyFlags & (1 << 8)) > 0)
            {
                _expiration = Long.parseLong(EncodingUtils.readShortString(buffer));
            }
            if ((_propertyFlags & (1 << 7)) > 0)
            {
                _messageId = EncodingUtils.readShortString(buffer);
            }
            if ((_propertyFlags & (1 << 6)) > 0)
            {
                _timestamp = EncodingUtils.readTimestamp(buffer);
            }
            if ((_propertyFlags & (1 << 5)) > 0)
            {
                _type = EncodingUtils.readShortString(buffer);
            }
            if ((_propertyFlags & (1 << 4)) > 0)
            {
                _userId = EncodingUtils.readShortString(buffer);
            }
            if ((_propertyFlags & (1 << 3)) > 0)
            {
                _appId = EncodingUtils.readShortString(buffer);
            }
            if ((_propertyFlags & (1 << 2)) > 0)
            {
                _clusterId = EncodingUtils.readShortString(buffer);
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
            _contentType = EncodingUtils.readShortString(buffer);
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

    public String getContentType()
    {
        decodeContentTypeIfNecessary();
        return _contentType;
    }

    public void setContentType(String contentType)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 15);
        _contentType = contentType;
    }

    public String getEncoding()
    {
        decodeIfNecessary();
        return _encoding;
    }

    public void setEncoding(String encoding)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 14);
        _encoding = encoding;
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
        return _correlationId;
    }

    public void setCorrelationId(String correlationId)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 10);
        _correlationId = correlationId;
    }

    public String getReplyTo()
    {
        decodeIfNecessary();
        return _replyTo;
    }

    public void setReplyTo(String replyTo)
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
        return _messageId;
    }

    public void setMessageId(String messageId)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 7);
        _messageId = messageId;
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
        return _type;
    }

    public void setType(String type)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 5);
        _type = type;
    }

    public String getUserId()
    {
        decodeIfNecessary();
        return _userId;
    }

    public void setUserId(String userId)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 4);
        _userId = userId;
    }

    public String getAppId()
    {
        decodeIfNecessary();
        return _appId;
    }

    public void setAppId(String appId)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 3);
        _appId = appId;
    }

    public String getClusterId()
    {
        decodeIfNecessary();
        return _clusterId;
    }

    public void setClusterId(String clusterId)
    {
        clearEncodedForm();
        _propertyFlags |= (1 << 2);
        _clusterId = clusterId;
    }

    public String toString()
    {
        return "reply-to = " + _replyTo + " propertyFlags = " + _propertyFlags;
    }
}
