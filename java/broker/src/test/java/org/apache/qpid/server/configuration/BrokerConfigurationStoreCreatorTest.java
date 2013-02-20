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
package org.apache.qpid.server.configuration;

import java.io.File;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.qpid.server.configuration.store.JsonConfigurationEntryStore;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

public class BrokerConfigurationStoreCreatorTest extends QpidTestCase
{
    private File _userStoreLocation;
    private BrokerConfigurationStoreCreator _storeCreator;

    public void setUp() throws Exception
    {
        super.setUp();

        // check whether QPID_HOME JVM system property is set
        if (QPID_HOME == null)
        {
            // set the properties in order to resolve the defaults store settings
            setTestSystemProperty("QPID_HOME", TMP_FOLDER);
        }
        _storeCreator = new BrokerConfigurationStoreCreator();
        _userStoreLocation = new File(TMP_FOLDER, "_store_" + System.currentTimeMillis() + "_" + getTestName());
    }

    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            if (_userStoreLocation != null)
            {
                FileUtils.delete(_userStoreLocation, true);
            }
        }
    }

    public void testCreateJsonStore()
    {
        ConfigurationEntryStore store = _storeCreator.createStore(_userStoreLocation.getAbsolutePath(), "json", null, null);
        assertNotNull("Store was not created", store);
        assertTrue("File should exists", _userStoreLocation.exists());
        assertTrue("File size should be greater than 0", _userStoreLocation.length() > 0);
        JsonConfigurationEntryStore jsonStore = new JsonConfigurationEntryStore();
        jsonStore.open(_userStoreLocation.getAbsolutePath());
        Set<UUID> childrenIds = jsonStore.getRootEntry().getChildrenIds();
        assertFalse("Unexpected children: " + childrenIds, childrenIds.isEmpty());
    }

    public void testCreateJsonStoreFromInitialStore() throws Exception
    {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);

        Map<String, Object> brokerObjectMap = new HashMap<String, Object>();
        UUID brokerId = UUID.randomUUID();
        brokerObjectMap.put(Broker.ID, brokerId);
        brokerObjectMap.put("name", "Test");

        StringWriter sw = new StringWriter();
        objectMapper.writeValue(sw, brokerObjectMap);

        String brokerJson = sw.toString();

        File _storeFile = TestFileUtils.createTempFile(this, ".json", brokerJson);

        ConfigurationEntryStore store = _storeCreator.createStore(_userStoreLocation.getAbsolutePath(), "json", _storeFile.getAbsolutePath(), "json");
        assertNotNull("Store was not created", store);
        assertTrue("File should exists", _userStoreLocation.exists());
        assertTrue("File size should be greater than 0", _userStoreLocation.length() > 0);
        JsonConfigurationEntryStore jsonStore = new JsonConfigurationEntryStore();
        jsonStore.open(_userStoreLocation.getAbsolutePath());
        ConfigurationEntry entry = jsonStore.getRootEntry();
        assertEquals("Unexpected root id", brokerId, entry.getId());
        Map<String, Object> attributes = entry.getAttributes();
        assertNotNull("Unexpected attributes: " + attributes, attributes);
        assertEquals("Unexpected attributes size: " + attributes.size(), 1, attributes.size());
        assertEquals("Unexpected attribute name: " + attributes.get("name"), "Test", attributes.get("name"));
        Set<UUID> childrenIds = entry.getChildrenIds();
        assertTrue("Unexpected children: " + childrenIds, childrenIds.isEmpty());
    }

    public void testCreateDerbyStore()
    {
        //TODO: Implement DERBY store
        try
        {
            _storeCreator.createStore(_userStoreLocation.getAbsolutePath(), "derby", null, null);
            fail("Store is not yet supported");
        }
        catch(IllegalConfigurationException e)
        {
            // pass
        }
    }

    public void testCreateXmlStore() throws Exception
    {
        try
        {
            _storeCreator.createStore(_userStoreLocation.getAbsolutePath(), "xml", null, null);
            fail("Store is not yet supported");
        }
        catch(IllegalConfigurationException e)
        {
            // pass
        }
    }

}
