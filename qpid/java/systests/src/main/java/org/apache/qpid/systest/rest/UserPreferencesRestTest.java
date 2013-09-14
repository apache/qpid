package org.apache.qpid.systest.rest;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
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
    protected void customizeConfiguration() throws ConfigurationException, IOException
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
                "/rest/userpreferences/" + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/webadmin");
        assertEquals("Unexpected number of preferences", 2, preferences.size());
        assertEquals("Unexpected language preference", "en", preferences.get("language"));
        assertEquals("Unexpected saveTabs preference", true, preferences.get("saveTabs"));
    }

    public void testGetUserListForAuthenticationProvider() throws Exception
    {
        List<Map<String, Object>> users = getRestTestHelper().getJsonAsList(
                "/rest/userpreferences/" + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER);
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
        List<Map<String, Object>> users = getRestTestHelper().getJsonAsList("/rest/userpreferences");
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
                "/rest/userpreferences?user="
                        + URLEncoder.encode(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/webadmin",
                                "UTF-8"), "DELETE", null);
        assertEquals("Unexpected status ", 200, status);
        List<Map<String, Object>> users = getRestTestHelper().getJsonAsList("/rest/userpreferences");
        assertEquals("Unexpected number of users", 1, users.size());
        Map<String, Object> user = findUser("admin", users);
        assertNotNull("User admin is not found", user);
        assertNull("User webadmin is found", findUser("webadmin", users));
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
