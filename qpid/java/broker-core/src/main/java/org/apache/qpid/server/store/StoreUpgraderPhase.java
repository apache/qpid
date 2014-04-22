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

public abstract class StoreUpgraderPhase extends NonNullUpgrader
{
    private final String _fromVersion;
    private final String _toVersion;
    private final String _versionAttributeName;

    public StoreUpgraderPhase(String versionAttributeName, String fromVersion, String toVersion)
    {
        _toVersion = toVersion;
        _fromVersion = fromVersion;
        _versionAttributeName = versionAttributeName;
    }

    protected ConfiguredObjectRecord upgradeRootRecord(ConfiguredObjectRecord record)
    {
        Map<String, Object> updatedAttributes = new HashMap<String, Object>(record.getAttributes());
        updatedAttributes.put(_versionAttributeName, _toVersion);
        record = new ConfiguredObjectRecordImpl(record.getId(), record.getType(), updatedAttributes, record.getParents());
        getUpdateMap().put(record.getId(), record);
        return record;
    }

    public String getFromVersion()
    {
        return _fromVersion;
    }

    public String getToVersion()
    {
        return _toVersion;
    }

}