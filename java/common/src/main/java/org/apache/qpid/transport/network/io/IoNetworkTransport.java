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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;

import org.apache.qpid.ssl.SSLContextFactory;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.OutgoingNetworkTransport;
import org.apache.qpid.transport.util.Logger;

public class IoNetworkTransport implements OutgoingNetworkTransport
{
    static
    {
        org.apache.mina.common.ByteBuffer.setAllocator
            (new org.apache.mina.common.SimpleByteBufferAllocator());
        org.apache.mina.common.ByteBuffer.setUseDirectBuffers
            (Boolean.getBoolean("amqj.enableDirectBuffers"));
    }

    private static final Logger LOGGER = Logger.get(IoNetworkTransport.class);

    private Socket _socket;
    private IoNetworkConnection _connection;
    private long _timeout = 60000;
    
    public NetworkConnection connect(ConnectionSettings settings, Receiver<ByteBuffer> delegate, SSLContextFactory sslFactory)
    {
        int sendBufferSize = settings.getWriteBufferSize();
        int receiveBufferSize = settings.getReadBufferSize();
        
        try
        {
            _socket = new Socket();
            _socket.setReuseAddress(true);
            _socket.setTcpNoDelay(settings.isTcpNodelay());
            _socket.setSendBufferSize(sendBufferSize);
            _socket.setReceiveBufferSize(receiveBufferSize);

            LOGGER.debug("SO_RCVBUF : %s", _socket.getReceiveBufferSize());
            LOGGER.debug("SO_SNDBUF : %s", _socket.getSendBufferSize());

            InetAddress address = InetAddress.getByName(settings.getHost());

            _socket.connect(new InetSocketAddress(address, settings.getPort()));
        }
        catch (SocketException e)
        {
            throw new TransportException("Error connecting to broker", e);
        }
        catch (IOException e)
        {
            throw new TransportException("Error connecting to broker", e);
        }

        try
        {
            _connection = new IoNetworkConnection(_socket, delegate, sendBufferSize, receiveBufferSize, _timeout);
        }
        catch(Exception e)
        {
            try
            {
                _socket.close();
            }
            catch(IOException ioe)
            {
                //ignored, throw based on original exception
            }

            throw new TransportException("Error creating network connection", e);
        }

        return _connection;
    }

    public void close()
    {
        _connection.close();
    }

    public NetworkConnection getConnection()
    {
        return _connection;
    }
}
