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
package org.apache.qpid.server.configuration;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.configuration.store.MemoryConfigurationEntryStore;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.plugin.ConfigurationStoreFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;

/**
 * A helper class responsible for creation and opening of broker store.
 */
public class BrokerConfigurationStoreCreator
{
    private Map<String, ConfigurationStoreFactory> _factories = new HashMap<String, ConfigurationStoreFactory>();

    public BrokerConfigurationStoreCreator()
    {
        QpidServiceLoader<ConfigurationStoreFactory> serviceLoader = new QpidServiceLoader<ConfigurationStoreFactory>();
        Iterable<ConfigurationStoreFactory> configurationStoreFactories = serviceLoader
                .instancesOf(ConfigurationStoreFactory.class);
        for (ConfigurationStoreFactory storeFactory : configurationStoreFactories)
        {
            String type = storeFactory.getType();
            ConfigurationStoreFactory factory = _factories.put(type.toLowerCase(), storeFactory);
            if (factory != null)
            {
                throw new IllegalStateException("ConfigurationStoreFactory with type name '" + type
                        + "' is already registered using class '" + factory.getClass().getName() + "', can not register class '"
                        + storeFactory.getClass().getName() + "'");
            }
        }
    }

    /**
     * Create broker configuration store for a given store location, store type, initial json config location
     *
     * @param systemContext applicationContext
     * @param storeType store type
     * @param initialConfigLocation initial store location
     * @param overwrite whether to overwrite an existing configuration store with the initial configuration
     * @param configProperties a map of configuration properties the store can use to resolve configuration variables
     * @throws IllegalConfigurationException if store type is unknown
     */
    public ConfigurationEntryStore createStore(SystemContext systemContext, String storeType, String initialConfigLocation, boolean overwrite, Map<String, String> configProperties)
    {
        ConfigurationEntryStore initialStore = new MemoryConfigurationEntryStore(initialConfigLocation, null, configProperties);
        ConfigurationStoreFactory factory = _factories.get(storeType.toLowerCase());
        if (factory == null)
        {
            throw new IllegalConfigurationException("Unknown store type: " + storeType);
        }
        return factory.createStore(systemContext, initialStore, overwrite, configProperties);
    }

    public Collection<String> getStoreTypes()
    {
        return Collections.unmodifiableCollection(_factories.keySet());
    }
}
