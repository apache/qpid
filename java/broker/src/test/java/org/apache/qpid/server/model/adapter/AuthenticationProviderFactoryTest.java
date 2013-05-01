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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import junit.framework.TestCase;

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
        AuthenticationManager am = mock(PrincipalDatabaseAuthenticationManager.class);
        AuthenticationProvider provider = testForFactory(am, true);
        assertTrue("The created provider should match the factory's AuthenticationManager type",
                provider instanceof PasswordCredentialManagingAuthenticationProvider);
        verify(am).onCreate();
    }

    public void testCreateNonPasswordCredentialManagingAuthenticationProvider()
    {
        AuthenticationManager am = mock(AuthenticationManager.class);
        AuthenticationProvider provider = testForFactory(am, true);
        assertFalse("The created provider should match the factory's AuthenticationManager type",
                provider instanceof PasswordCredentialManagingAuthenticationProvider);
        verify(am).onCreate();
    }

    public void testRecoverPasswordCredentialManagingAuthenticationProvider()
    {
        AuthenticationManager am = mock(PrincipalDatabaseAuthenticationManager.class);
        AuthenticationProvider provider = testForFactory(am, false);
        assertTrue("The created provider should match the factory's AuthenticationManager type",
                provider instanceof PasswordCredentialManagingAuthenticationProvider);
        verify(am, never()).onCreate();
    }

    public void testRecoverNonPasswordCredentialManagingAuthenticationProvider()
    {
        AuthenticationManager am = mock(AuthenticationManager.class);
        AuthenticationProvider provider = testForFactory(am, false);
        assertFalse("The created provider should match the factory's AuthenticationManager type",
                provider instanceof PasswordCredentialManagingAuthenticationProvider);
        verify(am, never()).onCreate();
    }

    @SuppressWarnings("unchecked")
    private AuthenticationProvider testForFactory(AuthenticationManager authenticationManager, boolean create)
    {
        UUID id = UUID.randomUUID();
        Map<String, Object> attributes = new HashMap<String, Object>();

        QpidServiceLoader<AuthenticationManagerFactory> authManagerFactoryServiceLoader = mock(QpidServiceLoader.class);
        AuthenticationManagerFactory authenticationManagerFactory = mock(AuthenticationManagerFactory.class);

        Broker broker = mock(Broker.class);

        when(authManagerFactoryServiceLoader.atLeastOneInstanceOf(AuthenticationManagerFactory.class)).thenReturn(
                Collections.singleton(authenticationManagerFactory));
        when(authenticationManagerFactory.createInstance(attributes)).thenReturn(authenticationManager);

        AuthenticationProviderFactory providerFactory = new AuthenticationProviderFactory(authManagerFactoryServiceLoader);

        AuthenticationProvider provider = null;
        if (create)
        {
            provider = providerFactory.create(id, broker, attributes);
        }
        else
        {
            provider = providerFactory.recover(id, attributes, broker);
        }

        assertNotNull("Provider is not created", provider);
        assertEquals("Unexpected ID", id, provider.getId());

        return provider;
    }

    public void testCreatePasswordCredentialManagingAuthenticationProviderFailsWhenAnotherOneAlready()
    {
        Broker broker = mock(Broker.class);
        PasswordCredentialManagingAuthenticationProvider anotherProvider = mock(PasswordCredentialManagingAuthenticationProvider.class);
        when(broker.getAuthenticationProviders()).thenReturn(Collections.<AuthenticationProvider>singleton(anotherProvider));

        QpidServiceLoader<AuthenticationManagerFactory> loader = mock(QpidServiceLoader.class);
        AuthenticationManagerFactory managerFactory = mock(AuthenticationManagerFactory.class);
        when(managerFactory.createInstance(any(Map.class))).thenReturn(mock(PrincipalDatabaseAuthenticationManager.class));
        when(loader.atLeastOneInstanceOf(AuthenticationManagerFactory.class)).thenReturn(Collections.singleton(managerFactory));

        AuthenticationProviderFactory providerFactory = new AuthenticationProviderFactory(loader);

        UUID randomUUID = UUID.randomUUID();
        AuthenticationProvider provider = providerFactory.create(randomUUID, broker, new HashMap<String, Object>());

        assertNotNull("Provider is not created", provider);
        assertEquals("Unexpected ID", randomUUID, provider.getId());
    }

    @SuppressWarnings("unchecked")
    public void testCreateNonPasswordCredentialManagingAuthenticationProviderWhenAnotherOneAlreadyExist()
    {
        Broker broker = mock(Broker.class);
        AuthenticationProvider anotherProvider = mock(AuthenticationProvider.class);
        when(broker.getAuthenticationProviders()).thenReturn(Collections.singleton(anotherProvider));

        QpidServiceLoader<AuthenticationManagerFactory> loader = mock(QpidServiceLoader.class);
        AuthenticationManagerFactory managerFactory = mock(AuthenticationManagerFactory.class);
        when(managerFactory.createInstance(any(Map.class))).thenReturn(mock(AuthenticationManager.class));
        when(loader.atLeastOneInstanceOf(AuthenticationManagerFactory.class)).thenReturn(Collections.singleton(managerFactory));

        AuthenticationProviderFactory providerFactory = new AuthenticationProviderFactory(loader);
        UUID id = UUID.randomUUID();
        AuthenticationProvider provider = providerFactory.create(id, broker, new HashMap<String, Object>());

        assertNotNull("Provider is not created", provider);
        assertEquals("Unexpected ID", id, provider.getId());
    }
}
