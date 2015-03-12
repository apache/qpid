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

import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.qpid.transport.ByteBufferSender;
import org.apache.qpid.util.BytesDataOutput;

public class AMQFrame extends AMQDataBlock implements EncodableAMQDataBlock
{
    private final int _channel;

    private final AMQBody _bodyFrame;
    public static final byte FRAME_END_BYTE = (byte) 0xCE;


    public AMQFrame(final int channel, final AMQBody bodyFrame)
    {
        _channel = channel;
        _bodyFrame = bodyFrame;
    }

    public long getSize()
    {
        return 1 + 2 + 4 + _bodyFrame.getSize() + 1;
    }

    public static final int getFrameOverhead()
    {
        return 1 + 2 + 4 + 1;
    }


    public void writePayload(DataOutput buffer) throws IOException
    {
        buffer.writeByte(_bodyFrame.getFrameType());
        EncodingUtils.writeUnsignedShort(buffer, _channel);
        EncodingUtils.writeUnsignedInteger(buffer, _bodyFrame.getSize());
        _bodyFrame.writePayload(buffer);
        buffer.writeByte(FRAME_END_BYTE);
    }

    private static final byte[] FRAME_END_BYTE_ARRAY = new byte[] { FRAME_END_BYTE };

    @Override
    public long writePayload(final ByteBufferSender sender) throws IOException
    {
        byte[] frameHeader = new byte[7];
        BytesDataOutput buffer = new BytesDataOutput(frameHeader);

        buffer.writeByte(_bodyFrame.getFrameType());
        EncodingUtils.writeUnsignedShort(buffer, _channel);
        EncodingUtils.writeUnsignedInteger(buffer, _bodyFrame.getSize());
        sender.send(ByteBuffer.wrap(frameHeader));

        long size = 8 + _bodyFrame.writePayload(sender);

        sender.send(ByteBuffer.wrap(FRAME_END_BYTE_ARRAY));
        return size;
    }

    public final int getChannel()
    {
        return _channel;
    }

    public final AMQBody getBodyFrame()
    {
        return _bodyFrame;
    }

    public String toString()
    {
        return "Frame channelId: " + _channel + ", bodyFrame: " + String.valueOf(_bodyFrame);
    }

    public static void writeFrame(DataOutput buffer, final int channel, AMQBody body) throws IOException
    {
        buffer.writeByte(body.getFrameType());
        EncodingUtils.writeUnsignedShort(buffer, channel);
        EncodingUtils.writeUnsignedInteger(buffer, body.getSize());
        body.writePayload(buffer);
        buffer.writeByte(FRAME_END_BYTE);

    }

    public static void writeFrames(DataOutput buffer, final int channel, AMQBody body1, AMQBody body2) throws IOException
    {
        buffer.writeByte(body1.getFrameType());
        EncodingUtils.writeUnsignedShort(buffer, channel);
        EncodingUtils.writeUnsignedInteger(buffer, body1.getSize());
        body1.writePayload(buffer);
        buffer.writeByte(FRAME_END_BYTE);
        buffer.writeByte(body2.getFrameType());
        EncodingUtils.writeUnsignedShort(buffer, channel);
        EncodingUtils.writeUnsignedInteger(buffer, body2.getSize());
        body2.writePayload(buffer);
        buffer.writeByte(FRAME_END_BYTE);

    }

    public static void writeFrames(DataOutput buffer, final int channel, AMQBody body1, AMQBody body2, AMQBody body3) throws IOException
    {
        buffer.writeByte(body1.getFrameType());
        EncodingUtils.writeUnsignedShort(buffer, channel);
        EncodingUtils.writeUnsignedInteger(buffer, body1.getSize());
        body1.writePayload(buffer);
        buffer.writeByte(FRAME_END_BYTE);
        buffer.writeByte(body2.getFrameType());
        EncodingUtils.writeUnsignedShort(buffer, channel);
        EncodingUtils.writeUnsignedInteger(buffer, body2.getSize());
        body2.writePayload(buffer);
        buffer.writeByte(FRAME_END_BYTE);
        buffer.writeByte(body3.getFrameType());
        EncodingUtils.writeUnsignedShort(buffer, channel);
        EncodingUtils.writeUnsignedInteger(buffer, body3.getSize());
        body3.writePayload(buffer);
        buffer.writeByte(FRAME_END_BYTE);

    }

}
