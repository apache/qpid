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
package org.apache.qpid.framing;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.qpid.AMQException;
import org.apache.qpid.codec.MarkableDataInput;
import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;
import org.apache.qpid.transport.ByteBufferSender;

public class HeartbeatBody implements AMQBody
{
    public static final byte TYPE = 8;
    public static final AMQFrame FRAME = new HeartbeatBody().toFrame();

    public HeartbeatBody()
    {

    }

    public HeartbeatBody(DataInputStream buffer, long size) throws IOException
    {
        if(size > 0)
        {
            //allow other implementations to have a payload, but ignore it:
            buffer.skip(size);
        }
    }

    public byte getFrameType()
    {
        return TYPE;
    }

    public int getSize()
    {
        return 0;//heartbeats we generate have no payload
    }

    public void writePayload(DataOutput buffer)
    {
    }

    @Override
    public long writePayload(final ByteBufferSender sender) throws IOException
    {
        return 0l;
    }

    public void handle(final int channelId, final AMQVersionAwareProtocolSession session)
            throws AMQException
    {
        session.heartbeatBodyReceived(channelId, this);
    }

    protected void populateFromBuffer(DataInputStream buffer, long size) throws AMQFrameDecodingException, IOException
    {
        if(size > 0)
        {
            //allow other implementations to have a payload, but ignore it:
            buffer.skip(size);
        }
    }

    public AMQFrame toFrame()
    {
        return new AMQFrame(0, this);
    }

    public static void process(final int channel,
                            final MarkableDataInput in,
                            final MethodProcessor processor,
                            final long bodySize) throws IOException
    {

        if(bodySize > 0)
        {
            in.skip(bodySize);
        }
        processor.receiveHeartbeat();
    }
}
