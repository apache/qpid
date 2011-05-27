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
package org.apache.qpid.server.registry;

import java.net.InetSocketAddress;
import java.util.UUID;

import org.apache.qpid.qmf.QMFService;
import org.apache.qpid.server.configuration.BrokerConfig;
import org.apache.qpid.server.configuration.ConfigStore;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.configuration.ConfigurationManager;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.management.ManagedObjectRegistry;
import org.apache.qpid.server.plugins.PluginManager;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.auth.database.PrincipalDatabaseManager;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.transport.QpidAcceptor;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public interface IApplicationRegistry extends StatisticsGatherer
{
    /**
     * Initialise the application registry. All initialisation must be done in this method so that any components
     * that need access to the application registry itself for initialisation are able to use it. Attempting to
     * initialise in the constructor will lead to failures since the registry reference will not have been set.
     * @param instanceID the instanceID that we can use to identify this AR.
     */
    void initialise(int instanceID) throws Exception;

    /**
     * Shutdown this Registry
     */
    void close();

    /**
     * Get the low level configuration. For use cases where the configured object approach is not required
     * you can get the complete configuration information.
     * @return a Commons Configuration instance
     */
    ServerConfiguration getConfiguration();

    ManagedObjectRegistry getManagedObjectRegistry();

    PrincipalDatabaseManager getDatabaseManager();

    AuthenticationManager getAuthenticationManager();

    VirtualHostRegistry getVirtualHostRegistry();

    SecurityManager getSecurityManager();

    PluginManager getPluginManager();

    ConfigurationManager getConfigurationManager();

    RootMessageLogger getRootMessageLogger();

    /**
     * Register any acceptors for this registry
     * @param bindAddress The address that the acceptor has been bound with
     * @param acceptor The acceptor in use
     */
    void addAcceptor(InetSocketAddress bindAddress, QpidAcceptor acceptor);

    public UUID getBrokerId();

    QMFService getQMFService();

    void setBroker(BrokerConfig broker);

    BrokerConfig getBroker();

    VirtualHost createVirtualHost(VirtualHostConfiguration vhostConfig) throws Exception;

    ConfigStore getConfigStore();

    void setConfigStore(ConfigStore store);
    
    void initialiseStatisticsReporting();
}
