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


import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.util.BrokerTestHelper;

public class SimpleLDAPAuthenticationManagerFactoryTest extends TestCase
{
    private ConfiguredObjectFactory _factory = BrokerModel.getInstance().getObjectFactory();
    private Map<String, Object> _configuration = new HashMap<String, Object>();
    private Broker _broker = BrokerTestHelper.createBrokerMock();

    private TrustStore _trustStore = mock(TrustStore.class);

    public void setUp() throws Exception
    {
        super.setUp();

        when(_trustStore.getName()).thenReturn("mytruststore");
        when(_trustStore.getId()).thenReturn(UUID.randomUUID());

        _configuration.put(AuthenticationProvider.ID, UUID.randomUUID());
        _configuration.put(AuthenticationProvider.NAME, getName());
    }

    public void testLdapCreated() throws Exception
    {
        _configuration.put(AuthenticationProvider.TYPE, SimpleLDAPAuthenticationManager.PROVIDER_TYPE);
        _configuration.put("providerUrl", "ldaps://example.com:636/");
        _configuration.put("searchContext", "dc=example");
        _configuration.put("searchFilter", "(uid={0})");
        _configuration.put("ldapContextFactory", TestLdapDirectoryContext.class.getName());

        _factory.create(AuthenticationProvider.class, _configuration, _broker);
    }

    public void testLdapsWhenTrustStoreNotFound() throws Exception
    {
        when(_broker.getChildren(eq(TrustStore.class))).thenReturn(Collections.singletonList(_trustStore));

        _configuration.put(AuthenticationProvider.TYPE, SimpleLDAPAuthenticationManager.PROVIDER_TYPE);
        _configuration.put("providerUrl", "ldaps://example.com:636/");
        _configuration.put("searchContext", "dc=example");
        _configuration.put("searchFilter", "(uid={0})");
        _configuration.put("trustStore", "notfound");

        try
        {
            _factory.create(AuthenticationProvider.class, _configuration, _broker);
            fail("Exception not thrown");
        }
        catch(IllegalArgumentException e)
        {
            // PASS
            assertTrue("Message does not include underlying issue ", e.getMessage().contains("name 'notfound'"));
            assertTrue("Message does not include the attribute name", e.getMessage().contains("trustStore"));
            assertTrue("Message does not include the expected type", e.getMessage().contains("TrustStore"));
        }
    }

}
