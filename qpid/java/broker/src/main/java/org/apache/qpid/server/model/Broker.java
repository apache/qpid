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

import java.security.AccessControlException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.security.SecurityManager;

public interface Broker extends ConfiguredObject
{

    String BUILD_VERSION = "buildVersion";
    String BYTES_RETAINED = "bytesRetained";
    String OPERATING_SYSTEM = "operatingSystem";
    String PLATFORM = "platform";
    String PROCESS_PID = "processPid";
    String PRODUCT_VERSION = "productVersion";
    String SUPPORTED_STORE_TYPES = "supportedStoreTypes";
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

    String ALERT_THRESHOLD_MESSAGE_AGE = "alertThresholdMessageAge";
    String ALERT_THRESHOLD_MESSAGE_COUNT = "alertThresholdMessageCount";
    String ALERT_THRESHOLD_QUEUE_DEPTH = "alertThresholdQueueDepth";
    String ALERT_THRESHOLD_MESSAGE_SIZE = "alertThresholdMessageSize";
    String ALERT_REPEAT_GAP = "alertRepeatGap";
    String FLOW_CONTROL_SIZE_BYTES = "queueFlowControlSizeBytes";
    String FLOW_CONTROL_RESUME_SIZE_BYTES = "queueFlowResumeSizeBytes";
    String MAXIMUM_DELIVERY_ATTEMPTS = "maximumDeliveryAttempts";
    String DEAD_LETTER_QUEUE_ENABLED = "deadLetterQueueEnabled";
    String HOUSEKEEPING_CHECK_PERIOD = "housekeepingCheckPeriod";

    // Attributes
    public static final Collection<String> AVAILABLE_ATTRIBUTES =
            Collections.unmodifiableList(
                Arrays.asList(BUILD_VERSION,
                              BYTES_RETAINED,
                              OPERATING_SYSTEM,
                              PLATFORM,
                              PROCESS_PID,
                              PRODUCT_VERSION,
                              SUPPORTED_STORE_TYPES,
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
                              ALERT_THRESHOLD_MESSAGE_COUNT,
                              ALERT_THRESHOLD_QUEUE_DEPTH,
                              ALERT_THRESHOLD_MESSAGE_SIZE,
                              ALERT_REPEAT_GAP,
                              FLOW_CONTROL_SIZE_BYTES,
                              FLOW_CONTROL_RESUME_SIZE_BYTES,
                              MAXIMUM_DELIVERY_ATTEMPTS,
                              DEAD_LETTER_QUEUE_ENABLED,
                              HOUSEKEEPING_CHECK_PERIOD));

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

}
