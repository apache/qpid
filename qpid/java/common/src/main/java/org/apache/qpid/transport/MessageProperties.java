package org.apache.qpid.transport;
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


import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.qpid.transport.codec.Decoder;
import org.apache.qpid.transport.codec.Encoder;



public final class MessageProperties extends Struct {

    public static final int TYPE = 1027;

    public final int getStructType() {
        return TYPE;
    }

    public final int getSizeWidth() {
        return 4;
    }

    public final int getPackWidth() {
        return 2;
    }

    public final boolean hasPayload() {
        return false;
    }

    public final byte getEncodedTrack() {
        return -1;
    }

    public final boolean isConnectionControl()
    {
        return false;
    }

    private short packing_flags = 0;
    private long contentLength;
    private java.util.UUID messageId;
    private byte[] correlationId;
    private ReplyTo replyTo;
    private String contentType;
    private String contentEncoding;
    private byte[] userId;
    private byte[] appId;
    private Map<String,Object> applicationHeaders;


    public MessageProperties() {}


    public MessageProperties(long contentLength, java.util.UUID messageId, byte[] correlationId, ReplyTo replyTo, String contentType, String contentEncoding, byte[] userId, byte[] appId, Map<String,Object> applicationHeaders) {
        setContentLength(contentLength);
        if(messageId != null) {
            setMessageId(messageId);
        }
        if(correlationId != null) {
            setCorrelationId(correlationId);
        }
        if(replyTo != null) {
            setReplyTo(replyTo);
        }
        if(contentType != null) {
            setContentType(contentType);
        }
        if(contentEncoding != null) {
            setContentEncoding(contentEncoding);
        }
        if(userId != null) {
            setUserId(userId);
        }
        if(appId != null) {
            setAppId(appId);
        }
        if(applicationHeaders != null) {
            setApplicationHeaders(applicationHeaders);
        }

    }




    public final boolean hasContentLength() {
        return (packing_flags & 256) != 0;
    }

    public final MessageProperties clearContentLength() {
        packing_flags &= ~256;
        this.contentLength = 0;
        setDirty(true);
        return this;
    }

    public final long getContentLength() {
        return contentLength;
    }

    public final MessageProperties setContentLength(long value) {
        this.contentLength = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final MessageProperties contentLength(long value) {
        return setContentLength(value);
    }

    public final boolean hasMessageId() {
        return (packing_flags & 512) != 0;
    }

    public final MessageProperties clearMessageId() {
        packing_flags &= ~512;
        this.messageId = null;
        setDirty(true);
        return this;
    }

    public final java.util.UUID getMessageId() {
        return messageId;
    }

    public final MessageProperties setMessageId(java.util.UUID value) {
        this.messageId = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final MessageProperties messageId(java.util.UUID value) {
        return setMessageId(value);
    }

    public final boolean hasCorrelationId() {
        return (packing_flags & 1024) != 0;
    }

    public final MessageProperties clearCorrelationId() {
        packing_flags &= ~1024;
        this.correlationId = null;
        setDirty(true);
        return this;
    }

    public final byte[] getCorrelationId() {
        return correlationId;
    }

    public final MessageProperties setCorrelationId(byte[] value) {
        this.correlationId = value;
        packing_flags |= 1024;
        setDirty(true);
        return this;
    }

    public final MessageProperties correlationId(byte[] value) {
        return setCorrelationId(value);
    }

    public final boolean hasReplyTo() {
        return (packing_flags & 2048) != 0;
    }

    public final MessageProperties clearReplyTo() {
        packing_flags &= ~2048;
        this.replyTo = null;
        setDirty(true);
        return this;
    }

    public final ReplyTo getReplyTo() {
        return replyTo;
    }

    public final MessageProperties setReplyTo(ReplyTo value) {
        this.replyTo = value;
        packing_flags |= 2048;
        setDirty(true);
        return this;
    }

    public final MessageProperties replyTo(ReplyTo value) {
        return setReplyTo(value);
    }

    public final boolean hasContentType() {
        return (packing_flags & 4096) != 0;
    }

    public final MessageProperties clearContentType() {
        packing_flags &= ~4096;
        this.contentType = null;
        setDirty(true);
        return this;
    }

    public final String getContentType() {
        return contentType;
    }

    public final MessageProperties setContentType(String value) {
        this.contentType = value;
        packing_flags |= 4096;
        setDirty(true);
        return this;
    }

    public final MessageProperties contentType(String value) {
        return setContentType(value);
    }

    public final boolean hasContentEncoding() {
        return (packing_flags & 8192) != 0;
    }

    public final MessageProperties clearContentEncoding() {
        packing_flags &= ~8192;
        this.contentEncoding = null;
        setDirty(true);
        return this;
    }

    public final String getContentEncoding() {
        return contentEncoding;
    }

    public final MessageProperties setContentEncoding(String value) {
        this.contentEncoding = value;
        packing_flags |= 8192;
        setDirty(true);
        return this;
    }

    public final MessageProperties contentEncoding(String value) {
        return setContentEncoding(value);
    }

    public final boolean hasUserId() {
        return (packing_flags & 16384) != 0;
    }

    public final MessageProperties clearUserId() {
        packing_flags &= ~16384;
        this.userId = null;
        setDirty(true);
        return this;
    }

    public final byte[] getUserId() {
        return userId;
    }

    public final MessageProperties setUserId(byte[] value) {
        this.userId = value;
        packing_flags |= 16384;
        setDirty(true);
        return this;
    }

    public final MessageProperties userId(byte[] value) {
        return setUserId(value);
    }

    public final boolean hasAppId() {
        return (packing_flags & 32768) != 0;
    }

    public final MessageProperties clearAppId() {
        packing_flags &= ~32768;
        this.appId = null;
        setDirty(true);
        return this;
    }

    public final byte[] getAppId() {
        return appId;
    }

    public final MessageProperties setAppId(byte[] value) {
        this.appId = value;
        packing_flags |= 32768;
        setDirty(true);
        return this;
    }

    public final MessageProperties appId(byte[] value) {
        return setAppId(value);
    }

    public final boolean hasApplicationHeaders() {
        return (packing_flags & 1) != 0;
    }

    public final MessageProperties clearApplicationHeaders() {
        packing_flags &= ~1;
        this.applicationHeaders = null;
        setDirty(true);
        return this;
    }

    public final Map<String,Object> getApplicationHeaders() {
        return applicationHeaders;
    }

    public final MessageProperties setApplicationHeaders(Map<String,Object> value) {
        this.applicationHeaders = value;
        packing_flags |= 1;
        setDirty(true);
        return this;
    }

    public final MessageProperties applicationHeaders(Map<String,Object> value) {
        return setApplicationHeaders(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeUint64(this.contentLength);
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeUuid(this.messageId);
        }
        if ((packing_flags & 1024) != 0)
        {
            enc.writeVbin16(this.correlationId);
        }
        if ((packing_flags & 2048) != 0)
        {
            enc.writeStruct(ReplyTo.TYPE, this.replyTo);
        }
        if ((packing_flags & 4096) != 0)
        {
            enc.writeStr8(this.contentType);
        }
        if ((packing_flags & 8192) != 0)
        {
            enc.writeStr8(this.contentEncoding);
        }
        if ((packing_flags & 16384) != 0)
        {
            enc.writeVbin16(this.userId);
        }
        if ((packing_flags & 32768) != 0)
        {
            enc.writeVbin16(this.appId);
        }
        if ((packing_flags & 1) != 0)
        {
            enc.writeMap(this.applicationHeaders);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.contentLength = dec.readUint64();
        }
        if ((packing_flags & 512) != 0)
        {
            this.messageId = dec.readUuid();
        }
        if ((packing_flags & 1024) != 0)
        {
            this.correlationId = dec.readVbin16();
        }
        if ((packing_flags & 2048) != 0)
        {
            this.replyTo = (ReplyTo)dec.readStruct(ReplyTo.TYPE);
        }
        if ((packing_flags & 4096) != 0)
        {
            this.contentType = dec.readStr8();
        }
        if ((packing_flags & 8192) != 0)
        {
            this.contentEncoding = dec.readStr8();
        }
        if ((packing_flags & 16384) != 0)
        {
            this.userId = dec.readVbin16();
        }
        if ((packing_flags & 32768) != 0)
        {
            this.appId = dec.readVbin16();
        }
        if ((packing_flags & 1) != 0)
        {
            this.applicationHeaders = dec.readMap();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("contentLength", getContentLength());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("messageId", getMessageId());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("correlationId", getCorrelationId());
        }
        if ((packing_flags & 2048) != 0)
        {
            result.put("replyTo", getReplyTo());
        }
        if ((packing_flags & 4096) != 0)
        {
            result.put("contentType", getContentType());
        }
        if ((packing_flags & 8192) != 0)
        {
            result.put("contentEncoding", getContentEncoding());
        }
        if ((packing_flags & 16384) != 0)
        {
            result.put("userId", getUserId());
        }
        if ((packing_flags & 32768) != 0)
        {
            result.put("appId", getAppId());
        }
        if ((packing_flags & 1) != 0)
        {
            result.put("applicationHeaders", getApplicationHeaders());
        }


        return result;
    }


}
