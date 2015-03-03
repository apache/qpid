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

import org.apache.qpid.server.model.DerivedAttribute;
import org.apache.qpid.server.model.ManagedAttribute;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.RemoteReplicationNode;

@ManagedObject(category=false, creatable=false)
public interface BDBHARemoteReplicationNode<X extends BDBHARemoteReplicationNode<X>> extends RemoteReplicationNode<X>
{
    String GROUP_NAME = "groupName";
    String ADDRESS = "address";
    String ROLE = "role";
    String LAST_KNOWN_REPLICATION_TRANSACTION_ID = "lastKnownReplicationTransactionId";
    String JOIN_TIME = "joinTime";
    String MONITOR = "monitor";

    @DerivedAttribute
    String getGroupName();

    @DerivedAttribute
    String getAddress();

    @ManagedAttribute(persist = false)
    NodeRole getRole();

    @DerivedAttribute
    long getJoinTime();

    @DerivedAttribute
    long getLastKnownReplicationTransactionId();

    @DerivedAttribute
    boolean isMonitor();
}
