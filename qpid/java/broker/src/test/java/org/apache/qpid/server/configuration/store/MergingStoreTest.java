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

import static org.apache.qpid.server.configuration.ConfigurationEntryStoreFactory.DEFAULTS;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

public class MergingStoreTest extends QpidTestCase
{
    private ConfigurationEntryStore _masterStore;
    private ConfigurationEntryStore _userStore;

    private File _userStoreFile;

    public void setUp() throws Exception
    {
        super.setUp();
        setTestSystemProperty("QPID_HOME", TMP_FOLDER);
        _masterStore = new JsonConfigurationEntryStore(getClass().getClassLoader().getResource(DEFAULTS));
        _userStoreFile = new File(TMP_FOLDER, "_store_" + System.currentTimeMillis() + "_" + getTestName());
        _userStore = createStore(_userStoreFile);
    }

    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            if (_userStoreFile != null)
            {
                FileUtils.delete(_userStoreFile, true);
            }
        }
    }

    private ConfigurationEntryStore createStore(File userStoreFile) throws Exception
    {
        return BrokerTestHelper.createTestProfileBrokerConfigurationStore(userStoreFile.getAbsolutePath());
    }

    public void testAllMasterEntriesAreCopiedForEmptyUserStore()
    {
        MergingStore store = new MergingStore(_userStore, _masterStore);

        assertStoreEntries(store, _userStore, _masterStore);
    }

    private void assertStoreEntries(ConfigurationEntryStore mergedStore, ConfigurationEntryStore userStore,
            ConfigurationEntryStore masterStore)
    {
        ConfigurationEntry masterRootEntry = masterStore.getRootEntry();
        ConfigurationEntry userRootEntry = userStore.getRootEntry();
        ConfigurationEntry mergedRootEntry = mergedStore.getRootEntry();

        Map<String, Object> masterStoreAttributes = masterRootEntry.getAttributes();
        assertFalse("Master store has no attributes defined for broker", masterStoreAttributes.isEmpty());

        Map<String, Object> userStoreAttributes = userRootEntry.getAttributes();
        Map<String, Object> mergedStoreAttributes = mergedRootEntry.getAttributes();
        for (Map.Entry<String, Object> attributeEntry : masterStoreAttributes.entrySet())
        {
            assertEquals("Unexpected attribute " + attributeEntry.getKey() + " in user store", attributeEntry.getValue(),
                    userStoreAttributes.get(attributeEntry.getKey()));
            assertEquals("Unexpected attribute " + attributeEntry.getKey() + " in merged store", attributeEntry.getValue(),
                    mergedStoreAttributes.get(attributeEntry.getKey()));
        }

        Set<UUID> childrenIds = masterRootEntry.getChildrenIds();
        assertFalse("Master store has no chldren", childrenIds.isEmpty());

        for (UUID id : childrenIds)
        {
            ConfigurationEntry masterEntry = masterStore.getEntry(id);
            ConfigurationEntry userEntry = userStore.getEntry(id);
            ConfigurationEntry mergedEntry = mergedStore.getEntry(id);

            assertEquals("Unexpected entry in user store", masterEntry, userEntry);
            assertEquals("Unexpected entry in merged store", masterEntry, mergedEntry);
        }
    }

    public void testMasterEntriesAreCopiedIntoUserStoreWhenTheyAreMissedInUserStore()
    {
        // merge all entries
        MergingStore store = new MergingStore(_userStore, _masterStore);

        Map<String, Collection<ConfigurationEntry>> userChildren = _userStore.getRootEntry().getChildren();
        Collection<ConfigurationEntry> ports = userChildren.get(Port.class.getSimpleName());
        assertFalse("Ports are missed in master store", ports.isEmpty());

        // remove ports
        for (ConfigurationEntry portEntry : ports)
        {
            _userStore.remove(portEntry.getId());
        }

        // merge again
        store = new MergingStore(_userStore, _masterStore);

        assertStoreEntries(store, _userStore, _masterStore);
    }

    public void testMasterEntriesAreCopiedIntoUserStoreWhenTheyAreReplacedWithEntriesWithDifferentNames()
    {
        // merge all entries
        MergingStore store = new MergingStore(_userStore, _masterStore);

        Map<String, Collection<ConfigurationEntry>> userChildren = _userStore.getRootEntry().getChildren();
        Collection<ConfigurationEntry> ports = userChildren.get(Port.class.getSimpleName());
        assertFalse("Ports are missed in master store", ports.isEmpty());

        // remove ports
        for (ConfigurationEntry portEntry : ports)
        {
            _userStore.remove(portEntry.getId());
        }

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.NAME, getTestName());
        ConfigurationEntry port = new ConfigurationEntry(UUID.randomUUID(), Port.class.getSimpleName(), attributes, null,
                _userStore);
        _userStore.save(port);

        // merge again
        store = new MergingStore(_userStore, _masterStore);

        assertStoreEntries(store, _userStore, _masterStore);

        // previously added custom entry still should be in store
        ConfigurationEntry storedPortEntry = store.getEntry(port.getId());
        assertEquals("User port entry was removed", port, storedPortEntry);
    }

    public void testStoreEntriesAreNotReplacedIfAttributesAreModified() throws Exception
    {
        // merge all entries
        MergingStore store = new MergingStore(_userStore, _masterStore);

        ConfigurationEntry root = store.getRootEntry();
        Set<UUID> childrenIds = root.getChildrenIds();
        assertFalse("Cannot find chldren", childrenIds.isEmpty());

        Set<UUID> all = new HashSet<UUID>(childrenIds);
        all.add(root.getId());

        // store new attributes in map for verification
        Map<UUID, Map<String, Object>> modifiedAttributes = new HashMap<UUID, Map<String, Object>>();
        Set<ConfigurationEntry> entriesToStore = new HashSet<ConfigurationEntry>();

        // modify primitive attributes in all entries
        for (UUID uuid : all)
        {
            ConfigurationEntry entry = store.getEntry(uuid);
            Map<String, Object> newAttributes = new HashMap<String, Object>();
            modifiedAttributes.put(entry.getId(), newAttributes);
            ConfigurationEntry modifiedEntry = new ConfigurationEntry(entry.getId(), entry.getType(), newAttributes,
                    entry.getChildrenIds(), entry.getStore());
            entriesToStore.add(modifiedEntry);
            for (Map.Entry<String, Object> attributeEntry : entry.getAttributes().entrySet())
            {
                Object value = attributeEntry.getValue();
                String key = attributeEntry.getKey();
                if (!key.equals("name"))
                {
                    if (value instanceof String)
                    {
                        value = (String) value + "_Modified";
                    }
                    else if (value instanceof Number)
                    {
                        value = ((Number) value).intValue() + 10000;
                    }
                    else if (value instanceof Boolean)
                    {
                        value = !((Boolean) value).booleanValue();
                    }
                }
                newAttributes.put(key, value);
            }
        }

        // save modified entries
        store.save(entriesToStore.toArray(new ConfigurationEntry[entriesToStore.size()]));

        // merge again
        store = new MergingStore(_userStore, _masterStore);

        for (Map.Entry<UUID, Map<String, Object>> entryAttributes : modifiedAttributes.entrySet())
        {
            ConfigurationEntry entry = store.getEntry(entryAttributes.getKey());
            assertEquals("Unexpected attributes", entryAttributes.getValue(), entry.getAttributes());
        }

        // assert that all values have been saved, re-create user store
        _userStore = createStore(_userStoreFile);

        for (Map.Entry<UUID, Map<String, Object>> entryAttributes : modifiedAttributes.entrySet())
        {
            ConfigurationEntry entry = store.getEntry(entryAttributes.getKey());
            assertEquals("Unexpected attributes in user store", entryAttributes.getValue(), entry.getAttributes());
        }

    }
}
