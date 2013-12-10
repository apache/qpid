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


public final class MessageReject extends Method {

    public static final int TYPE = 1027;

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
    private RangeSet transfers;
    private MessageRejectCode code;
    private String text;


    public MessageReject() {}


    public MessageReject(RangeSet transfers, MessageRejectCode code, String text, Option ... _options) {
        if(transfers != null) {
            setTransfers(transfers);
        }
        if(code != null) {
            setCode(code);
        }
        if(text != null) {
            setText(text);
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
        delegate.messageReject(context, this);
    }


    public final boolean hasTransfers() {
        return (packing_flags & 256) != 0;
    }

    public final MessageReject clearTransfers() {
        packing_flags &= ~256;
        this.transfers = null;
        setDirty(true);
        return this;
    }

    public final RangeSet getTransfers() {
        return transfers;
    }

    public final MessageReject setTransfers(RangeSet value) {
        this.transfers = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final MessageReject transfers(RangeSet value) {
        return setTransfers(value);
    }

    public final boolean hasCode() {
        return (packing_flags & 512) != 0;
    }

    public final MessageReject clearCode() {
        packing_flags &= ~512;
        this.code = null;
        setDirty(true);
        return this;
    }

    public final MessageRejectCode getCode() {
        return code;
    }

    public final MessageReject setCode(MessageRejectCode value) {
        this.code = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final MessageReject code(MessageRejectCode value) {
        return setCode(value);
    }

    public final boolean hasText() {
        return (packing_flags & 1024) != 0;
    }

    public final MessageReject clearText() {
        packing_flags &= ~1024;
        this.text = null;
        setDirty(true);
        return this;
    }

    public final String getText() {
        return text;
    }

    public final MessageReject setText(String value) {
        this.text = value;
        packing_flags |= 1024;
        setDirty(true);
        return this;
    }

    public final MessageReject text(String value) {
        return setText(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeSequenceSet(this.transfers);
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeUint16(this.code.getValue());
        }
        if ((packing_flags & 1024) != 0)
        {
            enc.writeStr8(this.text);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.transfers = dec.readSequenceSet();
        }
        if ((packing_flags & 512) != 0)
        {
            this.code = MessageRejectCode.get(dec.readUint16());
        }
        if ((packing_flags & 1024) != 0)
        {
            this.text = dec.readStr8();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("transfers", getTransfers());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("code", getCode());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("text", getText());
        }


        return result;
    }


}
