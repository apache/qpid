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
package org.apache.qpidity;

import java.util.Map;
import java.util.UUID;

import static org.apache.qpidity.Functions.*;


/**
 * AbstractDecoder
 *
 * @author Rafael H. Schloming
 */

abstract class AbstractDecoder implements Decoder
{

    protected abstract byte get();

    protected abstract void get(byte[] bytes);

    private byte bits = 0x0;
    private byte nbits = 0;

    public boolean readBit()
    {
        if (nbits == 0)
        {
            bits = get();
            nbits = 8;
        }

        nbits -= 1;

        boolean result = ((bits >>> nbits) & 0x01) != 0;
        return result;
    }

    private void clearBits()
    {
        bits = 0x0;
        nbits = 0;
    }

    public short readOctet()
    {
        clearBits();
        return unsigned(get());
    }

    public int readShort()
    {
        clearBits();
        int i = get() << 8;
        i |= get();
        return i;
    }

    public long readLong()
    {
        clearBits();
        long l = get() << 24;
        l |= get() << 16;
        l |= get() << 8;
        l |= get();
        return l;
    }

    public long readLonglong()
    {
        clearBits();
        long l = get() << 56;
        l |= get() << 48;
        l |= get() << 40;
        l |= get() << 32;
        l |= get() << 24;
        l |= get() << 16;
        l |= get() << 8;
        l |= get();
        return l;
    }

    public long readTimestamp()
    {
        return readLonglong();
    }


    public String readShortstr()
    {
        short size = readOctet();
        byte[] bytes = new byte[size];
        get(bytes);
        return new String(bytes);
    }

    public String readLongstr()
    {
        long size = readLong();
        assert size <= Integer.MAX_VALUE;
        byte[] bytes = new byte[(int) size];
        get(bytes);
        return new String(bytes);
    }

    public Map<String,?> readTable()
    {
        //throw new Error("TODO");
        return null;
    }

    public Range<Long>[] readRfc1982LongSet()
    {
        throw new Error("TODO");
    }

    public UUID readUuid()
    {
        long msb = readLong();
        long lsb = readLong();
        return new UUID(msb, lsb);
    }

    public String readContent()
    {
        throw new Error("Deprecated");
    }

}
