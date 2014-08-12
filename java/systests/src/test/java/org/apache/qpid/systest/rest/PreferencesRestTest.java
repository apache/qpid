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
import java.util.Map;

import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.adapter.FileSystemPreferencesProvider;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.test.utils.TestFileUtils;

public class PreferencesRestTest extends QpidRestTestCase
{
    private File _preferencesProviderFile;

    public void setUp() throws Exception
    {
        _preferencesProviderFile = TestFileUtils.createTempFile(this, ".prefs.json",
                "{\"webadmin\":{\"language\": \"en\", \"saveTabs\":true}}");
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

    public void testGetPreferences() throws Exception
    {
        Map<String, Object> preferences = getRestTestHelper().getJsonAsMap("/service/preferences");
        assertEquals("Unexpected number of preferences", 2, preferences.size());
        assertEquals("Unexpected language preference", "en", preferences.get("language"));
        assertEquals("Unexpected saveTabs preference", true, preferences.get("saveTabs"));
    }

    public void testUpdatePreferences() throws Exception
    {
        Map<String, Object> additionalPreferences = new HashMap<String, Object>();
        additionalPreferences.put("timezone", "Europe/London");
        additionalPreferences.put("test", 1);

        int status = getRestTestHelper().submitRequest("/service/preferences", "POST", additionalPreferences);
        assertEquals("Unexpected response code", 200, status);

        Map<String, Object> preferences = getRestTestHelper().getJsonAsMap("/service/preferences");
        assertEquals("Unexpected number of preferences", 4, preferences.size());
        assertEquals("Unexpected language preference", "en", preferences.get("language"));
        assertEquals("Unexpected saveTabs preference", true, preferences.get("saveTabs"));
        assertEquals("Unexpected timezone preference", "Europe/London", preferences.get("timezone"));
        assertEquals("Unexpected test preference", 1, preferences.get("test"));
    }

    public void testReplacePreferences() throws Exception
    {
        Map<String, Object> additionalPreferences = new HashMap<String, Object>();
        additionalPreferences.put("timezone", "Europe/London");
        additionalPreferences.put("test", 1);

        int status = getRestTestHelper().submitRequest("/service/preferences", "PUT", additionalPreferences);
        assertEquals("Unexpected response code", 200, status);

        Map<String, Object> preferences = getRestTestHelper().getJsonAsMap("/service/preferences");
        assertEquals("Unexpected number of preferences", 2, preferences.size());
        assertEquals("Unexpected timezone preference", "Europe/London", preferences.get("timezone"));
        assertEquals("Unexpected test preference", 1, preferences.get("test"));
    }

}
