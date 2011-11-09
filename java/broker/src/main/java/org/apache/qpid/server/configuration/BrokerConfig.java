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

package org.apache.qpid.server.configuration;

import java.util.List;


public interface BrokerConfig  extends ConfiguredObject<BrokerConfigType,BrokerConfig>
{
    void setSystem(SystemConfig system);

    SystemConfig getSystem();

    Integer getPort();

    Integer getWorkerThreads();

    Integer getMaxConnections();

    Integer getConnectionBacklogLimit();

    Long getStagingThreshold();

    Integer getManagementPublishInterval();

    String getVersion();

    String getDataDirectory();

    String getFederationTag();

    /**
     * List of feature(s) to be advertised to clients on connection.
     * Feature names are strings, beginning with qpid. followed by more or more
     * words separated by minus signs e.g. qpid.jms-selector.
     *
     * If there are no features, this method must return an empty array.
     *
     * @return list of feature names
     */
    List<String> getFeatures();

    void addVirtualHost(VirtualHostConfig virtualHost);

    void createBrokerConnection(String transport,
                                String host,
                                int port,
                                boolean durable,
                                String authMechanism,
                                String username, String password);

}
