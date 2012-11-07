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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;

public class AuthenticationProviderFactoryTest extends TestCase
{

    public void testCreatePasswordCredentialManagingAuthenticationProvider()
    {
        AuthenticationProvider provider = testForFactory(mock(PrincipalDatabaseAuthenticationManager.class));
        assertTrue("The created provider should match the factory's AuthenticationManager type",
                provider instanceof PasswordCredentialManagingAuthenticationProvider);
    }

    public void testCreateNonPasswordCredentialManagingAuthenticationProvider()
    {
        AuthenticationProvider provider = testForFactory(mock(AuthenticationManager.class));
        assertFalse("The created provider should match the factory's AuthenticationManager type",
                provider instanceof PasswordCredentialManagingAuthenticationProvider);
    }

    @SuppressWarnings("unchecked")
    private AuthenticationProvider testForFactory(AuthenticationManager authenticationManager)
    {
        UUID id = UUID.randomUUID();
        Map<String, Object> attributes = new HashMap<String, Object>();

        QpidServiceLoader<AuthenticationManagerFactory> authManagerFactoryServiceLoader = mock(QpidServiceLoader.class);
        AuthenticationManagerFactory authenticationManagerFactory = mock(AuthenticationManagerFactory.class);
        ConfigurationEntry configurationEntry = mock(ConfigurationEntry.class);

        when(configurationEntry.getId()).thenReturn(id);
        Broker broker = mock(Broker.class);

        when(authManagerFactoryServiceLoader.atLeastOneInstanceOf(AuthenticationManagerFactory.class)).thenReturn(
                Collections.singleton(authenticationManagerFactory));
        when(authenticationManagerFactory.createInstance(attributes)).thenReturn(authenticationManager);

        AuthenticationProviderFactory providerFactory = new AuthenticationProviderFactory(authManagerFactoryServiceLoader);
        AuthenticationProvider provider = providerFactory.create(id, broker, attributes, null);

        assertNotNull("Provider is not created", provider);
        assertEquals("Unexpected ID", id, provider.getId());

        return provider;
    }

}
