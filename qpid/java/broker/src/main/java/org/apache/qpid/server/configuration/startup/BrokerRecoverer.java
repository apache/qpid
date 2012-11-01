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
package org.apache.qpid.server.configuration.startup;

import java.util.Collection;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObjectType;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.AuthenticationProviderAdapter;
import org.apache.qpid.server.model.adapter.AuthenticationProviderFactory;
import org.apache.qpid.server.model.adapter.BrokerAdapter;
import org.apache.qpid.server.model.adapter.PortAdapter;
import org.apache.qpid.server.model.adapter.PortFactory;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.group.GroupPrincipalAccessor;

public class BrokerRecoverer
{
    private static final Logger LOGGER = Logger.getLogger(BrokerRecoverer.class);

    private final IApplicationRegistry _registry;
    private final VirtualHostRecoverer _virtualHostRecoverer;
    private final PortRecoverer _portRecoverer;
    private final AuthenticationProviderRecoverer _authenticationProviderRecoverer;

    private final AuthenticationProviderFactory _authenticationProviderFactory;

    private final PortFactory _portFactory;

    private final GroupProviderRecoverer _groupProviderRecoverer;

    public BrokerRecoverer(PortRecoverer portRecoverer, VirtualHostRecoverer virtualHostRecoverer,
            AuthenticationProviderRecoverer authenticationProviderRecoverer,
            AuthenticationProviderFactory authenticationProviderFactory, PortFactory portFactory,
            GroupProviderRecoverer groupProviderRecoverer, IApplicationRegistry registry)
    {
        _registry = registry;
        _virtualHostRecoverer = virtualHostRecoverer;
        _portRecoverer = portRecoverer;
        _portFactory = portFactory;
        _authenticationProviderRecoverer = authenticationProviderRecoverer;
        _authenticationProviderFactory = authenticationProviderFactory;
        _groupProviderRecoverer = groupProviderRecoverer;
    }

    public Broker create(ConfigurationEntry entry)
    {
        BrokerAdapter broker = new BrokerAdapter(entry.getId(), _registry, _authenticationProviderFactory, _portFactory);
        Map<ConfiguredObjectType, Collection<ConfigurationEntry>> childEntries = entry.getChildren();
        recoverVirtualHosts(broker, childEntries);
        recoverPorts(broker, childEntries);
        recoverGroupProviders(broker, childEntries);
        recoverAuthenticationProviders(broker, childEntries);

        wireUpAuthenticationProviders(broker, entry.getAttributesAsAttributeMap());

        return broker;
    }

    // XXX unit test this
    private void wireUpAuthenticationProviders(BrokerAdapter broker, AttributeMap brokerAttributes)
    {
        AuthenticationProvider defaultAuthenticationProvider = null;
        Collection<AuthenticationProvider> authenticationProviders = broker.getAuthenticationProviders();
        int numberOfAuthenticationProviders = authenticationProviders.size();
        if (numberOfAuthenticationProviders == 0)
        {
            LOGGER.error("No authentication providers configured");
            return;
            // XXX reinstate when wire-up has been separated from createion: throw new IllegalConfigurationException("No authentication providers configured");
        }
        else if (numberOfAuthenticationProviders == 1)
        {
            defaultAuthenticationProvider = authenticationProviders.iterator().next();
        }
        else
        {
            String name = brokerAttributes.getStringAttribute(Broker.DEFAULT_AUTHENTICATION_PROVIDER);
            defaultAuthenticationProvider = getAuthenticationProviderByName(broker, name);
        }
        broker.setDefaultAuthenticationProvider(defaultAuthenticationProvider);

        GroupPrincipalAccessor groupPrincipalAccessor = new GroupPrincipalAccessor(broker.getGroupProviders());
        for (AuthenticationProvider authenticationProvider : authenticationProviders)
        {
            // XXX : review this cast
            if (authenticationProvider instanceof AuthenticationProviderAdapter)
            {
                ((AuthenticationProviderAdapter<?>)authenticationProvider).setGroupAccessor(groupPrincipalAccessor);
            }
        }
        Collection<Port> ports = broker.getPorts();
        for (Port port : ports)
        {
            String authenticationProviderName = port.getAuthenticationManager();
            AuthenticationProvider provider = null;
            if (authenticationProviderName != null)
            {
                provider = getAuthenticationProviderByName(broker, authenticationProviderName);
            }
            else
            {
                provider = defaultAuthenticationProvider;
            }
            ((PortAdapter)port).setAuthenticationProvider(provider);
        }
    }

    private AuthenticationProvider getAuthenticationProviderByName(BrokerAdapter broker, String authenticationProviderName)
    {
        AuthenticationProvider provider = broker.getAuthenticationProviderByName(authenticationProviderName);
        if (provider == null)
        {
            throw new IllegalConfigurationException("Cannot find the authentication provider with name: " + authenticationProviderName);
        }
        return provider;
    }

    private void recoverPorts(BrokerAdapter broker, Map<ConfiguredObjectType, Collection<ConfigurationEntry>> childEntries)
    {
        Collection<ConfigurationEntry> portEntries = childEntries.get(ConfiguredObjectType.PORT);
        if (portEntries != null)
        {
            for (ConfigurationEntry portEntry : portEntries)
            {
                Port port = _portRecoverer.create(portEntry, broker);
                broker.addPort(port);
            }
        }
    }

    private void recoverVirtualHosts(BrokerAdapter broker, Map<ConfiguredObjectType, Collection<ConfigurationEntry>> childEntries)
    {
        Collection<ConfigurationEntry> virtualHostEntries = childEntries.get(ConfiguredObjectType.VIRTUAL_HOST);
        if (virtualHostEntries != null)
        {
            for (ConfigurationEntry virtualHostEntry : virtualHostEntries)
            {
                VirtualHost host = _virtualHostRecoverer.create(virtualHostEntry, broker);
                broker.addVirtualHost(host);
            }
        }
    }

    private void recoverAuthenticationProviders(BrokerAdapter broker,
            Map<ConfiguredObjectType, Collection<ConfigurationEntry>> childEntries)
    {
        Collection<ConfigurationEntry> entries = childEntries.get(ConfiguredObjectType.AUTHENTICATION_PROVIDER);
        if (entries != null)
        {
            for (ConfigurationEntry entry : entries)
            {
                AuthenticationProvider authenticationProvider = _authenticationProviderRecoverer.create(entry, broker);
                broker.addAuthenticationProvider(authenticationProvider);
            }
        }
    }

    private void recoverGroupProviders(BrokerAdapter broker,
            Map<ConfiguredObjectType, Collection<ConfigurationEntry>> childEntries)
    {
        Collection<ConfigurationEntry> entries = childEntries.get(ConfiguredObjectType.GROUP_PROVIDER);
        if (entries != null)
        {
            for (ConfigurationEntry entry : entries)
            {
                GroupProvider groupProvider = _groupProviderRecoverer.create(entry, broker);
                broker.addGroupProvider(groupProvider);
            }
        }
    }
}
