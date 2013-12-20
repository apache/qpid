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


import java.util.Collection;
import java.util.Map;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.VirtualHostAdapter;
import org.apache.qpid.server.stats.StatisticsGatherer;

public class VirtualHostRecoverer implements ConfiguredObjectRecoverer<VirtualHost>
{
    private StatisticsGatherer _brokerStatisticsGatherer;

    public VirtualHostRecoverer(StatisticsGatherer brokerStatisticsGatherer)
    {
        super();
        _brokerStatisticsGatherer = brokerStatisticsGatherer;
    }

    @Override
    public VirtualHost create(RecovererProvider recovererProvider, ConfigurationEntry entry, ConfiguredObject... parents)
    {
        Broker broker = RecovererHelper.verifyOnlyBrokerIsParent(parents);

        Map<String, Object> attributes = entry.getAttributes();
        VirtualHostAdapter virtualHostAdapter = new VirtualHostAdapter(entry.getId(), attributes, broker, _brokerStatisticsGatherer, broker.getTaskExecutor());

        Map<String, Collection<ConfigurationEntry>> childEntries = entry.getChildren();
        for (Map.Entry<String, Collection<ConfigurationEntry>> childrenEntry : childEntries.entrySet())
        {
            String childType = childrenEntry.getKey();
            ConfiguredObjectRecoverer<? extends ConfiguredObject> recoverer = recovererProvider.getRecoverer(childType);
            for (ConfigurationEntry childEntry : childrenEntry.getValue())
            {
                ConfiguredObject configuredObject = recoverer.create(recovererProvider, childEntry, virtualHostAdapter);
                virtualHostAdapter.recoverChild(configuredObject);
            }
        }
        return virtualHostAdapter;
    }

}
