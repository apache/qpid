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



public final class ExchangeBoundResult extends Struct {

    public static final int TYPE = 1794;

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


    public ExchangeBoundResult() {}


    public ExchangeBoundResult(Option ... _options) {

        for (int i=0; i < _options.length; i++) {
            switch (_options[i]) {
            case EXCHANGE_NOT_FOUND: packing_flags |= 256; break;
            case QUEUE_NOT_FOUND: packing_flags |= 512; break;
            case QUEUE_NOT_MATCHED: packing_flags |= 1024; break;
            case KEY_NOT_MATCHED: packing_flags |= 2048; break;
            case ARGS_NOT_MATCHED: packing_flags |= 4096; break;
            case NONE: break;
            default: throw new IllegalArgumentException("invalid option: " + _options[i]);
            }
        }

    }




    public final boolean hasExchangeNotFound() {
        return (packing_flags & 256) != 0;
    }

    public final ExchangeBoundResult clearExchangeNotFound() {
        packing_flags &= ~256;

        setDirty(true);
        return this;
    }

    public final boolean getExchangeNotFound() {
        return hasExchangeNotFound();
    }

    public final ExchangeBoundResult setExchangeNotFound(boolean value) {

        if (value)
        {
            packing_flags |= 256;
        }
        else
        {
            packing_flags &= ~256;
        }

        setDirty(true);
        return this;
    }

    public final ExchangeBoundResult exchangeNotFound(boolean value) {
        return setExchangeNotFound(value);
    }

    public final boolean hasQueueNotFound() {
        return (packing_flags & 512) != 0;
    }

    public final ExchangeBoundResult clearQueueNotFound() {
        packing_flags &= ~512;

        setDirty(true);
        return this;
    }

    public final boolean getQueueNotFound() {
        return hasQueueNotFound();
    }

    public final ExchangeBoundResult setQueueNotFound(boolean value) {

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

    public final ExchangeBoundResult queueNotFound(boolean value) {
        return setQueueNotFound(value);
    }

    public final boolean hasQueueNotMatched() {
        return (packing_flags & 1024) != 0;
    }

    public final ExchangeBoundResult clearQueueNotMatched() {
        packing_flags &= ~1024;

        setDirty(true);
        return this;
    }

    public final boolean getQueueNotMatched() {
        return hasQueueNotMatched();
    }

    public final ExchangeBoundResult setQueueNotMatched(boolean value) {

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

    public final ExchangeBoundResult queueNotMatched(boolean value) {
        return setQueueNotMatched(value);
    }

    public final boolean hasKeyNotMatched() {
        return (packing_flags & 2048) != 0;
    }

    public final ExchangeBoundResult clearKeyNotMatched() {
        packing_flags &= ~2048;

        setDirty(true);
        return this;
    }

    public final boolean getKeyNotMatched() {
        return hasKeyNotMatched();
    }

    public final ExchangeBoundResult setKeyNotMatched(boolean value) {

        if (value)
        {
            packing_flags |= 2048;
        }
        else
        {
            packing_flags &= ~2048;
        }

        setDirty(true);
        return this;
    }

    public final ExchangeBoundResult keyNotMatched(boolean value) {
        return setKeyNotMatched(value);
    }

    public final boolean hasArgsNotMatched() {
        return (packing_flags & 4096) != 0;
    }

    public final ExchangeBoundResult clearArgsNotMatched() {
        packing_flags &= ~4096;

        setDirty(true);
        return this;
    }

    public final boolean getArgsNotMatched() {
        return hasArgsNotMatched();
    }

    public final ExchangeBoundResult setArgsNotMatched(boolean value) {

        if (value)
        {
            packing_flags |= 4096;
        }
        else
        {
            packing_flags &= ~4096;
        }

        setDirty(true);
        return this;
    }

    public final ExchangeBoundResult argsNotMatched(boolean value) {
        return setArgsNotMatched(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("exchangeNotFound", getExchangeNotFound());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("queueNotFound", getQueueNotFound());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("queueNotMatched", getQueueNotMatched());
        }
        if ((packing_flags & 2048) != 0)
        {
            result.put("keyNotMatched", getKeyNotMatched());
        }
        if ((packing_flags & 4096) != 0)
        {
            result.put("argsNotMatched", getArgsNotMatched());
        }


        return result;
    }


}
