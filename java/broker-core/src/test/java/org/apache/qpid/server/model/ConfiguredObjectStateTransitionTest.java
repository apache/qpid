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
package org.apache.qpid.server.model;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.configuration.startup.DefaultRecovererProvider;
import org.apache.qpid.server.configuration.store.StoreConfigurationChangeListener;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManagerFactory;
import org.apache.qpid.server.security.group.FileGroupManagerFactory;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;

public class ConfiguredObjectStateTransitionTest extends QpidTestCase
{
    private Broker _broker;
    private RecovererProvider _recovererProvider;
    private ConfigurationEntryStore _store;
    private File _resourceToDelete;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();

        _broker = BrokerTestHelper.createBrokerMock();
        StatisticsGatherer statisticsGatherer = mock(StatisticsGatherer.class);
        TaskExecutor executor = mock(TaskExecutor.class);
        when(executor.isTaskExecutorThread()).thenReturn(true);
        when(_broker.getTaskExecutor()).thenReturn(executor);

        _recovererProvider = new DefaultRecovererProvider(statisticsGatherer, _broker.getVirtualHostRegistry(),
                _broker.getLogRecorder(), _broker.getRootMessageLogger(), executor, new BrokerOptions(), mock(StoreConfigurationChangeListener.class));

        _store = mock(ConfigurationEntryStore.class);

        _resourceToDelete = new File(TMP_FOLDER, getTestName());
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            BrokerTestHelper.tearDown();
            if (_resourceToDelete.exists())
            {
                _resourceToDelete.delete();
            }
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testGroupProviderValidStateTransitions() throws Exception
    {
        ConfigurationEntry providerEntry = getGroupProviderConfigurationEntry();
        ConfiguredObject provider = createConfiguredObject(providerEntry);
        provider.setDesiredState(State.INITIALISING, State.QUIESCED);
        assertValidStateTransition(provider, State.QUIESCED, State.STOPPED);

        provider = createConfiguredObject(providerEntry);
        assertValidStateTransition(provider, State.INITIALISING, State.DELETED);

        providerEntry = getGroupProviderConfigurationEntry();
        provider = createConfiguredObject(providerEntry);
        provider.setDesiredState(State.INITIALISING, State.QUIESCED);
        assertValidStateTransition(provider, State.QUIESCED, State.DELETED);

        providerEntry = getGroupProviderConfigurationEntry();
        provider = createConfiguredObject(providerEntry);
        provider.setDesiredState(State.INITIALISING, State.ACTIVE);
        assertValidStateTransition(provider, State.ACTIVE, State.DELETED);
    }

    public void testGroupProviderInvalidStateTransitions() throws Exception
    {
        ConfigurationEntry providerEntry = getGroupProviderConfigurationEntry();
        assertAllInvalidStateTransitions(providerEntry);
    }

    public void testAuthenticationProviderValidStateTransitions()
    {
        ConfigurationEntry providerEntry = getAuthenticationProviderConfigurationEntry();
        assertAllValidStateTransitions(providerEntry);
    }

    public void testAuthenticationProviderInvalidStateTransitions()
    {
        ConfigurationEntry providerEntry = getAuthenticationProviderConfigurationEntry();
        assertAllInvalidStateTransitions(providerEntry);
    }

    public void testPortValidStateTransitions()
    {
        ConfigurationEntry providerEntry = getPortConfigurationEntry();
        assertAllValidStateTransitions(providerEntry);
    }

    public void testPortInvalidStateTransitions()
    {
        ConfigurationEntry providerEntry = getPortConfigurationEntry();
        assertAllInvalidStateTransitions(providerEntry);
    }

    private void assertAllInvalidStateTransitions(ConfigurationEntry providerEntry)
    {
        ConfiguredObject provider = createConfiguredObject(providerEntry);
        assertInvalidStateTransition(provider, State.INITIALISING, State.REPLICA);

        provider.setDesiredState(State.INITIALISING, State.QUIESCED);
        assertInvalidStateTransition(provider, State.QUIESCED, State.INITIALISING);

        provider.setDesiredState(State.QUIESCED, State.ACTIVE);
        assertInvalidStateTransition(provider, State.ACTIVE, State.INITIALISING);

        provider.setDesiredState(State.ACTIVE, State.DELETED);
        assertInvalidStateTransition(provider, State.DELETED, State.INITIALISING);
        assertInvalidStateTransition(provider, State.DELETED, State.QUIESCED);
        assertInvalidStateTransition(provider, State.DELETED, State.ACTIVE);
        assertInvalidStateTransition(provider, State.DELETED, State.REPLICA);
        assertInvalidStateTransition(provider, State.DELETED, State.ERRORED);
    }

    private void assertAllValidStateTransitions(ConfigurationEntry providerEntry)
    {
        ConfiguredObject provider = createConfiguredObject(providerEntry);
        assertNormalStateTransition(provider);

        provider = createConfiguredObject(providerEntry);
        provider.setDesiredState(State.INITIALISING, State.QUIESCED);
        assertValidStateTransition(provider, State.QUIESCED, State.STOPPED);

        provider = createConfiguredObject(providerEntry);
        assertValidStateTransition(provider, State.INITIALISING, State.DELETED);

        provider = createConfiguredObject(providerEntry);
        provider.setDesiredState(State.INITIALISING, State.QUIESCED);
        assertValidStateTransition(provider, State.QUIESCED, State.DELETED);

        provider = createConfiguredObject(providerEntry);
        provider.setDesiredState(State.INITIALISING, State.ACTIVE);
        assertValidStateTransition(provider, State.ACTIVE, State.DELETED);
    }

    private void assertInvalidStateTransition(ConfiguredObject object, State initialState, State... invalidStates)
    {
        assertEquals("Unexpected state", initialState, object.getActualState());
        for (State state : invalidStates)
        {
            try
            {
                object.setDesiredState(initialState, state);
            }
            catch (IllegalStateException e)
            {
                // expected
            }
            assertEquals("Transition from state " + initialState + " into state " + state + " did occur", initialState,
                    object.getActualState());
        }
    }

    private void assertValidStateTransition(ConfiguredObject object, State initialState, State... validStateSequence)
    {
        assertEquals("Unexpected state", initialState, object.getActualState());
        State currentState = initialState;
        for (State state : validStateSequence)
        {
            object.setDesiredState(currentState, state);
            assertEquals("Transition from state " + currentState + " into state " + state + " did not occur", state,
                    object.getActualState());
            currentState = state;
        }
    }

    private void assertNormalStateTransition(ConfiguredObject object)
    {
        assertValidStateTransition(object, State.INITIALISING, State.QUIESCED, State.ACTIVE, State.STOPPED, State.DELETED);
    }

    private ConfiguredObject createConfiguredObject(ConfigurationEntry entry)
    {
        @SuppressWarnings("unchecked")
        ConfiguredObjectRecoverer<ConfiguredObject> recoverer =
                (ConfiguredObjectRecoverer<ConfiguredObject>)_recovererProvider.getRecoverer(entry.getType());
        return recoverer.create(_recovererProvider, entry, _broker);
    }

    private ConfigurationEntry createConfigurationEntry(String type, Map<String, Object> attributes, ConfigurationEntryStore store)
    {
        return new ConfigurationEntry(UUID.randomUUID(), type, attributes, Collections.<UUID>emptySet(), store);
    }

    private ConfigurationEntry getAuthenticationProviderConfigurationEntry()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, getTestName());
        attributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE);
        return createConfigurationEntry(AuthenticationProvider.class.getSimpleName(), attributes , _store);
    }

    private ConfigurationEntry getGroupProviderConfigurationEntry() throws Exception
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(GroupProvider.NAME, getTestName());
        attributes.put(GroupProvider.TYPE, FileGroupManagerFactory.GROUP_FILE_PROVIDER_TYPE);
        attributes.put(FileGroupManagerFactory.PATH, _resourceToDelete.getAbsolutePath());
        if (!_resourceToDelete.exists())
        {
            _resourceToDelete.createNewFile();
        }
        return createConfigurationEntry(GroupProvider.class.getSimpleName(), attributes , _store);
    }

    private ConfigurationEntry getPortConfigurationEntry()
    {
        ConfigurationEntry authProviderEntry = getAuthenticationProviderConfigurationEntry();
        AuthenticationProvider authProvider = (AuthenticationProvider)createConfiguredObject(authProviderEntry);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.NAME, getTestName());
        attributes.put(Port.PROTOCOLS, Collections.<Protocol>singleton(Protocol.HTTP));
        attributes.put(Port.AUTHENTICATION_PROVIDER, authProvider.getName());
        attributes.put(Port.PORT, 0);

        when(_broker.findAuthenticationProviderByName(authProvider.getName())).thenReturn(authProvider);
        return createConfigurationEntry(Port.class.getSimpleName(), attributes , _store);
    }

}
