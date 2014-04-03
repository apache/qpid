package org.apache.qpid.server.store.berkeleydb;/*
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

import java.util.Map;

import org.apache.qpid.server.plugin.VirtualHostFactory;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacadeFactory;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class BDBHAVirtualHostFactory implements VirtualHostFactory
{

    public static final String TYPE = "BDB_HA";

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public VirtualHost createVirtualHost(VirtualHostRegistry virtualHostRegistry,
                                         StatisticsGatherer brokerStatisticsGatherer,
                                         org.apache.qpid.server.security.SecurityManager parentSecurityManager,
                                         org.apache.qpid.server.model.VirtualHost virtualHost)
    {
        return new BDBHAVirtualHost(virtualHostRegistry,
                                    brokerStatisticsGatherer,
                                    parentSecurityManager,
                                    virtualHost);
    }

    @Override
    public void validateAttributes(Map<String, Object> attributes)
    {
        @SuppressWarnings("unchecked")
        Map<String, Object> messageStoreSettings = (Map<String, Object>)attributes.get(org.apache.qpid.server.model.VirtualHost.MESSAGE_STORE_SETTINGS);
        if (messageStoreSettings == null)
        {
            throw new IllegalArgumentException("Attribute '"+ org.apache.qpid.server.model.VirtualHost.MESSAGE_STORE_SETTINGS + "' is required.");
        }

        validateAttribute(MessageStore.STORE_PATH, String.class, messageStoreSettings);
        validateAttribute(ReplicatedEnvironmentFacadeFactory.GROUP_NAME, String.class, messageStoreSettings);
        validateAttribute(ReplicatedEnvironmentFacadeFactory.NODE_NAME, String.class, messageStoreSettings);
        validateAttribute(ReplicatedEnvironmentFacadeFactory.NODE_ADDRESS, String.class, messageStoreSettings);
        validateAttribute(ReplicatedEnvironmentFacadeFactory.HELPER_ADDRESS, String.class, messageStoreSettings);
    }

    private void validateAttribute(String attrName, Class<?> clazz, Map<String, Object> attributes)
    {
        Object attr = attributes.get(attrName);
        if(!clazz.isInstance(attr))
        {
            throw new IllegalArgumentException("Attribute '"+ attrName
                                               +"' is required and must be of type "+clazz.getSimpleName()+".");
        }
    }

}
