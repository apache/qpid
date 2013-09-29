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



public final class Xid extends Struct {

    public static final int TYPE = 1540;

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
    private long format;
    private byte[] globalId;
    private byte[] branchId;


    public Xid() {}


    public Xid(long format, byte[] globalId, byte[] branchId) {
        setFormat(format);
        if(globalId != null) {
            setGlobalId(globalId);
        }
        if(branchId != null) {
            setBranchId(branchId);
        }

    }




    public final boolean hasFormat() {
        return (packing_flags & 256) != 0;
    }

    public final Xid clearFormat() {
        packing_flags &= ~256;
        this.format = 0;
        setDirty(true);
        return this;
    }

    public final long getFormat() {
        return format;
    }

    public final Xid setFormat(long value) {
        this.format = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final Xid format(long value) {
        return setFormat(value);
    }

    public final boolean hasGlobalId() {
        return (packing_flags & 512) != 0;
    }

    public final Xid clearGlobalId() {
        packing_flags &= ~512;
        this.globalId = null;
        setDirty(true);
        return this;
    }

    public final byte[] getGlobalId() {
        return globalId;
    }

    public final Xid setGlobalId(byte[] value) {
        this.globalId = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final Xid globalId(byte[] value) {
        return setGlobalId(value);
    }

    public final boolean hasBranchId() {
        return (packing_flags & 1024) != 0;
    }

    public final Xid clearBranchId() {
        packing_flags &= ~1024;
        this.branchId = null;
        setDirty(true);
        return this;
    }

    public final byte[] getBranchId() {
        return branchId;
    }

    public final Xid setBranchId(byte[] value) {
        this.branchId = value;
        packing_flags |= 1024;
        setDirty(true);
        return this;
    }

    public final Xid branchId(byte[] value) {
        return setBranchId(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeUint32(this.format);
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeVbin8(this.globalId);
        }
        if ((packing_flags & 1024) != 0)
        {
            enc.writeVbin8(this.branchId);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.format = dec.readUint32();
        }
        if ((packing_flags & 512) != 0)
        {
            this.globalId = dec.readVbin8();
        }
        if ((packing_flags & 1024) != 0)
        {
            this.branchId = dec.readVbin8();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("format", getFormat());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("globalId", getGlobalId());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("branchId", getBranchId());
        }


        return result;
    }


}
