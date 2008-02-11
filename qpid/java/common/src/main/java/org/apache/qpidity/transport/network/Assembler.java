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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.nio.ByteBuffer;

import org.apache.qpidity.transport.codec.FragmentDecoder;

import org.apache.qpidity.transport.ConnectionEvent;
import org.apache.qpidity.transport.Data;
import org.apache.qpidity.transport.Header;
import org.apache.qpidity.transport.Method;
import org.apache.qpidity.transport.ProtocolError;
import org.apache.qpidity.transport.ProtocolEvent;
import org.apache.qpidity.transport.ProtocolHeader;
import org.apache.qpidity.transport.Receiver;
import org.apache.qpidity.transport.Struct;


/**
 * Assembler
 *
 */

public class Assembler implements Receiver<NetworkEvent>, NetworkDelegate
{

    private final Receiver<ConnectionEvent> receiver;
    private final Map<Integer,List<ByteBuffer>> segments;

    public Assembler(Receiver<ConnectionEvent> receiver)
    {
        this.receiver = receiver;
        segments = new HashMap<Integer,List<ByteBuffer>>();
    }

    private int segmentKey(Frame frame)
    {
        // XXX: can this overflow?
        return (frame.getTrack() + 1) * frame.getChannel();
    }

    private List<ByteBuffer> getSegment(Frame frame)
    {
        return segments.get(segmentKey(frame));
    }

    private void setSegment(Frame frame, List<ByteBuffer> segment)
    {
        int key = segmentKey(frame);
        if (segments.containsKey(key))
        {
            error(new ProtocolError(Frame.L2, "segment in progress: %s",
                                    frame));
        }
        segments.put(segmentKey(frame), segment);
    }

    private void clearSegment(Frame frame)
    {
        segments.remove(segmentKey(frame));
    }

    private void emit(int channel, ProtocolEvent event)
    {
        receiver.received(new ConnectionEvent(channel, event));
    }

    private void emit(Frame frame, ProtocolEvent event)
    {
        emit(frame.getChannel(), event);
    }

    public void received(NetworkEvent event)
    {
        event.delegate(this);
    }

    public void exception(Throwable t)
    {
        this.receiver.exception(t);
    }

    public void closed()
    {
        this.receiver.closed();
    }

    public void init(ProtocolHeader header)
    {
        emit(0, header);
    }

    public void frame(Frame frame)
    {
        switch (frame.getType())
        {
        case Frame.BODY:
            emit(frame, new Data(frame, frame.isFirstFrame(),
                                 frame.isLastFrame()));
            break;
        default:
            assemble(frame);
            break;
        }
    }

    public void error(ProtocolError error)
    {
        emit(0, error);
    }

    private void assemble(Frame frame)
    {
        List<ByteBuffer> segment;
        if (frame.isFirstFrame())
        {
            segment = new ArrayList<ByteBuffer>();
            setSegment(frame, segment);
        }
        else
        {
            segment = getSegment(frame);
        }

        for (ByteBuffer buf : frame)
        {
            segment.add(buf);
        }

        if (frame.isLastFrame())
        {
            clearSegment(frame);
            emit(frame, decode(frame.getType(), segment));
        }
    }

    private ProtocolEvent decode(byte type, List<ByteBuffer> segment)
    {
        FragmentDecoder dec = new FragmentDecoder(segment.iterator());

        switch (type)
        {
        case Frame.METHOD:
            int methodType = dec.readShort();
            Method method = Method.create(methodType);
            method.read(dec);
            return method;
        case Frame.HEADER:
            List<Struct> structs = new ArrayList();
            while (dec.hasRemaining())
            {
                structs.add(dec.readLongStruct());
            }
            return new Header(structs);
        default:
            throw new IllegalStateException("unknown frame type: " + type);
        }
    }

}
