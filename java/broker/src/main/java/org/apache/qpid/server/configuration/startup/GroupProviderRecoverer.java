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
package org.apache.qpid.server.configuration.startup;

import java.util.Map;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.adapter.GroupProviderAdapter;
import org.apache.qpid.server.plugin.GroupManagerFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.security.group.GroupManager;

public class GroupProviderRecoverer implements ConfiguredObjectRecoverer<GroupProvider>
{
    private QpidServiceLoader<GroupManagerFactory> _groupManagerServiceLoader;

    public GroupProviderRecoverer(QpidServiceLoader<GroupManagerFactory> groupManagerServiceLoader)
    {
        super();
        _groupManagerServiceLoader = groupManagerServiceLoader;
    }

    @Override
    public GroupProvider create(RecovererProvider recovererProvider, ConfigurationEntry configurationEntry, ConfiguredObject... parents)
    {
        Broker broker = RecovererHelper.verifyOnlyBrokerIsParent(parents);
        Map<String, Object> attributes = configurationEntry.getAttributes();
        GroupManager groupManager = createGroupManager(attributes);
        if (groupManager == null)
        {
            throw new IllegalConfigurationException("Cannot create GroupManager from attributes : " + attributes);
        }
        GroupProviderAdapter groupProviderAdapter = new GroupProviderAdapter(configurationEntry.getId(), groupManager, broker);
        return groupProviderAdapter;
    }

    private GroupManager createGroupManager(Map<String, Object> attributes)
    {
        for(GroupManagerFactory factory : _groupManagerServiceLoader.instancesOf(GroupManagerFactory.class))
        {
            GroupManager groupManager = factory.createInstance(attributes);
            if (groupManager != null)
            {
               return groupManager;
            }
        }
        return null;
    }

}
