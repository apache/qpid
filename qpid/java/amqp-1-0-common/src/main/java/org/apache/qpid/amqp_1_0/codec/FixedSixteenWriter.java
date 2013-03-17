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

public abstract class FixedSixteenWriter<T extends Object> implements ValueWriter<T>
{
    private int _written = 17;
    private long _msb;
    private long _lsb;

    public final int writeToBuffer(ByteBuffer buffer)
    {
        int remaining = buffer.remaining();
        int written = _written;
        switch(written)
        {
            case 0:
                if(buffer.hasRemaining())
                {
                    buffer.put(getFormatCode());
                    remaining--;
                    written = 1;
                }
                else
                {
                    break;
                }
            case 1:
                if(remaining>=8)
                {
                    buffer.putLong(_msb);
                    written = 9;
                    break;
                }
            case 2:
            case 3:
            case 4:
            case 5:
                if(remaining >= 4)
                {
                    buffer.putInt((int)((_msb >> ((5-written)<<3)) & 0xFFFFFFFF ));
                    remaining-=4;
                    written+=4;
                }
            case 6:
            case 7:
                if(remaining >= 2 && written <= 7)
                {
                    buffer.putShort((short)((_msb >> ((7-written)<<3)) & 0xFFFF ));
                    remaining -= 2;
                    written += 2;
                }
            case 8:
                if(remaining >=1 && written != 9)
                {
                    buffer.put((byte)((_msb >> ((8-written)<<3)) & 0xFF ));
                    written++;
                }


        }
        if(remaining != 0)
        {
            switch(written)
            {
                case 9:
                    if(remaining>=8)
                    {
                        buffer.putLong(_lsb);
                        written = 17;
                        break;
                    }
                case 10:
                case 11:
                case 12:
                case 13:
                    if(remaining >= 4)
                    {
                        buffer.putInt((int)((_lsb >> ((13-written)<<3)) & 0xFFFFFFFF ));
                        remaining-=4;
                        written+=4;
                    }
                case 14:
                case 15:
                    if(remaining >= 2 && written <= 15)
                    {
                        buffer.putShort((short)((_lsb >> ((15-written)<<3)) & 0xFFFF ));
                        remaining -= 2;
                        written += 2;
                    }
                case 16:
                    if(remaining >=1 && written != 17)
                    {
                        buffer.put((byte)((_msb >> ((16-written)<<3)) & 0xFF ));
                        written++;
                    }
            }

        }

        _written = written;

        return 17;
    }

    abstract byte getFormatCode();

    public final void setValue(T value)
    {
        _written = 0;
        _msb = convertValueToMSB(value);
        _lsb = convertValueToLSB(value);
    }

    abstract long convertValueToMSB(T value);
    abstract long convertValueToLSB(T value);

    public boolean isCacheable()
    {
        return true;
    }

    public final boolean isComplete()
    {
        return _written == 17;
    }


}
