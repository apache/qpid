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

package org.apache.qpid.server.model.adapter;

import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.plugin.PreferencesProviderFactory;

public class FileSystemPreferencesProviderFactory implements PreferencesProviderFactory
{

    @Override
    public String getType()
    {
        return FileSystemPreferencesProvider.PROVIDER_TYPE;
    }

    @Override
    public PreferencesProvider createInstance(UUID id, Map<String, Object> attributes,
            AuthenticationProvider authenticationProvider)
    {
        Broker broker = authenticationProvider.getParent(Broker.class);
        FileSystemPreferencesProvider provider = new FileSystemPreferencesProvider(id, attributes, authenticationProvider, broker.getTaskExecutor());

        // create store if such does not exist
        provider.createStoreIfNotExist();
        return provider;
    }

}
