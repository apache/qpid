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

class Connection implements ProtocolActions
{

    final private Handler<ByteBuffer> input;
    final private Handler<ByteBuffer> output;

    final private Map<Integer,Channel> channels = new HashMap<Integer,Channel>();
    private StructFactory factory;

    // XXX
    private int maxFrame = 64*1024;

    public Connection(Handler<ByteBuffer> output)
    {
        this.input = new InputHandler(this);
        this.output = output;
    }

    public Handler<ByteBuffer> getInputHandler()
    {
        return input;
    }

    public Handler<ByteBuffer> getOutputHandler()
    {
        return output;
    }

    public StructFactory getFactory()
    {
        return factory;
    }

    public int getMaxFrame()
    {
        return maxFrame;
    }

    public void init(ProtocolHeader header)
    {
        System.out.println(header);
        // XXX: hardcoded versions
        if (header.getMajor() != 0 && header.getMinor() != 10)
        {
            ByteBuffer buf = ByteBuffer.allocate(8);
            buf.put("AMQP".getBytes());
            buf.put((byte) 1);
            buf.put((byte) 1);
            buf.put((byte) 0);
            buf.put((byte) 10);
            buf.flip();
            output.handle(buf);
            // XXX: how do we close the connection?
        } else {
            factory = new StructFactory_v0_10();
        }
    }

    public void frame(Frame frame)
    {
        Channel channel = channels.get(frame.getChannel());
        if (channel == null)
        {
            channel = new Channel(this, frame.getChannel());
            channels.put(frame.getChannel(), channel);
        }

        channel.handle(frame);
    }

    public void error(ProtocolError error)
    {
        throw new RuntimeException(error.getMessage());
    }

}
