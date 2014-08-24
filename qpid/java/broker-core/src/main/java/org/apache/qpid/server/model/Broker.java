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

@ManagedObject( defaultType = "broker" )
public interface Broker<X extends Broker<X>> extends ConfiguredObject<X>, EventLoggerProvider, StatisticsGatherer
{

    String BUILD_VERSION = "buildVersion";
    String OPERATING_SYSTEM = "operatingSystem";
    String PLATFORM = "platform";
    String PROCESS_PID = "processPid";
    String PRODUCT_VERSION = "productVersion";
    String SUPPORTED_VIRTUALHOST_TYPES = "supportedVirtualHostTypes";
    String SUPPORTED_VIRTUALHOSTNODE_TYPES = "supportedVirtualHostNodeTypes";
    String SUPPORTED_AUTHENTICATION_PROVIDERS = "supportedAuthenticationProviders";
    String SUPPORTED_PREFERENCES_PROVIDER_TYPES = "supportedPreferencesProviderTypes";
    String DEFAULT_VIRTUAL_HOST = "defaultVirtualHost";
    String STATISTICS_REPORTING_PERIOD = "statisticsReportingPeriod";
    String STATISTICS_REPORTING_RESET_ENABLED = "statisticsReportingResetEnabled";
    String STORE_PATH = "storePath";
    String MODEL_VERSION = "modelVersion";
    String CONFIDENTIAL_CONFIGURATION_ENCRYPTION_PROVIDER = "confidentialConfigurationEncryptionProvider";

    String CONNECTION_SESSION_COUNT_LIMIT = "connection.sessionCountLimit";
    String CONNECTION_HEART_BEAT_DELAY = "connection.heartBeatDelay";
    String CONNECTION_CLOSE_WHEN_NO_ROUTE = "connection.closeWhenNoRoute";

    String BROKER_FLOW_TO_DISK_THRESHOLD = "broker.flowToDiskThreshold";

    String QPID_AMQP_PORT = "qpid.amqp_port";
    String QPID_HTTP_PORT = "qpid.http_port";
    String QPID_RMI_PORT  = "qpid.rmi_port";
    String QPID_JMX_PORT  = "qpid.jmx_port";

    @ManagedContextDefault(name = "broker.name")
    static final String DEFAULT_BROKER_NAME = "Broker";

    @ManagedContextDefault(name = QPID_AMQP_PORT)
    public static final String DEFAULT_AMQP_PORT_NUMBER = "5672";
    @ManagedContextDefault(name = QPID_HTTP_PORT)
    public static final String DEFAULT_HTTP_PORT_NUMBER = "8080";
    @ManagedContextDefault(name = QPID_RMI_PORT)
    public static final String DEFAULT_RMI_PORT_NUMBER  = "8999";
    @ManagedContextDefault(name = QPID_JMX_PORT)
    public static final String DEFAULT_JMX_PORT_NUMBER  = "9099";

    @ManagedContextDefault(name = BROKER_FLOW_TO_DISK_THRESHOLD)
    public static final long DEFAULT_FLOW_TO_DISK_THRESHOLD = (long)(0.4 * (double)Runtime.getRuntime().maxMemory());

    String BROKER_FRAME_SIZE = "qpid.broker_frame_size";
    @ManagedContextDefault(name = BROKER_FRAME_SIZE)
    long DEFAULT_FRAME_SIZE = 65535;


    @DerivedAttribute
    String getBuildVersion();

    @DerivedAttribute
    String getOperatingSystem();

    @DerivedAttribute
    String getPlatform();

    @DerivedAttribute
    String getProcessPid();

    @DerivedAttribute
    String getProductVersion();

    @DerivedAttribute
    Collection<String> getSupportedVirtualHostNodeTypes();

    @DerivedAttribute
    Collection<String> getSupportedAuthenticationProviders();

    @DerivedAttribute
    Collection<String> getSupportedPreferencesProviderTypes();

    @DerivedAttribute
    Collection<String> getSupportedVirtualHostTypes();

    @ManagedAttribute
    String getDefaultVirtualHost();

    @ManagedAttribute( defaultValue = "256" )
    int getConnection_sessionCountLimit();

    @ManagedAttribute( defaultValue = "0")
    int getConnection_heartBeatDelay();

    @ManagedAttribute( defaultValue = "true" )
    boolean getConnection_closeWhenNoRoute();

    @ManagedAttribute( defaultValue = "0" )
    int getStatisticsReportingPeriod();

    @ManagedAttribute( defaultValue = "false")
    boolean getStatisticsReportingResetEnabled();

    String BROKER_MESSAGE_COMPRESSION_ENABLED = "broker.messageCompressionEnabled";
    @ManagedContextDefault(name = BROKER_MESSAGE_COMPRESSION_ENABLED)
    boolean DEFAULT_MESSAGE_COMPRESSION_ENABLED = true;

    @ManagedAttribute( defaultValue = "${"+ BROKER_MESSAGE_COMPRESSION_ENABLED +"}")
    boolean isMessageCompressionEnabled();

    String MESSAGE_COMPRESSION_THRESHOLD_SIZE = "connection.messageCompressionThresholdSize";
    @ManagedContextDefault(name = MESSAGE_COMPRESSION_THRESHOLD_SIZE)
    int DEFAULT_MESSAGE_COMPRESSION_THRESHOLD_SIZE = 102400;

    @ManagedAttribute
    String getConfidentialConfigurationEncryptionProvider();

    @DerivedAttribute( persist = true )
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
    Collection<VirtualHostNode<?>> getVirtualHostNodes();

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
     * @param secure
     */
    SubjectCreator getSubjectCreator(SocketAddress localAddress, final boolean secure);

    Collection<KeyStore<?>> getKeyStores();

    Collection<TrustStore<?>> getTrustStores();

    TaskExecutor getTaskExecutor();

    boolean isManagementMode();

    AuthenticationProvider<?> getAuthenticationProvider(SocketAddress localAddress);

    EventLogger getEventLogger();

    void setEventLogger(EventLogger eventLogger);

    AuthenticationProvider<?> getManagementModeAuthenticationProvider();

    void assignTargetSizes();

}
