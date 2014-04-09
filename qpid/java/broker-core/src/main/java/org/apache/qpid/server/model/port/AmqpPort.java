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

import java.security.GeneralSecurityException;
import java.util.*;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.ManagedAttribute;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.plugin.TransportProviderFactory;
import org.apache.qpid.server.protocol.AmqpProtocolVersion;
import org.apache.qpid.server.transport.AcceptingTransport;
import org.apache.qpid.server.transport.TransportProvider;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.transport.network.security.ssl.QpidMultipleTrustManager;

@ManagedObject( category = false, type = "AMQP")
public class AmqpPort extends PortWithAuthProvider<AmqpPort>
{
    public static final String DEFAULT_AMQP_SEND_BUFFER_SIZE = "262144";
    public static final String DEFAULT_AMQP_RECEIVE_BUFFER_SIZE = "262144";

    public static final String DEFAULT_AMQP_TCP_NO_DELAY = "true";
    public static final String DEFAULT_AMQP_BINDING = "*";

    @ManagedAttributeField
    private boolean _tcpNoDelay;

    @ManagedAttributeField
    private int _sendBufferSize;

    @ManagedAttributeField
    private int _receiveBufferSize;

    private final Broker<?> _broker;
    private AcceptingTransport _transport;

    public AmqpPort(UUID id,
                    Broker<?> broker,
                    Map<String, Object> attributes,
                    TaskExecutor taskExecutor)
    {
        super(id, broker, attributes, defaults(attributes), taskExecutor);
        _broker = broker;
    }

    private static Map<String, Object> defaults(Map<String,Object> attributes)
    {
        Map<String,Object> defaults = new HashMap<String, Object>();

        defaults.put(BINDING_ADDRESS, DEFAULT_AMQP_BINDING);
        defaults.put(NAME, attributes.containsKey(BINDING_ADDRESS) ? attributes.get(BINDING_ADDRESS) : DEFAULT_AMQP_BINDING + ":" + attributes.get(PORT));
        defaults.put(PROTOCOLS, getDefaultProtocols());


        return defaults;
    }


    @ManagedAttribute( automate = true , defaultValue = DEFAULT_AMQP_TCP_NO_DELAY )
    public boolean isTcpNoDelay()
    {
        return _tcpNoDelay;
    }

    @ManagedAttribute( automate = true , defaultValue = DEFAULT_AMQP_SEND_BUFFER_SIZE )
    public int getSendBufferSize()
    {
        return _sendBufferSize;
    }

    @ManagedAttribute( automate = true , defaultValue = DEFAULT_AMQP_RECEIVE_BUFFER_SIZE )
    public int getReceiveBufferSize()
    {
        return _receiveBufferSize;
    }

    private static Set<Protocol> getDefaultProtocols()
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
    protected void onActivate()
    {
        Collection<Transport> transports = getTransports();
        Set<AmqpProtocolVersion> supported = convertFromModelProtocolsToAmqp(getProtocols());

        TransportProvider transportProvider = null;
        final HashSet<Transport> transportSet = new HashSet<Transport>(transports);
        for(TransportProviderFactory tpf : (new QpidServiceLoader<TransportProviderFactory>()).instancesOf(TransportProviderFactory.class))
        {
            if(tpf.getSupportedTransports().contains(transports))
            {
                transportProvider = tpf.getTransportProvider(transportSet);
            }
        }

        if(transportProvider == null)
        {
            throw new IllegalConfigurationException("No transport providers found which can satisfy the requirement to support the transports: " + transports);
        }

        SSLContext sslContext = null;
        if (transports.contains(Transport.SSL) || transports.contains(Transport.WSS))
        {
            sslContext = createSslContext();
        }

        AmqpProtocolVersion defaultSupportedProtocolReply = getDefaultAmqpSupportedReply();

        _transport = transportProvider.createTransport(transportSet,
                                                       sslContext,
                                                       this,
                                                       supported,
                                                       defaultSupportedProtocolReply);

        _transport.start();
        for(Transport transport : getTransports())
        {
            _broker.getEventLogger().message(BrokerMessages.LISTENING(String.valueOf(transport), getPort()));
        }
    }

    @Override
    protected void onStop()
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
            throw new ServerScopedRuntimeException("Unable to create SSLContext for key or trust store", e);
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

}
