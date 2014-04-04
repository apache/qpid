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
package org.apache.qpid.server.configuration.store.factory;

import java.util.Map;

import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.store.MemoryConfigurationEntryStore;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.plugin.ConfigurationStoreFactory;

public class MemoryConfigurationStoreFactory  implements ConfigurationStoreFactory
{
    @Override
    public ConfigurationEntryStore createStore(SystemContext systemContext, ConfigurationEntryStore initialStore, boolean overwrite, Map<String, String> configProperties)
    {
        return new MemoryConfigurationEntryStore(systemContext, null, initialStore, configProperties);
    }

    @Override
    public String getType()
    {
        return MemoryConfigurationEntryStore.STORE_TYPE;
    }
}
