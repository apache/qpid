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


public final class MessageFlow extends Method {

    public static final int TYPE = 1034;

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
    private String destination;
    private MessageCreditUnit unit;
    private long value;


    public MessageFlow() {}


    public MessageFlow(String destination, MessageCreditUnit unit, long value, Option ... _options) {
        if(destination != null) {
            setDestination(destination);
        }
        if(unit != null) {
            setUnit(unit);
        }
        setValue(value);

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
        delegate.messageFlow(context, this);
    }


    public final boolean hasDestination() {
        return (packing_flags & 256) != 0;
    }

    public final MessageFlow clearDestination() {
        packing_flags &= ~256;
        this.destination = null;
        setDirty(true);
        return this;
    }

    public final String getDestination() {
        return destination;
    }

    public final MessageFlow setDestination(String value) {
        this.destination = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final MessageFlow destination(String value) {
        return setDestination(value);
    }

    public final boolean hasUnit() {
        return (packing_flags & 512) != 0;
    }

    public final MessageFlow clearUnit() {
        packing_flags &= ~512;
        this.unit = null;
        setDirty(true);
        return this;
    }

    public final MessageCreditUnit getUnit() {
        return unit;
    }

    public final MessageFlow setUnit(MessageCreditUnit value) {
        this.unit = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final MessageFlow unit(MessageCreditUnit value) {
        return setUnit(value);
    }

    public final boolean hasValue() {
        return (packing_flags & 1024) != 0;
    }

    public final MessageFlow clearValue() {
        packing_flags &= ~1024;
        this.value = 0;
        setDirty(true);
        return this;
    }

    public final long getValue() {
        return value;
    }

    public final MessageFlow setValue(long value) {
        this.value = value;
        packing_flags |= 1024;
        setDirty(true);
        return this;
    }

    public final MessageFlow value(long value) {
        return setValue(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeStr8(this.destination);
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeUint8(this.unit.getValue());
        }
        if ((packing_flags & 1024) != 0)
        {
            enc.writeUint32(this.value);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.destination = dec.readStr8();
        }
        if ((packing_flags & 512) != 0)
        {
            this.unit = MessageCreditUnit.get(dec.readUint8());
        }
        if ((packing_flags & 1024) != 0)
        {
            this.value = dec.readUint32();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("destination", getDestination());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("unit", getUnit());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("value", getValue());
        }


        return result;
    }


}
