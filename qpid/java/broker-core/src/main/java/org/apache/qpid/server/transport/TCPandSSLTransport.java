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

import static org.apache.qpid.transport.ConnectionSettings.WILDCARD_ADDRESS;

import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.Collection;
import java.util.Set;

import javax.net.ssl.SSLContext;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.protocol.MultiVersionProtocolEngineFactory;
import org.apache.qpid.transport.NetworkTransportConfiguration;
import org.apache.qpid.transport.network.TransportEncryption;

class TCPandSSLTransport implements AcceptingTransport
{
    private NonBlockingNetworkTransport _networkTransport;
    private Set<Transport> _transports;
    private SSLContext _sslContext;
    private InetSocketAddress _bindingSocketAddress;
    private AmqpPort<?> _port;
    private Set<Protocol> _supported;
    private Protocol _defaultSupportedProtocolReply;

    TCPandSSLTransport(final Set<Transport> transports,
                       final SSLContext sslContext,
                       final AmqpPort<?> port,
                       final Set<Protocol> supported,
                       final Protocol defaultSupportedProtocolReply)
    {
        _transports = transports;
        _sslContext = sslContext;
        _port = port;
        _supported = supported;
        _defaultSupportedProtocolReply = defaultSupportedProtocolReply;
    }

    @Override
    public void start()
    {
        String bindingAddress = _port.getBindingAddress();
        if (WILDCARD_ADDRESS.equals(bindingAddress))
        {
            bindingAddress = null;
        }
        int port = _port.getPort();
        if ( bindingAddress == null )
        {
            _bindingSocketAddress = new InetSocketAddress(port);
        }
        else
        {
            _bindingSocketAddress = new InetSocketAddress(bindingAddress, port);
        }

        final NetworkTransportConfiguration settings = new ServerNetworkTransportConfiguration();
        _networkTransport = new NonBlockingNetworkTransport();
        final MultiVersionProtocolEngineFactory protocolEngineFactory =
                new MultiVersionProtocolEngineFactory(
                _port.getParent(Broker.class),
                _supported,
                _defaultSupportedProtocolReply,
                _port,
                _transports.contains(Transport.TCP) ? Transport.TCP : Transport.SSL);

        EnumSet<TransportEncryption> encryptionSet = EnumSet.noneOf(TransportEncryption.class);
        if(_transports.contains(Transport.TCP))
        {
            encryptionSet.add(TransportEncryption.NONE);
        }
        if(_transports.contains(Transport.SSL))
        {
            encryptionSet.add(TransportEncryption.TLS);
        }
        _networkTransport.accept(settings, protocolEngineFactory, _sslContext, encryptionSet);
    }

    public int getAcceptingPort()
    {
        return _networkTransport.getAcceptingPort();
    }

    @Override
    public void close()
    {
        _networkTransport.close();
    }

    class ServerNetworkTransportConfiguration implements NetworkTransportConfiguration
    {
        public ServerNetworkTransportConfiguration()
        {
        }

        @Override
        public boolean wantClientAuth()
        {
            return _port.getWantClientAuth();
        }

        @Override
        public Collection<String> getEnabledCipherSuites()
        {
            return _port.getEnabledCipherSuites();
        }

        @Override
        public Collection<String> getDisabledCipherSuites()
        {
            return _port.getDisabledCipherSuites();
        }

        @Override
        public boolean needClientAuth()
        {
            return _port.getNeedClientAuth();
        }

        @Override
        public boolean getTcpNoDelay()
        {
            return _port.isTcpNoDelay();
        }

        @Override
        public int getSendBufferSize()
        {
            return _port.getSendBufferSize();
        }

        @Override
        public int getReceiveBufferSize()
        {
            return _port.getReceiveBufferSize();
        }

        @Override
        public InetSocketAddress getAddress()
        {
            return _bindingSocketAddress;
        }

        @Override
        public String toString()
        {
            return _port.toString();
        }
    }
}
