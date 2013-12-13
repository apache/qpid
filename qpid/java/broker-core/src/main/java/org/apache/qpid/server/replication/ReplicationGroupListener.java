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
package org.apache.qpid.server.replication;

import org.apache.qpid.server.model.ReplicationNode;

public interface ReplicationGroupListener
{
    /**
     * Fired when a remote replication node is added to a group.  This event happens
     * exactly once just after a new replication node is created.
     */
    //void onReplicationNodeAddedToGroup(ReplicationNode node);

    /**
     * Fired exactly once for each existing remote node.  Used to inform the application
     * on any existing nodes as it starts up for the first time.
     */
    void onReplicationNodeRecovered(ReplicationNode node);

    /**
     * Fired when a remote replication node is (permanently) removed from group.  This event
     * happens exactly once just after the existing replication node is deleted.
     */
    //void onReplicationNodeRemovedFromGroup(ReplicationNode node);

    /**
     * Fired when a remote replication node (that is already a member of the group) joins
     * the group.  This will typically occur when another replication node is started perhaps
     * because the broker has been started.
     */
    //void onReplicationNodeUp();

    /**
     * Fired when a remote replication node (that is already a member of the group) leaves
     * the group.  This will typically occur when another replication node is stopped perhaps
     * because its broker has been stopped.
     */
    //void onReplicationNodeDown();

}
