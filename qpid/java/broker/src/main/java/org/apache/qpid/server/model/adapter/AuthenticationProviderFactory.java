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

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.model.adapter.AuthenticationProviderAdapter.PrincipalDatabaseAuthenticationManagerAdapter;
import org.apache.qpid.server.model.adapter.AuthenticationProviderAdapter.SimpleAuthenticationProviderAdapter;

public class AuthenticationProviderFactory
{
    private final Iterable<AuthenticationManagerFactory> _factories;
    private Collection<String> _supportedAuthenticationProviders;

    public AuthenticationProviderFactory(QpidServiceLoader<AuthenticationManagerFactory> authManagerFactoryServiceLoader)
    {
        _factories = authManagerFactoryServiceLoader.atLeastOneInstanceOf(AuthenticationManagerFactory.class);
        List<String> supportedAuthenticationProviders = new ArrayList<String>();
        for (AuthenticationManagerFactory factory : _factories)
        {
            supportedAuthenticationProviders.add(factory.getType());
        }
        _supportedAuthenticationProviders = Collections.unmodifiableCollection(supportedAuthenticationProviders);
    }

    /**
     * Creates {@link AuthenticationProvider} for given ID, {@link Broker} and attributes.
     * <p>
     * The configured {@link AuthenticationManagerFactory}'s are used to try to create the {@link AuthenticationProvider}.
     * The first non-null instance is returned. The factories are used in non-deterministic order.
     */
    public AuthenticationProvider create(UUID id, Broker broker, Map<String, Object> attributes)
    {
        for (AuthenticationManagerFactory factory : _factories)
        {
            AuthenticationManager manager = factory.createInstance(attributes);
            if (manager != null)
            {
                AuthenticationProviderAdapter<?> authenticationProvider;
                if (manager instanceof PrincipalDatabaseAuthenticationManager)
                {
                    // a temporary restriction to prevent creation of several instances
                    // of PlainPasswordFileAuthenticationProvider/Base64MD5PasswordFileAuthenticationProvider
                    // due to current limitation of JMX management which cannot cope
                    // with several user management MBeans as MBean type is used as a name.

                    // TODO: Remove this check after fixing of JMX management
                    for (AuthenticationProvider provider : broker.getAuthenticationProviders())
                    {
                        if (provider instanceof PasswordCredentialManagingAuthenticationProvider)
                        {
                            throw new IllegalConfigurationException("An authentication provider which can manage users alredy exists ["
                                    + provider.getName() + "]. Only one instance is allowed.");
                        }
                    }

                    authenticationProvider = new PrincipalDatabaseAuthenticationManagerAdapter(id, broker,
                            (PrincipalDatabaseAuthenticationManager) manager, attributes, factory.getAttributeNames());
                }
                else
                {
                    authenticationProvider = new SimpleAuthenticationProviderAdapter(id, broker, manager, attributes, factory.getAttributeNames());
                }
                return authenticationProvider;
            }
        }

        throw new IllegalArgumentException("No authentication provider factory found for configuration attributes " + attributes);
    }

    public Collection<String> getSupportedAuthenticationProviders()
    {
        return _supportedAuthenticationProviders;
    }
}
