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
 *
 */
package org.apache.qpid.server.protocol;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.SenderException;
import org.apache.qpid.transport.network.security.SSLStatus;
import org.apache.qpid.transport.network.security.ssl.SSLUtil;
import org.apache.qpid.transport.util.Logger;

public class SSLBufferingSender implements Sender<ByteBuffer>
{
    private static final Logger LOGGER = Logger.get(SSLBufferingSender.class);
    private static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);

    private final Sender<ByteBuffer> _delegate;
    private final SSLEngine _engine;
    private final int _sslBufSize;
    private final SSLStatus _sslStatus;

    private String _hostname;

    private final AtomicBoolean _closed = new AtomicBoolean(false);
    private ByteBuffer _appData = EMPTY_BYTE_BUFFER;


    public SSLBufferingSender(SSLEngine engine, Sender<ByteBuffer> delegate, SSLStatus sslStatus)
    {
        _engine = engine;
        _delegate = delegate;
        _sslBufSize = engine.getSession().getPacketBufferSize();
        _sslStatus = sslStatus;
    }

    public void setHostname(String hostname)
    {
        _hostname = hostname;
    }

    public void close()
    {
        if (!_closed.getAndSet(true))
        {
            if (_engine.isOutboundDone())
            {
                return;
            }
            LOGGER.debug("Closing SSL connection");
            doSend();
            _engine.closeOutbound();
            try
            {
                tearDownSSLConnection();
            }
            catch(Exception e)
            {
                throw new SenderException("Error closing SSL connection",e);
            }


            synchronized(_sslStatus.getSslLock())
            {
                while (!_engine.isOutboundDone())
                {
                    try
                    {
                        _sslStatus.getSslLock().wait();
                    }
                    catch(InterruptedException e)
                    {
                        // pass
                    }

                }
            }
            _delegate.close();
        }
    }

    private void tearDownSSLConnection() throws Exception
    {
        ByteBuffer netData = getNetDataBuffer();
        SSLEngineResult result = _engine.wrap(ByteBuffer.allocate(0), netData);
        Status status = result.getStatus();
        int read   = result.bytesProduced();
        while (status != Status.CLOSED)
        {
            if (status == Status.BUFFER_OVERFLOW)
            {
                netData.clear();
            }
            if(read > 0)
            {
                int limit = netData.limit();
                netData.limit(netData.position());
                netData.position(netData.position() - read);

                ByteBuffer data = netData.slice();

                netData.limit(limit);
                netData.position(netData.position() + read);

                _delegate.send(data);
                flush();
            }
            result = _engine.wrap(ByteBuffer.allocate(0), netData);
            status = result.getStatus();
            read   = result.bytesProduced();
        }
    }

    private ByteBuffer getNetDataBuffer()
    {
        return ByteBuffer.allocate(_sslBufSize);
    }

    public void flush()
    {
        _delegate.flush();
    }

    public void send()
    {
        if(!_closed.get())
        {
            doSend();
        }
    }

    public synchronized void send(ByteBuffer appData)
    {
        boolean buffered;
        if(buffered = _appData.hasRemaining())
        {
            ByteBuffer newBuf = ByteBuffer.allocate(_appData.remaining()+appData.remaining());
            newBuf.put(_appData);
            newBuf.put(appData);
            newBuf.flip();
            _appData = newBuf;
        }
        if (_closed.get())
        {
            throw new SenderException("SSL Sender is closed");
        }
        doSend();
        if(!appData.hasRemaining())
        {
            _appData = EMPTY_BYTE_BUFFER;
        }
        else if(!buffered)
        {
            _appData = ByteBuffer.allocate(appData.remaining());
            _appData.put(appData);
            _appData.flip();
        }
    }

    private synchronized void doSend()
    {

        HandshakeStatus handshakeStatus;
        Status status;

        while((_appData.hasRemaining() || _engine.getHandshakeStatus() == HandshakeStatus.NEED_WRAP)
              && !_sslStatus.getSslErrorFlag())
        {
            ByteBuffer netData = getNetDataBuffer();

            int read = 0;
            try
            {
                SSLEngineResult result = _engine.wrap(_appData, netData);
                read   = result.bytesProduced();
                status = result.getStatus();
                handshakeStatus = result.getHandshakeStatus();
            }
            catch(SSLException e)
            {
                // Should this set _sslError??
                throw new SenderException("SSL, Error occurred while encrypting data",e);
            }

            if(read > 0)
            {
                int limit = netData.limit();
                netData.limit(netData.position());
                netData.position(netData.position() - read);

                ByteBuffer data = netData.slice();

                netData.limit(limit);
                netData.position(netData.position() + read);

                _delegate.send(data);
            }

            switch(status)
            {
                case CLOSED:
                    throw new SenderException("SSLEngine is closed");

                case BUFFER_OVERFLOW:
                    netData.clear();
                    continue;

                case OK:
                    break; // do nothing

                default:
                    throw new IllegalStateException("SSLReceiver: Invalid State " + status);
            }

            switch (handshakeStatus)
            {
                case NEED_WRAP:
                    if (netData.hasRemaining())
                    {
                        continue;
                    }

                case NEED_TASK:
                    doTasks();
                    break;

                case NEED_UNWRAP:
                    flush();
                    return;

                case FINISHED:
                    if (_hostname != null)
                    {
                        SSLUtil.verifyHostname(_engine, _hostname);
                    }

                case NOT_HANDSHAKING:
                    break; //do  nothing

                default:
                    throw new IllegalStateException("SSLSender: Invalid State " + status);
            }

        }
    }

    private void doTasks()
    {
        Runnable runnable;
        while ((runnable = _engine.getDelegatedTask()) != null) {
            runnable.run();
        }
    }

    public void setIdleTimeout(int i)
    {
        _delegate.setIdleTimeout(i);
    }
}
