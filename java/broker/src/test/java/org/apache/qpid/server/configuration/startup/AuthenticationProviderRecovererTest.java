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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.configuration.store.StoreConfigurationChangeListener;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.adapter.AuthenticationProviderFactory;
import org.apache.qpid.server.model.adapter.FileSystemPreferencesProvider;
import org.apache.qpid.server.model.adapter.PreferencesProviderCreator;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManagerFactory;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;

public class AuthenticationProviderRecovererTest extends QpidTestCase
{
    private Broker _broker;
    private AuthenticationProviderRecoverer _recoverer;
    private ConfigurationEntryStore _configurationStore;
    private PreferencesProviderCreator _preferencesProviderCreator;

    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _broker = BrokerTestHelper.createBrokerMock();
        _preferencesProviderCreator = new PreferencesProviderCreator();
        QpidServiceLoader<AuthenticationManagerFactory> serviceLoader = new QpidServiceLoader<AuthenticationManagerFactory>();
        AuthenticationProviderFactory authenticationProviderFactory = new AuthenticationProviderFactory(serviceLoader, _preferencesProviderCreator);
        StoreConfigurationChangeListener storeChangeListener = mock(StoreConfigurationChangeListener.class);
        _recoverer = new AuthenticationProviderRecoverer(authenticationProviderFactory, storeChangeListener);
        _configurationStore = mock(ConfigurationEntryStore.class);
    }

    public void tearDown() throws Exception
    {
        try
        {
            BrokerTestHelper.tearDown();
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testRecoverAuthenticationProviderWithPreferencesProvider()
    {
        Map<String, Object> authenticationAttributes = new HashMap<String, Object>();
        authenticationAttributes.put(AuthenticationManagerFactory.ATTRIBUTE_TYPE, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE);
        authenticationAttributes.put(AuthenticationProvider.NAME, "test-authenticator");
        UUID authenticationId = UUID.randomUUID();

        final PreferencesProviderRecoverer preferencesRecoverer = new PreferencesProviderRecoverer(_preferencesProviderCreator);
        RecovererProvider recovererProvider = new RecovererProvider()
        {
            @Override
            public ConfiguredObjectRecoverer<? extends ConfiguredObject> getRecoverer(String type)
            {
                return preferencesRecoverer;
            }
        };

        Map<String, Object> preferencesAttributes = new HashMap<String, Object>();
        UUID preferencesId = UUID.randomUUID();
        preferencesAttributes.put(PreferencesProvider.TYPE, FileSystemPreferencesProvider.class);
        preferencesAttributes.put(PreferencesProvider.NAME, "test-provider");
        File file = TestFileUtils.createTempFile(this, ".prefs.json", "{\"test_user\":{\"pref1\": \"pref1Value\", \"pref2\": 1.0} }");
        preferencesAttributes.put(FileSystemPreferencesProvider.PATH, file.getAbsolutePath());
        ConfigurationEntry preferencesEntry = new ConfigurationEntry(preferencesId, PreferencesProvider.class.getSimpleName(), preferencesAttributes, Collections.<UUID>emptySet(), _configurationStore);
        when(_configurationStore.getEntry(preferencesId)).thenReturn(preferencesEntry);

        ConfigurationEntry authenticationProviderEntry = new ConfigurationEntry(authenticationId, AuthenticationProvider.class.getSimpleName(), authenticationAttributes, Collections.singleton(preferencesId), _configurationStore);
        try
        {
            AuthenticationProvider authenticationProvider = _recoverer.create(recovererProvider, authenticationProviderEntry, _broker);
            assertNotNull("Authentication provider was not recovered", authenticationProvider);
            assertEquals("Unexpected name", "test-authenticator", authenticationProvider.getName());
            assertEquals("Unexpected id", authenticationId, authenticationProvider.getId());
            PreferencesProvider preferencesProvider = authenticationProvider.getPreferencesProvider();
            assertNotNull("Preferences provider was not recovered", preferencesProvider);
            assertEquals("Unexpected path", file.getAbsolutePath(), preferencesProvider.getAttribute(FileSystemPreferencesProvider.PATH));
        }
        finally
        {
            file.delete();
        }
    }

}
