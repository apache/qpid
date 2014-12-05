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
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLContext;

import org.slf4j.LoggerFactory;

import org.apache.qpid.configuration.CommonProperties;
import org.apache.qpid.protocol.ProtocolEngine;
import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.NetworkTransportConfiguration;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.IncomingNetworkTransport;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.TransportActivity;

public class NonBlockingNetworkTransport implements IncomingNetworkTransport
{

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(AbstractNetworkTransport.class);
    private static final int TIMEOUT = Integer.getInteger(CommonProperties.IO_NETWORK_TRANSPORT_TIMEOUT_PROP_NAME,
                                                          CommonProperties.IO_NETWORK_TRANSPORT_TIMEOUT_DEFAULT);
    private static final int HANSHAKE_TIMEOUT = Integer.getInteger(CommonProperties.HANDSHAKE_TIMEOUT_PROP_NAME ,
                                                                   CommonProperties.HANDSHAKE_TIMEOUT_DEFAULT);
    private AcceptingThread _acceptor;

    private SocketChannel _socketChannel;
    private NonBlockingConnection _connection;

    public NetworkConnection connect(ConnectionSettings settings,
                                     Receiver<ByteBuffer> delegate,
                                     TransportActivity transportActivity)
    {
        int sendBufferSize = settings.getWriteBufferSize();
        int receiveBufferSize = settings.getReadBufferSize();

        try
        {
            _socketChannel = SocketChannel.open();
            _socketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            _socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, settings.isTcpNodelay());
            _socketChannel.setOption(StandardSocketOptions.SO_SNDBUF, sendBufferSize);
            _socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);

            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("SO_RCVBUF : " + _socketChannel.getOption(StandardSocketOptions.SO_RCVBUF));
                LOGGER.debug("SO_SNDBUF : " + _socketChannel.getOption(StandardSocketOptions.SO_SNDBUF));
                LOGGER.debug("TCP_NODELAY : " + _socketChannel.getOption(StandardSocketOptions.TCP_NODELAY));
            }

            InetAddress address = InetAddress.getByName(settings.getHost());

            _socketChannel.socket().connect(new InetSocketAddress(address, settings.getPort()),
                                            settings.getConnectTimeout());
        }
        catch (IOException e)
        {
            throw new TransportException("Error connecting to broker", e);
        }

        try
        {
            IdleTimeoutTicker ticker = new IdleTimeoutTicker(transportActivity, TIMEOUT);
            _connection = createNetworkConnection(_socketChannel, delegate, sendBufferSize, receiveBufferSize,
                                                  TIMEOUT, ticker);
            ticker.setConnection(_connection);
            _connection.start();
        }
        catch(Exception e)
        {
            try
            {
                _socketChannel.close();
            }
            catch(IOException ioe)
            {
                //ignored, throw based on original exception
            }

            throw new TransportException("Error creating network connection", e);
        }

        return _connection;
    }


    protected NonBlockingConnection createNetworkConnection(final SocketChannel socket,
                                                          final Receiver<ByteBuffer> engine,
                                                          final Integer sendBufferSize,
                                                          final Integer receiveBufferSize,
                                                          final int timeout,
                                                          final IdleTimeoutTicker ticker)
    {
        return new NonBlockingConnection(socket, engine, sendBufferSize, receiveBufferSize, timeout, ticker);
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

    public void accept(NetworkTransportConfiguration config,
                       ProtocolEngineFactory factory,
                       SSLContext sslContext)
    {
        try
        {
            _acceptor = new AcceptingThread(config, factory, sslContext);
            _acceptor.setDaemon(false);
            _acceptor.start();
        }
        catch (IOException e)
        {
            throw new TransportException("Failed to start AMQP on port : " + config, e);
        }
    }

    public int getAcceptingPort()
    {
        return _acceptor == null ? -1 : _acceptor.getPort();
    }

    private class AcceptingThread extends Thread
    {
        private volatile boolean _closed = false;
        private NetworkTransportConfiguration _config;
        private ProtocolEngineFactory _factory;
        private SSLContext _sslContext;
        private ServerSocketChannel _serverSocket;
        private int _timeout;

        private AcceptingThread(NetworkTransportConfiguration config,
                                ProtocolEngineFactory factory,
                                SSLContext sslContext) throws IOException
        {
            _config = config;
            _factory = factory;
            _sslContext = sslContext;
            _timeout = TIMEOUT;

            InetSocketAddress address = config.getAddress();

            _serverSocket =  ServerSocketChannel.open();

            _serverSocket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            _serverSocket.bind(address);
        }


        /**
         Close the underlying ServerSocket if it has not already been closed.
         */
        public void close()
        {
            LOGGER.debug("Shutting down the Acceptor");
            _closed = true;

            if (!_serverSocket.socket().isClosed())
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

        private int getPort()
        {
            return _serverSocket.socket().getLocalPort();
        }

        @Override
        public void run()
        {
            try
            {
                while (!_closed)
                {
                    SocketChannel socket = null;
                    try
                    {
                        socket = _serverSocket.accept();

                        ProtocolEngine engine = _factory.newProtocolEngine(socket.socket().getRemoteSocketAddress());

                        if(engine != null)
                        {
                            socket.setOption(StandardSocketOptions.TCP_NODELAY, _config.getTcpNoDelay());
                            socket.socket().setSoTimeout(1000 * HANSHAKE_TIMEOUT);

                            final Integer sendBufferSize = _config.getSendBufferSize();
                            final Integer receiveBufferSize = _config.getReceiveBufferSize();

                            socket.setOption(StandardSocketOptions.SO_SNDBUF, sendBufferSize);
                            socket.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);


                            final IdleTimeoutTicker ticker = new IdleTimeoutTicker(engine, TIMEOUT);

                            NetworkConnection connection =
                                    createNetworkConnection(socket,
                                                            engine,
                                                            sendBufferSize,
                                                            receiveBufferSize,
                                                            _timeout,
                                                            ticker);

                            connection.setMaxReadIdle(HANSHAKE_TIMEOUT);

                            ticker.setConnection(connection);

                            engine.setNetworkConnection(connection, connection.getSender());

                            connection.start();
                        }
                        else
                        {
                            socket.close();
                        }
                    }
                    catch(RuntimeException e)
                    {
                        LOGGER.error("Error in Acceptor thread on address " + _config.getAddress(), e);
                        closeSocketIfNecessary(socket.socket());
                    }
                    catch(IOException e)
                    {
                        if(!_closed)
                        {
                            LOGGER.error("Error in Acceptor thread on address " + _config.getAddress(), e);
                            closeSocketIfNecessary(socket.socket());
                            try
                            {
                                //Delay to avoid tight spinning the loop during issues such as too many open files
                                Thread.sleep(1000);
                            }
                            catch (InterruptedException ie)
                            {
                                LOGGER.debug("Stopping acceptor due to interrupt request");
                                _closed = true;
                            }
                        }
                    }
                }
            }
            finally
            {
                if(LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Acceptor exiting, no new connections will be accepted on address "
                                 + _config.getAddress());
                }
            }
        }

        private void closeSocketIfNecessary(final Socket socket)
        {
            if(socket != null)
            {
                try
                {
                    socket.close();
                }
                catch (IOException e)
                {
                    LOGGER.debug("Exception while closing socket", e);
                }
            }
        }

    }
}
