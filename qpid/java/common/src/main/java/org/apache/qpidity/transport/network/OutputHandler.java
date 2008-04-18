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
        }
    }

    public static final int FRAME_END = 0xCE;

    public void frame(Frame frame)
    {
       ByteBuffer hdr = ByteBuffer.allocate(HEADER_SIZE +   frame.getSize() + 1);
        hdr.put(frame.getFlags());
        hdr.put((byte) frame.getType().getValue());
        hdr.putShort((short) (frame.getSize() + HEADER_SIZE));
        hdr.put(RESERVED);
        hdr.put(frame.getTrack());
        hdr.putShort((short) frame.getChannel());
        hdr.put(RESERVED);
        hdr.put(RESERVED);
        hdr.put(RESERVED);
        hdr.put(RESERVED);
        for(ByteBuffer buf : frame)
        {
            hdr.put(buf);
        }
        hdr.put((byte) FRAME_END);
        hdr.flip();
        synchronized (lock)
        {
            sender.send(hdr);
        }
    }

    public void error(ProtocolError error)
    {
        throw new IllegalStateException("XXX");
    }

}
