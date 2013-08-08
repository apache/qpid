/*
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
package org.apache.qpid.server.model.adapter;

import static org.apache.qpid.transport.ConnectionSettings.WILDCARD_ADDRESS;

import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.protocol.AmqpProtocolVersion;
import org.apache.qpid.server.protocol.MultiVersionProtocolEngineFactory;
import org.apache.qpid.transport.NetworkTransportConfiguration;
import org.apache.qpid.transport.network.IncomingNetworkTransport;
import org.apache.qpid.transport.network.security.ssl.QpidMultipleTrustManager;

public class AmqpPortAdapter extends PortAdapter
{
    private final Broker _broker;
    private IncomingNetworkTransport _transport;

    public AmqpPortAdapter(UUID id, Broker broker, Map<String, Object> attributes, Map<String, Object> defaultAttributes, TaskExecutor taskExecutor)
    {
        super(id, broker, attributes, defaultAttributes, taskExecutor);
        _broker = broker;
    }

    @Override
    protected void onActivate()
    {
        Collection<Transport> transports = getTransports();
        Set<AmqpProtocolVersion> supported = convertFromModelProtocolsToAmqp(getProtocols());

        SSLContext sslContext = null;
        if (transports.contains(Transport.SSL))
        {
            sslContext = createSslContext();
        }

        AmqpProtocolVersion defaultSupportedProtocolReply = getDefaultAmqpSupportedReply();

        String bindingAddress = (String) getAttribute(Port.BINDING_ADDRESS);
        if (WILDCARD_ADDRESS.equals(bindingAddress))
        {
            bindingAddress = null;
        }
        Integer port = (Integer) getAttribute(Port.PORT);
        InetSocketAddress bindingSocketAddress = null;
        if ( bindingAddress == null )
        {
            bindingSocketAddress = new InetSocketAddress(port);
        }
        else
        {
            bindingSocketAddress = new InetSocketAddress(bindingAddress, port);
        }

        final NetworkTransportConfiguration settings = new ServerNetworkTransportConfiguration(
                bindingSocketAddress, (Boolean)getAttribute(TCP_NO_DELAY),
                (Integer)getAttribute(SEND_BUFFER_SIZE), (Integer)getAttribute(RECEIVE_BUFFER_SIZE),
                (Boolean)getAttribute(NEED_CLIENT_AUTH), (Boolean)getAttribute(WANT_CLIENT_AUTH));

        _transport = org.apache.qpid.transport.network.Transport.getIncomingTransportInstance();
        final MultiVersionProtocolEngineFactory protocolEngineFactory = new MultiVersionProtocolEngineFactory(
                _broker, transports.contains(Transport.TCP) ? sslContext : null,
                settings.wantClientAuth(), settings.needClientAuth(),
                supported, defaultSupportedProtocolReply, this, transports.contains(Transport.TCP) ? Transport.TCP : Transport.SSL);

        _transport.accept(settings, protocolEngineFactory, transports.contains(Transport.TCP) ? null : sslContext);
        for(Transport transport : getTransports())
        {
            CurrentActor.get().message(BrokerMessages.LISTENING(String.valueOf(transport), getPort()));
        }
    }

    @Override
    protected void onStop()
    {
        if (_transport != null)
        {
            for(Transport transport : getTransports())
            {
                CurrentActor.get().message(BrokerMessages.SHUTTING_DOWN(String.valueOf(transport), getPort()));
            }
            _transport.close();
        }
    }

    private Set<AmqpProtocolVersion> convertFromModelProtocolsToAmqp(Collection<Protocol> modelProtocols)
    {
        Set<AmqpProtocolVersion> amqpProtocols = new HashSet<AmqpProtocolVersion>();
        for (Protocol protocol : modelProtocols)
        {
            amqpProtocols.add(protocol.toAmqpProtocolVersion());
        }
        return amqpProtocols;
    }

    private SSLContext createSslContext()
    {
        KeyStore keyStore = getKeyStore();
        Collection<TrustStore> trustStores = getTrustStores();

        boolean needClientCert = (Boolean)getAttribute(NEED_CLIENT_AUTH) || (Boolean)getAttribute(WANT_CLIENT_AUTH);
        if (needClientCert && trustStores.isEmpty())
        {
            throw new IllegalConfigurationException("Client certificate authentication is enabled on AMQP port '"
                    + this.getName() + "' but no trust store defined");
        }

        try
        {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            KeyManager[] keyManagers = keyStore.getKeyManagers();

            TrustManager[] trustManagers;
            if(trustStores == null || trustStores.isEmpty())
            {
                trustManagers = null;
            }
            else if(trustStores.size() == 1)
            {
                trustManagers = trustStores.iterator().next().getTrustManagers();
            }
            else
            {
                Collection<TrustManager> trustManagerList = new ArrayList<TrustManager>();
                final QpidMultipleTrustManager mulTrustManager = new QpidMultipleTrustManager();

                for(TrustStore ts : trustStores)
                {
                    TrustManager[] managers = ts.getTrustManagers();
                    if(managers != null)
                    {
                        for(TrustManager manager : managers)
                        {
                            if(manager instanceof X509TrustManager)
                            {
                                mulTrustManager.addTrustManager((X509TrustManager)manager);
                            }
                            else
                            {
                                trustManagerList.add(manager);
                            }
                        }
                    }
                }
                if(!mulTrustManager.isEmpty())
                {
                    trustManagerList.add(mulTrustManager);
                }
                trustManagers = trustManagerList.toArray(new TrustManager[trustManagerList.size()]);
            }
            sslContext.init(keyManagers, trustManagers, null);

            return sslContext;

        }
        catch (GeneralSecurityException e)
        {
            throw new RuntimeException("Unable to create SSLContext for key or trust store", e);
        }
    }

    private AmqpProtocolVersion getDefaultAmqpSupportedReply()
    {
        String defaultAmqpSupportedReply = System.getProperty(BrokerProperties.PROPERTY_DEFAULT_SUPPORTED_PROTOCOL_REPLY);
        if (defaultAmqpSupportedReply != null)
        {
            return AmqpProtocolVersion.valueOf(defaultAmqpSupportedReply);
        }
        return null;
    }

    class ServerNetworkTransportConfiguration implements NetworkTransportConfiguration
    {
        private final InetSocketAddress _bindingSocketAddress;
        private final Boolean _tcpNoDelay;
        private final Integer _sendBufferSize;
        private final Integer _receiveBufferSize;
        private final boolean _needClientAuth;
        private final boolean _wantClientAuth;

        public ServerNetworkTransportConfiguration(
                InetSocketAddress bindingSocketAddress, boolean tcpNoDelay,
                int sendBufferSize, int receiveBufferSize,
                boolean needClientAuth, boolean wantClientAuth)
        {
            _bindingSocketAddress = bindingSocketAddress;
            _tcpNoDelay = tcpNoDelay;
            _sendBufferSize = sendBufferSize;
            _receiveBufferSize = receiveBufferSize;
            _needClientAuth = needClientAuth;
            _wantClientAuth = wantClientAuth;
        }

        @Override
        public boolean wantClientAuth()
        {
            return _wantClientAuth;
        }

        @Override
        public boolean needClientAuth()
        {
            return _needClientAuth;
        }

        @Override
        public Boolean getTcpNoDelay()
        {
            return _tcpNoDelay;
        }

        @Override
        public Integer getSendBufferSize()
        {
            return _sendBufferSize;
        }

        @Override
        public Integer getReceiveBufferSize()
        {
            return _receiveBufferSize;
        }

        @Override
        public InetSocketAddress getAddress()
        {
            return _bindingSocketAddress;
        }
    };

    public String toString()
    {
        return getName();
    }
}
