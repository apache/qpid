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


public final class SessionDetached extends Method {

    public static final int TYPE = 516;

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
        return Frame.L2;
    }

    public final boolean isConnectionControl()
    {
        return false;
    }

    private short packing_flags = 0;
    private byte[] name;
    private SessionDetachCode code;


    public SessionDetached() {}


    public SessionDetached(byte[] name, SessionDetachCode code, Option ... _options) {
        if(name != null) {
            setName(name);
        }
        if(code != null) {
            setCode(code);
        }

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
        delegate.sessionDetached(context, this);
    }


    public final boolean hasName() {
        return (packing_flags & 256) != 0;
    }

    public final SessionDetached clearName() {
        packing_flags &= ~256;
        this.name = null;
        setDirty(true);
        return this;
    }

    public final byte[] getName() {
        return name;
    }

    public final SessionDetached setName(byte[] value) {
        this.name = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final SessionDetached name(byte[] value) {
        return setName(value);
    }

    public final boolean hasCode() {
        return (packing_flags & 512) != 0;
    }

    public final SessionDetached clearCode() {
        packing_flags &= ~512;
        this.code = null;
        setDirty(true);
        return this;
    }

    public final SessionDetachCode getCode() {
        return code;
    }

    public final SessionDetached setCode(SessionDetachCode value) {
        this.code = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final SessionDetached code(SessionDetachCode value) {
        return setCode(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeVbin16(this.name);
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeUint8(this.code.getValue());
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.name = dec.readVbin16();
        }
        if ((packing_flags & 512) != 0)
        {
            this.code = SessionDetachCode.get(dec.readUint8());
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("name", getName());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("code", getCode());
        }


        return result;
    }


}
