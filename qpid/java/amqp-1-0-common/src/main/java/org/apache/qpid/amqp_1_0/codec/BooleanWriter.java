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

public class BooleanWriter implements ValueWriter<Boolean>
{
    private boolean _complete = true;
    private boolean _value;

    public int writeToBuffer(ByteBuffer buffer)
    {
        if(!_complete & buffer.hasRemaining())
        {
            buffer.put(_value ? (byte)0x41 : (byte)0x42);
            _complete = true;
        }
        return 1;
    }

    public void setValue(Boolean value)
    {
        _complete = false;
        _value = value.booleanValue();
    }

    public boolean isCacheable()
    {
        return true;
    }

    public boolean isComplete()
    {
        return _complete;
    }

    private static Factory<Boolean> FACTORY = new Factory<Boolean>()
                                            {

                                                public ValueWriter<Boolean> newInstance(Registry registry)
                                                {
                                                    return new BooleanWriter();
                                                }
                                            };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(Boolean.class, FACTORY);
    }
}