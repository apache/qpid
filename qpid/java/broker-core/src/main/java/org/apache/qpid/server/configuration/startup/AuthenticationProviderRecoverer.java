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

import java.util.Collection;
import java.util.Map;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.configuration.store.StoreConfigurationChangeListener;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.model.adapter.AuthenticationProviderFactory;

public class AuthenticationProviderRecoverer implements ConfiguredObjectRecoverer<AuthenticationProvider>
{
    private final AuthenticationProviderFactory _authenticationProviderFactory;
    private final StoreConfigurationChangeListener _storeChangeListener;

    public AuthenticationProviderRecoverer(AuthenticationProviderFactory authenticationProviderFactory,  StoreConfigurationChangeListener storeChangeListener)
    {
        _authenticationProviderFactory = authenticationProviderFactory;
        _storeChangeListener = storeChangeListener;
    }

    @Override
    public AuthenticationProvider create(RecovererProvider recovererProvider, ConfigurationEntry configurationEntry, ConfiguredObject... parents)
    {
        Broker broker = RecovererHelper.verifyOnlyBrokerIsParent(parents);
        Map<String, Object> attributes = configurationEntry.getAttributes();
        AuthenticationProvider authenticationProvider = _authenticationProviderFactory.recover(configurationEntry.getId(), attributes, broker);

        Map<String, Collection<ConfigurationEntry>> childEntries = configurationEntry.getChildren();

        for (String type : childEntries.keySet())
        {
            recoverType(recovererProvider, _storeChangeListener, authenticationProvider, childEntries, type);
        }

        return authenticationProvider;
    }

    private void recoverType(RecovererProvider recovererProvider,
            StoreConfigurationChangeListener storeChangeListener,
            AuthenticationProvider authenticationProvider,
            Map<String, Collection<ConfigurationEntry>> childEntries,
            String type)
    {
        ConfiguredObjectRecoverer<?> recoverer = null;

        if(authenticationProvider instanceof RecovererProvider)
        {
            recoverer = ((RecovererProvider)authenticationProvider).getRecoverer(type);
        }

        if(recoverer == null)
        {
            recoverer = recovererProvider.getRecoverer(type);
        }

        if (recoverer == null)
        {
            if(authenticationProvider instanceof RecovererProvider)
            {
                ((RecovererProvider)authenticationProvider).getRecoverer(type);
            }
            throw new IllegalConfigurationException("Cannot recover entry for the type '" + type + "' from broker");
        }
        Collection<ConfigurationEntry> entries = childEntries.get(type);
        for (ConfigurationEntry childEntry : entries)
        {
            ConfiguredObject object = recoverer.create(recovererProvider, childEntry, authenticationProvider);
            if (object == null)
            {
                throw new IllegalConfigurationException("Cannot create configured object for the entry " + childEntry);
            }
            if (object instanceof PreferencesProvider)
            {
                authenticationProvider.setPreferencesProvider((PreferencesProvider)object);
            }
            else if(object instanceof User)
            {
                authenticationProvider.recoverUser((User)object);
            }
            else
            {
                throw new IllegalConfigurationException("Cannot associate  " + object + " with authentication provider " + authenticationProvider);
            }
            object.addChangeListener(storeChangeListener);
        }
    }
}
