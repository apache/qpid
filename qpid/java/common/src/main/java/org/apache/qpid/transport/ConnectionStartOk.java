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


public final class ConnectionStartOk extends Method {

    public static final int TYPE = 258;

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
    private Map<String,Object> clientProperties;
    private String mechanism;
    private byte[] response;
    private String locale;


    public ConnectionStartOk() {}


    public ConnectionStartOk(Map<String,Object> clientProperties, String mechanism, byte[] response, String locale, Option ... _options) {
        if(clientProperties != null) {
            setClientProperties(clientProperties);
        }
        if(mechanism != null) {
            setMechanism(mechanism);
        }
        if(response != null) {
            setResponse(response);
        }
        if(locale != null) {
            setLocale(locale);
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
        delegate.connectionStartOk(context, this);
    }


    public final boolean hasClientProperties() {
        return (packing_flags & 256) != 0;
    }

    public final ConnectionStartOk clearClientProperties() {
        packing_flags &= ~256;
        this.clientProperties = null;
        setDirty(true);
        return this;
    }

    public final Map<String,Object> getClientProperties() {
        return clientProperties;
    }

    public final ConnectionStartOk setClientProperties(Map<String,Object> value) {
        this.clientProperties = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final ConnectionStartOk clientProperties(Map<String,Object> value) {
        return setClientProperties(value);
    }

    public final boolean hasMechanism() {
        return (packing_flags & 512) != 0;
    }

    public final ConnectionStartOk clearMechanism() {
        packing_flags &= ~512;
        this.mechanism = null;
        setDirty(true);
        return this;
    }

    public final String getMechanism() {
        return mechanism;
    }

    public final ConnectionStartOk setMechanism(String value) {
        this.mechanism = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final ConnectionStartOk mechanism(String value) {
        return setMechanism(value);
    }

    public final boolean hasResponse() {
        return (packing_flags & 1024) != 0;
    }

    public final ConnectionStartOk clearResponse() {
        packing_flags &= ~1024;
        this.response = null;
        setDirty(true);
        return this;
    }

    public final byte[] getResponse() {
        return response;
    }

    public final ConnectionStartOk setResponse(byte[] value) {
        this.response = value;
        packing_flags |= 1024;
        setDirty(true);
        return this;
    }

    public final ConnectionStartOk response(byte[] value) {
        return setResponse(value);
    }

    public final boolean hasLocale() {
        return (packing_flags & 2048) != 0;
    }

    public final ConnectionStartOk clearLocale() {
        packing_flags &= ~2048;
        this.locale = null;
        setDirty(true);
        return this;
    }

    public final String getLocale() {
        return locale;
    }

    public final ConnectionStartOk setLocale(String value) {
        this.locale = value;
        packing_flags |= 2048;
        setDirty(true);
        return this;
    }

    public final ConnectionStartOk locale(String value) {
        return setLocale(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeMap(this.clientProperties);
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeStr8(this.mechanism);
        }
        if ((packing_flags & 1024) != 0)
        {
            enc.writeVbin32(this.response);
        }
        if ((packing_flags & 2048) != 0)
        {
            enc.writeStr8(this.locale);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.clientProperties = dec.readMap();
        }
        if ((packing_flags & 512) != 0)
        {
            this.mechanism = dec.readStr8();
        }
        if ((packing_flags & 1024) != 0)
        {
            this.response = dec.readVbin32();
        }
        if ((packing_flags & 2048) != 0)
        {
            this.locale = dec.readStr8();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("clientProperties", getClientProperties());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("mechanism", getMechanism());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("response", getResponse());
        }
        if ((packing_flags & 2048) != 0)
        {
            result.put("locale", getLocale());
        }


        return result;
    }


}
