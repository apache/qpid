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
using System.Text;

namespace Qpid.Buffer
{
    /// <summary>
    /// 
    /// </summary>
    public class HeapByteBuffer //: ByteBuffer
    {
        private byte[] _underlyingData;

        /// <summary> The current position within the buffer where the next value to be read or written will occurr. </summary>
        private int _position;

        /// <summary> The index of the first element that should not be read or written. </summary>
        private int _limit;

        public HeapByteBuffer(int size) : this(new byte[size], 0)
        {
        }        
        
        private HeapByteBuffer(byte[] bytes, int length)
        {
            _underlyingData = bytes;
            _limit = bytes.Length;
            _position = length;
        }

        public /*override*/ int Capacity
        {
            get
            {
                return _underlyingData.Length;
            }
        }

        public /*override*/ int Position
        {
            get
            {
                return _position;
            }
            set
            {
                _position = value;
            }
        }

        /// <summary>
        /// Sets this buffer's limit. If the position is larger than the new limit then it is set to the new limit.
        /// </summary>
        /// <value>The new limit value; must be non-negative and no larger than this buffer's capacity</value>
        public /*override*/ int Limit
        {
            get
            {
                return _limit;
            }
            set
            {
                if (value < 0)
                {
                    throw new ArgumentException("Limit must not be negative");
                }
                if (value > Capacity)
                {
                    throw new ArgumentException("Limit must not be greater than Capacity");
                }
                _limit = value;
                if (_position > value)
                {
                    _position = value;
                }
            }
        }

        /// <summary>
        /// Returns the number of elements between the current position and the limit
        /// </summary>
        /// <value>The number of elements remaining in this buffer</value>
        public /*override*/ int Remaining
        {
            get
            {
                return (_limit - _position);
            }
        }

        public /*override*/ void Clear()
        {
            _position = 0;
            _limit = Capacity;
        }

        public /*override*/ void Flip()
        {
            _limit = _position;
            _position = 0;
        }

        public /*override*/ void Rewind()
        {
            _position = 0;
        }

        public byte[] array()
        {
            return _underlyingData;
        }

        public /*override*/ byte[] ToByteArray()
        {
            // Return copy of bytes remaining.
            byte[] result = new byte[Remaining];
            Array.Copy(_underlyingData, _position, result, 0, Remaining);
            return result;
        }

        private void CheckSpace(int size)
        {
            if (_position + size > _limit)
            {
                throw new BufferOverflowException("Attempt to write " + size + " byte(s) to buffer where position is " + _position +
                                                  " and limit is " + _limit);
            }
        }

        private void CheckSpaceForReading(int size)
        {
            if (_position + size > _limit)
            {
                throw new BufferUnderflowException("Attempt to read " + size + " byte(s) to buffer where position is " + _position +
                                                   " and limit is " + _limit);
            }
        }

        /// <summary>
        /// Writes the given byte into this buffer at the current position, and then increments the position.
        /// </summary>
        /// <param name="data">The byte to be written</param>
        /// <exception cref="BufferOverflowException">If this buffer's current position is not smaller than its limit</exception>
        public /*override*/ void Put(byte data)
        {
            CheckSpace(1);
            _underlyingData[_position++] = data;
        }

        /// <summary>
        /// Writes all the data in the given byte array into this buffer at the current
        /// position and then increments the position.
        /// </summary>
        /// <param name="data">The data to copy.</param>
        /// <exception cref="BufferOverflowException">If this buffer's current position plus the array length is not smaller than its limit</exception>
        public /*override*/ void Put(byte[] data)
        {
            Put(data, 0, data.Length);
        }

        public /*override*/ void Put(byte[] data, int offset, int size)
        {
            if (data == null)
            {
                throw new ArgumentNullException("data");
            }
            CheckSpace(size);
            Array.Copy(data, offset, _underlyingData, _position, size);
            _position += size;
        }

        /// <summary>
        /// Writes the given ushort into this buffer at the current position, and then increments the position.
        /// </summary>
        /// <param name="data">The ushort to be written</param>
        public /*override*/ void Put(ushort data)
        {
            CheckSpace(2);
            _underlyingData[_position++] = (byte) (data >> 8);
            _underlyingData[_position++] = (byte) data;
        }

        public /*override*/ void Put(uint data)
        {
            CheckSpace(4);
            _underlyingData[_position++] = (byte) (data >> 24);
            _underlyingData[_position++] = (byte) (data >> 16);
            _underlyingData[_position++] = (byte) (data >> 8);
            _underlyingData[_position++] = (byte) data;
        }

        public /*override*/ void Put(ulong data)
        {
            CheckSpace(8);
            _underlyingData[_position++] = (byte) (data >> 56);
            _underlyingData[_position++] = (byte) (data >> 48);
            _underlyingData[_position++] = (byte) (data >> 40);
            _underlyingData[_position++] = (byte) (data >> 32);
            _underlyingData[_position++] = (byte) (data >> 24);
            _underlyingData[_position++] = (byte) (data >> 16);
            _underlyingData[_position++] = (byte) (data >> 8);
            _underlyingData[_position++] = (byte) data;
        }

        public void Put(short data)
        {
           Put((ushort)data);
        }

        public void Put(int data)
        {
           Put((uint)data);
        }

        public void Put(long data)
        {
           Put((ulong)data);
        }

        public void Put(float data)
        {
           unsafe
           {
              uint val = *((uint*)&data);
              Put(val);
           }
        }

       public void Put(double data)
       {
          unsafe
          {
             ulong val = *((ulong*)&data);
             Put(val);
          }
       }

        
        /// <summary>
        /// Read the byte at the current position and increment the position
        /// </summary>
        /// <returns>a byte</returns>
        /// <exception cref="BufferUnderflowException">if there are no bytes left to read</exception>
        public /*override*/ byte Get()
        {
            CheckSpaceForReading(1);
            return _underlyingData[_position++];
        }

        /// <summary>
        /// Reads bytes from the buffer into the supplied array
        /// </summary>
        /// <param name="destination">The destination array. The array must not
        /// be bigger than the remaining space in the buffer, nor can it be null.</param>
        public /*override*/ void Get(byte[] destination)
        {
            if (destination == null)
            {
                throw new ArgumentNullException("destination");
            }
            int len = destination.Length;
            CheckSpaceForReading(len);
            Array.Copy(_underlyingData, _position, destination, 0, len);
            _position += len;
        }

        /// <summary>
        /// Reads and returns an unsigned short (two bytes, big endian) from this buffer
        /// </summary>
        /// <returns>an unsigned short</returns>
        /// <exception cref="BufferUnderflowException">If there are fewer than two bytes remaining in this buffer</exception>
        public /*override*/ ushort GetUnsignedShort()
        {
            CheckSpaceForReading(2);
            byte upper = _underlyingData[_position++];
            byte lower = _underlyingData[_position++];
            return (ushort) ((upper << 8) + lower);
        }

        /// <summary>
        /// Reads and returns an unsigned int (four bytes, big endian) from this buffer
        /// </summary>
        /// <returns>an unsigned integer</returns>
        /// <exception cref="BufferUnderflowException">If there are fewer than four bytes remaining in this buffer</exception>
        public /*override*/ uint GetUnsignedInt()
        {
            CheckSpaceForReading(4);
            byte b1 = _underlyingData[_position++];
            byte b2 = _underlyingData[_position++];
            byte b3 = _underlyingData[_position++];
            byte b4 = _underlyingData[_position++];
            return (uint) ((b1 << 24) + (b2 << 16) + (b3 << 8) + b4);
        }

        public /*override*/ ulong GetUnsignedLong()
        {
            CheckSpaceForReading(8);
            byte b1 = _underlyingData[_position++];
            byte b2 = _underlyingData[_position++];
            byte b3 = _underlyingData[_position++];
            byte b4 = _underlyingData[_position++];
            byte b5 = _underlyingData[_position++];
            byte b6 = _underlyingData[_position++];
            byte b7 = _underlyingData[_position++];
            byte b8 = _underlyingData[_position++];
            // all the casts necessary because otherwise each subexpression
            // only gets promoted to uint and cause incorrect results
            return (((ulong)b1 << 56) + ((ulong)b2 << 48) + ((ulong)b3 << 40) + 
               ((ulong)b4 << 32) + ((ulong)b5 << 24) +
               ((ulong)b6 << 16) + ((ulong)b7 << 8) + b8);
        }

        public short GetShort()
        {
           return (short) GetUnsignedShort();
        }

        public int GetInt()
        {
           return (int) GetUnsignedInt();
        }

        public long GetLong()
        {
           return (long) GetUnsignedLong();
        }

        public float GetFloat()
        {
           unsafe
           {
              uint val = GetUnsignedInt();
              return *((float*)&val);
           }
        }

        public double GetDouble()
        {
           unsafe
           {
              ulong val = GetUnsignedLong();
              return *((double*)&val);
           }
        }

        public /*override*/ string GetString(uint length, Encoding encoder)
        {
            CheckSpaceForReading((int)length);
            string result = encoder.GetString(_underlyingData, _position, (int)length);
            _position += (int)length;
            return result;
        }

        public /*override*/ void Acquire()
        {            
        }

        public /*override*/ void Release()
        {            
        }

        public /*override*/ bool IsAutoExpand
        {
            get { return false; }
            set { }
        }

        public /*override*/ void Expand(int expectedRemaining)
        {
            throw new NotImplementedException();
        }

        public /*override*/ void Expand(int pos, int expectedRemaining)
        {
            throw new NotImplementedException();
        }        
        
        public /*override*/ bool Pooled
        {
            get { return false; }
            set {  }
        }

        public void Mark()
        {
            throw new NotImplementedException();
        }

        public void Reset()
        {
            throw new NotImplementedException();
        }

        public /*override*/ byte Get(int index)
        {
            throw new NotImplementedException();
        }

//        public /*override*/ void Put(ByteBuffer src)
//        {            
//            if (src == this)
//            {
//                throw new ArgumentException("Cannot copy self into self!");
//            }
//
//            HeapByteBuffer sb;
//            if (src is HeapByteBuffer)
//            {
//                sb = (HeapByteBuffer) src;
//            }
//            else
//            {
//                sb = (HeapByteBuffer)((RefCountingByteBuffer) src).Buf; 
//            }
//            int n = sb.Remaining;
//            if (n > Remaining)
//            {
//                throw new BufferOverflowException("Not enought capacity in this buffer for " + n + " elements - only " + Remaining + " remaining");
//            }
//            Array.Copy(sb._underlyingData, sb._position, _underlyingData, _position, n);
//            sb._position += n;
//            _position += n;            
//        }        
        
        public /*override*/ void Compact()
        {            
            if (Remaining > 0)
            {                
                if (_position > 0)
                {                    
                    Array.Copy(_underlyingData, _position, _underlyingData, 0, Remaining);                    
                }
                _position = Remaining;
            }
            else
            {
                _position = 0;                
            }
            _limit = Capacity;            
        }

        public static HeapByteBuffer wrap(byte[] bytes, int length)
        {
            return new HeapByteBuffer(bytes, length);
        }

        public static HeapByteBuffer wrap(byte[] bytes)
        {
            return new HeapByteBuffer(bytes, bytes.Length);
        }
    }
}


