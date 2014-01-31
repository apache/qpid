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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.berkeleydb.EnvironmentFacade;
import org.apache.qpid.server.store.berkeleydb.EnvironmentFacadeFactory;

import com.sleepycat.je.Durability;
import com.sleepycat.je.Durability.SyncPolicy;

//TODO: Should LocalReplicationNode implement EnvironmentFacadeFactory instead of having this class?
public class ReplicatedEnvironmentFacadeFactory implements EnvironmentFacadeFactory
{

    @Override
    public EnvironmentFacade createEnvironmentFacade(VirtualHost virtualHost, boolean isMessageStore)
    {
        Collection<ReplicationNode> replicationNodes = virtualHost.getChildren(ReplicationNode.class);
        if (replicationNodes == null || replicationNodes.size() != 1)
        {
            throw new IllegalStateException("Expected exactly one replication node but got " + (replicationNodes==null ? 0 :replicationNodes.size()) + " nodes");
        }
        ReplicationNode localNode = replicationNodes.iterator().next();
        if (!(localNode instanceof LocalReplicationNode))
        {
            throw new IllegalStateException("Cannot find local replication node among virtual host nodes");
        }
        LocalReplicationNode localReplicationNode = (LocalReplicationNode)localNode;

        String durability = (String)localNode.getAttribute(ReplicationNode.DURABILITY);
        Boolean coalescingSync = (Boolean)localNode.getAttribute(ReplicationNode.COALESCING_SYNC);

        if (coalescingSync && Durability.parse(durability).getLocalSync() == SyncPolicy.SYNC)
        {
            throw new IllegalConfigurationException("Coalescing sync cannot be used with master sync policy " + SyncPolicy.SYNC
                    + "! Please set highAvailability.coalescingSync to false in store configuration.");
        }

        ReplicatedEnvironmentFacade facade =  new ReplicatedEnvironmentFacade(localReplicationNode, new RemoteReplicationNodeFactoryImpl(virtualHost));
        localReplicationNode.setReplicatedEnvironmentFacade(facade);
        return facade;
    }

    static class RemoteReplicationNodeFactoryImpl implements RemoteReplicationNodeFactory
    {
        private VirtualHost _virtualHost;

        public RemoteReplicationNodeFactoryImpl(VirtualHost virtualHost)
        {
            _virtualHost = virtualHost;
        }

        @Override
        public RemoteReplicationNode create(com.sleepycat.je.rep.ReplicationNode replicationNode, String groupName)
        {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(ReplicationNode.NAME, replicationNode.getName());
            attributes.put(ReplicationNode.GROUP_NAME, groupName);
            attributes.put(ReplicationNode.HOST_PORT, replicationNode.getHostName() + ":" + replicationNode.getPort());
            return new RemoteReplicationNode(replicationNode, groupName, _virtualHost, _virtualHost.getTaskExecutor());
        }

        @Override
        public long getRemoteNodeMonitorInterval()
        {
            return (Long)_virtualHost.getAttribute(VirtualHost.REMOTE_REPLICATION_NODE_MONITOR_INTERVAL);
        }
    }

    @Override
    public String getType()
    {
        return ReplicatedEnvironmentFacade.TYPE;
    }
}
