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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.sleepycat.je.config.ConfigParam;
import com.sleepycat.je.config.EnvironmentParams;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.berkeleydb.EnvironmentFacade;
import org.apache.qpid.server.store.berkeleydb.EnvironmentFacadeFactory;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNode;

public class ReplicatedEnvironmentFacadeFactory implements EnvironmentFacadeFactory
{
    @Override
    public EnvironmentFacade createEnvironmentFacade(final ConfiguredObject<?> parent, final Map<String, Object> messageStoreSettings)
    {
        ReplicatedEnvironmentConfiguration configuration = new ReplicatedEnvironmentConfiguration()
        {
            @Override
            public boolean isDesignatedPrimary()
            {
                return (Boolean)messageStoreSettings.get(BDBHAVirtualHostNode.DESIGNATED_PRIMARY);
            }

            @Override
            public String getStorePath()
            {
                return (String) messageStoreSettings.get(BDBHAVirtualHostNode.STORE_PATH);
            }

            @Override
            public Map<String, String> getParameters()
            {
                return buildEnvironmentConfigParameters(parent);
            }

            @Override
            public Map<String, String> getReplicationParameters()
            {
                return buildReplicationConfigParameters(parent);
            }

            @Override
            public int getQuorumOverride()
            {
                return (Integer)messageStoreSettings.get(BDBHAVirtualHostNode.QUORUM_OVERRIDE);
            }

            @Override
            public int getPriority()
            {
                return (Integer)messageStoreSettings.get(BDBHAVirtualHostNode.PRIORITY);
            }

            @Override
            public String getName()
            {
                return (String)messageStoreSettings.get(BDBHAVirtualHostNode.NAME);
            }

            @Override
            public String getHostPort()
            {
                return (String)messageStoreSettings.get(BDBHAVirtualHostNode.ADDRESS);
            }

            @Override
            public String getHelperHostPort()
            {
                return (String)messageStoreSettings.get(BDBHAVirtualHostNode.HELPER_ADDRESS);
            }

            @Override
            public String getGroupName()
            {
                return (String)messageStoreSettings.get(BDBHAVirtualHostNode.GROUP_NAME);
            }
        };
        return new ReplicatedEnvironmentFacade(configuration);

    }

    private Map<String, String> buildEnvironmentConfigParameters(ConfiguredObject<?> parent)
    {
        return buildConfig(parent, false);
    }

    private Map<String, String> buildReplicationConfigParameters(ConfiguredObject<?> parent)
    {

        return buildConfig(parent, true);
    }

    private Map<String, String> buildConfig(ConfiguredObject<?> parent,  boolean selectReplicationParaemeters)
    {
        Map<String, String> targetMap = new HashMap<>();
        for (ConfigParam entry : EnvironmentParams.SUPPORTED_PARAMS.values())
        {
            final String name = entry.getName();
            if (entry.isForReplication() == selectReplicationParaemeters  && parent.getContext().containsKey(name))
            {
                String contextValue = parent.getContext().get(name);
                targetMap.put(name, contextValue);
            }
        }
        return Collections.unmodifiableMap(targetMap);
    }


}
