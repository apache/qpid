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

public interface Broker extends ConfiguredObject
{

    String BUILD_VERSION = "buildVersion";
    String BYTES_RETAINED = "bytesRetained";
    String OPERATING_SYSTEM = "operatingSystem";
    String PLATFORM = "platform";
    String PROCESS_PID = "processPid";
    String PRODUCT_VERSION = "productVersion";
    String SUPPORTED_BROKER_STORE_TYPES = "supportedBrokerStoreTypes";
    String SUPPORTED_VIRTUALHOST_TYPES = "supportedVirtualHostTypes";
    String SUPPORTED_VIRTUALHOST_STORE_TYPES = "supportedVirtualHostStoreTypes";
    String SUPPORTED_AUTHENTICATION_PROVIDERS = "supportedAuthenticationProviders";
    String SUPPORTED_PREFERENCES_PROVIDERS_TYPES = "supportedPreferencesProviderTypes";
    String CREATED = "created";
    String DURABLE = "durable";
    String LIFETIME_POLICY = "lifetimePolicy";
    String STATE = "state";
    String TIME_TO_LIVE = "timeToLive";
    String UPDATED = "updated";
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

    // Attributes
    Collection<String> AVAILABLE_ATTRIBUTES =
            Collections.unmodifiableList(
                Arrays.asList(BUILD_VERSION,
                              BYTES_RETAINED,
                              OPERATING_SYSTEM,
                              PLATFORM,
                              PROCESS_PID,
                              PRODUCT_VERSION,
                              SUPPORTED_BROKER_STORE_TYPES,
                              SUPPORTED_VIRTUALHOST_STORE_TYPES,
                              SUPPORTED_AUTHENTICATION_PROVIDERS,
                              SUPPORTED_PREFERENCES_PROVIDERS_TYPES,
                              CREATED,
                              DURABLE,
                              ID,
                              LIFETIME_POLICY,
                              NAME,
                              STATE,
                              TIME_TO_LIVE,
                              UPDATED,
                              DEFAULT_VIRTUAL_HOST,
                              QUEUE_ALERT_THRESHOLD_MESSAGE_AGE,
                              QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES,
                              QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES,
                              QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE,
                              QUEUE_ALERT_REPEAT_GAP,
                              QUEUE_FLOW_CONTROL_SIZE_BYTES,
                              QUEUE_FLOW_CONTROL_RESUME_SIZE_BYTES,
                              QUEUE_MAXIMUM_DELIVERY_ATTEMPTS,
                              QUEUE_DEAD_LETTER_QUEUE_ENABLED,
                              VIRTUALHOST_HOUSEKEEPING_CHECK_PERIOD,
                              CONNECTION_SESSION_COUNT_LIMIT,
                              CONNECTION_HEART_BEAT_DELAY,
                              CONNECTION_CLOSE_WHEN_NO_ROUTE,
                              STATISTICS_REPORTING_PERIOD,
                              STATISTICS_REPORTING_RESET_ENABLED,
                              STORE_TYPE,
                              STORE_VERSION,
                              STORE_PATH,
                              MODEL_VERSION,
                              VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE,
                              VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_WARN,
                              VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE,
                              VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_WARN
                              ));

    //children
    Collection < VirtualHost > getVirtualHosts();

    Collection<Port> getPorts();

    Collection<AuthenticationProvider> getAuthenticationProviders();

    Collection<AccessControlProvider> getAccessControlProviders();

    Collection<GroupProvider> getGroupProviders();

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

    AuthenticationProvider findAuthenticationProviderByName(String authenticationProviderName);

    VirtualHost findVirtualHostByName(String name);

    KeyStore findKeyStoreByName(String name);

    TrustStore findTrustStoreByName(String name);

    /**
     * Get the SubjectCreator for the given socket address.
     * TODO: move the authentication related functionality into host aliases and AuthenticationProviders
     *
     * @param address The (listening) socket address for which the AuthenticationManager is required
     */
    SubjectCreator getSubjectCreator(SocketAddress localAddress);

    Collection<KeyStore> getKeyStores();

    Collection<TrustStore> getTrustStores();

    /*
     * TODO: Remove this method. Eventually the broker will become a registry.
     */
    VirtualHostRegistry getVirtualHostRegistry();

    TaskExecutor getTaskExecutor();

    boolean isManagementMode();

    AuthenticationProvider getAuthenticationProvider(SocketAddress localAddress);

    /**
     * TODO: Remove this
     */
    boolean isPreviouslyUsedPortNumber(int port);
}
