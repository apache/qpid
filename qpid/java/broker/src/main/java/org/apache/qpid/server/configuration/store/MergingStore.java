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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;

/**
 * A store implementation which is responsible for copying the configuration
 * from the master store into a user store if the configuration entries with the
 * same name do not exist in the user store.
 */
public class MergingStore implements ConfigurationEntryStore
{
    private final ConfigurationEntryStore _userStore;

    public MergingStore(ConfigurationEntryStore userStore, ConfigurationEntryStore masterStore)
    {
        mergeAndSave(userStore, masterStore);
        _userStore = userStore;
    }

    @Override
    public ConfigurationEntry getRootEntry()
    {
        return _userStore.getRootEntry();
    }

    @Override
    public ConfigurationEntry getEntry(UUID id)
    {
        return _userStore.getEntry(id);
    }

    @Override
    public void save(ConfigurationEntry... entries)
    {
        _userStore.save(entries);
    }

    @Override
    public UUID[] remove(UUID... entryIds)
    {
        return _userStore.remove(entryIds);
    }

    private void mergeAndSave(ConfigurationEntryStore userStore, ConfigurationEntryStore masterStore)
    {
        ConfigurationEntry masterRoot = masterStore.getRootEntry();
        Set<UUID> masterRootChildren = masterRoot.getChildrenIds();

        ConfigurationEntry userRoot = userStore.getRootEntry();
        Map<String, Collection<ConfigurationEntry>> userRootChildren = userRoot.getChildren();

        List<ConfigurationEntry> entriesToSave = new ArrayList<ConfigurationEntry>();
        Set<UUID> userRootNewChildren = new HashSet<UUID>();
        for (UUID uuid : masterRootChildren)
        {
            ConfigurationEntry masterEntry = masterStore.getEntry(uuid);
            String masterEntryName = (String) masterEntry.getAttributes().get(ConfigurationEntry.ATTRIBUTE_NAME);
            Collection<ConfigurationEntry> userEntriesOfTheSameType = userRootChildren.get(masterEntry.getType().toString());
            boolean found = false;
            if (userEntriesOfTheSameType != null && !userEntriesOfTheSameType.isEmpty())
            {
                for (ConfigurationEntry entry : userEntriesOfTheSameType)
                {
                    Map<String, Object> attributes = entry.getAttributes();
                    if (attributes != null && masterEntryName.equals(attributes.get(ConfigurationEntry.ATTRIBUTE_NAME)))
                    {
                        found = true;
                        break;
                    }
                }
            }
            if (!found)
            {
                entriesToSave.add(masterEntry);
                userRootNewChildren.add(masterEntry.getId());
            }
        }

        Map<String, Object> userRootAttributes = userRoot.getAttributes();
        boolean noUserStoreRootAttributes = userRootAttributes == null || userRootAttributes.isEmpty();
        if (noUserStoreRootAttributes || !userRootNewChildren.isEmpty())
        {
            Set<UUID> currentUserStoreRootChildrenIds = userRoot.getChildrenIds();
            if (currentUserStoreRootChildrenIds != null)
            {
                userRootNewChildren.addAll(currentUserStoreRootChildrenIds);
            }
            Map<String, Object> newAttributes = noUserStoreRootAttributes ? masterRoot.getAttributes() : userRootAttributes;
            entriesToSave.add(new ConfigurationEntry(userRoot.getId(), userRoot.getType(), newAttributes, userRootNewChildren,
                    userStore));
        }
        if (!entriesToSave.isEmpty())
        {
            userStore.save(entriesToSave.toArray(new ConfigurationEntry[entriesToSave.size()]));
        }
    }

}
