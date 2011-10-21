/*
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
 */
package org.apache.qpid.server.plugins;

import junit.framework.TestCase;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.exchange.ExchangeType;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.util.TestApplicationRegistry;

import java.util.Map;

public class ExtrasTest extends TestCase
{
    private static final String TEST_EXCHANGE_CLASS = "org.apache.qpid.extras.exchanges.example.TestExchangeType";
    
    private static final String PLUGIN_DIRECTORY = System.getProperty("example.plugin.target");
    private static final String CACHE_DIRECTORY = System.getProperty("example.cache.target");

    IApplicationRegistry _registry;

    @Override
    public void setUp() throws Exception
    {
        PropertiesConfiguration properties = new PropertiesConfiguration();
        properties.addProperty("plugin-directory", PLUGIN_DIRECTORY);
        properties.addProperty("cache-directory", CACHE_DIRECTORY);
        ServerConfiguration config = new ServerConfiguration(properties);

        // This Test requires an application Registry
        ApplicationRegistry.initialise(new TestApplicationRegistry(config));
        _registry = ApplicationRegistry.getInstance();
    }

    @Override
    public void tearDown() throws Exception
    {
        ApplicationRegistry.remove();
    }

    public void testLoadExchanges() throws Exception
    {
        PluginManager manager = _registry.getPluginManager();
        Map<String, ExchangeType<?>> exchanges = manager.getExchanges();
        assertNotNull("No exchanges found in " + PLUGIN_DIRECTORY, exchanges);
        assertEquals("Wrong number of exchanges found in " + PLUGIN_DIRECTORY, 2, exchanges.size());
        assertNotNull("Wrong exchange found in " + PLUGIN_DIRECTORY, exchanges.get(TEST_EXCHANGE_CLASS));
    } 
    
    public void testNoExchanges() throws Exception
    {
        PluginManager manager = new PluginManager("/path/to/nowhere", "/tmp");
        Map<String, ExchangeType<?>> exchanges = manager.getExchanges();
        assertTrue("Exchanges found", exchanges.isEmpty());
    }
}
