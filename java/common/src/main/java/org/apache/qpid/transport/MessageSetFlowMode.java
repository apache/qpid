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


public final class MessageSetFlowMode extends Method {

    public static final int TYPE = 1033;

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
    private MessageFlowMode flowMode;


    public MessageSetFlowMode() {}


    public MessageSetFlowMode(String destination, MessageFlowMode flowMode, Option ... _options) {
        if(destination != null) {
            setDestination(destination);
        }
        if(flowMode != null) {
            setFlowMode(flowMode);
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
        delegate.messageSetFlowMode(context, this);
    }


    public final boolean hasDestination() {
        return (packing_flags & 256) != 0;
    }

    public final MessageSetFlowMode clearDestination() {
        packing_flags &= ~256;
        this.destination = null;
        setDirty(true);
        return this;
    }

    public final String getDestination() {
        return destination;
    }

    public final MessageSetFlowMode setDestination(String value) {
        this.destination = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final MessageSetFlowMode destination(String value) {
        return setDestination(value);
    }

    public final boolean hasFlowMode() {
        return (packing_flags & 512) != 0;
    }

    public final MessageSetFlowMode clearFlowMode() {
        packing_flags &= ~512;
        this.flowMode = null;
        setDirty(true);
        return this;
    }

    public final MessageFlowMode getFlowMode() {
        return flowMode;
    }

    public final MessageSetFlowMode setFlowMode(MessageFlowMode value) {
        this.flowMode = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final MessageSetFlowMode flowMode(MessageFlowMode value) {
        return setFlowMode(value);
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
            enc.writeUint8(this.flowMode.getValue());
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
            this.flowMode = MessageFlowMode.get(dec.readUint8());
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
            result.put("flowMode", getFlowMode());
        }


        return result;
    }


}
