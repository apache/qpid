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
package org.apache.qpid.server.model.port;

import java.io.IOException;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.codehaus.jackson.map.ObjectMapper;

import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.plugin.ProtocolEngineCreator;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.plugin.TransportProviderFactory;
import org.apache.qpid.server.transport.AcceptingTransport;
import org.apache.qpid.server.transport.TransportProvider;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.network.security.ssl.QpidMultipleTrustManager;

public class AmqpPortImpl extends AbstractPortWithAuthProvider<AmqpPortImpl> implements AmqpPort<AmqpPortImpl>
{

    public static final String DEFAULT_BINDING_ADDRESS = "*";

    @ManagedAttributeField
    private boolean _tcpNoDelay;

    @ManagedAttributeField
    private int _sendBufferSize;

    @ManagedAttributeField
    private int _receiveBufferSize;

    private final Broker<?> _broker;
    private AcceptingTransport _transport;

    @ManagedObjectFactoryConstructor
    public AmqpPortImpl(Map<String, Object> attributes, Broker<?> broker)
    {
        super(attributes, broker);
        _broker = broker;
    }

    @Override
    public boolean isTcpNoDelay()
    {
        return _tcpNoDelay;
    }

    @Override
    public int getSendBufferSize()
    {
        return _sendBufferSize;
    }

    @Override
    public int getReceiveBufferSize()
    {
        return _receiveBufferSize;
    }

    @Override
    public VirtualHostImpl getVirtualHost(String name)
    {
        // TODO - aliases
        if(name == null || name.trim().length() == 0)
        {
            name = _broker.getDefaultVirtualHost();
        }

        return (VirtualHostImpl) _broker.findVirtualHostByName(name);
    }

    protected Set<Protocol> getDefaultProtocols()
    {
        Set<Protocol> defaultProtocols = EnumSet.of(Protocol.AMQP_0_8, Protocol.AMQP_0_9, Protocol.AMQP_0_9_1,
                                                    Protocol.AMQP_0_10, Protocol.AMQP_1_0);
        String excludedProtocols = System.getProperty(BrokerProperties.PROPERTY_BROKER_DEFAULT_AMQP_PROTOCOL_EXCLUDES);
        if (excludedProtocols != null)
        {
            String[] excludes = excludedProtocols.split(",");
            for (String exclude : excludes)
            {
                Protocol protocol = Protocol.valueOf(exclude);
                defaultProtocols.remove(protocol);
            }
        }
        String includedProtocols = System.getProperty(BrokerProperties.PROPERTY_BROKER_DEFAULT_AMQP_PROTOCOL_INCLUDES);
        if (includedProtocols != null)
        {
            String[] includes = includedProtocols.split(",");
            for (String include : includes)
            {
                Protocol protocol = Protocol.valueOf(include);
                defaultProtocols.add(protocol);
            }
        }
        return defaultProtocols;
    }


    @Override
    protected State onActivate()
    {
        if(_broker.isManagementMode())
        {
            return State.QUIESCED;
        }
        else
        {
            Collection<Transport> transports = getTransports();

            TransportProvider transportProvider = null;
            final HashSet<Transport> transportSet = new HashSet<Transport>(transports);
            for (TransportProviderFactory tpf : (new QpidServiceLoader()).instancesOf(TransportProviderFactory.class))
            {
                if (tpf.getSupportedTransports().contains(transports))
                {
                    transportProvider = tpf.getTransportProvider(transportSet);
                }
            }

            if (transportProvider == null)
            {
                throw new IllegalConfigurationException(
                        "No transport providers found which can satisfy the requirement to support the transports: "
                        + transports
                );
            }

            SSLContext sslContext = null;
            if (transports.contains(Transport.SSL) || transports.contains(Transport.WSS))
            {
                sslContext = createSslContext();
            }

            Protocol defaultSupportedProtocolReply = getDefaultAmqpSupportedReply();

            _transport = transportProvider.createTransport(transportSet,
                                                           sslContext,
                                                           this,
                                                           getAvailableProtocols(),
                                                           defaultSupportedProtocolReply);

            _transport.start();
            for (Transport transport : getTransports())
            {
                _broker.getEventLogger().message(BrokerMessages.LISTENING(String.valueOf(transport), getPort()));
            }

            return State.ACTIVE;
        }
    }

    @Override
    protected void onClose()
    {
        if (_transport != null)
        {
            for(Transport transport : getTransports())
            {
                _broker.getEventLogger().message(BrokerMessages.SHUTTING_DOWN(String.valueOf(transport), getPort()));
            }
            _transport.close();
        }
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
            throw new IllegalArgumentException("Unable to create SSLContext for key or trust store", e);
        }
    }

    private Protocol getDefaultAmqpSupportedReply()
    {
        String defaultAmqpSupportedReply = System.getProperty(BrokerProperties.PROPERTY_DEFAULT_SUPPORTED_PROTOCOL_REPLY);
        if (defaultAmqpSupportedReply != null && defaultAmqpSupportedReply.length() != 0)
        {
            return Protocol.valueOf("AMQP_" + defaultAmqpSupportedReply.substring(1));
        }
        return null;
    }

    public static Set<Protocol> getInstalledProtocols()
    {
        Set<Protocol> protocols = new HashSet<>();
        for(ProtocolEngineCreator installedEngine : (new QpidServiceLoader()).instancesOf(ProtocolEngineCreator.class))
        {
            protocols.add(installedEngine.getVersion());
        }
        return protocols;
    }

    @SuppressWarnings("unused")
    public static Collection<String> getAllAvailableProtocolCombinations()
    {
        Set<Protocol> protocols = getInstalledProtocols();

        Set<Set<String>> last = new HashSet<>();
        for(Protocol protocol : protocols)
        {
            last.add(Collections.singleton(protocol.name()));
        }

        Set<Set<String>> protocolCombinations = new HashSet<>(last);
        for(int i = 1; i < protocols.size(); i++)
        {
            Set<Set<String>> current = new HashSet<>();
            for(Set<String> set : last)
            {
                for(Protocol p : protocols)
                {
                    if(!set.contains(p.name()))
                    {
                        Set<String> potential = new HashSet<>(set);
                        potential.add(p.name());
                        current.add(potential);
                    }
                }
            }
            protocolCombinations.addAll(current);
            last = current;
        }
        Set<String> combinationsAsString = new HashSet<>(protocolCombinations.size());
        ObjectMapper mapper = new ObjectMapper();
        for(Set<String> combination : protocolCombinations)
        {
            try(StringWriter writer = new StringWriter())
            {
                mapper.writeValue(writer, combination);
                combinationsAsString.add(writer.toString());
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException("Unexpected IO Exception generating JSON string", e);
            }
        }
        return Collections.unmodifiableSet(combinationsAsString);
    }

    @SuppressWarnings("unused")
    public static Collection<String> getAllAvailableTransportCombinations()
    {
        Set<Set<Transport>> combinations = new HashSet<>();

        for(TransportProviderFactory providerFactory : (new QpidServiceLoader()).instancesOf(TransportProviderFactory.class))
        {
            combinations.addAll(providerFactory.getSupportedTransports());
        }

        Set<String> combinationsAsString = new HashSet<>(combinations.size());
        ObjectMapper mapper = new ObjectMapper();
        for(Set<Transport> combination : combinations)
        {
            try(StringWriter writer = new StringWriter())
            {
                mapper.writeValue(writer, combination);
                combinationsAsString.add(writer.toString());
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException("Unexpected IO Exception generating JSON string", e);
            }
        }
        return Collections.unmodifiableSet(combinationsAsString);
    }
}
