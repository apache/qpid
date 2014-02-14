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
import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.model.adapter.VirtualHostAdapter;
import org.apache.qpid.server.plugin.MessageStoreFactory;
import org.apache.qpid.server.plugin.VirtualHostFactory;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.MessageStoreConstants;
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
                                         VirtualHostConfiguration hostConfig,
                                         org.apache.qpid.server.model.VirtualHost virtualHost)
    {
        return new StandardVirtualHost(virtualHostRegistry, brokerStatisticsGatherer, parentSecurityManager, hostConfig, virtualHost);
    }


    public static final String STORE_TYPE_ATTRIBUTE = org.apache.qpid.server.model.VirtualHost.STORE_TYPE;
    public static final String STORE_PATH_ATTRIBUTE = org.apache.qpid.server.model.VirtualHost.STORE_PATH;

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

        for(MessageStoreFactory factory : storeCreator.getFactories())
        {
            if(factory.getType().equalsIgnoreCase((String)storeType))
            {
                factory.validateAttributes(attributes);
            }
        }

    }

    @Override
    public Map<String,Object> createVirtualHostConfiguration(VirtualHostAdapter virtualHostAdapter)
    {
        Map<String,Object> convertedMap = new LinkedHashMap<String, Object>();
        convertedMap.put("store.type", virtualHostAdapter.getAttribute(org.apache.qpid.server.model.VirtualHost.STORE_TYPE));
        convertedMap.put("store.environment-path", virtualHostAdapter.getAttribute(org.apache.qpid.server.model.VirtualHost.STORE_PATH));

        return convertedMap;
    }

    @Override
    public Map<String, Object> convertVirtualHostConfiguration(Configuration configuration)
    {
        Map<String,Object> convertedMap = new LinkedHashMap<String, Object>();
        Configuration storeConfiguration = configuration.subset("store");
        convertedMap.put(org.apache.qpid.server.model.VirtualHost.STORE_TYPE, storeConfiguration.getString("type"));
        convertedMap.put(org.apache.qpid.server.model.VirtualHost.STORE_PATH, storeConfiguration.getString(MessageStoreConstants.ENVIRONMENT_PATH_PROPERTY));

        convertedMap.put(MessageStoreConstants.OVERFULL_SIZE_ATTRIBUTE, storeConfiguration.getString(MessageStoreConstants.OVERFULL_SIZE_PROPERTY));
        convertedMap.put(MessageStoreConstants.UNDERFULL_SIZE_ATTRIBUTE, storeConfiguration.getString(MessageStoreConstants.UNDERFULL_SIZE_PROPERTY));

        for(MessageStoreFactory mf : new MessageStoreCreator().getFactories())
        {
            convertedMap.putAll(mf.convertStoreConfiguration(storeConfiguration));
        }

        return convertedMap;

    }
}
