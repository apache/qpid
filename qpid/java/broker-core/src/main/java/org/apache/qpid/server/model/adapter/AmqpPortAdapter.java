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

import java.security.GeneralSecurityException;
import java.util.ArrayList;
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
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.plugin.TransportProviderFactory;
import org.apache.qpid.server.protocol.AmqpProtocolVersion;
import org.apache.qpid.server.transport.AcceptingTransport;
import org.apache.qpid.server.transport.TransportProvider;
import org.apache.qpid.transport.network.security.ssl.QpidMultipleTrustManager;

public class AmqpPortAdapter extends PortAdapter
{
    private final Broker _broker;
    private AcceptingTransport _transport;

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
}
