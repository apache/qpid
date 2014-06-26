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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;

public class JsonConfigurationEntryStore extends MemoryConfigurationEntryStore
{
    public static final String STORE_TYPE = "json";
    private final ConfiguredObject<?> _parentObject;

    private File _storeFile;

    public JsonConfigurationEntryStore(ConfiguredObject<?> parentObject, DurableConfigurationStore initialStore, boolean overwrite, Map<String, String> configProperties)
    {
        super(parentObject, configProperties);
        _parentObject = parentObject;
        String storeLocation = (String) parentObject.getAttribute("storePath");
        _storeFile = new File(storeLocation);

        if(_storeFile.isDirectory())
        {
            throw new IllegalConfigurationException("A directory exists at the location for the broker configuration store file: " + storeLocation);
        }

        if(overwrite && _storeFile.exists())
        {
            if(!_storeFile.delete())
            {
                throw new StoreException("Unable to overwrite existing configuration store file as requested: " + storeLocation);
            }
        }

        if ((!_storeFile.exists() || _storeFile.length() == 0))
        {
           initialiseStore(_storeFile, initialStore);
        }
        else
        {
            load(getConfigurationEntryStoreUtil().fileToURL(_storeFile));
        }
        if(isGeneratedObjectIdDuringLoad())
        {
            saveAsTree(_storeFile);
        }
    }

    @Override
    public synchronized UUID[] remove(final ConfiguredObjectRecord... records)
    {
        UUID[] removedIds = super.remove(records);
        if (removedIds.length > 0)
        {
            saveAsTree(_storeFile);
        }
        return removedIds;
    }

    @Override
    public synchronized void save(ConfigurationEntry... entries)
    {
        if (replaceEntries(entries))
        {
            saveAsTree(_storeFile);
        }
    }

    @Override
    public String getStoreLocation()
    {
        return _storeFile.getAbsolutePath();
    }

    @Override
    public String getType()
    {
        return STORE_TYPE;
    }

    @Override
    public String toString()
    {
        return "JsonConfigurationEntryStore [_storeFile=" + _storeFile + ", _rootId=" + getRootEntry().getId() + "]";
    }

    private void initialiseStore(File storeFile, DurableConfigurationStore initialStore)
    {
        createFileIfNotExist(storeFile);
        if (initialStore == null)
        {
           throw new IllegalConfigurationException("Cannot create new store without an initial store");
        }
        else
        {
            final Collection<ConfiguredObjectRecord> records = new ArrayList<ConfiguredObjectRecord>();
            final ConfiguredObjectRecordHandler replayHandler = new ConfiguredObjectRecordHandler()
            {
                @Override
                public void begin()
                {
                }

                @Override
                public boolean handle(ConfiguredObjectRecord record)
                {
                    records.add(record);
                    return true;
                }

                @Override
                public void end()
                {
                }
            };

            initialStore.openConfigurationStore(_parentObject);
            initialStore.visitConfiguredObjectRecords(replayHandler);

            update(true, records.toArray(new ConfiguredObjectRecord[records.size()]));
        }
    }

}
