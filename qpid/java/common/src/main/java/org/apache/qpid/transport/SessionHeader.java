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



public final class SessionHeader extends Struct {

    public static final int TYPE = -1;

    public final int getStructType() {
        return TYPE;
    }

    public final int getSizeWidth() {
        return 1;
    }

    public final int getPackWidth() {
        return 1;
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

    private byte packing_flags = 0;


    public SessionHeader() {}


    public SessionHeader(Option ... _options) {

        for (int i=0; i < _options.length; i++) {
            switch (_options[i]) {
            case SYNC: packing_flags |= 1; break;
            case NONE: break;
            default: throw new IllegalArgumentException("invalid option: " + _options[i]);
            }
        }

    }




    public final boolean hasSync() {
        return (packing_flags & 1) != 0;
    }

    public final SessionHeader clearSync() {
        packing_flags &= ~1;

        setDirty(true);
        return this;
    }

    public final boolean getSync() {
        return hasSync();
    }

    public final SessionHeader setSync(boolean value) {

        if (value)
        {
            packing_flags |= 1;
        }
        else
        {
            packing_flags &= ~1;
        }

        setDirty(true);
        return this;
    }

    public final SessionHeader sync(boolean value) {
        return setSync(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint8(packing_flags);

    }

    public void read(Decoder dec)
    {
        packing_flags = (byte) dec.readUint8();

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 1) != 0)
        {
            result.put("sync", getSync());
        }


        return result;
    }


}
