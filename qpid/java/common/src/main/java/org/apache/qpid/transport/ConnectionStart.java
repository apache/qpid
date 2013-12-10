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


public final class ConnectionStart extends Method {

    public static final int TYPE = 257;

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
    private Map<String,Object> serverProperties;
    private java.util.List<Object> mechanisms;
    private java.util.List<Object> locales;


    public ConnectionStart() {}


    public ConnectionStart(Map<String,Object> serverProperties, java.util.List<Object> mechanisms, java.util.List<Object> locales, Option ... _options) {
        if(serverProperties != null) {
            setServerProperties(serverProperties);
        }
        if(mechanisms != null) {
            setMechanisms(mechanisms);
        }
        if(locales != null) {
            setLocales(locales);
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
        delegate.connectionStart(context, this);
    }


    public final boolean hasServerProperties() {
        return (packing_flags & 256) != 0;
    }

    public final ConnectionStart clearServerProperties() {
        packing_flags &= ~256;
        this.serverProperties = null;
        setDirty(true);
        return this;
    }

    public final Map<String,Object> getServerProperties() {
        return serverProperties;
    }

    public final ConnectionStart setServerProperties(Map<String,Object> value) {
        this.serverProperties = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final ConnectionStart serverProperties(Map<String,Object> value) {
        return setServerProperties(value);
    }

    public final boolean hasMechanisms() {
        return (packing_flags & 512) != 0;
    }

    public final ConnectionStart clearMechanisms() {
        packing_flags &= ~512;
        this.mechanisms = null;
        setDirty(true);
        return this;
    }

    public final java.util.List<Object> getMechanisms() {
        return mechanisms;
    }

    public final ConnectionStart setMechanisms(java.util.List<Object> value) {
        this.mechanisms = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final ConnectionStart mechanisms(java.util.List<Object> value) {
        return setMechanisms(value);
    }

    public final boolean hasLocales() {
        return (packing_flags & 1024) != 0;
    }

    public final ConnectionStart clearLocales() {
        packing_flags &= ~1024;
        this.locales = null;
        setDirty(true);
        return this;
    }

    public final java.util.List<Object> getLocales() {
        return locales;
    }

    public final ConnectionStart setLocales(java.util.List<Object> value) {
        this.locales = value;
        packing_flags |= 1024;
        setDirty(true);
        return this;
    }

    public final ConnectionStart locales(java.util.List<Object> value) {
        return setLocales(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeMap(this.serverProperties);
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeArray(this.mechanisms);
        }
        if ((packing_flags & 1024) != 0)
        {
            enc.writeArray(this.locales);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.serverProperties = dec.readMap();
        }
        if ((packing_flags & 512) != 0)
        {
            this.mechanisms = dec.readArray();
        }
        if ((packing_flags & 1024) != 0)
        {
            this.locales = dec.readArray();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("serverProperties", getServerProperties());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("mechanisms", getMechanisms());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("locales", getLocales());
        }


        return result;
    }


}
