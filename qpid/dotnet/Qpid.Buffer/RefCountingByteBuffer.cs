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
    public class RefCountingByteBuffer : ByteBuffer
    {
        private ByteBuffer _buf;

        private int _refCount = 1;
        private bool _autoExpand;
        private bool _pooled;
        
        public void Init(ByteBuffer buf)
        {
            lock (this)
            {
                _buf = buf;
                buf.Clear();
                _autoExpand = false;
                _pooled = true;
                _refCount = 1;
            }
        }
        
        public override void Acquire()
        {
            lock (this)
            {
                if (_refCount <= 0)
                {
                    throw new Exception("Already released buffer");
                }
                _refCount++;
            }
        }

        public override void Release()
        {
            lock (this)
            {
                if (_refCount <= 0)
                {
                    _refCount = 0;
                    throw new Exception("Already released buffer. Release called too many times");
                }

                _refCount--;
                if (_refCount > 0)
                {
                    return;
                }
            }
            
            if (_pooled)
            {
                Release0(_buf);
            }
            
            lock (_containerStack)
            {
                _containerStack.Push(this);
            }
        }

        public ByteBuffer Buf
        {
            get
            {
                return _buf;
            }
        }
        
        public override bool IsAutoExpand
        {
            get
            {
                return _autoExpand;
            }
            
            set
            {
                _autoExpand = value;
            }
        }
        
        public override bool Pooled
        {
            get
            {
                return _pooled;
            }
            
            set
            {
                _pooled = value;
            }
        }

        public override int Capacity
        {
            get
            {
                return _buf.Capacity;
            }
        }

        public override int Position
        {
            get
            {
                return _buf.Position;
            }
            set
            {
                AutoExpand(value, 0);
                _buf.Position = value;
            }
        }

        public override int Limit
        {
            get
            {
                return _buf.Limit;
            }
            set
            {
                AutoExpand(value, 0);
                _buf.Limit = value;
            }
        }

        /*public override void Mark()
        {
            _buf.Mark();
        }

        public override void Reset()
        {
            _buf.Reset();
        }*/
        
        public override void Clear()
        {
            _buf.Clear();
        }

        public override void Flip()
        {
            _buf.Flip();
        }

        public override void Rewind()
        {
            _buf.Rewind();
        }

        public override int Remaining
        {
            get
            {
                return _buf.Remaining;
            }
        }
        
        public override byte Get()
        {
            return _buf.Get();
        }

        public override void Put(byte data)
        {
            AutoExpand(1);
            _buf.Put(data);
        }

        public override byte Get(int index)
        {
            return _buf.Get(index);
        }

        public override void Compact()
        {
            _buf.Compact();
        }
        

        public override void Get(byte[] destination)
        {
            _buf.Get(destination);
        }

        public override ushort GetUnsignedShort()
        {
            return _buf.GetUnsignedShort();
        }

        public override uint GetUnsignedInt()
        {
            return _buf.GetUnsignedInt();
        }

        public override ulong GetUnsignedLong()
        {
            return _buf.GetUnsignedLong();
        }

        public override string GetString(uint length, Encoding encoder)
        {
            return _buf.GetString(length, encoder);
        }

        public override void Put(byte[] data)
        {
            AutoExpand(data.Length);
            _buf.Put(data);
        }

        public override void Put(byte[] data, int offset, int size)
        {
            AutoExpand(size);
            _buf.Put(data, offset, size);
        }

        public override void Put(ushort data)
        {
            AutoExpand(2);
            _buf.Put(data);
        }

        public override void Put(uint data)
        {
            AutoExpand(4);
            _buf.Put(data);
        }

        public override void Put(ulong data)
        {
            AutoExpand(8);
            _buf.Put(data);
        }

        public override void Put(ByteBuffer buf)
        {
            AutoExpand(buf.Remaining);
            _buf.Put(buf);
        }
        
        public override void Expand(int expectedRemaining)
        {
            if (_autoExpand)
            {
                int pos = _buf.Position;
                int limit = _buf.Limit;
                int end = pos + expectedRemaining;
                if (end > limit)
                {
                    EnsureCapacity(end);
                    _buf.Limit = end;                                        
                }
            }
        }

        public override void Expand(int pos, int expectedRemaining)
        {        
            if (_autoExpand)
            {
                int limit = _buf.Limit;
                int end = pos + expectedRemaining;
                if (end > limit)
                {
                    EnsureCapacity(end);
                    _buf.Limit = end;                    
                }
            }
        }

        private void EnsureCapacity(int requestedCapacity)
        {
            if (requestedCapacity <= _buf.Capacity)
            {
                return;
            }
            
            int newCapacity = MINIMUM_CAPACITY;
            while (newCapacity < requestedCapacity)
            {
                newCapacity <<= 1;
            }

            ByteBuffer oldBuf = _buf;
            ByteBuffer newBuf = Allocate0(newCapacity, false);
            newBuf.Clear();
            int pos = oldBuf.Position;
            int limit = oldBuf.Limit;
            oldBuf.Clear();
            newBuf.Put(oldBuf);
            newBuf.Position = pos;
            newBuf.Limit = limit;
            _buf = newBuf;
            Release0(oldBuf);
        }
        
        public override byte[] ToByteArray()
        {
            return _buf.ToByteArray();
        }

        public override string ToString()
        {
            return "RefCountingByteBuffer: refs= " + _refCount + "buf=" + base.ToString();
        }
    }
}

