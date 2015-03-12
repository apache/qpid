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
import java.net.SocketAddress;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.TreeSet;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.codehaus.jackson.map.ObjectMapper;

import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.logging.messages.PortMessages;
import org.apache.qpid.server.logging.subjects.PortLogSubject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.DefaultVirtualHostAlias;
import org.apache.qpid.server.model.HostNameAlias;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.VirtualHostAlias;
import org.apache.qpid.server.model.VirtualHostNameAlias;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.plugin.ProtocolEngineCreator;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.plugin.TransportProviderFactory;
import org.apache.qpid.server.transport.AcceptingTransport;
import org.apache.qpid.server.transport.TransportProvider;
import org.apache.qpid.server.util.PortUtil;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.network.security.ssl.QpidMultipleTrustManager;

public class AmqpPortImpl extends AbstractClientAuthCapablePortWithAuthProvider<AmqpPortImpl> implements AmqpPort<AmqpPortImpl>
{

    public static final String DEFAULT_BINDING_ADDRESS = "*";


    private static final Comparator<VirtualHostAlias> VIRTUAL_HOST_ALIAS_COMPARATOR = new Comparator<VirtualHostAlias>()
    {
        @Override
        public int compare(final VirtualHostAlias left, final VirtualHostAlias right)
        {
            int comparison = left.getPriority() - right.getPriority();
            if (comparison == 0)
            {
                long createCompare = left.getCreatedTime() - right.getCreatedTime();
                if (createCompare == 0)
                {
                    comparison = left.getName().compareTo(right.getName());
                }
                else
                {
                    comparison = createCompare < 0l ? -1 : 1;
                }
            }
            return comparison;
        }
    };

    @ManagedAttributeField
    private boolean _tcpNoDelay;

    @ManagedAttributeField
    private int _sendBufferSize;

    @ManagedAttributeField
    private int _receiveBufferSize;

    @ManagedAttributeField
    private String _bindingAddress;

    @ManagedAttributeField
    private int _maxOpenConnections;

    private final AtomicInteger _connectionCount = new AtomicInteger();
    private final AtomicBoolean _connectionCountWarningGiven = new AtomicBoolean();

    private final Broker<?> _broker;
    private AcceptingTransport _transport;
    private final AtomicBoolean _closing = new AtomicBoolean();
    private final SettableFuture _noConnectionsRemain = SettableFuture.create();

    @ManagedObjectFactoryConstructor
    public AmqpPortImpl(Map<String, Object> attributes, Broker<?> broker)
    {
        super(attributes, broker);
        _broker = broker;
    }


    @Override
    public String getBindingAddress()
    {
        return _bindingAddress;
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
    public int getMaxOpenConnections()
    {
        return _maxOpenConnections;
    }

    @Override
    protected void onCreate()
    {
        super.onCreate();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(VirtualHostAlias.NAME, "nameAlias");
        attributes.put(VirtualHostAlias.TYPE, VirtualHostNameAlias.TYPE_NAME);
        attributes.put(VirtualHostAlias.DURABLE, true);
        createVirtualHostAlias(attributes);

        attributes = new HashMap<>();
        attributes.put(VirtualHostAlias.NAME, "defaultAlias");
        attributes.put(VirtualHostAlias.TYPE, DefaultVirtualHostAlias.TYPE_NAME);
        attributes.put(VirtualHostAlias.DURABLE, true);
        createVirtualHostAlias(attributes);


        attributes = new HashMap<>();
        attributes.put(VirtualHostAlias.NAME, "hostnameAlias");
        attributes.put(VirtualHostAlias.TYPE, HostNameAlias.TYPE_NAME);
        attributes.put(VirtualHostAlias.DURABLE, true);
        createVirtualHostAlias(attributes);

    }

    @Override
    public VirtualHostImpl getVirtualHost(String name)
    {
        Collection<VirtualHostAlias> aliases = new TreeSet<>(VIRTUAL_HOST_ALIAS_COMPARATOR);

        aliases.addAll(getChildren(VirtualHostAlias.class));

        for(VirtualHostAlias alias : aliases)
        {
            VirtualHostNode vhn = alias.getVirtualHostNode(name);
            if (vhn != null)
            {
                return (VirtualHostImpl) vhn.getVirtualHost();
            }
        }
        return null;
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
                                                           getProtocols(),
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
    protected ListenableFuture<Void> beforeClose()
    {
        _closing.set(true);

        if (_connectionCount.get() == 0)
        {
            _noConnectionsRemain.set(null);
        }

        return _noConnectionsRemain;
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

    @Override
    public VirtualHostAlias createVirtualHostAlias(Map<String, Object> attributes)
    {
        VirtualHostAlias child = addVirtualHostAlias(attributes);
        childAdded(child);
        return child;
    }

    private VirtualHostAlias addVirtualHostAlias(Map<String,Object> attributes)
    {
        return getObjectFactory().create(VirtualHostAlias.class, attributes, this);
    }


    @Override
    public void validateOnCreate()
    {
        super.validateOnCreate();
        String bindingAddress = getBindingAddress();
        if (!PortUtil.isPortAvailable(bindingAddress, getPort()))
        {
            throw new IllegalConfigurationException(String.format("Cannot bind to port %d and binding address '%s'. Port is already is use.",
                    getPort(), bindingAddress == null || "".equals(bindingAddress) ? "*" : bindingAddress));
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


    public static String getInstalledProtocolsAsString()
    {
        Set<Protocol> installedProtocols = getInstalledProtocols();
        ObjectMapper mapper = new ObjectMapper();

        try(StringWriter output = new StringWriter())
        {
            mapper.writeValue(output, installedProtocols);
            return output.toString();
        }
        catch (IOException e)
        {
            throw new ServerScopedRuntimeException(e);
        }
    }

    @Override
    public int incrementConnectionCount()
    {
        int openConnections = _connectionCount.incrementAndGet();
        int maxOpenConnections = getMaxOpenConnections();
        if(maxOpenConnections > 0
           && openConnections > (maxOpenConnections * getContextValue(Integer.class, OPEN_CONNECTIONS_WARN_PERCENT)) / 100
           && _connectionCountWarningGiven.compareAndSet(false, true))
        {
            _broker.getEventLogger().message(new PortLogSubject(this),
                                             PortMessages.CONNECTION_COUNT_WARN(openConnections,
                                                                                getContextValue(Integer.class, OPEN_CONNECTIONS_WARN_PERCENT),
                                                                                maxOpenConnections));
        }
        return openConnections;
    }

    @Override
    public int decrementConnectionCount()
    {

        int openConnections = _connectionCount.decrementAndGet();
        int maxOpenConnections = getMaxOpenConnections();

        if(maxOpenConnections > 0
           && openConnections < (maxOpenConnections * square(getContextValue(Integer.class, OPEN_CONNECTIONS_WARN_PERCENT))) / 10000)
        {
           _connectionCountWarningGiven.compareAndSet(true,false);
        }

        if (_closing.get() && _connectionCount.get() == 0)
        {
            _noConnectionsRemain.set(null);
        }

        return openConnections;
    }

    private static int square(int val)
    {
        return val * val;
    }

    @Override
    public boolean canAcceptNewConnection(final SocketAddress remoteSocketAddress)
    {
        return !_closing.get() && ( _maxOpenConnections < 0 || _connectionCount.get() < _maxOpenConnections );
    }

    @Override
    public int getConnectionCount()
    {
        return _connectionCount.get();
    }
}
