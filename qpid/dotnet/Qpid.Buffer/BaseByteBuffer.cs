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
//package org.apache.mina.common.support;
//
//import java.nio.ByteOrder;
//import java.nio.CharBuffer;
//import java.nio.DoubleBuffer;
//import java.nio.FloatBuffer;
//import java.nio.IntBuffer;
//import java.nio.LongBuffer;
//import java.nio.ShortBuffer;
//
//import org.apache.mina.common.ByteBuffer;
//import org.apache.mina.common.ByteBufferAllocator;

namespace Qpid.Buffer
{
    /**
     * A base implementation of {@link ByteBuffer}.  This implementation
     * assumes that {@link ByteBuffer#buf()} always returns a correct NIO
     * {@link FixedByteBuffer} instance.  Most implementations could
     * extend this class and implement their own buffer management mechanism.
     *
     * @noinspection StaticNonFinalField
     * @see ByteBufferAllocator
     */
    public abstract class BaseByteBuffer : ByteBuffer
    {
        private bool _autoExpand;
        
        /**
         * We don't have any access to Buffer.markValue(), so we need to track it down,
         * which will cause small extra overhead.
         */
        private int _mark = -1;

        protected BaseByteBuffer()
        {
        }

        public override bool isDirect()
        {
            return buf().isDirect();
        }

        public override bool isReadOnly()
        {
            return buf().isReadOnly();
        }

        public override int capacity()
        {
            return buf().capacity();
        }

        public override ByteBuffer capacity(int newCapacity)
        {
            if( newCapacity > capacity() )
            {
                // Allocate a new buffer and transfer all settings to it.
                int pos = position();
                int lim = limit();
                ByteOrder bo = order();

                capacity0( newCapacity );
                buf().limit( lim );
                if( _mark >= 0 )
                {
                    buf().position( _mark );
                    buf().mark();
                }
                buf().position( pos );
                buf().order( bo );
            }
            
            return this;
        }
        
        /**
         * Implement this method to increase the capacity of this buffer.
         * <tt>newCapacity</tt> is always greater than the current capacity.
         */
        protected abstract void capacity0( int newCapacity );

        public override bool isAutoExpand()
        {
            return _autoExpand;
        }

        public override ByteBuffer setAutoExpand(bool autoExpand)
        {
            _autoExpand = autoExpand;
            return this;
        }

        public override ByteBuffer expand(int pos, int expectedRemaining)
        {
            int end = pos + expectedRemaining;
            if( end > capacity() )
            {
                // The buffer needs expansion.
                capacity( end );
            }
            
            if( end > limit() )
            {
                // We call limit() directly to prevent StackOverflowError
                buf().limit( end );
            }
            return this;
        }

        public override int position()
        {
            return buf().position();
        }

        public override ByteBuffer position(int newPosition)
        {
            autoExpand( newPosition, 0 );
            buf().position( newPosition );
            if( _mark > newPosition )
            {
                _mark = -1;
            }
            return this;
        }

        public override int limit()
        {
            return buf().limit();
        }

        public override ByteBuffer limit(int newLimit)
        {
            autoExpand( newLimit, 0 );
            buf().limit( newLimit );
            if( _mark > newLimit )
            {
                _mark = -1;
            }
            return this;
        }
        
        public override ByteBuffer mark()
        {
            buf().mark();
            _mark = position();
            return this;
        }

        public override int markValue()
        {
            return _mark;
        }

        public override ByteBuffer reset()
        {
            buf().reset();
            return this;
        }

        public override ByteBuffer clear()
        {
            buf().clear();
            _mark = -1;
            return this;
        }

        public override ByteBuffer flip()
        {
            buf().flip();
            _mark = -1;
            return this;
        }

        public override ByteBuffer rewind()
        {
            buf().rewind();
            _mark = -1;
            return this;
        }

        public override byte get()
        {
            return buf().get();
        }

        public override ByteBuffer put(byte b)
        {
            autoExpand( 1 );
            buf().put( b );
            return this;
        }

        public override byte get(int index)
        {
            return buf().get( index );
        }

        public override ByteBuffer put(int index, byte b)
        {
            autoExpand( index, 1 );
            buf().put( index, b );
            return this;
        }

        public override ByteBuffer get(byte[] dst, int offset, int length)
        {
            buf().get( dst, offset, length );
            return this;
        }

        public override ByteBuffer get(byte[] dst)
        {
            buf().get(dst);
            return this;
        }

        public override ByteBuffer put(FixedByteBuffer src)
        {
            autoExpand( src.remaining() );
            buf().put( src );
            return this;
        }

        public override ByteBuffer put(byte[] src, int offset, int length)
        {
            autoExpand( length );
            buf().put( src, offset, length );
            return this;
        }

        public override ByteBuffer compact()
        {
            buf().compact();
            _mark = -1;
            return this;
        }

        public override ByteOrder order()
        {
            return buf().order();
        }

        public override ByteBuffer order(ByteOrder bo)
        {
            buf().order( bo );
            return this;
        }

        public override char getChar()
        {
            return buf().getChar();
        }

        public override ByteBuffer putChar(char value)
        {
            autoExpand( 2 );
            buf().putChar( value );
            return this;
        }

        public override char getChar(int index)
        {
            return buf().getChar( index );
        }

        public override ByteBuffer putChar(int index, char value)
        {
            autoExpand( index, 2 );
            buf().putChar( index, value );
            return this;
        }

//        public CharBuffer asCharBuffer()
//        {
//            return buf().asCharBuffer();
//        }

        public override short getShort()
        {
            return buf().getShort();
        }

        public override ByteBuffer putShort(short value)
        {
            autoExpand( 2 );
            buf().putShort( value );
            return this;
        }

        public override short getShort(int index)
        {
            return buf().getShort( index );
        }

        public override ByteBuffer putShort(int index, short value)
        {
            autoExpand( index, 2 );
            buf().putShort( index, value );
            return this;
        }

        public override ushort GetUnsignedShort()
        {
            return buf().getUnsignedShort();            
        }


//        public ShortBuffer asShortBuffer()
//        {
//            return buf().asShortBuffer();
//        }

        public override int getInt()
        {
            return buf().getInt();
        }

        public override uint GetUnsignedInt()
        {
            return buf().getUnsignedInt();
        }

        public override ByteBuffer putInt(int value)
        {
            autoExpand( 4 );
            buf().putInt( value );
            return this;
        }

        public override int getInt(int index)
        {
            return buf().getInt( index );
        }

        public override ByteBuffer putInt(int index, int value)
        {
            autoExpand( index, 4 );
            buf().putInt( index, value );
            return this;
        }

//        public IntBuffer asIntBuffer()
//        {
//            return buf().asIntBuffer();
//        }

        public override long getLong()
        {
            return buf().getLong();
        }

        public override ByteBuffer putLong(long value)
        {
            autoExpand( 8 );
            buf().putLong( value );
            return this;
        }

        public override long getLong(int index)
        {
            return buf().getLong( index );
        }

        public override ByteBuffer putLong(int index, long value)
        {
            autoExpand( index, 8 );
            buf().putLong( index, value );
            return this;
        }

//        public LongBuffer asLongBuffer()
//        {
//            return buf().asLongBuffer();
//        }

        public override float getFloat()
        {
            return buf().getFloat();
        }

        public override ByteBuffer putFloat(float value)
        {
            autoExpand( 4 );
            buf().putFloat( value );
            return this;
        }

        public override float getFloat(int index)
        {
            return buf().getFloat( index );
        }

        public override ByteBuffer putFloat(int index, float value)
        {
            autoExpand( index, 4 );
            buf().putFloat( index, value );
            return this;
        }

//        public FloatBuffer asFloatBuffer()
//        {
//            return buf().asFloatBuffer();
//        }

        public override double getDouble()
        {
            return buf().getDouble();
        }

        public override ByteBuffer putDouble(double value)
        {
            autoExpand( 8 );
            buf().putDouble( value );
            return this;
        }

        public override double getDouble(int index)
        {
            return buf().getDouble( index );
        }

        public override ByteBuffer putDouble(int index, double value)
        {
            autoExpand( index, 8 );
            buf().putDouble( index, value );
            return this;
        }

        public override ByteBuffer put(byte[] src)
        {
            buf().put(src);
            return this;
        }

//        public DoubleBuffer asDoubleBuffer()
//        {
//            return buf().asDoubleBuffer();
//        }
    }
}