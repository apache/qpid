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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.BrokerShutdownProvider;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.JsonSystemConfigImpl;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.SystemConfig;

public class BrokerRecovererTest extends TestCase
{
    private ConfiguredObjectRecord _brokerEntry = mock(ConfiguredObjectRecord.class);

    private UUID _brokerId = UUID.randomUUID();
    private AuthenticationProvider<?> _authenticationProvider1;
    private UUID _authenticationProvider1Id = UUID.randomUUID();
    private SystemConfig<?> _systemConfig;
    private TaskExecutor _taskExecutor;
    private BrokerShutdownProvider _brokerShutdownProvider;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _taskExecutor = new CurrentThreadTaskExecutor();
        _taskExecutor.start();
        _brokerShutdownProvider = mock(BrokerShutdownProvider.class);
        _systemConfig = new JsonSystemConfigImpl(_taskExecutor,
                                               mock(EventLogger.class), mock(LogRecorder.class),
                                               new BrokerOptions().convertToSystemConfigAttributes(),
                                               _brokerShutdownProvider);

        when(_brokerEntry.getId()).thenReturn(_brokerId);
        when(_brokerEntry.getType()).thenReturn(Broker.class.getSimpleName());
        Map<String, Object> attributesMap = new HashMap<String, Object>();
        attributesMap.put(Broker.MODEL_VERSION, BrokerModel.MODEL_VERSION);
        attributesMap.put(Broker.NAME, getName());

        when(_brokerEntry.getAttributes()).thenReturn(attributesMap);
        when(_brokerEntry.getParents()).thenReturn(Collections.singletonMap(SystemConfig.class.getSimpleName(), _systemConfig
                .getId()));

        //Add a base AuthenticationProvider for all tests
        _authenticationProvider1 = mock(AuthenticationProvider.class);
        when(_authenticationProvider1.getName()).thenReturn("authenticationProvider1");
        when(_authenticationProvider1.getId()).thenReturn(_authenticationProvider1Id);
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            _taskExecutor.stop();
        }
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
        attributes.put(Broker.MODEL_VERSION, BrokerModel.MODEL_VERSION);

        Map<String, Object> entryAttributes = new HashMap<String, Object>();
        for (Map.Entry<String, Object> attribute : attributes.entrySet())
        {
            String value = convertToString(attribute.getValue());
            entryAttributes.put(attribute.getKey(), value);
        }

        when(_brokerEntry.getAttributes()).thenReturn(entryAttributes);

        resolveObjects(_brokerEntry);
        Broker<?> broker = _systemConfig.getBroker();

        assertNotNull(broker);

        broker.open();

        assertEquals(_brokerId, broker.getId());

        for (Map.Entry<String, Object> attribute : attributes.entrySet())
        {
            Object attributeValue = broker.getAttribute(attribute.getKey());
            assertEquals("Unexpected value of attribute '" + attribute.getKey() + "'", attribute.getValue(), attributeValue);
        }
    }

    public ConfiguredObjectRecord createAuthProviderRecord(UUID id, String name)
    {
        final Map<String, Object> authProviderAttrs = new HashMap<String, Object>();
        authProviderAttrs.put(AuthenticationProvider.NAME, name);
        authProviderAttrs.put(AuthenticationProvider.TYPE, "Anonymous");

        return new ConfiguredObjectRecordImpl(id, AuthenticationProvider.class.getSimpleName(), authProviderAttrs, Collections
                .singletonMap(Broker.class.getSimpleName(), _brokerEntry.getId()));
    }


    public ConfiguredObjectRecord createGroupProviderRecord(UUID id, String name)
    {
        final Map<String, Object> groupProviderAttrs = new HashMap<String, Object>();
        groupProviderAttrs.put(GroupProvider.NAME, name);
        groupProviderAttrs.put(GroupProvider.TYPE, "GroupFile");
        groupProviderAttrs.put("path", "/no-such-path");

        return new ConfiguredObjectRecordImpl(id, GroupProvider.class.getSimpleName(), groupProviderAttrs, Collections
                .singletonMap(Broker.class.getSimpleName(), _brokerEntry.getId()));
    }

    public ConfiguredObjectRecord createPortRecord(UUID id, int port, Object authProviderRef)
    {
        final Map<String, Object> portAttrs = new HashMap<String, Object>();
        portAttrs.put(Port.NAME, "port-"+port);
        portAttrs.put(Port.TYPE, "HTTP");
        portAttrs.put(Port.PORT, port);
        portAttrs.put(Port.AUTHENTICATION_PROVIDER, authProviderRef);

        return new ConfiguredObjectRecordImpl(id, Port.class.getSimpleName(), portAttrs, Collections
                .singletonMap(Broker.class.getSimpleName(), _brokerEntry.getId()));
    }


    public void testCreateBrokerWithPorts()
    {
        UUID authProviderId = UUID.randomUUID();
        UUID portId = UUID.randomUUID();

        resolveObjects(_brokerEntry, createAuthProviderRecord(authProviderId, "authProvider"), createPortRecord(
                portId,
                5672,
                "authProvider"));
        Broker<?> broker = _systemConfig.getBroker();


        assertNotNull(broker);
        broker.open();
        assertEquals(_brokerId, broker.getId());
        assertEquals(1, broker.getPorts().size());
    }

    public void testCreateBrokerWithOneAuthenticationProvider()
    {
        UUID authProviderId = UUID.randomUUID();

        resolveObjects(_brokerEntry, createAuthProviderRecord(authProviderId, "authProvider"));
        Broker<?> broker = _systemConfig.getBroker();


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

        resolveObjects(_brokerEntry,
                                      createAuthProviderRecord(authProviderId, "authProvider"),
                                      createPortRecord(portId, 5672, "authProvider"),
                                      createAuthProviderRecord(authProvider2Id, "authProvider2"),
                                      createPortRecord(port2Id, 5673, "authProvider2"));
        Broker<?> broker = _systemConfig.getBroker();


        assertNotNull(broker);
        broker.open();
        assertEquals(_brokerId, broker.getId());
        assertEquals(2, broker.getPorts().size());

        assertEquals("Unexpected number of authentication providers", 2, broker.getAuthenticationProviders().size());

    }

    public void testCreateBrokerWithGroupProvider()
    {

        UUID authProviderId = UUID.randomUUID();

        resolveObjects(_brokerEntry, createGroupProviderRecord(authProviderId, "groupProvider"));
        Broker<?> broker = _systemConfig.getBroker();


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

            resolveObjects(_brokerEntry);
            Broker<?> broker = _systemConfig.getBroker();
            broker.open();
            verify(_brokerShutdownProvider).shutdown(1);
        }
    }


    public void testModelVersionValidationForIncompatibleMinorVersion() throws Exception
    {
        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        String incompatibleVersion = BrokerModel.MODEL_MAJOR_VERSION + "." + Integer.MAX_VALUE;
        brokerAttributes.put(Broker.MODEL_VERSION, incompatibleVersion);
        brokerAttributes.put(Broker.NAME, getName());

        when(_brokerEntry.getAttributes()).thenReturn(brokerAttributes);

        UnresolvedConfiguredObject<? extends ConfiguredObject> recover =
                _systemConfig.getObjectFactory().recover(_brokerEntry, _systemConfig);

        Broker<?> broker = (Broker<?>) recover.resolve();
        broker.open();
        verify(_brokerShutdownProvider).shutdown(1);
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

            UnresolvedConfiguredObject<? extends ConfiguredObject> recover =
                    _systemConfig.getObjectFactory().recover(_brokerEntry, _systemConfig);
            Broker<?> broker = (Broker<?>) recover.resolve();
            broker.open();
            verify(_brokerShutdownProvider).shutdown(1);
            reset(_brokerShutdownProvider);
        }
    }

    private String convertToString(Object attributeValue)
    {
        return String.valueOf(attributeValue);
    }

    private void resolveObjects(ConfiguredObjectRecord... records)
    {
        GenericRecoverer recoverer = new GenericRecoverer(_systemConfig);
        recoverer.recover(Arrays.asList(records));
    }

}
