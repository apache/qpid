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

import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;

public class AMQDataBlockDecoder
{
    private static final String SESSION_METHOD_BODY_FACTORY = "QPID_SESSION_METHOD_BODY_FACTORY";

    private static final BodyFactory[] _bodiesSupported = new BodyFactory[Byte.MAX_VALUE];

    static
    {
        _bodiesSupported[ContentHeaderBody.TYPE] = ContentHeaderBodyFactory.getInstance();
        _bodiesSupported[ContentBody.TYPE] = ContentBodyFactory.getInstance();
        _bodiesSupported[HeartbeatBody.TYPE] = new HeartbeatBodyFactory();
    }


    Logger _logger = Logger.getLogger(AMQDataBlockDecoder.class);



    public AMQDataBlockDecoder()
    {
    }

    public boolean decodable(IoSession session, ByteBuffer in) throws AMQFrameDecodingException
    {
        final int remainingAfterAttributes = in.remaining() - (1 + 2 + 4 + 1);
        // type, channel, body length and end byte
        if (remainingAfterAttributes < 0)
        {
            return false;
        }
        in.skip(1 + 2);
        final long bodySize = in.getUnsignedInt();



        return (remainingAfterAttributes >= bodySize);

    }


    protected Object createAndPopulateFrame(IoSession session, ByteBuffer in)
                    throws AMQFrameDecodingException, AMQProtocolVersionException
    {
        final byte type = in.get();

        BodyFactory bodyFactory;
        if(type == AMQMethodBodyImpl.TYPE)
        {
            bodyFactory = (BodyFactory) session.getAttribute(SESSION_METHOD_BODY_FACTORY);
            if(bodyFactory == null)
            {
                AMQVersionAwareProtocolSession protocolSession = (AMQVersionAwareProtocolSession) session.getAttachment();
                bodyFactory = new AMQMethodBodyFactory(protocolSession);
                session.setAttribute(SESSION_METHOD_BODY_FACTORY, bodyFactory);

            }

        }
        else
        {
            bodyFactory = _bodiesSupported[type];
        }




        if(bodyFactory == null)
        {
            throw new AMQFrameDecodingException("Unsupported frame type: " + type);
        }

        final int channel = in.getUnsignedShort();
        final long bodySize = in.getUnsignedInt();

        // bodySize can be zero
        if (channel < 0 || bodySize < 0)
        {
            throw new AMQFrameDecodingException("Undecodable frame: type = " + type + " channel = " + channel +
                                                " bodySize = " + bodySize);
        }

        AMQFrame frame = new AMQFrame(in, channel, bodySize, bodyFactory);

        
        byte marker = in.get();
        if ((marker & 0xFF) != 0xCE)
        {
            throw new AMQFrameDecodingException("End of frame marker not found. Read " + marker + " length=" + bodySize + " type=" + type);
        }
        return frame;
    }

    public void decode(IoSession session, ByteBuffer in, ProtocolDecoderOutput out)
        throws Exception
    {
        out.write(createAndPopulateFrame(session, in));
    }
}
