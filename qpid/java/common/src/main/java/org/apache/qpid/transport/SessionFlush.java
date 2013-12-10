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


public final class SessionFlush extends Method {

    public static final int TYPE = 524;

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


    public SessionFlush() {}


    public SessionFlush(Option ... _options) {

        for (int i=0; i < _options.length; i++) {
            switch (_options[i]) {
            case EXPECTED: packing_flags |= 256; break;
            case CONFIRMED: packing_flags |= 512; break;
            case COMPLETED: packing_flags |= 1024; break;
            case SYNC: this.setSync(true); break;
            case BATCH: this.setBatch(true); break;
            case UNRELIABLE: this.setUnreliable(true); break;
            case NONE: break;
            default: throw new IllegalArgumentException("invalid option: " + _options[i]);
            }
        }

    }

    public <C> void dispatch(C context, MethodDelegate<C> delegate) {
        delegate.sessionFlush(context, this);
    }


    public final boolean hasExpected() {
        return (packing_flags & 256) != 0;
    }

    public final SessionFlush clearExpected() {
        packing_flags &= ~256;

        setDirty(true);
        return this;
    }

    public final boolean getExpected() {
        return hasExpected();
    }

    public final SessionFlush setExpected(boolean value) {

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

    public final SessionFlush expected(boolean value) {
        return setExpected(value);
    }

    public final boolean hasConfirmed() {
        return (packing_flags & 512) != 0;
    }

    public final SessionFlush clearConfirmed() {
        packing_flags &= ~512;

        setDirty(true);
        return this;
    }

    public final boolean getConfirmed() {
        return hasConfirmed();
    }

    public final SessionFlush setConfirmed(boolean value) {

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

    public final SessionFlush confirmed(boolean value) {
        return setConfirmed(value);
    }

    public final boolean hasCompleted() {
        return (packing_flags & 1024) != 0;
    }

    public final SessionFlush clearCompleted() {
        packing_flags &= ~1024;

        setDirty(true);
        return this;
    }

    public final boolean getCompleted() {
        return hasCompleted();
    }

    public final SessionFlush setCompleted(boolean value) {

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

    public final SessionFlush completed(boolean value) {
        return setCompleted(value);
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
            result.put("expected", getExpected());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("confirmed", getConfirmed());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("completed", getCompleted());
        }


        return result;
    }


}
