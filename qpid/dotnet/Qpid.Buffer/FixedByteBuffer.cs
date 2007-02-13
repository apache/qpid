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
using System;

namespace Qpid.Buffer
{
    public class FixedByteBuffer
    {
        private HeapByteBuffer _buf;

        public FixedByteBuffer(int capacity)
        {
            _buf = new HeapByteBuffer(capacity);
        }

        public FixedByteBuffer(byte[] bytes)
        {
            _buf = HeapByteBuffer.wrap(bytes);
        }

        public static FixedByteBuffer wrap(byte[] array)
        {
            return new FixedByteBuffer(array);
        }

        public static FixedByteBuffer wrap(byte[] array, int offset, int length)
        {
            throw new NotImplementedException();            
        }

        public ByteOrder order()
        {
            return ByteOrder.LittleEndian;
        }

        public void order(ByteOrder bo)
        {
            // Ignore endianess.
        }

        public void compact()
        {
            _buf.Compact();
        }

        public char getChar()
        {
            throw new NotImplementedException();
        }

        public char getChar(int index)
        {
            throw new NotImplementedException();
        }

        public void putChar(char value)
        {
            throw new NotImplementedException();
        }

        public void putChar(int index, char value)
        {
            throw new NotImplementedException();
        }

        public bool isDirect()
        {
            return false;
        }

        public bool isReadOnly()
        {
            throw new NotImplementedException();
        }

        public int capacity()
        {
            return _buf.Capacity;
        }

        public int limit()
        {
            return _buf.Limit;
        }

        public int limit(int limit)
        {
            int previousLimit = _buf.Limit;
            _buf.Limit = limit;
            return previousLimit;
        }

        public int position()
        {
            return _buf.Position;
        }

        public int position(int newPosition)
        {
            int prev = _buf.Position;
            _buf.Position = newPosition;
            return prev;
        }

        public void mark()
        {
            throw new NotImplementedException();
        }

        public static FixedByteBuffer allocateDirect(int capacity)
        {
            throw new NotImplementedException();
        }

        public static FixedByteBuffer allocate(int capacity)
        {
            return new FixedByteBuffer(capacity);
        }

        public void clear()
        {
            _buf.Clear();
        }

        public void put(byte b)
        {
            _buf.Put(b);
        }

        public void put(int index, byte b)
        {
            throw new NotImplementedException();
        }

        public void put(FixedByteBuffer buf)
        {
            _buf.Put(buf.array(), buf.position(), buf.limit() - buf.position());
        }

        public FixedByteBuffer duplicate()
        {
            throw new NotImplementedException();
        }

        public FixedByteBuffer slice()
        {
            throw new NotImplementedException();
        }

        public FixedByteBuffer asReadOnlyBuffer()
        {
            throw new NotImplementedException();
        }

        /// <summary>
        /// Returns backing array.
        /// </summary>
        /// <returns></returns>
        public byte[] array()
        {
            return _buf.array();
        }

        public int arrayOffset()
        {
            throw new NotImplementedException();
        }

        public void reset()
        {
            throw new NotImplementedException();
        }

        public void flip()
        {
            _buf.Flip();
        }

        public void rewind()
        {
            _buf.Rewind();
        }

        public byte get()
        {
            return _buf.Get();
        }

        public byte get(int index)
        {
            throw new NotImplementedException();
        }

        public short getShort()
        {
           return _buf.GetShort();
        }

        public short getShort(int index)
        {
            throw new NotImplementedException();
        }

        public void putShort(short value)
        {
            _buf.Put(value);
        }

        public void putShort(int index, short value)
        {
            throw new NotImplementedException();
        }

        public int getInt()
        {
            return _buf.GetInt();
        }

        public int getInt(int index)
        {
            throw new NotImplementedException();
        }

        public void putInt(int value)
        {
            _buf.Put(value);
        }

        public void putInt(int index, int value)
        {
            throw new NotImplementedException();
        }

        public ByteBuffer get(byte[] dst, int offset, int length)
        {
            throw new NotImplementedException();             
        }

        public ByteBuffer put(byte[] src, int offset, int length)
        {
            throw new NotImplementedException();
        }

        public long getLong()
        {
            return _buf.GetLong();
        }

        public long getLong(int index)
        {
            throw new NotImplementedException();
        }

        public void putLong(long value)
        {
            _buf.Put(value);
        }
    
        public void putLong(int index, long value)
        {
            throw new NotImplementedException();
        }

        public int remaining()
        {
            return _buf.Remaining;
        }

        public float getFloat()
        {
           return _buf.GetFloat();
        }

        public float getFloat(int index)
        {
           throw new NotImplementedException();
        }

        public void putFloat(float value)
        {
            _buf.Put(value);
        }

        public void putFloat(int index, float value)
        {
            throw new NotImplementedException();
        }

        public double getDouble()
        {
            return _buf.GetDouble();
        }

        public double getDouble(int index)
        {
            throw new NotImplementedException();
        }

        public void putDouble(double value)
        {
            _buf.Put(value);
        }

        public void putDouble(int index, double value)
        {
            throw new NotImplementedException();
        }

        public ushort getUnsignedShort()
        {
            return _buf.GetUnsignedShort();
        }

        public uint getUnsignedInt()
        {
            return _buf.GetUnsignedInt();
        }

        public void get(byte[] dst)
        {
            _buf.Get(dst);
        }

        public void put(ushort value)
        {
            _buf.Put(value);
        }

        public void put(uint max)
        {
            _buf.Put(max);
        }

        public void put(ulong tag)
        {
            _buf.Put(tag);
        }

        public void put(byte[] src)
        {
            _buf.Put(src);
        }

        public ulong getUnsignedLong()
        {
            return _buf.GetUnsignedLong();
        }
    }
}
