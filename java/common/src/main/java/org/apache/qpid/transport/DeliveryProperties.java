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



public final class DeliveryProperties extends Struct {

    public static final int TYPE = 1025;

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
    private MessageDeliveryPriority priority;
    private MessageDeliveryMode deliveryMode;
    private long ttl;
    private long timestamp;
    private long expiration;
    private String exchange;
    private String routingKey;
    private String resumeId;
    private long resumeTtl;


    public DeliveryProperties() {}


    public DeliveryProperties(MessageDeliveryPriority priority, MessageDeliveryMode deliveryMode, long ttl, long timestamp, long expiration, String exchange, String routingKey, String resumeId, long resumeTtl, Option ... _options) {
        if(priority != null) {
            setPriority(priority);
        }
        if(deliveryMode != null) {
            setDeliveryMode(deliveryMode);
        }
        setTtl(ttl);
        setTimestamp(timestamp);
        setExpiration(expiration);
        if(exchange != null) {
            setExchange(exchange);
        }
        if(routingKey != null) {
            setRoutingKey(routingKey);
        }
        if(resumeId != null) {
            setResumeId(resumeId);
        }
        setResumeTtl(resumeTtl);

        for (int i=0; i < _options.length; i++) {
            switch (_options[i]) {
            case DISCARD_UNROUTABLE: packing_flags |= 256; break;
            case IMMEDIATE: packing_flags |= 512; break;
            case REDELIVERED: packing_flags |= 1024; break;
            case NONE: break;
            default: throw new IllegalArgumentException("invalid option: " + _options[i]);
            }
        }

    }




    public final boolean hasDiscardUnroutable() {
        return (packing_flags & 256) != 0;
    }

    public final DeliveryProperties clearDiscardUnroutable() {
        packing_flags &= ~256;

        setDirty(true);
        return this;
    }

    public final boolean getDiscardUnroutable() {
        return hasDiscardUnroutable();
    }

    public final DeliveryProperties setDiscardUnroutable(boolean value) {

        if (value)
        {
            packing_flags |= 256;
        }
        else
        {
            packing_flags &= ~256;
        }

        setDirty(true);
        return this;
    }

    public final DeliveryProperties discardUnroutable(boolean value) {
        return setDiscardUnroutable(value);
    }

    public final boolean hasImmediate() {
        return (packing_flags & 512) != 0;
    }

    public final DeliveryProperties clearImmediate() {
        packing_flags &= ~512;

        setDirty(true);
        return this;
    }

    public final boolean getImmediate() {
        return hasImmediate();
    }

    public final DeliveryProperties setImmediate(boolean value) {

        if (value)
        {
            packing_flags |= 512;
        }
        else
        {
            packing_flags &= ~512;
        }

        setDirty(true);
        return this;
    }

    public final DeliveryProperties immediate(boolean value) {
        return setImmediate(value);
    }

    public final boolean hasRedelivered() {
        return (packing_flags & 1024) != 0;
    }

    public final DeliveryProperties clearRedelivered() {
        packing_flags &= ~1024;

        setDirty(true);
        return this;
    }

    public final boolean getRedelivered() {
        return hasRedelivered();
    }

    public final DeliveryProperties setRedelivered(boolean value) {

        if (value)
        {
            packing_flags |= 1024;
        }
        else
        {
            packing_flags &= ~1024;
        }

        setDirty(true);
        return this;
    }

    public final DeliveryProperties redelivered(boolean value) {
        return setRedelivered(value);
    }

    public final boolean hasPriority() {
        return (packing_flags & 2048) != 0;
    }

    public final DeliveryProperties clearPriority() {
        packing_flags &= ~2048;
        this.priority = null;
        setDirty(true);
        return this;
    }

    public final MessageDeliveryPriority getPriority() {
        return priority;
    }

    public final DeliveryProperties setPriority(MessageDeliveryPriority value) {
        this.priority = value;
        packing_flags |= 2048;
        setDirty(true);
        return this;
    }

    public final DeliveryProperties priority(MessageDeliveryPriority value) {
        return setPriority(value);
    }

    public final boolean hasDeliveryMode() {
        return (packing_flags & 4096) != 0;
    }

    public final DeliveryProperties clearDeliveryMode() {
        packing_flags &= ~4096;
        this.deliveryMode = null;
        setDirty(true);
        return this;
    }

    public final MessageDeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public final DeliveryProperties setDeliveryMode(MessageDeliveryMode value) {
        this.deliveryMode = value;
        packing_flags |= 4096;
        setDirty(true);
        return this;
    }

    public final DeliveryProperties deliveryMode(MessageDeliveryMode value) {
        return setDeliveryMode(value);
    }

    public final boolean hasTtl() {
        return (packing_flags & 8192) != 0;
    }

    public final DeliveryProperties clearTtl() {
        packing_flags &= ~8192;
        this.ttl = 0;
        setDirty(true);
        return this;
    }

    public final long getTtl() {
        return ttl;
    }

    public final DeliveryProperties setTtl(long value) {
        this.ttl = value;
        packing_flags |= 8192;
        setDirty(true);
        return this;
    }

    public final DeliveryProperties ttl(long value) {
        return setTtl(value);
    }

    public final boolean hasTimestamp() {
        return (packing_flags & 16384) != 0;
    }

    public final DeliveryProperties clearTimestamp() {
        packing_flags &= ~16384;
        this.timestamp = 0;
        setDirty(true);
        return this;
    }

    public final long getTimestamp() {
        return timestamp;
    }

    public final DeliveryProperties setTimestamp(long value) {
        this.timestamp = value;
        packing_flags |= 16384;
        setDirty(true);
        return this;
    }

    public final DeliveryProperties timestamp(long value) {
        return setTimestamp(value);
    }

    public final boolean hasExpiration() {
        return (packing_flags & 32768) != 0;
    }

    public final DeliveryProperties clearExpiration() {
        packing_flags &= ~32768;
        this.expiration = 0;
        setDirty(true);
        return this;
    }

    public final long getExpiration() {
        return expiration;
    }

    public final DeliveryProperties setExpiration(long value) {
        this.expiration = value;
        packing_flags |= 32768;
        setDirty(true);
        return this;
    }

    public final DeliveryProperties expiration(long value) {
        return setExpiration(value);
    }

    public final boolean hasExchange() {
        return (packing_flags & 1) != 0;
    }

    public final DeliveryProperties clearExchange() {
        packing_flags &= ~1;
        this.exchange = null;
        setDirty(true);
        return this;
    }

    public final String getExchange() {
        return exchange;
    }

    public final DeliveryProperties setExchange(String value) {
        this.exchange = value;
        packing_flags |= 1;
        setDirty(true);
        return this;
    }

    public final DeliveryProperties exchange(String value) {
        return setExchange(value);
    }

    public final boolean hasRoutingKey() {
        return (packing_flags & 2) != 0;
    }

    public final DeliveryProperties clearRoutingKey() {
        packing_flags &= ~2;
        this.routingKey = null;
        setDirty(true);
        return this;
    }

    public final String getRoutingKey() {
        return routingKey;
    }

    public final DeliveryProperties setRoutingKey(String value) {
        this.routingKey = value;
        packing_flags |= 2;
        setDirty(true);
        return this;
    }

    public final DeliveryProperties routingKey(String value) {
        return setRoutingKey(value);
    }

    public final boolean hasResumeId() {
        return (packing_flags & 4) != 0;
    }

    public final DeliveryProperties clearResumeId() {
        packing_flags &= ~4;
        this.resumeId = null;
        setDirty(true);
        return this;
    }

    public final String getResumeId() {
        return resumeId;
    }

    public final DeliveryProperties setResumeId(String value) {
        this.resumeId = value;
        packing_flags |= 4;
        setDirty(true);
        return this;
    }

    public final DeliveryProperties resumeId(String value) {
        return setResumeId(value);
    }

    public final boolean hasResumeTtl() {
        return (packing_flags & 8) != 0;
    }

    public final DeliveryProperties clearResumeTtl() {
        packing_flags &= ~8;
        this.resumeTtl = 0;
        setDirty(true);
        return this;
    }

    public final long getResumeTtl() {
        return resumeTtl;
    }

    public final DeliveryProperties setResumeTtl(long value) {
        this.resumeTtl = value;
        packing_flags |= 8;
        setDirty(true);
        return this;
    }

    public final DeliveryProperties resumeTtl(long value) {
        return setResumeTtl(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 2048) != 0)
        {
            enc.writeUint8(this.priority.getValue());
        }
        if ((packing_flags & 4096) != 0)
        {
            enc.writeUint8(this.deliveryMode.getValue());
        }
        if ((packing_flags & 8192) != 0)
        {
            enc.writeUint64(this.ttl);
        }
        if ((packing_flags & 16384) != 0)
        {
            enc.writeDatetime(this.timestamp);
        }
        if ((packing_flags & 32768) != 0)
        {
            enc.writeDatetime(this.expiration);
        }
        if ((packing_flags & 1) != 0)
        {
            enc.writeStr8(this.exchange);
        }
        if ((packing_flags & 2) != 0)
        {
            enc.writeStr8(this.routingKey);
        }
        if ((packing_flags & 4) != 0)
        {
            enc.writeStr16(this.resumeId);
        }
        if ((packing_flags & 8) != 0)
        {
            enc.writeUint64(this.resumeTtl);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 2048) != 0)
        {
            this.priority = MessageDeliveryPriority.get(dec.readUint8());
        }
        if ((packing_flags & 4096) != 0)
        {
            this.deliveryMode = MessageDeliveryMode.get(dec.readUint8());
        }
        if ((packing_flags & 8192) != 0)
        {
            this.ttl = dec.readUint64();
        }
        if ((packing_flags & 16384) != 0)
        {
            this.timestamp = dec.readDatetime();
        }
        if ((packing_flags & 32768) != 0)
        {
            this.expiration = dec.readDatetime();
        }
        if ((packing_flags & 1) != 0)
        {
            this.exchange = dec.readStr8();
        }
        if ((packing_flags & 2) != 0)
        {
            this.routingKey = dec.readStr8();
        }
        if ((packing_flags & 4) != 0)
        {
            this.resumeId = dec.readStr16();
        }
        if ((packing_flags & 8) != 0)
        {
            this.resumeTtl = dec.readUint64();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("discardUnroutable", getDiscardUnroutable());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("immediate", getImmediate());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("redelivered", getRedelivered());
        }
        if ((packing_flags & 2048) != 0)
        {
            result.put("priority", getPriority());
        }
        if ((packing_flags & 4096) != 0)
        {
            result.put("deliveryMode", getDeliveryMode());
        }
        if ((packing_flags & 8192) != 0)
        {
            result.put("ttl", getTtl());
        }
        if ((packing_flags & 16384) != 0)
        {
            result.put("timestamp", getTimestamp());
        }
        if ((packing_flags & 32768) != 0)
        {
            result.put("expiration", getExpiration());
        }
        if ((packing_flags & 1) != 0)
        {
            result.put("exchange", getExchange());
        }
        if ((packing_flags & 2) != 0)
        {
            result.put("routingKey", getRoutingKey());
        }
        if ((packing_flags & 4) != 0)
        {
            result.put("resumeId", getResumeId());
        }
        if ((packing_flags & 8) != 0)
        {
            result.put("resumeTtl", getResumeTtl());
        }


        return result;
    }


}
