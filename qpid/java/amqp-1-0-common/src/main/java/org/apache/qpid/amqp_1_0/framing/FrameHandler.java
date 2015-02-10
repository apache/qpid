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
package org.apache.qpid.amqp_1_0.framing;

import java.nio.ByteBuffer;
import java.util.Formatter;

import org.apache.qpid.amqp_1_0.codec.ProtocolHandler;
import org.apache.qpid.amqp_1_0.codec.ValueHandler;
import org.apache.qpid.amqp_1_0.transport.ConnectionEndpoint;
import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.ErrorCondition;
import org.apache.qpid.amqp_1_0.type.transport.ConnectionError;
import org.apache.qpid.amqp_1_0.type.transport.Error;
import org.apache.qpid.amqp_1_0.type.transport.Transfer;

public class FrameHandler implements ProtocolHandler
{
    private ConnectionEndpoint _connection;
    private ValueHandler _typeHandler;

    enum State {
        SIZE_0,
        SIZE_1,
        SIZE_2,
        SIZE_3,
        PRE_PARSE,
        BUFFERING,
        PARSING,
        ERROR
    }

    private State _state = State.SIZE_0;
    private int _size;

    private ByteBuffer _buffer;



    public FrameHandler(final ConnectionEndpoint connection)
    {
        _connection = connection;
        _typeHandler = new ValueHandler(connection.getDescribedTypeRegistry());

    }

    public ProtocolHandler parse(ByteBuffer in)
    {
        try
        {
        Error frameParsingError = null;
        int size = _size;
        State state = _state;
        ByteBuffer oldIn = null;

        while(in.hasRemaining() && state != State.ERROR)
        {

            final int remaining = in.remaining();
            if(remaining == 0)
            {
                return this;
            }


            switch(state)
            {
                case SIZE_0:
                    if(remaining >= 4)
                    {
                        size = in.getInt();
                        state = State.PRE_PARSE;
                        break;
                    }
                    else
                    {
                        size = (in.get() << 24) & 0xFF000000;
                        if(!in.hasRemaining())
                        {
                            state = State.SIZE_1;
                            break;
                        }
                    }
                case SIZE_1:
                    size |= (in.get() << 16) & 0xFF0000;
                    if(!in.hasRemaining())
                    {
                        state = State.SIZE_2;
                        break;
                    }
                case SIZE_2:
                    size |= (in.get() << 8) & 0xFF00;
                    if(!in.hasRemaining())
                    {
                        state = State.SIZE_3;
                        break;
                    }
                case SIZE_3:
                    size |= in.get() & 0xFF;
                    state = State.PRE_PARSE;

                case PRE_PARSE:

                    if(size < 8)
                    {
                        frameParsingError = createFramingError("specified frame size %d smaller than minimum frame header size %d", size, 8);
                        state = State.ERROR;
                        break;
                    }

                    else if(size > _connection.getDesiredMaxFrameSize().intValue())
                    {
                        frameParsingError = createFramingError("specified frame size %d larger than maximum frame header size %d", size, _connection.getDesiredMaxFrameSize().intValue());
                        state = State.ERROR;
                        break;
                    }

                    if(in.remaining() < size-4)
                    {
                        _buffer = ByteBuffer.allocate(size-4);
                        _buffer.put(in);
                        state = State.BUFFERING;
                        break;
                    }
                case BUFFERING:
                    if(_buffer != null)
                    {
                        if(in.remaining() < _buffer.remaining())
                        {
                            _buffer.put(in);
                            break;
                        }
                        else
                        {
                            ByteBuffer dup = in.duplicate();
                            dup.limit(dup.position()+_buffer.remaining());
                            int i = _buffer.remaining();
                            int d = dup.remaining();
                            in.position(in.position()+_buffer.remaining());
                            _buffer.put(dup);
                            oldIn = in;
                            _buffer.flip();
                            in = _buffer;
                            state = State.PARSING;
                        }
                    }

                case PARSING:

                    int dataOffset = (in.get() << 2) & 0x3FF;

                    if(dataOffset < 8)
                    {
                        frameParsingError = createFramingError("specified frame data offset %d smaller than minimum frame header size %d", dataOffset, 8);
                        state = State.ERROR;
                        break;
                    }
                    else if(dataOffset > size)
                    {
                        frameParsingError = createFramingError("specified frame data offset %d larger than the frame size %d", dataOffset, _size);
                        state = State.ERROR;
                        break;
                    }

                    // type

                    int type = in.get() & 0xFF;
                    int channel = in.getShort() & 0xFFFF;

                    if(type != 0 && type != 1)
                    {
                        frameParsingError = createFramingError("unknown frame type: %d", type);
                        state = State.ERROR;
                        break;
                    }

                    // channel

                    /*if(channel > _connection.getChannelMax())
                    {
                        frameParsingError = createError(AmqpError.DECODE_ERROR,
                                                        "frame received on invalid channel %d above channel-max %d",
                                                        channel, _connection.getChannelMax());

                        state = State.ERROR;
                    }
*/
                    // ext header
                    if(dataOffset!=8)
                    {
                        in.position(in.position()+dataOffset-8);
                    }

                    // oldIn null iff not working on duplicated buffer
                    if(oldIn == null)
                    {
                        oldIn = in;
                        in = in.duplicate();
                        final int endPos = in.position() + size - dataOffset;
                        in.limit(endPos);
                        oldIn.position(endPos);

                    }

                    int inPos = in.position();
                    int inLimit = in.limit();
                    // PARSE HERE
                    try
                    {
                        Object val = in.hasRemaining() ? _typeHandler.parse(in) : null;
                        
                        if(in.hasRemaining())
                        {
                            if(val instanceof Transfer)
                            {
                                ByteBuffer buf = ByteBuffer.allocate(in.remaining());
                                buf.put(in);
                                buf.flip();
                                ((Transfer)val).setPayload(buf);
                            }
                        }

                        _connection.receive((short)channel,val);
                        reset();
                        in = oldIn;
                        oldIn = null;
                        _buffer = null;
                        state = State.SIZE_0;
                        break;


                    }
                    catch (AmqpErrorException ex)
                    {
                        state = State.ERROR;
                        frameParsingError = ex.getError();
                    }
                    catch (RuntimeException e)
                    {
                        in.position(inPos);
                        in.limit(inLimit);
                        System.err.println(toHex(in));
                        throw e;
                    }
            }

        }

        _state = state;
        _size = size;

        if(_state == State.ERROR)
        {
            _connection.handleError(frameParsingError);
        }
        }
        catch(RuntimeException e)
        {
            // This exception is unexpected. The up layer should handle error condition gracefully
            _connection.handleError(this.createError(ConnectionError.CONNECTION_FORCED, e.toString()));
            e.printStackTrace();
        }
        return this;
    }

    private static String toHex(ByteBuffer in)
    {
        Formatter formatter = new Formatter();
        int count = 0;
        while(in.hasRemaining())
        {
            formatter.format("%02x ", in.get() & 0xFF);
            if(count++ == 16)
            {
                formatter.format("\n");
                count = 0;
            }

        }
        return formatter.toString();
    }

    private Error createFramingError(String description, Object... args)
    {
        return createError(ConnectionError.FRAMING_ERROR, description, args);
    }

    private Error createError(final ErrorCondition framingError,
                              final String description,
                              final Object... args)
    {
        Error error = new Error();
        error.setCondition(framingError);
        Formatter formatter = new Formatter();
        error.setDescription(formatter.format(description, args).toString());
        return error;
    }


    private void reset()
    {
        _size = 0;
        _state = State.SIZE_0;
    }


    public boolean isDone()
    {
        return _state == State.ERROR || _connection.closedForInput();
    }
}
