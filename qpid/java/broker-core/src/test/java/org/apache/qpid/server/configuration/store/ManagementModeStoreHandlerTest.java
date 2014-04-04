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
package org.apache.qpid.server.configuration.store;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.ConfiguredObjectRecordImpl;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.test.utils.QpidTestCase;

public class ManagementModeStoreHandlerTest extends QpidTestCase
{
    private ManagementModeStoreHandler _handler;
    private BrokerOptions _options;
    private DurableConfigurationStore _store;
    private ConfiguredObjectRecord _root;
    private ConfiguredObjectRecord _portEntry;
    private UUID _rootId, _portEntryId;
    private SystemContext _systemContext;

    protected void setUp() throws Exception
    {
        super.setUp();
        _rootId = UUID.randomUUID();
        _portEntryId = UUID.randomUUID();
        _store = mock(DurableConfigurationStore.class);


        _systemContext = new SystemContext(new TaskExecutor(), new ConfiguredObjectFactory(), mock(
                EventLogger.class), mock(LogRecorder.class), new BrokerOptions());


        ConfiguredObjectRecord systemContextRecord = _systemContext.asObjectRecord();



        _root = new ConfiguredObjectRecordImpl(_rootId, Broker.class.getSimpleName(), Collections.<String,Object>emptyMap(), Collections.singletonMap(SystemContext.class.getSimpleName(), systemContextRecord));

        _portEntry = mock(ConfiguredObjectRecord.class);
        when(_portEntry.getId()).thenReturn(_portEntryId);
        when(_portEntry.getParents()).thenReturn(Collections.singletonMap(Broker.class.getSimpleName(), _root));
        when(_portEntry.getType()).thenReturn(Port.class.getSimpleName());

        final ArgumentCaptor<ConfigurationRecoveryHandler> recovererArgumentCaptor = ArgumentCaptor.forClass(ConfigurationRecoveryHandler.class);
        doAnswer(
                new Answer()
                {
                    @Override
                    public Object answer(final InvocationOnMock invocation) throws Throwable
                    {
                        ConfigurationRecoveryHandler recoverer = recovererArgumentCaptor.getValue();
                        recoverer.configuredObject(_root);
                        recoverer.configuredObject(_portEntry);
                        return null;
                    }
                }
                ).when(_store).recoverConfigurationStore(recovererArgumentCaptor.capture());
        _options = new BrokerOptions();
        _handler = new ManagementModeStoreHandler(_store, _options);

        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());
    }

    private ConfiguredObjectRecord getRootEntry()
    {
        BrokerFinder brokerFinder = new BrokerFinder();
        _handler.recoverConfigurationStore(brokerFinder);
        return brokerFinder.getBrokerRecord();
    }

    private ConfiguredObjectRecord getEntry(UUID id)
    {
        RecordFinder recordFinder = new RecordFinder(id);
        _handler.recoverConfigurationStore(recordFinder);
        return recordFinder.getFoundRecord();
    }

    private Collection<UUID> getChildrenIds(ConfiguredObjectRecord record)
    {
        ChildFinder childFinder = new ChildFinder(record);
        _handler.recoverConfigurationStore(childFinder);
        return childFinder.getChildIds();
    }

    public void testGetRootEntryWithEmptyOptions()
    {
        ConfiguredObjectRecord root = getRootEntry();
        assertEquals("Unexpected root id", _rootId, root.getId());
        assertEquals("Unexpected children", Collections.singleton(_portEntryId), getChildrenIds(root));
    }

    public void testGetRootEntryWithHttpPortOverriden()
    {
        _options.setManagementModeHttpPortOverride(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());
        ConfiguredObjectRecord root = getRootEntry();
        assertEquals("Unexpected root id", _rootId, root.getId());
        Collection<UUID> childrenIds = getChildrenIds(root);
        assertEquals("Unexpected children size", 2, childrenIds.size());
        assertTrue("Store port entry id is not found", childrenIds.contains(_portEntryId));
    }

    public void testGetRootEntryWithRmiPortOverriden()
    {
        _options.setManagementModeRmiPortOverride(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());

        ConfiguredObjectRecord root = getRootEntry();
        assertEquals("Unexpected root id", _rootId, root.getId());
        Collection<UUID> childrenIds = getChildrenIds(root);
        assertEquals("Unexpected children size", 3, childrenIds.size());
        assertTrue("Store port entry id is not found", childrenIds.contains(_portEntryId));
    }

    public void testGetRootEntryWithConnectorPortOverriden()
    {
        _options.setManagementModeJmxPortOverride(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());

        ConfiguredObjectRecord root = getRootEntry();
        assertEquals("Unexpected root id", _rootId, root.getId());
        Collection<UUID> childrenIds = getChildrenIds(root);
        assertEquals("Unexpected children size", 2, childrenIds.size());
        assertTrue("Store port entry id is not found", childrenIds.contains(_portEntryId));
    }

    public void testGetRootEntryWithManagementPortsOverriden()
    {
        _options.setManagementModeHttpPortOverride(1000);
        _options.setManagementModeRmiPortOverride(2000);
        _options.setManagementModeJmxPortOverride(3000);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());

        ConfiguredObjectRecord root = getRootEntry();
        assertEquals("Unexpected root id", _rootId, root.getId());
        Collection<UUID> childrenIds = getChildrenIds(root);
        assertEquals("Unexpected children size", 4, childrenIds.size());
        assertTrue("Store port entry id is not found", childrenIds.contains(_portEntryId));
    }

    public void testGetEntryByRootId()
    {
        ConfiguredObjectRecord root = getEntry(_rootId);
        assertEquals("Unexpected root id", _rootId, root.getId());
        assertEquals("Unexpected children", Collections.singleton(_portEntryId), getChildrenIds(root));
    }

    public void testGetEntryByPortId()
    {
        ConfiguredObjectRecord portEntry = getEntry(_portEntryId);
        assertEquals("Unexpected entry id", _portEntryId, portEntry.getId());
        assertTrue("Unexpected children", getChildrenIds(portEntry).isEmpty());
        assertEquals("Unexpected state", State.QUIESCED, portEntry.getAttributes().get(Port.STATE));
    }

    public void testGetEntryByCLIConnectorPortId()
    {
        _options.setManagementModeJmxPortOverride(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());


        UUID optionsPort = getOptionsPortId();
        ConfiguredObjectRecord portEntry = getEntry(optionsPort);
        assertCLIPortEntry(portEntry, optionsPort, Protocol.JMX_RMI);
    }

    public void testGetEntryByCLIHttpPortId()
    {
        _options.setManagementModeHttpPortOverride(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());


        UUID optionsPort = getOptionsPortId();
        ConfiguredObjectRecord portEntry = getEntry(optionsPort);
        assertCLIPortEntry(portEntry, optionsPort, Protocol.HTTP);
    }

    public void testHttpPortEntryIsQuiesced()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.PROTOCOLS, Collections.singleton(Protocol.HTTP));
        when(_portEntry.getAttributes()).thenReturn(attributes);
        _options.setManagementModeHttpPortOverride(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());


        ConfiguredObjectRecord portEntry = getEntry(_portEntryId);
        assertEquals("Unexpected state", State.QUIESCED, portEntry.getAttributes().get(Port.STATE));
    }

    public void testRmiPortEntryIsQuiesced()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.PROTOCOLS, Collections.singleton(Protocol.RMI));
        when(_portEntry.getAttributes()).thenReturn(attributes);
        _options.setManagementModeRmiPortOverride(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());


        ConfiguredObjectRecord portEntry = getEntry(_portEntryId);
        assertEquals("Unexpected state", State.QUIESCED, portEntry.getAttributes().get(Port.STATE));
    }

    public void testConnectorPortEntryIsQuiesced()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.PROTOCOLS, Collections.singleton(Protocol.JMX_RMI));
        when(_portEntry.getAttributes()).thenReturn(attributes);
        _options.setManagementModeRmiPortOverride(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());


        ConfiguredObjectRecord portEntry = getEntry(_portEntryId);
        assertEquals("Unexpected state", State.QUIESCED, portEntry.getAttributes().get(Port.STATE));
    }

    public void testVirtualHostEntryIsNotQuiescedByDefault()
    {
        virtualHostEntryQuiescedStatusTestImpl(false);
    }

    public void testVirtualHostEntryIsQuiescedWhenRequested()
    {
        virtualHostEntryQuiescedStatusTestImpl(true);
    }

    private void virtualHostEntryQuiescedStatusTestImpl(boolean mmQuiesceVhosts)
    {
        UUID virtualHostId = UUID.randomUUID();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(VirtualHost.TYPE, "STANDARD");

        final ConfiguredObjectRecord virtualHost = new ConfiguredObjectRecordImpl(virtualHostId, VirtualHost.class.getSimpleName(), attributes, Collections.singletonMap(Broker.class.getSimpleName(), _root));
        final ArgumentCaptor<ConfigurationRecoveryHandler> recovererArgumentCaptor = ArgumentCaptor.forClass(ConfigurationRecoveryHandler.class);
        doAnswer(
                new Answer()
                {
                    @Override
                    public Object answer(final InvocationOnMock invocation) throws Throwable
                    {
                        ConfigurationRecoveryHandler recoverer = recovererArgumentCaptor.getValue();
                        recoverer.configuredObject(_root);
                        recoverer.configuredObject(_portEntry);
                        recoverer.configuredObject(virtualHost);
                        return null;
                    }
                }
                ).when(_store).recoverConfigurationStore(recovererArgumentCaptor.capture());

        State expectedState = mmQuiesceVhosts ? State.QUIESCED : null;
        if(mmQuiesceVhosts)
        {
            _options.setManagementModeQuiesceVirtualHosts(mmQuiesceVhosts);
        }

        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());

        ConfiguredObjectRecord hostEntry = getEntry(virtualHostId);
        Map<String, Object> hostAttributes = new HashMap<String, Object>(hostEntry.getAttributes());
        assertEquals("Unexpected state", expectedState, hostAttributes.get(VirtualHost.STATE));
        hostAttributes.remove(VirtualHost.STATE);
        assertEquals("Unexpected attributes", attributes, hostAttributes);
    }

    @SuppressWarnings("unchecked")
    private void assertCLIPortEntry(ConfiguredObjectRecord portEntry, UUID optionsPort, Protocol protocol)
    {
        assertEquals("Unexpected entry id", optionsPort, portEntry.getId());
        assertTrue("Unexpected children", getChildrenIds(portEntry).isEmpty());
        Map<String, Object> attributes = portEntry.getAttributes();
        assertEquals("Unexpected name", "MANAGEMENT-MODE-PORT-" + protocol.name(), attributes.get(Port.NAME));
        assertEquals("Unexpected protocol", Collections.singleton(protocol), new HashSet<Protocol>(
                (Collection<Protocol>) attributes.get(Port.PROTOCOLS)));
    }

    public void testSavePort()
    {
        _options.setManagementModeHttpPortOverride(1000);
        _options.setManagementModeRmiPortOverride(2000);
        _options.setManagementModeJmxPortOverride(3000);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.NAME, "TEST");
        ConfiguredObjectRecord
                configurationEntry = new ConfiguredObjectRecordImpl(_portEntryId, Port.class.getSimpleName(), attributes,
                Collections.singletonMap(Broker.class.getSimpleName(), getRootEntry()));
        _handler.create(configurationEntry);
        verify(_store).create(any(ConfiguredObjectRecord.class));
    }

    public void testSaveRoot()
    {
        _options.setManagementModeHttpPortOverride(1000);
        _options.setManagementModeRmiPortOverride(2000);
        _options.setManagementModeJmxPortOverride(3000);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());

        ConfiguredObjectRecord root = getRootEntry();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Broker.NAME, "TEST");
        ConfiguredObjectRecord
                configurationEntry = new ConfiguredObjectRecordImpl(_rootId, Broker.class.getSimpleName(), attributes,root.getParents());
        _handler.update(false, configurationEntry);
        verify(_store).update(anyBoolean(), any(ConfiguredObjectRecord.class));
    }

    public void testSaveCLIHttpPort()
    {
        _options.setManagementModeHttpPortOverride(1000);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());

        UUID portId = getOptionsPortId();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.NAME, "TEST");
        ConfiguredObjectRecord
                configurationEntry = new ConfiguredObjectRecordImpl(portId, Port.class.getSimpleName(), attributes,
                                                                    Collections.singletonMap(Broker.class.getSimpleName(),
                                                                                             getRootEntry()));
        try
        {
            _handler.update(false, configurationEntry);
            fail("Exception should be thrown on trying to save CLI port");
        }
        catch (IllegalConfigurationException e)
        {
            // pass
        }
    }

    public void testRemove()
    {
        _options.setManagementModeHttpPortOverride(1000);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());

        ConfiguredObjectRecord record = new ConfiguredObjectRecord()
        {
            @Override
            public UUID getId()
            {
                return _portEntryId;
            }

            @Override
            public String getType()
            {
                return Port.class.getSimpleName();
            }

            @Override
            public Map<String, Object> getAttributes()
            {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, ConfiguredObjectRecord> getParents()
            {
                return null;
            }
        };
        _handler.remove(record);
        verify(_store).remove(record);
    }

    public void testRemoveCLIPort()
    {
        _options.setManagementModeHttpPortOverride(1000);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemContext,Collections.<String,Object>emptyMap());

        UUID portId = getOptionsPortId();
        ConfiguredObjectRecord record = mock(ConfiguredObjectRecord.class);
        when(record.getId()).thenReturn(portId);
        try
        {
            _handler.remove(record);
            fail("Exception should be thrown on trying to remove CLI port");
        }
        catch (IllegalConfigurationException e)
        {
            // pass
        }
    }

    private UUID getOptionsPortId()
    {
        ConfiguredObjectRecord root = getRootEntry();
        assertEquals("Unexpected root id", _rootId, root.getId());
        Collection<UUID> childrenIds = getChildrenIds(root);

        childrenIds.remove(_portEntryId);
        UUID optionsPort = childrenIds.iterator().next();
        return optionsPort;
    }


    private class BrokerFinder implements ConfigurationRecoveryHandler
    {
        private ConfiguredObjectRecord _brokerRecord;
        @Override
        public void beginConfigurationRecovery(final DurableConfigurationStore store, final int configVersion)
        {

        }

        @Override
        public void configuredObject(final ConfiguredObjectRecord object)
        {
            if(object.getType().equals(Broker.class.getSimpleName()))
            {
                _brokerRecord = object;
            }
        }

        @Override
        public int completeConfigurationRecovery()
        {
            return 0;
        }

        public ConfiguredObjectRecord getBrokerRecord()
        {
            return _brokerRecord;
        }
    }

    private class RecordFinder implements ConfigurationRecoveryHandler
    {
        private final UUID _id;
        private ConfiguredObjectRecord _foundRecord;

        private RecordFinder(final UUID id)
        {
            _id = id;
        }

        @Override
        public void beginConfigurationRecovery(final DurableConfigurationStore store, final int configVersion)
        {

        }

        @Override
        public void configuredObject(final ConfiguredObjectRecord object)
        {
            if(object.getId().equals(_id))
            {
                _foundRecord = object;
            }
        }

        @Override
        public int completeConfigurationRecovery()
        {
            return 0;
        }

        public ConfiguredObjectRecord getFoundRecord()
        {
            return _foundRecord;
        }
    }

    private class ChildFinder implements ConfigurationRecoveryHandler
    {
        private final Collection<UUID> _childIds = new HashSet<UUID>();
        private final ConfiguredObjectRecord _parent;

        private ChildFinder(final ConfiguredObjectRecord parent)
        {
            _parent = parent;
        }

        @Override
        public void beginConfigurationRecovery(final DurableConfigurationStore store, final int configVersion)
        {

        }

        @Override
        public void configuredObject(final ConfiguredObjectRecord object)
        {

            if(object.getParents() != null)
            {
                for(ConfiguredObjectRecord parent : object.getParents().values())
                {
                    if(parent.getId().equals(_parent.getId()))
                    {
                        _childIds.add(object.getId());
                    }
                }

            }
        }

        @Override
        public int completeConfigurationRecovery()
        {
            return 0;
        }

        public Collection<UUID> getChildIds()
        {
            return _childIds;
        }
    }
}
