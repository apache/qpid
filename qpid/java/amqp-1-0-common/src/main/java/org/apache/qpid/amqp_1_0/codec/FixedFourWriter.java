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

public abstract class FixedFourWriter<T extends Object> implements ValueWriter<T>
{
    private int _written = 5;
    private int _value;

    public final int writeToBuffer(ByteBuffer buffer)
    {
        int remaining = buffer.remaining();
        int written = _written;
        switch(written)
        {
            case 0:
                if(remaining-- != 0)
                {
                    buffer.put(getFormatCode());
                    written = 1;
                }
                else
                {
                    break;
                }
            case 1:
                if(remaining>=4)
                {
                    buffer.putInt(_value);
                    written = 5;
                    break;
                }
                else if(remaining-- != 0)
                {
                    buffer.put((byte)((_value >> 24)&0xFF));
                    written = 2;
                }
                else
                {
                    break;
                }
            case 2:
                if(remaining-- != 0)
                {
                    buffer.put((byte)((_value >> 16)&0xFF));
                    written = 3;
                }
                else
                {
                    break;
                }
            case 3:
                if(remaining-- != 0)
                {
                    buffer.put((byte)((_value >> 8)&0xFF));
                    written = 4;
                }
                else
                {
                    break;
                }
            case 4:
                if(remaining-- != 0)
                {
                    buffer.put((byte)(_value&0xFF));
                    written = 5;
                }

        }
        _written = written;

        return 5;
    }

    abstract byte getFormatCode();

    public final void setValue(T value)
    {
        if(_written==1)
        {
            // TODO - remove
            System.out.println("Remove");
        }
        _written = 0;
        _value = convertValueToInt(value);
    }

    abstract int convertValueToInt(T value);

    public boolean isCacheable()
    {
        return true;
    }

    public final boolean isComplete()
    {
        return _written == 5;
    }


}