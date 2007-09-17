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

import org.apache.qpidity.codec.BBEncoder;
import org.apache.qpidity.codec.SizeEncoder;

import org.apache.qpidity.transport.ConnectionEvent;
import org.apache.qpidity.transport.Data;
import org.apache.qpidity.transport.Header;
import org.apache.qpidity.transport.Method;
import org.apache.qpidity.transport.ProtocolDelegate;
import org.apache.qpidity.transport.ProtocolError;
import org.apache.qpidity.transport.ProtocolEvent;
import org.apache.qpidity.transport.ProtocolHeader;
import org.apache.qpidity.transport.Sender;
import org.apache.qpidity.transport.Struct;

import java.nio.ByteBuffer;

import static org.apache.qpidity.transport.network.Frame.*;

import static java.lang.Math.*;


/**
 * Disassembler
 *
 */

public class Disassembler implements Sender<ConnectionEvent>,
                                     ProtocolDelegate<ConnectionEvent>
{

    private final Sender<NetworkEvent> sender;
    private final int maxFrame;
    private final byte major;
    private final byte minor;

    public Disassembler(Sender<NetworkEvent> sender, byte major, byte minor,
                        int maxFrame)
    {
        this.sender = sender;
        this.major = major;
        this.minor = minor;
        this.maxFrame = maxFrame;
    }

    public void send(ConnectionEvent event)
    {
        event.getProtocolEvent().delegate(event, this);
    }

    public void close()
    {
        sender.close();
    }

    private void fragment(byte flags, byte type, ConnectionEvent event,
                          ByteBuffer buf, boolean first, boolean last)
    {
        while (buf.hasRemaining())
        {
            ByteBuffer slice = buf.slice();
            slice.limit(min(maxFrame, slice.remaining()));
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

            Frame frame = new Frame(newflags, type,
                                    event.getProtocolEvent().getEncodedTrack(),
                                    event.getChannel());
            frame.addFragment(slice);
            sender.send(frame);
        }
    }

    public void init(ConnectionEvent event, ProtocolHeader header)
    {
        sender.send(header);
    }

    public void method(ConnectionEvent event, Method method)
    {
        SizeEncoder sizer = new SizeEncoder(major, minor);
        sizer.writeLong(method.getEncodedType());
        method.write(sizer, major, minor);
        sizer.flush();
        int size = sizer.getSize();

        ByteBuffer buf = ByteBuffer.allocate(size);
        BBEncoder enc = new BBEncoder(major, minor, buf);
        enc.writeLong(method.getEncodedType());
        method.write(enc, major, minor);
        enc.flush();
        buf.flip();

        byte flags = FIRST_SEG;

        if (!method.hasPayload())
        {
            flags |= LAST_SEG;
        }

        fragment(flags, METHOD, event, buf, true, true);
    }

    public void header(ConnectionEvent event, Header header)
    {
        SizeEncoder sizer = new SizeEncoder(major, minor);
        for (Struct st : header.getStructs())
        {
            sizer.writeLongStruct(st);
        }

        ByteBuffer buf = ByteBuffer.allocate(sizer.getSize());
        BBEncoder enc = new BBEncoder(major, minor, buf);
        for (Struct st : header.getStructs())
        {
            enc.writeLongStruct(st);
            enc.flush();
        }
        buf.flip();

        fragment((byte) 0x0, HEADER, event, buf, true, true);
    }

    public void data(ConnectionEvent event, Data data)
    {
        for (ByteBuffer buf : data.getFragments())
        {
            fragment(LAST_SEG, BODY, event, buf, data.isFirst(),
                     data.isLast());
        }
    }

    public void error(ConnectionEvent event, ProtocolError error)
    {
        sender.send(error);
    }

}
