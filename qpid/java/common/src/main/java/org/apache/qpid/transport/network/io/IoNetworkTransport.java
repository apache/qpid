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
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

import org.apache.qpid.ssl.SSLContextFactory;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.OutgoingNetworkTransport;
import org.apache.qpid.transport.network.Transport;
import org.apache.qpid.transport.util.Logger;

public class IoNetworkTransport implements OutgoingNetworkTransport
{
    private static final Logger _log = Logger.get(IoNetworkTransport.class);

    private static final int DEFAULT_BUFFER_SIZE = 32 * 1024;

    private Socket _socket;
    private IoNetworkConnection _connection;
    private long _timeout = 60000; 
    
    public NetworkConnection connect(ConnectionSettings settings, Receiver<ByteBuffer> delegate, SSLContextFactory sslfactory)
    {
        if (!settings.getProtocol().equalsIgnoreCase(Transport.TCP))
        {
            throw new TransportException("Invalid protocol: " + settings.getProtocol());
        }

        boolean noDelay = Boolean.getBoolean("amqj.tcpNoDelay");
        Integer sendBufferSize = Integer.getInteger("amqj.sendBufferSize", DEFAULT_BUFFER_SIZE);
        Integer receiveBufferSize = Integer.getInteger("amqj.receiveBufferSize", DEFAULT_BUFFER_SIZE);
 
        try
        {
            _socket = new Socket();
            
            _log.debug("default-SO_RCVBUF : %s", _socket.getReceiveBufferSize());
            _log.debug("default-SO_SNDBUF : %s", _socket.getSendBufferSize());

            _socket.setTcpNoDelay(noDelay);
            _socket.setSendBufferSize(sendBufferSize);
            _socket.setReceiveBufferSize(receiveBufferSize);
            _socket.setReuseAddress(true);

            _log.debug("new-SO_RCVBUF : %s", _socket.getReceiveBufferSize());
            _log.debug("new-SO_SNDBUF : %s", _socket.getSendBufferSize());

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

        _connection = new IoNetworkConnection(_socket, delegate, sendBufferSize, receiveBufferSize, _timeout);
 
        return _connection;
    }

    public void close()
    {
        if (_connection != null)
        {
	        _connection.close();
	        _connection = null;
        }
    }

    public SocketAddress getAddress()
    {
        return _socket.getLocalSocketAddress();
    }

    public boolean isCompatible(String protocol)
    {
        return (protocol.equalsIgnoreCase(Transport.TCP) ||
                 protocol.equalsIgnoreCase(Transport.SOCKET));
    }
}
