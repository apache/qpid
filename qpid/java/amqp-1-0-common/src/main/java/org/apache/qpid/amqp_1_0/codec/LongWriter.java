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

public class LongWriter implements ValueWriter<Long>
{
    private static final byte EIGHT_BYTE_FORMAT_CODE = (byte) 0x81;


    private static final byte ONE_BYTE_FORMAT_CODE = (byte) 0x55;

    private ValueWriter<Long> _delegate;

    private final FixedEightWriter<Long> _eightByteWriter = new FixedEightWriter<Long>()
    {

        @Override
        byte getFormatCode()
        {
            return EIGHT_BYTE_FORMAT_CODE;
        }

        @Override
        long convertValueToLong(Long value)
        {
            return value;
        }

    };

    private final ValueWriter<Long> _oneByteWriter = new FixedOneWriter<Long>()
    {

        @Override protected byte getFormatCode()
        {
            return ONE_BYTE_FORMAT_CODE;
        }

        @Override protected byte convertToByte(final Long value)
        {
            return value.byteValue();
        }
    };

    public int writeToBuffer(final ByteBuffer buffer)
    {
        return _delegate.writeToBuffer(buffer);
    }

    public void setValue(final Long l)
    {
        if(l >= -128 && l <= 127)
        {
            _delegate = _oneByteWriter;
        }
        else
        {
            _delegate = _eightByteWriter;
        }
        _delegate.setValue(l);
    }

    public boolean isComplete()
    {
        return _delegate.isComplete();
    }

    public boolean isCacheable()
    {
        return false;
    }




    private static Factory<Long> FACTORY = new Factory<Long>()
                                            {

                                                public ValueWriter<Long> newInstance(Registry registry)
                                                {
                                                    return new LongWriter();
                                                }
                                            };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(Long.class, FACTORY);
    }

}