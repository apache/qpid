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

import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.network.NetworkConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IoNetworkConnection implements NetworkConnection
{
    private static final Logger LOGGER = LoggerFactory.getLogger(IoNetworkConnection.class);
    private final Socket _socket;
    private final long _timeout;
    private final IoSender _ioSender;
    private final IoReceiver _ioReceiver;

    public IoNetworkConnection(Socket socket, Receiver<ByteBuffer> delegate,
            int sendBufferSize, int receiveBufferSize, long timeout)
    {
        _socket = socket;
        _timeout = timeout;

        _ioReceiver = new IoReceiver(_socket, delegate, receiveBufferSize,_timeout);

        _ioSender = new IoSender(_socket, 2 * sendBufferSize, _timeout);

        _ioSender.registerCloseListener(_ioReceiver);

    }

    public void start()
    {
        _ioReceiver.initiate();
        _ioSender.initiate();
    }

    public Sender<ByteBuffer> getSender()
    {
        return _ioSender;
    }

    public void close()
    {
        try
        {
            _ioSender.close();
        }
        finally
        {
            _ioReceiver.close(false);
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

    public void setMaxWriteIdle(int sec)
    {
        // TODO implement support for setting heartbeating config in this way
        // Currently a socket timeout is used in IoSender
    }

    public void setMaxReadIdle(int sec)
    {
        // TODO implement support for setting heartbeating config in this way
        // Currently a socket timeout is used in IoSender
    }
}
