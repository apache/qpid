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

public abstract class VariableWidthWriter<V> implements ValueWriter<V>
{
    private int _written;
    private int _size;

    public int writeToBuffer(ByteBuffer buffer)
    {

        int written = _written;
        final int length = getLength();
        boolean singleOctetSize = _size == 1;
        if(singleOctetSize)
        {
            switch(written)
            {
                case 0:
                    if(buffer.hasRemaining())
                    {
                        buffer.put(getSingleOctetEncodingCode());
                    }
                    else
                    {
                        break;
                    }
                case 1:
                    if(buffer.hasRemaining())
                    {
                        buffer.put((byte)length);
                        written = 2;
                    }
                    else
                    {
                        written = 1;
                        break;
                    }
                default:

                    final int toWrite = 2 + length - written;
                    if(buffer.remaining() >= toWrite)
                    {
                        writeBytes(buffer, written-2,toWrite);
                        written = length + 2;
                        clearValue();
                    }
                    else
                    {
                        final int remaining = buffer.remaining();

                        writeBytes(buffer, written-2, remaining);
                        written += remaining;
                    }

            }
        }
        else
        {

            int remaining = buffer.remaining();

            switch(written)
            {

                case 0:
                    if(buffer.hasRemaining())
                    {
                        buffer.put(getFourOctetEncodingCode());
                        remaining--;
                        written = 1;
                    }
                    else
                    {
                        break;
                    }
                case 1:
                    if(remaining >= 4)
                    {
                        buffer.putInt(length);
                        remaining-=4;
                        written+=4;
                    }
                case 2:
                case 3:
                    if(remaining >= 2 && written <= 3)
                    {
                        buffer.putShort((short)((length >> ((3-written)<<3)) & 0xFFFF ));
                        remaining -= 2;
                        written += 2;
                    }
                case 4:
                    if(remaining >=1 && written <=4)
                    {
                        buffer.put((byte)((length>> ((4-written)<<3)) & 0xFF ));
                        written++;
                    }

                default:

                    final int toWrite = 5 + length - written;
                    if(buffer.remaining() >= toWrite)
                    {
                        writeBytes(buffer, written-5,toWrite);
                        written = length + 5;
                        clearValue();
                    }
                    else if(buffer.hasRemaining())
                    {
                        written += buffer.remaining();
                        writeBytes(buffer, written-5, buffer.remaining());
                    }

            }

        }

        _written = written;
        return 1 + _size + length;
    }

    protected abstract void clearValue();

    protected abstract boolean hasValue();

    protected abstract byte getFourOctetEncodingCode();

    protected abstract byte getSingleOctetEncodingCode();

    public void setValue(V value)
    {
        _written = 0;
        _size = (getLength() & 0xFFFFFF00) == 0 ? 1 : 4;
    }

    protected abstract int getLength();

    protected abstract void writeBytes(ByteBuffer buf, int offset, int length);


    public boolean isComplete()
    {
        return !hasValue() || _written == getLength() + _size + 1;
    }


}
