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

import java.util.Collection;
import java.util.Map;

import org.apache.qpid.server.plugin.MessageStoreFactory;
import org.apache.qpid.server.plugin.VirtualHostFactory;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.MessageStore;

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
                                         org.apache.qpid.server.model.VirtualHost virtualHost)
    {
        return new StandardVirtualHost(virtualHostRegistry, brokerStatisticsGatherer, parentSecurityManager, virtualHost);
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

        Object storeType = messageStoreSettings.get(MessageStore.STORE_TYPE);

        // need store type and path
        Collection<String> knownTypes = MessageStoreFactory.FACTORY_LOADER.getSupportedTypes();

        if (storeType == null)
        {
            throw new IllegalArgumentException("Setting '"+ MessageStore.STORE_TYPE
                    +"' is required in attribute " + org.apache.qpid.server.model.VirtualHost.MESSAGE_STORE_SETTINGS + ". Known types are : " + knownTypes);
        }
        else if (!(storeType instanceof String))
        {
            throw new IllegalArgumentException("Setting '"+ MessageStore.STORE_TYPE
                                               +"' is required and must be of type String. "
                                               +"Known types are : " + knownTypes);
        }

        MessageStoreFactory factory = MessageStoreFactory.FACTORY_LOADER.get((String)storeType);
        if(factory == null)
        {
            throw new IllegalArgumentException("Setting '"+ MessageStore.STORE_TYPE
                                                +"' has value '" + storeType + "' which is not one of the valid values: "
                                                + "Known types are : " + knownTypes);
        }

        factory.validateAttributes(attributes);

    }
}
