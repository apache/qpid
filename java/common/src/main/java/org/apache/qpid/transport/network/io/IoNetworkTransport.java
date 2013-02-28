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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.Principal;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import org.apache.qpid.configuration.CommonProperties;
import org.apache.qpid.protocol.ProtocolEngine;
import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.NetworkTransportConfiguration;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.*;

import org.slf4j.LoggerFactory;

public class IoNetworkTransport implements OutgoingNetworkTransport, IncomingNetworkTransport
{
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(IoNetworkTransport.class);
    private static final int TIMEOUT = Integer.getInteger(CommonProperties.IO_NETWORK_TRANSPORT_TIMEOUT_PROP_NAME,
                                                              CommonProperties.IO_NETWORK_TRANSPORT_TIMEOUT_DEFAULT);

    private Socket _socket;
    private IoNetworkConnection _connection;
    private AcceptingThread _acceptor;

    public NetworkConnection connect(ConnectionSettings settings,
                                     Receiver<ByteBuffer> delegate,
                                     TransportActivity transportActivity)
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

            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("SO_RCVBUF : " + _socket.getReceiveBufferSize());
                LOGGER.debug("SO_SNDBUF : " + _socket.getSendBufferSize());
                LOGGER.debug("TCP_NODELAY : " + _socket.getTcpNoDelay());
            }

            InetAddress address = InetAddress.getByName(settings.getHost());

            _socket.connect(new InetSocketAddress(address, settings.getPort()), settings.getConnectTimeout());
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
            IdleTimeoutTicker ticker = new IdleTimeoutTicker(transportActivity, TIMEOUT);
            _connection = new IoNetworkConnection(_socket, delegate, sendBufferSize, receiveBufferSize, TIMEOUT, ticker);
            ticker.setConnection(_connection);
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
            throw new TransportException("Unable to start server socket", e);
        }
    }

    private class AcceptingThread extends Thread
    {
        private volatile boolean _closed = false;
        private NetworkTransportConfiguration _config;
        private ProtocolEngineFactory _factory;
        private SSLContext _sslContext;
        private ServerSocket _serverSocket;
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

            if(sslContext == null)
            {
                _serverSocket = new ServerSocket();
            }
            else
            {
                SSLServerSocketFactory socketFactory = _sslContext.getServerSocketFactory();
                _serverSocket = socketFactory.createServerSocket();

                if(config.needClientAuth())
                {
                    ((SSLServerSocket)_serverSocket).setNeedClientAuth(true);
                }
                else if(config.wantClientAuth())
                {
                    ((SSLServerSocket)_serverSocket).setWantClientAuth(true);
                }
            }

            _serverSocket.setReuseAddress(true);
            _serverSocket.bind(address);
        }


        /**
            Close the underlying ServerSocket if it has not already been closed.
         */
        public void close()
        {
            LOGGER.debug("Shutting down the Acceptor");
            _closed = true;

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
                while (!_closed)
                {
                    Socket socket = null;
                    try
                    {
                        socket = _serverSocket.accept();
                        socket.setTcpNoDelay(_config.getTcpNoDelay());
                        socket.setSoTimeout(_timeout);

                        final Integer sendBufferSize = _config.getSendBufferSize();
                        final Integer receiveBufferSize = _config.getReceiveBufferSize();

                        socket.setSendBufferSize(sendBufferSize);
                        socket.setReceiveBufferSize(receiveBufferSize);

                        ProtocolEngine engine = _factory.newProtocolEngine();

                        final IdleTimeoutTicker ticker = new IdleTimeoutTicker(engine, TIMEOUT);
                        NetworkConnection connection = new IoNetworkConnection(socket, engine, sendBufferSize, receiveBufferSize, _timeout,
                                                                               ticker);
                        ticker.setConnection(connection);

                        if(_sslContext != null)
                        {
                            try
                            {
                                Principal peerPrincipal = ((SSLSocket) socket).getSession().getPeerPrincipal();
                                connection.setPeerPrincipal(peerPrincipal);
                            }
                            catch(SSLPeerUnverifiedException e)
                            {
                                // ignore
                            }
                        }

                        engine.setNetworkConnection(connection, connection.getSender());

                        connection.start();
                    }
                    catch(RuntimeException e)
                    {
                        LOGGER.error("Error in Acceptor thread on address " + _config.getAddress(), e);
                        closeSocketIfNecessary(socket);
                    }
                    catch(IOException e)
                    {
                        if(!_closed)
                        {
                            LOGGER.error("Error in Acceptor thread on address " + _config.getAddress(), e);
                            closeSocketIfNecessary(socket);
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
                    LOGGER.debug("Acceptor exiting, no new connections will be accepted on address " + _config.getAddress());
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
