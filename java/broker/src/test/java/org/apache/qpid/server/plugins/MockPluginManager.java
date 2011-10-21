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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.exchange.ExchangeType;
import org.apache.qpid.server.security.SecurityPluginFactory;

public class MockPluginManager extends PluginManager
{
    private Map<String, SecurityPluginFactory> _securityPlugins = new HashMap<String, SecurityPluginFactory>();
    private Map<List<String>, ConfigurationPluginFactory> _configPlugins = new HashMap<List<String>, ConfigurationPluginFactory>();

    public MockPluginManager(String pluginPath, String cachePath) throws Exception
    {
        super(pluginPath, cachePath);
    }

    @Override
    public Map<String, ExchangeType<?>> getExchanges()
    {
       return null;
    }

    @Override
    public Map<String, SecurityPluginFactory> getSecurityPlugins()
    {
        return _securityPlugins;
    }

    @Override
    public Map<List<String>, ConfigurationPluginFactory> getConfigurationPlugins()
    {
        return _configPlugins;
    }
}
