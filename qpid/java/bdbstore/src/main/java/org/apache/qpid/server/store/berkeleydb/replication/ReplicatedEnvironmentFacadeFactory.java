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

import java.util.Map;

import org.apache.qpid.server.store.berkeleydb.EnvironmentFacade;
import org.apache.qpid.server.store.berkeleydb.EnvironmentFacadeFactory;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNodeImpl;

public class ReplicatedEnvironmentFacadeFactory implements EnvironmentFacadeFactory
{
    @Override
    public EnvironmentFacade createEnvironmentFacade(final Map<String, Object> messageStoreSettings, EnvironmentFacadeTask... initialisationTasks)
    {
        ReplicatedEnvironmentConfiguration configuration = new ReplicatedEnvironmentConfiguration()
        {
            @Override
            public boolean isDesignatedPrimary()
            {
                return (Boolean)messageStoreSettings.get(BDBHAVirtualHostNodeImpl.DESIGNATED_PRIMARY);
            }

            @Override
            public boolean isCoalescingSync()
            {
                return (Boolean)messageStoreSettings.get(BDBHAVirtualHostNodeImpl.COALESCING_SYNC);
            }

            @Override
            public String getStorePath()
            {
                return (String) messageStoreSettings.get(BDBHAVirtualHostNodeImpl.STORE_PATH);
            }

            @SuppressWarnings("unchecked")
            @Override
            public Map<String, String> getParameters()
            {
                return (Map<String, String>) messageStoreSettings.get(BDBHAVirtualHostNodeImpl.ENVIRONMENT_CONFIGURATION);
            }

            @SuppressWarnings("unchecked")
            @Override
            public Map<String, String> getReplicationParameters()
            {
                return (Map<String, String>) messageStoreSettings.get(BDBHAVirtualHostNodeImpl.REPLICATED_ENVIRONMENT_CONFIGURATION);
            }

            @Override
            public int getQuorumOverride()
            {
                return (Integer)messageStoreSettings.get(BDBHAVirtualHostNodeImpl.QUORUM_OVERRIDE);
            }

            @Override
            public int getPriority()
            {
                return (Integer)messageStoreSettings.get(BDBHAVirtualHostNodeImpl.PRIORITY);
            }

            @Override
            public String getName()
            {
                return (String)messageStoreSettings.get(BDBHAVirtualHostNodeImpl.NAME);
            }

            @Override
            public String getHostPort()
            {
                return (String)messageStoreSettings.get(BDBHAVirtualHostNodeImpl.ADDRESS);
            }

            @Override
            public String getHelperHostPort()
            {
                return (String)messageStoreSettings.get(BDBHAVirtualHostNodeImpl.HELPER_ADDRESS);
            }

            @Override
            public String getGroupName()
            {
                return (String)messageStoreSettings.get(BDBHAVirtualHostNodeImpl.GROUP_NAME);
            }

            @Override
            public String getDurability()
            {
                return (String)messageStoreSettings.get(BDBHAVirtualHostNodeImpl.DURABILITY);
             }
        };
        return new ReplicatedEnvironmentFacade(configuration, initialisationTasks);

    }

    @Override
    public String getType()
    {
        return ReplicatedEnvironmentFacade.TYPE;
    }

}
