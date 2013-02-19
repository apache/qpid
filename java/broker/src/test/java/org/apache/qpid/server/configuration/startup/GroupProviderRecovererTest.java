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
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.plugin.GroupManagerFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.security.group.GroupManager;

import junit.framework.TestCase;

public class GroupProviderRecovererTest extends TestCase
{

    private UUID _id;
    private Map<String, Object> _attributes;

    private GroupManagerFactory _factory;
    private QpidServiceLoader<GroupManagerFactory> _groupManagerServiceLoader;
    private Broker _broker;
    private ConfigurationEntry _configurationEntry;

    @SuppressWarnings("unchecked")
    protected void setUp() throws Exception
    {
        super.setUp();
        _id = UUID.randomUUID();
        _attributes = new HashMap<String, Object>();

        _factory = mock(GroupManagerFactory.class);

        _groupManagerServiceLoader = mock(QpidServiceLoader.class);
        when(_groupManagerServiceLoader.instancesOf(GroupManagerFactory.class)).thenReturn(Collections.singletonList(_factory ));

        _broker = mock(Broker.class);

        _configurationEntry = mock(ConfigurationEntry.class);
        when(_configurationEntry.getId()).thenReturn(_id);
        when(_configurationEntry.getAttributes()).thenReturn(_attributes);
    }

    public void testCreate()
    {
        GroupManager groupManager = mock(GroupManager.class);
        String name = groupManager.getClass().getSimpleName();
        when(_factory.createInstance(_attributes)).thenReturn(groupManager);
        GroupProviderRecoverer groupProviderRecoverer = new GroupProviderRecoverer(_groupManagerServiceLoader);
        GroupProvider groupProvider = groupProviderRecoverer.create(null, _configurationEntry, _broker);
        assertNotNull("Null group provider", groupProvider);
        assertEquals("Unexpected name", name, groupProvider.getName());
        assertEquals("Unexpected ID", _id, groupProvider.getId());
    }

    public void testCreateThrowsExceptionWhenNoGroupManagerIsCreated()
    {
        when(_factory.createInstance(_attributes)).thenReturn(null);

        GroupProviderRecoverer groupProviderRecoverer = new GroupProviderRecoverer(_groupManagerServiceLoader);
        try
        {
            groupProviderRecoverer.create(null, _configurationEntry, _broker);
            fail("Configuration exception should be thrown when group manager is not created");
        }
        catch(IllegalConfigurationException e)
        {
            // pass
        }
    }

}
