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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;


import junit.framework.TestCase;

public class VirtualHostRecovererTest extends TestCase
{
    public void testCreate()
    {
        VirtualHostRegistry virtualHostRegistry = mock(VirtualHostRegistry.class);
        StatisticsGatherer statisticsGatherer = mock(StatisticsGatherer.class);
        SecurityManager securityManager = mock(SecurityManager.class);
        Map<String, VirtualHostConfiguration> configurations = new HashMap<String, VirtualHostConfiguration>();
        VirtualHostRecoverer recoverer = new VirtualHostRecoverer(virtualHostRegistry, statisticsGatherer, securityManager, configurations);

        ConfigurationEntry entry = mock(ConfigurationEntry.class);
        Broker parent = mock(Broker.class);
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(VirtualHost.NAME, getName());
        when(entry.getAttributes()).thenReturn(attributes);
        configurations.put(getName(), mock(VirtualHostConfiguration.class));

        VirtualHost host = recoverer.create(null, entry, parent);

        assertNotNull("Null is returned", host);
        assertEquals("Unexpected name", getName(), host.getName());
    }

}
