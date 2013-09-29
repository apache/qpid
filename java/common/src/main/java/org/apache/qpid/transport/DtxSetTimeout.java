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


public final class DtxSetTimeout extends Method {

    public static final int TYPE = 1546;

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
    private Xid xid;
    private long timeout;


    public DtxSetTimeout() {}


    public DtxSetTimeout(Xid xid, long timeout, Option ... _options) {
        if(xid != null) {
            setXid(xid);
        }
        setTimeout(timeout);

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
        delegate.dtxSetTimeout(context, this);
    }


    public final boolean hasXid() {
        return (packing_flags & 256) != 0;
    }

    public final DtxSetTimeout clearXid() {
        packing_flags &= ~256;
        this.xid = null;
        setDirty(true);
        return this;
    }

    public final Xid getXid() {
        return xid;
    }

    public final DtxSetTimeout setXid(Xid value) {
        this.xid = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final DtxSetTimeout xid(Xid value) {
        return setXid(value);
    }

    public final boolean hasTimeout() {
        return (packing_flags & 512) != 0;
    }

    public final DtxSetTimeout clearTimeout() {
        packing_flags &= ~512;
        this.timeout = 0;
        setDirty(true);
        return this;
    }

    public final long getTimeout() {
        return timeout;
    }

    public final DtxSetTimeout setTimeout(long value) {
        this.timeout = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final DtxSetTimeout timeout(long value) {
        return setTimeout(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeStruct(Xid.TYPE, this.xid);
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeUint32(this.timeout);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.xid = (Xid)dec.readStruct(Xid.TYPE);
        }
        if ((packing_flags & 512) != 0)
        {
            this.timeout = dec.readUint32();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("xid", getXid());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("timeout", getTimeout());
        }


        return result;
    }


}
