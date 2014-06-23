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
package org.apache.qpid.server.virtualhostnode.berkeleydb;

import java.util.Map;

import org.apache.qpid.server.model.DerivedAttribute;
import org.apache.qpid.server.model.ManagedAttribute;

public interface BDBHAVirtualHostNode<X extends BDBHAVirtualHostNode<X>> extends BDBVirtualHostNode<X>
{
    public static final String GROUP_NAME = "groupName";
    public static final String ADDRESS = "address";
    public static final String HELPER_ADDRESS = "helperAddress";
    public static final String DURABILITY = "durability";
    public static final String DESIGNATED_PRIMARY = "designatedPrimary";
    public static final String PRIORITY = "priority";
    public static final String QUORUM_OVERRIDE = "quorumOverride";
    public static final String ROLE = "role";
    public static final String REPLICATED_ENVIRONMENT_CONFIGURATION = "replicatedEnvironmentConfiguration";
    public static final String LAST_KNOWN_REPLICATION_TRANSACTION_ID = "lastKnownReplicationTransactionId";
    public static final String JOIN_TIME = "joinTime";

    @ManagedAttribute(mandatory=true)
    String getGroupName();

    @ManagedAttribute(mandatory=true)
    String getAddress();

    @ManagedAttribute(mandatory=true)
    String getHelperAddress();

    @ManagedAttribute(defaultValue = "false")
    boolean isDesignatedPrimary();

    @ManagedAttribute(defaultValue = "1")
    int getPriority();

    @ManagedAttribute(defaultValue = "0")
    int getQuorumOverride();

    @ManagedAttribute(persist = false)
    String getRole();

    @ManagedAttribute
    Map<String, String> getReplicatedEnvironmentConfiguration();

    @DerivedAttribute
    Long getLastKnownReplicationTransactionId();

    @DerivedAttribute
    Long getJoinTime();
}
