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

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public final class NullUpgrader implements DurableConfigurationStoreUpgrader
{

    @Override
    public void configuredObject(final ConfiguredObjectRecord record)
    {
    }

    @Override
    public void complete()
    {
    }

    @Override
    public void setNextUpgrader(final DurableConfigurationStoreUpgrader upgrader)
    {
        throw new UnsupportedOperationException("NullUpgrader must always be the last upgrader");
    }

    @Override
    public Map<UUID, ConfiguredObjectRecord> getUpdatedRecords()
    {
        return Collections.emptyMap();
    }

    @Override
    public Map<UUID, ConfiguredObjectRecord> getDeletedRecords()
    {
        return Collections.emptyMap();
    }
}
