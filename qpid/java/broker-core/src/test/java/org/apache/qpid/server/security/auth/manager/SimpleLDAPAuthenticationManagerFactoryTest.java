/*
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
package org.apache.qpid.server.security.auth.manager;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.TrustStore;


import junit.framework.TestCase;

public class SimpleLDAPAuthenticationManagerFactoryTest extends TestCase
{
    private SimpleLDAPAuthenticationManagerFactory _factory = new SimpleLDAPAuthenticationManagerFactory();
    private Map<String, Object> _configuration = new HashMap<String, Object>();
    private Broker _broker = mock(Broker.class);
    private TrustStore _trustStore = mock(TrustStore.class);

    public void testLdapInstanceCreated() throws Exception
    {
        _configuration.put(SimpleLDAPAuthenticationManagerFactory.ATTRIBUTE_TYPE, SimpleLDAPAuthenticationManagerFactory.PROVIDER_TYPE);
        _configuration.put("providerUrl", "ldap://example.com:389/");
        _configuration.put("searchContext", "dc=example");

        AuthenticationManager manager = _factory.createInstance(_broker, _configuration);
        assertNotNull(manager);

        verifyZeroInteractions(_broker);
    }

    public void testLdapsInstanceCreated() throws Exception
    {
        _configuration.put(SimpleLDAPAuthenticationManagerFactory.ATTRIBUTE_TYPE, SimpleLDAPAuthenticationManagerFactory.PROVIDER_TYPE);
        _configuration.put("providerUrl", "ldaps://example.com:636/");
        _configuration.put("searchContext", "dc=example");

        AuthenticationManager manager = _factory.createInstance(_broker, _configuration);
        assertNotNull(manager);

        verifyZeroInteractions(_broker);
    }

    public void testLdapsWithTrustStoreInstanceCreated() throws Exception
    {
        when(_broker.findTrustStoreByName("mytruststore")).thenReturn(_trustStore);

        _configuration.put(SimpleLDAPAuthenticationManagerFactory.ATTRIBUTE_TYPE, SimpleLDAPAuthenticationManagerFactory.PROVIDER_TYPE);
        _configuration.put("providerUrl", "ldaps://example.com:636/");
        _configuration.put("searchContext", "dc=example");
        _configuration.put("trustStore", "mytruststore");

        AuthenticationManager manager = _factory.createInstance(_broker, _configuration);
        assertNotNull(manager);
    }

    public void testLdapsWhenTrustStoreNotFound() throws Exception
    {
        when(_broker.findTrustStoreByName("notfound")).thenReturn(null);

        _configuration.put(SimpleLDAPAuthenticationManagerFactory.ATTRIBUTE_TYPE, SimpleLDAPAuthenticationManagerFactory.PROVIDER_TYPE);
        _configuration.put("providerUrl", "ldaps://example.com:636/");
        _configuration.put("searchContext", "dc=example");
        _configuration.put("trustStore", "notfound");

        try
        {
            _factory.createInstance(_broker, _configuration);
            fail("Exception not thrown");
        }
        catch(IllegalConfigurationException e)
        {
            assertEquals("Can't find truststore with name 'notfound'", e.getMessage());
        }
    }

    public void testReturnsNullWhenNoConfig() throws Exception
    {
        AuthenticationManager manager = _factory.createInstance(_broker, _configuration);
        assertNull(manager);
    }
}
