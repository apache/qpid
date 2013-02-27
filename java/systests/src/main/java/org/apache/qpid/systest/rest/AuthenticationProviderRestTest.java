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
package org.apache.qpid.systest.rest;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManagerFactory;
import org.apache.qpid.server.security.auth.manager.PlainPasswordFileAuthenticationManagerFactory;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class AuthenticationProviderRestTest extends QpidRestTestCase
{

    private static final String PRINCIPAL_DATABASE_AUTHENTICATION_MANAGER = "PrincipalDatabaseAuthenticationManager";
    private static final String AUTHENTICATION_MANAGER = "AuthenticationManager";

    public void testGet() throws Exception
    {
        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("/rest/authenticationprovider");
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());
        for (Map<String, Object> provider : providerDetails)
        {
            assertProvider(PRINCIPAL_DATABASE_AUTHENTICATION_MANAGER, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE, provider);
            Map<String, Object> data = getRestTestHelper().getJsonAsSingletonList("/rest/authenticationprovider/"
                    + provider.get(AuthenticationProvider.NAME));
            assertNotNull("Cannot load data for " + provider.get(AuthenticationProvider.NAME), data);
            assertProvider(PRINCIPAL_DATABASE_AUTHENTICATION_MANAGER, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE, data);
        }
    }

    public void testPutCreateNewPlainPrincipalDatabaseProvider() throws Exception
    {
        File principalDatabase = getRestTestHelper().createTemporaryPasswdFile(new String[]{"admin2", "guest2", "test2"});

        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);
        attributes.put(PlainPasswordFileAuthenticationManagerFactory.ATTRIBUTE_PATH, principalDatabase.getAbsolutePath());

        int responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Unexpected response code", 201, responseCode);

        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("/rest/authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());
        Map<String, Object> provider = providerDetails.get(0);
        assertProvider(PRINCIPAL_DATABASE_AUTHENTICATION_MANAGER, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE, provider);

        // provider should exists after broker restart
        restartBroker();
        providerDetails = getRestTestHelper().getJsonAsList("/rest/authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());
    }

    public void testPutCreateNewAnonymousProvider() throws Exception
    {
        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE);

        int responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Unexpected response code", 201, responseCode);

        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("/rest/authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());
        Map<String, Object> provider = providerDetails.get(0);
        assertProvider(AUTHENTICATION_MANAGER, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE, provider);
    }

    public void testDeleteOfDefaultAuthenticationProviderFails() throws Exception
    {
        String providerName = TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER;

        int responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName , "DELETE", null);
        assertEquals("Unexpected response code", 409, responseCode);

        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("/rest/authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());
        assertProvider(PRINCIPAL_DATABASE_AUTHENTICATION_MANAGER, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE, providerDetails.get(0));
    }

    public void testDeleteOfUsedAuthenticationProviderFails() throws Exception
    {
        // create provider
        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE);

        int responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Unexpected response code for provider creation", 201, responseCode);

        // create port
        String portName = "test-port";
        Map<String, Object> portAttributes = new HashMap<String, Object>();
        portAttributes.put(Port.NAME, portName);
        portAttributes.put(Port.AUTHENTICATION_MANAGER, providerName);
        portAttributes.put(Port.PORT, findFreePort());

        responseCode = getRestTestHelper().submitRequest("/rest/port/" + portName, "PUT", portAttributes);
        assertEquals("Unexpected response code for port creation", 201, responseCode);

        responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName , "DELETE", null);
        assertEquals("Unexpected response code for provider deletion", 409, responseCode);

        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("/rest/authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());
        assertProvider(AUTHENTICATION_MANAGER, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE, providerDetails.get(0));
    }

    public void testDeleteOfUnusedAuthenticationProvider() throws Exception
    {
        // create provider
        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE);

        int responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Unexpected response code for provider creation", 201, responseCode);

        responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName , "DELETE", null);
        assertEquals("Unexpected response code for provider deletion", 200, responseCode);

        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("/rest/authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 0, providerDetails.size());
    }

    public void testPutUpdateAuthenticationProvider() throws Exception
    {
        File principalDatabase = getRestTestHelper().createTemporaryPasswdFile(new String[]{"admin2", "guest2", "test2"});

        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);
        attributes.put(PlainPasswordFileAuthenticationManagerFactory.ATTRIBUTE_PATH, principalDatabase.getAbsolutePath());

        int responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Unexpected response code", 201, responseCode);

        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("/rest/authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());

        String[] users = new String[]{"admin3", "guest3", "test3"};
        File principalDatabase2 = getRestTestHelper().createTemporaryPasswdFile(users);

        Map<String, Object> newAttributes = new HashMap<String, Object>();
        newAttributes.put(AuthenticationProvider.NAME, providerName);
        newAttributes.put(AuthenticationProvider.TYPE, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);
        newAttributes.put(PlainPasswordFileAuthenticationManagerFactory.ATTRIBUTE_PATH, principalDatabase2.getAbsolutePath());

        responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", newAttributes);
        assertEquals("Unexpected response code", 200, responseCode);

        providerDetails = getRestTestHelper().getJsonAsList("/rest/authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());

        List<Map<String, Object>> userData = getRestTestHelper().getJsonAsList("/rest/user/" + providerName);
        assertEquals(3, userData.size());
        for (int i = 0; i < users.length; i++)
        {
            boolean userFound = false;
            for (Map<String, Object> userEntry : userData)
            {
                String name = (String)userEntry.get(User.NAME);
                if (users[i].equals(name))
                {
                    userFound = true;
                    break;
                }
            }
            assertTrue("User " + users[i] + " is not found", userFound);
        }
    }

    public void testPutUpdateAuthenticationProviderToDifferrentCategoryFails() throws Exception
    {
        File principalDatabase = getRestTestHelper().createTemporaryPasswdFile(new String[]{"admin2", "guest2", "test2"});

        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);
        attributes.put(PlainPasswordFileAuthenticationManagerFactory.ATTRIBUTE_PATH, principalDatabase.getAbsolutePath());

        int responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Unexpected response code", 201, responseCode);

        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("/rest/authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());

        Map<String, Object> newAttributes = new HashMap<String, Object>();
        newAttributes.put(AuthenticationProvider.NAME, providerName);
        newAttributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE);

        responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", newAttributes);
        assertEquals("Unexpected response code", 409, responseCode);
    }

    private void assertProvider(String category, String subType, Map<String, Object> provider)
    {
        Asserts.assertAttributesPresent(provider, AuthenticationProvider.AVAILABLE_ATTRIBUTES,
                AuthenticationProvider.CREATED, AuthenticationProvider.UPDATED, AuthenticationProvider.DESCRIPTION,
                AuthenticationProvider.TIME_TO_LIVE);
        assertEquals("Unexpected value of provider attribute " + AuthenticationProvider.STATE, State.ACTIVE.name(),
                provider.get(AuthenticationProvider.STATE));
        assertEquals("Unexpected value of provider attribute " + AuthenticationProvider.LIFETIME_POLICY,
                LifetimePolicy.PERMANENT.name(), provider.get(AuthenticationProvider.LIFETIME_POLICY));
        assertEquals("Unexpected value of provider attribute " + AuthenticationProvider.DURABLE, Boolean.TRUE,
                provider.get(AuthenticationProvider.DURABLE));
        assertEquals("Unexpected value of provider attribute " + AuthenticationProvider.CATEGORY, category,
                provider.get(AuthenticationProvider.CATEGORY));
        assertEquals("Unexpected value of provider attribute " + AuthenticationProvider.TYPE, subType,
                provider.get(AuthenticationProvider.TYPE));

        if (PRINCIPAL_DATABASE_AUTHENTICATION_MANAGER.equals(category))
        {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> users = (List<Map<String, Object>>) provider.get("users");
            assertNotNull("Users are not found", users);
            assertTrue("Unexpected number of users", users.size() > 1);
            for (Map<String, Object> user : users)
            {
                assertNotNull("Attribute " + User.ID, user.get(User.ID));
                assertNotNull("Attribute " + User.NAME, user.get(User.NAME));
            }
        }
    }
}
