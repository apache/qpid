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
using System.Runtime.CompilerServices;

namespace Qpid.Buffer
{
    /// <summary>
    /// A simplistic <see cref="ByteBufferAllocator"/> which simply allocates a new
    /// buffer every time
    /// </summary>
    public class SimpleByteBufferAllocator : IByteBufferAllocator
    {
        private const int MINIMUM_CAPACITY = 1;

        public SimpleByteBufferAllocator()
        {
        }
        
        public ByteBuffer Allocate( int capacity, bool direct )
        {
            FixedByteBuffer nioBuffer;
            if( direct )
            {
                nioBuffer = FixedByteBuffer.allocateDirect( capacity );            
            }
            else
            {
                nioBuffer = FixedByteBuffer.allocate( capacity );            
            }
            return new SimpleByteBuffer( nioBuffer );
        }
        
        public ByteBuffer Wrap( FixedByteBuffer nioBuffer )
        {
            return new SimpleByteBuffer( nioBuffer );
        }

        public void Dispose()
        {
        }

        private class SimpleByteBuffer : BaseByteBuffer
        {
            private FixedByteBuffer _buf;
            private int refCount = 1;

            internal SimpleByteBuffer( FixedByteBuffer buf )
            {
                this._buf = buf;
                buf.order( ByteOrder.BigEndian );
                refCount = 1;
            }

            [MethodImpl(MethodImplOptions.Synchronized)]
            public override void acquire()
            {
                if( refCount <= 0 )
                {
                    throw new InvalidOperationException("Already released buffer.");
                }

                refCount ++;
            }

            public override void release()
            {
                lock( this )
                {
                    if( refCount <= 0 )
                    {
                        refCount = 0;
                        throw new InvalidOperationException(
                                "Already released buffer.  You released the buffer too many times." );
                    }

                    refCount --;
                    if( refCount > 0)
                    {
                        return;
                    }
                }
            }

            public override FixedByteBuffer buf()
            {
                return _buf;
            }

            public override bool isPooled()
            {
                return false;
            }

            public override void setPooled(bool pooled)
            {
            }

            protected override void capacity0(int requestedCapacity)
            {
                int newCapacity = MINIMUM_CAPACITY;
                while( newCapacity < requestedCapacity )
                {
                    newCapacity <<= 1;
                }
                
                FixedByteBuffer oldBuf = this._buf;
                FixedByteBuffer newBuf;
                if( isDirect() )
                {
                    newBuf = FixedByteBuffer.allocateDirect( newCapacity );
                }
                else
                {
                    newBuf = FixedByteBuffer.allocate( newCapacity );
                }

                newBuf.clear();
                oldBuf.clear();
                newBuf.put( oldBuf );
                this._buf = newBuf;
            }

            public override ByteBuffer duplicate() {
                return new SimpleByteBuffer( this._buf.duplicate() );
            }

            public override ByteBuffer slice()
            {
                return new SimpleByteBuffer( this._buf.slice() );
            }

            public override ByteBuffer asReadOnlyBuffer()
            {
                return new SimpleByteBuffer( this._buf.asReadOnlyBuffer() );
            }

            public override byte[] array()
            {
                return _buf.array();
            }

            public override int arrayOffset()
            {
                return _buf.arrayOffset();
            }

            public override void put(ushort value)
            {
                _buf.put(value);
            }

            public override void put(uint max)
            {
                _buf.put(max);
            }

            public override void put(ulong tag)
            {
                _buf.put(tag);
            }

            public override ulong GetUnsignedLong()
            {
                return _buf.getUnsignedLong();
            }

        }
    }   
}
