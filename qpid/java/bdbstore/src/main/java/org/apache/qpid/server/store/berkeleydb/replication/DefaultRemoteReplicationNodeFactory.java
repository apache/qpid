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

import org.apache.qpid.server.model.VirtualHost;

public class DefaultRemoteReplicationNodeFactory implements RemoteReplicationNodeFactory
{
    private VirtualHost _virtualHost;

    public DefaultRemoteReplicationNodeFactory(VirtualHost virtualHost)
    {
        _virtualHost = virtualHost;
    }

    @Override
    public RemoteReplicationNode create(com.sleepycat.je.rep.ReplicationNode replicationNode,
            ReplicatedEnvironmentFacade environmentFacade)
    {
        return new RemoteReplicationNode(replicationNode, _virtualHost, _virtualHost.getTaskExecutor(),
                environmentFacade);
    }

    @Override
    public long getRemoteNodeMonitorInterval()
    {
        return (Long) _virtualHost.getAttribute(VirtualHost.REMOTE_REPLICATION_NODE_MONITOR_INTERVAL);
    }
}