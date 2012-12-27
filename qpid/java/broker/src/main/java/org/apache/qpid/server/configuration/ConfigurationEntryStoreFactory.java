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

import java.io.File;
import java.net.URL;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.store.MergingStore;
import org.apache.qpid.server.configuration.store.JsonConfigurationEntryStore;
import org.apache.qpid.server.configuration.store.XMLConfigurationEntryStore;

public class ConfigurationEntryStoreFactory
{
    /**
     * Path to resource containing broker default configuration
     */
    public static final String DEFAULT_STORE = "default.json";

    /**
     * Create broker configuration store for given store location, store type
     * and command line options
     */
    public ConfigurationEntryStore createStore(String storeLocation, String storeType, BrokerOptions options)
    {
        ConfigurationEntryStoreType type = ConfigurationEntryStoreType.getType(storeType, storeLocation);
        ConfigurationEntryStore store = null;
        switch (type)
        {
        case JSON:
            store = new JsonConfigurationEntryStore(new File(storeLocation));
            break;
        case XML:
            try
            {
                return new XMLConfigurationEntryStore(new File(storeLocation), options);
            }
            catch (ConfigurationException e)
            {
                throw new IllegalConfigurationException("Unexpected error", e);
            }
        case DERBY:
        case BDB:
        default:
            throw new IllegalConfigurationException("Unsupported store type " + type);
        }

        if (!options.isNoDefault())
        {
            URL defaultStoreLocation = ConfigurationEntryStoreFactory.class.getClassLoader().getResource(DEFAULT_STORE);
            store = new MergingStore(store, new JsonConfigurationEntryStore(defaultStoreLocation));
        }
        return store;
    }

    public static enum ConfigurationEntryStoreType
    {
        JSON, XML, DERBY, BDB;

        public static ConfigurationEntryStoreType getType(String storeType, String storeLocation)
        {
            ConfigurationEntryStoreType type = null;
            if (storeType == null)
            {
                if (storeLocation != null)
                {
                    // define type from file extension
                    String lower = storeLocation.toLowerCase();
                    if (lower.endsWith(".json"))
                    {
                        type = ConfigurationEntryStoreType.JSON;
                    }
                    else if (lower.endsWith(".xml"))
                    {
                        type = ConfigurationEntryStoreType.XML;
                    }
                }
                if (type == null)
                {
                    // default is JSON
                    type = ConfigurationEntryStoreType.JSON;
                }
            }
            else
            {
                type = ConfigurationEntryStoreType.valueOf(storeType.toUpperCase());
            }
            return type;
        }
    }
}
