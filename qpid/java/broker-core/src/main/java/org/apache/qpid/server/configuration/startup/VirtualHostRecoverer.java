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

        return new VirtualHostAdapter(entry.getId(), entry.getAttributes(), broker, _brokerStatisticsGatherer, broker.getTaskExecutor());
    }

}
