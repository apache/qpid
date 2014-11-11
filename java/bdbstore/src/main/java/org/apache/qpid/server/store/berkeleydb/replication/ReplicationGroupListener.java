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
package org.apache.qpid.server.store.berkeleydb.replication;

import com.sleepycat.je.rep.NodeState;
import com.sleepycat.je.rep.ReplicationNode;

public interface ReplicationGroupListener
{
    /**
     * Fired when a remote replication node is added to a group.  This event happens
     * exactly once just after a new replication node is created.
     */
    void onReplicationNodeAddedToGroup(ReplicationNode node);

    /**
     * Fired exactly once for each existing remote node.  Used to inform the application
     * on any existing nodes as it starts up for the first time.
     */
    void onReplicationNodeRecovered(ReplicationNode node);

    /**
     * Fired when a remote replication node is (permanently) removed from group.  This event
     * happens exactly once just after the existing replication node is deleted.
     */
    void onReplicationNodeRemovedFromGroup(ReplicationNode node);

    /**
     * Invoked to notify listener on node state update
     */
    void onNodeState(ReplicationNode node, NodeState nodeState);

    /**
     * Invoked on intruder node detected
     */
    boolean onIntruderNode(ReplicationNode node);

    void onNoMajority();

    /**
     * Signifies that node need to discard one or more transactions in order to rejoin the group.  Most likely
     * caused by use of the weak durability options such as node priority zero.
     */
    void onNodeRolledback();

    /**
     * Callback method to invoke on critical JE exceptions
     * @param e je exception
     */
    void onException(Exception e);
}
