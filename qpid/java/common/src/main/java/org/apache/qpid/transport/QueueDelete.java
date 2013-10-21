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


public final class QueueDelete extends Method {

    public static final int TYPE = 2050;

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


    public QueueDelete() {}


    public QueueDelete(String queue, Option ... _options) {
        if(queue != null) {
            setQueue(queue);
        }

        for (int i=0; i < _options.length; i++) {
            switch (_options[i]) {
            case IF_UNUSED: packing_flags |= 512; break;
            case IF_EMPTY: packing_flags |= 1024; break;
            case SYNC: this.setSync(true); break;
            case BATCH: this.setBatch(true); break;
            case UNRELIABLE: this.setUnreliable(true); break;
            case NONE: break;
            default: throw new IllegalArgumentException("invalid option: " + _options[i]);
            }
        }

    }

    public <C> void dispatch(C context, MethodDelegate<C> delegate) {
        delegate.queueDelete(context, this);
    }


    public final boolean hasQueue() {
        return (packing_flags & 256) != 0;
    }

    public final QueueDelete clearQueue() {
        packing_flags &= ~256;
        this.queue = null;
        setDirty(true);
        return this;
    }

    public final String getQueue() {
        return queue;
    }

    public final QueueDelete setQueue(String value) {
        this.queue = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final QueueDelete queue(String value) {
        return setQueue(value);
    }

    public final boolean hasIfUnused() {
        return (packing_flags & 512) != 0;
    }

    public final QueueDelete clearIfUnused() {
        packing_flags &= ~512;

        setDirty(true);
        return this;
    }

    public final boolean getIfUnused() {
        return hasIfUnused();
    }

    public final QueueDelete setIfUnused(boolean value) {

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

    public final QueueDelete ifUnused(boolean value) {
        return setIfUnused(value);
    }

    public final boolean hasIfEmpty() {
        return (packing_flags & 1024) != 0;
    }

    public final QueueDelete clearIfEmpty() {
        packing_flags &= ~1024;

        setDirty(true);
        return this;
    }

    public final boolean getIfEmpty() {
        return hasIfEmpty();
    }

    public final QueueDelete setIfEmpty(boolean value) {

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

    public final QueueDelete ifEmpty(boolean value) {
        return setIfEmpty(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeStr8(this.queue);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.queue = dec.readStr8();
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
            result.put("ifUnused", getIfUnused());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("ifEmpty", getIfEmpty());
        }


        return result;
    }


}
