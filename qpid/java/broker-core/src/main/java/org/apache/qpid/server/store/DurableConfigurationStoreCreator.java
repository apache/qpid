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
package org.apache.qpid.server.store;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.plugin.DurableConfigurationStoreFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;

public class DurableConfigurationStoreCreator
{
    private Map<String, DurableConfigurationStoreFactory> _factories = new HashMap<String, DurableConfigurationStoreFactory>();

    public DurableConfigurationStoreCreator()
    {
        QpidServiceLoader<DurableConfigurationStoreFactory> qpidServiceLoader = new QpidServiceLoader<DurableConfigurationStoreFactory>();
        Iterable<DurableConfigurationStoreFactory> factories = qpidServiceLoader.atLeastOneInstanceOf(DurableConfigurationStoreFactory.class);
        for (DurableConfigurationStoreFactory durableConfigurationStoreFactory : factories)
        {
            String type = durableConfigurationStoreFactory.getType();
            DurableConfigurationStoreFactory factory = _factories.put(type.toLowerCase(), durableConfigurationStoreFactory);
            if (factory != null)
            {
                throw new IllegalStateException("DurableConfigurationStoreFactory with type name '" + type
                        + "' is already registered using class '" + factory.getClass().getName() + "', can not register class '"
                        + durableConfigurationStoreFactory.getClass().getName() + "'");
            }
        }
    }

    public boolean isValidType(String storeType)
    {
        return _factories.containsKey(storeType.toLowerCase());
    }


    public DurableConfigurationStore createMessageStore(String storeType)
    {
        DurableConfigurationStoreFactory factory = _factories.get(storeType.toLowerCase());
        if (factory == null)
        {
            throw new IllegalConfigurationException("Unknown store type: " + storeType
                                                    + ". Supported types: " + _factories.keySet());
        }
        return factory.createDurableConfigurationStore();
    }

    public Collection<DurableConfigurationStoreFactory> getFactories()
    {
        return Collections.unmodifiableCollection(_factories.values());
    }

    public Collection<String> getStoreTypes()
    {
        return Collections.unmodifiableCollection(_factories.keySet());
    }
}
