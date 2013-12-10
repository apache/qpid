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


public final class SessionExpected extends Method {

    public static final int TYPE = 520;

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
        return Frame.L3;
    }

    public final boolean isConnectionControl()
    {
        return false;
    }

    private short packing_flags = 0;
    private RangeSet commands;
    private java.util.List<Object> fragments;


    public SessionExpected() {}


    public SessionExpected(RangeSet commands, java.util.List<Object> fragments, Option ... _options) {
        if(commands != null) {
            setCommands(commands);
        }
        if(fragments != null) {
            setFragments(fragments);
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
        delegate.sessionExpected(context, this);
    }


    public final boolean hasCommands() {
        return (packing_flags & 256) != 0;
    }

    public final SessionExpected clearCommands() {
        packing_flags &= ~256;
        this.commands = null;
        setDirty(true);
        return this;
    }

    public final RangeSet getCommands() {
        return commands;
    }

    public final SessionExpected setCommands(RangeSet value) {
        this.commands = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final SessionExpected commands(RangeSet value) {
        return setCommands(value);
    }

    public final boolean hasFragments() {
        return (packing_flags & 512) != 0;
    }

    public final SessionExpected clearFragments() {
        packing_flags &= ~512;
        this.fragments = null;
        setDirty(true);
        return this;
    }

    public final java.util.List<Object> getFragments() {
        return fragments;
    }

    public final SessionExpected setFragments(java.util.List<Object> value) {
        this.fragments = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final SessionExpected fragments(java.util.List<Object> value) {
        return setFragments(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeSequenceSet(this.commands);
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeArray(this.fragments);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.commands = dec.readSequenceSet();
        }
        if ((packing_flags & 512) != 0)
        {
            this.fragments = dec.readArray();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("commands", getCommands());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("fragments", getFragments());
        }


        return result;
    }


}
