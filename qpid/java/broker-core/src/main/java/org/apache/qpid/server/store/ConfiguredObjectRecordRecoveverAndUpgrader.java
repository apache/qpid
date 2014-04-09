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

import java.util.Map;

import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.server.virtualhost.DefaultUpgraderProvider;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class ConfiguredObjectRecordRecoveverAndUpgrader implements ConfiguredObjectRecordHandler
{
    private DurableConfigurationRecoverer _configRecoverer;
    private DurableConfigurationStore _store;

    public ConfiguredObjectRecordRecoveverAndUpgrader(VirtualHostImpl virtualHost, Map<String, DurableConfiguredObjectRecoverer> recoverers)
    {
        DefaultUpgraderProvider upgraderProvider = new DefaultUpgraderProvider(virtualHost);
        _configRecoverer = new DurableConfigurationRecoverer(virtualHost.getName(), recoverers, upgraderProvider, virtualHost.getEventLogger());
        _store = virtualHost.getDurableConfigurationStore();
    }

    @Override
    public void begin(int configVersion)
    {
        _configRecoverer.beginConfigurationRecovery(_store, configVersion);
    }

    @Override
    public boolean handle(ConfiguredObjectRecord record)
    {
        _configRecoverer.configuredObject(record);
        return true;
    }

    @Override
    public int end()
    {
        return _configRecoverer.completeConfigurationRecovery();
    }

}
