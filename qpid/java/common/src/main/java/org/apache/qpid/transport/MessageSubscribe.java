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

import org.apache.qpid.transport.network.Frame;


public final class MessageSubscribe extends Method {

    public static final int TYPE = 1031;

    public final int getStructType() {
        return TYPE;
    }

    public final int getSizeWidth() {
        return 0;
    }

    public final int getPackWidth() {
        return 2;
    }

    public final boolean hasPayload() {
        return false;
    }

    public final byte getEncodedTrack() {
        return Frame.L4;
    }

    public final boolean isConnectionControl()
    {
        return false;
    }

    private short packing_flags = 0;
    private String queue;
    private String destination;
    private MessageAcceptMode acceptMode;
    private MessageAcquireMode acquireMode;
    private String resumeId;
    private long resumeTtl;
    private Map<String,Object> arguments;


    public MessageSubscribe() {}


    public MessageSubscribe(String queue, String destination, MessageAcceptMode acceptMode, MessageAcquireMode acquireMode, String resumeId, long resumeTtl, Map<String,Object> arguments, Option ... _options) {
        if(queue != null) {
            setQueue(queue);
        }
        if(destination != null) {
            setDestination(destination);
        }
        if(acceptMode != null) {
            setAcceptMode(acceptMode);
        }
        if(acquireMode != null) {
            setAcquireMode(acquireMode);
        }
        if(resumeId != null) {
            setResumeId(resumeId);
        }
        setResumeTtl(resumeTtl);
        if(arguments != null) {
            setArguments(arguments);
        }

        for (int i=0; i < _options.length; i++) {
            switch (_options[i]) {
            case EXCLUSIVE: packing_flags |= 4096; break;
            case SYNC: this.setSync(true); break;
            case BATCH: this.setBatch(true); break;
            case UNRELIABLE: this.setUnreliable(true); break;
            case NONE: break;
            default: throw new IllegalArgumentException("invalid option: " + _options[i]);
            }
        }

    }

    public <C> void dispatch(C context, MethodDelegate<C> delegate) {
        delegate.messageSubscribe(context, this);
    }


    public final boolean hasQueue() {
        return (packing_flags & 256) != 0;
    }

    public final MessageSubscribe clearQueue() {
        packing_flags &= ~256;
        this.queue = null;
        setDirty(true);
        return this;
    }

    public final String getQueue() {
        return queue;
    }

    public final MessageSubscribe setQueue(String value) {
        this.queue = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final MessageSubscribe queue(String value) {
        return setQueue(value);
    }

    public final boolean hasDestination() {
        return (packing_flags & 512) != 0;
    }

    public final MessageSubscribe clearDestination() {
        packing_flags &= ~512;
        this.destination = null;
        setDirty(true);
        return this;
    }

    public final String getDestination() {
        return destination;
    }

    public final MessageSubscribe setDestination(String value) {
        this.destination = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final MessageSubscribe destination(String value) {
        return setDestination(value);
    }

    public final boolean hasAcceptMode() {
        return (packing_flags & 1024) != 0;
    }

    public final MessageSubscribe clearAcceptMode() {
        packing_flags &= ~1024;
        this.acceptMode = null;
        setDirty(true);
        return this;
    }

    public final MessageAcceptMode getAcceptMode() {
        return acceptMode;
    }

    public final MessageSubscribe setAcceptMode(MessageAcceptMode value) {
        this.acceptMode = value;
        packing_flags |= 1024;
        setDirty(true);
        return this;
    }

    public final MessageSubscribe acceptMode(MessageAcceptMode value) {
        return setAcceptMode(value);
    }

    public final boolean hasAcquireMode() {
        return (packing_flags & 2048) != 0;
    }

    public final MessageSubscribe clearAcquireMode() {
        packing_flags &= ~2048;
        this.acquireMode = null;
        setDirty(true);
        return this;
    }

    public final MessageAcquireMode getAcquireMode() {
        return acquireMode;
    }

    public final MessageSubscribe setAcquireMode(MessageAcquireMode value) {
        this.acquireMode = value;
        packing_flags |= 2048;
        setDirty(true);
        return this;
    }

    public final MessageSubscribe acquireMode(MessageAcquireMode value) {
        return setAcquireMode(value);
    }

    public final boolean hasExclusive() {
        return (packing_flags & 4096) != 0;
    }

    public final MessageSubscribe clearExclusive() {
        packing_flags &= ~4096;

        setDirty(true);
        return this;
    }

    public final boolean getExclusive() {
        return hasExclusive();
    }

    public final MessageSubscribe setExclusive(boolean value) {

        if (value)
        {
            packing_flags |= 4096;
        }
        else
        {
            packing_flags &= ~4096;
        }

        setDirty(true);
        return this;
    }

    public final MessageSubscribe exclusive(boolean value) {
        return setExclusive(value);
    }

    public final boolean hasResumeId() {
        return (packing_flags & 8192) != 0;
    }

    public final MessageSubscribe clearResumeId() {
        packing_flags &= ~8192;
        this.resumeId = null;
        setDirty(true);
        return this;
    }

    public final String getResumeId() {
        return resumeId;
    }

    public final MessageSubscribe setResumeId(String value) {
        this.resumeId = value;
        packing_flags |= 8192;
        setDirty(true);
        return this;
    }

    public final MessageSubscribe resumeId(String value) {
        return setResumeId(value);
    }

    public final boolean hasResumeTtl() {
        return (packing_flags & 16384) != 0;
    }

    public final MessageSubscribe clearResumeTtl() {
        packing_flags &= ~16384;
        this.resumeTtl = 0;
        setDirty(true);
        return this;
    }

    public final long getResumeTtl() {
        return resumeTtl;
    }

    public final MessageSubscribe setResumeTtl(long value) {
        this.resumeTtl = value;
        packing_flags |= 16384;
        setDirty(true);
        return this;
    }

    public final MessageSubscribe resumeTtl(long value) {
        return setResumeTtl(value);
    }

    public final boolean hasArguments() {
        return (packing_flags & 32768) != 0;
    }

    public final MessageSubscribe clearArguments() {
        packing_flags &= ~32768;
        this.arguments = null;
        setDirty(true);
        return this;
    }

    public final Map<String,Object> getArguments() {
        return arguments;
    }

    public final MessageSubscribe setArguments(Map<String,Object> value) {
        this.arguments = value;
        packing_flags |= 32768;
        setDirty(true);
        return this;
    }

    public final MessageSubscribe arguments(Map<String,Object> value) {
        return setArguments(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeStr8(this.queue);
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeStr8(this.destination);
        }
        if ((packing_flags & 1024) != 0)
        {
            enc.writeUint8(this.acceptMode.getValue());
        }
        if ((packing_flags & 2048) != 0)
        {
            enc.writeUint8(this.acquireMode.getValue());
        }
        if ((packing_flags & 8192) != 0)
        {
            enc.writeStr16(this.resumeId);
        }
        if ((packing_flags & 16384) != 0)
        {
            enc.writeUint64(this.resumeTtl);
        }
        if ((packing_flags & 32768) != 0)
        {
            enc.writeMap(this.arguments);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.queue = dec.readStr8();
        }
        if ((packing_flags & 512) != 0)
        {
            this.destination = dec.readStr8();
        }
        if ((packing_flags & 1024) != 0)
        {
            this.acceptMode = MessageAcceptMode.get(dec.readUint8());
        }
        if ((packing_flags & 2048) != 0)
        {
            this.acquireMode = MessageAcquireMode.get(dec.readUint8());
        }
        if ((packing_flags & 8192) != 0)
        {
            this.resumeId = dec.readStr16();
        }
        if ((packing_flags & 16384) != 0)
        {
            this.resumeTtl = dec.readUint64();
        }
        if ((packing_flags & 32768) != 0)
        {
            this.arguments = dec.readMap();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("queue", getQueue());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("destination", getDestination());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("acceptMode", getAcceptMode());
        }
        if ((packing_flags & 2048) != 0)
        {
            result.put("acquireMode", getAcquireMode());
        }
        if ((packing_flags & 4096) != 0)
        {
            result.put("exclusive", getExclusive());
        }
        if ((packing_flags & 8192) != 0)
        {
            result.put("resumeId", getResumeId());
        }
        if ((packing_flags & 16384) != 0)
        {
            result.put("resumeTtl", getResumeTtl());
        }
        if ((packing_flags & 32768) != 0)
        {
            result.put("arguments", getArguments());
        }


        return result;
    }


}
