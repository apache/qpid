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
package org.apache.qpid.framing;

import org.apache.qpid.codec.MarkableDataInput;

public class ByteArrayDataInput implements ExtendedDataInput, MarkableDataInput
{
    private byte[] _data;
    private int _offset;
    private int _length;
    private int _origin;
    private int _mark;

    public ByteArrayDataInput(byte[] data)
    {
        this(data,0, data.length);
    }

    public ByteArrayDataInput(byte[] data, int offset, int length)
    {
        _data = data;
        _offset = offset;
        _length = length;
        _origin = offset;
        _mark = 0;
    }

    public void readFully(byte[] b)
    {
        System.arraycopy(_data,_offset,b,0,b.length);
        _offset+=b.length;
    }

    public void readFully(byte[] b, int off, int len)
    {
        System.arraycopy(_data,_offset,b,off,len);
        _offset+=len;
    }

    public int skipBytes(int n)
    {
        return _offset+=n;
    }

    public boolean readBoolean()
    {
        return _data[_offset++] != 0;
    }

    public byte readByte()
    {
        return _data[_offset++];
    }

    public int readUnsignedByte()
    {
        return ((int)_data[_offset++]) & 0xFF;
    }

    public short readShort()
    {
        return (short) (((((int)_data[_offset++]) << 8) & 0xFF00) | (((int)_data[_offset++]) & 0xFF));
    }

    public int readUnsignedShort()
    {
        return (((((int)_data[_offset++]) << 8) & 0xFF00) | (((int)_data[_offset++]) & 0xFF));
    }

    public char readChar()
    {
        return (char) (((((int)_data[_offset++]) << 8) & 0xFF00) | (((int)_data[_offset++]) & 0xFF));
    }

    public int readInt()
    {
        return  ((((int)_data[_offset++]) << 24) & 0xFF000000)
                | ((((int)_data[_offset++]) << 16) & 0xFF0000)
                | ((((int)_data[_offset++]) << 8) & 0xFF00)
                | (((int)_data[_offset++]) & 0xFF);
    }

    public long readLong()
    {
        return    ((((long)_data[_offset++]) << 56) & 0xFF00000000000000L)
                | ((((long)_data[_offset++]) << 48) & 0xFF000000000000L)
                | ((((long)_data[_offset++]) << 40) & 0xFF0000000000L)
                | ((((long)_data[_offset++]) << 32) & 0xFF00000000L)
                | ((((long)_data[_offset++]) << 24) & 0xFF000000L)
                | ((((long)_data[_offset++]) << 16) & 0xFF0000L)
                | ((((long)_data[_offset++]) << 8)  & 0xFF00L)
                | (((long)_data[_offset++]) & 0xFFL);
    }

    public float readFloat()
    {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble()
    {
        return Double.longBitsToDouble(readLong());
    }

    public AMQShortString readAMQShortString()
    {
        int length = _data[_offset++] & 0xff;
        if(length == 0) 
        {
            return null;
        }
        else
        {
            final AMQShortString amqShortString = new AMQShortString(_data, _offset, length);
            _offset+=length;
            return amqShortString;
        }
    }

    public String readLine()
    {
        throw new UnsupportedOperationException();
    }

    public String readUTF()
    {
        throw new UnsupportedOperationException();
    }

    public int available()
    {
        return (_origin+_length)-_offset;
    }


    public long skip(long i)
    {
        _offset+=i;
        return i;
    }

    public int read(byte[] b)
    {
        readFully(b);
        return b.length;
    }

    public int position()
    {
        return _offset - _origin;
    }

    public void position(int position)
    {
        _offset = position + _origin;
    }

    public int length()
    {
        return _length;
    }


    public void mark(int readAhead)
    {
        _mark = _offset-_origin;
    }

    public void reset()
    {
        _offset = _origin + _mark;
    }
}
