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


public final class QueueDeclare extends Method {

    public static final int TYPE = 2049;

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
    private String alternateExchange;
    private Map<String,Object> arguments;


    public QueueDeclare() {}


    public QueueDeclare(String queue, String alternateExchange, Map<String,Object> arguments, Option ... _options) {
        if(queue != null) {
            setQueue(queue);
        }
        if(alternateExchange != null) {
            setAlternateExchange(alternateExchange);
        }
        if(arguments != null) {
            setArguments(arguments);
        }

        for (int i=0; i < _options.length; i++) {
            switch (_options[i]) {
            case PASSIVE: packing_flags |= 1024; break;
            case DURABLE: packing_flags |= 2048; break;
            case EXCLUSIVE: packing_flags |= 4096; break;
            case AUTO_DELETE: packing_flags |= 8192; break;
            case SYNC: this.setSync(true); break;
            case BATCH: this.setBatch(true); break;
            case UNRELIABLE: this.setUnreliable(true); break;
            case NONE: break;
            default: throw new IllegalArgumentException("invalid option: " + _options[i]);
            }
        }

    }

    public <C> void dispatch(C context, MethodDelegate<C> delegate) {
        delegate.queueDeclare(context, this);
    }


    public final boolean hasQueue() {
        return (packing_flags & 256) != 0;
    }

    public final QueueDeclare clearQueue() {
        packing_flags &= ~256;
        this.queue = null;
        setDirty(true);
        return this;
    }

    public final String getQueue() {
        return queue;
    }

    public final QueueDeclare setQueue(String value) {
        this.queue = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final QueueDeclare queue(String value) {
        return setQueue(value);
    }

    public final boolean hasAlternateExchange() {
        return (packing_flags & 512) != 0;
    }

    public final QueueDeclare clearAlternateExchange() {
        packing_flags &= ~512;
        this.alternateExchange = null;
        setDirty(true);
        return this;
    }

    public final String getAlternateExchange() {
        return alternateExchange;
    }

    public final QueueDeclare setAlternateExchange(String value) {
        this.alternateExchange = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final QueueDeclare alternateExchange(String value) {
        return setAlternateExchange(value);
    }

    public final boolean hasPassive() {
        return (packing_flags & 1024) != 0;
    }

    public final QueueDeclare clearPassive() {
        packing_flags &= ~1024;

        setDirty(true);
        return this;
    }

    public final boolean getPassive() {
        return hasPassive();
    }

    public final QueueDeclare setPassive(boolean value) {

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

    public final QueueDeclare passive(boolean value) {
        return setPassive(value);
    }

    public final boolean hasDurable() {
        return (packing_flags & 2048) != 0;
    }

    public final QueueDeclare clearDurable() {
        packing_flags &= ~2048;

        setDirty(true);
        return this;
    }

    public final boolean getDurable() {
        return hasDurable();
    }

    public final QueueDeclare setDurable(boolean value) {

        if (value)
        {
            packing_flags |= 2048;
        }
        else
        {
            packing_flags &= ~2048;
        }

        setDirty(true);
        return this;
    }

    public final QueueDeclare durable(boolean value) {
        return setDurable(value);
    }

    public final boolean hasExclusive() {
        return (packing_flags & 4096) != 0;
    }

    public final QueueDeclare clearExclusive() {
        packing_flags &= ~4096;

        setDirty(true);
        return this;
    }

    public final boolean getExclusive() {
        return hasExclusive();
    }

    public final QueueDeclare setExclusive(boolean value) {

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

    public final QueueDeclare exclusive(boolean value) {
        return setExclusive(value);
    }

    public final boolean hasAutoDelete() {
        return (packing_flags & 8192) != 0;
    }

    public final QueueDeclare clearAutoDelete() {
        packing_flags &= ~8192;

        setDirty(true);
        return this;
    }

    public final boolean getAutoDelete() {
        return hasAutoDelete();
    }

    public final QueueDeclare setAutoDelete(boolean value) {

        if (value)
        {
            packing_flags |= 8192;
        }
        else
        {
            packing_flags &= ~8192;
        }

        setDirty(true);
        return this;
    }

    public final QueueDeclare autoDelete(boolean value) {
        return setAutoDelete(value);
    }

    public final boolean hasArguments() {
        return (packing_flags & 16384) != 0;
    }

    public final QueueDeclare clearArguments() {
        packing_flags &= ~16384;
        this.arguments = null;
        setDirty(true);
        return this;
    }

    public final Map<String,Object> getArguments() {
        return arguments;
    }

    public final QueueDeclare setArguments(Map<String,Object> value) {
        this.arguments = value;
        packing_flags |= 16384;
        setDirty(true);
        return this;
    }

    public final QueueDeclare arguments(Map<String,Object> value) {
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
            enc.writeStr8(this.alternateExchange);
        }
        if ((packing_flags & 16384) != 0)
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
            this.alternateExchange = dec.readStr8();
        }
        if ((packing_flags & 16384) != 0)
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
            result.put("alternateExchange", getAlternateExchange());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("passive", getPassive());
        }
        if ((packing_flags & 2048) != 0)
        {
            result.put("durable", getDurable());
        }
        if ((packing_flags & 4096) != 0)
        {
            result.put("exclusive", getExclusive());
        }
        if ((packing_flags & 8192) != 0)
        {
            result.put("autoDelete", getAutoDelete());
        }
        if ((packing_flags & 16384) != 0)
        {
            result.put("arguments", getArguments());
        }


        return result;
    }


}
