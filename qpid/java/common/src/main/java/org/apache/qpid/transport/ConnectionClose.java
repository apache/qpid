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


public final class ConnectionClose extends Method {

    public static final int TYPE = 267;

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
    private ConnectionCloseCode replyCode;
    private String replyText;


    public ConnectionClose() {}


    public ConnectionClose(ConnectionCloseCode replyCode, String replyText, Option ... _options) {
        if(replyCode != null) {
            setReplyCode(replyCode);
        }
        if(replyText != null) {
            setReplyText(replyText);
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
        delegate.connectionClose(context, this);
    }


    public final boolean hasReplyCode() {
        return (packing_flags & 256) != 0;
    }

    public final ConnectionClose clearReplyCode() {
        packing_flags &= ~256;
        this.replyCode = null;
        setDirty(true);
        return this;
    }

    public final ConnectionCloseCode getReplyCode() {
        return replyCode;
    }

    public final ConnectionClose setReplyCode(ConnectionCloseCode value) {
        this.replyCode = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final ConnectionClose replyCode(ConnectionCloseCode value) {
        return setReplyCode(value);
    }

    public final boolean hasReplyText() {
        return (packing_flags & 512) != 0;
    }

    public final ConnectionClose clearReplyText() {
        packing_flags &= ~512;
        this.replyText = null;
        setDirty(true);
        return this;
    }

    public final String getReplyText() {
        return replyText;
    }

    public final ConnectionClose setReplyText(String value) {
        this.replyText = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final ConnectionClose replyText(String value) {
        return setReplyText(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeUint16(this.replyCode.getValue());
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeStr8(this.replyText);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.replyCode = ConnectionCloseCode.get(dec.readUint16());
        }
        if ((packing_flags & 512) != 0)
        {
            this.replyText = dec.readStr8();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("replyCode", getReplyCode());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("replyText", getReplyText());
        }


        return result;
    }


}
