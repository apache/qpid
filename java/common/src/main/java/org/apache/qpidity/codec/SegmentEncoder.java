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
package org.apache.qpidity.codec;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.qpidity.Frame;
import org.apache.qpidity.Handler;

import static java.lang.Math.*;

import static org.apache.qpidity.Frame.*;


/**
 * SegmentEncoder
 *
 * @author Rafael H. Schloming
 */

public class SegmentEncoder extends AbstractEncoder
{

    private final Handler<ByteBuffer> handler;
    private final int max;
    private final byte flags;
    private final byte track;
    private final byte type;
    private final int channel;

    private int remaining;
    private ByteBuffer frame;
    private boolean first;

    public SegmentEncoder(byte major, byte minor, Handler<ByteBuffer> handler,
                          int max, byte flags, byte track, byte type,
                          int channel, int remaining)
    {
        super(major, minor);
        if (max < HEADER_SIZE + 1)
        {
            throw new IllegalArgumentException
                ("max frame size must be large enough to include header");
        }

        this.handler = handler;
        this.max = max;
        this.flags = flags;
        this.track = track;
        this.type = type;
        this.channel = channel;
        this.remaining = remaining;
        this.frame = null;
        this.first = true;
    }

    private void preWrite() {
        if (remaining == 0)
        {
            throw new BufferOverflowException();
        }

        if (frame == null)
        {
            frame = ByteBuffer.allocate(min(max, remaining + HEADER_SIZE));
            frame.order(ByteOrder.BIG_ENDIAN);

            byte frameFlags = flags;
            if (first) { frameFlags |= FIRST_FRAME; first = false; }
            if (remaining <= (frame.remaining() - HEADER_SIZE))
            {
                frameFlags |= LAST_FRAME;
            }

            frame.put(frameFlags);
            frame.put(type);
            frame.putShort((short) frame.limit());
            frame.put(RESERVED);
            frame.put(track);
            frame.putShort((short) channel);
            frame.put(RESERVED);
            frame.put(RESERVED);
            frame.put(RESERVED);
            frame.put(RESERVED);

            assert frame.position() == HEADER_SIZE;
        }
    }

    private void postWrite() {
        if (!frame.hasRemaining())
        {
            frame.flip();
            handler.handle(frame);
            frame = null;
        }
    }

    @Override public void put(byte b)
    {
        preWrite();
        frame.put(b);
        remaining -= 1;
        postWrite();
    }

    @Override public void put(ByteBuffer src)
    {
        if (src.remaining() > remaining)
        {
            throw new BufferOverflowException();
        }

        while (src.hasRemaining())
        {
            preWrite();
            int limit = src.limit();
            src.limit(src.position() + min(frame.remaining(), src.remaining()));
            remaining -= src.remaining();
            frame.put(src);
            src.limit(limit);
            postWrite();
        }
    }

    public static final void main(String[] args) {
        ByteBuffer buf = ByteBuffer.allocate(1024);
        buf.put("AMQP_PROTOCOL_HEADER".getBytes());
        buf.flip();

        SegmentEncoder enc = new SegmentEncoder((byte) 0, (byte) 10,
                                                new Handler<ByteBuffer>()
                                                {
                                                    public void handle(ByteBuffer frame)
                                                    {
                                                        System.out.println(frame);
                                                    }
                                                },
                                                16,
                                                (byte) 0x0,
                                                (byte) Frame.L1,
                                                (byte) Frame.METHOD,
                                                0,
                                                7 + buf.remaining());
        enc.put((byte)0);
        enc.put((byte)1);
        enc.put((byte)2);
        enc.put((byte)3);
        enc.put((byte)4);
        enc.put((byte)5);
        enc.put((byte)6);
        enc.put(buf);
    }

}
