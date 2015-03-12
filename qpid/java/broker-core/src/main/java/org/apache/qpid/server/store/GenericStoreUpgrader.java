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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;

public class GenericStoreUpgrader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GenericStoreUpgrader.class);

    private final Map<UUID, ConfiguredObjectRecord> _records = new HashMap<UUID, ConfiguredObjectRecord>();
    private final Map<String, StoreUpgraderPhase> _upgraders;
    private final DurableConfigurationStore _store;
    private final String _rootCategory;
    private final String _modelVersionAttributeName;

    public GenericStoreUpgrader(String rootCategory, String rootModelVersionAttributeName, DurableConfigurationStore configurationStore, Map<String, StoreUpgraderPhase> upgraders)
    {
        super();
        _upgraders = upgraders;
        _store = configurationStore;
        _rootCategory = rootCategory;
        _modelVersionAttributeName = rootModelVersionAttributeName;
    }


    public List<ConfiguredObjectRecord> getRecords()
    {
        return new ArrayList<ConfiguredObjectRecord>(_records.values());
    }

    public void upgrade()
    {
        ConfiguredObjectRecordHandler handler = new ConfiguredObjectRecordHandler()
        {
            @Override
            public void begin()
            {
            }

            @Override
            public boolean handle(final ConfiguredObjectRecord record)
            {
                _records.put(record.getId(), record);
                return true;
            }

            @Override
            public void end()
            {
                performUpgrade();
            }
        };

        _store.visitConfiguredObjectRecords(handler);
    }

    private void performUpgrade()
    {
        String version = getCurrentVersion();

        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info(_rootCategory + " store has model version " + version + ". Number of record(s) " + _records.size());
        }

        DurableConfigurationStoreUpgrader upgrader = buildUpgraderChain(version);

        for(ConfiguredObjectRecord record : _records.values())
        {
            upgrader.configuredObject(record);
        }

        upgrader.complete();

        Map<UUID, ConfiguredObjectRecord> deletedRecords = upgrader.getDeletedRecords();
        Map<UUID, ConfiguredObjectRecord> updatedRecords = upgrader.getUpdatedRecords();

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug(_rootCategory + " store upgrade is about to complete. " + _records.size() + " total record(s)."
                    + " Records to update " + updatedRecords.size()
                    + " Records to delete " + deletedRecords.size());
        }

        _store.update(true, updatedRecords.values().toArray(new ConfiguredObjectRecord[updatedRecords.size()]));
        _store.remove(deletedRecords.values().toArray(new ConfiguredObjectRecord[deletedRecords.size()]));

        _records.keySet().removeAll(deletedRecords.keySet());
        _records.putAll(updatedRecords);
    }

    private DurableConfigurationStoreUpgrader buildUpgraderChain(String version)
    {
        DurableConfigurationStoreUpgrader head = null;
        while(!BrokerModel.MODEL_VERSION.equals(version))
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Adding " + _rootCategory + " store upgrader from model version: " + version);
            }

            StoreUpgraderPhase upgrader = _upgraders.get(version);
            if (upgrader == null)
            {
                throw new IllegalConfigurationException("No phase upgrader for version " + version);
            }

            if(head == null)
            {
                head = upgrader;
            }
            else
            {
                head.setNextUpgrader(upgrader);
            }
            version = upgrader.getToVersion();
        }

        if(head == null)
        {
            head = new NullUpgrader();
        }
        else
        {
            head.setNextUpgrader(new NullUpgrader());
        }

        return head;
    }

    private String getCurrentVersion()
    {
        for(ConfiguredObjectRecord record : _records.values())
        {
            if(_rootCategory.equals(record.getType()))
            {
                return (String) record.getAttributes().get(_modelVersionAttributeName);
            }
        }
        return BrokerModel.MODEL_VERSION;
    }
}
