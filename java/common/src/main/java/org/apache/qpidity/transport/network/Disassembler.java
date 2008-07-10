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
package org.apache.qpidity.transport.network;

import org.apache.qpidity.transport.codec.BBEncoder;

import org.apache.qpidity.transport.Data;
import org.apache.qpidity.transport.Header;
import org.apache.qpidity.transport.Method;
import org.apache.qpidity.transport.ProtocolDelegate;
import org.apache.qpidity.transport.ProtocolError;
import org.apache.qpidity.transport.ProtocolEvent;
import org.apache.qpidity.transport.ProtocolHeader;
import org.apache.qpidity.transport.SegmentType;
import org.apache.qpidity.transport.Sender;
import org.apache.qpidity.transport.Struct;

import java.nio.ByteBuffer;
import java.util.Iterator;

import static org.apache.qpidity.transport.network.Frame.*;

import static java.lang.Math.*;


/**
 * Disassembler
 *
 */

public class Disassembler implements Sender<ProtocolEvent>,
                                     ProtocolDelegate<Void>
{

    private final Sender<NetworkEvent> sender;
    private final int maxPayload;
    private final ThreadLocal<BBEncoder> encoder = new ThreadLocal()
    {
        public BBEncoder initialValue()
        {
            return new BBEncoder(4*1024);
        }
    };

    public Disassembler(Sender<NetworkEvent> sender, int maxFrame)
    {
        if (maxFrame <= HEADER_SIZE || maxFrame >= 64*1024)
        {
            throw new IllegalArgumentException
                ("maxFrame must be > HEADER_SIZE and < 64K: " + maxFrame);
        }
        this.sender = sender;
        this.maxPayload  = maxFrame - HEADER_SIZE;

    }

    public void send(ProtocolEvent event)
    {
        event.delegate(null, this);
    }

    public void flush()
    {
        sender.flush();
    }

    public void close()
    {
        sender.close();
    }

    private void fragment(byte flags, SegmentType type, ProtocolEvent event,
                          ByteBuffer buf, boolean first, boolean last)
    {
        byte track = event.getEncodedTrack() == Frame.L4 ? (byte) 1 : (byte) 0;

        if(!buf.hasRemaining())
        {
            //empty data
            byte nflags = flags;
            if (first)
            {
                nflags |= FIRST_FRAME;
                first = false;
            }
            nflags |= LAST_FRAME;
            Frame frame = new Frame(nflags, type, track, event.getChannel(), buf.slice());
            sender.send(frame);
        }
        else
        {
            while (buf.hasRemaining())
            {
                ByteBuffer slice = buf.slice();
                slice.limit(min(maxPayload, slice.remaining()));
                buf.position(buf.position() + slice.remaining());

                byte newflags = flags;
                if (first)
                {
                    newflags |= FIRST_FRAME;
                    first = false;
                }
                if (last && !buf.hasRemaining())
                {
                    newflags |= LAST_FRAME;
                }

                Frame frame = new Frame(newflags, type, track, event.getChannel(), slice);
                sender.send(frame);
            }
        }
    }

    public void init(Void v, ProtocolHeader header)
    {
        sender.send(header);
    }

    public void control(Void v, Method method)
    {
        method(method, SegmentType.CONTROL);
    }

    public void command(Void v, Method method)
    {
        method(method, SegmentType.COMMAND);
    }

    private ByteBuffer copy(ByteBuffer src)
    {
        ByteBuffer buf = ByteBuffer.allocate(src.remaining());
        buf.put(src);
        buf.flip();
        return buf;
    }

    private void method(Method method, SegmentType type)
    {
        BBEncoder enc = encoder.get();
        enc.init();
        enc.writeUint16(method.getEncodedType());
        if (type == SegmentType.COMMAND)
        {
            if (method.isSync())
            {
                enc.writeUint16(0x0101);
            }
            else
            {
                enc.writeUint16(0x0100);
            }
        }
        method.write(enc);
        ByteBuffer buf = enc.done();

        byte flags = FIRST_SEG;

        if (!method.hasPayload())
        {
            flags |= LAST_SEG;
        }

        fragment(flags, type, method, buf, true, true);
    }

    public void header(Void v, Header header)
    {
        ByteBuffer buf;
        if (header.getBuf() == null)
        {
            BBEncoder enc = encoder.get();
            enc.init();
            for (Struct st : header.getStructs())
            {
                enc.writeStruct32(st);
            }
            buf = enc.done();
            header.setBuf(buf);
        }
        else
        {
            buf = header.getBuf();
            buf.flip();
        }
        fragment((byte) 0x0, SegmentType.HEADER, header, buf, true, true);
    }

    public void data(Void v, Data data)
    {
        fragment(LAST_SEG, SegmentType.BODY, data, data.getData(), data.isFirst(), data.isLast());
    }

    public void error(Void v, ProtocolError error)
    {
        sender.send(error);
    }

}
