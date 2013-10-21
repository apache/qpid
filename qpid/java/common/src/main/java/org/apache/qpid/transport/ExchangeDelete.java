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


public final class ExchangeDelete extends Method {

    public static final int TYPE = 1794;

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
    private String exchange;


    public ExchangeDelete() {}


    public ExchangeDelete(String exchange, Option ... _options) {
        if(exchange != null) {
            setExchange(exchange);
        }

        for (int i=0; i < _options.length; i++) {
            switch (_options[i]) {
            case IF_UNUSED: packing_flags |= 512; break;
            case SYNC: this.setSync(true); break;
            case BATCH: this.setBatch(true); break;
            case UNRELIABLE: this.setUnreliable(true); break;
            case NONE: break;
            default: throw new IllegalArgumentException("invalid option: " + _options[i]);
            }
        }

    }

    public <C> void dispatch(C context, MethodDelegate<C> delegate) {
        delegate.exchangeDelete(context, this);
    }


    public final boolean hasExchange() {
        return (packing_flags & 256) != 0;
    }

    public final ExchangeDelete clearExchange() {
        packing_flags &= ~256;
        this.exchange = null;
        setDirty(true);
        return this;
    }

    public final String getExchange() {
        return exchange;
    }

    public final ExchangeDelete setExchange(String value) {
        this.exchange = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final ExchangeDelete exchange(String value) {
        return setExchange(value);
    }

    public final boolean hasIfUnused() {
        return (packing_flags & 512) != 0;
    }

    public final ExchangeDelete clearIfUnused() {
        packing_flags &= ~512;

        setDirty(true);
        return this;
    }

    public final boolean getIfUnused() {
        return hasIfUnused();
    }

    public final ExchangeDelete setIfUnused(boolean value) {

        if (value)
        {
            packing_flags |= 512;
        }
        else
        {
            packing_flags &= ~512;
        }

        setDirty(true);
        return this;
    }

    public final ExchangeDelete ifUnused(boolean value) {
        return setIfUnused(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeStr8(this.exchange);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.exchange = dec.readStr8();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("exchange", getExchange());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("ifUnused", getIfUnused());
        }


        return result;
    }


}
