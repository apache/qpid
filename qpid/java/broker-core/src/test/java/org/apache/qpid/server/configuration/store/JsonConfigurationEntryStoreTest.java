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
package org.apache.qpid.server.configuration.store;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryImpl;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.model.SystemContextImpl;
import org.apache.qpid.server.model.adapter.FileSystemPreferencesProvider;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.test.utils.TestFileUtils;


public class JsonConfigurationEntryStoreTest extends ConfigurationEntryStoreTestCase
{
    private File _storeFile;
    private ObjectMapper _objectMapper;


    @Override
    public void setUp() throws Exception
    {
        _objectMapper = new ObjectMapper();
        _objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception
    {
        _storeFile.delete();
        super.tearDown();
    }

    @Override
    protected JsonConfigurationEntryStore createStore(UUID brokerId, Map<String, Object> brokerAttributes) throws Exception
    {
        _storeFile = createStoreFile(brokerId, brokerAttributes);
        return createStore();
    }

    private JsonConfigurationEntryStore createStore()
    {
        String absolutePath = _storeFile.getAbsolutePath();
        return createStore(absolutePath);
    }

    private JsonConfigurationEntryStore createStore(final String absolutePath)
    {
        return createStore(absolutePath, null);
    }

    private JsonConfigurationEntryStore createStore(final String absolutePath,
                                                    final DurableConfigurationStore initialStore)
    {
        final BrokerOptions brokerOptions = mock(BrokerOptions.class);
        when(brokerOptions.getConfigurationStoreLocation()).thenReturn(absolutePath);
        SystemContext context = new SystemContextImpl(new TaskExecutor(),
                                                  new ConfiguredObjectFactory(Model.getInstance()),
                                                  mock(EventLogger.class),
                                                  mock(LogRecorder.class),
                                                  brokerOptions);

        JsonConfigurationEntryStore store = new JsonConfigurationEntryStore(context, initialStore, false,
                                                                            Collections.<String,String>emptyMap());
        return store;
    }

    private File createStoreFile(UUID brokerId, Map<String, Object> brokerAttributes) throws IOException,
                                                                                             JsonGenerationException, JsonMappingException
    {
        return createStoreFile(brokerId, brokerAttributes, true);
    }

    private File createStoreFile(UUID brokerId, Map<String, Object> brokerAttributes, boolean setVersion) throws IOException,
            JsonGenerationException, JsonMappingException
    {
        Map<String, Object> brokerObjectMap = new HashMap<String, Object>();
        brokerObjectMap.put(Broker.ID, brokerId);
        if (setVersion)
        {
            brokerObjectMap.put(Broker.STORE_VERSION, MemoryConfigurationEntryStore.STORE_VERSION);
        }
        brokerObjectMap.put(Broker.NAME, getTestName());
        brokerObjectMap.putAll(brokerAttributes);

        StringWriter sw = new StringWriter();
        _objectMapper.writeValue(sw, brokerObjectMap);

        String brokerJson = sw.toString();

        return TestFileUtils.createTempFile(this, ".json", brokerJson);
    }

    @Override
    protected void addConfiguration(UUID id, String type, Map<String, Object> attributes, UUID parentId)
    {
        MemoryConfigurationEntryStore store = getStore();
        ConfigurationEntry parentEntry = getStore().getEntry(parentId);
        Set<UUID> children = new HashSet<UUID>(parentEntry.getChildrenIds());
        children.add(id);
        ConfigurationEntry newParentEntry = new ConfigurationEntryImpl(parentEntry.getId(), parentEntry.getType(), parentEntry.getAttributes(), children, store);
        store.save(newParentEntry, new ConfigurationEntryImpl(id, type, attributes, Collections.<UUID> emptySet(), store));
    }

    /* **
     *  TODO - RG : This functionality has been moved to the generic configured object.  The JsonConfigurationEntryStore
     *  needs to be removed and replaced with the Json configuration store written for the virtual host, however testing
     *  resolution functionality needs to occur in a configured object resolution test
     * **


    public void testAttributeIsResolvedFromSystemProperties()
    {
        String defaultVhost = getTestName();
        setTestSystemProperty("my.test.property", defaultVhost);

        MemoryConfigurationEntryStore store = getStore();
        ConfigurationEntry brokerConfigEntry = store.getRootEntry();
        Map<String, Object> attributes = new HashMap<String, Object>(brokerConfigEntry.getAttributes());
        attributes.put(Broker.DEFAULT_VIRTUAL_HOST, "${my.test.property}");
        ConfigurationEntry
                updatedBrokerEntry = new ConfigurationEntryImpl(brokerConfigEntry.getId(), Broker.class.getSimpleName(),
                attributes, brokerConfigEntry.getChildrenIds(), store);
        store.save(updatedBrokerEntry);

        JsonConfigurationEntryStore store2 = createStore();

        assertEquals("Unresolved default virtualhost  value", defaultVhost, store2.getRootEntry().getAttributes().get(Broker.DEFAULT_VIRTUAL_HOST));
    }
    */

    public void testCreateEmptyStore()
    {
        File file = TestFileUtils.createTempFile(this, ".json");
        try
        {
            createStore(file.getAbsolutePath());
            fail("Cannot create a new store without initial store");
        }
        catch(IllegalConfigurationException e)
        {
            // pass
        }
    }

    public void testCreateFromExistingLocation() throws Exception
    {
        UUID brokerId = UUID.randomUUID();
        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        brokerAttributes.put(Broker.NAME, getTestName());
        File file = createStoreFile(brokerId, brokerAttributes);

        JsonConfigurationEntryStore store = createStore(file.getAbsolutePath());
        ConfigurationEntry root = store.getRootEntry();
        assertNotNull("Root entry is not found", root);
        assertEquals("Unexpected root entry", brokerId, root.getId());
        Map<String, Object> attributes = root.getAttributes();
        assertNotNull("Attributes not found", attributes);
        assertEquals("Unexpected number of attributes", 2, attributes.size());
        assertEquals("Unexpected name attribute", getTestName(), attributes.get(Broker.NAME));
        assertEquals("Unexpected version attribute", 1, attributes.get(Broker.STORE_VERSION));
    }

    public void testCreateFromInitialStore() throws Exception
    {
        UUID brokerId = UUID.randomUUID();
        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        File initialStoreFile = createStoreFile(brokerId, brokerAttributes);

        JsonConfigurationEntryStore initialStore = createStore(initialStoreFile.getAbsolutePath());

        File storeFile = TestFileUtils.createTempFile(this, ".json");
        JsonConfigurationEntryStore store = createStore(storeFile.getAbsolutePath(), initialStore);

        ConfigurationEntry root = store.getRootEntry();
        assertNotNull("Root entry is not found", root);
        assertEquals("Unexpected root entry", brokerId, root.getId());
        Map<String, Object> attributes = root.getAttributes();
        assertNotNull("Attributes not found", attributes);
        assertEquals("Unexpected number of attributes", 2, attributes.size());
        assertEquals("Unexpected name attribute", getTestName(), attributes.get(Broker.NAME));
        assertEquals("Unexpected version attribute", 1, attributes.get(Broker.STORE_VERSION));
    }

    public void testGetVersion()
    {
        assertEquals("Unexpected version", 1, getStore().getVersion());
    }

    public void testGetType()
    {
        assertEquals("Unexpected type", "json", getStore().getType());
    }

    public void testUnsupportedStoreVersion() throws Exception
    {
        UUID brokerId = UUID.randomUUID();
        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        int[] storeVersions = {Integer.MAX_VALUE, 0};
        for (int storeVersion : storeVersions)
        {
            brokerAttributes.put(Broker.STORE_VERSION, storeVersion);
            File storeFile = null;
            try
            {
                storeFile = createStoreFile(brokerId, brokerAttributes);
                createStore(storeFile.getAbsolutePath());
                fail("The store creation should fail due to unsupported store version");
            }
            catch (IllegalConfigurationException e)
            {
                assertEquals("The data of version " + storeVersion
                        + " can not be loaded by store of version " + MemoryConfigurationEntryStore.STORE_VERSION, e.getMessage());
            }
            finally
            {
                if (storeFile != null)
                {
                    storeFile.delete();
                }
            }
        }
    }

    public void testStoreVersionNotSpecified() throws Exception
    {
        UUID brokerId = UUID.randomUUID();
        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        File storeFile = null;
        try
        {
            storeFile = createStoreFile(brokerId, brokerAttributes, false);
            createStore(storeFile.getAbsolutePath());
            fail("The store creation should fail due to unspecified store version");
        }
        catch (IllegalConfigurationException e)
        {
            assertEquals("Broker " + Broker.STORE_VERSION + " attribute must be specified", e.getMessage());
        }
        finally
        {
            if (storeFile != null)
            {
                storeFile.delete();
            }
        }
    }

    public void testGetPreferencesProvider() throws Exception
    {
        UUID preferencesProviderId = UUID.randomUUID();
        String path = TMP_FOLDER;
        String name = getTestName();

        addPreferencesProvider(preferencesProviderId, name, path);

        // verify that store can deserialise child of a child
        JsonConfigurationEntryStore newStore = createStore();

        ConfigurationEntry authenticationProviderEntry = newStore.getEntry(_authenticationProviderId);
        assertEquals("Unexpected preference provider ID in authentication provider children set", preferencesProviderId, authenticationProviderEntry.getChildrenIds().iterator().next());
        ConfigurationEntry preferencesProviderEntry = newStore.getEntry(preferencesProviderId);
        assertNotNull("Preferences provider is not found", preferencesProviderEntry);
        assertEquals("Unexpected preferences provider id", preferencesProviderId, preferencesProviderEntry.getId());
        Map<String, Object> attributes = preferencesProviderEntry.getAttributes();
        assertEquals("Unexpected preferences provider name", name, attributes.get(PreferencesProvider.NAME));
        assertEquals("Unexpected preferences provider path", path, attributes.get(FileSystemPreferencesProvider.PATH));
        assertEquals("Unexpected preferences provider type", FileSystemPreferencesProvider.PROVIDER_TYPE,
                attributes.get(PreferencesProvider.TYPE));
    }

}
