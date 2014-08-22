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
package org.apache.qpid.server.transport.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.model.port.HttpPort;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketHandler;

import org.apache.qpid.protocol.ProtocolEngine;
import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.protocol.MultiVersionProtocolEngineFactory;
import org.apache.qpid.server.transport.AcceptingTransport;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.network.NetworkConnection;

class WebSocketProvider implements AcceptingTransport
{
    public static final String AMQP_WEBSOCKET_SUBPROTOCOL = "AMQPWSB10";
    private final Transport _transport;
    private final SSLContext _sslContext;
    private final Port<?> _port;
    private final Set<Protocol> _supported;
    private final Protocol _defaultSupportedProtocolReply;
    private final ProtocolEngineFactory _factory;
    private Server _server;

    WebSocketProvider(final Transport transport,
                      final SSLContext sslContext,
                      final Port<?> port,
                      final Set<Protocol> supported,
                      final Protocol defaultSupportedProtocolReply)
    {
        _transport = transport;
        _sslContext = sslContext;
        _port = port;
        _supported = supported;
        _defaultSupportedProtocolReply = defaultSupportedProtocolReply;
        _factory = new MultiVersionProtocolEngineFactory(
                        _port.getParent(Broker.class), null,
                        (Boolean)_port.getAttribute(Port.WANT_CLIENT_AUTH),
                        (Boolean)_port.getAttribute(Port.NEED_CLIENT_AUTH),
                        _supported,
                        _defaultSupportedProtocolReply,
                        _port,
                        _transport);

    }

    @Override
    public void start()
    {
        _server = new Server();

        Connector connector = null;


        if (_transport == Transport.WS)
        {
            connector = new SelectChannelConnector();
        }
        else if (_transport == Transport.WSS)
        {
            SslContextFactory factory = new SslContextFactory();
            factory.setSslContext(_sslContext);
            factory.setNeedClientAuth(true);
            connector = new SslSelectChannelConnector(factory);
        }
        else
        {
            throw new IllegalArgumentException("Unexpected transport on port " + _port.getName() + ":" + _transport);
        }

        String bindingAddress = null;
        if (_port instanceof HttpPort)
        {
            bindingAddress = ((HttpPort)_port).getBindingAddress();
        }
        else if (_port instanceof AmqpPort)
        {
            bindingAddress = ((AmqpPort)_port).getBindingAddress();
        }

        if (bindingAddress != null && !bindingAddress.trim().equals("") && !bindingAddress.trim().equals("*"))
        {
            connector.setHost(bindingAddress.trim());
        }

        connector.setPort(_port.getPort());
        _server.addConnector(connector);

        WebSocketHandler wshandler = new WebSocketHandler()
        {
            @Override
            public WebSocket doWebSocketConnect(final HttpServletRequest request, final String protocol)
            {

                Principal principal = null;
                if(Collections.list(request.getAttributeNames()).contains("javax.servlet.request.X509Certificate"))
                {
                    X509Certificate[] certificates =
                            (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
                    if(certificates != null && certificates.length != 0)
                    {
                        principal = certificates[0].getSubjectDN();
                    }
                }

                SocketAddress remoteAddress = new InetSocketAddress(request.getRemoteHost(), request.getRemotePort());
                SocketAddress localAddress = new InetSocketAddress(request.getLocalName(), request.getLocalPort());
                return AMQP_WEBSOCKET_SUBPROTOCOL.equals(protocol) ? new AmqpWebSocket(_transport, localAddress, remoteAddress, principal) : null;
            }
        };

        _server.setHandler(wshandler);
        try
        {
            _server.start();
        }
        catch(RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServerScopedRuntimeException(e);
        }

    }

    @Override
    public void close()
    {

    }

    private class AmqpWebSocket implements WebSocket,WebSocket.OnBinaryMessage
    {
        private final SocketAddress _localAddress;
        private final SocketAddress _remoteAddress;
        private final Principal _userPrincipal;
        private Connection _connection;
        private final Transport _transport;
        private ProtocolEngine _engine;

        private AmqpWebSocket(final Transport transport,
                              final SocketAddress localAddress,
                              final SocketAddress remoteAddress,
                              final Principal userPrincipal)
        {
            _transport = transport;
            _localAddress = localAddress;
            _remoteAddress = remoteAddress;
            _userPrincipal = userPrincipal;
        }

        @Override
        public void onMessage(final byte[] data, final int offset, final int length)
        {
            _engine.received(ByteBuffer.wrap(data, offset, length).slice());
        }

        @Override
        public void onOpen(final Connection connection)
        {
            _connection = connection;

            _engine = _factory.newProtocolEngine();

            final ConnectionWrapper connectionWrapper =
                    new ConnectionWrapper(connection, _localAddress, _remoteAddress);
            connectionWrapper.setPeerPrincipal(_userPrincipal);
            _engine.setNetworkConnection(connectionWrapper, connectionWrapper.getSender());

        }

        @Override
        public void onClose(final int closeCode, final String message)
        {
            _engine.closed();
        }
    }

    private class ConnectionWrapper implements NetworkConnection, Sender<ByteBuffer>
    {
        private final WebSocket.Connection _connection;
        private final SocketAddress _localAddress;
        private final SocketAddress _remoteAddress;
        private Principal _principal;
        private int _maxWriteIdle;
        private int _maxReadIdle;

        public ConnectionWrapper(final WebSocket.Connection connection,
                                 final SocketAddress localAddress,
                                 final SocketAddress remoteAddress)
        {
            _connection = connection;
            _localAddress = localAddress;
            _remoteAddress = remoteAddress;
        }

        @Override
        public Sender<ByteBuffer> getSender()
        {
            return this;
        }

        @Override
        public void start()
        {

        }

        @Override
        public void setIdleTimeout(final int i)
        {

        }

        @Override
        public void send(final ByteBuffer msg)
        {
            try
            {
                _connection.sendMessage(msg.array(),msg.arrayOffset()+msg.position(),msg.remaining());
            }
            catch (IOException e)
            {
                close();
            }
        }

        @Override
        public void flush()
        {

        }

        @Override
        public void close()
        {
            _connection.close();
        }

        @Override
        public SocketAddress getRemoteAddress()
        {
            return _remoteAddress;
        }

        @Override
        public SocketAddress getLocalAddress()
        {
            return _localAddress;
        }

        @Override
        public void setMaxWriteIdle(final int sec)
        {
            _maxWriteIdle = sec;
        }

        @Override
        public void setMaxReadIdle(final int sec)
        {
            _maxReadIdle = sec;
        }

        @Override
        public Principal getPeerPrincipal()
        {
            return _principal;
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

        void setPeerPrincipal(final Principal peerPrincipal)
        {
            _principal = peerPrincipal;
        }
    }
}
