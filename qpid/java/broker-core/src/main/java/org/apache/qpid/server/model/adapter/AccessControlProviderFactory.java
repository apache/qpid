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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.AccessControlProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.plugin.AccessControlFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.security.AccessControl;

public class AccessControlProviderFactory
{
    private final Iterable<AccessControlFactory> _factories;
    private Collection<String> _supportedAccessControlProviders;

    public AccessControlProviderFactory(QpidServiceLoader<AccessControlFactory> accessControlFactoryServiceLoader)
    {
        _factories = accessControlFactoryServiceLoader.instancesOf(AccessControlFactory.class);
        List<String> supportedAccessControlProviders = new ArrayList<String>();
        for (AccessControlFactory factory : _factories)
        {
            supportedAccessControlProviders.add(factory.getType());
        }
        _supportedAccessControlProviders = Collections.unmodifiableCollection(supportedAccessControlProviders);
    }

    /**
     * Creates {@link AccessControlProvider} for given ID, {@link Broker} and attributes.
     * <p>
     * The configured {@link AccessControlFactory}'s are used to try to create the {@link AccessControlProvider}.
     * The first non-null instance is returned. The factories are used in non-deterministic order.
     */
    public AccessControlProvider create(UUID id, Broker broker, Map<String, Object> attributes)
    {
        AccessControlProvider ac = createAccessControlProvider(id, broker, attributes);
        ac.getAccessControl().onCreate();

        return ac;
    }

    public AccessControlProvider recover(UUID id, Broker broker, Map<String, Object> attributes)
    {
        return createAccessControlProvider(id, broker, attributes);
    }

    private AccessControlProvider createAccessControlProvider(UUID id,
            Broker broker, Map<String, Object> attributes)
    {
        for (AccessControlFactory factory : _factories)
        {
            AccessControl accessControl = factory.createInstance(attributes);
            if (accessControl != null)
            {
                return new AccessControlProviderAdapter(id, broker,accessControl, attributes, factory.getAttributeNames());
            }
        }

        throw new IllegalArgumentException("No access control provider factory found for configuration attributes " + attributes);
    }

    public Collection<String> getSupportedAuthenticationProviders()
    {
        return _supportedAccessControlProviders;
    }
}
