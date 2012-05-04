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


import org.apache.qpid.amqp_1_0.type.Binary;

import java.nio.ByteBuffer;
import java.util.Map;

public class ProtocolHeaderTransport implements BytesTransport
{
    private final Object _inputLock = new Object();
    private final Object _outputLock = new Object();

    private volatile boolean _inputOpen;
    private volatile boolean _outputOpen;

    private final Map<Binary,BytesTransport> _headersMap;

    private BytesTransport _delegate;
    private ByteBuffer _bytesToSend;

    private byte[] _header = new byte[8];
    private int _received;
    private ByteBuffer _outputBuffer;

    public ProtocolHeaderTransport(Map<Binary, BytesTransport> validHeaders)
    {
        _headersMap = validHeaders;

        // if only one valid header then we can send, else we have to wait
    }

    public boolean isOpenForInput()
    {
        return _inputOpen;
    }

    public void inputClosed()
    {
        synchronized(_inputLock)
        {
            _inputOpen = false;
            _inputLock.notifyAll();
        }
    }

    public void processBytes(final ByteBuffer buf)
    {
        if(_delegate != null)
        {
            _delegate.processBytes(buf);
        }
        else
        {

            while( _received < 8 && buf.hasRemaining())
            {
                _header[_received++] = buf.get();
            }
            if(_received == 8)
            {
                Binary header = new Binary(_header);
                _delegate = _headersMap.get(header);
                if(_delegate != null)
                {
                    _delegate.processBytes(ByteBuffer.wrap(_header));
                    _delegate.processBytes(buf);
                }
                else
                {
                    inputClosed();
                    _outputBuffer = _headersMap.keySet().iterator().next().asByteBuffer();
                }
            }
        }
    }

    public void setInputStateChangeListener(final StateChangeListener listener)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }


    public void getNextBytes(final BytesProcessor processor)
    {
        if(_bytesToSend != null && _bytesToSend.hasRemaining())
        {
            processor.processBytes(_bytesToSend);
        }
        else if(_delegate != null)
        {
            _delegate.getNextBytes(processor);
        }

    }

    public void outputClosed()
    {
        synchronized (_outputLock)
        {
            _outputOpen = false;
            _outputLock.notifyAll();
        }
    }

    public boolean isOpenForOutput()
    {
        return _outputOpen;
    }

    public void setOutputStateChangeListener(final StateChangeListener listener)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
