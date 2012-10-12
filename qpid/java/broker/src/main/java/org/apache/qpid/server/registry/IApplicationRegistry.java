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

import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.IAuthenticationManagerRegistry;
import org.apache.qpid.server.security.group.GroupManager;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.transport.QpidAcceptor;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface IApplicationRegistry extends StatisticsGatherer
{
    /**
     * Initialise the application registry. All initialisation must be done in this method so that any components
     * that need access to the application registry itself for initialisation are able to use it. Attempting to
     * initialise in the constructor will lead to failures since the registry reference will not have been set.
     */
    void initialise() throws Exception;

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

    /**
     * Get the SubjectCreator for the given socket address.
     *
     * @param address The (listening) socket address for which the AuthenticationManager is required
     */
    SubjectCreator getSubjectCreator(SocketAddress localAddress);

    IAuthenticationManagerRegistry getAuthenticationManagerRegistry();

    List<GroupManager> getGroupManagers();

    VirtualHostRegistry getVirtualHostRegistry();

    SecurityManager getSecurityManager();

    RootMessageLogger getRootMessageLogger();

    /**
     * Register any acceptors for this registry
     * @param bindAddress The address that the acceptor has been bound with
     * @param acceptor The acceptor in use
     */
    void addAcceptor(InetSocketAddress bindAddress, QpidAcceptor acceptor);

    public UUID getBrokerId();

    Broker getBroker();

    VirtualHost createVirtualHost(VirtualHostConfiguration vhostConfig) throws Exception;

    void initialiseStatisticsReporting();

    Map<InetSocketAddress, QpidAcceptor> getAcceptors();

    void addPortBindingListener(PortBindingListener listener);

    boolean useHTTPManagement();

    int getHTTPManagementPort();

    boolean useHTTPSManagement();

    int getHTTPSManagementPort();

    void addAuthenticationManagerRegistryChangeListener(IAuthenticationManagerRegistry.RegistryChangeListener registryChangeListener);

    public interface PortBindingListener
    {
        public void bound(QpidAcceptor acceptor, InetSocketAddress bindAddress);
        public void unbound(QpidAcceptor acceptor);

    }

    void addGroupManagerChangeListener(GroupManagerChangeListener groupManagerChangeListener);

    public static interface GroupManagerChangeListener
    {
        void groupManagerRegistered(GroupManager groupManager);
        void groupManagerUnregistered(GroupManager groupManager);
    }
}
