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

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.codehaus.jackson.map.ObjectMapper;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryImpl;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObjectFactoryImpl;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.model.SystemContextImpl;

public class MemoryConfigurationEntryStoreTest extends ConfigurationEntryStoreTestCase
{
    private SystemContext _systemContext;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _systemContext = new SystemContextImpl(getTaskExecutor(), new ConfiguredObjectFactoryImpl(Model.getInstance()),
                                           mock(EventLogger.class), mock(LogRecorder.class),
                                           new BrokerOptions());

    }

    @Override
    protected MemoryConfigurationEntryStore createStore(UUID brokerId, Map<String, Object> brokerAttributes) throws Exception
    {
        Map<String, Object> broker = new HashMap<String, Object>();
        broker.put(Broker.ID, brokerId);
        broker.putAll(brokerAttributes);
        ObjectMapper mapper = new ObjectMapper();

        return new MemoryConfigurationEntryStore(mapper.writeValueAsString(broker), Collections.<String,String>emptyMap());
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

    public void testCreateWithNullLocationAndNullInitialStore()
    {
        try
        {
            new MemoryConfigurationEntryStore(null, null, null, Collections.<String,String>emptyMap());
            fail("Cannot create a memory store without either initial store or path to an initial store file");
        }
        catch(IllegalConfigurationException e)
        {
            // pass
        }
    }

    public void testCreateWithNullJson()
    {
        MemoryConfigurationEntryStore store = new MemoryConfigurationEntryStore(null, Collections.<String,String>emptyMap());

        ConfigurationEntry root = store.getRootEntry();
        assertNotNull("Root entry is not found", root);
    }

    public void testOpenInMemoryWithInitialStore() throws Exception
    {
        UUID brokerId = UUID.randomUUID();
        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        brokerAttributes.put(Broker.NAME, getTestName());
        MemoryConfigurationEntryStore  initialStoreFile = createStore(brokerId, brokerAttributes);
        MemoryConfigurationEntryStore store = new MemoryConfigurationEntryStore(_systemContext, null, initialStoreFile, Collections.<String,String>emptyMap());

        ConfigurationEntry root = store.getRootEntry();
        assertNotNull("Root entry is not found", root);
        assertEquals("Unexpected root entry", brokerId, root.getId());
        Map<String, Object> attributes = root.getAttributes();
        assertNotNull("Attributes not found", attributes);
        assertEquals("Unexpected number of attributes", 1, attributes.size());
        assertEquals("Unexpected name attribute", getTestName(), attributes.get(Broker.NAME));
    }


    public void testOpenWithDefaultInitialStore() throws Exception
    {
        // check whether QPID_HOME JVM system property is set
        if (QPID_HOME == null)
        {
            // set the properties in order to resolve the defaults store settings
            setTestSystemProperty("QPID_HOME", TMP_FOLDER);
            setTestSystemProperty("QPID_WORK", TMP_FOLDER + File.separator + "work");
        }
        MemoryConfigurationEntryStore initialStore = new MemoryConfigurationEntryStore(_systemContext,BrokerOptions.DEFAULT_INITIAL_CONFIG_LOCATION, null, new BrokerOptions().getConfigProperties());
        ConfigurationEntry initialStoreRoot = initialStore.getRootEntry();
        assertNotNull("Initial store root entry is not found", initialStoreRoot);

         MemoryConfigurationEntryStore store = new MemoryConfigurationEntryStore(_systemContext, null, initialStore, Collections.<String,String>emptyMap());

        ConfigurationEntry root = store.getRootEntry();
        assertNotNull("Root entry is not found", root);

        assertEquals("Unexpected broker attributes", initialStoreRoot.getAttributes(), root.getAttributes());
        assertEquals("Unexpected broker children", initialStoreRoot.getChildrenIds(), root.getChildrenIds());
    }

    public void testGetVersion()
    {
        assertEquals("Unexpected version", 1, getStore().getVersion());
    }

    public void testGetType()
    {
        assertEquals("Unexpected type", "memory", getStore().getType());
    }

}
