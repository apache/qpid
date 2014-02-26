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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public interface Broker<X extends Broker<X>> extends ConfiguredObject<X>
{

    String BUILD_VERSION = "buildVersion";
    String OPERATING_SYSTEM = "operatingSystem";
    String PLATFORM = "platform";
    String PROCESS_PID = "processPid";
    String PRODUCT_VERSION = "productVersion";
    String SUPPORTED_BROKER_STORE_TYPES = "supportedBrokerStoreTypes";
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

    String QUEUE_ALERT_THRESHOLD_MESSAGE_AGE = "queue.alertThresholdMessageAge";
    String QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES = "queue.alertThresholdQueueDepthMessages";
    String QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES = "queue.alertThresholdQueueDepthBytes";
    String QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE = "queue.alertThresholdMessageSize";
    String QUEUE_ALERT_REPEAT_GAP = "queue.alertRepeatGap";
    String QUEUE_FLOW_CONTROL_SIZE_BYTES = "queue.flowControlSizeBytes";
    String QUEUE_FLOW_CONTROL_RESUME_SIZE_BYTES = "queue.flowResumeSizeBytes";
    String QUEUE_MAXIMUM_DELIVERY_ATTEMPTS = "queue.maximumDeliveryAttempts";
    String QUEUE_DEAD_LETTER_QUEUE_ENABLED = "queue.deadLetterQueueEnabled";

    String CONNECTION_SESSION_COUNT_LIMIT = "connection.sessionCountLimit";
    String CONNECTION_HEART_BEAT_DELAY = "connection.heartBeatDelay";
    String CONNECTION_CLOSE_WHEN_NO_ROUTE = "connection.closeWhenNoRoute";

    String VIRTUALHOST_HOUSEKEEPING_CHECK_PERIOD            = "virtualhost.housekeepingCheckPeriod";
    String VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE = "virtualhost.storeTransactionIdleTimeoutClose";
    String VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_WARN  = "virtualhost.storeTransactionIdleTimeoutWarn";
    String VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE = "virtualhost.storeTransactionOpenTimeoutClose";
    String VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_WARN  = "virtualhost.storeTransactionOpenTimeoutWarn";

    @ManagedAttribute
    String getBuildVersion();

    @ManagedAttribute
    String getOperatingSystem();

    @ManagedAttribute
    String getPlatform();

    @ManagedAttribute
    String getProcessPid();

    @ManagedAttribute
    String getProductVersion();

    @ManagedAttribute
    Collection<String> getSupportedBrokerStoreTypes();

    @ManagedAttribute
    Collection<String> getSupportedVirtualHostStoreTypes();

    @ManagedAttribute
    Collection<String> getSupportedAuthenticationProviders();

    @ManagedAttribute
    Collection<String> getSupportedPreferencesProviderTypes();

    @ManagedAttribute
    String getDefaultVirtualHost();

    @ManagedAttribute
    int getQueue_alertThresholdMessageAge();

    @ManagedAttribute
    long getQueue_alertThresholdQueueDepthMessages();

    @ManagedAttribute
    long getQueue_alertThresholdQueueDepthBytes();

    @ManagedAttribute
    long getQueue_alertThresholdMessageSize();

    @ManagedAttribute
    long getQueue_alertRepeatGap();

    @ManagedAttribute
    long getQueue_flowControlSizeBytes();

    @ManagedAttribute
    long getQueue_flowResumeSizeBytes();

    @ManagedAttribute
    int getQueue_maximumDeliveryAttempts();

    @ManagedAttribute
    boolean isQueue_deadLetterQueueEnabled();

    @ManagedAttribute
    long getVirtualhost_housekeepingCheckPeriod();

    @ManagedAttribute
    int getConnection_sessionCountLimit();

    @ManagedAttribute
    int getConnection_heartBeatDelay();

    @ManagedAttribute
    boolean getConnection_closeWhenNoRoute();

    @ManagedAttribute
    int getStatisticsReportingPeriod();

    @ManagedAttribute
    boolean getStatisticsReportingResetEnabled();

    @ManagedAttribute
    String getStoreType();

    @ManagedAttribute
    int getStoreVersion();

    @ManagedAttribute
    String getStorePath();

    @ManagedAttribute
    String getModelVersion();

    @ManagedAttribute
    long getVirtualhost_storeTransactionIdleTimeoutClose();

    @ManagedAttribute
    long getVirtualhost_storeTransactionIdleTimeoutWarn();

    @ManagedAttribute
    long getVirtualhost_storeTransactionOpenTimeoutClose();

    @ManagedAttribute
    long getVirtualhost_storeTransactionOpenTimeoutWarn();



    @ManagedStatistic
    long getBytesIn();

    @ManagedStatistic
    long getBytesOut();

    @ManagedStatistic
    long getMessagesIn();

    @ManagedStatistic
    long getMessagesOut();


    //children
    Collection < VirtualHost<?> > getVirtualHosts();

    Collection<Port<?>> getPorts();

    Collection<AuthenticationProvider<?>> getAuthenticationProviders();

    Collection<AccessControlProvider<?>> getAccessControlProviders();

    Collection<GroupProvider<?>> getGroupProviders();

    /**
     * A temporary hack to expose root message logger via broker instance.
     * TODO We need a better way to do operational logging, for example, via logging listeners
     */
    RootMessageLogger getRootMessageLogger();

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

    VirtualHost<?> findVirtualHostByName(String name);

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
}
