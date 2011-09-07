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
import java.net.*;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

import org.apache.qpid.protocol.ProtocolEngine;
import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.transport.*;
import org.apache.qpid.transport.network.IncomingNetworkTransport;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.OutgoingNetworkTransport;
import org.apache.qpid.transport.util.Logger;

public class IoNetworkTransport implements OutgoingNetworkTransport, IncomingNetworkTransport
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
    private AcceptingThread _acceptor;

    public NetworkConnection connect(ConnectionSettings settings, Receiver<ByteBuffer> delegate, SSLContext sslContext)
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
            _connection.start();
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
        if(_connection != null)
        {
            _connection.close();
        }
        if(_acceptor != null)
        {
            _acceptor.close();
        }
    }

    public NetworkConnection getConnection()
    {
        return _connection;
    }

    public void accept(NetworkTransportConfiguration config, ProtocolEngineFactory factory, SSLContext sslContext)
    {

        try
        {
            _acceptor = new AcceptingThread(config, factory, sslContext);

            _acceptor.start();
        }
        catch (IOException e)
        {
            throw new TransportException("Unable to start server socket", e);
        }


    }

    private class AcceptingThread extends Thread
    {
        private NetworkTransportConfiguration _config;
        private ProtocolEngineFactory _factory;
        private SSLContext _sslContent;
        private ServerSocket _serverSocket;

        private AcceptingThread(NetworkTransportConfiguration config,
                                ProtocolEngineFactory factory,
                                SSLContext sslContext)
                throws IOException
        {
            _config = config;
            _factory = factory;
            _sslContent = sslContext;

            InetSocketAddress address = new InetSocketAddress(config.getHost(), config.getPort());

            if(sslContext == null)
            {
                _serverSocket = new ServerSocket();
            }
            else
            {
                SSLServerSocketFactory socketFactory = sslContext.getServerSocketFactory();
                _serverSocket = socketFactory.createServerSocket();
            }

            _serverSocket.bind(address);
            _serverSocket.setReuseAddress(true);


        }


        /**
            Close the underlying ServerSocket if it has not already been closed.
         */
        public void close()
        {
            if (!_serverSocket.isClosed())
            {
                try
                {
                    _serverSocket.close();
                }
                catch (IOException e)
                {
                    throw new TransportException(e);
                }
            }
        }

        @Override
        public void run()
        {
            try
            {
                while (true)
                {
                    try
                    {
                        Socket socket = _serverSocket.accept();
                        socket.setTcpNoDelay(_config.getTcpNoDelay());

                        final Integer sendBufferSize = _config.getSendBufferSize();
                        final Integer receiveBufferSize = _config.getReceiveBufferSize();

                        socket.setSendBufferSize(sendBufferSize);
                        socket.setReceiveBufferSize(receiveBufferSize);

                        ProtocolEngine engine = _factory.newProtocolEngine();

                        NetworkConnection connection = new IoNetworkConnection(socket, engine, sendBufferSize, receiveBufferSize, _timeout);


                        engine.setNetworkConnection(connection, connection.getSender());

                        connection.start();


                    }
                    catch(RuntimeException e)
                    {
                        LOGGER.error(e, "Error in Acceptor thread " + _config.getPort());
                    }
                }
            }
            catch (IOException e)
            {
                LOGGER.debug(e, "SocketException - no new connections will be accepted on port "
                        + _config.getPort());
            }
        }


    }

}
