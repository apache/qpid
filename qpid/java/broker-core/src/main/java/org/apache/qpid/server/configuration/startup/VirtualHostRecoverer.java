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


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.VirtualHostAdapter;
import org.apache.qpid.server.plugin.ReplicationNodeFactory;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.util.MapValueConverter;

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

        // TODO temporary code to bridge from VH attributes to LocalReplicationNode - will be move into a new ReplicationNodeRecoverer
        if (attributes.containsKey(VirtualHost.TYPE))
        {
            String type = MapValueConverter.getStringAttribute(VirtualHost.TYPE, attributes);
            ReplicationNodeFactory replicationNodeFactory = ReplicationNodeFactory.FACTORIES.get(type);
            
            UUID uuid = null;
            Map<String, Object> replicationNodeAttributes = new HashMap<String, Object>();
            replicationNodeAttributes.put(ReplicationNode.NAME, attributes.get("haNodeName"));
            replicationNodeAttributes.put(ReplicationNode.GROUP_NAME, attributes.get("haGroupName"));
            replicationNodeAttributes.put(ReplicationNode.HOST_PORT, attributes.get("haNodeAddress"));
            replicationNodeAttributes.put(ReplicationNode.HELPER_HOST_PORT, attributes.get("haHelperAddress"));

            if (attributes.get("haDurability") != null)
            {
                replicationNodeAttributes.put(ReplicationNode.DURABILITY, attributes.get("haDurability"));
            }

            if (attributes.get("haDesignatedPrimary") != null)
            {
                replicationNodeAttributes.put(ReplicationNode.DESIGNATED_PRIMARY, attributes.get("haDesignatedPrimary"));
            }

            if (attributes.get("haCoalescingSync") != null)
            {
                replicationNodeAttributes.put(ReplicationNode.COALESCING_SYNC, attributes.get("haCoalescingSync"));
            }

            if (attributes.get("bdbEnvironmentConfig") != null)
            {
                replicationNodeAttributes.put(ReplicationNode.PARAMETERS, attributes.get("bdbEnvironmentConfig"));
            }

            if (attributes.get("haReplicationConfig") != null)
            {
                replicationNodeAttributes.put(ReplicationNode.REPLICATION_PARAMETERS, attributes.get("haReplicationConfig"));
            }

            if (replicationNodeFactory != null)
            {
                ReplicationNode node = replicationNodeFactory.createInstance(uuid , attributes, virtualHostAdapter);
                virtualHostAdapter.onReplicationNodeRecovered(node);
            }
        }
        return virtualHostAdapter;
    }

}
