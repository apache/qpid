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

import org.apache.qpid.amqp_1_0.framing.AMQFrame;

import java.nio.ByteBuffer;

public class FrameWriter implements ValueWriter<AMQFrame>
{
    private Registry _registry;
    private AMQFrame _frame;
    private State _state = State.DONE;
    private ValueWriter _typeWriter;
    private int _size = -1;
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[] {};
    private ByteBuffer _payload;

    enum State
    {
        SIZE_0,
        SIZE_1,
        SIZE_2,
        SIZE_3,
        DOFF,
        TYPE,
        CHANNEL_0,
        CHANNEL_1,
        DELEGATE,
        PAYLOAD,
        DONE
    }

    public FrameWriter(final Registry registry)
    {
        _registry = registry;
    }

    public boolean isComplete()
    {
        return _state == State.DONE;
    }

    public boolean isCacheable()
    {
        return false;
    }

    public int writeToBuffer(ByteBuffer buffer)
    {
        int remaining;



        while((remaining = buffer.remaining()) != 0 && _state != State.DONE)
        {
            switch(_state)
            {
                case SIZE_0:

                    int payloadLength = _payload == null ? 0 : _payload.remaining();

                    if(_typeWriter!=null)
                    {
                        _typeWriter.setValue(_frame.getFrameBody());


                        _size = _typeWriter.writeToBuffer(remaining > 8
                                                          ? (ByteBuffer)buffer.duplicate().position(buffer.position()+8)
                                                          : ByteBuffer.wrap(EMPTY_BYTE_ARRAY)) + 8 + payloadLength;
                    }
                    else
                    {
                        _size = 8 + payloadLength;
                    }
                    if(remaining >= 4)
                    {
                        buffer.putInt(_size);

                        if(remaining >= 8)
                        {
                            buffer.put((byte)2); // DOFF
                            buffer.put(_frame.getFrameType()); // AMQP Frame Type
                            buffer.putShort(_frame.getChannel());

                            if(_size - payloadLength > remaining)
                            {
                                buffer.position(buffer.limit());
                                _state = State.DELEGATE;
                            }
                            else if(_size > remaining )
                            {
                                buffer.position(buffer.position()+_size-8-payloadLength);
                                if(payloadLength > 0)
                                {

                                    ByteBuffer dup = _payload.slice();
                                    int payloadUsed = buffer.remaining();
                                    dup.limit(payloadUsed);
                                    buffer.put(dup);
                                    _payload.position(_payload.position()+payloadUsed);
                                }
                                _state = State.PAYLOAD;
                            }
                            else
                            {

                                buffer.position(buffer.position()+_size-8-payloadLength);
                                if(payloadLength > 0)
                                {
                                    buffer.put(_payload);
                                }
                                _state = State.DONE;
                            }

                        }
                        else
                        {
                            _state = State.DOFF;
                        }
                        break;
                    }
                    else
                    {
                        buffer.put((byte)((_size >> 24) & 0xFF));
                        if(!buffer.hasRemaining())
                        {
                            _state = State.SIZE_1;
                            break;
                        }
                    }

                case SIZE_1:
                    buffer.put((byte)((_size >> 16) & 0xFF));
                    if(!buffer.hasRemaining())
                    {
                        _state = State.SIZE_2;
                        break;
                    }
                case SIZE_2:
                    buffer.put((byte)((_size >> 8) & 0xFF));
                    if(!buffer.hasRemaining())
                    {
                        _state = State.SIZE_3;
                        break;
                    }
                case SIZE_3:
                    buffer.put((byte)(_size & 0xFF));
                    if(!buffer.hasRemaining())
                    {
                        _state = State.DOFF;
                        break;
                    }
                case DOFF:
                    buffer.put((byte)2); // Always 2 (8 bytes)
                    if(!buffer.hasRemaining())
                    {
                        _state = State.TYPE;
                        break;
                    }
                case TYPE:
                    buffer.put((byte)0);
                    if(!buffer.hasRemaining())
                    {
                        _state = State.CHANNEL_0;
                        break;
                    }
                case CHANNEL_0:
                    buffer.put((byte)((_frame.getChannel() >> 8) & 0xFF));
                    if(!buffer.hasRemaining())
                    {
                        _state = State.CHANNEL_1;
                        break;
                    }
                case CHANNEL_1:
                    buffer.put((byte)(_frame.getChannel() & 0xFF));
                    if(!buffer.hasRemaining())
                    {
                        _state = State.DELEGATE;
                        break;
                    }
                case DELEGATE:
                    _typeWriter.writeToBuffer(buffer);
                    if(_typeWriter.isComplete())
                    {
                        _state = State.PAYLOAD;
                        _frame = null;
                        _typeWriter = null;
                    }
                    else
                    {
                        break;
                    }
                case PAYLOAD:
                    if(_payload == null || _payload.remaining() == 0)
                    {
                        _state = State.DONE;
                        _frame = null;
                        _typeWriter = null;
                        _payload = null;

                    }
                    else if(buffer.hasRemaining())
                    {
                        buffer.put(_payload);
                        if(_payload.remaining() == 0)
                        {
                            _state = State.DONE;
                            _frame = null;
                            _typeWriter = null;
                            _payload = null;
                        }
                    }

            }
        }
        if(_size == -1)
        {
            _size =  _typeWriter.writeToBuffer(ByteBuffer.wrap(EMPTY_BYTE_ARRAY)) + 8 + (_payload == null ? 0 : _payload.remaining());
        }
        return _size;
    }

    public void setValue(AMQFrame frame)
    {
        _frame = frame;
        _state = State.SIZE_0;
        _size = -1;
        _payload = null;
        final Object frameBody = frame.getFrameBody();
        if(frameBody!=null)
        {
            _typeWriter = _registry.getValueWriter(frameBody);
        }
        else
        {
            _typeWriter = null;
        }
        _payload = frame.getPayload() == null ? null : frame.getPayload().duplicate();
    }
}
