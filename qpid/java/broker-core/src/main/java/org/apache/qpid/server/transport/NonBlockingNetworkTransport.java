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
package org.apache.qpid.server.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.EnumSet;
import java.util.Set;

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.configuration.CommonProperties;
import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.server.protocol.MultiVersionProtocolEngineFactory;
import org.apache.qpid.server.protocol.ServerProtocolEngine;
import org.apache.qpid.transport.NetworkTransportConfiguration;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.TransportEncryption;
import org.apache.qpid.transport.network.io.AbstractNetworkTransport;
import org.apache.qpid.transport.network.io.IdleTimeoutTicker;

public class NonBlockingNetworkTransport
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractNetworkTransport.class);
    private static final int TIMEOUT = Integer.getInteger(CommonProperties.IO_NETWORK_TRANSPORT_TIMEOUT_PROP_NAME,
                                                          CommonProperties.IO_NETWORK_TRANSPORT_TIMEOUT_DEFAULT);
    private static final int HANDSHAKE_TIMEOUT = Integer.getInteger(CommonProperties.HANDSHAKE_TIMEOUT_PROP_NAME ,
                                                                   CommonProperties.HANDSHAKE_TIMEOUT_DEFAULT);
    private final Set<TransportEncryption> _encryptionSet;
    private final NetworkTransportConfiguration _config;
    private final ProtocolEngineFactory _factory;
    private final SSLContext _sslContext;
    private final ServerSocketChannel _serverSocket;
    private final int _timeout;

    private SelectorThread _selector;

    public NonBlockingNetworkTransport(final NetworkTransportConfiguration config,
                                       final MultiVersionProtocolEngineFactory factory,
                                       final SSLContext sslContext,
                                       final EnumSet<TransportEncryption> encryptionSet)
    {
        try
        {

            _config = config;
            _factory = factory;
            _sslContext = sslContext;
            _timeout = TIMEOUT;

            InetSocketAddress address = config.getAddress();

            _serverSocket =  ServerSocketChannel.open();

            _serverSocket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            _serverSocket.bind(address);
            _serverSocket.configureBlocking(false);
            _encryptionSet = encryptionSet;

        }
        catch (IOException e)
        {
            throw new TransportException("Failed to start AMQP on port : " + config, e);
        }

    }

    public void start()
    {
        try
        {
            _selector = new SelectorThread(this);
            _selector.start();
            _selector.addAcceptingSocket(_serverSocket);
        }
        catch (IOException e)
        {
            throw new TransportException("Failed to start", e);
        }
    }


    public void close()
    {
        if(_selector != null)
        {
            _selector.cancelAcceptingSocket(_serverSocket);
            try
            {
                _serverSocket.close();
            }
            catch (IOException e)
            {
                LOGGER.warn("Error closing the server socket for : " +  _config.getAddress().toString(), e);
            }
            finally
            {
                _selector.close();
                _selector = null;
            }
        }
    }

    public int getAcceptingPort()
    {
        return _serverSocket.socket().getLocalPort();
    }

    public NetworkTransportConfiguration getConfig()
    {
        return _config;
    }

    void acceptSocketChannel(final ServerSocketChannel serverSocketChannel)
    {
        SocketChannel socketChannel = null;
        boolean success = false;
        try
        {
            socketChannel = serverSocketChannel.accept();

            final ServerProtocolEngine engine =
                    (ServerProtocolEngine) _factory.newProtocolEngine(socketChannel.socket()
                                                                              .getRemoteSocketAddress());

            if(engine != null)
            {
                socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, _config.getTcpNoDelay());
                socketChannel.socket().setSoTimeout(1000 * HANDSHAKE_TIMEOUT);

                final int sendBufferSize = _config.getSendBufferSize();
                final int receiveBufferSize = _config.getReceiveBufferSize();

                socketChannel.setOption(StandardSocketOptions.SO_SNDBUF, sendBufferSize);
                socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);

                socketChannel.configureBlocking(false);

                final IdleTimeoutTicker ticker = new IdleTimeoutTicker(engine, _timeout);

                NonBlockingConnection connection =
                        new NonBlockingConnection(socketChannel,
                                                  engine,
                                                  receiveBufferSize,
                                                  ticker,
                                                  _encryptionSet,
                                                  _sslContext,
                                                  _config.wantClientAuth(),
                                                  _config.needClientAuth(),
                                                  _config.getEnabledCipherSuites(),
                                                  _config.getDisabledCipherSuites(),
                                                  new Runnable()
                                                  {

                                                      @Override
                                                      public void run()
                                                      {
                                                          engine.encryptedTransport();
                                                      }
                                                  },
                                                  _selector);

                engine.setNetworkConnection(connection, connection.getSender());
                connection.setMaxReadIdle(HANDSHAKE_TIMEOUT);

                ticker.setConnection(connection);

                connection.start();

                _selector.addConnection(connection);

                success = true;
            }
        }
        catch (IOException e)
        {
            LOGGER.error("Failed to process incoming socket", e);
        }
        finally
        {
            if (!success && socketChannel != null)
            {
                try
                {
                    socketChannel.close();
                }
                catch (IOException e)
                {
                    LOGGER.debug("Failed to close socket " + socketChannel, e);
                }
            }
        }
    }


}
