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

public abstract class FixedTwoWriter <T extends Object> implements ValueWriter<T>
{
    private int _written = 3;
    private short _value;

    public int writeToBuffer(ByteBuffer buffer)
    {

        switch(_written)
        {
            case 0:
                if(buffer.hasRemaining())
                {
                    buffer.put(getFormatCode());
                }
                else
                {
                    break;
                }
            case 1:

                if(buffer.remaining()>1)
                {
                    buffer.putShort(_value);
                    _written = 3;
                }
                else if(buffer.hasRemaining())
                {
                    buffer.put((byte) (0xFF & (_value >> 8)));
                    _written = 2;
                }
                else
                {
                    _written = 1;
                }
                break;
            case 2:
                if(buffer.hasRemaining())
                {
                    buffer.put((byte)(0xFF & _value));
                }


        }

        return 3;
    }


    public final void setValue(T value)
    {
        _written = 0;
        _value = convertValueToShort(value);
    }

    abstract short convertValueToShort(T value);

    public boolean isCacheable()
    {
        return true;
    }

    public boolean isComplete()
    {
        return _written == 3;
    }

    abstract byte getFormatCode();


}