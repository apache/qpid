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



public final class FragmentProperties extends Struct {

    public static final int TYPE = 1026;

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
    private long fragmentSize;


    public FragmentProperties() {}


    public FragmentProperties(long fragmentSize, Option ... _options) {
        setFragmentSize(fragmentSize);

        for (int i=0; i < _options.length; i++) {
            switch (_options[i]) {
            case FIRST: packing_flags |= 256; break;
            case LAST: packing_flags |= 512; break;
            case NONE: break;
            default: throw new IllegalArgumentException("invalid option: " + _options[i]);
            }
        }

    }




    public final boolean hasFirst() {
        return (packing_flags & 256) != 0;
    }

    public final FragmentProperties clearFirst() {
        packing_flags &= ~256;

        setDirty(true);
        return this;
    }

    public final boolean getFirst() {
        return hasFirst();
    }

    public final FragmentProperties setFirst(boolean value) {

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

    public final FragmentProperties first(boolean value) {
        return setFirst(value);
    }

    public final boolean hasLast() {
        return (packing_flags & 512) != 0;
    }

    public final FragmentProperties clearLast() {
        packing_flags &= ~512;

        setDirty(true);
        return this;
    }

    public final boolean getLast() {
        return hasLast();
    }

    public final FragmentProperties setLast(boolean value) {

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

    public final FragmentProperties last(boolean value) {
        return setLast(value);
    }

    public final boolean hasFragmentSize() {
        return (packing_flags & 1024) != 0;
    }

    public final FragmentProperties clearFragmentSize() {
        packing_flags &= ~1024;
        this.fragmentSize = 0;
        setDirty(true);
        return this;
    }

    public final long getFragmentSize() {
        return fragmentSize;
    }

    public final FragmentProperties setFragmentSize(long value) {
        this.fragmentSize = value;
        packing_flags |= 1024;
        setDirty(true);
        return this;
    }

    public final FragmentProperties fragmentSize(long value) {
        return setFragmentSize(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 1024) != 0)
        {
            enc.writeUint64(this.fragmentSize);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 1024) != 0)
        {
            this.fragmentSize = dec.readUint64();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("first", getFirst());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("last", getLast());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("fragmentSize", getFragmentSize());
        }


        return result;
    }


}
