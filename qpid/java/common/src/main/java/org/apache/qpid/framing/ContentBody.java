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
import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;
import org.apache.qpid.AMQException;

public class ContentBody implements AMQBody
{
    public static final byte TYPE = 3;

    public byte[] _payload;

    public ContentBody()
    {
    }

    public ContentBody(DataInputStream buffer, long size) throws AMQFrameDecodingException, IOException
    {
        _payload = new byte[(int)size];
        buffer.read(_payload);
    }


    public ContentBody(byte[] payload)
    {
        _payload = payload;
    }

    public byte getFrameType()
    {
        return TYPE;
    }

    public int getSize()
    {
        return _payload == null ? 0 : _payload.length;
    }

    public void writePayload(DataOutputStream buffer) throws IOException
    {
        buffer.write(_payload);
    }

    public void handle(final int channelId, final AMQVersionAwareProtocolSession session)
            throws AMQException
    {
        session.contentBodyReceived(channelId, this);
    }

    protected void populateFromBuffer(DataInputStream buffer, long size) throws AMQFrameDecodingException, IOException
    {
        if (size > 0)
        {
            _payload = new byte[(int)size];
            buffer.read(_payload);
        }

    }

    public void reduceBufferToFit()
    {
    }



    public static AMQFrame createAMQFrame(int channelId, ContentBody body)
    {
        final AMQFrame frame = new AMQFrame(channelId, body);
        return frame;
    }
}
