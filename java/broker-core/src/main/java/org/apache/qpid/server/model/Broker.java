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
package org.apache.qpid.server.model;

import java.net.SocketAddress;
import java.util.Collection;

import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.EventLoggerProvider;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

@ManagedObject( defaultType = "adapter" )
public interface Broker<X extends Broker<X>> extends ConfiguredObject<X>, EventLoggerProvider, StatisticsGatherer
{

    String BUILD_VERSION = "buildVersion";
    String OPERATING_SYSTEM = "operatingSystem";
    String PLATFORM = "platform";
    String PROCESS_PID = "processPid";
    String PRODUCT_VERSION = "productVersion";
    String SUPPORTED_VIRTUALHOST_TYPES = "supportedVirtualHostTypes";
    String SUPPORTED_VIRTUALHOST_STORE_TYPES = "supportedVirtualHostStoreTypes";
    String SUPPORTED_AUTHENTICATION_PROVIDERS = "supportedAuthenticationProviders";
    String SUPPORTED_PREFERENCES_PROVIDER_TYPES = "supportedPreferencesProviderTypes";
    String DURABLE = "durable";
    String LIFETIME_POLICY = "lifetimePolicy";
    String DEFAULT_VIRTUAL_HOST = "defaultVirtualHost";
    String STATISTICS_REPORTING_PERIOD = "statisticsReportingPeriod";
    String STATISTICS_REPORTING_RESET_ENABLED = "statisticsReportingResetEnabled";
    String STORE_TYPE = "storeType";
    String STORE_VERSION = "storeVersion";
    String STORE_PATH = "storePath";
    String MODEL_VERSION = "modelVersion";

    String CONNECTION_SESSION_COUNT_LIMIT = "connection.sessionCountLimit";
    String CONNECTION_HEART_BEAT_DELAY = "connection.heartBeatDelay";
    String CONNECTION_CLOSE_WHEN_NO_ROUTE = "connection.closeWhenNoRoute";

    @ManagedAttribute( derived = true )
    String getBuildVersion();

    @ManagedAttribute( derived = true )
    String getOperatingSystem();

    @ManagedAttribute( derived = true )
    String getPlatform();

    @ManagedAttribute( derived = true )
    String getProcessPid();

    @ManagedAttribute( derived = true )
    String getProductVersion();

    @ManagedAttribute( derived = true )
    Collection<String> getSupportedVirtualHostStoreTypes();

    @ManagedAttribute( derived = true )
    Collection<String> getSupportedAuthenticationProviders();

    @ManagedAttribute( derived = true )
    Collection<String> getSupportedPreferencesProviderTypes();

    @ManagedAttribute( automate = true )
    String getDefaultVirtualHost();

    @ManagedAttribute( automate = true, defaultValue = "256" )
    int getConnection_sessionCountLimit();

    @ManagedAttribute( automate = true, defaultValue = "0")
    int getConnection_heartBeatDelay();

    @ManagedAttribute( automate = true, defaultValue = "true" )
    boolean getConnection_closeWhenNoRoute();

    @ManagedAttribute( automate = true, defaultValue = "0" )
    int getStatisticsReportingPeriod();

    @ManagedAttribute( automate = true, defaultValue = "false")
    boolean getStatisticsReportingResetEnabled();

    @ManagedAttribute( derived = true )
    String getModelVersion();

    @ManagedStatistic
    long getBytesIn();

    @ManagedStatistic
    long getBytesOut();

    @ManagedStatistic
    long getMessagesIn();

    @ManagedStatistic
    long getMessagesOut();


    //children
    Collection < VirtualHost<?,?,?> > getVirtualHosts();

    Collection<Port<?>> getPorts();

    Collection<AuthenticationProvider<?>> getAuthenticationProviders();

    Collection<AccessControlProvider<?>> getAccessControlProviders();

    Collection<GroupProvider<?>> getGroupProviders();

    /**
     * A temporary hack to expose security manager via broker instance.
     * TODO We need to add and implement an authorization provider configured object instead
     */
    SecurityManager getSecurityManager();

    /**
     * TODO: A temporary hack to expose log recorder via broker instance.
     */
    LogRecorder getLogRecorder();

    AuthenticationProvider<?> findAuthenticationProviderByName(String authenticationProviderName);

    VirtualHost<?,?,?> findVirtualHostByName(String name);

    KeyStore<?> findKeyStoreByName(String name);

    TrustStore<?> findTrustStoreByName(String name);

    /**
     * Get the SubjectCreator for the given socket address.
     * TODO: move the authentication related functionality into host aliases and AuthenticationProviders
     *
     * @param localAddress The (listening) socket address for which the AuthenticationManager is required
     */
    SubjectCreator getSubjectCreator(SocketAddress localAddress);

    Collection<KeyStore<?>> getKeyStores();

    Collection<TrustStore<?>> getTrustStores();

    /*
     * TODO: Remove this method. Eventually the broker will become a registry.
     */
    VirtualHostRegistry getVirtualHostRegistry();

    TaskExecutor getTaskExecutor();

    boolean isManagementMode();

    AuthenticationProvider<?> getAuthenticationProvider(SocketAddress localAddress);

    EventLogger getEventLogger();

    void setEventLogger(EventLogger eventLogger);

    AuthenticationProvider<?> getManagementModeAuthenticationProvider();
}
