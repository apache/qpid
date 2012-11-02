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

import java.util.Map;

import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.model.ConfiguredObjectType;
import org.apache.qpid.server.model.adapter.AuthenticationProviderFactory;
import org.apache.qpid.server.model.adapter.PortFactory;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.plugin.GroupManagerFactory;
import org.apache.qpid.server.plugin.PluginFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.stats.StatisticsGatherer;

public class DefaultRecovererProvider implements RecovererProvider
{

    private final AuthenticationProviderFactory _authenticationProviderFactory;
    private final PortFactory _portFactory;
    private final IApplicationRegistry _registry;
    private final Map<String, VirtualHostConfiguration> _virtualHostConfiguration;
    private final QpidServiceLoader<GroupManagerFactory> _groupManagerServiceLoader;
    private final QpidServiceLoader<PluginFactory> _pluginFactoryServiceLoader;

    public DefaultRecovererProvider(IApplicationRegistry registry, Map<String, VirtualHostConfiguration> virtualHostConfiguration)
    {
        _authenticationProviderFactory = new AuthenticationProviderFactory(new QpidServiceLoader<AuthenticationManagerFactory>());
        _portFactory = new PortFactory(registry);
        _registry = registry;
        _virtualHostConfiguration = virtualHostConfiguration;
        _groupManagerServiceLoader = new QpidServiceLoader<GroupManagerFactory>();
        _pluginFactoryServiceLoader = new QpidServiceLoader<PluginFactory>();
    }

    @Override
    public ConfiguredObjectRecoverer<?> getRecoverer(ConfiguredObjectType type)
    {
        switch(type)
        {
        case BROKER:
            return new BrokerRecoverer(_authenticationProviderFactory, _portFactory, _registry);
        case VIRTUAL_HOST:
            return new VirtualHostRecoverer(_registry.getVirtualHostRegistry(),(StatisticsGatherer)_registry, _registry.getSecurityManager(), _virtualHostConfiguration);
        case AUTHENTICATION_PROVIDER:
            return new AuthenticationProviderRecoverer(_authenticationProviderFactory);
        case PORT:
            return new PortRecoverer(_portFactory);
        case GROUP_PROVIDER:
            return new GroupProviderRecoverer(_groupManagerServiceLoader);
        case PLUGIN:
            return new PluginRecoverer(_pluginFactoryServiceLoader);
        }
        throw new IllegalConfigurationException("Cannot create a recoverer for the type: " + type);
    }

}
