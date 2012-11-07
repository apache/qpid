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
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.VirtualHostAdapter;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class VirtualHostRecoverer extends AbstractBrokerChildRecoverer<VirtualHost>
{
    private VirtualHostRegistry _virtualHostRegistry;
    private StatisticsGatherer _statisticsGatherer;
    private SecurityManager _securityManager;
    private Map<String, VirtualHostConfiguration> _configurations;

    public VirtualHostRecoverer(VirtualHostRegistry virtualHostRegistry, StatisticsGatherer statisticsGatherer,
            SecurityManager securityManager, Map<String, VirtualHostConfiguration> configurations)
    {
        super();
        _virtualHostRegistry = virtualHostRegistry;
        _statisticsGatherer = statisticsGatherer;
        _securityManager = securityManager;
        _configurations = configurations;
    }


    @Override
    VirtualHost createBrokerChild(RecovererProvider recovererProvider, ConfigurationEntry entry, Broker broker)
    {
        Map<String, Object> attributes = entry.getAttributes();
        String name = (String) attributes.get(VirtualHost.NAME);
        if (name == null)
        {
            throw new IllegalConfigurationException("Mandatory attribute name is not found in virtual host configuration :"
                    + entry);
        }
        // XXX hack
        //VirtualHostConfiguration virtualHostConfiguration = (VirtualHostConfiguration)attributes.get("configuration");

        // String configurationPath = attributes.get("configuration");
        // VirtualHostConfiguration virtualHostConfiguration = new VirtualHostConfiguration(configurationPath);

        return new VirtualHostAdapter(entry.getId(), broker, attributes, _virtualHostRegistry,
                _statisticsGatherer, _securityManager, _configurations.get(name));
    }

}
