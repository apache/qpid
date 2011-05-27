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
package org.apache.qpid.transport.network;

import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.Method;
import org.apache.qpid.transport.ProtocolDelegate;
import org.apache.qpid.transport.ProtocolError;
import org.apache.qpid.transport.ProtocolEvent;
import org.apache.qpid.transport.ProtocolHeader;
import org.apache.qpid.transport.SegmentType;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.Struct;
import org.apache.qpid.transport.codec.BBEncoder;
import static org.apache.qpid.transport.network.Frame.FIRST_FRAME;
import static org.apache.qpid.transport.network.Frame.FIRST_SEG;
import static org.apache.qpid.transport.network.Frame.HEADER_SIZE;
import static org.apache.qpid.transport.network.Frame.LAST_FRAME;
import static org.apache.qpid.transport.network.Frame.LAST_SEG;

import static java.lang.Math.min;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Disassembler
 */
public final class Disassembler implements Sender<ProtocolEvent>, ProtocolDelegate<Void>
{
    private final Sender<ByteBuffer> sender;
    private final int maxPayload;
    private final Object sendlock = new Object();
    private final static ThreadLocal<BBEncoder> _encoder = new ThreadLocal<BBEncoder>()
    {
        public BBEncoder initialValue()
        {
            return new BBEncoder(4*1024);
        }
    };

    public Disassembler(Sender<ByteBuffer> sender, int maxFrame)
    {
        if (maxFrame <= HEADER_SIZE || maxFrame >= 64*1024)
        {
            throw new IllegalArgumentException("maxFrame must be > HEADER_SIZE and < 64K: " + maxFrame);
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
        synchronized (sendlock)
        {
            sender.flush();
        }
    }

    public void close()
    {
        synchronized (sendlock)
        {
            sender.close();
        }
    }

    private void frame(byte flags, byte type, byte track, int channel, int size, ByteBuffer buf)
    {
        synchronized (sendlock)
        {
            ByteBuffer data = ByteBuffer.allocate(size + HEADER_SIZE);
            data.order(ByteOrder.BIG_ENDIAN);
            
            data.put(0, flags);
            data.put(1, type);
            data.putShort(2, (short) (size + HEADER_SIZE));
            data.put(5, track);
            data.putShort(6, (short) channel);
            data.position(HEADER_SIZE);

            int limit = buf.limit();
            buf.limit(buf.position() + size);
            data.put(buf);
            buf.limit(limit);
 
            data.rewind();
            sender.send(data);
        }
    }

    private void fragment(byte flags, SegmentType type, ProtocolEvent event, ByteBuffer buf)
    {
        byte typeb = (byte) type.getValue();
        byte track = event.getEncodedTrack() == Frame.L4 ? (byte) 1 : (byte) 0;

        int remaining = buf.remaining();
        boolean first = true;
        while (true)
        {
            int size = min(maxPayload, remaining);
            remaining -= size;

            byte newflags = flags;
            if (first)
            {
                newflags |= FIRST_FRAME;
                first = false;
            }
            if (remaining == 0)
            {
                newflags |= LAST_FRAME;
            }

            frame(newflags, typeb, track, event.getChannel(), size, buf);

            if (remaining == 0)
            {
                break;
            }
        }
    }

    public void init(Void v, ProtocolHeader header)
    {
        synchronized (sendlock)
        {
            sender.send(header.toByteBuffer());
            sender.flush();
        }
    }

    public void control(Void v, Method method)
    {
        method(method, SegmentType.CONTROL);
    }

    public void command(Void v, Method method)
    {
        method(method, SegmentType.COMMAND);
    }

    private void method(Method method, SegmentType type)
    {
        BBEncoder enc = _encoder.get();
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
        ByteBuffer methodSeg = enc.segment();

        byte flags = FIRST_SEG;

        boolean payload = method.hasPayload();
        if (!payload)
        {
            flags |= LAST_SEG;
        }

        ByteBuffer headerSeg = null;
        if (payload)
        {
            final Header hdr = method.getHeader();
            if (hdr != null)
            {
                final Struct[] structs = hdr.getStructs();

                for (Struct st : structs)
                {
                    enc.writeStruct32(st);
                }
            }
            headerSeg = enc.segment();
        }

        synchronized (sendlock)
        {
            fragment(flags, type, method, methodSeg);
            if (payload)
            {
                ByteBuffer body = method.getBody();
                fragment(body == null ? LAST_SEG : 0x0, SegmentType.HEADER, method, headerSeg);
                if (body != null)
                {
                    fragment(LAST_SEG, SegmentType.BODY, method, body);
                }

            }
        }
    }

    public void error(Void v, ProtocolError error)
    {
        throw new IllegalArgumentException(String.valueOf(error));
    }

    public void setIdleTimeout(int i)
    {
        sender.setIdleTimeout(i);
    }
}
