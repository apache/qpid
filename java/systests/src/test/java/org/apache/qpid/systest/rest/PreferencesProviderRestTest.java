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
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ExternalFileBasedAuthenticationManager;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.adapter.FileSystemPreferencesProvider;
import org.apache.qpid.server.security.auth.manager.PlainPasswordDatabaseAuthenticationManager;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.test.utils.TestFileUtils;

public class PreferencesProviderRestTest extends QpidRestTestCase
{
    private Map<String, File> _preferencesProviderFiles;
    private File _authenticationProviderFile;

    public void setUp() throws Exception
    {
        _authenticationProviderFile = TestFileUtils.createTempFile(this, ".test.prefs.txt", "test:test");
        _preferencesProviderFiles = new HashMap<String, File>();
        super.setUp();
    }

    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            if (_authenticationProviderFile != null)
            {
                _authenticationProviderFile.delete();
            }
            for (File file : _preferencesProviderFiles.values())
            {
                file.delete();
            }
        }
    }

    @Override
    protected void customizeConfiguration() throws IOException
    {
        super.customizeConfiguration();
        Map<String, Object> anonymousAuthProviderAttributes = new HashMap<String, Object>();
        anonymousAuthProviderAttributes.put(AuthenticationProvider.TYPE, PlainPasswordDatabaseAuthenticationManager.PROVIDER_TYPE);
        anonymousAuthProviderAttributes.put(AuthenticationProvider.NAME,  TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "-2");
        anonymousAuthProviderAttributes.put(ExternalFileBasedAuthenticationManager.PATH, _authenticationProviderFile.getAbsolutePath());
        getBrokerConfiguration().addObjectConfiguration(AuthenticationProvider.class,anonymousAuthProviderAttributes);
    }

    public void testCreateAndGetProvider() throws Exception
    {
        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("preferencesprovider");
        assertEquals("Unexpected number of providers", 0, providerDetails.size());

        createPreferencesProvider(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER, "test1");
        createPreferencesProvider(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "-2", "test2");

        providerDetails = getRestTestHelper().getJsonAsList("preferencesprovider");
        assertEquals("Unexpected number of providers", 2, providerDetails.size());

        for (Map<String, Object> provider : providerDetails)
        {
            assertProvider(provider);
        }

        Map<String, Object> provider = getRestTestHelper().getJsonAsSingletonList(
                "preferencesprovider/" + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/test1");
        assertProvider(provider);
        assertEquals("Unexpected provider name ", "test1", provider.get(PreferencesProvider.NAME));

        Map<String, Object> provider2 = getRestTestHelper().getJsonAsSingletonList(
                "preferencesprovider/" + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "-2/test2");
        assertProvider(provider);
        assertEquals("Unexpected provider name ", "test2", provider2.get(PreferencesProvider.NAME));
    }

    public void testDeleteProvider() throws Exception
    {
        createPreferencesProvider(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER, "test1");
        String providerUrl = "preferencesprovider/" + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/test1";
        Map<String, Object> provider = getRestTestHelper().getJsonAsSingletonList(providerUrl);
        assertProvider(provider);
        assertEquals("Unexpected provider name ", "test1", provider.get(PreferencesProvider.NAME));

        int responseCode = getRestTestHelper().submitRequest(providerUrl, "DELETE");
        assertEquals("Failed to delete preferences provider", 200, responseCode);

        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList(providerUrl);
        assertEquals("Unexpected number of providers", 0, providerDetails.size());
    }

    public void testUpdateProvider() throws Exception
    {
        createPreferencesProvider(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER, "test1");
        String providerUrl = "preferencesprovider/" + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/test1";
        Map<String, Object> provider = getRestTestHelper().getJsonAsSingletonList(providerUrl);
        assertProvider(provider);
        assertEquals("Unexpected provider name ", "test1", provider.get(PreferencesProvider.NAME));

        File file = TestFileUtils.createTempFile(this, ".prefs.json", "{\"admin\":{\"something\": \"somethingValue\"}}");
        _preferencesProviderFiles.put("new-test1", file);
        Map<String, Object> newAttributes = new HashMap<String, Object>();
        newAttributes.put(FileSystemPreferencesProvider.PATH, file.getAbsolutePath());

        int responseCode = getRestTestHelper().submitRequest(providerUrl, "PUT", newAttributes);
        assertEquals("Failed to update preferences provider", 200, responseCode);

        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList(providerUrl);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());

        provider = providerDetails.get(0);
        assertProviderCommonAttributes(provider);
        String name = (String) provider.get(PreferencesProvider.NAME);
        assertEquals("Unexpected name", "test1", name);
        assertEquals("Unexpected path for provider " + name, (String) provider.get(FileSystemPreferencesProvider.PATH),
                file.getAbsolutePath());
    }

    private void assertProvider(Map<String, Object> provider)
    {
        assertProviderCommonAttributes(provider);

        String name = (String) provider.get(PreferencesProvider.NAME);
        assertNotNull("Name cannot be null", name);
        assertEquals("Unexpected path for provider " + name, (String) provider.get(FileSystemPreferencesProvider.PATH),
                _preferencesProviderFiles.get(name).getAbsolutePath());
    }

    public void assertProviderCommonAttributes(Map<String, Object> provider)
    {
        Asserts.assertAttributesPresent(provider,
                                        BrokerModel.getInstance().getTypeRegistry().getAttributeNames(
                                                PreferencesProvider.class),
                                        ConfiguredObject.CREATED_BY,
                                        ConfiguredObject.CREATED_TIME,
                                        ConfiguredObject.LAST_UPDATED_BY,
                                        ConfiguredObject.LAST_UPDATED_TIME,
                                        ConfiguredObject.DESCRIPTION,
                                        ConfiguredObject.CONTEXT,
                                        ConfiguredObject.DESIRED_STATE);
        assertEquals("Unexpected value of provider attribute " + PreferencesProvider.STATE, State.ACTIVE.name(),
                provider.get(PreferencesProvider.STATE));
        assertEquals("Unexpected value of provider attribute " + PreferencesProvider.LIFETIME_POLICY,
                LifetimePolicy.PERMANENT.name(), provider.get(PreferencesProvider.LIFETIME_POLICY));
        assertEquals("Unexpected value of provider attribute " + PreferencesProvider.DURABLE, Boolean.TRUE,
                provider.get(PreferencesProvider.DURABLE));
        assertEquals("Unexpected value of provider attribute " + PreferencesProvider.TYPE,
                 FileSystemPreferencesProvider.PROVIDER_TYPE, provider.get(PreferencesProvider.TYPE));
    }

    private void createPreferencesProvider(String authenticationProvider, String providerName) throws Exception
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(PreferencesProvider.NAME, providerName);
        attributes.put(PreferencesProvider.TYPE, FileSystemPreferencesProvider.PROVIDER_TYPE);
        File file = TestFileUtils.createTempFile(this, ".prefs.json", "{\"admin\":{\"language\": \"en\"}}");
        _preferencesProviderFiles.put(providerName, file);
        attributes.put(FileSystemPreferencesProvider.PATH, file.getAbsolutePath());

        int responseCode = getRestTestHelper().submitRequest(
                "preferencesprovider/" + authenticationProvider + "/" + providerName, "PUT", attributes);
        assertEquals("Unexpected response code", 201, responseCode);
    }

}
