package org.apache.qpid.server.virtualhost;/*
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

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.model.adapter.VirtualHostAdapter;
import org.apache.qpid.server.plugin.VirtualHostFactory;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.store.MessageStoreCreator;

public class StandardVirtualHostFactory implements VirtualHostFactory
{

    public static final String TYPE = "STANDARD";

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public VirtualHost createVirtualHost(VirtualHostRegistry virtualHostRegistry,
                                         StatisticsGatherer brokerStatisticsGatherer,
                                         org.apache.qpid.server.security.SecurityManager parentSecurityManager,
                                         VirtualHostConfiguration hostConfig) throws Exception
    {
        return new StandardVirtualHost(virtualHostRegistry, brokerStatisticsGatherer, parentSecurityManager, hostConfig);
    }


    private static final String STORE_TYPE_ATTRIBUTE = org.apache.qpid.server.model.VirtualHost.STORE_TYPE;
    private static final String STORE_PATH_ATTRIBUTE = org.apache.qpid.server.model.VirtualHost.STORE_PATH;

    @Override
    public void validateAttributes(Map<String, Object> attributes)
    {

        // need store type and path
        Object storeType = attributes.get(STORE_TYPE_ATTRIBUTE);
        if(!(storeType instanceof String))
        {

            throw new IllegalArgumentException("Attribute '"+ STORE_TYPE_ATTRIBUTE
                                               +"' is required and must be of type String.");
        }
        final MessageStoreCreator storeCreator = new MessageStoreCreator();
        if(!storeCreator.isValidType((String)storeType))
        {
            throw new IllegalArgumentException("Attribute '"+ STORE_TYPE_ATTRIBUTE
                                                +"' has value '"+storeType+"' which is not one of the valid values: "
                                                + storeCreator.getStoreTypes() + ".");

        }

        // TODO - each store type should validate its own attributes
        if(!((String) storeType).equalsIgnoreCase(MemoryMessageStore.TYPE))
        {
            Object storePath = attributes.get(STORE_PATH_ATTRIBUTE);
            if(!(storePath instanceof String))
            {
                throw new IllegalArgumentException("Attribute '"+ STORE_PATH_ATTRIBUTE
                                                               +"' is required and must be of type String.");

            }
        }

    }

    @Override
    public Map<String,Object> createVirtualHostConfiguration(VirtualHostAdapter virtualHostAdapter)
    {
        Map<String,Object> convertedMap = new LinkedHashMap<String, Object>();
        convertedMap.put("store.type", virtualHostAdapter.getAttribute(org.apache.qpid.server.model.VirtualHost.STORE_TYPE));
        convertedMap.put("store.environment-path", virtualHostAdapter.getAttribute(org.apache.qpid.server.model.VirtualHost.STORE_PATH));

        // TODO - this should all be inverted to populate vhost from xml and then pass model object to the store

        convertedMap.put("store.pool.type",virtualHostAdapter.getAttribute("connectionPool"));
        convertedMap.put("store.pool.minConnectionsPerPartition",virtualHostAdapter.getAttribute("minConnectionsPerPartition"));
        convertedMap.put("store.pool.maxConnectionsPerPartition",virtualHostAdapter.getAttribute("maxConnectionsPerPartition"));
        convertedMap.put("store.pool.partitionCount",virtualHostAdapter.getAttribute("partitionCount"));

        return convertedMap;
    }
}
