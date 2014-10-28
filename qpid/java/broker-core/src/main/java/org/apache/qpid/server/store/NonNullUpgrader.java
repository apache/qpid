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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class NonNullUpgrader implements DurableConfigurationStoreUpgrader
{
    private DurableConfigurationStoreUpgrader _nextUpgrader;
    private final Map<UUID, ConfiguredObjectRecord> _updates = new HashMap<UUID, ConfiguredObjectRecord>();
    private final Map<UUID, ConfiguredObjectRecord> _deletes = new HashMap<UUID, ConfiguredObjectRecord>();

    public final void setNextUpgrader(final DurableConfigurationStoreUpgrader upgrader)
    {
        if(_nextUpgrader == null)
        {
            _nextUpgrader = upgrader;
        }
        else
        {
            _nextUpgrader.setNextUpgrader(upgrader);
        }
    }

    protected DurableConfigurationStoreUpgrader getNextUpgrader()
    {
        return _nextUpgrader;
    }

    protected Map<UUID, ConfiguredObjectRecord> getUpdateMap()
    {
        return _updates;
    }
    protected Map<UUID, ConfiguredObjectRecord> getDeleteMap()
    {
        return _deletes;
    }

    @Override
    public final Map<UUID, ConfiguredObjectRecord> getUpdatedRecords()
    {
        final Map<UUID, ConfiguredObjectRecord> updates = new HashMap<UUID, ConfiguredObjectRecord>(_updates);
        updates.putAll(_nextUpgrader.getUpdatedRecords());
        updates.keySet().removeAll(getDeletedRecords().keySet());
        return updates;
    }

    @Override
    public final Map<UUID, ConfiguredObjectRecord> getDeletedRecords()
    {
        final Map<UUID, ConfiguredObjectRecord> deletes = new HashMap<UUID, ConfiguredObjectRecord>(_deletes);
        deletes.putAll(_nextUpgrader.getDeletedRecords());
        return deletes;
    }
}
