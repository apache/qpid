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

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.qpid.AMQException;

public class ProtocolInitiation extends AMQDataBlock implements EncodableAMQDataBlock
{
    public char[] header = new char[]{'A','M','Q','P'};
    // TODO: generate these constants automatically from the xml protocol spec file

    private static byte CURRENT_PROTOCOL_CLASS = 1;
    private static final int CURRENT_PROTOCOL_INSTANCE = 1;

    public byte protocolClass = CURRENT_PROTOCOL_CLASS;
    public byte protocolInstance = CURRENT_PROTOCOL_INSTANCE;
    public byte protocolMajor;
    public byte protocolMinor;

//    public ProtocolInitiation() {}

    public ProtocolInitiation(byte major, byte minor)
    {
        protocolMajor = major;
        protocolMinor = minor;
    }

    public long getSize()
    {
        return 4 + 1 + 1 + 1 + 1;
    }

    public void writePayload(ByteBuffer buffer)
    {
        for (int i = 0; i < header.length; i++)
        {
            buffer.put((byte) header[i]);
        }
        buffer.put(protocolClass);
        buffer.put(protocolInstance);
        buffer.put(protocolMajor);
        buffer.put(protocolMinor);
    }

    public void populateFromBuffer(ByteBuffer buffer) throws AMQException
    {
        throw new AMQException("Method not implemented");
    }

    public boolean equals(Object o)
    {
        if (!(o instanceof ProtocolInitiation))
        {
            return false;
        }

        ProtocolInitiation pi = (ProtocolInitiation) o;
        if (pi.header == null)
        {
            return false;
        }

        if (header.length != pi.header.length)
        {
            return false;
        }

        for (int i = 0; i < header.length; i++)
        {
            if (header[i] != pi.header[i])
            {
                return false;
            }
        }

        return (protocolClass == pi.protocolClass &&
                protocolInstance == pi.protocolInstance &&
                protocolMajor == pi.protocolMajor &&
                protocolMinor == pi.protocolMinor);
    }

    public static class Decoder //implements MessageDecoder
    {
        /**
         *
         * @param session
         * @param in
         * @return true if we have enough data to decode the PI frame fully, false if more
         * data is required
         */
        public boolean decodable(IoSession session, ByteBuffer in)
        {
            return (in.remaining() >= 8);
        }

        public void decode(IoSession session, ByteBuffer in, ProtocolDecoderOutput out)
            throws Exception
        {
            byte[] theHeader = new byte[4];
            in.get(theHeader);
            ProtocolInitiation pi = new ProtocolInitiation((byte)0, (byte)0);
            pi.header = new char[]{(char) theHeader[0],(char) theHeader[CURRENT_PROTOCOL_INSTANCE],(char) theHeader[2], (char) theHeader[3]};
            String stringHeader = new String(pi.header);
            if (!"AMQP".equals(stringHeader))
            {
                throw new AMQProtocolHeaderException("Invalid protocol header - read " + stringHeader);
            }
            pi.protocolClass = in.get();
            pi.protocolInstance = in.get();
            pi.protocolMajor = in.get();
            pi.protocolMinor = in.get();
            out.write(pi);
        }
    }

    public void checkVersion(ProtocolVersionList pvl) throws AMQException
    {
        if (protocolClass != CURRENT_PROTOCOL_CLASS)
        {
            throw new AMQProtocolClassException("Protocol class " + CURRENT_PROTOCOL_CLASS + " was expected; received " +
                    protocolClass);
        }
        if (protocolInstance != CURRENT_PROTOCOL_INSTANCE)
        {
            throw new AMQProtocolInstanceException("Protocol instance " + CURRENT_PROTOCOL_INSTANCE + " was expected; received " +
                    protocolInstance);
        }
        
        /* Look through list of available protocol versions */
        boolean found = false;
        for (int i=0; i<pvl.pv.length; i++)
        {
            if (pvl.pv[i][pvl.PROTOCOL_MAJOR] == protocolMajor &&
                pvl.pv[i][pvl.PROTOCOL_MINOR] == protocolMinor)
            {
                found = true;
            }
        }
        if (!found)
        {
            // TODO: add list of available versions in list to msg...
            throw new AMQProtocolVersionException("Protocol version " +
                protocolMajor + "." +  protocolMinor + " not found in protocol version list.");
        }
    }
}
