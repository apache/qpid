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
import org.apache.qpidity.transport.codec.SizeEncoder;

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
import java.util.Iterator;

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
    private final int maxPayload;

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
              Frame frame = new Frame(nflags, type,
                                    event.getProtocolEvent().getEncodedTrack(),
                                    event.getChannel());
           // frame.addFragment(buf);
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

            Frame frame = new Frame(newflags, type,
                                    event.getProtocolEvent().getEncodedTrack(),
                                    event.getChannel());
            frame.addFragment(slice);
            sender.send(frame);
        }
        }
    }

    public void init(ConnectionEvent event, ProtocolHeader header)
    {
        sender.send(header);
    }

    public void method(ConnectionEvent event, Method method)
    {
        SizeEncoder sizer = new SizeEncoder();
        sizer.writeShort(method.getEncodedType());
        method.write(sizer);

        ByteBuffer buf = ByteBuffer.allocate(sizer.size());
        BBEncoder enc = new BBEncoder(buf);
        enc.writeShort(method.getEncodedType());
        method.write(enc);
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
         ByteBuffer buf;
        if( header.getBuf() == null)
        {
            SizeEncoder sizer = new SizeEncoder();
            for (Struct st : header.getStructs())
            {
                sizer.writeLongStruct(st);
            }

            buf = ByteBuffer.allocate(sizer.size());
            BBEncoder enc = new BBEncoder(buf);
            for (Struct st : header.getStructs())
            {
                enc.writeLongStruct(st);
                enc.flush();
            }
            header.setBuf(buf);
        }
        else
        {
            buf = header.getBuf();          
        }
          buf.flip();
        fragment((byte) 0x0, HEADER, event, buf, true, true);
    }

    public void data(ConnectionEvent event, Data data)
    {
        boolean first = data.isFirst();
        for (Iterator<ByteBuffer> it = data.getFragments().iterator();
             it.hasNext(); )
        {
            ByteBuffer buf = it.next();
            boolean last = data.isLast() && !it.hasNext();
            fragment(LAST_SEG, BODY, event, buf, first, last);
            first = false;
        }
    }

    public void error(ConnectionEvent event, ProtocolError error)
    {
        sender.send(error);
    }

}
