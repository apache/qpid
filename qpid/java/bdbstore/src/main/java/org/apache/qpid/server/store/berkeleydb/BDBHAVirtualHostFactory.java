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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.model.adapter.VirtualHostAdapter;
import org.apache.qpid.server.plugin.VirtualHostFactory;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.MessageStoreConstants;
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
                                         VirtualHostConfiguration hostConfig,
                                         org.apache.qpid.server.model.VirtualHost virtualHost) throws Exception
    {
        return new BDBHAVirtualHost(virtualHostRegistry,
                                    brokerStatisticsGatherer,
                                    parentSecurityManager,
                                    hostConfig,
                                    virtualHost);
    }

    @Override
    public void validateAttributes(Map<String, Object> attributes)
    {
        validateAttribute(org.apache.qpid.server.model.VirtualHost.STORE_PATH, String.class, attributes);
        validateAttribute("haGroupName", String.class, attributes);
        validateAttribute("haNodeName", String.class, attributes);
        validateAttribute("haNodeAddress", String.class, attributes);
        validateAttribute("haHelperAddress", String.class, attributes);
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

    @Override
    public Map<String, Object> createVirtualHostConfiguration(VirtualHostAdapter virtualHostAdapter)
    {
        LinkedHashMap<String,Object> convertedMap = new LinkedHashMap<String, Object>();
        convertedMap.put("store.environment-path", virtualHostAdapter.getAttribute(org.apache.qpid.server.model.VirtualHost.STORE_PATH));

        return convertedMap;
    }

    public Map<String, Object> convertVirtualHostConfiguration(Configuration configuration)
    {

        LinkedHashMap<String,Object> convertedMap = new LinkedHashMap<String, Object>();

        Configuration storeConfiguration = configuration.subset("store");

        convertedMap.put(org.apache.qpid.server.model.VirtualHost.STORE_PATH, storeConfiguration.getString(MessageStoreConstants.ENVIRONMENT_PATH_PROPERTY));
        convertedMap.put(MessageStoreConstants.OVERFULL_SIZE_ATTRIBUTE, storeConfiguration.getString(MessageStoreConstants.OVERFULL_SIZE_PROPERTY));
        convertedMap.put(MessageStoreConstants.UNDERFULL_SIZE_ATTRIBUTE, storeConfiguration.getString(MessageStoreConstants.UNDERFULL_SIZE_PROPERTY));
        convertedMap.put("haGroupName", configuration.getString("store.highAvailability.groupName"));
        convertedMap.put("haNodeName", configuration.getString("store.highAvailability.nodeName"));
        convertedMap.put("haNodeAddress", configuration.getString("store.highAvailability.nodeHostPort"));
        convertedMap.put("haHelperAddress", configuration.getString("store.highAvailability.helperHostPort"));

        final Object haDurability = configuration.getString("store.highAvailability.durability");
        if(haDurability !=null)
        {
            convertedMap.put("haDurability", haDurability);
        }

        final Object designatedPrimary = configuration.getString("store.highAvailability.designatedPrimary");
        if(designatedPrimary!=null)
        {
            convertedMap.put("haDesignatedPrimary", designatedPrimary);
        }

        final Object coalescingSync = configuration.getString("store.highAvailability.coalescingSync");
        if(coalescingSync!=null)
        {
            convertedMap.put("haCoalescingSync", coalescingSync);
        }


        Map<String, String> attributes = getEnvironmentMap(storeConfiguration, "envConfig");

        if(!attributes.isEmpty())
        {
            convertedMap.put("bdbEnvironmentConfig",attributes);
        }

        attributes = getEnvironmentMap(storeConfiguration, "repConfig");

        if(!attributes.isEmpty())
        {
            convertedMap.put("haReplicationConfig",attributes);
        }

        return convertedMap;

    }

    private Map<String, String> getEnvironmentMap(Configuration storeConfiguration, String configName)
    {
        final List<Object> argumentNames = storeConfiguration.getList(configName +".name");
        final List<Object> argumentValues = storeConfiguration.getList(configName +".value");
        final int initialSize = argumentNames.size();

        final Map<String,String> attributes = new HashMap<String,String>(initialSize);

        for (int i = 0; i < argumentNames.size(); i++)
        {
            final String argName = argumentNames.get(i).toString();
            final String argValue = argumentValues.get(i).toString();

            attributes.put(argName, argValue);
        }
        return attributes;
    }
}
