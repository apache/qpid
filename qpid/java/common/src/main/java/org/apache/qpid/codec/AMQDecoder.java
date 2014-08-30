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

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQDataBlockDecoder;
import org.apache.qpid.framing.AMQFrameDecodingException;
import org.apache.qpid.framing.AMQMethodBodyFactory;
import org.apache.qpid.framing.AMQProtocolVersionException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ByteArrayDataInput;
import org.apache.qpid.framing.EncodingUtils;
import org.apache.qpid.framing.ProtocolInitiation;
import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;

/**
 * AMQDecoder delegates the decoding of AMQP either to a data block decoder, or in the case of new connections, to a
 * protocol initiation decoder. It is a cumulative decoder, which means that it can accumulate data to decode in the
 * buffer until there is enough data to decode.
 *
 * <p>One instance of this class is created per session, so any changes or configuration done at run time to the
 * decoder will only affect decoding of the protocol session data to which is it bound.
 *
 * <p>
 * TODO If protocol initiation decoder not needed, then don't create it. Probably not a big deal, but it adds to the
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

    private AMQMethodBodyFactory _bodyFactory;

    private boolean _firstRead = true;

    private List<ByteArrayInputStream> _remainingBufs = new ArrayList<ByteArrayInputStream>();

    /**
     * Creates a new AMQP decoder.
     *
     * @param expectProtocolInitiation <tt>true</tt> if this decoder needs to handle protocol initiation.
     * @param session protocol session (connection)
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

    public void setMaxFrameSize(final int frameMax)
    {
        _dataBlockDecoder.setMaxFrameSize(frameMax);
    }

    private class RemainingByteArrayInputStream extends InputStream
    {
        private int _currentListPos;
        private int _markPos;


        @Override
        public int read() throws IOException
        {
            ByteArrayInputStream currentStream = _remainingBufs.get(_currentListPos);
            if(currentStream.available() > 0)
            {
                return currentStream.read();
            }
            else if((_currentListPos == _remainingBufs.size())
                    || (++_currentListPos == _remainingBufs.size()))
            {
                return -1;
            }
            else
            {

                ByteArrayInputStream stream = _remainingBufs.get(_currentListPos);
                stream.mark(0);
                return stream.read();
            }
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException
        {

            if(_currentListPos == _remainingBufs.size())
            {
                return -1;
            }
            else
            {
                ByteArrayInputStream currentStream = _remainingBufs.get(_currentListPos);
                final int available = currentStream.available();
                int read = currentStream.read(b, off, len > available ? available : len);
                if(read < len)
                {
                    if(_currentListPos++ != _remainingBufs.size())
                    {
                        _remainingBufs.get(_currentListPos).mark(0);
                    }
                    int correctRead = read == -1 ? 0 : read;
                    int subRead = read(b, off+correctRead, len-correctRead);
                    if(subRead == -1)
                    {
                        return read;
                    }
                    else
                    {
                        return correctRead+subRead;
                    }
                }
                else
                {
                    return len;
                }
            }
        }

        @Override
        public int available() throws IOException
        {
            int total = 0;
            for(int i = _currentListPos; i < _remainingBufs.size(); i++)
            {
                total += _remainingBufs.get(i).available();
            }
            return total;
        }

        @Override
        public void mark(final int readlimit)
        {
            _markPos = _currentListPos;
            final ByteArrayInputStream stream = _remainingBufs.get(_currentListPos);
            if(stream != null)
            {
                stream.mark(readlimit);
            }
        }

        @Override
        public void reset() throws IOException
        {
            _currentListPos = _markPos;
            final int size = _remainingBufs.size();
            if(_currentListPos < size)
            {
                _remainingBufs.get(_currentListPos).reset();
            }
            for(int i = _currentListPos+1; i<size; i++)
            {
                _remainingBufs.get(i).reset();
            }
        }
    }

    private static class SimpleDataInputStream extends DataInputStream implements MarkableDataInput
    {
        public SimpleDataInputStream(InputStream in)
        {
            super(in);
        }

        public AMQShortString readAMQShortString() throws IOException
        {
            return EncodingUtils.readAMQShortString(this);
        }

    }


    public ArrayList<AMQDataBlock> decodeBuffer(ByteBuffer buf) throws AMQFrameDecodingException, AMQProtocolVersionException, IOException
    {

        // get prior remaining data from accumulator
        ArrayList<AMQDataBlock> dataBlocks = new ArrayList<AMQDataBlock>();
        MarkableDataInput msg;


        ByteArrayInputStream bais;
        DataInput di;
        if(!_remainingBufs.isEmpty())
        {
             bais = new ByteArrayInputStream(buf.array(),buf.arrayOffset()+buf.position(), buf.remaining());
            _remainingBufs.add(bais);
            msg = new SimpleDataInputStream(new RemainingByteArrayInputStream());
        }
        else
        {
            bais = null;
            msg = new ByteArrayDataInput(buf.array(),buf.arrayOffset()+buf.position(), buf.remaining());
        }

        // If this is the first read then we may be getting a protocol initiation back if we tried to negotiate
        // an unsupported version
        if(_firstRead && buf.hasRemaining())
        {
            _firstRead = false;
            if(!_expectProtocolInitiation && buf.get(buf.position()) > 8)
            {
                _expectProtocolInitiation = true;
            }
        }

        boolean enoughData = true;
        while (enoughData)
        {
            if(!_expectProtocolInitiation)
            {
                enoughData = _dataBlockDecoder.decodable(msg);
                if (enoughData)
                {
                    dataBlocks.add(_dataBlockDecoder.createAndPopulateFrame(_bodyFactory, msg));
                }
            }
            else
            {
                enoughData = _piDecoder.decodable(msg);
                if (enoughData)
                {
                    dataBlocks.add(new ProtocolInitiation(msg));
                }

            }

            if(!enoughData)
            {
                if(!_remainingBufs.isEmpty())
                {
                    _remainingBufs.remove(_remainingBufs.size()-1);
                    ListIterator<ByteArrayInputStream> iterator = _remainingBufs.listIterator();
                    while(iterator.hasNext() && iterator.next().available() == 0)
                    {
                        iterator.remove();
                    }
                }

                if(bais == null)
                {
                    if(msg.available()!=0)
                    {
                        byte[] remaining = new byte[msg.available()];
                        msg.read(remaining);
                        _remainingBufs.add(new ByteArrayInputStream(remaining));
                    }
                }
                else
                {
                    if(bais.available()!=0)
                    {
                        byte[] remaining = new byte[bais.available()];
                        bais.read(remaining);
                        _remainingBufs.add(new ByteArrayInputStream(remaining));
                    }
                }
            }
        }
        return dataBlocks;
    }
}
