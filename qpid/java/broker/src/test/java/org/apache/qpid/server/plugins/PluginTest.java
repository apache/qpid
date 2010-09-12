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

import org.apache.qpid.server.exchange.ExchangeType;
import org.apache.qpid.server.util.InternalBrokerBaseCase;


import java.util.Map;

public class PluginTest extends InternalBrokerBaseCase
{
    private static final String TEST_EXCHANGE_CLASS = "org.apache.qpid.extras.exchanges.example.TestExchangeType";
    
    private static final String PLUGIN_DIRECTORY = System.getProperty("example.plugin.target");
    private static final String CACHE_DIRECTORY = System.getProperty("example.cache.target");

    @Override
    public void configure()
    {
        getConfiguration().getConfig().addProperty("plugin-directory", PLUGIN_DIRECTORY);
        getConfiguration().getConfig().addProperty("cache-directory", CACHE_DIRECTORY);
    }

    public void disabled_testLoadExchanges() throws Exception
    {
        PluginManager manager = getRegistry().getPluginManager();
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
