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
import java.util.UUID;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ExternalFileBasedAuthenticationManager;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PlainPasswordDatabaseAuthenticationManager;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class AuthenticationProviderRestTest extends QpidRestTestCase
{

    public void testGet() throws Exception
    {
        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("authenticationprovider");
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());
        for (Map<String, Object> provider : providerDetails)
        {
            boolean managesPrincipals = true;
            String type = PlainPasswordDatabaseAuthenticationManager.PROVIDER_TYPE;
            assertProvider(managesPrincipals, type , provider);
            Map<String, Object> data = getRestTestHelper().getJsonAsSingletonList("authenticationprovider/"
                    + provider.get(AuthenticationProvider.NAME));
            assertNotNull("Cannot load data for " + provider.get(AuthenticationProvider.NAME), data);
            assertProvider(managesPrincipals, type, data);
        }
    }

    public void testPutCreateSecondPlainPrincipalDatabaseProviderSucceeds() throws Exception
    {
        File principalDatabase = getRestTestHelper().createTemporaryPasswdFile(new String[]{"admin2", "guest2", "test2"});

        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, PlainPasswordDatabaseAuthenticationManager.PROVIDER_TYPE);
        attributes.put(ExternalFileBasedAuthenticationManager.PATH, principalDatabase.getAbsolutePath());

        int responseCode = getRestTestHelper().submitRequest("authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("failed to create authentication provider", 201, responseCode);
    }

    public void testPutCreateNewAnonymousProvider() throws Exception
    {
        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManager.PROVIDER_TYPE);

        int responseCode = getRestTestHelper().submitRequest("authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Unexpected response code", 201, responseCode);

        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());
        Map<String, Object> provider = providerDetails.get(0);
        assertProvider(false, AnonymousAuthenticationManager.PROVIDER_TYPE, provider);
    }

    public void testUpdateAuthenticationProviderIdFails() throws Exception
    {
        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManager.PROVIDER_TYPE);

        int responseCode = getRestTestHelper().submitRequest("authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Unexpected response code", 201, responseCode);

        attributes.put(AuthenticationProvider.ID, UUID.randomUUID());

        responseCode = getRestTestHelper().submitRequest("authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Update with new ID should fail", 409, responseCode);
    }

    public void testDeleteOfUsedAuthenticationProviderFails() throws Exception
    {
        // create provider
        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManager.PROVIDER_TYPE);

        int responseCode = getRestTestHelper().submitRequest("authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Unexpected response code for provider creation", 201, responseCode);

        // create port
        String portName = "test-port";
        Map<String, Object> portAttributes = new HashMap<String, Object>();
        portAttributes.put(Port.NAME, portName);
        portAttributes.put(Port.AUTHENTICATION_PROVIDER, providerName);
        portAttributes.put(Port.PORT, findFreePort());

        responseCode = getRestTestHelper().submitRequest("port/" + portName, "PUT", portAttributes);
        assertEquals("Unexpected response code for port creation", 201, responseCode);

        responseCode = getRestTestHelper().submitRequest("authenticationprovider/" + providerName , "DELETE");
        assertEquals("Unexpected response code for provider deletion", 409, responseCode);

        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());
        assertProvider(false, AnonymousAuthenticationManager.PROVIDER_TYPE, providerDetails.get(0));
    }

    public void testDeleteOfUnusedAuthenticationProvider() throws Exception
    {
        // create provider
        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManager.PROVIDER_TYPE);

        int responseCode = getRestTestHelper().submitRequest("authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Unexpected response code for provider creation", 201, responseCode);

        responseCode = getRestTestHelper().submitRequest("authenticationprovider/" + providerName , "DELETE");
        assertEquals("Unexpected response code for provider deletion", 200, responseCode);

        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 0, providerDetails.size());
    }

    public void testRemovalOfAuthenticationProviderInErrorStateUsingManagementMode() throws Exception
    {
        stopBroker();

        File file = new File(TMP_FOLDER, getTestName());
        if (file.exists())
        {
            file.delete();
        }
        assertFalse("Group file should not exist", file.exists());

        TestBrokerConfiguration config = getBrokerConfiguration();

        String providerName = getTestName();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.TYPE, PlainPasswordDatabaseAuthenticationManager.PROVIDER_TYPE);
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(ExternalFileBasedAuthenticationManager.PATH, file.getAbsoluteFile());

        UUID id = config.addObjectConfiguration(AuthenticationProvider.class, attributes);
        config.setSaved(false);
        startBroker(0, true);

        getRestTestHelper().setUsernameAndPassword(BrokerOptions.MANAGEMENT_MODE_USER_NAME, MANAGEMENT_MODE_PASSWORD);

        Map<String, Object> provider = getRestTestHelper().getJsonAsSingletonList("authenticationprovider/" + providerName);
        assertEquals("Unexpected id", id.toString(), provider.get(AuthenticationProvider.ID));
        assertEquals("Unexpected name", providerName, provider.get(AuthenticationProvider.NAME));
        assertEquals("Unexpected path", file.getAbsolutePath() , provider.get(ExternalFileBasedAuthenticationManager.PATH));
        assertEquals("Unexpected state", State.ERRORED.name() , provider.get(AuthenticationProvider.STATE));

        int status = getRestTestHelper().submitRequest("authenticationprovider/" + providerName, "DELETE");
        assertEquals("ACL was not deleted", 200, status);

        List<Map<String, Object>> providers = getRestTestHelper().getJsonAsList("authenticationprovider/" + providerName);
        assertEquals("Provider exists", 0, providers.size());
    }

    public void testUpdateOfAuthenticationProviderInErrorStateUsingManagementMode() throws Exception
    {
        stopBroker();

        File file = new File(TMP_FOLDER, getTestName());
        if (file.exists())
        {
            file.delete();
        }
        assertFalse("Group file should not exist", file.exists());

        TestBrokerConfiguration config = getBrokerConfiguration();

        String providerName = getTestName();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.TYPE, PlainPasswordDatabaseAuthenticationManager.PROVIDER_TYPE);
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(ExternalFileBasedAuthenticationManager.PATH, file.getAbsoluteFile());

        UUID id = config.addObjectConfiguration(AuthenticationProvider.class, attributes);
        config.setSaved(false);
        startBroker(0, true);

        getRestTestHelper().setUsernameAndPassword(BrokerOptions.MANAGEMENT_MODE_USER_NAME, MANAGEMENT_MODE_PASSWORD);

        Map<String, Object> provider = getRestTestHelper().getJsonAsSingletonList("authenticationprovider/" + providerName);
        assertEquals("Unexpected id", id.toString(), provider.get(AuthenticationProvider.ID));
        assertEquals("Unexpected name", providerName, provider.get(AuthenticationProvider.NAME));
        assertEquals("Unexpected path", file.getAbsolutePath() , provider.get(ExternalFileBasedAuthenticationManager.PATH));
        assertEquals("Unexpected state", State.ERRORED.name() , provider.get(AuthenticationProvider.STATE));

        File principalDatabase = null;
        try
        {
            principalDatabase = getRestTestHelper().createTemporaryPasswdFile(new String[]{"admin2", "guest2", "test2"});
            attributes = new HashMap<String, Object>();
            attributes.put(AuthenticationProvider.NAME, providerName);
            attributes.put(AuthenticationProvider.ID, id);
            attributes.put(AuthenticationProvider.TYPE, PlainPasswordDatabaseAuthenticationManager.PROVIDER_TYPE);
            attributes.put(ExternalFileBasedAuthenticationManager.PATH, principalDatabase.getAbsolutePath());

            int status = getRestTestHelper().submitRequest("authenticationprovider/" + providerName, "PUT", attributes);
            assertEquals("ACL was not deleted", 200, status);

            provider = getRestTestHelper().getJsonAsSingletonList("authenticationprovider/" + providerName);
            assertEquals("Unexpected id", id.toString(), provider.get(AuthenticationProvider.ID));
            assertEquals("Unexpected name", providerName, provider.get(AuthenticationProvider.NAME));
            assertEquals("Unexpected path", principalDatabase.getAbsolutePath() , provider.get(
                    ExternalFileBasedAuthenticationManager.PATH));
            assertEquals("Unexpected state", State.ACTIVE.name() , provider.get(AuthenticationProvider.STATE));
        }
        finally
        {
            if (principalDatabase != null)
            {
                principalDatabase.delete();
            }
        }
    }

    private void assertProvider(boolean managesPrincipals, String type, Map<String, Object> provider)
    {
        Asserts.assertAttributesPresent(provider, BrokerModel.getInstance().getTypeRegistry().getAttributeNames(
                                                AuthenticationProvider.class),
                AuthenticationProvider.DESCRIPTION, ConfiguredObject.CONTEXT,
                ConfiguredObject.DESIRED_STATE, ConfiguredObject.CREATED_BY,
                ConfiguredObject.CREATED_TIME, ConfiguredObject.LAST_UPDATED_BY, ConfiguredObject.LAST_UPDATED_TIME);
        assertEquals("Unexpected value of provider attribute " + AuthenticationProvider.STATE, State.ACTIVE.name(),
                provider.get(AuthenticationProvider.STATE));
        assertEquals("Unexpected value of provider attribute " + AuthenticationProvider.LIFETIME_POLICY,
                LifetimePolicy.PERMANENT.name(), provider.get(AuthenticationProvider.LIFETIME_POLICY));
        assertEquals("Unexpected value of provider attribute " + AuthenticationProvider.DURABLE, Boolean.TRUE,
                provider.get(AuthenticationProvider.DURABLE));
        assertEquals("Unexpected value of provider attribute " + AuthenticationProvider.TYPE, type,
                provider.get(AuthenticationProvider.TYPE));

        if (managesPrincipals)
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
