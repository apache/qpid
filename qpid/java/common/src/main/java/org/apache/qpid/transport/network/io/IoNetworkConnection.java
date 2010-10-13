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

import org.apache.qpid.thread.Threading;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.Transport;

/**
 * IoNetworkConnection
 */
public class IoNetworkConnection implements NetworkConnection
{
    private final int _sendBufferSize;
    private final Socket _socket;
    private final long _timeout;
    private final AtomicBoolean _closed = new AtomicBoolean(false);
    private final Thread _receiverThread;

    public IoNetworkConnection(Socket socket, Receiver<ByteBuffer> receiver,
                      int sendBufferSize, int receiveBufferSize, long timeout)
    {
        _sendBufferSize = sendBufferSize;
        _socket = socket;
        _timeout = timeout;
        
        try
        {
	        IoNetworkHandler handler = new IoNetworkHandler(socket, receiver, receiveBufferSize);
            _receiverThread = Threading.getThreadFactory().newThread(handler);
        }
        catch(Exception e)
        {
            throw new Error("Error creating IoNetworkConnection thread",e);
        }
        
        _receiverThread.setDaemon(true);
        _receiverThread.setName(String.format("IoNetworkConnection-%s", socket.getRemoteSocketAddress()));
        _receiverThread.start();
    }

    @Override
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
                    throw new TransportException("join timed out");
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

    @Override
    public SocketAddress getRemoteAddress()
    {
        return _socket.getRemoteSocketAddress();
    }

    @Override
    public Sender<ByteBuffer> getSender()
    {
        return new IoSender(_socket, 2 * _sendBufferSize, _timeout);
    }

    @Override
    public long getReadBytes()
    {
        return 0;
    }

    @Override
    public long getWrittenBytes()
    {
        return 0;
    }
}
