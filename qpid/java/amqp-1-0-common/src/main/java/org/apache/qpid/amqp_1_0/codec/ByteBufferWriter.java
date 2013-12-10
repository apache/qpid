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

public class ByteBufferWriter extends SimpleVariableWidthWriter<ByteBuffer>
{

    @Override
    protected byte getFourOctetEncodingCode()
    {
        return (byte)0xb0;
    }

    @Override
    protected byte getSingleOctetEncodingCode()
    {
        return (byte)0xa0;
    }

    @Override
    protected byte[] getByteArray(ByteBuffer value)
    {
        if(value.hasArray() && value.arrayOffset() == 0 && value.remaining() == value.array().length)
        {
            return value.array();
        }
        else
        {
            byte[] copy = new byte[value.remaining()];
            value.duplicate().get(copy);
            return copy;
        }
    }

    @Override
    protected int getOffset()
    {
        return 0;
    }

    private static Factory<ByteBuffer> FACTORY = new Factory<ByteBuffer>()
                                            {

                                                public ValueWriter<ByteBuffer> newInstance(Registry registry)
                                                {
                                                    return new ByteBufferWriter();
                                                }
                                            };

    public static void register(Registry registry)
    {
        registry.register(ByteBuffer.class, FACTORY);
    }
}