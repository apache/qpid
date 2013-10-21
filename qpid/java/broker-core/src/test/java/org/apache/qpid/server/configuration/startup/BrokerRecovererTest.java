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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.AccessControlProviderFactory;
import org.apache.qpid.server.model.adapter.AuthenticationProviderFactory;
import org.apache.qpid.server.model.adapter.GroupProviderFactory;
import org.apache.qpid.server.model.adapter.PortFactory;
import org.apache.qpid.server.configuration.store.StoreConfigurationChangeListener;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class BrokerRecovererTest extends TestCase
{
    private BrokerRecoverer _brokerRecoverer;
    private ConfigurationEntry _brokerEntry = mock(ConfigurationEntry.class);

    private UUID _brokerId = UUID.randomUUID();
    private Map<String, Collection<ConfigurationEntry>> _brokerEntryChildren = new HashMap<String, Collection<ConfigurationEntry>>();
    private ConfigurationEntry _authenticationProviderEntry1;
    private AuthenticationProvider _authenticationProvider1;
    private UUID _authenticationProvider1Id = UUID.randomUUID();

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _brokerRecoverer = new BrokerRecoverer(mock(AuthenticationProviderFactory.class), mock(GroupProviderFactory.class), mock(AccessControlProviderFactory.class), mock(PortFactory.class),
                mock(StatisticsGatherer.class), mock(VirtualHostRegistry.class), mock(LogRecorder.class), mock(RootMessageLogger.class), mock(TaskExecutor.class), mock(BrokerOptions.class),
                mock(StoreConfigurationChangeListener.class));
        when(_brokerEntry.getId()).thenReturn(_brokerId);
        when(_brokerEntry.getChildren()).thenReturn(_brokerEntryChildren);
        when(_brokerEntry.getAttributes()).thenReturn(Collections.<String, Object>singletonMap(Broker.MODEL_VERSION, Model.MODEL_VERSION));

        //Add a base AuthenticationProvider for all tests
        _authenticationProvider1 = mock(AuthenticationProvider.class);
        when(_authenticationProvider1.getName()).thenReturn("authenticationProvider1");
        when(_authenticationProvider1.getId()).thenReturn(_authenticationProvider1Id);
        _authenticationProviderEntry1 = mock(ConfigurationEntry.class);
        _brokerEntryChildren.put(AuthenticationProvider.class.getSimpleName(), Arrays.asList(_authenticationProviderEntry1));
    }

    public void testCreateBrokerAttributes()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Broker.DEFAULT_VIRTUAL_HOST, "test");
        attributes.put(Broker.QUEUE_ALERT_THRESHOLD_MESSAGE_AGE, 9l);
        attributes.put(Broker.QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES, 8l);
        attributes.put(Broker.QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES, 7l);
        attributes.put(Broker.QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE, 6l);
        attributes.put(Broker.QUEUE_ALERT_REPEAT_GAP, 5l);
        attributes.put(Broker.QUEUE_FLOW_CONTROL_SIZE_BYTES, 5l);
        attributes.put(Broker.QUEUE_FLOW_CONTROL_RESUME_SIZE_BYTES, 3l);
        attributes.put(Broker.QUEUE_MAXIMUM_DELIVERY_ATTEMPTS, 2);
        attributes.put(Broker.QUEUE_DEAD_LETTER_QUEUE_ENABLED, true);
        attributes.put(Broker.VIRTUALHOST_HOUSEKEEPING_CHECK_PERIOD, 1l);
        attributes.put(Broker.CONNECTION_SESSION_COUNT_LIMIT, 1000);
        attributes.put(Broker.CONNECTION_HEART_BEAT_DELAY, 2000);
        attributes.put(Broker.STATISTICS_REPORTING_PERIOD, 4000);
        attributes.put(Broker.STATISTICS_REPORTING_RESET_ENABLED, true);
        attributes.put(Broker.MODEL_VERSION, Model.MODEL_VERSION);

        Map<String, Object> entryAttributes = new HashMap<String, Object>();
        for (Map.Entry<String, Object> attribute : attributes.entrySet())
        {
            String value = convertToString(attribute.getValue());
            entryAttributes.put(attribute.getKey(), value);
        }

        when(_brokerEntry.getAttributes()).thenReturn(entryAttributes);

        final ConfigurationEntry virtualHostEntry = mock(ConfigurationEntry.class);
        String typeName = VirtualHost.class.getSimpleName();
        when(virtualHostEntry.getType()).thenReturn(typeName);
        _brokerEntryChildren.put(typeName, Arrays.asList(virtualHostEntry));
        final VirtualHost virtualHost = mock(VirtualHost.class);
        when(virtualHost.getName()).thenReturn("test");

        RecovererProvider recovererProvider = createRecoveryProvider(new ConfigurationEntry[] { virtualHostEntry, _authenticationProviderEntry1 },
                new ConfiguredObject[] { virtualHost, _authenticationProvider1 });
        Broker broker = _brokerRecoverer.create(recovererProvider, _brokerEntry);
        assertNotNull(broker);
        assertEquals(_brokerId, broker.getId());

        for (Map.Entry<String, Object> attribute : attributes.entrySet())
        {
            Object attributeValue = broker.getAttribute(attribute.getKey());
            assertEquals("Unexpected value of attribute '" + attribute.getKey() + "'", attribute.getValue(), attributeValue);
        }
    }

    public void testCreateBrokerWithVirtualHost()
    {
        final ConfigurationEntry virtualHostEntry = mock(ConfigurationEntry.class);

        String typeName = VirtualHost.class.getSimpleName();
        when(virtualHostEntry.getType()).thenReturn(typeName);
        _brokerEntryChildren.put(typeName, Arrays.asList(virtualHostEntry));

        final VirtualHost virtualHost = mock(VirtualHost.class);

        RecovererProvider recovererProvider = createRecoveryProvider(new ConfigurationEntry[]{virtualHostEntry, _authenticationProviderEntry1},
                                                                     new ConfiguredObject[]{virtualHost, _authenticationProvider1});

        Broker broker = _brokerRecoverer.create(recovererProvider, _brokerEntry);

        assertNotNull(broker);
        assertEquals(_brokerId, broker.getId());
        assertEquals(1, broker.getVirtualHosts().size());
        assertEquals(virtualHost, broker.getVirtualHosts().iterator().next());
    }

    public void testCreateBrokerWithPorts()
    {
        ConfigurationEntry portEntry = mock(ConfigurationEntry.class);
        Port port = mock(Port.class);
        _brokerEntryChildren.put(Port.class.getSimpleName(), Arrays.asList(portEntry));

        RecovererProvider recovererProvider = createRecoveryProvider(new ConfigurationEntry[]{portEntry, _authenticationProviderEntry1},
                                                                     new ConfiguredObject[]{port, _authenticationProvider1});

        Broker broker = _brokerRecoverer.create(recovererProvider, _brokerEntry);

        assertNotNull(broker);
        assertEquals(_brokerId, broker.getId());
        assertEquals(Collections.singletonList(port), broker.getPorts());
    }

    public void testCreateBrokerWithOneAuthenticationProvider()
    {
        RecovererProvider recovererProvider = createRecoveryProvider(new ConfigurationEntry[]{_authenticationProviderEntry1},
                                                                     new ConfiguredObject[]{_authenticationProvider1});

        Broker broker = _brokerRecoverer.create(recovererProvider, _brokerEntry);

        assertNotNull(broker);
        assertEquals(_brokerId, broker.getId());
        assertEquals(Collections.singletonList(_authenticationProvider1), broker.getAuthenticationProviders());
    }

    public void testCreateBrokerWithMultipleAuthenticationProvidersAndPorts()
    {
        //Create a second authentication provider
        AuthenticationProvider authenticationProvider2 = mock(AuthenticationProvider.class);
        when(authenticationProvider2.getName()).thenReturn("authenticationProvider2");
        ConfigurationEntry authenticationProviderEntry2 = mock(ConfigurationEntry.class);
        _brokerEntryChildren.put(AuthenticationProvider.class.getSimpleName(), Arrays.asList(_authenticationProviderEntry1, authenticationProviderEntry2));

        //Add a couple ports
        ConfigurationEntry portEntry1 = mock(ConfigurationEntry.class);
        Port port1 = mock(Port.class);
        when(port1.getId()).thenReturn(UUIDGenerator.generateRandomUUID());
        when(port1.getName()).thenReturn("port1");
        when(port1.getPort()).thenReturn(5671);
        when(port1.getAttribute(Port.AUTHENTICATION_PROVIDER)).thenReturn("authenticationProvider1");
        ConfigurationEntry portEntry2 = mock(ConfigurationEntry.class);
        Port port2 = mock(Port.class);
        when(port2.getId()).thenReturn(UUIDGenerator.generateRandomUUID());
        when(port2.getName()).thenReturn("port2");
        when(port2.getPort()).thenReturn(5672);
        when(port2.getAttribute(Port.AUTHENTICATION_PROVIDER)).thenReturn("authenticationProvider2");
        _brokerEntryChildren.put(Port.class.getSimpleName(), Arrays.asList(portEntry1, portEntry2));

        RecovererProvider recovererProvider = createRecoveryProvider(
                new ConfigurationEntry[]{portEntry1, portEntry2, authenticationProviderEntry2, _authenticationProviderEntry1},
                new ConfiguredObject[]{port1, port2, authenticationProvider2, _authenticationProvider1});

        Broker broker = _brokerRecoverer.create(recovererProvider, _brokerEntry);

        assertNotNull(broker);
        assertEquals("Unexpected number of authentication providers", 2, broker.getAuthenticationProviders().size());

        Collection<Port> ports = broker.getPorts();
        assertEquals("Unexpected number of ports", 2, ports.size());
        assertTrue(ports.contains(port1));
        assertTrue(ports.contains(port2));
    }

    public void testCreateBrokerWithGroupProvider()
    {
        ConfigurationEntry groupProviderEntry = mock(ConfigurationEntry.class);
        GroupProvider groupProvider = mock(GroupProvider.class);
        _brokerEntryChildren.put(GroupProvider.class.getSimpleName(), Arrays.asList(groupProviderEntry));

        RecovererProvider recovererProvider = createRecoveryProvider(new ConfigurationEntry[]{groupProviderEntry, _authenticationProviderEntry1},
                                                                     new ConfiguredObject[]{groupProvider, _authenticationProvider1});

        Broker broker = _brokerRecoverer.create(recovererProvider, _brokerEntry);

        assertNotNull(broker);
        assertEquals(_brokerId, broker.getId());
        assertEquals(Collections.singletonList(groupProvider), broker.getGroupProviders());
    }

    public void testCreateBrokerWithPlugins()
    {
        ConfigurationEntry pluginEntry = mock(ConfigurationEntry.class);
        Plugin plugin = mock(Plugin.class);
        _brokerEntryChildren.put(Plugin.class.getSimpleName(), Arrays.asList(pluginEntry));

        RecovererProvider recovererProvider = createRecoveryProvider(new ConfigurationEntry[]{pluginEntry, _authenticationProviderEntry1},
                                                                     new ConfiguredObject[]{plugin, _authenticationProvider1});

        Broker broker = _brokerRecoverer.create(recovererProvider, _brokerEntry);

        assertNotNull(broker);
        assertEquals(_brokerId, broker.getId());
        assertEquals(Collections.singleton(plugin), new HashSet<ConfiguredObject>(broker.getChildren(Plugin.class)));
    }

    public void testCreateBrokerWithKeyStores()
    {
        ConfigurationEntry pluginEntry = mock(ConfigurationEntry.class);
        KeyStore keyStore = mock(KeyStore.class);
        _brokerEntryChildren.put(KeyStore.class.getSimpleName(), Arrays.asList(pluginEntry));

        RecovererProvider recovererProvider = createRecoveryProvider(new ConfigurationEntry[]{pluginEntry, _authenticationProviderEntry1},
                                                                     new ConfiguredObject[]{keyStore, _authenticationProvider1});

        Broker broker = _brokerRecoverer.create(recovererProvider, _brokerEntry);

        assertNotNull(broker);
        assertEquals(_brokerId, broker.getId());
        assertEquals(Collections.singleton(keyStore), new HashSet<ConfiguredObject>(broker.getChildren(KeyStore.class)));
    }

    public void testCreateBrokerWithTrustStores()
    {
        ConfigurationEntry pluginEntry = mock(ConfigurationEntry.class);
        TrustStore trustStore = mock(TrustStore.class);
        _brokerEntryChildren.put(TrustStore.class.getSimpleName(), Arrays.asList(pluginEntry));

        RecovererProvider recovererProvider = createRecoveryProvider(new ConfigurationEntry[]{pluginEntry, _authenticationProviderEntry1},
                                                                     new ConfiguredObject[]{trustStore, _authenticationProvider1});

        Broker broker = _brokerRecoverer.create(recovererProvider, _brokerEntry);

        assertNotNull(broker);
        assertEquals(_brokerId, broker.getId());
        assertEquals(Collections.singleton(trustStore), new HashSet<ConfiguredObject>(broker.getChildren(TrustStore.class)));
    }

    public void testModelVersionValidationForIncompatibleMajorVersion() throws Exception
    {
        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        String[] incompatibleVersions = {Integer.MAX_VALUE + "." + 0, "0.0"};
        for (String incompatibleVersion : incompatibleVersions)
        {
            brokerAttributes.put(Broker.MODEL_VERSION, incompatibleVersion);
            when(_brokerEntry.getAttributes()).thenReturn(brokerAttributes);

            try
            {
                _brokerRecoverer.create(null, _brokerEntry);
                fail("The broker creation should fail due to unsupported model version");
            }
            catch (IllegalConfigurationException e)
            {
                assertEquals("The model version '" + incompatibleVersion
                        + "' in configuration is incompatible with the broker model version '" + Model.MODEL_VERSION + "'", e.getMessage());
            }
        }
    }


    public void testModelVersionValidationForIncompatibleMinorVersion() throws Exception
    {
        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        String incompatibleVersion = Model.MODEL_MAJOR_VERSION + "." + Integer.MAX_VALUE;
        brokerAttributes.put(Broker.MODEL_VERSION, incompatibleVersion);
        when(_brokerEntry.getAttributes()).thenReturn(brokerAttributes);

        try
        {
            _brokerRecoverer.create(null, _brokerEntry);
            fail("The broker creation should fail due to unsupported model version");
        }
        catch (IllegalConfigurationException e)
        {
            assertEquals("The model version '" + incompatibleVersion
                    + "' in configuration is incompatible with the broker model version '" + Model.MODEL_VERSION + "'", e.getMessage());
        }
    }

    public void testIncorrectModelVersion() throws Exception
    {
        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        String[] versions = { Integer.MAX_VALUE + "_" + 0, "", null };
        for (String modelVersion : versions)
        {
            brokerAttributes.put(Broker.MODEL_VERSION, modelVersion);
            when(_brokerEntry.getAttributes()).thenReturn(brokerAttributes);

            try
            {
                _brokerRecoverer.create(null, _brokerEntry);
                fail("The broker creation should fail due to unsupported model version");
            }
            catch (IllegalConfigurationException e)
            {
                // pass
            }
        }
    }

    private String convertToString(Object attributeValue)
    {
        return String.valueOf(attributeValue);
    }

    private  RecovererProvider createRecoveryProvider(final ConfigurationEntry[] entries, final ConfiguredObject[] objectsToRecoverer)
    {
        RecovererProvider recovererProvider = new RecovererProvider()
        {
            @Override
            public ConfiguredObjectRecoverer<? extends ConfiguredObject> getRecoverer(String type)
            {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                final ConfiguredObjectRecoverer<?  extends ConfiguredObject> recovever = new ConfiguredObjectRecoverer()
                {
                    @Override
                    public ConfiguredObject create(RecovererProvider recovererProvider, ConfigurationEntry entry, ConfiguredObject... parents)
                    {
                        for (int i = 0; i < entries.length; i++)
                        {
                            ConfigurationEntry e = entries[i];
                            if (entry == e)
                            {
                                return objectsToRecoverer[i];
                            }
                        }
                        return null;
                    }
                };

                return recovever;
            }
        };
        return recovererProvider;
    }
}
