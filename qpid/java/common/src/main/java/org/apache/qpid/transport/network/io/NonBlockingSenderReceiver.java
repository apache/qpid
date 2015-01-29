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

package org.apache.qpid.transport.network.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLPeerUnverifiedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.protocol.ServerProtocolEngine;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.SenderClosedException;
import org.apache.qpid.transport.SenderException;
import org.apache.qpid.transport.network.Ticker;
import org.apache.qpid.transport.network.TransportEncryption;
import org.apache.qpid.transport.network.security.ssl.SSLUtil;
import org.apache.qpid.util.SystemUtils;

public class NonBlockingSenderReceiver  implements Sender<ByteBuffer>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(NonBlockingSenderReceiver.class);
    public static final int NUMBER_OF_BYTES_FOR_TLS_CHECK = 6;

    private final SocketChannel _socketChannel;

    private final ConcurrentLinkedQueue<ByteBuffer> _buffers = new ConcurrentLinkedQueue<>();
    private final List<ByteBuffer> _encryptedOutput = new ArrayList<>();

    private final String _remoteSocketAddress;
    private final AtomicBoolean _closed = new AtomicBoolean(false);
    private final ServerProtocolEngine _receiver;
    private final int _receiveBufSize;
    private final Ticker _ticker;
    private final Set<TransportEncryption> _encryptionSet;
    private final SSLContext _sslContext;
    private final Runnable _onTransportEncryptionAction;
    private final NonBlockingConnection _connection;
    private ByteBuffer _netInputBuffer;
    private SSLEngine _sslEngine;

    private ByteBuffer _currentBuffer;

    private TransportEncryption _transportEncryption;
    private SSLEngineResult _status;
    private volatile boolean _fullyWritten = true;
    private AtomicBoolean _stateChanged = new AtomicBoolean();
    private boolean _workDone;


    public NonBlockingSenderReceiver(final NonBlockingConnection connection,
                                     ServerProtocolEngine receiver,
                                     int receiveBufSize,
                                     Ticker ticker,
                                     final Set<TransportEncryption> encryptionSet,
                                     final SSLContext sslContext,
                                     final boolean wantClientAuth,
                                     final boolean needClientAuth,
                                     final Runnable onTransportEncryptionAction)
    {
        _connection = connection;
        _socketChannel = connection.getSocketChannel();
        _receiver = receiver;
        _receiveBufSize = receiveBufSize;
        _ticker = ticker;
        _encryptionSet = encryptionSet;
        _sslContext = sslContext;
        _onTransportEncryptionAction = onTransportEncryptionAction;

        if(encryptionSet.size() == 1)
        {
            _transportEncryption = _encryptionSet.iterator().next();
            if (_transportEncryption == TransportEncryption.TLS)
            {
                onTransportEncryptionAction.run();
            }
        }

        if(encryptionSet.contains(TransportEncryption.TLS))
        {
            _sslEngine = _sslContext.createSSLEngine();
            _sslEngine.setUseClientMode(false);
            SSLUtil.removeSSLv3Support(_sslEngine);
            if(needClientAuth)
            {
                _sslEngine.setNeedClientAuth(true);
            }
            else if(wantClientAuth)
            {
                _sslEngine.setWantClientAuth(true);
            }
            _netInputBuffer = ByteBuffer.allocate(Math.max(_sslEngine.getSession().getPacketBufferSize(), _receiveBufSize * 2));
        }

        try
        {
            _remoteSocketAddress = _socketChannel.getRemoteAddress().toString();
            _socketChannel.configureBlocking(false);
        }
        catch (IOException e)
        {
            throw new SenderException("Unable to prepare the channel for non-blocking IO", e);
        }

    }

    @Override
    public void send(final ByteBuffer msg)
    {
        if (_closed.get())
        {
            throw new SenderClosedException("I/O for thread " + _remoteSocketAddress + " is already closed");
        }
        // append to list and do selector wakeup
        _buffers.add(msg);
        _stateChanged.set(true);
    }


    public boolean doWork()
    {
        _stateChanged.set(false);
        boolean closed = _closed.get();
        if (!closed)
        {
            try
            {
                _workDone = false;

                long currentTime = System.currentTimeMillis();
                int tick = _ticker.getTimeToNextTick(currentTime);
                if (tick <= 0)
                {
                    _ticker.tick(currentTime);
                }

                _receiver.setTransportBlockedForWriting(!doWrite());
                boolean dataRead = doRead();
                _fullyWritten = doWrite();
                _receiver.setTransportBlockedForWriting(!_fullyWritten);

                if(dataRead || (_workDone && _netInputBuffer != null && _netInputBuffer.position() != 0))
                {
                    _stateChanged.set(true);
                }
            }
            catch (IOException e)
            {
                LOGGER.info("Exception performing I/O for thread '" + _remoteSocketAddress + "': " + e);
                close();
            }
        }
        else
        {

            if(!SystemUtils.isWindows())
            {
                try
                {
                    _socketChannel.shutdownInput();
                }
                catch (IOException e)
                {
                    LOGGER.info("Exception shutting down input for thread '" + _remoteSocketAddress + "': " + e);

                }
            }
            try
            {
                while(!doWrite())
                {
                }
            }
            catch (IOException e)
            {
                LOGGER.info("Exception performing final write/close for thread '" + _remoteSocketAddress + "': " + e);

            }
            LOGGER.debug("Closing receiver");
            _receiver.closed();

            try
            {
                if(!SystemUtils.isWindows())
                {
                    _socketChannel.shutdownOutput();
                }

                _socketChannel.close();
            }
            catch (IOException e)
            {
                LOGGER.info("Exception closing socket thread '" + _remoteSocketAddress + "': " + e);
            }
        }

        return closed;

    }

    @Override
    public void flush()
    {
        _connection.getSelector().wakeup();
    }

    @Override
    public void close()
    {
        LOGGER.debug("Closing " + _remoteSocketAddress);
        if(_closed.compareAndSet(false,true))
        {
            _stateChanged.set(true);
            _connection.getSelector().wakeup();
        }
    }

    private boolean doWrite() throws IOException
    {

        ByteBuffer[] bufArray = new ByteBuffer[_buffers.size()];
        Iterator<ByteBuffer> bufferIterator = _buffers.iterator();
        for (int i = 0; i < bufArray.length; i++)
        {
            bufArray[i] = bufferIterator.next();
        }

        int byteBuffersWritten = 0;

        if(_transportEncryption == TransportEncryption.NONE)
        {


            long written = _socketChannel.write(bufArray);
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Written " + written + " bytes");
            }

            for (ByteBuffer buf : bufArray)
            {
                if (buf.remaining() == 0)
                {
                    byteBuffersWritten++;
                    _buffers.poll();
                }
            }


            return bufArray.length == byteBuffersWritten;
        }
        else if(_transportEncryption == TransportEncryption.TLS)
        {
            int remaining = 0;
            do
            {
                if(_sslEngine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_UNWRAP)
                {
                    _workDone = true;
                    final ByteBuffer netBuffer = ByteBuffer.allocate(_sslEngine.getSession().getPacketBufferSize());
                    _status = _sslEngine.wrap(bufArray, netBuffer);
                    runSSLEngineTasks(_status);

                    netBuffer.flip();
                    remaining = netBuffer.remaining();
                    if (remaining != 0)
                    {
                        _encryptedOutput.add(netBuffer);
                    }
                    for (ByteBuffer buf : bufArray)
                    {
                        if (buf.remaining() == 0)
                        {
                            byteBuffersWritten++;
                            _buffers.poll();
                        }
                    }
                }

            }
            while(remaining != 0 && _sslEngine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_UNWRAP);
            ByteBuffer[] encryptedBuffers = _encryptedOutput.toArray(new ByteBuffer[_encryptedOutput.size()]);
            long written  = _socketChannel.write(encryptedBuffers);
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Written " + written + " encrypted bytes");
            }
            ListIterator<ByteBuffer> iter = _encryptedOutput.listIterator();
            while(iter.hasNext())
            {
                ByteBuffer buf = iter.next();
                if(buf.remaining() == 0)
                {
                    iter.remove();
                }
                else
                {
                    break;
                }
            }

            return bufArray.length == byteBuffersWritten;

        }
        else
        {
            return true;
        }
    }

    private boolean doRead() throws IOException
    {
        boolean readData = false;
        if(_transportEncryption == TransportEncryption.NONE)
        {
            int remaining = 0;
            while (remaining == 0 && !_closed.get())
            {
                if (_currentBuffer == null || _currentBuffer.remaining() == 0)
                {
                    _currentBuffer = ByteBuffer.allocate(_receiveBufSize);
                }
                int read = _socketChannel.read(_currentBuffer);
                if(read > 0)
                {
                    readData = true;
                }
                if (read == -1)
                {
                    _closed.set(true);
                }
                remaining = _currentBuffer.remaining();
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Read " + read + " byte(s)");
                }
                ByteBuffer dup = _currentBuffer.duplicate();
                dup.flip();
                _currentBuffer = _currentBuffer.slice();
                _receiver.received(dup);
            }
        }
        else if(_transportEncryption == TransportEncryption.TLS)
        {
            int read = 1;
            while(!_closed.get() && read > 0  && _sslEngine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_WRAP && (_status == null || _status.getStatus() != SSLEngineResult.Status.CLOSED))
            {
                read = _socketChannel.read(_netInputBuffer);
                if (read == -1)
                {
                    _closed.set(true);
                }
                else if(read > 0)
                {
                    readData = true;
                }
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Read " + read + " encrypted bytes ");
                }

                _netInputBuffer.flip();


                int unwrapped = 0;
                boolean tasksRun;
                do
                {
                    ByteBuffer appInputBuffer =
                            ByteBuffer.allocate(_sslEngine.getSession().getApplicationBufferSize() + 50);

                    _status = _sslEngine.unwrap(_netInputBuffer, appInputBuffer);
                    tasksRun = runSSLEngineTasks(_status);

                    appInputBuffer.flip();
                    unwrapped = appInputBuffer.remaining();
                    if(unwrapped > 0)
                    {
                        readData = true;
                    }
                    _receiver.received(appInputBuffer);
                }
                while(unwrapped > 0 || tasksRun);

                _netInputBuffer.compact();

            }
        }
        else
        {
            int read = 1;
            while (!_closed.get() && read > 0)
            {

                read = _socketChannel.read(_netInputBuffer);
                if (read == -1)
                {
                    _closed.set(true);
                }

                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Read " + read + " possibly encrypted bytes " + _netInputBuffer);
                }

                if (_netInputBuffer.position() >= NUMBER_OF_BYTES_FOR_TLS_CHECK)
                {
                    _netInputBuffer.flip();
                    final byte[] headerBytes = new byte[NUMBER_OF_BYTES_FOR_TLS_CHECK];
                    ByteBuffer dup = _netInputBuffer.duplicate();
                    dup.get(headerBytes);

                    _transportEncryption =  looksLikeSSL(headerBytes) ? TransportEncryption.TLS : TransportEncryption.NONE;
                    LOGGER.debug("Identified transport encryption as " + _transportEncryption);

                    if (_transportEncryption == TransportEncryption.NONE)
                    {
                        _receiver.received(_netInputBuffer);
                    }
                    else
                    {
                        _onTransportEncryptionAction.run();
                        _netInputBuffer.compact();
                        readData = doRead();
                    }
                    break;
                }
            }
        }
        return readData;
    }

    private boolean runSSLEngineTasks(final SSLEngineResult status)
    {
        if(status.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK)
        {
            Runnable task;
            while((task = _sslEngine.getDelegatedTask()) != null)
            {
                task.run();
            }
            return true;
        }
        return false;
    }

    private boolean looksLikeSSL(byte[] headerBytes)
    {
        return looksLikeSSLv3ClientHello(headerBytes) || looksLikeSSLv2ClientHello(headerBytes);
    }

    private boolean looksLikeSSLv3ClientHello(byte[] headerBytes)
    {
        return headerBytes[0] == 22 && // SSL Handshake
               (headerBytes[1] == 3 && // SSL 3.0 / TLS 1.x
                (headerBytes[2] == 0 || // SSL 3.0
                 headerBytes[2] == 1 || // TLS 1.0
                 headerBytes[2] == 2 || // TLS 1.1
                 headerBytes[2] == 3)) && // TLS1.2
               (headerBytes[5] == 1); // client_hello
    }

    private boolean looksLikeSSLv2ClientHello(byte[] headerBytes)
    {
        return headerBytes[0] == -128 &&
               headerBytes[3] == 3 && // SSL 3.0 / TLS 1.x
               (headerBytes[4] == 0 || // SSL 3.0
                headerBytes[4] == 1 || // TLS 1.0
                headerBytes[4] == 2 || // TLS 1.1
                headerBytes[4] == 3);
    }

    public Principal getPeerPrincipal()
    {

        if (_sslEngine != null)
        {
            try
            {
                return _sslEngine.getSession().getPeerPrincipal();
            }
            catch (SSLPeerUnverifiedException e)
            {
                return null;
            }
        }

        return null;
    }

    public boolean canRead()
    {
        return true;
    }

    public boolean waitingForWrite()
    {
        return !_fullyWritten;
    }

    public boolean isStateChanged()
    {
        return _stateChanged.get();
    }

}
