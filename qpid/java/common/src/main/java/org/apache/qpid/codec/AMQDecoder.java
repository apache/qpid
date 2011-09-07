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
package org.apache.qpid.codec;

import java.util.ArrayList;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.SimpleByteBufferAllocator;

import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQDataBlockDecoder;
import org.apache.qpid.framing.AMQFrameDecodingException;
import org.apache.qpid.framing.AMQMethodBodyFactory;
import org.apache.qpid.framing.AMQProtocolVersionException;
import org.apache.qpid.framing.ProtocolInitiation;
import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;

/**
 * AMQDecoder delegates the decoding of AMQP either to a data block decoder, or in the case of new connections, to a
 * protocol initiation decoder. It is a cumulative decoder, which means that it can accumulate data to decode in the
 * buffer until there is enough data to decode.
 *
 * <p/>One instance of this class is created per session, so any changes or configuration done at run time to the
 * decoder will only affect decoding of the protocol session data to which is it bound.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Delegate protocol initiation to its decoder. <td> {@link ProtocolInitiation.Decoder}
 * <tr><td> Delegate AMQP data to its decoder. <td> {@link AMQDataBlockDecoder}
 * <tr><td> Accept notification that protocol initiation has completed.
 * </table>
 *
 * @todo If protocol initiation decoder not needed, then don't create it. Probably not a big deal, but it adds to the
 *       per-session overhead.
 */
public class AMQDecoder
{
    /** Holds the 'normal' AMQP data decoder. */
    private AMQDataBlockDecoder _dataBlockDecoder = new AMQDataBlockDecoder();

    /** Holds the protocol initiation decoder. */
    private ProtocolInitiation.Decoder _piDecoder = new ProtocolInitiation.Decoder();

    /** Flag to indicate whether this decoder needs to handle protocol initiation. */
    private boolean _expectProtocolInitiation;
    private boolean firstDecode = true;

    private AMQMethodBodyFactory _bodyFactory;

    private ByteBuffer _remainingBuf;
    
    /**
     * Creates a new AMQP decoder.
     *
     * @param expectProtocolInitiation <tt>true</tt> if this decoder needs to handle protocol initiation.
     */
    public AMQDecoder(boolean expectProtocolInitiation, AMQVersionAwareProtocolSession session)
    {
        _expectProtocolInitiation = expectProtocolInitiation;
        _bodyFactory = new AMQMethodBodyFactory(session);
    }



    /**
     * Sets the protocol initation flag, that determines whether decoding is handled by the data decoder of the protocol
     * initation decoder. This method is expected to be called with <tt>false</tt> once protocol initation completes.
     *
     * @param expectProtocolInitiation <tt>true</tt> to use the protocol initiation decoder, <tt>false</tt> to use the
     *                                data decoder.
     */
    public void setExpectProtocolInitiation(boolean expectProtocolInitiation)
    {
        _expectProtocolInitiation = expectProtocolInitiation;
    }


    private static final SimpleByteBufferAllocator SIMPLE_BYTE_BUFFER_ALLOCATOR = new SimpleByteBufferAllocator();

    public ArrayList<AMQDataBlock> decodeBuffer(java.nio.ByteBuffer buf) throws AMQFrameDecodingException, AMQProtocolVersionException
    {

        // get prior remaining data from accumulator
        ArrayList<AMQDataBlock> dataBlocks = new ArrayList<AMQDataBlock>();
        ByteBuffer msg;
        // if we have a session buffer, append data to that otherwise
        // use the buffer read from the network directly
        if( _remainingBuf != null )
        {
            _remainingBuf.put(buf);
            _remainingBuf.flip();
            msg = _remainingBuf;
        }
        else
        {
            msg = ByteBuffer.wrap(buf);
        }
        
        if (_expectProtocolInitiation  
            || (firstDecode
                && (msg.remaining() > 0)
                && (msg.get(msg.position()) == (byte)'A')))
        {
            if (_piDecoder.decodable(msg.buf()))
            {
                dataBlocks.add(new ProtocolInitiation(msg.buf()));
            }
        }
        else
        {
            boolean enoughData = true;
            while (enoughData)
            {
                int pos = msg.position();

                enoughData = _dataBlockDecoder.decodable(msg);
                msg.position(pos);
                if (enoughData)
                {
                    dataBlocks.add(_dataBlockDecoder.createAndPopulateFrame(_bodyFactory, msg));
                }
                else
                {
                    _remainingBuf = SIMPLE_BYTE_BUFFER_ALLOCATOR.allocate(msg.remaining(), false);
                    _remainingBuf.setAutoExpand(true);
                    _remainingBuf.put(msg);
                }
            }
        }
        if(firstDecode && dataBlocks.size() > 0)
        {
            firstDecode = false;
        }
        return dataBlocks;
    }
}
