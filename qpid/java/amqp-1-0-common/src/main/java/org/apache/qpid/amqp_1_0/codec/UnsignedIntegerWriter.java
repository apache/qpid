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

import org.apache.qpid.amqp_1_0.type.UnsignedInteger;

import java.nio.ByteBuffer;

public class UnsignedIntegerWriter implements ValueWriter<UnsignedInteger>
{
    private static final byte EIGHT_BYTE_FORMAT_CODE = (byte)0x70;
    private static final byte ONE_BYTE_FORMAT_CODE = (byte) 0x52;
    private static final byte ZERO_BYTE_FORMAT_CODE = (byte) 0x43;

    private ValueWriter<UnsignedInteger> _delegate;

    private final FixedFourWriter<UnsignedInteger> _eightByteWriter = new FixedFourWriter<UnsignedInteger>()
    {

        @Override
        byte getFormatCode()
        {
            return EIGHT_BYTE_FORMAT_CODE;
        }

        @Override
        int convertValueToInt(UnsignedInteger value)
        {
            return value.intValue();
        }
    };

    private final ValueWriter<UnsignedInteger> _oneByteWriter = new FixedOneWriter<UnsignedInteger>()
    {

        @Override protected byte getFormatCode()
        {
            return ONE_BYTE_FORMAT_CODE;
        }

        @Override protected byte convertToByte(final UnsignedInteger value)
        {
            return value.byteValue();
        }
    };

    private final ValueWriter<UnsignedInteger> _zeroByteWriter = new ValueWriter<UnsignedInteger>()
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

            public void setValue(UnsignedInteger uint)
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



    public int writeToBuffer(final ByteBuffer buffer)
    {
        return _delegate.writeToBuffer(buffer);
    }

    public void setValue(final UnsignedInteger uint)
    {
        if(uint.equals(UnsignedInteger.ZERO))
        {
            _delegate = _zeroByteWriter;
        }
        else if(uint.compareTo(UnsignedInteger.valueOf(256))<0)
        {
            _delegate = _oneByteWriter;
        }
        else
        {
            _delegate = _eightByteWriter;
        }
        _delegate.setValue(uint);
    }

    public boolean isComplete()
    {
        return _delegate.isComplete();
    }

    public boolean isCacheable()
    {
        return false;
    }


    private static Factory<UnsignedInteger> FACTORY = new Factory<UnsignedInteger>()
                                            {

                                                public ValueWriter<UnsignedInteger> newInstance(Registry registry)
                                                {
                                                    return new UnsignedIntegerWriter();
                                                }
                                            };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(UnsignedInteger.class, FACTORY);
    }
}