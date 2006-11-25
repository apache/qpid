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
using System.Collections;
using System.Text;

namespace Qpid.Buffer
{
    /// <summary>
    /// A buffer that manages an underlying byte oriented stream, and writes and reads to and from it in
    /// BIG ENDIAN order.
    /// </summary>
    public abstract class ByteBuffer
    {
        protected const int MINIMUM_CAPACITY = 1;
        
        protected static Stack _containerStack = new Stack();
        
        protected static Stack[] _heapBufferStacks = new Stack[]
            {
                new Stack(), new Stack(), new Stack(), new Stack(), 
                new Stack(), new Stack(), new Stack(), new Stack(), 
                new Stack(), new Stack(), new Stack(), new Stack(), 
                new Stack(), new Stack(), new Stack(), new Stack(), 
                new Stack(), new Stack(), new Stack(), new Stack(), 
                new Stack(), new Stack(), new Stack(), new Stack(), 
                new Stack(), new Stack(), new Stack(), new Stack(), 
                new Stack(), new Stack(), new Stack(), new Stack()
            };

        /// <summary>
        /// Returns the direct or heap buffer which is capable of the specified size.
        /// Currently does not support direct buffers but this will be an option in future.
        /// </summary>
        /// <param name="capacity">The capacity.</param>
        /// <returns></returns>
        public static ByteBuffer Allocate(int capacity)
        {
            // for now, just allocate a heap buffer but in future could do an optimised "direct" buffer
            // that is implemented natively
            return Allocate(capacity, false);
        }
        
        public static ByteBuffer Allocate(int capacity, bool direct)
        {
            ByteBuffer buffer = Allocate0(capacity, direct);
            RefCountingByteBuffer buf = AllocateContainer();
            buf.Init(buffer);
            return buf;
        }
        
        private static RefCountingByteBuffer AllocateContainer()
        {
            RefCountingByteBuffer buf = null;
            lock (_containerStack)
            {
                if (_containerStack.Count > 0)
                {
                    buf = (RefCountingByteBuffer) _containerStack.Pop();
                }
            }
            
            if (buf == null)
            {
                buf = new RefCountingByteBuffer();                
            }
            return buf;
        }
        
        protected static ByteBuffer Allocate0(int capacity, bool direct)
        {
            if (direct)
            {
                throw new NotSupportedException("Direct buffers not currently implemented");
            }            
            int idx = GetBufferStackIndex(_heapBufferStacks, capacity);
            Stack stack = _heapBufferStacks[idx];
            ByteBuffer buf = null;
            lock (stack)
            {
                if (stack.Count > 0)
                {
                    buf = (ByteBuffer) stack.Pop();
                }
            }

            if (buf == null)
            {
                buf = new HeapByteBuffer(MINIMUM_CAPACITY << idx);
            }

            return buf;
        }
        
        protected static void Release0(ByteBuffer buf)
        {
            Stack stack = _heapBufferStacks[GetBufferStackIndex(_heapBufferStacks, buf.Capacity)];
            lock (stack)
            {
                stack.Push(buf);
            }            
        }
        
        private static int GetBufferStackIndex(Stack[] bufferStacks, int size)
        {
            int targetSize = MINIMUM_CAPACITY;
            int stackIdx = 0;
            // each bucket contains buffers that are double the size of the previous bucket
            while (size > targetSize)
            {
                targetSize <<= 1;
                stackIdx++;
                if (stackIdx >= bufferStacks.Length)
                {
                    throw new ArgumentOutOfRangeException("size", "Buffer size is too big: " + size);
                }
            }
            return stackIdx;
        }

        /// <summary>
        /// Increases the internal reference count of this buffer to defer automatic release. You have
        /// to invoke release() as many times as you invoked this method to release this buffer.
        /// </summary>
        public abstract void Acquire();

        /// <summary>
        /// Releases the specified buffer to the buffer pool.
        /// </summary>
        public abstract void Release();

        public abstract int Capacity
        {
            get;
        }

        public abstract bool IsAutoExpand
        {
            get;
            set;
        }

        /// <summary>
        /// Changes the capacity and limit of this buffer sot his buffer gets the specified
        /// expectedRemaining room from the current position. This method works even if you didn't set
        /// autoExpand to true.
        /// </summary>
        /// <param name="expectedRemaining">Room you want from the current position</param>        
        public abstract void Expand(int expectedRemaining);

        /// <summary>
        /// Changes the capacity and limit of this buffer sot his buffer gets the specified
        /// expectedRemaining room from the specified position.
        /// </summary>
        /// <param name="pos">The pos you want the room to be available from.</param>
        /// <param name="expectedRemaining">The expected room you want available.</param>        
        public abstract void Expand(int pos, int expectedRemaining);

        /// <summary>
        /// Returns true if and only if this buffer is returned back to the buffer pool when released.
        /// </summary>
        /// <value><c>true</c> if pooled; otherwise, <c>false</c>.</value>
        public abstract bool Pooled
        {
            get;
            set;
        }
        
        public abstract int Position
        {
            get;
            set;
        }

        public abstract int Limit
        {
            get;
            set;
        }

        //public abstract void Mark();

        //public abstract void Reset();

        public abstract void Clear();

        /// <summary>
        /// Clears this buffer and fills its content with NULL. The position is set to zero, the limit is set to
        /// capacity and the mark is discarded.
        /// </summary>
        public void Sweep()
        {
            Clear();
            FillAndReset(Remaining);
        }
        
        public void Sweep(byte value)
        {
            Clear();
            FillAndReset(value, Remaining);
        }

        public abstract void Flip();

        public abstract void Rewind();

        public abstract int Remaining
        {
            get;
        }
        
        public bool HasRemaining()
        {
            return Remaining > 0;
        }

        public abstract byte Get();

        public abstract byte Get(int index);
        
        public abstract void Get(byte[] destination);

        public abstract ushort GetUnsignedShort();

        public abstract uint GetUnsignedInt();

        public abstract ulong GetUnsignedLong();

        public abstract string GetString(uint length, Encoding encoder);

        public abstract void Put(byte data);

        public abstract void Put(byte[] data);
        public abstract void Put(byte[] data, int offset, int size);

        public abstract void Put(ushort data);

        public abstract void Put(uint data);

        public abstract void Put(ulong data);

        public abstract void Put(ByteBuffer buf);
        
        public abstract void Compact();

        public abstract byte[] ToByteArray();
        
        public override string ToString()
        {
            StringBuilder buf = new StringBuilder();
            buf.Append("HeapBuffer");
            buf.AppendFormat("[pos={0} lim={1} cap={2} : {3}]", Position, Limit, Capacity, HexDump);
            return buf.ToString();
        }

        public override int GetHashCode()
        {
            int h = 1;
            int p = Position;
            for (int i = Limit - 1; i >= p; i--)
            {
                h = 31 * h + Get(i);
            }

            return h;
        }

        public override bool Equals(object obj)
        {
            if (!(obj is ByteBuffer))
            {
                return false;
            }
            ByteBuffer that = (ByteBuffer) obj;
            
            if (Remaining != that.Remaining)
            {
                return false;
            }
            int p = Position;
            for (int i = Limit - 1, j = that.Limit - 1; i >= p; i--, j--)
            {
                byte v1 = this.Get(i);
                byte v2 = that.Get(j);
                if (v1 != v2)
                {
                    return false;
                }
            }
            return true;
        }
        
        public string HexDump
        {
            get
            {
                return ByteBufferHexDumper.GetHexDump(this);
            }
        }

        /// <summary>
        /// Fills the buffer with the specified specified value. This method moves the buffer position forward.
        /// </summary>
        /// <param name="value">The value.</param>
        /// <param name="size">The size.</param>
        public void Fill(byte value, int size)
        {
            AutoExpand(size);
            int q = size >> 3;
            int r = size & 7;
                        
            if (q > 0)
            {
                int intValue = value | (value << 8) | (value << 16) | (value << 24);
                long longValue = intValue;
                longValue <<= 32;
                longValue |= (ushort)intValue;
                
                for (int i = q; i > 0; i--)
                {
                    Put((ulong)longValue);
                }
            }

            q = r >> 2;
            r = r & 3;
            
            if (q > 0)
            {
                int intValue = value | (value << 8) | (value << 16) | (value << 24);
                Put((uint)intValue);
            }

            q = r >> 1;
            r = r & 1;
            
            if (q > 0)
            {
                short shortValue = (short) (value | (value << 8));
                Put((ushort) shortValue);
            }
            if (r > 0)
            {
                Put(value);
            }
        }
        
        public void FillAndReset(byte value, int size)
        {
            AutoExpand(size);
            int pos = Position;
            try
            {
                Fill(value, size);
            }
            finally
            {
                Position = pos;
            }            
        }
        
        public void Fill(int size)
        {
            AutoExpand(size);
            int q = size >> 3;
            int r = size & 7;

            for (int i = q; i > 0; i--)
            {
                Put(0L);
            }

            q = r >> 2;
            r = r & 3;

            if (q > 0)
            {
                Put(0);
            }

            q = r >> 1;
            r = r & 1;

            if(q > 0)
            {
                Put((ushort) 0);
            }

            if (r > 0)
            {
                Put((byte) 0);
            }
        }
        
        public void FillAndReset(int size)
        {
            AutoExpand(size);
            int pos = Position;
            try
            {
                Fill(size);
            }
            finally
            {
                Position = pos;
            }
        }
        
        public void Skip(int size)
        {
            AutoExpand(size);
            Position = Position + size;
        }
        
        protected void AutoExpand(int expectedRemaining)
        {
            if (IsAutoExpand)
            {
                Expand(expectedRemaining);
            }
        }
        
        protected void AutoExpand(int pos, int expectedRemaining)
        {
            if (IsAutoExpand)
            {
                Expand(pos, expectedRemaining);
            }
        }                
    }
}

