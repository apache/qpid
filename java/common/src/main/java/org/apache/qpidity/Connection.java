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
package org.apache.qpidity;

import java.util.HashMap;
import java.util.Map;

import java.nio.ByteBuffer;


/**
 * Connection
 *
 * @author Rafael H. Schloming
 *
 * @todo the channels map should probably be replaced with something
 * more efficient, e.g. an array or a map implementation that can use
 * short instead of Short
 */

// RA making this public until we sort out the package issues
public class Connection implements ProtocolActions
{

    final private Handler<ByteBuffer> input;
    final private Handler<ByteBuffer> output;
    final private ConnectionDelegate delegate;

    final private Map<Integer,Channel> channels = new HashMap<Integer,Channel>();
    // XXX: hardcoded versions
    private ProtocolHeader header = new ProtocolHeader((byte) 1, (byte) 0, (byte) 10);
    // XXX
    private int maxFrame = 64*1024;

    public Connection(Handler<ByteBuffer> output,
                      ConnectionDelegate delegate,
                      InputHandler.State state)
    {
        this.input = new InputHandler(this, state);
        this.output = output;
        this.delegate = delegate;
    }

    public ConnectionDelegate getConnectionDelegate()
    {
        return delegate;
    }
    
    public Connection(Handler<ByteBuffer> output,
                      ConnectionDelegate delegate)
    {
        this(output, delegate, InputHandler.State.PROTO_HDR);
    }

    public Handler<ByteBuffer> getInputHandler()
    {
        return input;
    }

    public Handler<ByteBuffer> getOutputHandler()
    {
        return output;
    }

    public ProtocolHeader getHeader()
    {
        return header;
    }

    public byte getMajor()
    {
        return header.getMajor();
    }

    public byte getMinor()
    {
        return header.getMinor();
    }

    public int getMaxFrame()
    {
        return maxFrame;
    }

    public void init(ProtocolHeader hdr)
    {
        System.out.println(header);
        if (hdr.getMajor() != header.getMajor() &&
            hdr.getMinor() != header.getMinor())
        {
            output.handle(header.toByteBuffer());
            // XXX: how do we close the connection?
        }
        
        // not sure if this is the right place
        getChannel(0).connectionStart(header.getMajor(), header.getMinor(), null, "PLAIN", "utf8");
    }

    public Channel getChannel(int number)
    {
        Channel channel = channels.get(number);
        if (channel == null)
        {
            channel = new Channel(this, number, delegate.getSessionDelegate());
            channels.put(number, channel);
        }
        return channel;
    }

    public void frame(Frame frame)
    {
        Channel channel = getChannel(frame.getChannel());
        channel.handle(frame);
    }

    public void error(ProtocolError error)
    {
        throw new RuntimeException(error.getMessage());
    }

}
