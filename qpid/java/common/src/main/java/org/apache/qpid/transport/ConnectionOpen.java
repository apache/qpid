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


public final class ConnectionOpen extends Method {

    public static final int TYPE = 263;

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
    private String virtualHost;
    private java.util.List<Object> capabilities;


    public ConnectionOpen() {}


    public ConnectionOpen(String virtualHost, java.util.List<Object> capabilities, Option ... _options) {
        if(virtualHost != null) {
            setVirtualHost(virtualHost);
        }
        if(capabilities != null) {
            setCapabilities(capabilities);
        }

        for (int i=0; i < _options.length; i++) {
            switch (_options[i]) {
            case INSIST: packing_flags |= 1024; break;
            case SYNC: this.setSync(true); break;
            case BATCH: this.setBatch(true); break;
            case UNRELIABLE: this.setUnreliable(true); break;
            case NONE: break;
            default: throw new IllegalArgumentException("invalid option: " + _options[i]);
            }
        }

    }

    public <C> void dispatch(C context, MethodDelegate<C> delegate) {
        delegate.connectionOpen(context, this);
    }


    public final boolean hasVirtualHost() {
        return (packing_flags & 256) != 0;
    }

    public final ConnectionOpen clearVirtualHost() {
        packing_flags &= ~256;
        this.virtualHost = null;
        setDirty(true);
        return this;
    }

    public final String getVirtualHost() {
        return virtualHost;
    }

    public final ConnectionOpen setVirtualHost(String value) {
        this.virtualHost = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final ConnectionOpen virtualHost(String value) {
        return setVirtualHost(value);
    }

    public final boolean hasCapabilities() {
        return (packing_flags & 512) != 0;
    }

    public final ConnectionOpen clearCapabilities() {
        packing_flags &= ~512;
        this.capabilities = null;
        setDirty(true);
        return this;
    }

    public final java.util.List<Object> getCapabilities() {
        return capabilities;
    }

    public final ConnectionOpen setCapabilities(java.util.List<Object> value) {
        this.capabilities = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final ConnectionOpen capabilities(java.util.List<Object> value) {
        return setCapabilities(value);
    }

    public final boolean hasInsist() {
        return (packing_flags & 1024) != 0;
    }

    public final ConnectionOpen clearInsist() {
        packing_flags &= ~1024;

        setDirty(true);
        return this;
    }

    public final boolean getInsist() {
        return hasInsist();
    }

    public final ConnectionOpen setInsist(boolean value) {

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

    public final ConnectionOpen insist(boolean value) {
        return setInsist(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeStr8(this.virtualHost);
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeArray(this.capabilities);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.virtualHost = dec.readStr8();
        }
        if ((packing_flags & 512) != 0)
        {
            this.capabilities = dec.readArray();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("virtualHost", getVirtualHost());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("capabilities", getCapabilities());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("insist", getInsist());
        }


        return result;
    }


}
