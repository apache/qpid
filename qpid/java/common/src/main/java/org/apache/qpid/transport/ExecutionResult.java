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


public final class ExecutionResult extends Method {

    public static final int TYPE = 770;

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
    private int commandId;
    private Struct value;


    public ExecutionResult() {}


    public ExecutionResult(int commandId, Struct value, Option ... _options) {
        setCommandId(commandId);
        if(value != null) {
            setValue(value);
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
        delegate.executionResult(context, this);
    }


    public final boolean hasCommandId() {
        return (packing_flags & 256) != 0;
    }

    public final ExecutionResult clearCommandId() {
        packing_flags &= ~256;
        this.commandId = 0;
        setDirty(true);
        return this;
    }

    public final int getCommandId() {
        return commandId;
    }

    public final ExecutionResult setCommandId(int value) {
        this.commandId = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final ExecutionResult commandId(int value) {
        return setCommandId(value);
    }

    public final boolean hasValue() {
        return (packing_flags & 512) != 0;
    }

    public final ExecutionResult clearValue() {
        packing_flags &= ~512;
        this.value = null;
        setDirty(true);
        return this;
    }

    public final Struct getValue() {
        return value;
    }

    public final ExecutionResult setValue(Struct value) {
        this.value = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final ExecutionResult value(Struct value) {
        return setValue(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeSequenceNo(this.commandId);
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeStruct32(this.value);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.commandId = dec.readSequenceNo();
        }
        if ((packing_flags & 512) != 0)
        {
            this.value = dec.readStruct32();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("commandId", getCommandId());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("value", getValue());
        }


        return result;
    }


}
