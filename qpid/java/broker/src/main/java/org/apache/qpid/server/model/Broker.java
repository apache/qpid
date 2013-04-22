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
import java.security.AccessControlException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

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
    String SUPPORTED_STORE_TYPES = "supportedStoreTypes";
    String SUPPORTED_AUTHENTICATION_PROVIDERS = "supportedAuthenticationProviders";
    String CREATED = "created";
    String DURABLE = "durable";
    String ID = "id";
    String LIFETIME_POLICY = "lifetimePolicy";
    String NAME = "name";
    String STATE = "state";
    String TIME_TO_LIVE = "timeToLive";
    String UPDATED = "updated";
    String DEFAULT_AUTHENTICATION_PROVIDER = "defaultAuthenticationProvider";
    String DEFAULT_VIRTUAL_HOST = "defaultVirtualHost";

    String ALERT_THRESHOLD_MESSAGE_AGE = "queue.alertThresholdMessageAge";
    String ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES = "queue.alertThresholdQueueDepthMessages";
    String ALERT_THRESHOLD_QUEUE_DEPTH_BYTES = "queue.alertThresholdQueueDepthBytes";
    String ALERT_THRESHOLD_MESSAGE_SIZE = "queue.alertThresholdMessageSize";
    String ALERT_REPEAT_GAP = "queue.alertRepeatGap";
    String FLOW_CONTROL_SIZE_BYTES = "queue.flowControlSizeBytes";
    String FLOW_CONTROL_RESUME_SIZE_BYTES = "queue.flowResumeSizeBytes";
    String MAXIMUM_DELIVERY_ATTEMPTS = "queue.maximumDeliveryAttempts";
    String DEAD_LETTER_QUEUE_ENABLED = "queue.deadLetterQueueEnabled";
    String HOUSEKEEPING_CHECK_PERIOD = "virtualhost.housekeepingCheckPeriod";

    String SESSION_COUNT_LIMIT = "connection.sessionCountLimit";
    String HEART_BEAT_DELAY = "connection.heartBeatDelay";
    String STATISTICS_REPORTING_PERIOD = "statisticsReportingPeriod";
    String STATISTICS_REPORTING_RESET_ENABLED = "statisticsReportingResetEnabled";
    String STORE_TYPE = "storeType";
    String STORE_VERSION = "storeVersion";
    String STORE_PATH = "storePath";
    String MANAGEMENT_VERSION = "managementVersion";

    String VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE = "virtualhost.storeTransactionIdleTimeoutClose";
    String VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_WARN  = "virtualhost.storeTransactionIdleTimeoutWarn";
    String VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE = "virtualhost.storeTransactionOpenTimeoutClose";
    String VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_WARN  = "virtualhost.storeTransactionOpenTimeoutWarn";
    /*
     * A temporary attribute to pass the path to ACL file.
     * TODO: It should be a part of AuthorizationProvider.
     */
    String ACL_FILE = "aclFile";

    /*
     * A temporary attributes to set the broker default key/trust stores.
     * TODO: Remove them after adding a full support to configure KeyStore/TrustStore via management layers.
     */
    String KEY_STORE_PATH = "keyStorePath";
    String KEY_STORE_PASSWORD = "keyStorePassword";
    String KEY_STORE_CERT_ALIAS = "keyStoreCertAlias";
    String TRUST_STORE_PATH = "trustStorePath";
    String TRUST_STORE_PASSWORD = "trustStorePassword";
    String PEER_STORE_PATH = "peerStorePath";
    String PEER_STORE_PASSWORD = "peerStorePassword";

    /*
     * A temporary attributes to set the broker group file.
     * TODO: Remove them after adding a full support to configure authorization providers via management layers.
     */
    String GROUP_FILE = "groupFile";

    // Attributes
    Collection<String> AVAILABLE_ATTRIBUTES =
            Collections.unmodifiableList(
                Arrays.asList(BUILD_VERSION,
                              BYTES_RETAINED,
                              OPERATING_SYSTEM,
                              PLATFORM,
                              PROCESS_PID,
                              PRODUCT_VERSION,
                              SUPPORTED_STORE_TYPES,
                              SUPPORTED_AUTHENTICATION_PROVIDERS,
                              CREATED,
                              DURABLE,
                              ID,
                              LIFETIME_POLICY,
                              NAME,
                              STATE,
                              TIME_TO_LIVE,
                              UPDATED,
                              DEFAULT_AUTHENTICATION_PROVIDER,
                              DEFAULT_VIRTUAL_HOST,
                              ALERT_THRESHOLD_MESSAGE_AGE,
                              ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES,
                              ALERT_THRESHOLD_QUEUE_DEPTH_BYTES,
                              ALERT_THRESHOLD_MESSAGE_SIZE,
                              ALERT_REPEAT_GAP,
                              FLOW_CONTROL_SIZE_BYTES,
                              FLOW_CONTROL_RESUME_SIZE_BYTES,
                              MAXIMUM_DELIVERY_ATTEMPTS,
                              DEAD_LETTER_QUEUE_ENABLED,
                              HOUSEKEEPING_CHECK_PERIOD,
                              SESSION_COUNT_LIMIT,
                              HEART_BEAT_DELAY,
                              STATISTICS_REPORTING_PERIOD,
                              STATISTICS_REPORTING_RESET_ENABLED,
                              STORE_TYPE,
                              STORE_VERSION,
                              STORE_PATH,
                              MANAGEMENT_VERSION,
                              VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE,
                              VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_WARN,
                              VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE,
                              VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_WARN,

                              ACL_FILE,
                              KEY_STORE_PATH,
                              KEY_STORE_PASSWORD,
                              KEY_STORE_CERT_ALIAS,
                              TRUST_STORE_PATH,
                              TRUST_STORE_PASSWORD,
                              PEER_STORE_PATH,
                              PEER_STORE_PASSWORD,
                              GROUP_FILE
                              ));

    //children
    Collection < VirtualHost > getVirtualHosts();

    Collection<Port> getPorts();

    Collection<AuthenticationProvider> getAuthenticationProviders();

    VirtualHost createVirtualHost(String name, State initialState, boolean durable,
                                  LifetimePolicy lifetime, long ttl, Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException;

    AuthenticationProvider getDefaultAuthenticationProvider();

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

    VirtualHost findVirtualHostByName(String name);

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

    KeyStore getDefaultKeyStore();

    TrustStore getDefaultTrustStore();

    TaskExecutor getTaskExecutor();
}
