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



public final class ReplyTo extends Struct {

    public static final int TYPE = -3;

    public final int getStructType() {
        return TYPE;
    }

    public final int getSizeWidth() {
        return 2;
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
    private String exchange;
    private String routingKey;


    public ReplyTo() {}


    public ReplyTo(String exchange, String routingKey) {
        if(exchange != null) {
            setExchange(exchange);
        }
        if(routingKey != null) {
            setRoutingKey(routingKey);
        }

    }




    public final boolean hasExchange() {
        return (packing_flags & 256) != 0;
    }

    public final ReplyTo clearExchange() {
        packing_flags &= ~256;
        this.exchange = null;
        setDirty(true);
        return this;
    }

    public final String getExchange() {
        return exchange;
    }

    public final ReplyTo setExchange(String value) {
        this.exchange = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final ReplyTo exchange(String value) {
        return setExchange(value);
    }

    public final boolean hasRoutingKey() {
        return (packing_flags & 512) != 0;
    }

    public final ReplyTo clearRoutingKey() {
        packing_flags &= ~512;
        this.routingKey = null;
        setDirty(true);
        return this;
    }

    public final String getRoutingKey() {
        return routingKey;
    }

    public final ReplyTo setRoutingKey(String value) {
        this.routingKey = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final ReplyTo routingKey(String value) {
        return setRoutingKey(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeStr8(this.exchange);
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeStr8(this.routingKey);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.exchange = dec.readStr8();
        }
        if ((packing_flags & 512) != 0)
        {
            this.routingKey = dec.readStr8();
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
            result.put("routingKey", getRoutingKey());
        }


        return result;
    }

    public boolean equals(final Object obj){
        if (this == obj){
            return true;
        }

        if(!(obj instanceof ReplyTo)){
            return false;
        }

        final ReplyTo reply = (ReplyTo) obj;
        return (routingKey == null ? reply.getRoutingKey() == null : routingKey.equals(reply.getRoutingKey()))
            && (exchange == null ? reply.getExchange() == null : exchange.equals(reply.getExchange()));
    }

    public int hashCode(){
        int result = routingKey == null ? 1 : routingKey.hashCode();
        return 31 * result + (exchange == null ? 5 : exchange.hashCode());
    }
}
