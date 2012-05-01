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

public interface Broker extends ConfiguredObject
{

    String BUILD_VERSION = "buildVersion";
    String BYTES_RETAINED = "bytesRetained";
    String OPERATING_SYSTEM = "operatingSystem";
    String PLATFORM = "platform";
    String PROCESS_PID = "processPid";
    String PRODUCT_VERSION = "productVersion";
    String STATISTICS_ENABLED = "statisticsEnabled";
    String SUPPORTED_STORE_TYPES = "supportedStoreTypes";
    String CREATED = "created";
    String DURABLE = "durable";
    String ID = "id";
    String LIFETIME_POLICY = "lifetimePolicy";
    String NAME = "name";
    String STATE = "state";
    String TIME_TO_LIVE = "timeToLive";
    String UPDATED = "updated";

    // Attributes
    public static final Collection<String> AVAILABLE_ATTRIBUTES =
            Collections.unmodifiableList(
                Arrays.asList(BUILD_VERSION,
                              BYTES_RETAINED,
                              OPERATING_SYSTEM,
                              PLATFORM,
                              PROCESS_PID,
                              PRODUCT_VERSION,
                              STATISTICS_ENABLED,
                              SUPPORTED_STORE_TYPES,
                              CREATED,
                              DURABLE,
                              ID,
                              LIFETIME_POLICY,
                              NAME,
                              STATE,
                              TIME_TO_LIVE,
                              UPDATED));

    //children
    Collection < VirtualHost > getVirtualHosts();

    Collection<Port> getPorts();

    Collection<AuthenticationProvider> getAuthenticationProviders();

    VirtualHost createVirtualHost(String name, State initialState, boolean durable,
                                  LifetimePolicy lifetime, long ttl, Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException;

    void deleteVirtualHost(VirtualHost virtualHost)
            throws AccessControlException, IllegalStateException;
}
