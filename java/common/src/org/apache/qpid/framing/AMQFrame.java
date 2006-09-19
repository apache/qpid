/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.framing;

import org.apache.mina.common.ByteBuffer;

public class AMQFrame extends AMQDataBlock implements EncodableAMQDataBlock
{
    public int channel;

    public AMQBody bodyFrame;

    public AMQFrame()
    {
    }

    public AMQFrame(int channel, AMQBody bodyFrame)
    {
        this.channel = channel;
        this.bodyFrame = bodyFrame;
    }

    public long getSize()
    {
        return 1 + 2 + 4 + bodyFrame.getSize() + 1;
    }

    public void writePayload(ByteBuffer buffer)
    {
        buffer.put(bodyFrame.getType());
        // TODO: how does channel get populated
        EncodingUtils.writeUnsignedShort(buffer, channel);
        EncodingUtils.writeUnsignedInteger(buffer, bodyFrame.getSize());
        bodyFrame.writePayload(buffer);
        buffer.put((byte) 0xCE);
    }

    /**
     *
     * @param buffer
     * @param channel unsigned short
     * @param bodySize unsigned integer
     * @param bodyFactory
     * @throws AMQFrameDecodingException
     */
    public void populateFromBuffer(ByteBuffer buffer, int channel, long bodySize, BodyFactory bodyFactory)
            throws AMQFrameDecodingException
    {
        this.channel = channel;
        bodyFrame = bodyFactory.createBody(buffer);
        bodyFrame.populateFromBuffer(buffer, bodySize);
    }

    public String toString()
    {
        return "Frame channelId: " + channel + ", bodyFrame: " + String.valueOf(bodyFrame);
    }
}
