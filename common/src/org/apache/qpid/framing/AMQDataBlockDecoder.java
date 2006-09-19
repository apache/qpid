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

import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;

import java.util.HashMap;
import java.util.Map;

public class AMQDataBlockDecoder
{
    Logger _logger = Logger.getLogger(AMQDataBlockDecoder.class);

    private final Map _supportedBodies = new HashMap();

    public AMQDataBlockDecoder()
    {
        _supportedBodies.put(new Byte(AMQMethodBody.TYPE), AMQMethodBodyFactory.getInstance());
        _supportedBodies.put(new Byte(ContentHeaderBody.TYPE), ContentHeaderBodyFactory.getInstance());
        _supportedBodies.put(new Byte(ContentBody.TYPE), ContentBodyFactory.getInstance());
        _supportedBodies.put(new Byte(HeartbeatBody.TYPE), new HeartbeatBodyFactory());
    }

    public boolean decodable(IoSession session, ByteBuffer in) throws AMQFrameDecodingException
    {
        // type, channel, body size and end byte
        if (in.remaining() < (1 + 2 + 4 + 1))
        {
            return false;
        }

        final byte type = in.get();
        final int channel = in.getUnsignedShort();
        final long bodySize = in.getUnsignedInt();

        // bodySize can be zero
        if (type <= 0 || channel < 0 || bodySize < 0)
        {
            throw new AMQFrameDecodingException("Undecodable frame: type = " + type + " channel = " + channel +
                                                " bodySize = " + bodySize);
        }

        if (in.remaining() < (bodySize + 1))
        {
            return false;
        }
        return true;
    }

    private boolean isSupportedFrameType(byte frameType)
    {
        final boolean result = _supportedBodies.containsKey(new Byte(frameType));

        if (!result)
        {
        	_logger.warn("AMQDataBlockDecoder does not handle frame type " + frameType);
        }

        return result;
    }

    protected Object createAndPopulateFrame(ByteBuffer in)
                    throws AMQFrameDecodingException
    {
        final byte type = in.get();
        if (!isSupportedFrameType(type))
        {
            throw new AMQFrameDecodingException("Unsupported frame type: " + type);
        }
        final int channel = in.getUnsignedShort();
        final long bodySize = in.getUnsignedInt();

        BodyFactory bodyFactory = (BodyFactory) _supportedBodies.get(new Byte(type));
        if (bodyFactory == null)
        {
            throw new AMQFrameDecodingException("Unsupported body type: " + type);
        }
        AMQFrame frame = new AMQFrame();

        frame.populateFromBuffer(in, channel, bodySize, bodyFactory);

        byte marker = in.get();
        if ((marker & 0xFF) != 0xCE)
        {
            throw new AMQFrameDecodingException("End of frame marker not found. Read " + marker + " size=" + bodySize + " type=" + type);
        }
        return frame;
    }

    public void decode(IoSession session, ByteBuffer in, ProtocolDecoderOutput out)
        throws Exception
    {
        out.write(createAndPopulateFrame(in));
    }
}
