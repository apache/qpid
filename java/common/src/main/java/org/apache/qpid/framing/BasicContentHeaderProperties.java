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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicContentHeaderProperties implements CommonContentHeaderProperties
{
    //persistent & non-persistent constants, values as per JMS DeliveryMode
    public static final int NON_PERSISTENT = 1;
    public static final int PERSISTENT = 2;

    private static final Logger _logger = LoggerFactory.getLogger(BasicContentHeaderProperties.class);

    private static final AMQShortString ZERO_STRING = null;

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
    private static final int CONTENT_TYPE_MASK = 1 << 15;
    private static final int ENCODING_MASK = 1 << 14;
    private static final int HEADERS_MASK = 1 << 13;
    private static final int DELIVERY_MODE_MASK = 1 << 12;
    private static final int PRIORITY_MASK = 1 << 11;
    private static final int CORRELATION_ID_MASK = 1 << 10;
    private static final int REPLY_TO_MASK = 1 << 9;
    private static final int EXPIRATION_MASK = 1 << 8;
    private static final int MESSAGE_ID_MASK = 1 << 7;
    private static final int TIMESTAMP_MASK = 1 << 6;
    private static final int TYPE_MASK = 1 << 5;
    private static final int USER_ID_MASK = 1 << 4;
    private static final int APPLICATION_ID_MASK = 1 << 3;
    private static final int CLUSTER_ID_MASK = 1 << 2;


    public BasicContentHeaderProperties()
    { }

    public int getPropertyListSize()
    {
            int size = 0;

            if ((_propertyFlags & (CONTENT_TYPE_MASK)) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_contentType);
            }

            if ((_propertyFlags & ENCODING_MASK) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_encoding);
            }

            if ((_propertyFlags & HEADERS_MASK) > 0)
            {
                size += EncodingUtils.encodedFieldTableLength(_headers);
            }

            if ((_propertyFlags & DELIVERY_MODE_MASK) > 0)
            {
                size += 1;
            }

            if ((_propertyFlags & PRIORITY_MASK) > 0)
            {
                size += 1;
            }

            if ((_propertyFlags & CORRELATION_ID_MASK) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_correlationId);
            }

            if ((_propertyFlags & REPLY_TO_MASK) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_replyTo);
            }

            if ((_propertyFlags & EXPIRATION_MASK) > 0)
            {
                if (_expiration == 0L)
                {
                    size += EncodingUtils.encodedShortStringLength(ZERO_STRING);
                }
                else
                {
                    size += EncodingUtils.encodedShortStringLength(_expiration);
                }
            }

            if ((_propertyFlags & MESSAGE_ID_MASK) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_messageId);
            }

            if ((_propertyFlags & TIMESTAMP_MASK) > 0)
            {
                size += 8;
            }

            if ((_propertyFlags & TYPE_MASK) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_type);
            }

            if ((_propertyFlags & USER_ID_MASK) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_userId);
            }

            if ((_propertyFlags & APPLICATION_ID_MASK) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_appId);
            }

            if ((_propertyFlags & CLUSTER_ID_MASK) > 0)
            {
                size += EncodingUtils.encodedShortStringLength(_clusterId);
            }

            return size;
    }

    public void setPropertyFlags(int propertyFlags)
    {
        _propertyFlags = propertyFlags;
    }

    public int getPropertyFlags()
    {
        return _propertyFlags;
    }

    public void writePropertyListPayload(DataOutputStream buffer) throws IOException
    {
        if ((_propertyFlags & (CONTENT_TYPE_MASK)) != 0)
        {
            EncodingUtils.writeShortStringBytes(buffer, _contentType);
        }

        if ((_propertyFlags & ENCODING_MASK) != 0)
        {
            EncodingUtils.writeShortStringBytes(buffer, _encoding);
        }

        if ((_propertyFlags & HEADERS_MASK) != 0)
        {
            EncodingUtils.writeFieldTableBytes(buffer, _headers);
        }

        if ((_propertyFlags & DELIVERY_MODE_MASK) != 0)
        {
            buffer.writeByte(_deliveryMode);
        }

        if ((_propertyFlags & PRIORITY_MASK) != 0)
        {
            buffer.writeByte(_priority);
        }

        if ((_propertyFlags & CORRELATION_ID_MASK) != 0)
        {
            EncodingUtils.writeShortStringBytes(buffer, _correlationId);
        }

        if ((_propertyFlags & REPLY_TO_MASK) != 0)
        {
            EncodingUtils.writeShortStringBytes(buffer, _replyTo);
        }

        if ((_propertyFlags & EXPIRATION_MASK) != 0)
        {
            if (_expiration == 0L)
            {
                EncodingUtils.writeShortStringBytes(buffer, ZERO_STRING);
            }
            else
            {
                EncodingUtils.writeShortStringBytes(buffer, String.valueOf(_expiration));
            }
        }

        if ((_propertyFlags & MESSAGE_ID_MASK) != 0)
        {
            EncodingUtils.writeShortStringBytes(buffer, _messageId);
        }

        if ((_propertyFlags & TIMESTAMP_MASK) != 0)
        {
            EncodingUtils.writeTimestamp(buffer, _timestamp);
        }

        if ((_propertyFlags & TYPE_MASK) != 0)
        {
            EncodingUtils.writeShortStringBytes(buffer, _type);
        }

        if ((_propertyFlags & USER_ID_MASK) != 0)
        {
            EncodingUtils.writeShortStringBytes(buffer, _userId);
        }

        if ((_propertyFlags & APPLICATION_ID_MASK) != 0)
        {
            EncodingUtils.writeShortStringBytes(buffer, _appId);
        }

        if ((_propertyFlags & CLUSTER_ID_MASK) != 0)
        {
            EncodingUtils.writeShortStringBytes(buffer, _clusterId);
        }
    }

    public void populatePropertiesFromBuffer(DataInputStream buffer, int propertyFlags, int size) throws AMQFrameDecodingException, IOException
    {
        _propertyFlags = propertyFlags;

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Property flags: " + _propertyFlags);
        }

        decode(buffer);
    }

    private void decode(DataInputStream buffer) throws IOException, AMQFrameDecodingException
    {
        // ByteBuffer buffer = ByteBuffer.wrap(_encodedForm);

            if ((_propertyFlags & (CONTENT_TYPE_MASK)) != 0)
            {
                _contentType = EncodingUtils.readAMQShortString(buffer);
            }

            if ((_propertyFlags & ENCODING_MASK) != 0)
            {
                _encoding = EncodingUtils.readAMQShortString(buffer);
            }

            if ((_propertyFlags & HEADERS_MASK) != 0)
            {
                _headers = EncodingUtils.readFieldTable(buffer);
            }

            if ((_propertyFlags & DELIVERY_MODE_MASK) != 0)
            {
                _deliveryMode = buffer.readByte();
            }

            if ((_propertyFlags & PRIORITY_MASK) != 0)
            {
                _priority = buffer.readByte();
            }

            if ((_propertyFlags & CORRELATION_ID_MASK) != 0)
            {
                _correlationId = EncodingUtils.readAMQShortString(buffer);
            }

            if ((_propertyFlags & REPLY_TO_MASK) != 0)
            {
                _replyTo = EncodingUtils.readAMQShortString(buffer);
            }

            if ((_propertyFlags & EXPIRATION_MASK) != 0)
            {
                _expiration = EncodingUtils.readLongAsShortString(buffer);
            }

            if ((_propertyFlags & MESSAGE_ID_MASK) != 0)
            {
                _messageId = EncodingUtils.readAMQShortString(buffer);
            }

            if ((_propertyFlags & TIMESTAMP_MASK) != 0)
            {
                _timestamp = EncodingUtils.readTimestamp(buffer);
            }

            if ((_propertyFlags & TYPE_MASK) != 0)
            {
                _type = EncodingUtils.readAMQShortString(buffer);
            }

            if ((_propertyFlags & USER_ID_MASK) != 0)
            {
                _userId = EncodingUtils.readAMQShortString(buffer);
            }

            if ((_propertyFlags & APPLICATION_ID_MASK) != 0)
            {
                _appId = EncodingUtils.readAMQShortString(buffer);
            }

            if ((_propertyFlags & CLUSTER_ID_MASK) != 0)
            {
                _clusterId = EncodingUtils.readAMQShortString(buffer);
            }


    }


    public AMQShortString getContentType()
    {
        return _contentType;
    }

    public String getContentTypeAsString()
    {
        return (_contentType == null) ? null : _contentType.toString();
    }

    public void setContentType(AMQShortString contentType)
    {
        _propertyFlags |= (CONTENT_TYPE_MASK);
        _contentType = contentType;
    }

    public void setContentType(String contentType)
    {
        setContentType((contentType == null) ? null : new AMQShortString(contentType));
    }

    public String getEncodingAsString()
    {

        return (getEncoding() == null) ? null : getEncoding().toString();
    }

    public AMQShortString getEncoding()
    {
        return _encoding;
    }

    public void setEncoding(String encoding)
    {
        _propertyFlags |= ENCODING_MASK;
        _encoding = (encoding == null) ? null : new AMQShortString(encoding);
    }

    public void setEncoding(AMQShortString encoding)
    {
        _propertyFlags |= ENCODING_MASK;
        _encoding = encoding;
    }

    public FieldTable getHeaders()
    {
        if (_headers == null)
        {
            setHeaders(FieldTableFactory.newFieldTable());
        }

        return _headers;
    }

    public void setHeaders(FieldTable headers)
    {
        _propertyFlags |= HEADERS_MASK;
        _headers = headers;
    }

    public byte getDeliveryMode()
    {
        return _deliveryMode;
    }

    public void setDeliveryMode(byte deliveryMode)
    {
        _propertyFlags |= DELIVERY_MODE_MASK;
        _deliveryMode = deliveryMode;
    }

    public byte getPriority()
    {
        return _priority;
    }

    public void setPriority(byte priority)
    {
        _propertyFlags |= PRIORITY_MASK;
        _priority = priority;
    }

    public AMQShortString getCorrelationId()
    {
        return _correlationId;
    }

    public String getCorrelationIdAsString()
    {
        return (_correlationId == null) ? null : _correlationId.toString();
    }

    public void setCorrelationId(String correlationId)
    {
        setCorrelationId((correlationId == null) ? null : new AMQShortString(correlationId));
    }

    public void setCorrelationId(AMQShortString correlationId)
    {
        _propertyFlags |= CORRELATION_ID_MASK;
        _correlationId = correlationId;
    }

    public String getReplyToAsString()
    {
        return (_replyTo == null) ? null : _replyTo.toString();
    }

    public AMQShortString getReplyTo()
    {
        return _replyTo;
    }

    public void setReplyTo(String replyTo)
    {
        setReplyTo((replyTo == null) ? null : new AMQShortString(replyTo));
    }

    public void setReplyTo(AMQShortString replyTo)
    {
        _propertyFlags |= REPLY_TO_MASK;
        _replyTo = replyTo;
    }

    public long getExpiration()
    {
        return _expiration;
    }

    public void setExpiration(long expiration)
    {
        _propertyFlags |= EXPIRATION_MASK;
        _expiration = expiration;
    }

    public AMQShortString getMessageId()
    {
        return _messageId;
    }

    public String getMessageIdAsString()
    {
        return (_messageId == null) ? null : _messageId.toString();
    }

    public void setMessageId(String messageId)
    {
        _propertyFlags |= MESSAGE_ID_MASK;
        _messageId = (messageId == null) ? null : new AMQShortString(messageId);
    }

    public void setMessageId(AMQShortString messageId)
    {
        _propertyFlags |= MESSAGE_ID_MASK;
        _messageId = messageId;
    }

    public long getTimestamp()
    {
        return _timestamp;
    }

    public void setTimestamp(long timestamp)
    {
        _propertyFlags |= TIMESTAMP_MASK;
        _timestamp = timestamp;
    }

    public String getTypeAsString()
    {
        return (_type == null) ? null : _type.toString();
    }

    public AMQShortString getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        setType((type == null) ? null : new AMQShortString(type));
    }

    public void setType(AMQShortString type)
    {
        _propertyFlags |= TYPE_MASK;
        _type = type;
    }

    public String getUserIdAsString()
    {
        return (_userId == null) ? null : _userId.toString();
    }

    public AMQShortString getUserId()
    {
        return _userId;
    }

    public void setUserId(String userId)
    {
        setUserId((userId == null) ? null : new AMQShortString(userId));
    }

    public void setUserId(AMQShortString userId)
    {
        _propertyFlags |= USER_ID_MASK;
        _userId = userId;
    }

    public String getAppIdAsString()
    {
        return (_appId == null) ? null : _appId.toString();
    }

    public AMQShortString getAppId()
    {
        return _appId;
    }

    public void setAppId(String appId)
    {
        setAppId((appId == null) ? null : new AMQShortString(appId));
    }

    public void setAppId(AMQShortString appId)
    {
        _propertyFlags |= APPLICATION_ID_MASK;
        _appId = appId;
    }

    public String getClusterIdAsString()
    {
        return (_clusterId == null) ? null : _clusterId.toString();
    }

    public AMQShortString getClusterId()
    {
        return _clusterId;
    }

    public void setClusterId(String clusterId)
    {
        setClusterId((clusterId == null) ? null : new AMQShortString(clusterId));
    }

    public void setClusterId(AMQShortString clusterId)
    {
        _propertyFlags |= CLUSTER_ID_MASK;
        _clusterId = clusterId;
    }

    public String toString()
    {
        return "reply-to = " + _replyTo + ",propertyFlags = " + _propertyFlags + ",ApplicationID = " + _appId
            + ",ClusterID = " + _clusterId + ",UserId = " + _userId + ",JMSMessageID = " + _messageId
            + ",JMSCorrelationID = " + _correlationId + ",JMSDeliveryMode = " + _deliveryMode + ",JMSExpiration = "
            + _expiration + ",JMSPriority = " + _priority + ",JMSTimestamp = " + _timestamp + ",JMSType = " + _type;
    }

}
