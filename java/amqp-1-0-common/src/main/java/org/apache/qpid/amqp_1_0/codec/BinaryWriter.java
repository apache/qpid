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

import org.apache.qpid.amqp_1_0.type.Binary;

public class BinaryWriter extends SimpleVariableWidthWriter<Binary>
{
    private int _offset;
    private int _length;

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
    protected byte[] getByteArray(Binary value)
    {
        _offset = value.getArrayOffset();
        _length = value.getLength();
        return value.getArray();
    }

    @Override
    protected int getOffset()
    {
        return _offset;
    }

    @Override protected int getLength()
    {
        return _length;
    }

    private static Factory<Binary> FACTORY = new Factory<Binary>()
                                            {

                                                public ValueWriter<Binary> newInstance(Registry registry)
                                                {
                                                    return new BinaryWriter();
                                                }
                                            };

    public static void register(Registry registry)
    {
        registry.register(Binary.class, FACTORY);
    }
}