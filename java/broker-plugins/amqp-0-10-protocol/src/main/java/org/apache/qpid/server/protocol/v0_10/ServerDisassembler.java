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
package org.apache.qpid.server.protocol.v0_10;

import static java.lang.Math.min;
import static org.apache.qpid.transport.network.Frame.FIRST_FRAME;
import static org.apache.qpid.transport.network.Frame.FIRST_SEG;
import static org.apache.qpid.transport.network.Frame.HEADER_SIZE;
import static org.apache.qpid.transport.network.Frame.LAST_FRAME;
import static org.apache.qpid.transport.network.Frame.LAST_SEG;

import java.nio.ByteBuffer;

import org.apache.qpid.transport.ByteBufferSender;
import org.apache.qpid.transport.FrameSizeObserver;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.Method;
import org.apache.qpid.transport.ProtocolDelegate;
import org.apache.qpid.transport.ProtocolError;
import org.apache.qpid.transport.ProtocolEvent;
import org.apache.qpid.transport.ProtocolEventSender;
import org.apache.qpid.transport.ProtocolHeader;
import org.apache.qpid.transport.SegmentType;
import org.apache.qpid.transport.Struct;
import org.apache.qpid.transport.codec.Encoder;
import org.apache.qpid.transport.network.Frame;

/**
 * Disassembler
 */
public final class ServerDisassembler implements ProtocolEventSender, ProtocolDelegate<Void>, FrameSizeObserver
{
    private final ByteBufferSender _sender;
    private int _maxPayload;
    private final Object _sendLock = new Object();
    private final Encoder _encoder =  new ServerEncoder();

    public ServerDisassembler(ByteBufferSender sender, int maxFrame)
    {
        _sender = sender;
        if (maxFrame <= HEADER_SIZE || maxFrame >= 64 * 1024)
        {
            throw new IllegalArgumentException("maxFrame must be > HEADER_SIZE and < 64K: " + maxFrame);
        }
        _maxPayload = maxFrame - HEADER_SIZE;
    }

    public void send(ProtocolEvent event)
    {
        synchronized (_sendLock)
        {
            event.delegate(null, this);
        }
    }

    public void flush()
    {
        synchronized (_sendLock)
        {
            _sender.flush();
        }
    }

    public void close()
    {
        synchronized (_sendLock)
        {
            _sender.close();
        }
    }

    private void frame(byte flags, byte type, byte track, int channel, int size, ByteBuffer buf)
    {
        ByteBuffer data = ByteBuffer.wrap(new byte[HEADER_SIZE]);

        data.put(0, flags);
        data.put(1, type);
        data.putShort(2, (short) (size + HEADER_SIZE));
        data.put(5, track);
        data.putShort(6, (short) channel);


        ByteBuffer dup = buf.duplicate();
        dup.limit(dup.position() + size);
        buf.position(buf.position() + size);
        _sender.send(data);
        _sender.send(dup);


    }

    private void fragment(byte flags, SegmentType type, ProtocolEvent event, ByteBuffer buf)
    {
        byte typeb = (byte) type.getValue();
        byte track = event.getEncodedTrack() == Frame.L4 ? (byte) 1 : (byte) 0;

        int remaining = buf.remaining();
        boolean first = true;
        while (true)
        {
            int size = min(_maxPayload, remaining);
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
        _sender.send(header.toByteBuffer());
        _sender.flush();
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
        Encoder enc = _encoder;
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
        int methodLimit = enc.position();

        byte flags = FIRST_SEG;

        boolean payload = method.hasPayload();
        if (!payload)
        {
            flags |= LAST_SEG;
        }

        int headerLimit = -1;
        if (payload)
        {
            final Header hdr = method.getHeader();
            if (hdr != null)
            {
                if (hdr.getDeliveryProperties() != null)
                {
                    enc.writeStruct32(hdr.getDeliveryProperties());
                }
                if (hdr.getMessageProperties() != null)
                {
                    enc.writeStruct32(hdr.getMessageProperties());
                }
                if (hdr.getNonStandardProperties() != null)
                {
                    for (Struct st : hdr.getNonStandardProperties())
                    {
                        enc.writeStruct32(st);
                    }
                }
            }

            headerLimit = enc.position();
        }
        synchronized (_sendLock)
        {
            ByteBuffer buf = enc.underlyingBuffer();
            buf.position(0);
            buf.limit(methodLimit);

            fragment(flags, type, method, buf.duplicate());
            if (payload)
            {
                ByteBuffer body = method.getBody();
                buf.limit(headerLimit);
                buf.position(methodLimit);

                fragment(body == null ? LAST_SEG : 0x0, SegmentType.HEADER, method, buf.duplicate());
                if (body != null)
                {
                    fragment(LAST_SEG, SegmentType.BODY, method, body.duplicate());
                }

            }
        }
    }

    public void error(Void v, ProtocolError error)
    {
        throw new IllegalArgumentException(String.valueOf(error));
    }

    @Override
    public void setMaxFrameSize(final int maxFrame)
    {
        if (maxFrame <= HEADER_SIZE || maxFrame >= 64*1024)
        {
            throw new IllegalArgumentException("maxFrame must be > HEADER_SIZE and < 64K: " + maxFrame);
        }
        this._maxPayload = maxFrame - HEADER_SIZE;

    }
}
