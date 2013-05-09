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
import java.util.HashMap;
import java.util.Map;

public abstract class CompoundWriter<V> implements ValueWriter<V>
{
    private int _length;
    private Registry _registry;
    private static final int LARGE_COMPOUND_THRESHOLD_COUNT = 25;
    private ValueWriter _delegate;

    public CompoundWriter(final Registry registry)
    {
        _registry = registry;
    }

    enum State {
        FORMAT_CODE,
        SIZE_0,
        SIZE_1,
        SIZE_2,
        SIZE_3,
        COUNT_0,
        COUNT_1,
        COUNT_2,
        COUNT_3,
        DELEGATING,
        DONE
    }

    private State _state = State.FORMAT_CODE;

    public int writeToBuffer(ByteBuffer buffer)
    {
        return writeToBuffer(buffer, false);
    }

    public int writeToBuffer(ByteBuffer buffer, boolean large)
    {
        final int length = _length;

        if(length == -1)
        {
            writeFirstPass(buffer, (large || getCount() > LARGE_COMPOUND_THRESHOLD_COUNT) ? 4 : 1);
            if(_delegate != null && _delegate.isComplete())
            {
                _delegate = null;
            }
        }
        else
        {
            //

            final int size = (length & 0xFFFFFF00) == 0 ? 1 : 4;
            final int count = getCount();
            final int typeLength = length - (1+size);

            State state = _state;

            switch(state)
            {
                case FORMAT_CODE:
                    if(buffer.hasRemaining())
                    {
                        buffer.put(size == 1 ? getSingleOctetEncodingCode(): getFourOctetEncodingCode());
                        state = State.SIZE_0;
                    }
                    else
                    {
                        break;
                    }

                case SIZE_0:
                    if(size == 4)
                    {
                        if(buffer.remaining()>=4)
                        {
                            buffer.putInt(typeLength);
                            state = State.COUNT_0;
                        }
                    }
                    else if(size == 1)
                    {
                        if(buffer.hasRemaining())
                        {
                            buffer.put((byte)(typeLength));
                            state = State.COUNT_0;
                        }
                        else
                        {
                            break;
                        }

                    }
                case SIZE_1:
                case SIZE_2:
                    if(state != State.COUNT_0 && buffer.remaining() >= 2)
                    {
                        buffer.putShort((short)(((typeLength) >> ((3-state.ordinal())<<3)) & 0xFFFF ));
                        state = (state == State.SIZE_0)
                                 ? State.SIZE_2
                                 : (state == State.SIZE_1)
                                    ? State.SIZE_3
                                    : State.COUNT_0;
                    }
                case SIZE_3:
                    if(state != State.COUNT_0 && buffer.hasRemaining())
                    {
                        buffer.put((byte)(((typeLength) >> ((4-state.ordinal())<<3)) & 0xFF ));
                        state = (state == State.SIZE_0)
                                 ? State.SIZE_1
                                 : (state == State.SIZE_1)
                                    ? State.SIZE_2
                                    : (state == State.SIZE_2)
                                      ? State.SIZE_3
                                      : State.COUNT_0;
                    }
                case COUNT_0:
                    if(size == 4)
                    {
                        if(buffer.remaining()>=4)
                        {
                            buffer.putInt(count);
                            state = State.DELEGATING;
                        }
                    }
                    else if(size == 1)
                    {
                        if(buffer.hasRemaining())
                        {
                            buffer.put((byte)count);
                            state = State.DELEGATING;
                        }
                        else
                        {
                            break;
                        }

                    }

                case COUNT_1:
                case COUNT_2:
                    if(state != State.DELEGATING && buffer.remaining() >= 2)
                    {
                        buffer.putShort((short)((count >> ((7-state.ordinal())<<3)) & 0xFFFF ));
                        state = state == State.COUNT_0
                                 ? State.COUNT_2
                                 : state == State.COUNT_1
                                    ? State.COUNT_3
                                    : State.DELEGATING;
                    }
                case COUNT_3:
                    if(state != State.DELEGATING && buffer.hasRemaining())
                    {
                        buffer.put((byte)((count >> ((8-state.ordinal())<<3)) & 0xFF ));
                        state = state == State.COUNT_0
                                 ? State.COUNT_1
                                 : state == State.COUNT_1
                                    ? State.COUNT_2
                                    : state == State.COUNT_2
                                       ? State.COUNT_3
                                       : State.DELEGATING;
                    }
                case DELEGATING:
                    while(state == State.DELEGATING && buffer.hasRemaining())
                    {
                        if(_delegate == null || _delegate.isComplete())
                        {
                            if(hasNext())
                            {
                                Object val = next();
                                _delegate = _registry.getValueWriter(val);
                            }
                            else
                            {
                                state = State.DONE;
                                break;
                            }
                        }
                        _delegate.writeToBuffer(buffer);
                    }
            }

            _state = state;

        }

        return _length;
    }

    private void writeFirstPass(ByteBuffer buffer, int size)
    {

        State state = State.FORMAT_CODE;
        /*ByteBuffer origBuffer = buffer;
        buffer = buffer.duplicate();*/
        int origPosition = buffer.position();
        int length ;


        if(size == 4)
        {
            if(buffer.hasRemaining())
            {
                buffer.put(getFourOctetEncodingCode());

                // Skip the size - we will come back and patch this
                if(buffer.remaining() >= 4 )
                {
                    buffer.position(buffer.position()+4);
                    state = State.COUNT_0;
                }
                else
                {
                    state = State.values()[buffer.remaining()+1];
                    buffer.position(buffer.limit());
                }


                switch(buffer.remaining())
                {
                    case 0:
                        break;
                    case 1:
                        buffer.put((byte)((getCount() >> 24) & 0xFF));
                        state = State.COUNT_1;
                        break;
                    case 2:
                        buffer.putShort((short)((getCount() >> 16) & 0xFFFF));
                        state = State.COUNT_2;
                        break;
                    case 3:
                        buffer.putShort((short)((getCount() >> 16) & 0xFFFF));
                        buffer.put((byte)((getCount() >> 8) & 0xFF));
                        state = State.COUNT_3;
                        break;
                    default:
                        buffer.putInt(getCount());
                        state = State.DELEGATING;
                }



            }
            length = 9;



        }
        else
        {
            if(buffer.hasRemaining())
            {
                buffer.put(getSingleOctetEncodingCode());
                if(buffer.hasRemaining())
                {
                    // Size - we will come back and patch this
                    buffer.put((byte) 0);

                    if(buffer.hasRemaining())
                    {
                        buffer.put((byte)getCount());
                        state = State.DELEGATING;
                    }
                    else
                    {
                        state = State.COUNT_0;
                    }
                }
                else
                {
                    state = State.SIZE_0;
                }
            }
            length = 3;

        }


        int iterPos = -1;
        for(int i = 0; i < getCount(); i++)
        {
            Object val = next();
            ValueWriter writer = _registry.getValueWriter(val);
            if(writer == null)
            {
                // TODO
                System.out.println("no writer for " + val);
            }
            length += writer.writeToBuffer(buffer);
            if(iterPos == -1 && !writer.isComplete())
            {
                iterPos = i;
                _delegate = writer;
            }

            if(size == 1 && length > 255)
            {
                reset();
                buffer.position(origPosition);
                writeFirstPass(buffer, 4);
                return;
            }

        }

        // TODO - back-patch size
        if(buffer.limit() - origPosition >= 2)
        {
            buffer.position(origPosition+1);
            if(size == 1)
            {
                buffer.put((byte)((length & 0xFF)-2));
            }
            else
            {
                switch(buffer.remaining())
                {
                    case 1:
                        buffer.put((byte)(((length-5) >> 24) & 0xFF));
                        break;
                    case 2:
                        buffer.putShort((short)(((length-5) >> 16) & 0xFFFF));
                        break;
                    case 3:
                        buffer.putShort((short)(((length-5) >> 16) & 0xFFFF));
                        buffer.put((byte)(((length-5) >> 8) & 0xFF));
                        break;
                    default:
                        buffer.putInt(length-5);
                }
            }
        }

        if(buffer.limit() - origPosition >= length)
        {
            buffer.position(origPosition+length);
            state = State.DONE;
        }
        else
        {
            reset();
            while(iterPos-- >= 0)
            {
                next();
            }
            buffer.position(buffer.limit());
        }
        _state = state;
        _length = length;
    }

    protected abstract byte getFourOctetEncodingCode();

    protected abstract byte getSingleOctetEncodingCode();

    public void setValue(V value)
    {
        _length = -1;
        _delegate = null;
        _state = State.FORMAT_CODE;
        onSetValue(value);
    }

    public void setRegistry(Registry registry)
    {
        _registry = registry;
    }

    public Registry getRegistry()
    {
        return _registry;
    }

    protected abstract void onSetValue(final V value);

    protected abstract int getCount();

    protected abstract boolean hasNext();

    protected abstract Object next();

    protected abstract void clear();

    protected abstract void reset();

    public boolean isCacheable()
    {
        return false;
    }

    public boolean isComplete()
    {
        return _state == State.DONE;
    }
}