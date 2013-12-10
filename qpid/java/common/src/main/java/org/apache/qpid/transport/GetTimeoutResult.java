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



public final class GetTimeoutResult extends Struct {

    public static final int TYPE = 1538;

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
    private long timeout;


    public GetTimeoutResult() {}


    public GetTimeoutResult(long timeout) {
        setTimeout(timeout);

    }




    public final boolean hasTimeout() {
        return (packing_flags & 256) != 0;
    }

    public final GetTimeoutResult clearTimeout() {
        packing_flags &= ~256;
        this.timeout = 0;
        setDirty(true);
        return this;
    }

    public final long getTimeout() {
        return timeout;
    }

    public final GetTimeoutResult setTimeout(long value) {
        this.timeout = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final GetTimeoutResult timeout(long value) {
        return setTimeout(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeUint32(this.timeout);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.timeout = dec.readUint32();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("timeout", getTimeout());
        }


        return result;
    }


}
