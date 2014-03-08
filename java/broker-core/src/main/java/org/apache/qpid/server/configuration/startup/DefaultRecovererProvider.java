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

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.model.AccessControlProvider;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.AccessControlProviderFactory;
import org.apache.qpid.server.model.adapter.AuthenticationProviderFactory;
import org.apache.qpid.server.model.adapter.GroupProviderFactory;
import org.apache.qpid.server.model.adapter.PortFactory;
import org.apache.qpid.server.configuration.store.StoreConfigurationChangeListener;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.plugin.AccessControlFactory;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.plugin.GroupManagerFactory;
import org.apache.qpid.server.plugin.PluginFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class DefaultRecovererProvider implements RecovererProvider
{

    private final StatisticsGatherer _brokerStatisticsGatherer;
    private final VirtualHostRegistry _virtualHostRegistry;
    private final LogRecorder _logRecorder;
    private final AuthenticationProviderFactory _authenticationProviderFactory;
    private final AccessControlProviderFactory _accessControlProviderFactory;
    private final PortFactory _portFactory;
    private final GroupProviderFactory _groupProviderFactory;
    private final QpidServiceLoader<PluginFactory> _pluginFactoryServiceLoader;
    private final TaskExecutor _taskExecutor;
    private final BrokerOptions _brokerOptions;
    private final StoreConfigurationChangeListener _storeChangeListener;

    public DefaultRecovererProvider(StatisticsGatherer brokerStatisticsGatherer,
                                    VirtualHostRegistry virtualHostRegistry,
                                    LogRecorder logRecorder,
                                    TaskExecutor taskExecutor,
                                    BrokerOptions brokerOptions,
                                    StoreConfigurationChangeListener storeChangeListener)
    {
        _authenticationProviderFactory = new AuthenticationProviderFactory(new QpidServiceLoader<AuthenticationManagerFactory>());
        _accessControlProviderFactory = new AccessControlProviderFactory(new QpidServiceLoader<AccessControlFactory>());
        _groupProviderFactory = new GroupProviderFactory(new QpidServiceLoader<GroupManagerFactory>());
        _portFactory = new PortFactory();
        _brokerStatisticsGatherer = brokerStatisticsGatherer;
        _virtualHostRegistry = virtualHostRegistry;
        _logRecorder = logRecorder;
        _pluginFactoryServiceLoader = new QpidServiceLoader<PluginFactory>();
        _taskExecutor = taskExecutor;
        _brokerOptions = brokerOptions;
        _storeChangeListener = storeChangeListener;
    }

    @Override
    public ConfiguredObjectRecoverer<?> getRecoverer(String type)
    {
        if (Broker.class.getSimpleName().equals(type))
        {
            return new BrokerRecoverer(_authenticationProviderFactory, _groupProviderFactory, _accessControlProviderFactory, _portFactory,
                    _brokerStatisticsGatherer, _virtualHostRegistry, _logRecorder,
                    _taskExecutor, _brokerOptions, _storeChangeListener);
        }
        else if(VirtualHost.class.getSimpleName().equals(type))
        {
            return new VirtualHostRecoverer(_brokerStatisticsGatherer);
        }
        else if(AccessControlProvider.class.getSimpleName().equals(type))
        {
            return new AccessControlProviderRecoverer(_accessControlProviderFactory);
        }
        else if(AuthenticationProvider.class.getSimpleName().equals(type))
        {
            return new AuthenticationProviderRecoverer(_authenticationProviderFactory, _storeChangeListener);
        }
        else if(Port.class.getSimpleName().equals(type))
        {
            return new PortRecoverer(_portFactory);
        }
        else if(GroupProvider.class.getSimpleName().equals(type))
        {
            return new GroupProviderRecoverer(_groupProviderFactory);
        }
        else if(KeyStore.class.getSimpleName().equals(type))
        {
            return new KeyStoreRecoverer();
        }
        else if(TrustStore.class.getSimpleName().equals(type))
        {
            return new TrustStoreRecoverer();
        }
        else if(PreferencesProvider.class.getSimpleName().equals(type))
        {
            return new PreferencesProviderRecoverer();
        }
        else if(Plugin.class.getSimpleName().equals(type))
        {
            return new PluginRecoverer(_pluginFactoryServiceLoader);
        }

        return null;
    }

}
