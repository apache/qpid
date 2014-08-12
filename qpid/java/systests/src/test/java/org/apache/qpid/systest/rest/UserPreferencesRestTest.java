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
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.model.adapter.FileSystemPreferencesProvider;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.test.utils.TestFileUtils;

public class UserPreferencesRestTest extends QpidRestTestCase
{
    private File _preferencesProviderFile;

    public void setUp() throws Exception
    {
        _preferencesProviderFile = TestFileUtils.createTempFile(this, ".prefs.json",
                "{\"webadmin\":{\"language\": \"en\", \"saveTabs\":true},"
              + " \"admin\":{\"language\": \"fr\", \"saveTabs\":false}" + "}");
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
            if (_preferencesProviderFile != null)
            {
                _preferencesProviderFile.delete();
            }
        }
    }

    @Override
    protected void customizeConfiguration() throws IOException
    {
        super.customizeConfiguration();

        TestBrokerConfiguration brokerConfiguration = getBrokerConfiguration();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(PreferencesProvider.NAME, "test");
        attributes.put(PreferencesProvider.TYPE, FileSystemPreferencesProvider.PROVIDER_TYPE);
        attributes.put(FileSystemPreferencesProvider.PATH, _preferencesProviderFile.getAbsolutePath());
        brokerConfiguration.addPreferencesProviderConfiguration(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER,
                attributes);

    }

    public void testGetUserPreferences() throws Exception
    {
        Map<String, Object> preferences = getRestTestHelper().getJsonAsMap(
                "/service/userpreferences/" + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/webadmin");
        assertEquals("Unexpected number of preferences", 2, preferences.size());
        assertEquals("Unexpected language preference", "en", preferences.get("language"));
        assertEquals("Unexpected saveTabs preference", true, preferences.get("saveTabs"));
    }

    public void testGetUserListForAuthenticationProvider() throws Exception
    {
        List<Map<String, Object>> users = getRestTestHelper().getJsonAsList(
                "/service/userpreferences/" + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER);
        assertEquals("Unexpected number of users", 2, users.size());
        String[] expectedUsers = { "webadmin", "admin" };
        for (int i = 0; i < expectedUsers.length; i++)
        {
            Map<String, Object> user = findUser(expectedUsers[i], users);
            assertNotNull(String.format("User %s is not found", expectedUsers[i]), user);
        }
    }

    public void testGetUserList() throws Exception
    {
        List<Map<String, Object>> users = getRestTestHelper().getJsonAsList("/service/userpreferences");
        assertEquals("Unexpected number of users", 2, users.size());
        String[] expectedUsers = { "webadmin", "admin" };
        for (int i = 0; i < expectedUsers.length; i++)
        {
            Map<String, Object> user = findUser(expectedUsers[i], users);
            assertNotNull(String.format("User %s is not found", expectedUsers[i]), user);
        }
    }

    public void testDeleteUser() throws Exception
    {
        int status = getRestTestHelper().submitRequest(
                "/service/userpreferences?user="
                        + URLEncoder.encode(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/webadmin",
                                "UTF-8"), "DELETE");
        assertEquals("Unexpected status ", 200, status);
        List<Map<String, Object>> users = getRestTestHelper().getJsonAsList("/service/userpreferences");
        assertEquals("Unexpected number of users", 1, users.size());
        Map<String, Object> user = findUser("admin", users);
        assertNotNull("User admin is not found", user);
        assertNull("User webadmin is found", findUser("webadmin", users));
    }

    public void testDeleteMultipleUser() throws Exception
    {
        int status = getRestTestHelper().submitRequest("/service/userpreferences?user="
                + URLEncoder.encode(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/webadmin", "UTF-8")
                + "&user=" + URLEncoder.encode(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/admin", "UTF-8"),
                "DELETE");
        assertEquals("Unexpected status ", 200, status);
        List<Map<String, Object>> users = getRestTestHelper().getJsonAsList("/service/userpreferences");
        assertEquals("Unexpected number of users", 0, users.size());
    }

    private Map<String, Object> findUser(String userName, List<Map<String, Object>> users)
    {
        for (Map<String, Object> map : users)
        {
            if (userName.equals(map.get(User.NAME)))
            {
                return map;
            }
        }
        return null;
    }

}
