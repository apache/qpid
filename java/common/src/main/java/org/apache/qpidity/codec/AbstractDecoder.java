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
package org.apache.qpidity.codec;

import java.util.Map;
import java.util.UUID;

import org.apache.qpidity.RangeSet;
import org.apache.qpidity.Struct;

import static org.apache.qpidity.Functions.*;


/**
 * AbstractDecoder
 *
 * @author Rafael H. Schloming
 */

abstract class AbstractDecoder implements Decoder
{

    private final byte major;
    private final byte minor;

    protected AbstractDecoder(byte major, byte minor)
    {
        this.major = major;
        this.minor = minor;
    }

    protected abstract byte get();

    protected abstract void get(byte[] bytes);

    protected short uget()
    {
        return unsigned(get());
    }

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
        return uget();
    }

    public int readShort()
    {
        clearBits();
        int i = uget() << 8;
        i |= uget();
        return i;
    }

    public long readLong()
    {
        clearBits();
        long l = uget() << 24;
        l |= uget() << 16;
        l |= uget() << 8;
        l |= uget();
        return l;
    }

    public long readLonglong()
    {
        clearBits();
        long l = uget() << 56;
        l |= uget() << 48;
        l |= uget() << 40;
        l |= uget() << 32;
        l |= uget() << 24;
        l |= uget() << 16;
        l |= uget() << 8;
        l |= uget();
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

    public RangeSet readRfc1982LongSet()
    {
        int count = readShort()/8;
        if (count == 0)
        {
            return null;
        }
        else
        {
            RangeSet ranges = new RangeSet();
            for (int i = 0; i < count; i++)
            {
                ranges.add(readLong(), readLong());
            }
            return ranges;
        }
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

    public Struct readLongStruct()
    {
        long size = readLong();
        if (size == 0)
        {
            return null;
        }
        else
        {
            int type = readShort();
            Struct result = Struct.create(type);
            result.read(this, major, minor);
            return result;
        }
    }

}
