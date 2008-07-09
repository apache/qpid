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

import org.apache.qpidity.transport.codec.BBDecoder;
import org.apache.qpidity.transport.codec.Decoder;

import org.apache.qpidity.transport.ConnectionEvent;
import org.apache.qpidity.transport.Data;
import org.apache.qpidity.transport.Header;
import org.apache.qpidity.transport.Method;
import org.apache.qpidity.transport.ProtocolError;
import org.apache.qpidity.transport.ProtocolEvent;
import org.apache.qpidity.transport.ProtocolHeader;
import org.apache.qpidity.transport.Receiver;
import org.apache.qpidity.transport.SegmentType;
import org.apache.qpidity.transport.Struct;


/**
 * Assembler
 *
 */

public class Assembler implements Receiver<NetworkEvent>, NetworkDelegate
{

    private final Receiver<ConnectionEvent> receiver;
    private final Map<Integer,List<Frame>> segments;
    private final ThreadLocal<BBDecoder> decoder = new ThreadLocal<BBDecoder>()
    {
        public BBDecoder initialValue()
        {
            return new BBDecoder();
        }
    };

    public Assembler(Receiver<ConnectionEvent> receiver)
    {
        this.receiver = receiver;
        segments = new HashMap<Integer,List<Frame>>();
    }

    private int segmentKey(Frame frame)
    {
        return (frame.getTrack() + 1) * frame.getChannel();
    }

    private List<Frame> getSegment(Frame frame)
    {
        return segments.get(segmentKey(frame));
    }

    private void setSegment(Frame frame, List<Frame> segment)
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
        case BODY:
            emit(frame, new Data(frame.getBody(), frame.isFirstFrame(),
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
        ByteBuffer segment;
        if (frame.isFirstFrame() && frame.isLastFrame())
        {
            segment = frame.getBody();
            emit(frame, decode(frame, segment));
        }
        else
        {
            List<Frame> frames;
            if (frame.isFirstFrame())
            {
                frames = new ArrayList<Frame>();
                setSegment(frame, frames);
            }
            else
            {
                frames = getSegment(frame);
            }

            frames.add(frame);

            if (frame.isLastFrame())
            {
                clearSegment(frame);

                int size = 0;
                for (Frame f : frames)
                {
                    size += f.getSize();
                }
                segment = ByteBuffer.allocate(size);
                for (Frame f : frames)
                {
                    segment.put(f.getBody());
                }
                segment.flip();
                emit(frame, decode(frame, segment));
            }
        }

    }

    private ProtocolEvent decode(Frame frame, ByteBuffer segment)
    {
        BBDecoder dec = decoder.get();
        dec.init(segment);

        switch (frame.getType())
        {
        case CONTROL:
            int controlType = dec.readUint16();
            Method control = Method.create(controlType);
            control.read(dec);
            return control;
        case COMMAND:
            int commandType = dec.readUint16();
            // read in the session header, right now we don't use it
            dec.readUint16();
            Method command = Method.create(commandType);
            command.read(dec);
            return command;
        case HEADER:
            List<Struct> structs = new ArrayList();
            while (dec.hasRemaining())
            {
                structs.add(dec.readStruct32());
            }
            return new Header(structs, frame.isLastFrame() && frame.isLastSegment());
        default:
            throw new IllegalStateException("unknown frame type: " + frame.getType());
        }
    }

}
