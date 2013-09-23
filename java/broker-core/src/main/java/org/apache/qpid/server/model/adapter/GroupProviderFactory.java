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
package org.apache.qpid.server.model.adapter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.plugin.GroupManagerFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.security.group.GroupManager;

public class GroupProviderFactory
{
    private final Map<String, GroupManagerFactory> _factories;
    private Collection<String> _supportedGroupProviders;

    public GroupProviderFactory(QpidServiceLoader<GroupManagerFactory> groupManagerFactoryServiceLoader)
    {
        Iterable<GroupManagerFactory> factories = groupManagerFactoryServiceLoader.instancesOf(GroupManagerFactory.class);

        Map<String, GroupManagerFactory> registeredGroupProviderFactories = new HashMap<String, GroupManagerFactory>();
        for (GroupManagerFactory factory : factories)
        {
            GroupManagerFactory existingFactory = registeredGroupProviderFactories.put(factory.getType(), factory);
            if (existingFactory != null)
            {
                throw new IllegalConfigurationException("Group provider factory of the same type '" + factory.getType()
                        + "' is already registered using class '" + existingFactory.getClass().getName()
                        + "', can not register class '" + factory.getClass().getName() + "'");
            }
        }
        _factories = registeredGroupProviderFactories;
        _supportedGroupProviders = Collections.unmodifiableCollection(registeredGroupProviderFactories.keySet());
    }

    /**
     * Creates {@link GroupProvider} for given ID, {@link Broker} and attributes.
     * <p>
     * The configured {@link GroupManagerFactory}'s are used to try to create the {@link GroupProvider}. The first non-null
     * instance is returned. The factories are used in non-deterministic order.
     */
    public GroupProvider create(UUID id, Broker broker, Map<String, Object> attributes)
    {
        GroupProviderAdapter authenticationProvider = createGroupProvider(id, broker, attributes);
        authenticationProvider.getGroupManager().onCreate();
        return authenticationProvider;
    }

    /**
     * Recovers {@link GroupProvider} with given ID, {@link Broker} and attributes.
     * <p>
     * The configured {@link GroupManagerFactory}'s are used to try to create the {@link GroupProvider}. The first non-null
     * instance is returned. The factories are used in non-deterministic order.
     */
    public GroupProvider recover(UUID id, Broker broker, Map<String, Object> attributes)
    {
        return createGroupProvider(id, broker, attributes);
    }

    public Collection<String> getSupportedGroupProviders()
    {
        return _supportedGroupProviders;
    }

    private GroupProviderAdapter createGroupProvider(UUID id, Broker broker, Map<String, Object> attributes)
    {
        for (GroupManagerFactory factory : _factories.values())
        {
            GroupManager manager = factory.createInstance(attributes);
            if (manager != null)
            {
                verifyGroupManager(manager, broker);
                return new GroupProviderAdapter(id, broker, manager, attributes,factory.getAttributeNames());
            }
        }
        throw new IllegalConfigurationException("No group provider factory found for configuration attributes " + attributes);
    }

    private void verifyGroupManager(GroupManager manager, Broker broker)
    {
        Collection<GroupProvider> groupProviders = broker.getGroupProviders();
        for (GroupProvider groupProvider : groupProviders)
        {
            if (groupProvider instanceof GroupProviderAdapter)
            {
                GroupManager providerManager = ((GroupProviderAdapter) groupProvider).getGroupManager();
                if (manager.equals(providerManager))
                {
                    throw new IllegalConfigurationException("A group provider with the same settings already exists");
                }
            }
        }
    }
}
