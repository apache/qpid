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

import java.nio.ByteBuffer;

public class IntegerWriter implements ValueWriter<Integer>
{
    private static final byte EIGHT_BYTE_FORMAT_CODE = (byte)0x71;
    private static final byte ONE_BYTE_FORMAT_CODE = (byte) 0x54;

    private ValueWriter<Integer> _delegate;

    private final FixedFourWriter<Integer> _eightByteWriter = new FixedFourWriter<Integer>()
    {

        @Override
        byte getFormatCode()
        {
            return EIGHT_BYTE_FORMAT_CODE;
        }

        @Override
        int convertValueToInt(Integer value)
        {
            return value.intValue();
        }
    };

    private final ValueWriter<Integer> _oneByteWriter = new FixedOneWriter<Integer>()
    {

        @Override protected byte getFormatCode()
        {
            return ONE_BYTE_FORMAT_CODE;
        }

        @Override protected byte convertToByte(final Integer value)
        {
            return value.byteValue();
        }
    };


    public int writeToBuffer(final ByteBuffer buffer)
    {
        return _delegate.writeToBuffer(buffer);
    }

    public void setValue(final Integer i)
    {
        if(i >= -128 && i <= 127)
        {
            _delegate = _oneByteWriter;
        }
        else
        {
            _delegate = _eightByteWriter;
        }
        _delegate.setValue(i);
    }

    public boolean isComplete()
    {
        return _delegate.isComplete();
    }

    public boolean isCacheable()
    {
        return false;
    }


    private static Factory<Integer> FACTORY = new Factory<Integer>()
                                            {

                                                public ValueWriter<Integer> newInstance(Registry registry)
                                                {
                                                    return new IntegerWriter();
                                                }
                                            };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(Integer.class, FACTORY);
    }
}