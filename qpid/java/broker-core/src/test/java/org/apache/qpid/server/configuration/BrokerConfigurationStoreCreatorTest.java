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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.store.JsonConfigurationEntryStore;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObjectFactoryImpl;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.model.SystemContextImpl;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;

public class BrokerConfigurationStoreCreatorTest extends QpidTestCase
{
    private File _userStoreLocation;
    private BrokerConfigurationStoreCreator _storeCreator;
    private SystemContext _systemContext;
    private TaskExecutor _taskExecutor;

    public void setUp() throws Exception
    {
        super.setUp();

        // check whether QPID_HOME JVM system property is set
        if (QPID_HOME == null)
        {
            // set the properties in order to resolve the defaults store settings
            setTestSystemProperty("QPID_HOME", TMP_FOLDER);
            setTestSystemProperty("QPID_WORK", TMP_FOLDER + File.separator + "work");
        }
        _storeCreator = new BrokerConfigurationStoreCreator();
        _userStoreLocation = new File(TMP_FOLDER, "_store_" + System.currentTimeMillis() + "_" + getTestName());
        final BrokerOptions brokerOptions = mock(BrokerOptions.class);
        when(brokerOptions.getConfigurationStoreLocation()).thenReturn(_userStoreLocation.getAbsolutePath());
        _taskExecutor = new TaskExecutor();
        _taskExecutor.start();
        _systemContext = new SystemContextImpl(_taskExecutor,
                                                  new ConfiguredObjectFactoryImpl(Model.getInstance()),
                                                  mock(EventLogger.class),
                                                  mock(LogRecorder.class),
                                                  brokerOptions);
    }

    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
            _taskExecutor.stop();
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
        ConfigurationEntryStore store = _storeCreator.createStore(_systemContext, "json", BrokerOptions.DEFAULT_INITIAL_CONFIG_LOCATION, false, new BrokerOptions().getConfigProperties());
        assertNotNull("Store was not created", store);
        assertTrue("File should exists", _userStoreLocation.exists());
        assertTrue("File size should be greater than 0", _userStoreLocation.length() > 0);
        JsonConfigurationEntryStore jsonStore = new JsonConfigurationEntryStore(_systemContext, null, false, Collections
                .<String,String>emptyMap());
        Set<UUID> childrenIds = jsonStore.getRootEntry().getChildrenIds();
        assertFalse("Unexpected children: " + childrenIds, childrenIds.isEmpty());
    }

    public void testCreateJsonStoreFromInitialStore() throws Exception
    {
        createJsonStoreFromInitialStoreTestImpl(false);
    }

    public void testOverwriteExistingJsonStoreWithInitialConfig() throws Exception
    {
        createJsonStoreFromInitialStoreTestImpl(true);
    }

    public void createJsonStoreFromInitialStoreTestImpl(boolean overwrite) throws Exception
    {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);

        String defaultBrokerName = "${broker.name}";
        String testBrokerName = getTestName();

        Map<String, Object> brokerObjectMap = new HashMap<String, Object>();
        UUID testBrokerId = UUID.randomUUID();
        brokerObjectMap.put(Broker.ID, testBrokerId);
        brokerObjectMap.put(Broker.NAME, testBrokerName);
        brokerObjectMap.put(Broker.MODEL_VERSION, Model.MODEL_VERSION);
        brokerObjectMap.put(Broker.STORE_VERSION, 1);

        StringWriter sw = new StringWriter();
        objectMapper.writeValue(sw, brokerObjectMap);

        String brokerJson = sw.toString();

        File _initialStoreFile = TestFileUtils.createTempFile(this, ".json", brokerJson);

        ConfigurationEntryStore store = _storeCreator.createStore(_systemContext, "json", _initialStoreFile.getAbsolutePath(), false, Collections.<String,String>emptyMap());
        assertNotNull("Store was not created", store);
        assertTrue("File should exists", _userStoreLocation.exists());
        assertTrue("File size should be greater than 0", _userStoreLocation.length() > 0);
        JsonConfigurationEntryStore jsonStore = new JsonConfigurationEntryStore(_systemContext, null, false, Collections.<String,String>emptyMap());
        ConfigurationEntry entry = jsonStore.getRootEntry();
        assertEquals("Unexpected root id", testBrokerId, entry.getId());
        Map<String, Object> attributes = entry.getAttributes();
        assertNotNull("Unexpected attributes: " + attributes, attributes);
        assertEquals("Unexpected attributes size: " + attributes.size(), 3, attributes.size());
        assertEquals("Unexpected attribute name: " + attributes.get("name"), testBrokerName, attributes.get(Broker.NAME));
        Set<UUID> childrenIds = entry.getChildrenIds();
        assertTrue("Unexpected children: " + childrenIds, childrenIds.isEmpty());

        if(overwrite)
        {
            ConfigurationEntryStore overwrittenStore = _storeCreator.createStore(_systemContext, "json", BrokerOptions.DEFAULT_INITIAL_CONFIG_LOCATION, true, new BrokerOptions().getConfigProperties());
            assertNotNull("Store was not created", overwrittenStore);
            assertTrue("File should exists", _userStoreLocation.exists());
            assertTrue("File size should be greater than 0", _userStoreLocation.length() > 0);

            //check the contents reflect the test store content having been overwritten with the default store
            JsonConfigurationEntryStore reopenedOverwrittenStore = new JsonConfigurationEntryStore(_systemContext, null, false, Collections.<String,String>emptyMap());
            entry = reopenedOverwrittenStore.getRootEntry();
            assertFalse("Root id did not change, store content was not overwritten", testBrokerId.equals(entry.getId()));
            attributes = entry.getAttributes();
            assertNotNull("No attributes found", attributes);
            assertFalse("Test name should not equal default broker name", testBrokerName.equals(defaultBrokerName));
            assertEquals("Unexpected broker name value" , defaultBrokerName, attributes.get(Broker.NAME));
            childrenIds = entry.getChildrenIds();
            assertFalse("Expected children were not found" + childrenIds, childrenIds.isEmpty());
        }
    }

    public void testCreateStoreWithUnknownType()
    {
        try
        {
            _storeCreator.createStore(_systemContext, "other", null, false, Collections.<String,String>emptyMap());
            fail("Store is not yet supported");
        }
        catch(IllegalConfigurationException e)
        {
            // pass
        }
    }

}
