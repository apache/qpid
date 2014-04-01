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
package org.apache.qpid.server.configuration.startup;

import java.util.Map;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.plugin.PreferencesProviderFactory;
import org.apache.qpid.server.util.MapValueConverter;

public class PreferencesProviderRecoverer implements ConfiguredObjectRecoverer<PreferencesProvider>
{

    @Override
    public PreferencesProvider create(RecovererProvider recovererProvider, ConfigurationEntry entry,
            ConfiguredObject... parents)
    {
        AuthenticationProvider authenticationProvider = RecovererHelper.verifyOnlyParentIsOfType(AuthenticationProvider.class, parents);
        Map<String, Object> attributes = entry.getAttributes();
        String type = MapValueConverter.getStringAttribute(PreferencesProvider.TYPE, attributes);
        PreferencesProviderFactory factory = PreferencesProviderFactory.FACTORY_LOADER.get(type);
        return factory.createInstance(entry.getId(), attributes, authenticationProvider);
    }

}
