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
package org.apache.qpid.server.store.berkeleydb;

import java.util.Map;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.plugin.DurableConfigurationStoreFactory;
import org.apache.qpid.server.plugin.MessageStoreFactory;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;

public class BDBMessageStoreFactory implements MessageStoreFactory, DurableConfigurationStoreFactory
{

    @Override
    public String getType()
    {
        return StandardEnvironmentFacade.TYPE;
    }

    @Override
    public DurableConfigurationStore createDurableConfigurationStore()
    {
        return new BDBMessageStore();
    }

    @Override
    public MessageStore createMessageStore()
    {
        return new BDBMessageStore();
    }

    @Override
    public void validateAttributes(Map<String, Object> attributes)
    {
        if(getType().equals(attributes.get(VirtualHost.STORE_TYPE)))
        {
            Object storePath = attributes.get(VirtualHost.STORE_PATH);
            if(!(storePath instanceof String))
            {
                throw new IllegalArgumentException("Attribute '"+ VirtualHost.STORE_PATH
                                                               +"' is required and must be of type String.");

            }
        }
        if(getType().equals(attributes.get(VirtualHost.CONFIG_STORE_TYPE)))
        {
            Object storePath = attributes.get(VirtualHost.CONFIG_STORE_PATH);
            if(!(storePath instanceof String))
            {
                throw new IllegalArgumentException("Attribute '"+ VirtualHost.CONFIG_STORE_PATH
                                                               +"' is required and must be of type String.");

            }
        }
    }
}
