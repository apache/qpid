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

import org.apache.qpid.server.model.BrokerShutdownProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.JsonSystemConfigImpl;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.SystemConfig;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.ConfiguredObjectRecordImpl;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.test.utils.QpidTestCase;

public class ManagementModeStoreHandlerTest extends QpidTestCase
{
    private ManagementModeStoreHandler _handler;
    private BrokerOptions _options;
    private DurableConfigurationStore _store;
    private ConfiguredObjectRecord _root;
    private ConfiguredObjectRecord _portEntry;
    private UUID _rootId, _portEntryId;
    private SystemConfig _systemConfig;
    private TaskExecutor _taskExecutor;

    protected void setUp() throws Exception
    {
        super.setUp();
        _rootId = UUID.randomUUID();
        _portEntryId = UUID.randomUUID();
        _store = mock(DurableConfigurationStore.class);
        _taskExecutor = new CurrentThreadTaskExecutor();
        _taskExecutor.start();

        _systemConfig = new JsonSystemConfigImpl(_taskExecutor, mock(EventLogger.class),
                                               mock(LogRecorder.class), new BrokerOptions(),
                                               mock(BrokerShutdownProvider.class));


        ConfiguredObjectRecord systemContextRecord = _systemConfig.asObjectRecord();



        _root = new ConfiguredObjectRecordImpl(_rootId, Broker.class.getSimpleName(), Collections.<String,Object>emptyMap(), Collections.singletonMap(SystemConfig.class.getSimpleName(), systemContextRecord.getId()));

        _portEntry = mock(ConfiguredObjectRecord.class);
        when(_portEntry.getId()).thenReturn(_portEntryId);
        when(_portEntry.getParents()).thenReturn(Collections.singletonMap(Broker.class.getSimpleName(), _root.getId()));
        when(_portEntry.getType()).thenReturn(Port.class.getSimpleName());

        final ArgumentCaptor<ConfiguredObjectRecordHandler> recovererArgumentCaptor = ArgumentCaptor.forClass(ConfiguredObjectRecordHandler.class);
        doAnswer(
                new Answer()
                {
                    @Override
                    public Object answer(final InvocationOnMock invocation) throws Throwable
                    {
                        ConfiguredObjectRecordHandler recoverer = recovererArgumentCaptor.getValue();
                        if(recoverer.handle(_root))
                        {
                            recoverer.handle(_portEntry);
                        }
                        return null;
                    }
                }
                ).when(_store).visitConfiguredObjectRecords(recovererArgumentCaptor.capture());
        _options = new BrokerOptions();
        _handler = new ManagementModeStoreHandler(_store, _options);

        _handler.openConfigurationStore(_systemConfig, false);
    }

    @Override
    public void tearDown() throws Exception
    {
        _taskExecutor.stop();
        super.tearDown();
    }

    private ConfiguredObjectRecord getRootEntry()
    {
        BrokerFinder brokerFinder = new BrokerFinder();
        _handler.visitConfiguredObjectRecords(brokerFinder);
        return brokerFinder.getBrokerRecord();
    }

    private ConfiguredObjectRecord getEntry(UUID id)
    {
        RecordFinder recordFinder = new RecordFinder(id);
        _handler.visitConfiguredObjectRecords(recordFinder);
        return recordFinder.getFoundRecord();
    }

    private Collection<UUID> getChildrenIds(ConfiguredObjectRecord record)
    {
        ChildFinder childFinder = new ChildFinder(record);
        _handler.visitConfiguredObjectRecords(childFinder);
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
        _handler.openConfigurationStore(_systemConfig, false);
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
        _handler.openConfigurationStore(_systemConfig, false);

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
        _handler.openConfigurationStore(_systemConfig, false);

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
        _handler.openConfigurationStore(_systemConfig, false);

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
        _handler.openConfigurationStore(_systemConfig, false);


        UUID optionsPort = getOptionsPortId();
        ConfiguredObjectRecord portEntry = getEntry(optionsPort);
        assertCLIPortEntry(portEntry, optionsPort, Protocol.JMX_RMI);
    }

    public void testGetEntryByCLIHttpPortId()
    {
        _options.setManagementModeHttpPortOverride(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemConfig, false);


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
        _handler.openConfigurationStore(_systemConfig, false);


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
        _handler.openConfigurationStore(_systemConfig, false);


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
        _handler.openConfigurationStore(_systemConfig, false);


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

        final ConfiguredObjectRecord virtualHost = new ConfiguredObjectRecordImpl(virtualHostId, VirtualHost.class.getSimpleName(), attributes, Collections.singletonMap(Broker.class.getSimpleName(), _root.getId()));
        final ArgumentCaptor<ConfiguredObjectRecordHandler> recovererArgumentCaptor = ArgumentCaptor.forClass(ConfiguredObjectRecordHandler.class);
        doAnswer(
                new Answer()
                {
                    @Override
                    public Object answer(final InvocationOnMock invocation) throws Throwable
                    {
                        ConfiguredObjectRecordHandler recoverer = recovererArgumentCaptor.getValue();
                        if(recoverer.handle(_root))
                        {
                            if(recoverer.handle(_portEntry))
                            {
                                recoverer.handle(virtualHost);
                            }
                        }
                        return null;
                    }
                }
                ).when(_store).visitConfiguredObjectRecords(recovererArgumentCaptor.capture());

        State expectedState = mmQuiesceVhosts ? State.QUIESCED : null;
        if(mmQuiesceVhosts)
        {
            _options.setManagementModeQuiesceVirtualHosts(mmQuiesceVhosts);
        }

        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemConfig, false);

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
        _handler.openConfigurationStore(_systemConfig, false);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.NAME, "TEST");
        ConfiguredObjectRecord
                configurationEntry = new ConfiguredObjectRecordImpl(_portEntryId, Port.class.getSimpleName(), attributes,
                Collections.singletonMap(Broker.class.getSimpleName(), getRootEntry().getId()));
        _handler.create(configurationEntry);
        verify(_store).create(any(ConfiguredObjectRecord.class));
    }

    public void testSaveRoot()
    {
        _options.setManagementModeHttpPortOverride(1000);
        _options.setManagementModeRmiPortOverride(2000);
        _options.setManagementModeJmxPortOverride(3000);
        _handler = new ManagementModeStoreHandler(_store, _options);
        _handler.openConfigurationStore(_systemConfig, false);

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
        _handler.openConfigurationStore(_systemConfig, false);

        UUID portId = getOptionsPortId();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.NAME, "TEST");
        ConfiguredObjectRecord
                configurationEntry = new ConfiguredObjectRecordImpl(portId, Port.class.getSimpleName(), attributes,
                                                                    Collections.singletonMap(Broker.class.getSimpleName(),
                                                                                             getRootEntry().getId()));
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
        _handler.openConfigurationStore(_systemConfig, false);

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
            public Map<String, UUID> getParents()
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
        _handler.openConfigurationStore(_systemConfig, false);

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


    private class BrokerFinder implements ConfiguredObjectRecordHandler
    {
        private ConfiguredObjectRecord _brokerRecord;
        private int _version;

        @Override
        public void begin()
        {
        }

        @Override
        public boolean handle(final ConfiguredObjectRecord object)
        {
            if(object.getType().equals(Broker.class.getSimpleName()))
            {
                _brokerRecord = object;
                return false;
            }
            return true;
        }

        @Override
        public void end()
        {
        }

        public ConfiguredObjectRecord getBrokerRecord()
        {
            return _brokerRecord;
        }
    }

    private class RecordFinder implements ConfiguredObjectRecordHandler
    {
        private final UUID _id;
        private ConfiguredObjectRecord _foundRecord;

        private RecordFinder(final UUID id)
        {
            _id = id;
        }

        @Override
        public void begin()
        {
        }

        @Override
        public boolean handle(final ConfiguredObjectRecord object)
        {
            if(object.getId().equals(_id))
            {
                _foundRecord = object;
                return false;
            }
            return true;
        }

        @Override
        public void end()
        {
        }

        public ConfiguredObjectRecord getFoundRecord()
        {
            return _foundRecord;
        }
    }

    private class ChildFinder implements ConfiguredObjectRecordHandler
    {
        private final Collection<UUID> _childIds = new HashSet<UUID>();
        private final ConfiguredObjectRecord _parent;

        private ChildFinder(final ConfiguredObjectRecord parent)
        {
            _parent = parent;
        }

        @Override
        public void begin()
        {
        }

        @Override
        public boolean handle(final ConfiguredObjectRecord object)
        {

            if(object.getParents() != null)
            {
                for(UUID parent : object.getParents().values())
                {
                    if(parent.equals(_parent.getId()))
                    {
                        _childIds.add(object.getId());
                    }
                }

            }
            return true;
        }

        @Override
        public void end()
        {
        }

        public Collection<UUID> getChildIds()
        {
            return _childIds;
        }
    }
}
