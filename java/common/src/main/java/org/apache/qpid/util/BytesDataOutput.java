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
package org.apache.qpid.util;

import java.io.DataOutput;

public class BytesDataOutput implements DataOutput
{
    private int _pos = 0;
    private byte[] _buf;

    public BytesDataOutput(byte[] buf)
    {
        _buf = buf;
    }

    public void setBuffer(byte[] buf)
    {
        _buf = buf;
        _pos = 0;
    }

    public void reset()
    {
        _pos = 0;
    }

    public int length()
    {
        return _pos;
    }

    public void write(int b)
    {
        _buf[_pos++] = (byte) b;
    }

    public void write(byte[] b)
    {
        System.arraycopy(b, 0, _buf, _pos, b.length);
        _pos+=b.length;
    }


    public void write(byte[] b, int off, int len)
    {
        System.arraycopy(b, off, _buf, _pos, len);
        _pos+=len;

    }

    public void writeBoolean(boolean v)
    {
        _buf[_pos++] = v ? (byte) 1 : (byte) 0;
    }

    public void writeByte(int v)
    {
        _buf[_pos++] = (byte) v;
    }

    public void writeShort(int v)
    {
        _buf[_pos++] = (byte) (v >>> 8);
        _buf[_pos++] = (byte) v;
    }

    public void writeChar(int v)
    {
        _buf[_pos++] = (byte) (v >>> 8);
        _buf[_pos++] = (byte) v;
    }

    public void writeInt(int v)
    {
        _buf[_pos++] = (byte) (v >>> 24);
        _buf[_pos++] = (byte) (v >>> 16);
        _buf[_pos++] = (byte) (v >>> 8);
        _buf[_pos++] = (byte) v;
    }

    public void writeLong(long v)
    {
        _buf[_pos++] = (byte) (v >>> 56);
        _buf[_pos++] = (byte) (v >>> 48);
        _buf[_pos++] = (byte) (v >>> 40);
        _buf[_pos++] = (byte) (v >>> 32);
        _buf[_pos++] = (byte) (v >>> 24);
        _buf[_pos++] = (byte) (v >>> 16);
        _buf[_pos++] = (byte) (v >>> 8);
        _buf[_pos++] = (byte)v;
    }

    public void writeFloat(float v)
    {
        writeInt(Float.floatToIntBits(v));
    }

    public void writeDouble(double v)
    {
        writeLong(Double.doubleToLongBits(v));
    }

    public void writeBytes(String s)
    {
        int len = s.length();
        for (int i = 0 ; i < len ; i++)
        {
            _buf[_pos++] = ((byte)s.charAt(i));
        }
    }

    public void writeChars(String s)
    {
        int len = s.length();
        for (int i = 0 ; i < len ; i++)
        {
            int v = s.charAt(i);
            _buf[_pos++] = (byte) (v >>> 8);
            _buf[_pos++] = (byte) v;
        }
    }

    public void writeUTF(String s)
    {
        int strlen = s.length();

        int pos = _pos;
        _pos+=2;


        for (int i = 0; i < strlen; i++)
        {
            int c = s.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F))
            {
                c = s.charAt(i);
                _buf[_pos++] = (byte) c;

            }
            else if (c > 0x07FF)
            {
                _buf[_pos++]  = (byte) (0xE0 | ((c >> 12) & 0x0F));
                _buf[_pos++]  = (byte) (0x80 | ((c >>  6) & 0x3F));
                _buf[_pos++]  = (byte) (0x80 | (c & 0x3F));
            }
            else
            {
                _buf[_pos++] = (byte) (0xC0 | ((c >>  6) & 0x1F));
                _buf[_pos++]  = (byte) (0x80 | (c & 0x3F));
            }
        }

        int len = _pos - (pos + 2);

        _buf[pos++] = (byte) (len >>> 8);
        _buf[pos] = (byte) len;
    }

}
