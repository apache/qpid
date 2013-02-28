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

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.configuration.startup.DefaultRecovererProvider;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.security.auth.manager.PlainPasswordFileAuthenticationManagerFactory;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;

import java.io.File;
import java.security.Provider;
import java.security.Security;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * QPID-1390 : Test to validate that the AuthenticationManger can successfully unregister any new SASL providers when
 * the broker is stopped.
 */
public class BrokerShutdownTest extends QpidTestCase
{
    private Provider[] _defaultProviders;
    private Broker _broker;
    private TaskExecutor _taskExecutor;

    @Override
    public void setUp() throws Exception
    {
        // Get default providers
        _defaultProviders = Security.getProviders();

        super.setUp();

        _taskExecutor = new TaskExecutor();
        _taskExecutor.start();

        // Startup the new broker and register the new providers
        _broker = startBroker();
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            if (_taskExecutor != null)
            {
                _taskExecutor.stopImmediately();
            }
        }

    }

    private Broker startBroker() throws Exception
    {
        ConfigurationEntryStore store = mock(ConfigurationEntryStore.class);
        UUID brokerId = UUID.randomUUID();
        UUID authenticationProviderId = UUID.randomUUID();

        ConfigurationEntry root = new ConfigurationEntry(brokerId, Broker.class.getSimpleName(), Collections.<String, Object> emptyMap(),
                Collections.singleton(authenticationProviderId), store);

        File file = TestFileUtils.createTempFile(BrokerShutdownTest.this, ".db.users");
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationManagerFactory.ATTRIBUTE_TYPE, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);
        attributes.put(PlainPasswordFileAuthenticationManagerFactory.ATTRIBUTE_PATH, file.getAbsolutePath());
        ConfigurationEntry authenticationProviderEntry = new ConfigurationEntry(authenticationProviderId, AuthenticationProvider.class.getSimpleName(), attributes,
                Collections.<UUID> emptySet(), store);

        when(store.getRootEntry()).thenReturn(root);
        when(store.getEntry(brokerId)).thenReturn(root);
        when(store.getEntry(authenticationProviderId)).thenReturn(authenticationProviderEntry);

        // mocking the required object
        StatisticsGatherer statisticsGatherer = mock(StatisticsGatherer.class);
        VirtualHostRegistry virtualHostRegistry = mock(VirtualHostRegistry.class);
        LogRecorder logRecorder = mock(LogRecorder.class);
        RootMessageLogger rootMessageLogger = mock(RootMessageLogger.class);

        // recover the broker from the store
        RecovererProvider provider = new DefaultRecovererProvider(statisticsGatherer, virtualHostRegistry, logRecorder, rootMessageLogger, _taskExecutor);
        ConfiguredObjectRecoverer<? extends ConfiguredObject> brokerRecoverer = provider.getRecoverer(Broker.class.getSimpleName());

        Broker broker = (Broker) brokerRecoverer.create(provider, store.getRootEntry());

        // start broker
        broker.setDesiredState(State.INITIALISING, State.ACTIVE);
        return broker;
    }

    private void stopBroker()
    {
        _broker.setDesiredState(State.ACTIVE, State.STOPPED);
    }

    /**
     * QPID-1399 : Ensure that the Authentication manager unregisters any SASL providers created during
     * broker start-up.
     *
     */
    public void testAuthenticationMangerCleansUp() throws Exception
    {

        // Get the providers after initialisation
        Provider[] providersAfterInitialisation = Security.getProviders();

        // Find the additions
        List<Provider> additions = new LinkedList<Provider>();
        for (Provider afterInit : providersAfterInitialisation)
        {
            boolean found = false;
            for (Provider defaultProvider : _defaultProviders)
            {
                if (defaultProvider == afterInit)
                {
                    found = true;
                    break;
                }
            }

            // Record added registies
            if (!found)
            {
                additions.add(afterInit);
            }
        }

        assertFalse("No new SASL mechanisms added by initialisation.", additions.isEmpty());

        // Close the registry which will perform the close the
        // AuthenticationManager
        stopBroker();

        // Validate that the SASL plugins have been removed.
        Provider[] providersAfterClose = Security.getProviders();

        assertTrue("No providers unregistered", providersAfterInitialisation.length > providersAfterClose.length);

        // Ensure that the additions are not still present after close().
        for (Provider afterClose : providersAfterClose)
        {
            assertFalse("Added provider not unregistered", additions.contains(afterClose));
        }
    }

}
