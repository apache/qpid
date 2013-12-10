/*
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
 */

package org.apache.qpid.amqp_1_0.transport;

import org.apache.qpid.amqp_1_0.codec.FrameWriter;
import org.apache.qpid.amqp_1_0.framing.AMQFrame;
import org.apache.qpid.amqp_1_0.framing.TransportFrame;
import org.apache.qpid.amqp_1_0.type.FrameBody;

import java.nio.ByteBuffer;

public class AMQPTransport implements BytesTransport
{
    private volatile boolean _inputOpen = true;
    private volatile boolean _outputOpen = true;

    private static final int INPUT_BUFFER_SIZE = 1 << 16;
    private static final int OUTPUT_BUFFER_SIZE = 1 << 16;


    private final CircularBytesBuffer _inputBuffer = new CircularBytesBuffer(INPUT_BUFFER_SIZE);
    private TransportFrame _currentInputFrame;
    private boolean _readingFrames;


    private final CircularBytesBuffer _outputBuffer = new CircularBytesBuffer(OUTPUT_BUFFER_SIZE);

    private AMQFrame<FrameBody> _currentOutputFrame;


    private AMQPFrameTransport _frameTransport;

    private FrameWriter _frameWriter;

    private final BytesProcessor _frameWriterProcessor = new BytesProcessor()
            {
                public void processBytes(final ByteBuffer buf)
                {
                    _frameWriter.writeToBuffer(buf);

                    if(_frameWriter.isComplete())
                    {
                        _currentOutputFrame = null;
                    }
                }
            };

    private StateChangeListener _inputListener;
    private StateChangeListener _outputListener;


    public AMQPTransport(AMQPFrameTransport frameTransport)
    {
        _frameTransport = frameTransport;
        _frameWriter = new FrameWriter(_frameTransport.getRegistry());
        _outputBuffer.put( ByteBuffer.wrap( new byte[] { (byte) 'A',
                                                         (byte) 'M',
                                                         (byte) 'Q',
                                                         (byte) 'P',
                                                         (byte) 0,
                                                         _frameTransport.getMajorVersion(),
                                                         _frameTransport.getMajorVersion(),
                                                         _frameTransport.getRevision(),
                                                       } ) );


    }

    public boolean isOpenForInput()
    {
        return _inputOpen;
    }

    public void inputClosed()
    {
        _inputOpen = false;
    }

    public void processBytes(final ByteBuffer buf)
    {
        _inputBuffer.put(buf);

        if(!_readingFrames)
        {
            if(_inputBuffer.size()>=8)
            {
                final byte[] incomingHeader = new byte[8];
                _inputBuffer.get(new BytesProcessor()
                {
                    public void processBytes(final ByteBuffer buf)
                    {
                        buf.get(incomingHeader);
                    }
                });
                _frameTransport.setVersion(incomingHeader[5], incomingHeader[6], incomingHeader[7]);
                _readingFrames = true;
            }
        }
        else
        {

        }


        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setInputStateChangeListener(final StateChangeListener listener)
    {
        _inputListener = listener;
        _frameTransport.setInputStateChangeListener(listener);
    }

    public void getNextBytes(final BytesProcessor processor)
    {
        // First try to fill the buffer as much as possible with frames
        while(!_outputBuffer.isFull())
        {
            if(_currentOutputFrame == null)
            {
                _currentOutputFrame = _frameTransport.getNextFrame();
                _frameWriter.setValue(_currentOutputFrame);
            }

            if(_currentOutputFrame == null)
            {
                break;
            }


            _outputBuffer.put(_frameWriterProcessor);



        }

    }

    public void outputClosed()
    {
        _outputOpen = false;
    }

    public boolean isOpenForOutput()
    {
        return _outputOpen;
    }

    public void setOutputStateChangeListener(final StateChangeListener listener)
    {
        _outputListener = listener;
    }
}
