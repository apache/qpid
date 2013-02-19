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

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.plugin.QpidServiceLoader;

public class BrokerConfigurationStoreCreator
{
    /**
     * Path to resource containing broker default configuration
     */
    public static final String INITIAL_STORE_LOCATION = "initial-store.json";

    /**
     * Create broker configuration store for given store location, store type
     * and command line options
     */
    public ConfigurationEntryStore createStore(String storeLocation, String storeType, BrokerOptions options)
    {
        ConfigurationEntryStore store = null;
        QpidServiceLoader<ConfigurationStoreFactory> serviceLoader = new QpidServiceLoader<ConfigurationStoreFactory>();
        Iterable<ConfigurationStoreFactory> configurationStoreFactories = serviceLoader.instancesOf(ConfigurationStoreFactory.class);
        for (ConfigurationStoreFactory storeFactory : configurationStoreFactories)
        {
            if (storeFactory.getStoreType().equals(storeType))
            {
                store = storeFactory.createStore();
                break;
            }
        }
        if (store == null)
        {
            throw new IllegalConfigurationException("Cannot create store for the type " + storeType);
        }
        store.open(storeLocation);
        return store;
    }

}
