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
package org.apache.qpid.server.store.derby;

import java.util.Map;

import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.plugin.DurableConfigurationStoreFactory;
import org.apache.qpid.server.plugin.MessageStoreFactory;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;

public class DerbyMessageStoreFactory implements MessageStoreFactory, DurableConfigurationStoreFactory
{

    @Override
    public String getType()
    {
        return DerbyMessageStore.TYPE;
    }

    @Override
    public DurableConfigurationStore createDurableConfigurationStore()
    {
        return new DerbyMessageStore();
    }

    @Override
    public MessageStore createMessageStore()
    {
        return new DerbyMessageStore();
    }

    @Override
    public void validateAttributes(Map<String, Object> attributes)
    {
        @SuppressWarnings("unchecked")
        Map<String, Object> messageStoreSettings = (Map<String, Object>) attributes.get(VirtualHost.MESSAGE_STORE_SETTINGS);

        if(getType().equals(messageStoreSettings.get(MessageStore.STORE_TYPE)))
        {
            Object storePath = messageStoreSettings.get(MessageStore.STORE_PATH);
            if(!(storePath instanceof String))
            {
                throw new IllegalArgumentException("Setting '"+ MessageStore.STORE_PATH
                                                               +"' is required and must be of type String.");

            }
        }

    }

    @Override
    public void validateConfigurationStoreSettings(Map<String, Object> configurationStoreSettings)
    {
        if(configurationStoreSettings != null && getType().equals(configurationStoreSettings.get(DurableConfigurationStore.STORE_TYPE)))
        {
            Object storePath = configurationStoreSettings.get(DurableConfigurationStore.STORE_PATH);
            if(!(storePath instanceof String))
            {
                throw new IllegalArgumentException("Setting '"+ DurableConfigurationStore.STORE_PATH
                                                               +"' is required and must be of type String.");

            }
        }
    }

}
