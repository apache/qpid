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

import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.store.DurableConfigurationStore;

@ManagedObject (creatable = false)
public interface SystemConfig<X extends SystemConfig<X>> extends ConfiguredObject<X>
{
    String MANAGEMENT_MODE = "managementMode";
    
    String MANAGEMENT_MODE_QUIESCE_VIRTUAL_HOSTS = "managementModeQuiesceVirtualHosts";
    String MANAGEMENT_MODE_RMI_PORT_OVERRIDE = "managementModeRmiPortOverride";
    String MANAGEMENT_MODE_JMX_PORT_OVERRIDE = "managementModeJmxPortOverride";
    String MANAGEMENT_MODE_HTTP_PORT_OVERRIDE = "managementModeHttpPortOverride";
    String MANAGEMENT_MODE_PASSWORD = "managementModePassword";
    String INITIAL_CONFIGURATION_LOCATION = "initialConfigurationLocation";
    String STARTUP_LOGGED_TO_SYSTEM_OUT = "startupLoggedToSystemOut";

    @ManagedAttribute(defaultValue = "false")
    boolean isManagementMode();

    @ManagedAttribute(defaultValue = "0")
    int getManagementModeRmiPortOverride();

    @ManagedAttribute(defaultValue = "0")
    int getManagementModeJmxPortOverride();

    @ManagedAttribute(defaultValue = "0")
    int getManagementModeHttpPortOverride();

    @ManagedAttribute(defaultValue = "false")
    boolean isManagementModeQuiesceVirtualHosts();

    @ManagedAttribute(secure = true)
    String getManagementModePassword();

    @ManagedAttribute
    String getInitialConfigurationLocation();

    @ManagedAttribute(defaultValue = "true")
    boolean isStartupLoggedToSystemOut();

    EventLogger getEventLogger();

    Broker getBroker();

    LogRecorder getLogRecorder();

    DurableConfigurationStore getConfigurationStore();

    BrokerShutdownProvider getBrokerShutdownProvider();

}
