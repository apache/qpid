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
package org.apache.qpidity.transport.codec;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * BBEncoder
 *
 * @author Rafael H. Schloming
 */

public final class BBEncoder extends AbstractEncoder
{

    private ByteBuffer out;

    public BBEncoder(int capacity) {
        out = ByteBuffer.allocate(capacity);
        out.order(ByteOrder.BIG_ENDIAN);
    }

    public void init()
    {
        out.clear();
    }

    public ByteBuffer done()
    {
        out.flip();
        ByteBuffer encoded = ByteBuffer.allocate(out.remaining());
        encoded.put(out);
        encoded.flip();
        return encoded;
    }

    private void grow(int size)
    {
        ByteBuffer old = out;
        int capacity = old.capacity();
        out = ByteBuffer.allocate(Math.max(capacity + size, 2*capacity));
        out.order(ByteOrder.BIG_ENDIAN);
        out.put(old);
    }

    protected void doPut(byte b)
    {
        try
        {
            out.put(b);
        }
        catch (BufferOverflowException e)
        {
            grow(1);
            out.put(b);
        }
    }

    protected void doPut(ByteBuffer src)
    {
        try
        {
            out.put(src);
        }
        catch (BufferOverflowException e)
        {
            grow(src.remaining());
            out.put(src);
        }
    }

    protected void put(byte[] bytes)
    {
        try
        {
            out.put(bytes);
        }
        catch (BufferOverflowException e)
        {
            grow(bytes.length);
            out.put(bytes);
        }
    }

    public void writeUint8(short b)
    {
        assert b < 0x100;

        try
        {
            out.put((byte) b);
        }
        catch (BufferOverflowException e)
        {
            grow(1);
            out.put((byte) b);
        }
    }

    public void writeUint16(int s)
    {
        assert s < 0x10000;

        try
        {
            out.putShort((short) s);
        }
        catch (BufferOverflowException e)
        {
            grow(2);
            out.putShort((short) s);
        }
    }

    public void writeUint32(long i)
    {
        assert i < 0x100000000L;

        try
        {
            out.putInt((int) i);
        }
        catch (BufferOverflowException e)
        {
            grow(4);
            out.putInt((int) i);
        }
    }

    public void writeUint64(long l)
    {
        try
        {
            out.putLong(l);
        }
        catch (BufferOverflowException e)
        {
            grow(8);
            out.putLong(l);
        }
    }

    public int beginSize8()
    {
        int pos = out.position();
        try
        {
            out.put((byte) 0);
        }
        catch (BufferOverflowException e)
        {
            grow(1);
            out.put((byte) 0);
        }
        return pos;
    }

    public void endSize8(int pos)
    {
        int cur = out.position();
        out.put(pos, (byte) (cur - pos - 1));
    }

    public int beginSize16()
    {
        int pos = out.position();
        try
        {
            out.putShort((short) 0);
        }
        catch (BufferOverflowException e)
        {
            grow(2);
            out.putShort((short) 0);
        }
        return pos;
    }

    public void endSize16(int pos)
    {
        int cur = out.position();
        out.putShort(pos, (short) (cur - pos - 2));
    }

    public int beginSize32()
    {
        int pos = out.position();
        try
        {
            out.putInt(0);
        }
        catch (BufferOverflowException e)
        {
            grow(4);
            out.putInt(0);
        }
        return pos;
    }

    public void endSize32(int pos)
    {
        int cur = out.position();
        out.putInt(pos, (cur - pos - 4));
    }

}
