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
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.adapter.GroupProviderFactory;

public class GroupProviderRecoverer implements ConfiguredObjectRecoverer<GroupProvider>
{
    private GroupProviderFactory _groupProviderFactory;

    public GroupProviderRecoverer(GroupProviderFactory groupProviderFactory)
    {
        super();
        _groupProviderFactory = groupProviderFactory;
    }

    @Override
    public GroupProvider create(RecovererProvider recovererProvider, ConfigurationEntry configurationEntry, ConfiguredObject... parents)
    {
        Broker broker = RecovererHelper.verifyOnlyBrokerIsParent(parents);
        Map<String, Object> attributes = configurationEntry.getAttributes();

        GroupProvider groupProvider = _groupProviderFactory.recover(configurationEntry.getId(), broker, attributes);

        return groupProvider;
    }
}
