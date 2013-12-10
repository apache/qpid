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

package org.apache.qpid.amqp_1_0.codec;

import org.apache.qpid.amqp_1_0.type.UnsignedLong;

import java.nio.ByteBuffer;

public class UnsignedLongWriter implements ValueWriter<UnsignedLong>
{


    private static final byte EIGHT_BYTE_FORMAT_CODE = (byte) 0x80;
    private static final byte ONE_BYTE_FORMAT_CODE = (byte) 0x53;
    private static final byte ZERO_BYTE_FORMAT_CODE = (byte) 0x44;

    private ValueWriter<UnsignedLong> _delegate;

    private final FixedEightWriter<UnsignedLong> _eightByteWriter = new FixedEightWriter<UnsignedLong>()
    {

        @Override
        byte getFormatCode()
        {
            return EIGHT_BYTE_FORMAT_CODE;
        }

        @Override
        long convertValueToLong(UnsignedLong value)
        {
            return value.longValue();
        }

    };

    private final ValueWriter<UnsignedLong> _oneByteWriter = new FixedOneWriter<UnsignedLong>()
    {

        @Override protected byte getFormatCode()
        {
            return ONE_BYTE_FORMAT_CODE;
        }

        @Override protected byte convertToByte(final UnsignedLong value)
        {
            return value.byteValue();
        }
    };

    private final ValueWriter<UnsignedLong> _zeroByteWriter = new ValueWriter<UnsignedLong>()
    {
        private boolean _complete;


        public int writeToBuffer(ByteBuffer buffer)
        {

            if(!_complete && buffer.hasRemaining())
            {
                buffer.put(ZERO_BYTE_FORMAT_CODE);
                _complete = true;
            }

            return 1;
        }

        public void setValue(UnsignedLong ulong)
        {
            _complete = false;
        }

        public boolean isCacheable()
        {
            return true;
        }

        public boolean isComplete()
        {
            return _complete;
        }

    };



    private static Factory<UnsignedLong> FACTORY = new Factory<UnsignedLong>()
                                            {

                                                public ValueWriter<UnsignedLong> newInstance(Registry registry)
                                                {
                                                    return new UnsignedLongWriter();
                                                }
                                            };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(UnsignedLong.class, FACTORY);
    }

    public int writeToBuffer(final ByteBuffer buffer)
    {
        return _delegate.writeToBuffer(buffer);
    }

    public void setValue(final UnsignedLong ulong)
    {
        if(ulong.equals(UnsignedLong.ZERO))
        {
            _delegate = _zeroByteWriter;
        }
        else if((ulong.longValue() & 0xffL) == ulong.longValue())
        {
            _delegate = _oneByteWriter;
        }
        else
        {
            _delegate = _eightByteWriter;
        }

        _delegate.setValue(ulong);
    }

    public boolean isComplete()
    {
        return _delegate.isComplete();
    }

    public boolean isCacheable()
    {
        return false;
    }

}