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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.plugin.PluginFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;

public class PluginRecovererTest extends TestCase
{
    private UUID _id;
    private Map<String, Object> _attributes;

    private PluginFactory _factory;
    private QpidServiceLoader<PluginFactory> _pluginFactoryServiceLoader;
    private Broker _broker;
    private ConfigurationEntry _configurationEntry;

    @SuppressWarnings("unchecked")
    protected void setUp() throws Exception
    {
        super.setUp();
        _id = UUID.randomUUID();
        _attributes = new HashMap<String, Object>();

        _factory = mock(PluginFactory.class);

        _pluginFactoryServiceLoader = mock(QpidServiceLoader.class);
        when(_pluginFactoryServiceLoader.instancesOf(PluginFactory.class)).thenReturn(Collections.singletonList(_factory ));

        _broker = mock(Broker.class);

        _configurationEntry = mock(ConfigurationEntry.class);
        when(_configurationEntry.getId()).thenReturn(_id);
        when(_configurationEntry.getAttributes()).thenReturn(_attributes);
    }

    public void testCreate()
    {
        Plugin pluginFromFactory = mock(Plugin.class);
        when(pluginFromFactory.getId()).thenReturn(_id);
        when(_factory.createInstance(_id, _attributes, _broker)).thenReturn(pluginFromFactory);

        PluginRecoverer pluginRecoverer = new PluginRecoverer(_pluginFactoryServiceLoader);
        ConfiguredObject pluginFromRecoverer = pluginRecoverer.create(null, _configurationEntry, _broker);
        assertNotNull("Null group provider", pluginFromRecoverer);
        assertSame("Unexpected plugin", pluginFromFactory, pluginFromRecoverer);
        assertEquals("Unexpected ID", _id, pluginFromRecoverer.getId());
    }

    public void testCreateThrowsExceptionForUnexpectedId()
    {
        Plugin pluginFromFactory = mock(Plugin.class);
        when(pluginFromFactory.getId()).thenReturn(UUID.randomUUID());
        when(_factory.createInstance(_id, _attributes, _broker)).thenReturn(pluginFromFactory);

        PluginRecoverer pluginRecoverer = new PluginRecoverer(_pluginFactoryServiceLoader);
        try
        {
            pluginRecoverer.create(null, _configurationEntry, _broker);
            fail("An exception should be thrown for incorrect id");
        }
        catch(IllegalStateException e)
        {
            //pass
        }
    }

    public void testCreateThrowsExceptionWhenNoPluginIsCreated()
    {
        when(_factory.createInstance(_id, _attributes, _broker)).thenReturn(null);

        PluginRecoverer pluginRecoverer = new PluginRecoverer(_pluginFactoryServiceLoader);
        try
        {
            pluginRecoverer.create(null, _configurationEntry, _broker);
            fail("Configuration exception should be thrown when plugin is not created");
        }
        catch(IllegalConfigurationException e)
        {
            // pass
        }
    }

}
