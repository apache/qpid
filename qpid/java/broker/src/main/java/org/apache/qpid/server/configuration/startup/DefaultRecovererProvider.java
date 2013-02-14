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

import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.AuthenticationProviderFactory;
import org.apache.qpid.server.model.adapter.PortFactory;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
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
    private final RootMessageLogger _rootMessageLogger;
    private final AuthenticationProviderFactory _authenticationProviderFactory;
    private final PortFactory _portFactory;
    private final QpidServiceLoader<GroupManagerFactory> _groupManagerServiceLoader;
    private final QpidServiceLoader<PluginFactory> _pluginFactoryServiceLoader;
    private final TaskExecutor _taskExecutor;

    public DefaultRecovererProvider(StatisticsGatherer brokerStatisticsGatherer, VirtualHostRegistry virtualHostRegistry,
            LogRecorder logRecorder, RootMessageLogger rootMessageLogger, TaskExecutor taskExecutor)
    {
        _authenticationProviderFactory = new AuthenticationProviderFactory(new QpidServiceLoader<AuthenticationManagerFactory>());
        _portFactory = new PortFactory();
        _brokerStatisticsGatherer = brokerStatisticsGatherer;
        _virtualHostRegistry = virtualHostRegistry;
        _logRecorder = logRecorder;
        _rootMessageLogger = rootMessageLogger;
        _groupManagerServiceLoader = new QpidServiceLoader<GroupManagerFactory>();
        _pluginFactoryServiceLoader = new QpidServiceLoader<PluginFactory>();
        _taskExecutor = taskExecutor;
    }

    @Override
    public ConfiguredObjectRecoverer<?> getRecoverer(String type)
    {
        if (Broker.class.getSimpleName().equals(type))
        {
            return new BrokerRecoverer(_authenticationProviderFactory, _portFactory, _brokerStatisticsGatherer, _virtualHostRegistry,
                    _logRecorder, _rootMessageLogger, _taskExecutor);
        }
        else if(VirtualHost.class.getSimpleName().equals(type))
        {
            return new VirtualHostRecoverer(_brokerStatisticsGatherer);
        }
        else if(AuthenticationProvider.class.getSimpleName().equals(type))
        {
            return new AuthenticationProviderRecoverer(_authenticationProviderFactory);
        }
        else if(Port.class.getSimpleName().equals(type))
        {
            return new PortRecoverer(_portFactory);
        }
        else if(GroupProvider.class.getSimpleName().equals(type))
        {
            return new GroupProviderRecoverer(_groupManagerServiceLoader);
        }
        else if(KeyStore.class.getSimpleName().equals(type))
        {
            return new KeyStoreRecoverer();
        }
        else if(TrustStore.class.getSimpleName().equals(type))
        {
            return new TrustStoreRecoverer();
        }
        else if(Plugin.class.getSimpleName().equals(type))
        {
            return new PluginRecoverer(_pluginFactoryServiceLoader);
        }

        return null;
    }

}
