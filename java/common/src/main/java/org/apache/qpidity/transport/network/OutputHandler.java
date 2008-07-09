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

import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.List;

import org.apache.qpidity.transport.Constant;
import org.apache.qpidity.transport.ProtocolError;
import org.apache.qpidity.transport.ProtocolHeader;
import org.apache.qpidity.transport.Sender;

import static org.apache.qpidity.transport.network.Frame.*;


/**
 * OutputHandler
 *
 */

public class OutputHandler implements Sender<NetworkEvent>, NetworkDelegate
{

    private Sender<ByteBuffer> sender;
    private Object lock = new Object();
    private int bytes = 0;
    private List<Frame> frames = new ArrayList<Frame>();

    public OutputHandler(Sender<ByteBuffer> sender)
    {
        this.sender = sender;
    }

    public void send(NetworkEvent event)
    {
        event.delegate(this);
    }

    public void close()
    {
        synchronized (lock)
        {
            sender.close();
        }
    }

    public void init(ProtocolHeader header)
    {
        synchronized (lock)
        {
            sender.send(header.toByteBuffer());
            sender.flush();
        }
    }

    public void frame(Frame frame)
    {
        synchronized (lock)
        {
            frames.add(frame);
            bytes += HEADER_SIZE + frame.getSize();

            if (bytes > 64*1024)
            {
                flush();
            }
        }
    }

    public void flush()
    {
        synchronized (lock)
        {
            ByteBuffer buf = ByteBuffer.allocate(bytes);
            int nframes = frames.size();
            for (int i = 0; i < nframes; i++)
            {
                Frame frame = frames.get(i);
                buf.put(frame.getFlags());
                buf.put((byte) frame.getType().getValue());
                buf.putShort((short) (frame.getSize() + HEADER_SIZE));
                // RESERVED
                buf.put(RESERVED);
                buf.put(frame.getTrack());
                buf.putShort((short) frame.getChannel());
                // RESERVED
                buf.putInt(0);
                buf.put(frame.getBody());
            }
            buf.flip();

            frames.clear();
            bytes = 0;

            sender.send(buf);
            sender.flush();
        }
    }

    public void error(ProtocolError error)
    {
        throw new IllegalStateException("XXX");
    }

}
