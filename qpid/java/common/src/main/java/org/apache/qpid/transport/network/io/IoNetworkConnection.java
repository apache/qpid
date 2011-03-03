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
package org.apache.qpid.transport.network.io;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.thread.Threading;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IoNetworkConnection
 */
public class IoNetworkConnection implements NetworkConnection
{
    private static final Logger _log = LoggerFactory.getLogger(IoNetworkConnection.class);
    private static final AtomicLong _id = new AtomicLong(0);

    private final int _sendBufferSize;
    private final Socket _socket;
    private final long _timeout;
    private final AtomicBoolean _closed = new AtomicBoolean(false);
    private final Thread _receiverThread;
    private Sender<ByteBuffer> _sender;

    public IoNetworkConnection(Socket socket, Receiver<ByteBuffer> receiver,
                      int sendBufferSize, int receiveBufferSize, long timeout)
    {
        _sendBufferSize = sendBufferSize;
        _socket = socket;
        _timeout = timeout;
        
        try
        {
	        IoNetworkHandler handler = new IoNetworkHandler(socket, receiver, receiveBufferSize);
            _receiverThread = Threading.getThreadFactory().createThread(handler);
        }
        catch(Exception e)
        {
            throw new Error("Error creating IoNetworkTransport thread",e);
        }
        
        // Start receiver thread as daemon
        _receiverThread.setDaemon(true);
        _receiverThread.setName(String.format("IoNetworkTransport-%d-%s", _id.getAndIncrement(), socket.getRemoteSocketAddress()));
        _receiverThread.start();

        // Create sender
        _sender = new IoSender(_socket, 2 * _sendBufferSize, _timeout);
    }

    public void close()
    {
        if (!_closed.getAndSet(true))
        {
            try
            {
                if (Transport.WINDOWS)
                {
                   _socket.close();
                }
                else
                {
                    _socket.shutdownInput();
                }
                _receiverThread.join(_timeout);
                if (_receiverThread.isAlive())
                {
                    throw new TransportException("receiverThread join timed out");
                }
                if (_sender != null)
                {
	                _sender.close();
	                _sender = null;
                }
            }
            catch (InterruptedException e)
            {
                // ignore
            }
            catch (IOException e)
            {
                // ignore
            }
        }
    }

    public SocketAddress getRemoteAddress()
    {
        return _socket.getRemoteSocketAddress();
    }

    public SocketAddress getLocalAddress()
    {
        return _socket.getLocalSocketAddress();
    }

    public Sender<ByteBuffer> getSender()
    {
        return _sender;
    }

    public long getReadBytes()
    {
        return 0;
    }

    public long getWrittenBytes()
    {
        return 0;
    }
}
