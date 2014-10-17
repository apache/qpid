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

import org.apache.qpid.framing.*;
import org.apache.qpid.protocol.AMQConstant;

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
public abstract class AMQDecoder<T extends MethodProcessor>
{
    private final T _methodProcessor;

    /** Holds the protocol initiation decoder. */
    private ProtocolInitiation.Decoder _piDecoder = new ProtocolInitiation.Decoder();

    /** Flag to indicate whether this decoder needs to handle protocol initiation. */
    private boolean _expectProtocolInitiation;


    private boolean _firstRead = true;

    private int _maxFrameSize = AMQConstant.FRAME_MIN_SIZE.getCode();

    private List<ByteArrayInputStream> _remainingBufs = new ArrayList<ByteArrayInputStream>();

    /**
     * Creates a new AMQP decoder.
     *
     * @param expectProtocolInitiation <tt>true</tt> if this decoder needs to handle protocol initiation.
     * @param methodProcessor method processor
     */
    protected AMQDecoder(boolean expectProtocolInitiation, T methodProcessor)
    {
        _expectProtocolInitiation = expectProtocolInitiation;
        _methodProcessor = methodProcessor;
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
        _maxFrameSize = frameMax;
    }

    public T getMethodProcessor()
    {
        return _methodProcessor;
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


    public void decodeBuffer(ByteBuffer buf) throws AMQFrameDecodingException, AMQProtocolVersionException, IOException
    {

        MarkableDataInput msg;


        // get prior remaining data from accumulator
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
                enoughData = decodable(msg);
                if (enoughData)
                {
                    processInput(msg);
                }
            }
            else
            {
                enoughData = _piDecoder.decodable(msg);
                if (enoughData)
                {
                    _methodProcessor.receiveProtocolHeader(new ProtocolInitiation(msg));
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
    }

    private boolean decodable(final MarkableDataInput in) throws AMQFrameDecodingException, IOException
    {
        final int remainingAfterAttributes = in.available() - (1 + 2 + 4 + 1);
        // type, channel, body length and end byte
        if (remainingAfterAttributes < 0)
        {
            return false;
        }

        in.mark(8);
        in.skip(1 + 2);


        // Get an unsigned int, lifted from MINA ByteBuffer getUnsignedInt()
        final long bodySize = in.readInt() & 0xffffffffL;
        if (bodySize > _maxFrameSize)
        {
            throw new AMQFrameDecodingException(AMQConstant.FRAME_ERROR,
                                                "Incoming frame size of "
                                                + bodySize
                                                + " is larger than negotiated maximum of  "
                                                + _maxFrameSize);
        }
        in.reset();

        return (remainingAfterAttributes >= bodySize);

    }

    private void processInput(final MarkableDataInput in)
            throws AMQFrameDecodingException, AMQProtocolVersionException, IOException
    {
        final byte type = in.readByte();

        final int channel = in.readUnsignedShort();
        final long bodySize = EncodingUtils.readUnsignedInteger(in);

        // bodySize can be zero
        if ((channel < 0) || (bodySize < 0))
        {
            throw new AMQFrameDecodingException(AMQConstant.FRAME_ERROR,
                                                "Undecodable frame: type = " + type + " channel = " + channel
                                                + " bodySize = " + bodySize);
        }

        processFrame(channel, type, bodySize, in);

        byte marker = in.readByte();
        if ((marker & 0xFF) != 0xCE)
        {
            throw new AMQFrameDecodingException(AMQConstant.FRAME_ERROR,
                                                "End of frame marker not found. Read " + marker + " length=" + bodySize
                                                + " type=" + type);
        }

    }

    protected void processFrame(final int channel, final byte type, final long bodySize, final MarkableDataInput in)
            throws AMQFrameDecodingException, IOException
    {
        switch (type)
        {
            case 1:
                processMethod(channel, in);
                break;
            case 2:
                ContentHeaderBody.process(in, _methodProcessor.getChannelMethodProcessor(channel), bodySize);
                break;
            case 3:
                ContentBody.process(in, _methodProcessor.getChannelMethodProcessor(channel), bodySize);
                break;
            case 8:
                HeartbeatBody.process(channel, in, _methodProcessor, bodySize);
                break;
            default:
                throw new AMQFrameDecodingException(AMQConstant.FRAME_ERROR, "Unsupported frame type: " + type);
        }
    }


    abstract void processMethod(int channelId,
                               MarkableDataInput in)
            throws AMQFrameDecodingException, IOException;

    AMQFrameDecodingException newUnknownMethodException(final int classId,
                                                        final int methodId,
                                                        ProtocolVersion protocolVersion)
    {
        return new AMQFrameDecodingException(AMQConstant.COMMAND_INVALID,
                                             "Method "
                                             + methodId
                                             + " unknown in AMQP version "
                                             + protocolVersion
                                             + " (while trying to decode class "
                                             + classId
                                             + " method "
                                             + methodId
                                             + ".");
    }

}
