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

package org.apache.qpid.systest.rest.acl;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.adapter.FileSystemPreferencesProvider;
import org.apache.qpid.server.security.acl.AbstractACLTestCase;
import org.apache.qpid.systest.rest.QpidRestTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.test.utils.TestFileUtils;

public class UserPreferencesRestACLTest extends QpidRestTestCase
{

    private static final String REST_USER_PREFERENCES_BASE_URL = "/service/userpreferences";
    private static final String ALLOWED_USER = "webadmin";
    private static final String DENIED_USER = "admin";
    private static final String TEST_USER_PREFERENCES_GET_URL = REST_USER_PREFERENCES_BASE_URL + "/"
            + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/%s";

    private File _preferencesProviderFile;

    public void setUp() throws Exception
    {
        _preferencesProviderFile = TestFileUtils.createTempFile(this, ".prefs.json",
                "{\"webadmin\":{\"language\": \"en\", \"saveTabs\":true}," + " \"admin\":{\"language\": \"fr\", \"saveTabs\":false}"
                        + "}");
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
        getRestTestHelper().configureTemporaryPasswordFile(this, ALLOWED_USER, DENIED_USER);

        AbstractACLTestCase.writeACLFileUtil(this, "ACL ALLOW-LOG ALL ACCESS MANAGEMENT", "ACL ALLOW-LOG " + ALLOWED_USER
                + " UPDATE USER", "ACL DENY-LOG " + DENIED_USER + " UPDATE USER", "ACL DENY-LOG ALL ALL");

        TestBrokerConfiguration brokerConfiguration = getBrokerConfiguration();
        brokerConfiguration.setObjectAttribute(Plugin.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT,
                HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, true);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(PreferencesProvider.NAME, "test");
        attributes.put(PreferencesProvider.TYPE, FileSystemPreferencesProvider.PROVIDER_TYPE);
        attributes.put(FileSystemPreferencesProvider.PATH, _preferencesProviderFile.getAbsolutePath());
        brokerConfiguration
                .addPreferencesProviderConfiguration(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER, attributes);
    }

    public void testListUsersWithPreferencesAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        List<Map<String, Object>> users = getRestTestHelper().getJsonAsList(REST_USER_PREFERENCES_BASE_URL);
        assertUsers(users);

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        users = getRestTestHelper().getJsonAsList(REST_USER_PREFERENCES_BASE_URL);
        assertUsers(users);
    }

    public void testViewOtherUserPreferencesAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String userPreferencesUrl = String.format(TEST_USER_PREFERENCES_GET_URL, DENIED_USER);
        Map<String, Object> preferences = getRestTestHelper().getJsonAsMap(userPreferencesUrl);
        assertEquals("Unexpected number of preferences", 2, preferences.size());
        assertEquals("Unexpected language preference", "fr", preferences.get("language"));
        assertEquals("Unexpected saveTabs preference", false, preferences.get("saveTabs"));
    }

    public void testViewOtherUserPreferencesDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        int responseCode = getRestTestHelper().submitRequest(
                "/service/userpreferences?user="
                        + URLEncoder.encode(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/" + ALLOWED_USER, "UTF-8"),
                "DELETE");
        assertEquals("Preferences deletion should be denied", 403, responseCode);
    }

    public void testDeleteOtherUserPreferencesAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String userPreferencesUrl = String.format(TEST_USER_PREFERENCES_GET_URL, DENIED_USER);
        Map<String, Object> preferences = getRestTestHelper().getJsonAsMap(userPreferencesUrl);
        assertEquals("Unexpected number of preferences", 2, preferences.size());
        assertEquals("Unexpected language preference", "fr", preferences.get("language"));
        assertEquals("Unexpected saveTabs preference", false, preferences.get("saveTabs"));

        int responseCode = getRestTestHelper().submitRequest(
                "/service/userpreferences?user="
                        + URLEncoder.encode(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/" + DENIED_USER, "UTF-8"),
                "DELETE");
        assertEquals("Preferences deletion should be allowed", 200, responseCode);

        preferences = getRestTestHelper().getJsonAsMap(userPreferencesUrl);
        assertEquals("Unexpected number of preferences after deletion", 0, preferences.size());
    }

    public void testDeleteOtherUserPreferencesDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String userPreferencesUrl = String.format(TEST_USER_PREFERENCES_GET_URL, ALLOWED_USER);
        Map<String, Object> preferences = getRestTestHelper().getJsonAsMap(userPreferencesUrl);
        assertEquals("Unexpected number of preferences", 2, preferences.size());
        assertEquals("Unexpected language preference", "en", preferences.get("language"));
        assertEquals("Unexpected saveTabs preference", true, preferences.get("saveTabs"));

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        int responseCode = getRestTestHelper().submitRequest(
                "/service/userpreferences?user="
                        + URLEncoder.encode(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/" + ALLOWED_USER, "UTF-8"),
                "DELETE");
        assertEquals("Preferences deletion should be denied", 403, responseCode);

        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        preferences = getRestTestHelper().getJsonAsMap(userPreferencesUrl);
        assertEquals("Unexpected number of preferences after deletion", 2, preferences.size());
    }


    private void assertUsers(List<Map<String, Object>> users)
    {
        assertEquals("Unexpected number of users", 2, users.size());
        Map<String, Object> webadmin = findUser("webadmin", users);
        assertEquals("Unexpected name", "webadmin", webadmin.get("name"));
        assertEquals("Unexpected authentication provider", TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER, webadmin.get("authenticationProvider"));
        Map<String, Object> admin = findUser("admin", users);
        assertEquals("Unexpected name", "admin", admin.get("name"));
        assertEquals("Unexpected authentication provider", TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER, admin.get("authenticationProvider"));
    }

    private Map<String, Object> findUser(String name, List<Map<String, Object>> users)
    {
        for (Map<String, Object> user : users)
        {
            if (name.equals(user.get("name")))
            {
                return user;
            }
        }
        return null;
    }
}
