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

import org.apache.qpid.server.model.ManagedAttribute;

public interface BDBHAVirtualHostNode<X extends BDBHAVirtualHostNode<X>> extends BDBVirtualHostNode<X>
{
    public static final String GROUP_NAME = "groupName";
    public static final String ADDRESS = "address";
    public static final String HELPER_ADDRESS = "helperAddress";
    public static final String DURABILITY = "durability";
    public static final String COALESCING_SYNC = "coalescingSync";
    public static final String DESIGNATED_PRIMARY = "designatedPrimary";
    public static final String PRIORITY = "priority";
    public static final String QUORUM_OVERRIDE = "quorumOverride";
    public static final String REPLICATED_ENVIRONMENT_CONFIGURATION = "replicatedEnvironmentConfiguration";

    @ManagedAttribute(automate = true, mandatory=true)
    String getGroupName();

    @ManagedAttribute(automate = true, mandatory=true)
    String getAddress();

    @ManagedAttribute(automate = true, mandatory=true)
    String getHelperAddress();

    @ManagedAttribute(automate = true, defaultValue = "NO_SYNC,NO_SYNC,SIMPLE_MAJORITY")
    String getDurability();

    @ManagedAttribute(automate = true, defaultValue = "true")
    boolean isCoalescingSync();

    @ManagedAttribute(automate = true, defaultValue = "false")
    boolean isDesignatedPrimary();

    @ManagedAttribute(automate = true, defaultValue = "1")
    int getPriority();

    @ManagedAttribute(automate = true, defaultValue = "0")
    int getQuorumOverride();

    @ManagedAttribute(automate = true)
    Map<String, String> getReplicatedEnvironmentConfiguration();
}
