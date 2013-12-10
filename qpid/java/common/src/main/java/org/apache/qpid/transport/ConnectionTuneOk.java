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


public final class ConnectionTuneOk extends Method {

    public static final int TYPE = 262;

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
        return Frame.L1;
    }

    public final boolean isConnectionControl()
    {
        return true;
    }

    private short packing_flags = 0;
    private int channelMax;
    private int maxFrameSize;
    private int heartbeat;


    public ConnectionTuneOk() {}


    public ConnectionTuneOk(int channelMax, int maxFrameSize, int heartbeat, Option ... _options) {
        setChannelMax(channelMax);
        setMaxFrameSize(maxFrameSize);
        setHeartbeat(heartbeat);

        for (int i=0; i < _options.length; i++) {
            switch (_options[i]) {
            case SYNC: this.setSync(true); break;
            case BATCH: this.setBatch(true); break;
            case UNRELIABLE: this.setUnreliable(true); break;
            case NONE: break;
            default: throw new IllegalArgumentException("invalid option: " + _options[i]);
            }
        }

    }

    public <C> void dispatch(C context, MethodDelegate<C> delegate) {
        delegate.connectionTuneOk(context, this);
    }


    public final boolean hasChannelMax() {
        return (packing_flags & 256) != 0;
    }

    public final ConnectionTuneOk clearChannelMax() {
        packing_flags &= ~256;
        this.channelMax = 0;
        setDirty(true);
        return this;
    }

    public final int getChannelMax() {
        return channelMax;
    }

    public final ConnectionTuneOk setChannelMax(int value) {
        this.channelMax = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final ConnectionTuneOk channelMax(int value) {
        return setChannelMax(value);
    }

    public final boolean hasMaxFrameSize() {
        return (packing_flags & 512) != 0;
    }

    public final ConnectionTuneOk clearMaxFrameSize() {
        packing_flags &= ~512;
        this.maxFrameSize = 0;
        setDirty(true);
        return this;
    }

    public final int getMaxFrameSize() {
        return maxFrameSize;
    }

    public final ConnectionTuneOk setMaxFrameSize(int value) {
        this.maxFrameSize = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final ConnectionTuneOk maxFrameSize(int value) {
        return setMaxFrameSize(value);
    }

    public final boolean hasHeartbeat() {
        return (packing_flags & 1024) != 0;
    }

    public final ConnectionTuneOk clearHeartbeat() {
        packing_flags &= ~1024;
        this.heartbeat = 0;
        setDirty(true);
        return this;
    }

    public final int getHeartbeat() {
        return heartbeat;
    }

    public final ConnectionTuneOk setHeartbeat(int value) {
        this.heartbeat = value;
        packing_flags |= 1024;
        setDirty(true);
        return this;
    }

    public final ConnectionTuneOk heartbeat(int value) {
        return setHeartbeat(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeUint16(this.channelMax);
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeUint16(this.maxFrameSize);
        }
        if ((packing_flags & 1024) != 0)
        {
            enc.writeUint16(this.heartbeat);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.channelMax = dec.readUint16();
        }
        if ((packing_flags & 512) != 0)
        {
            this.maxFrameSize = dec.readUint16();
        }
        if ((packing_flags & 1024) != 0)
        {
            this.heartbeat = dec.readUint16();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("channelMax", getChannelMax());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("maxFrameSize", getMaxFrameSize());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("heartbeat", getHeartbeat());
        }


        return result;
    }


}
