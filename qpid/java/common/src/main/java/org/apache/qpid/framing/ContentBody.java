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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.qpid.AMQException;
import org.apache.qpid.codec.MarkableDataInput;
import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;

public class ContentBody implements AMQBody
{
    public static final byte TYPE = 3;

    private byte[] _payload;

    public ContentBody()
    {
    }

    public ContentBody(DataInput buffer, long size) throws AMQFrameDecodingException, IOException
    {
        _payload = new byte[(int)size];
        buffer.readFully(getPayload());
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
        return getPayload() == null ? 0 : getPayload().length;
    }

    public void writePayload(DataOutput buffer) throws IOException
    {
        buffer.write(getPayload());
    }

    public void handle(final int channelId, final AMQVersionAwareProtocolSession session)
            throws AMQException
    {
        session.contentBodyReceived(channelId, this);
    }

    public byte[] getPayload()
    {
        return _payload;
    }

    public static void process(final MarkableDataInput in,
                               final ChannelMethodProcessor methodProcessor, final long bodySize)
            throws IOException
    {

        byte[] payload = new byte[(int)bodySize];
        in.readFully(payload);

        if(!methodProcessor.ignoreAllButCloseOk())
        {
            methodProcessor.receiveMessageContent(payload);
        }
    }

    private static class BufferContentBody implements AMQBody
    {
        private final int _length;
        private final int _offset;
        private final ByteBuffer _buf;

        private BufferContentBody( ByteBuffer buf, int offset, int length)
        {
            _length = length;
            _offset = offset;
            _buf = buf;
        }

        public byte getFrameType()
        {
            return TYPE;
        }


        public int getSize()
        {
            return _length;
        }

        public void writePayload(DataOutput buffer) throws IOException
        {
            if(_buf.hasArray())
            {
                buffer.write(_buf.array(), _buf.arrayOffset() +  _offset, _length);
            }
            else
            {
                byte[] data = new byte[_length];
                ByteBuffer buf = _buf.duplicate();

                buf.position(_offset);
                buf.limit(_offset+_length);
                buf.get(data);
                buffer.write(data);
            }
        }


        public void handle(int channelId, AMQVersionAwareProtocolSession amqProtocolSession) throws AMQException
        {
            throw new RuntimeException("Buffered Body only to be used for outgoing data");
        }
    }

    public static AMQFrame createAMQFrame(int channelId, ByteBuffer buf, int offset, int length)
    {
        return new AMQFrame(channelId, new BufferContentBody(buf, offset, length));
    }

    public static AMQFrame createAMQFrame(int channelId, ContentBody body)
    {
        return new AMQFrame(channelId, body);
    }
}
