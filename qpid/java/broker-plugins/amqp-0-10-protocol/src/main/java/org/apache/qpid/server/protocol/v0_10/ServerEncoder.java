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
package org.apache.qpid.server.protocol.v0_10;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.UUID;

import org.apache.qpid.transport.codec.AbstractEncoder;


public final class ServerEncoder extends AbstractEncoder
{
    public static final int DEFAULT_CAPACITY = 8192;
    private final int _threshold;
    private ByteBuffer _out;
    private int _segment;
    private int _initialCapacity;

    public ServerEncoder()
    {
        this(DEFAULT_CAPACITY);
    }

    public ServerEncoder(int capacity)
    {
        _initialCapacity = capacity;
        _threshold = capacity/16;
        _out = ByteBuffer.allocate(capacity);
        _segment = 0;
    }

    public void init()
    {
        _out.position(_out.limit());
        _out.limit(_out.capacity());
        _out = _out.slice();
        if(_out.remaining() < _threshold)
        {
            _out = ByteBuffer.allocate(_initialCapacity);
        }
        _segment = 0;
    }

    public ByteBuffer buffer()
    {
        int pos = _out.position();
        _out.position(_segment);
        ByteBuffer slice = _out.slice();
        slice.limit(pos - _segment);
        _out.position(pos);
        return slice;
    }

    public int position()
    {
        return _out.position();
    }

    public ByteBuffer underlyingBuffer()
    {
        return _out;
    }

    private void grow(int size)
    {
        ByteBuffer old = _out;
        int capacity = old.capacity();
        _out = ByteBuffer.allocate(Math.max(Math.max(capacity + size, 2*capacity), _initialCapacity));
        old.flip();
        _out.put(old);
    }

    protected void doPut(byte b)
    {
        try
        {
            _out.put(b);
        }
        catch (BufferOverflowException e)
        {
            grow(1);
            _out.put(b);
        }
    }

    protected void doPut(ByteBuffer src)
    {
        try
        {
            _out.put(src);
        }
        catch (BufferOverflowException e)
        {
            grow(src.remaining());
            _out.put(src);
        }
    }

    protected void put(byte[] bytes)
    {
        try
        {
            _out.put(bytes);
        }
        catch (BufferOverflowException e)
        {
            grow(bytes.length);
            _out.put(bytes);
        }
    }

    public void writeUint8(short b)
    {
        assert b < 0x100;

        try
        {
            _out.put((byte) b);
        }
        catch (BufferOverflowException e)
        {
            grow(1);
            _out.put((byte) b);
        }
    }

    public void writeUint16(int s)
    {
        assert s < 0x10000;

        try
        {
            _out.putShort((short) s);
        }
        catch (BufferOverflowException e)
        {
            grow(2);
            _out.putShort((short) s);
        }
    }

    public void writeUint32(long i)
    {
        assert i < 0x100000000L;

        try
        {
            _out.putInt((int) i);
        }
        catch (BufferOverflowException e)
        {
            grow(4);
            _out.putInt((int) i);
        }
    }

    public void writeUint64(long l)
    {
        try
        {
            _out.putLong(l);
        }
        catch (BufferOverflowException e)
        {
            grow(8);
            _out.putLong(l);
        }
    }

    public int beginSize8()
    {
        int pos = _out.position();
        try
        {
            _out.put((byte) 0);
        }
        catch (BufferOverflowException e)
        {
            grow(1);
            _out.put((byte) 0);
        }
        return pos;
    }

    public void endSize8(int pos)
    {
        int cur = _out.position();
        _out.put(pos, (byte) (cur - pos - 1));
    }

    public int beginSize16()
    {
        int pos = _out.position();
        try
        {
            _out.putShort((short) 0);
        }
        catch (BufferOverflowException e)
        {
            grow(2);
            _out.putShort((short) 0);
        }
        return pos;
    }

    public void endSize16(int pos)
    {
        int cur = _out.position();
        _out.putShort(pos, (short) (cur - pos - 2));
    }

    public int beginSize32()
    {
        int pos = _out.position();
        try
        {
            _out.putInt(0);
        }
        catch (BufferOverflowException e)
        {
            grow(4);
            _out.putInt(0);
        }
        return pos;

    }

    public void endSize32(int pos)
    {
        int cur = _out.position();
        _out.putInt(pos, (cur - pos - 4));

    }

	public void writeDouble(double aDouble)
	{
		try
		{
			_out.putDouble(aDouble);
		}
        catch(BufferOverflowException exception)
		{
			grow(8);
			_out.putDouble(aDouble);
		}
	}

	public void writeInt16(short aShort)
	{
		try 
		{
			_out.putShort(aShort);
		}
        catch(BufferOverflowException exception)
		{
			grow(2);
			_out.putShort(aShort);
		}
	}

	public void writeInt32(int anInt)
	{
		try
		{
			_out.putInt(anInt);
		}
        catch(BufferOverflowException exception)
		{
			grow(4);
			_out.putInt(anInt);
		}
	}

	public void writeInt64(long aLong)
	{
		try
		{
			_out.putLong(aLong);
		}
        catch(BufferOverflowException exception)
		{
			grow(8);
			_out.putLong(aLong);
		}
	}
      
	public void writeInt8(byte aByte)
	{
		try 
		{
			_out.put(aByte);
		}
        catch(BufferOverflowException exception)
		{
			grow(1);
			_out.put(aByte);
		}
	}	
	
	public void writeBin128(byte[] byteArray)
	{
		byteArray = (byteArray != null) ? byteArray : new byte [16];
		
		assert byteArray.length == 16;
		
		try 
		{
			_out.put(byteArray);
		}
        catch(BufferOverflowException exception)
		{
			grow(16);
			_out.put(byteArray);
		}
	}

    public void writeBin128(UUID id)
	{
        byte[] data = new byte[16];

        long msb = id.getMostSignificantBits();
        long lsb = id.getLeastSignificantBits();

        assert data.length == 16;
        for (int i=7; i>=0; i--)
        {
            data[i] = (byte)(msb & 0xff);
            msb = msb >> 8;
        }

        for (int i=15; i>=8; i--)
        {
            data[i] = (byte)(lsb & 0xff);
            lsb = (lsb >> 8);
        }
        writeBin128(data);
    }

	public void writeFloat(float aFloat)
	{
		try 
		{
			_out.putFloat(aFloat);
		}
        catch(BufferOverflowException exception)
		{
			grow(4);
			_out.putFloat(aFloat);
		}
	}

}
