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
import java.util.Map;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.model.SystemContextImpl;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.ConfiguredObjectRecordImpl;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TestMemoryMessageStore;
import org.apache.qpid.server.store.UnresolvedConfiguredObject;

public class BrokerRecovererTest extends TestCase
{
    private ConfiguredObjectRecord _brokerEntry = mock(ConfiguredObjectRecord.class);

    private UUID _brokerId = UUID.randomUUID();
    private Map<String, Collection<ConfiguredObjectRecord>> _brokerEntryChildren = new HashMap<String, Collection<ConfiguredObjectRecord>>();
    private ConfiguredObjectRecord _authenticationProviderEntry1;
    private AuthenticationProvider _authenticationProvider1;
    private UUID _authenticationProvider1Id = UUID.randomUUID();
    private SystemContext _systemContext;
    private ConfiguredObjectFactory _configuredObjectFactory;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _configuredObjectFactory = new ConfiguredObjectFactory(Model.getInstance());
        _systemContext = new SystemContextImpl(mock(TaskExecutor.class),
                                           _configuredObjectFactory, mock(EventLogger.class), mock(LogRecorder.class), mock(BrokerOptions.class));

        when(_brokerEntry.getId()).thenReturn(_brokerId);
        when(_brokerEntry.getType()).thenReturn(Broker.class.getSimpleName());
        Map<String, Object> attributesMap = new HashMap<String, Object>();
        attributesMap.put(Broker.MODEL_VERSION, Model.MODEL_VERSION);
        attributesMap.put(Broker.NAME, getName());

        when(_brokerEntry.getAttributes()).thenReturn(attributesMap);
        when(_brokerEntry.getParents()).thenReturn(Collections.singletonMap(SystemContext.class.getSimpleName(), _systemContext.asObjectRecord()));

        //Add a base AuthenticationProvider for all tests
        _authenticationProvider1 = mock(AuthenticationProvider.class);
        when(_authenticationProvider1.getName()).thenReturn("authenticationProvider1");
        when(_authenticationProvider1.getId()).thenReturn(_authenticationProvider1Id);
        _authenticationProviderEntry1 = mock(ConfiguredObjectRecord.class);
        _brokerEntryChildren.put(AuthenticationProvider.class.getSimpleName(), Arrays.asList(_authenticationProviderEntry1));
    }

    public void testCreateBrokerAttributes()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Broker.NAME, getName());
        attributes.put(Broker.DEFAULT_VIRTUAL_HOST, "test");
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

        _systemContext.resolveObjects(_brokerEntry);
        Broker broker = _systemContext.getBroker();

        assertNotNull(broker);

        broker.open();

        assertEquals(_brokerId, broker.getId());

        for (Map.Entry<String, Object> attribute : attributes.entrySet())
        {
            Object attributeValue = broker.getAttribute(attribute.getKey());
            assertEquals("Unexpected value of attribute '" + attribute.getKey() + "'", attribute.getValue(), attributeValue);
        }
    }

    public void testCreateBrokerWithVirtualHost()
    {
        final ConfiguredObjectRecord virtualHostEntry = mock(ConfiguredObjectRecord.class);

        String typeName = VirtualHost.class.getSimpleName();
        when(virtualHostEntry.getType()).thenReturn(typeName);
        _brokerEntryChildren.put(typeName, Arrays.asList(virtualHostEntry));

        UUID vhostId = UUID.randomUUID();
        _systemContext.resolveObjects(_brokerEntry, createVhostRecord(vhostId));
        Broker<?> broker = _systemContext.getBroker();

        assertNotNull(broker);
        broker.open();
        assertEquals(_brokerId, broker.getId());
        assertEquals(1, broker.getVirtualHosts().size());
        assertEquals(vhostId, broker.getVirtualHosts().iterator().next().getId());

    }

    public ConfiguredObjectRecord createVhostRecord(UUID id)
    {
        final Map<String, Object> vhostAttributes = new HashMap<String, Object>();
        vhostAttributes.put(VirtualHost.NAME, "vhost");
        vhostAttributes.put(VirtualHost.TYPE, "STANDARD");
        vhostAttributes.put(VirtualHost.MESSAGE_STORE_SETTINGS, Collections.singletonMap(MessageStore.STORE_TYPE,
                                                                                               TestMemoryMessageStore.TYPE));
        return new ConfiguredObjectRecordImpl(id, VirtualHost.class.getSimpleName(), vhostAttributes, Collections
                .singletonMap(Broker.class.getSimpleName(), _brokerEntry));
    }

    public ConfiguredObjectRecord createAuthProviderRecord(UUID id, String name)
    {
        final Map<String, Object> authProviderAttrs = new HashMap<String, Object>();
        authProviderAttrs.put(AuthenticationProvider.NAME, name);
        authProviderAttrs.put(AuthenticationProvider.TYPE, "Anonymous");

        return new ConfiguredObjectRecordImpl(id, AuthenticationProvider.class.getSimpleName(), authProviderAttrs, Collections
                .singletonMap(Broker.class.getSimpleName(), _brokerEntry));
    }


    public ConfiguredObjectRecord createGroupProviderRecord(UUID id, String name)
    {
        final Map<String, Object> groupProviderAttrs = new HashMap<String, Object>();
        groupProviderAttrs.put(GroupProvider.NAME, name);
        groupProviderAttrs.put(GroupProvider.TYPE, "GroupFile");
        groupProviderAttrs.put("path", "/no-such-path");

        return new ConfiguredObjectRecordImpl(id, GroupProvider.class.getSimpleName(), groupProviderAttrs, Collections
                .singletonMap(Broker.class.getSimpleName(), _brokerEntry));
    }

    public ConfiguredObjectRecord createPortRecord(UUID id, int port, Object authProviderRef)
    {
        final Map<String, Object> portAttrs = new HashMap<String, Object>();
        portAttrs.put(Port.NAME, "port-"+port);
        portAttrs.put(Port.TYPE, "HTTP");
        portAttrs.put(Port.PORT, port);
        portAttrs.put(Port.AUTHENTICATION_PROVIDER, authProviderRef);

        return new ConfiguredObjectRecordImpl(id, Port.class.getSimpleName(), portAttrs, Collections
                .singletonMap(Broker.class.getSimpleName(), _brokerEntry));
    }


    public void testCreateBrokerWithPorts()
    {
        UUID authProviderId = UUID.randomUUID();
        UUID portId = UUID.randomUUID();

        _systemContext.resolveObjects(_brokerEntry, createAuthProviderRecord(authProviderId, "authProvider"), createPortRecord(
                portId,
                5672,
                "authProvider"));
        Broker<?> broker = _systemContext.getBroker();


        assertNotNull(broker);
        broker.open();
        assertEquals(_brokerId, broker.getId());
        assertEquals(1, broker.getPorts().size());
    }

    public void testCreateBrokerWithOneAuthenticationProvider()
    {
        UUID authProviderId = UUID.randomUUID();

        _systemContext.resolveObjects(_brokerEntry, createAuthProviderRecord(authProviderId, "authProvider"));
        Broker<?> broker = _systemContext.getBroker();


        assertNotNull(broker);
        broker.open();
        assertEquals(_brokerId, broker.getId());
        assertEquals(1, broker.getAuthenticationProviders().size());

    }

    public void testCreateBrokerWithMultipleAuthenticationProvidersAndPorts()
    {
        UUID authProviderId = UUID.randomUUID();
        UUID portId = UUID.randomUUID();
        UUID authProvider2Id = UUID.randomUUID();
        UUID port2Id = UUID.randomUUID();

        _systemContext.resolveObjects(_brokerEntry,
                                      createAuthProviderRecord(authProviderId, "authProvider"),
                                      createPortRecord(portId, 5672, "authProvider"),
                                      createAuthProviderRecord(authProvider2Id, "authProvider2"),
                                      createPortRecord(port2Id, 5673, "authProvider2"));
        Broker<?> broker = _systemContext.getBroker();


        assertNotNull(broker);
        broker.open();
        assertEquals(_brokerId, broker.getId());
        assertEquals(2, broker.getPorts().size());

        assertEquals("Unexpected number of authentication providers", 2, broker.getAuthenticationProviders().size());

    }

    public void testCreateBrokerWithGroupProvider()
    {

        UUID authProviderId = UUID.randomUUID();

        _systemContext.resolveObjects(_brokerEntry, createGroupProviderRecord(authProviderId, "groupProvider"));
        Broker<?> broker = _systemContext.getBroker();


        assertNotNull(broker);
        broker.open();
        assertEquals(_brokerId, broker.getId());
        assertEquals(1, broker.getGroupProviders().size());

    }

    public void testModelVersionValidationForIncompatibleMajorVersion() throws Exception
    {
        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        String[] incompatibleVersions = {Integer.MAX_VALUE + "." + 0, "0.0"};
        for (String incompatibleVersion : incompatibleVersions)
        {
            // need to reset all the shared objects for every iteration of the test
            setUp();
            brokerAttributes.put(Broker.MODEL_VERSION, incompatibleVersion);
            brokerAttributes.put(Broker.NAME, getName());
            when(_brokerEntry.getAttributes()).thenReturn(brokerAttributes);

            try
            {
                _systemContext.resolveObjects(_brokerEntry);
                Broker<?> broker = _systemContext.getBroker();
                broker.open();
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
        brokerAttributes.put(Broker.NAME, getName());

        when(_brokerEntry.getAttributes()).thenReturn(brokerAttributes);

        try
        {
            UnresolvedConfiguredObject<? extends ConfiguredObject> recover =
                    _configuredObjectFactory.recover(_brokerEntry, _systemContext);

            Broker<?> broker = (Broker<?>) recover.resolve();
            broker.open();
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
        brokerAttributes.put(Broker.NAME, getName());

        String[] versions = { Integer.MAX_VALUE + "_" + 0, "", null };
        for (String modelVersion : versions)
        {
            brokerAttributes.put(Broker.MODEL_VERSION, modelVersion);
            when(_brokerEntry.getAttributes()).thenReturn(brokerAttributes);

            try
            {
                UnresolvedConfiguredObject<? extends ConfiguredObject> recover =
                        _configuredObjectFactory.recover(_brokerEntry, _systemContext);
                Broker<?> broker = (Broker<?>) recover.resolve();
                broker.open();
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

    private  RecovererProvider createRecoveryProvider(final ConfiguredObjectRecord[] entries, final ConfiguredObject[] objectsToRecoverer)
    {
        RecovererProvider recovererProvider = new RecovererProvider()
        {
            @Override
            public ConfiguredObjectRecoverer<? extends ConfiguredObject> getRecoverer(String type)
            {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                final ConfiguredObjectRecoverer<?  extends ConfiguredObject> recoverer = new ConfiguredObjectRecoverer()
                {
                    public ConfiguredObject create(RecovererProvider recovererProvider, ConfiguredObjectRecord entry, ConfiguredObject... parents)
                    {
                        for (int i = 0; i < entries.length; i++)
                        {
                            ConfiguredObjectRecord e = entries[i];
                            if (entry == e)
                            {
                                return objectsToRecoverer[i];
                            }
                        }
                        return null;
                    }
                };

                return recoverer;
            }
        };
        return recovererProvider;
    }
}
