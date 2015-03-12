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
import java.util.Set;

import javax.net.ssl.SSLContext;

import org.slf4j.LoggerFactory;

import org.apache.qpid.configuration.CommonProperties;
import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.server.protocol.ServerProtocolEngine;
import org.apache.qpid.transport.NetworkTransportConfiguration;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.TransportEncryption;
import org.apache.qpid.transport.network.io.AbstractNetworkTransport;
import org.apache.qpid.transport.network.io.IdleTimeoutTicker;

public class NonBlockingNetworkTransport
{

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(AbstractNetworkTransport.class);
    private static final int TIMEOUT = Integer.getInteger(CommonProperties.IO_NETWORK_TRANSPORT_TIMEOUT_PROP_NAME,
                                                          CommonProperties.IO_NETWORK_TRANSPORT_TIMEOUT_DEFAULT);
    private static final int HANDSHAKE_TIMEOUT = Integer.getInteger(CommonProperties.HANDSHAKE_TIMEOUT_PROP_NAME ,
                                                                   CommonProperties.HANDSHAKE_TIMEOUT_DEFAULT);
    private SelectorThread _selector;


    private  Set<TransportEncryption> _encryptionSet;
    private volatile boolean _closed = false;
    private NetworkTransportConfiguration _config;
    private ProtocolEngineFactory _factory;
    private SSLContext _sslContext;
    private ServerSocketChannel _serverSocket;
    private int _timeout;

    public void close()
    {
        if(_selector != null)
        {
            try
            {
                if (_serverSocket != null)
                {
                    _selector.cancelAcceptingSocket(_serverSocket);
                    _serverSocket.close();
                }
            }
            catch (IOException e)
            {
                // TODO
                e.printStackTrace();
            }
            finally
            {

                _selector.close();
            }
        }
    }

    public void accept(NetworkTransportConfiguration config,
                       ProtocolEngineFactory factory,
                       SSLContext sslContext,
                       final Set<TransportEncryption> encryptionSet)
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

            _selector = new SelectorThread(config.getAddress().toString(), this);
            _selector.start();
            _selector.addAcceptingSocket(_serverSocket);
        }
        catch (IOException e)
        {
            throw new TransportException("Failed to start AMQP on port : " + config, e);
        }


    }

    public int getAcceptingPort()
    {
        return _serverSocket == null ? -1 : _serverSocket.socket().getLocalPort();
    }

    public void acceptSocketChannel(final SocketChannel socketChannel) throws IOException
    {
        final ServerProtocolEngine engine =
                (ServerProtocolEngine) _factory.newProtocolEngine(socketChannel.socket()
                                                                          .getRemoteSocketAddress());

        if(engine != null)
        {
            socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, _config.getTcpNoDelay());
            socketChannel.socket().setSoTimeout(1000 * HANDSHAKE_TIMEOUT);

            final Integer sendBufferSize = _config.getSendBufferSize();
            final Integer receiveBufferSize = _config.getReceiveBufferSize();

            socketChannel.setOption(StandardSocketOptions.SO_SNDBUF, sendBufferSize);
            socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);


            final IdleTimeoutTicker ticker = new IdleTimeoutTicker(engine, TIMEOUT);

            NonBlockingConnection connection =
                    new NonBlockingConnection(socketChannel,
                                              engine,
                                              sendBufferSize,
                                              receiveBufferSize,
                                              _timeout,
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

        }
        else
        {
            socketChannel.close();
        }
    }


}
