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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;

import javax.net.ssl.SSLContext;

import org.slf4j.LoggerFactory;

import org.apache.qpid.configuration.CommonProperties;
import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.protocol.ServerProtocolEngine;
import org.apache.qpid.transport.NetworkTransportConfiguration;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.IncomingNetworkTransport;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.TransportEncryption;

public class NonBlockingNetworkTransport implements IncomingNetworkTransport
{

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(AbstractNetworkTransport.class);
    private static final int TIMEOUT = Integer.getInteger(CommonProperties.IO_NETWORK_TRANSPORT_TIMEOUT_PROP_NAME,
                                                          CommonProperties.IO_NETWORK_TRANSPORT_TIMEOUT_DEFAULT);
    private static final int HANDSHAKE_TIMEOUT = Integer.getInteger(CommonProperties.HANDSHAKE_TIMEOUT_PROP_NAME ,
                                                                   CommonProperties.HANDSHAKE_TIMEOUT_DEFAULT);
    private AcceptingThread _acceptor;

    protected NonBlockingConnection createNetworkConnection(final SocketChannel socket,
                                                            final ServerProtocolEngine engine,
                                                            final Integer sendBufferSize,
                                                            final Integer receiveBufferSize,
                                                            final int timeout,
                                                            final IdleTimeoutTicker ticker,
                                                            final Set<TransportEncryption> encryptionSet,
                                                            final SSLContext sslContext,
                                                            final boolean wantClientAuth,
                                                            final boolean needClientAuth,
                                                            final Runnable onTransportEncryptionAction)
    {
        return new NonBlockingConnection(socket, engine, sendBufferSize, receiveBufferSize, timeout, ticker, encryptionSet, sslContext, wantClientAuth, needClientAuth, onTransportEncryptionAction);
    }

    public void close()
    {
        if(_acceptor != null)
        {
            _acceptor.close();
        }
    }

    public void accept(NetworkTransportConfiguration config,
                       ProtocolEngineFactory factory,
                       SSLContext sslContext,
                       final Set<TransportEncryption> encryptionSet)
    {
        try
        {
            _acceptor = new AcceptingThread(config, factory, sslContext, encryptionSet);
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
        private final Set<TransportEncryption> _encryptionSet;
        private volatile boolean _closed = false;
        private final NetworkTransportConfiguration _config;
        private final ProtocolEngineFactory _factory;
        private final SSLContext _sslContext;
        private final ServerSocketChannel _serverSocket;
        private int _timeout;

        private AcceptingThread(NetworkTransportConfiguration config,
                                ProtocolEngineFactory factory,
                                SSLContext sslContext,
                                final Set<TransportEncryption> encryptionSet) throws IOException
        {
            _config = config;
            _factory = factory;
            _sslContext = sslContext;
            _timeout = TIMEOUT;

            InetSocketAddress address = config.getAddress();

            _serverSocket =  ServerSocketChannel.open();

            _serverSocket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            _serverSocket.bind(address);
            _encryptionSet = encryptionSet;
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

                        final ServerProtocolEngine engine =
                                (ServerProtocolEngine) _factory.newProtocolEngine(socket.socket().getRemoteSocketAddress());

                        if(engine != null)
                        {
                            socket.setOption(StandardSocketOptions.TCP_NODELAY, _config.getTcpNoDelay());
                            socket.socket().setSoTimeout(1000 * HANDSHAKE_TIMEOUT);

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
                                                            ticker,
                                                            _encryptionSet,
                                                            _sslContext,
                                                            _config.wantClientAuth(),
                                                            _config.needClientAuth(),
                                                            new Runnable()
                                                            {

                                                                @Override
                                                                public void run()
                                                                {
                                                                    engine.encryptedTransport();
                                                                }
                                                            });

                            engine.setNetworkConnection(connection, connection.getSender());
                            connection.setMaxReadIdle(HANDSHAKE_TIMEOUT);

                            ticker.setConnection(connection);

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
