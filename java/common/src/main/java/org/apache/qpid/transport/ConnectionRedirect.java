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


public final class ConnectionRedirect extends Method {

    public static final int TYPE = 265;

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
    private String host;
    private java.util.List<Object> knownHosts;


    public ConnectionRedirect() {}


    public ConnectionRedirect(String host, java.util.List<Object> knownHosts, Option ... _options) {
        if(host != null) {
            setHost(host);
        }
        if(knownHosts != null) {
            setKnownHosts(knownHosts);
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
        delegate.connectionRedirect(context, this);
    }


    public final boolean hasHost() {
        return (packing_flags & 256) != 0;
    }

    public final ConnectionRedirect clearHost() {
        packing_flags &= ~256;
        this.host = null;
        setDirty(true);
        return this;
    }

    public final String getHost() {
        return host;
    }

    public final ConnectionRedirect setHost(String value) {
        this.host = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final ConnectionRedirect host(String value) {
        return setHost(value);
    }

    public final boolean hasKnownHosts() {
        return (packing_flags & 512) != 0;
    }

    public final ConnectionRedirect clearKnownHosts() {
        packing_flags &= ~512;
        this.knownHosts = null;
        setDirty(true);
        return this;
    }

    public final java.util.List<Object> getKnownHosts() {
        return knownHosts;
    }

    public final ConnectionRedirect setKnownHosts(java.util.List<Object> value) {
        this.knownHosts = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final ConnectionRedirect knownHosts(java.util.List<Object> value) {
        return setKnownHosts(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeStr16(this.host);
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeArray(this.knownHosts);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.host = dec.readStr16();
        }
        if ((packing_flags & 512) != 0)
        {
            this.knownHosts = dec.readArray();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("host", getHost());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("knownHosts", getKnownHosts());
        }


        return result;
    }


}
