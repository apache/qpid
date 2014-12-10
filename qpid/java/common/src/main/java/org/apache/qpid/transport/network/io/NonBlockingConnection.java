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

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.Principal;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.Ticker;
import org.apache.qpid.transport.network.TransportEncryption;

public class NonBlockingConnection implements NetworkConnection
{
    private static final Logger LOGGER = LoggerFactory.getLogger(NonBlockingConnection.class);
    private final SocketChannel _socket;
    private final long _timeout;
    private final NonBlockingSenderReceiver _nonBlockingSenderReceiver;
    private int _maxReadIdle;
    private int _maxWriteIdle;
    private Principal _principal;
    private boolean _principalChecked;
    private final Object _lock = new Object();

    public NonBlockingConnection(SocketChannel socket,
                                 Receiver<ByteBuffer> delegate,
                                 int sendBufferSize,
                                 int receiveBufferSize,
                                 long timeout,
                                 Ticker ticker,
                                 final Set<TransportEncryption> encryptionSet,
                                 final SSLContext sslContext,
                                 final boolean wantClientAuth, final boolean needClientAuth)
    {
        _socket = socket;
        _timeout = timeout;

        _nonBlockingSenderReceiver = new NonBlockingSenderReceiver(_socket, delegate, receiveBufferSize, ticker, encryptionSet, sslContext, wantClientAuth, needClientAuth);

    }

    public void start()
    {
        _nonBlockingSenderReceiver.initiate();
    }

    public Sender<ByteBuffer> getSender()
    {
        return _nonBlockingSenderReceiver;
    }

    public void close()
    {
        _nonBlockingSenderReceiver.close();
    }

    public SocketAddress getRemoteAddress()
    {
        return _socket.socket().getRemoteSocketAddress();
    }

    public SocketAddress getLocalAddress()
    {
        return _socket.socket().getLocalSocketAddress();
    }

    public void setMaxWriteIdle(int sec)
    {
        _maxWriteIdle = sec;
    }

    public void setMaxReadIdle(int sec)
    {
        _maxReadIdle = sec;
    }

    @Override
    public Principal getPeerPrincipal()
    {
        synchronized (_lock)
        {
            if(!_principalChecked)
            {

                _principal =  _nonBlockingSenderReceiver.getPeerPrincipal();

                _principalChecked = true;
            }

            return _principal;
        }
    }

    @Override
    public int getMaxReadIdle()
    {
        return _maxReadIdle;
    }

    @Override
    public int getMaxWriteIdle()
    {
        return _maxWriteIdle;
    }
}
