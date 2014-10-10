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

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;

public class FileSystemPreferencesProviderTest extends QpidTestCase
{
    private static final String TEST_PREFERENCES = "{\"user1\":{\"pref1\":\"pref1User1Value\", \"pref2\": true, \"pref3\": 1.0, \"pref4\": 2},"
            + "\"user2\":{\"pref1\":\"pref1User2Value\", \"pref2\": false, \"pref3\": 2.0, \"pref4\": 3}}";
    private FileSystemPreferencesProviderImpl _preferencesProvider;
    private AuthenticationProvider _authenticationProvider;
    private Broker _broker;
    private String _user1, _user2;
    private File _preferencesFile;
    private TaskExecutor _taskExecutor;

    protected void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _authenticationProvider = mock(AuthenticationProvider.class);
        _user1 = "user1";
        _user2 = "user2";
        _preferencesFile = TestFileUtils.createTempFile(this, ".prefs.json", TEST_PREFERENCES);

        _broker = BrokerTestHelper.createBrokerMock();
        _taskExecutor = new CurrentThreadTaskExecutor();
        _taskExecutor.start();
        when(_authenticationProvider.getTaskExecutor()).thenReturn(_taskExecutor);

        when(_authenticationProvider.getParent(Broker.class)).thenReturn(_broker);
        ConfiguredObjectFactory objectFactory = _broker.getObjectFactory();
        when(_authenticationProvider.getModel()).thenReturn(objectFactory.getModel());
        when(_authenticationProvider.getObjectFactory()).thenReturn(objectFactory);
        when(_authenticationProvider.getCategoryClass()).thenReturn(AuthenticationProvider.class);
    }

    protected void tearDown() throws Exception
    {
        try
        {
            if (_preferencesProvider != null)
            {
                _preferencesProvider.delete();
            }
            BrokerTestHelper.tearDown();
            _preferencesFile.delete();
            _taskExecutor.stopImmediately();
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testConstructionWithExistingFile()
    {
        _preferencesProvider = createPreferencesProvider();
        assertEquals(State.ACTIVE, _preferencesProvider.getState());
    }

    public void testConstructionWithNonExistingFile()
    {
        File nonExistingFile = new File(TMP_FOLDER, "preferences-" + getTestName() + ".json");
        assertFalse("Preferences file exists", nonExistingFile.exists());
        try
        {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(FileSystemPreferencesProvider.PATH, nonExistingFile.getAbsolutePath());
            attributes.put(ConfiguredObject.ID, UUID.randomUUID());
            attributes.put(ConfiguredObject.NAME, getTestName());
            _preferencesProvider = new FileSystemPreferencesProviderImpl(attributes, _authenticationProvider);
            _preferencesProvider.create();

            assertEquals(State.ACTIVE, _preferencesProvider.getState());
            assertTrue("Preferences file was not created", nonExistingFile.exists());
        }
        finally
        {
            nonExistingFile.delete();
        }
    }

    public void testValidationOnCreateForInvalidPath() throws Exception
    {
        File file = new File(TMP_FOLDER + File.separator + getTestName() + System.nanoTime() );
        file.createNewFile();
        String path = file.getAbsolutePath() + File.separator + "users";

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(FileSystemPreferencesProvider.PATH, path);
        attributes.put(ConfiguredObject.ID, UUID.randomUUID());
        attributes.put(ConfiguredObject.NAME, getTestName());
        _preferencesProvider = new FileSystemPreferencesProviderImpl(attributes, _authenticationProvider);

        try
        {

            _preferencesProvider.create();

            fail("Creation of preferences provider with invalid path should have failed");
        }
        catch(IllegalConfigurationException e)
        {
            assertEquals("Unexpected exception message:" + e.getMessage(), String.format("Cannot create preferences store file at '%s'", path), e.getMessage());
        }
    }

    public void testValidationOnCreateWithInvalidPreferences()
    {
        File tmp = TestFileUtils.createTempFile(this, "preferences", "{blah:=boo}");
        try
        {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(FileSystemPreferencesProvider.PATH, tmp.getAbsolutePath());
            attributes.put(ConfiguredObject.ID, UUID.randomUUID());
            attributes.put(ConfiguredObject.NAME, getTestName());
            _preferencesProvider = new FileSystemPreferencesProviderImpl(attributes, _authenticationProvider);
            try
            {
                _preferencesProvider.create();
                fail("Exception is expected on validation of groups provider with invalid preferences format");
            }
            catch (IllegalConfigurationException e)
            {
                assertEquals("Unexpected exception message:" + e.getMessage(), "Cannot parse preferences json in " + tmp.getName(), e.getMessage());
            }
        }
        finally
        {
            tmp.delete();
        }
    }

    public void testConstructionWithEmptyFile() throws Exception
    {
        File emptyPrefsFile = new File(TMP_FOLDER, "preferences-" + getTestName() + ".json");
        emptyPrefsFile.createNewFile();
        assertTrue("Preferences file does not exist", emptyPrefsFile.exists());
        try
        {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(ConfiguredObject.ID, UUID.randomUUID());
            attributes.put(ConfiguredObject.NAME, getTestName());
            attributes.put(FileSystemPreferencesProvider.PATH, emptyPrefsFile.getAbsolutePath());
            _preferencesProvider = new FileSystemPreferencesProviderImpl(attributes, _authenticationProvider);
            assertEquals(State.UNINITIALIZED, _preferencesProvider.getState());
            _preferencesProvider.close();
        }
        finally
        {
            emptyPrefsFile.delete();
        }
    }

    public void testActivate()
    {
        _preferencesProvider = createPreferencesProvider();
        _preferencesProvider.start();

        assertEquals("Unexpected state", State.ACTIVE, _preferencesProvider.getState());
    }

    public void testChangeAttributes()
    {
        _preferencesProvider = createPreferencesProvider();
        _preferencesProvider.start();

        File newPrefsFile = TestFileUtils.createTempFile(this, ".prefs.json",  "{\"user3\":{\"pref1\":\"pref1User3Value\", \"pref3\": 2.0}}");
        try
        {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(FileSystemPreferencesProvider.PATH, newPrefsFile.getAbsolutePath());
            _preferencesProvider.changeAttributes(attributes);
            assertEquals("Unexpected path", newPrefsFile.getAbsolutePath(),
                    _preferencesProvider.getAttribute(FileSystemPreferencesProvider.PATH));

            Map<String, Object> preferences1 = _preferencesProvider.getPreferences(_user1);
            assertTrue("Unexpected preferences for user1", preferences1.isEmpty());

            String user3 = "user3";
            Map<String, Object> preferences3 = _preferencesProvider.getPreferences(user3);
            assertFalse("No preference found for user3", preferences3.isEmpty());
            assertEquals("Unexpected preference 1 for user 3", "pref1User3Value", preferences3.get("pref1"));
            assertEquals("Unexpected preference 3 for user 3", 2.0, ((Number) preferences3.get("pref3")).floatValue(), 0.01);
        }
        finally
        {
            newPrefsFile.delete();
        }
    }

    public void testGetPreferences()
    {
        _preferencesProvider = createPreferencesProvider();
        _preferencesProvider.start();

        Map<String, Object> preferences1 = _preferencesProvider.getPreferences(_user1);
        assertUser1Preferences(preferences1);

        Map<String, Object> preferences2 = _preferencesProvider.getPreferences(_user2);
        assertUser2Preferences(preferences2);

        String user3 = "user3";
        Map<String, Object> preferences3 = _preferencesProvider.getPreferences(user3);
        assertTrue("No preference found for user3", preferences3.isEmpty());
    }

    public void testSetPreferences()
    {
        _preferencesProvider = createPreferencesProvider();
        _preferencesProvider.start();

        Map<String, Object> newPreferences = new HashMap<String, Object>();
        newPreferences.put("pref2", false);
        newPreferences.put("pref4", 8);
        Map<String, Object> pref5 = new HashMap<String, Object>();
        pref5.put("test1", "test1Value");
        pref5.put("test2", 5);
        newPreferences.put("pref5", pref5);

        _preferencesProvider.setPreferences(_user1, newPreferences);
        _preferencesProvider.close();

        _preferencesProvider = createPreferencesProvider();
        _preferencesProvider.start();
        Map<String, Object> preferences1 = _preferencesProvider.getPreferences(_user1);
        assertNotNull("Preferences should not be null for user 1", preferences1);
        assertEquals("Unexpected preference 1 for user 1", "pref1User1Value", preferences1.get("pref1"));
        assertEquals("Unexpected preference 2 for user 1", false, preferences1.get("pref2"));
        assertEquals("Unexpected preference 3 for user 1", 1.0, ((Number) preferences1.get("pref3")).floatValue(), 0.01);
        assertEquals("Unexpected preference 4 for user 1", 8, preferences1.get("pref4"));
        assertNotNull("Unexpected preference 5 for user 1", preferences1.get("pref5"));
        assertEquals("Unexpected preference 5 for user 1", pref5, preferences1.get("pref5"));

        Map<String, Object> preferences2 = _preferencesProvider.getPreferences(_user2);
        assertUser2Preferences(preferences2);

        String user3 = "user3";
        Map<String, Object> preferences3 = _preferencesProvider.getPreferences(user3);
        assertTrue("Unexpected preferences found for user3", preferences3.isEmpty());
    }

    public void testDeletePreferences()
    {
        _preferencesProvider = createPreferencesProvider();
        _preferencesProvider.start();

        assertUser1Preferences(_preferencesProvider.getPreferences(_user1));
        assertUser2Preferences(_preferencesProvider.getPreferences(_user2));

        _preferencesProvider.deletePreferences(_user1);
        _preferencesProvider.close();

        _preferencesProvider = createPreferencesProvider();
        _preferencesProvider.start();
        Map<String, Object> preferences1 = _preferencesProvider.getPreferences(_user1);
        assertTrue("Preferences should not be set for user 1", preferences1.isEmpty());

        Map<String, Object> preferences2 = _preferencesProvider.getPreferences(_user2);
        assertUser2Preferences(preferences2);

        String user3 = "user3";
        Map<String, Object> preferences3 = _preferencesProvider.getPreferences(user3);
        assertTrue("Unexpected preferences found for user3", preferences3.isEmpty());
    }

    public void testDeleteMultipleUsersPreferences()
    {
        _preferencesProvider = createPreferencesProvider();
        _preferencesProvider.start();

        assertUser1Preferences(_preferencesProvider.getPreferences(_user1));
        assertUser2Preferences(_preferencesProvider.getPreferences(_user2));

        _preferencesProvider.deletePreferences(_user1, _user2);
        _preferencesProvider.close();

        _preferencesProvider = createPreferencesProvider();
        _preferencesProvider.start();
        Map<String, Object> preferences1 = _preferencesProvider.getPreferences(_user1);
        assertTrue("Preferences should not be set for user 1", preferences1.isEmpty());

        Map<String, Object> preferences2 = _preferencesProvider.getPreferences(_user2);
        assertTrue("Preferences should not be set for user 2", preferences2.isEmpty());

        String user3 = "user3";
        Map<String, Object> preferences3 = _preferencesProvider.getPreferences(user3);
        assertTrue("No preference found for user3", preferences3.isEmpty());
    }

    public void testListUserNames()
    {
        _preferencesProvider = createPreferencesProvider();
        _preferencesProvider.start();

        Set<String> userNames = _preferencesProvider.listUserIDs();

        assertEquals("Unexpected user names", new HashSet<String>(Arrays.asList("user1", "user2")), userNames);
    }

    private FileSystemPreferencesProviderImpl createPreferencesProvider()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(FileSystemPreferencesProvider.PATH, _preferencesFile.getAbsolutePath());
        attributes.put(ConfiguredObject.ID, UUID.randomUUID());
        attributes.put(ConfiguredObject.NAME, "test");
        _preferencesProvider = new FileSystemPreferencesProviderImpl(attributes, _authenticationProvider);
        _preferencesProvider.open();
        return _preferencesProvider;
    }

    private void assertUser1Preferences(Map<String, Object> preferences1)
    {
        assertNotNull("Preferences should not be null for user 1", preferences1);
        assertEquals("Unexpected preference 1 for user 1", "pref1User1Value", preferences1.get("pref1"));
        assertEquals("Unexpected preference 2 for user 1", true, preferences1.get("pref2"));
        assertEquals("Unexpected preference 3 for user 1", 1.0, ((Number) preferences1.get("pref3")).floatValue(), 0.01);
        assertEquals("Unexpected preference 4 for user 1", 2, preferences1.get("pref4"));
        assertNull("Unexpected preference 5 for user 1", preferences1.get("pref5"));
    }

    private void assertUser2Preferences(Map<String, Object> preferences2)
    {
        assertNotNull("Preferences should not be null for user 2", preferences2);
        assertEquals("Unexpected preference 1 for user 2", "pref1User2Value", preferences2.get("pref1"));
        assertEquals("Unexpected preference 2 for user 2", false, preferences2.get("pref2"));
        assertEquals("Unexpected preference 2 for user 2", 2.0, ((Number) preferences2.get("pref3")).floatValue(), 0.01);
        assertEquals("Unexpected preference 3 for user 2", 3, preferences2.get("pref4"));
        assertNull("Unexpected preference 5 for user 2", preferences2.get("pref5"));
    }
}
