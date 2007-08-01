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

import java.nio.ByteBuffer;

import java.util.Map;
import java.util.UUID;

import static org.apache.qpidity.Functions.*;


/**
 * AbstractEncoder
 *
 * @author Rafael H. Schloming
 */

abstract class AbstractEncoder implements Encoder
{

    protected abstract void put(byte b);

    protected abstract void put(ByteBuffer src);

    private byte bits = 0x0;
    private byte nbits = 0;

    public void writeBit(boolean b)
    {
        if (b)
        {
            bits |= 1 << nbits;
        }

        nbits += 1;

        if (nbits == 8)
        {
            flushBits();
        }
    }

    private void flushBits()
    {
        if (nbits > 0)
        {
            put(bits);
            bits = 0x0;
            nbits = 0;
        }
    }

    public void writeOctet(short b)
    {
        assert b < 0x100;

        flushBits();
        put((byte) b);
    }

    public void writeShort(int s)
    {
        assert s < 0x10000;

        flushBits();
        put(lsb(s >> 8));
        put(lsb(s));
    }

    public void writeLong(long i)
    {
        assert i < 0x100000000L;

        flushBits();
        put(lsb(i >> 24));
        put(lsb(i >> 16));
        put(lsb(i >> 8));
        put(lsb(i));
    }

    public void writeLonglong(long l)
    {
        flushBits();
        put(lsb(l >> 56));
        put(lsb(l >> 48));
        put(lsb(l >> 40));
        put(lsb(l >> 32));
        put(lsb(l >> 24));
        put(lsb(l >> 16));
        put(lsb(l >> 8));
        put(lsb(l));
    }


    public void writeTimestamp(long l)
    {
        flushBits();
        writeLonglong(l);
    }


    public void writeShortstr(String s)
    {
        if (s.length() > 255) {
            throw new IllegalArgumentException(s);
        }
        writeOctet((short) s.length());
        put(ByteBuffer.wrap(s.getBytes()));
    }

    public void writeLongstr(String s)
    {
        writeLong(s.length());
        put(ByteBuffer.wrap(s.getBytes()));
    }


    public void writeTable(Map<String,?> table)
    {
        //throw new Error("TODO");
    }

    public void writeRfc1982LongSet(Range<Long>[] ranges)
    {
        throw new Error("TODO");
    }

    public void writeUuid(UUID uuid)
    {
        writeLong(uuid.getMostSignificantBits());
        writeLong(uuid.getLeastSignificantBits());
    }

    public void writeContent(String c)
    {
        throw new Error("Deprecated");
    }

    public void flush()
    {
        flushBits();
    }

}
