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
package org.apache.qpid.server.store;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;

public abstract class AbstractMemoryStore implements DurableConfigurationStore, MessageStoreProvider
{
    private final MessageStore _messageStore = new MemoryMessageStore();
    private final Class<? extends ConfiguredObject> _rootClass;


    private final ConcurrentMap<UUID, ConfiguredObjectRecord> _configuredObjectRecords = new ConcurrentHashMap<UUID, ConfiguredObjectRecord>();

    protected AbstractMemoryStore(final Class<? extends ConfiguredObject> rootClass)
    {
        _rootClass = rootClass;
    }

    @Override
    public void create(ConfiguredObjectRecord record)
    {
        if (_configuredObjectRecords.putIfAbsent(record.getId(), record) != null)
        {
            throw new StoreException("Record with id " + record.getId() + " is already present");
        }
    }

    @Override
    public void update(boolean createIfNecessary, ConfiguredObjectRecord... records)
    {
        for (ConfiguredObjectRecord record : records)
        {
            if(createIfNecessary)
            {
                _configuredObjectRecords.put(record.getId(), record);
            }
            else
            {
                ConfiguredObjectRecord previousValue = _configuredObjectRecords.replace(record.getId(), record);
                if (previousValue == null)
                {
                    throw new StoreException("Record with id " + record.getId() + " does not exist");
                }
            }
        }
    }

    @Override
    public UUID[] remove(final ConfiguredObjectRecord... objects)
    {
        List<UUID> removed = new ArrayList<UUID>();
        for (ConfiguredObjectRecord record : objects)
        {
            if (_configuredObjectRecords.remove(record.getId()) != null)
            {
                removed.add(record.getId());
            }
        }
        return removed.toArray(new UUID[removed.size()]);
    }

    @Override
    public void openConfigurationStore(ConfiguredObject<?> parent,
                                       final boolean overwrite,
                                       final ConfiguredObjectRecord... initialRecords)
    {
        for(ConfiguredObjectRecord record : initialRecords)
        {
            _configuredObjectRecords.put(record.getId(), record);
        }
    }

    @Override
    public void upgradeStoreStructure() throws StoreException
    {

    }

    @Override
    public void closeConfigurationStore()
    {
        _configuredObjectRecords.clear();
    }


    @Override
    public void visitConfiguredObjectRecords(ConfiguredObjectRecordHandler handler) throws StoreException
    {
        handler.begin();
        for (ConfiguredObjectRecord record : _configuredObjectRecords.values())
        {
            if (!handler.handle(record))
            {
                break;
            }
        }
        handler.end();
    }

    @Override
    public MessageStore getMessageStore()
    {
        return _messageStore;
    }

    @Override
    public void onDelete()
    {
    }

}
