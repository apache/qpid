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



public final class RecoverResult extends Struct {

    public static final int TYPE = 1539;

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
    private java.util.List<Object> inDoubt;


    public RecoverResult() {}


    public RecoverResult(java.util.List<Object> inDoubt) {
        if(inDoubt != null) {
            setInDoubt(inDoubt);
        }

    }




    public final boolean hasInDoubt() {
        return (packing_flags & 256) != 0;
    }

    public final RecoverResult clearInDoubt() {
        packing_flags &= ~256;
        this.inDoubt = null;
        setDirty(true);
        return this;
    }

    public final java.util.List<Object> getInDoubt() {
        return inDoubt;
    }

    public final RecoverResult setInDoubt(java.util.List<Object> value) {
        this.inDoubt = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final RecoverResult inDoubt(java.util.List<Object> value) {
        return setInDoubt(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeArray(this.inDoubt);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.inDoubt = dec.readArray();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("inDoubt", getInDoubt());
        }


        return result;
    }


}
