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
package org.apache.qpid.server.virtualhostnode;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactoryImpl;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.RemoteReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.SystemConfig;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.NullMessageStore;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.TestMemoryVirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

public class AbstractStandardVirtualHostNodeTest extends QpidTestCase
{
    private static final String TEST_VIRTUAL_HOST_NODE_NAME = "testNode";
    private static final String TEST_VIRTUAL_HOST_NAME = "testVirtualHost";

    private final UUID _nodeId = UUID.randomUUID();
    private final SecurityManager _mockSecurityManager = mock(SecurityManager.class);
    private Broker<?> _broker;
    private TaskExecutor _taskExecutor;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _broker = BrokerTestHelper.createBrokerMock();
        SystemConfig<?> systemConfig = _broker.getParent(SystemConfig.class);
        when(systemConfig.getObjectFactory()).thenReturn(new ConfiguredObjectFactoryImpl(mock(Model.class)));

        _taskExecutor = new CurrentThreadTaskExecutor();
        _taskExecutor.start();
        when(_broker.getTaskExecutor()).thenReturn(_taskExecutor);
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            _taskExecutor.stopImmediately();
        }
        finally
        {
            super.tearDown();
        }
    }

    /**
     *  Tests activating a virtualhostnode with a config store that specifies a
     *  virtualhost.  Ensures that the virtualhost created.
     */
    public void testActivateVHN_StoreHasVH() throws Exception
    {
        UUID virtualHostId = UUID.randomUUID();
        ConfiguredObjectRecord vhostRecord = createVirtualHostConfiguredObjectRecord(virtualHostId);
        DurableConfigurationStore configStore = configStoreThatProduces(vhostRecord);

        Map<String, Object> nodeAttributes = new HashMap<>();
        nodeAttributes.put(VirtualHostNode.NAME, TEST_VIRTUAL_HOST_NODE_NAME);
        nodeAttributes.put(VirtualHostNode.ID, _nodeId);

        VirtualHostNode<?> node = new TestVirtualHostNode(_broker, nodeAttributes, configStore);
        node.open();
        node.start();

        VirtualHost<?, ?, ?> virtualHost = node.getVirtualHost();
        assertNotNull("Virtual host was not recovered", virtualHost);
        assertEquals("Unexpected virtual host name", TEST_VIRTUAL_HOST_NAME, virtualHost.getName());
        assertEquals("Unexpected virtual host state", State.ACTIVE, virtualHost.getState());
        assertEquals("Unexpected virtual host id", virtualHostId, virtualHost.getId());
    }

    /**
     *  Tests activating a virtualhostnode with a config store which does not specify
     *  a virtualhost.  Checks no virtualhost is created.
     */
    public void testActivateVHN_StoreHasNoVH() throws Exception
    {
        DurableConfigurationStore configStore = configStoreThatProducesNoRecords();

        Map<String, Object> nodeAttributes = new HashMap<>();
        nodeAttributes.put(VirtualHostNode.NAME, TEST_VIRTUAL_HOST_NODE_NAME);
        nodeAttributes.put(VirtualHostNode.ID, _nodeId);

        VirtualHostNode<?> node = new TestVirtualHostNode(_broker, nodeAttributes, configStore);
        node.open();
        node.start();

        VirtualHost<?, ?, ?> virtualHost = node.getVirtualHost();
        assertNull("Virtual host should not be automatically created", virtualHost);
    }

    /**
     *  Tests activating a virtualhostnode with a blueprint context variable.  Config store
     *  does not specify a virtualhost.  Checks virtualhost is created from the blueprint.
     */
    public void testActivateVHNWithVHBlueprint_StoreHasNoVH() throws Exception
    {
        DurableConfigurationStore configStore = new NullMessageStore() {};

        String vhBlueprint = String.format("{ \"type\" : \"%s\", \"name\" : \"%s\"}",
                                           TestMemoryVirtualHost.VIRTUAL_HOST_TYPE,
                                           TEST_VIRTUAL_HOST_NAME);
        Map<String, String> context = Collections.singletonMap(AbstractVirtualHostNode.VIRTUALHOST_BLUEPRINT_CONTEXT_VAR, vhBlueprint);

        Map<String, Object> nodeAttributes = new HashMap<>();
        nodeAttributes.put(VirtualHostNode.NAME, TEST_VIRTUAL_HOST_NODE_NAME);
        nodeAttributes.put(VirtualHostNode.ID, _nodeId);
        nodeAttributes.put(VirtualHostNode.CONTEXT, context);

        VirtualHostNode<?> node = new TestVirtualHostNode(_broker, nodeAttributes, configStore);
        node.open();
        node.start();

        VirtualHost<?, ?, ?> virtualHost = node.getVirtualHost();

        assertNotNull("Virtual host should be created by blueprint", virtualHost);
        assertEquals("Unexpected virtual host name", TEST_VIRTUAL_HOST_NAME, virtualHost.getName());
        assertEquals("Unexpected virtual host state", State.ACTIVE, virtualHost.getState());
        assertNotNull("Unexpected virtual host id", virtualHost.getId());

        assertEquals("Initial configuration should be empty", "{}", node.getVirtualHostInitialConfiguration());
    }

    /**
     *  Tests activating a virtualhostnode with blueprint context variable and the
     *  but the virtualhostInitialConfiguration set to empty.  Config store does not specify a virtualhost.
     *  Checks virtualhost is not recreated from the blueprint.
     */
    public void testActivateVHNWithVHBlueprintUsed_StoreHasNoVH() throws Exception
    {
        DurableConfigurationStore configStore = configStoreThatProducesNoRecords();

        String vhBlueprint = String.format("{ \"type\" : \"%s\", \"name\" : \"%s\"}",
                                           TestMemoryVirtualHost.VIRTUAL_HOST_TYPE,
                                           TEST_VIRTUAL_HOST_NAME);
        Map<String, String> context = new HashMap<>();
        context.put(AbstractVirtualHostNode.VIRTUALHOST_BLUEPRINT_CONTEXT_VAR, vhBlueprint);

        Map<String, Object> nodeAttributes = new HashMap<>();
        nodeAttributes.put(VirtualHostNode.NAME, TEST_VIRTUAL_HOST_NODE_NAME);
        nodeAttributes.put(VirtualHostNode.ID, _nodeId);
        nodeAttributes.put(VirtualHostNode.CONTEXT, context);
        nodeAttributes.put(VirtualHostNode.VIRTUALHOST_INITIAL_CONFIGURATION, "{}");

        VirtualHostNode<?> node = new TestVirtualHostNode(_broker, nodeAttributes, configStore);
        node.open();
        node.start();

        VirtualHost<?, ?, ?> virtualHost = node.getVirtualHost();

        assertNull("Virtual host should not be created by blueprint", virtualHost);
    }

    /**
     *  Tests activating a virtualhostnode with a blueprint context variable.  Config store
     *  does specify a virtualhost.  Checks that virtualhost is recovered from store and
     *  blueprint is ignored..
     */
    public void testActivateVHNWithVHBlueprint_StoreHasExistingVH() throws Exception
    {
        UUID virtualHostId = UUID.randomUUID();
        ConfiguredObjectRecord record = createVirtualHostConfiguredObjectRecord(virtualHostId);

        DurableConfigurationStore configStore = configStoreThatProduces(record);

        String vhBlueprint = String.format("{ \"type\" : \"%s\", \"name\" : \"%s\"}",
                                           TestMemoryVirtualHost.VIRTUAL_HOST_TYPE,
                                           "vhFromBlueprint");
        Map<String, String> context = Collections.singletonMap(AbstractVirtualHostNode.VIRTUALHOST_BLUEPRINT_CONTEXT_VAR, vhBlueprint);

        Map<String, Object> nodeAttributes = new HashMap<>();
        nodeAttributes.put(VirtualHostNode.NAME, TEST_VIRTUAL_HOST_NODE_NAME);
        nodeAttributes.put(VirtualHostNode.ID, _nodeId);
        nodeAttributes.put(VirtualHostNode.CONTEXT, context);

        VirtualHostNode<?> node = new TestVirtualHostNode(_broker, nodeAttributes, configStore);
        node.open();
        node.start();

        VirtualHost<?, ?, ?> virtualHost = node.getVirtualHost();

        assertNotNull("Virtual host should be recovered", virtualHost);
        assertEquals("Unexpected virtual host name", TEST_VIRTUAL_HOST_NAME, virtualHost.getName());
        assertEquals("Unexpected virtual host state", State.ACTIVE, virtualHost.getState());
        assertEquals("Unexpected virtual host id", virtualHostId, virtualHost.getId());
    }

    public void testStopStartVHN() throws Exception
    {
        DurableConfigurationStore configStore = configStoreThatProducesNoRecords();

        Map<String, Object> nodeAttributes = new HashMap<>();
        nodeAttributes.put(VirtualHostNode.NAME, TEST_VIRTUAL_HOST_NODE_NAME);
        nodeAttributes.put(VirtualHostNode.ID, _nodeId);

        VirtualHostNode<?> node = new TestVirtualHostNode(_broker, nodeAttributes, configStore);
        node.open();
        node.start();

        assertEquals("Unexpected virtual host node state", State.ACTIVE, node.getState());

        node.stop();
        assertEquals("Unexpected virtual host node state after stop", State.STOPPED, node.getState());

        node.start();
        assertEquals("Unexpected virtual host node state after start", State.ACTIVE, node.getState());
    }


    // ***************  VHN Access Control Tests  ***************

    public void testUpdateVHNDeniedByACL() throws Exception
    {
        when(_broker.getSecurityManager()).thenReturn(_mockSecurityManager);

        DurableConfigurationStore configStore = configStoreThatProducesNoRecords();

        Map<String, Object> nodeAttributes = new HashMap<>();
        nodeAttributes.put(VirtualHostNode.NAME, TEST_VIRTUAL_HOST_NODE_NAME);
        nodeAttributes.put(VirtualHostNode.ID, _nodeId);

        VirtualHostNode<?> node = new TestVirtualHostNode(_broker, nodeAttributes, configStore);
        node.open();
        node.start();

        doThrow(new AccessControlException("mocked ACL exception")).when(_mockSecurityManager).authoriseUpdate(node);

        assertNull(node.getDescription());
        try
        {
            node.setAttribute(VirtualHostNode.DESCRIPTION, null, "My virtualhost node");
            fail("Exception not throws");
        }
        catch (AccessControlException ace)
        {
            // PASS
        }
        assertNull("Description unexpected updated", node.getDescription());
    }

    public void testDeleteVHNDeniedByACL() throws Exception
    {
        SecurityManager mockSecurityManager = mock(SecurityManager.class);
        when(_broker.getSecurityManager()).thenReturn(mockSecurityManager);

        DurableConfigurationStore configStore = configStoreThatProducesNoRecords();

        Map<String, Object> nodeAttributes = new HashMap<>();
        nodeAttributes.put(VirtualHostNode.NAME, TEST_VIRTUAL_HOST_NODE_NAME);
        nodeAttributes.put(VirtualHostNode.ID, _nodeId);

        VirtualHostNode<?> node = new TestVirtualHostNode(_broker, nodeAttributes, configStore);
        node.open();
        node.start();

        doThrow(new AccessControlException("mocked ACL exception")).when(mockSecurityManager).authoriseDelete(node);

        try
        {
            node.delete();
            fail("Exception not throws");
        }
        catch (AccessControlException ace)
        {
            // PASS
        }

        assertEquals("Virtual host node state changed unexpectedly", State.ACTIVE, node.getState());
    }

    public void testStopVHNDeniedByACL() throws Exception
    {
        SecurityManager mockSecurityManager = mock(SecurityManager.class);
        when(_broker.getSecurityManager()).thenReturn(mockSecurityManager);

        DurableConfigurationStore configStore = configStoreThatProducesNoRecords();

        Map<String, Object> nodeAttributes = new HashMap<>();
        nodeAttributes.put(VirtualHostNode.NAME, TEST_VIRTUAL_HOST_NODE_NAME);
        nodeAttributes.put(VirtualHostNode.ID, _nodeId);

        VirtualHostNode<?> node = new TestVirtualHostNode(_broker, nodeAttributes, configStore);
        node.open();
        node.start();

        doThrow(new AccessControlException("mocked ACL exception")).when(mockSecurityManager).authoriseUpdate(node);

        try
        {
            node.stop();
            fail("Exception not throws");
        }
        catch (AccessControlException ace)
        {
            // PASS
        }

        assertEquals("Virtual host node state changed unexpectedly", State.ACTIVE, node.getState());
    }

    public void testValidateOnCreateFails() throws Exception
    {
        String nodeName = getTestName();
        Map<String, Object> attributes = Collections.<String, Object>singletonMap(TestVirtualHostNode.NAME, nodeName);

        final DurableConfigurationStore store = mock(DurableConfigurationStore.class);
        doThrow(new RuntimeException("Cannot open store")).when(store).openConfigurationStore(any(ConfiguredObject.class), any(boolean.class));
        AbstractStandardVirtualHostNode node = createAbstractStandardVirtualHostNode(attributes, store);

        try
        {
            node.validateOnCreate();
            fail("Cannot create node");
        }
        catch (IllegalConfigurationException e)
        {
            assertTrue("Unexpected exception " + e.getMessage(), e.getMessage().startsWith("Cannot open node configuration store"));
        }
    }

    public void testValidateOnCreateSucceeds() throws Exception
    {
        String nodeName = getTestName();
        Map<String, Object> attributes = Collections.<String, Object>singletonMap(TestVirtualHostNode.NAME, nodeName);

        final DurableConfigurationStore store = mock(DurableConfigurationStore.class);
        AbstractStandardVirtualHostNode node = createAbstractStandardVirtualHostNode(attributes, store);

        node.validateOnCreate();
        verify(store).openConfigurationStore(node, false);
        verify(store).closeConfigurationStore();
    }

    public void testOpenFails() throws Exception
    {
        String nodeName = getTestName();
        Map<String, Object> attributes = Collections.<String, Object>singletonMap(TestVirtualHostNode.NAME, nodeName);

        DurableConfigurationStore store = mock(DurableConfigurationStore.class);
        AbstractVirtualHostNode node = new TestAbstractVirtualHostNode( _broker, attributes, store);
        node.open();
        assertEquals("Unexpected node state", State.ERRORED, node.getState());
    }

    public void testOpenSucceeds() throws Exception
    {
        String nodeName = getTestName();
        Map<String, Object> attributes = Collections.<String, Object>singletonMap(TestVirtualHostNode.NAME, nodeName);

        final AtomicBoolean onFailureFlag = new AtomicBoolean();
        DurableConfigurationStore store = mock(DurableConfigurationStore.class);
        AbstractVirtualHostNode node = new TestAbstractVirtualHostNode( _broker, attributes, store)
        {
            @Override
            public void onValidate()
            {
                // no op
            }

            @Override
            protected void onExceptionInOpen(RuntimeException e)
            {
                try
                {
                    super.onExceptionInOpen(e);
                }
                finally
                {
                    onFailureFlag.set(true);
                }
            }
        };

        node.open();
        assertEquals("Unexpected node state", State.ACTIVE, node.getState());
        assertFalse("onExceptionInOpen was called", onFailureFlag.get());
    }


    public void testDeleteInErrorStateAfterOpen()
    {
        String nodeName = getTestName();
        Map<String, Object> attributes = Collections.<String, Object>singletonMap(TestVirtualHostNode.NAME, nodeName);

        final DurableConfigurationStore store = mock(DurableConfigurationStore.class);
        doThrow(new RuntimeException("Cannot open store")).when(store).openConfigurationStore(any(ConfiguredObject.class), any(boolean.class));
        AbstractStandardVirtualHostNode node = createAbstractStandardVirtualHostNode(attributes, store);
        node.open();
        assertEquals("Unexpected node state", State.ERRORED, node.getState());

        node.delete();
        assertEquals("Unexpected state", State.DELETED, node.getState());
    }

    public void testActivateInErrorStateAfterOpen() throws Exception
    {
        String nodeName = getTestName();
        Map<String, Object> attributes = Collections.<String, Object>singletonMap(TestVirtualHostNode.NAME, nodeName);

        DurableConfigurationStore store = mock(DurableConfigurationStore.class);
        doThrow(new RuntimeException("Cannot open store")).when(store).openConfigurationStore(any(ConfiguredObject.class), any(boolean.class));
        AbstractVirtualHostNode node = createAbstractStandardVirtualHostNode(attributes, store);
        node.open();
        assertEquals("Unexpected node state", State.ERRORED, node.getState());
        doNothing().when(store).openConfigurationStore(any(ConfiguredObject.class), any(boolean.class));

        node.setAttributes(Collections.<String, Object>singletonMap(VirtualHostNode.DESIRED_STATE, State.ACTIVE));
        assertEquals("Unexpected state", State.ACTIVE, node.getState());
    }

    public void testStartInErrorStateAfterOpen() throws Exception
    {
        String nodeName = getTestName();
        Map<String, Object> attributes = Collections.<String, Object>singletonMap(TestVirtualHostNode.NAME, nodeName);

        DurableConfigurationStore store = mock(DurableConfigurationStore.class);
        doThrow(new RuntimeException("Cannot open store")).when(store).openConfigurationStore(any(ConfiguredObject.class), any(boolean.class));
        AbstractVirtualHostNode node = createAbstractStandardVirtualHostNode(attributes, store);
        node.open();
        assertEquals("Unexpected node state", State.ERRORED, node.getState());
        doNothing().when(store).openConfigurationStore(any(ConfiguredObject.class), any(boolean.class));

        node.start();
        assertEquals("Unexpected state", State.ACTIVE, node.getState());
    }

    private ConfiguredObjectRecord createVirtualHostConfiguredObjectRecord(UUID virtualHostId)
    {
        Map<String, Object> virtualHostAttributes = new HashMap<>();
        virtualHostAttributes.put(VirtualHost.NAME, TEST_VIRTUAL_HOST_NAME);
        virtualHostAttributes.put(VirtualHost.TYPE, TestMemoryVirtualHost.VIRTUAL_HOST_TYPE);
        virtualHostAttributes.put(VirtualHost.MODEL_VERSION, BrokerModel.MODEL_VERSION);

        ConfiguredObjectRecord record = mock(ConfiguredObjectRecord.class);
        when(record.getId()).thenReturn(virtualHostId);
        when(record.getAttributes()).thenReturn(virtualHostAttributes);
        when(record.getType()).thenReturn(VirtualHost.class.getSimpleName());
        return record;
    }

    private NullMessageStore configStoreThatProduces(final ConfiguredObjectRecord record)
    {
        return new NullMessageStore(){

            @Override
            public void visitConfiguredObjectRecords(ConfiguredObjectRecordHandler handler) throws StoreException
            {
                handler.begin();
                if (record != null)
                {
                    handler.handle(record);
                }
                handler.end();
            }
        };
    }

    private NullMessageStore configStoreThatProducesNoRecords()
    {
        return configStoreThatProduces(null);
    }


    private AbstractStandardVirtualHostNode createAbstractStandardVirtualHostNode(final Map<String, Object> attributes, final DurableConfigurationStore store)
    {
        return new AbstractStandardVirtualHostNode(attributes,  _broker){

            @Override
            protected void writeLocationEventLog()
            {

            }

            @Override
            protected DurableConfigurationStore createConfigurationStore()
            {
                return store;
            }
        };
    }

    private class TestAbstractVirtualHostNode extends AbstractVirtualHostNode
    {
        private DurableConfigurationStore _store;

        public TestAbstractVirtualHostNode(Broker parent, Map attributes, DurableConfigurationStore store)
        {
            super(parent, attributes);
            _store = store;
        }

        @Override
        public void onValidate()
        {
            throw new RuntimeException("Cannot validate");
        }

        @Override
        protected DurableConfigurationStore createConfigurationStore()
        {
            return _store;
        }

        @Override
        protected ListenableFuture<Void> activate()
        {
            return Futures.immediateFuture(null);
        }

        @Override
        protected ConfiguredObjectRecord enrichInitialVirtualHostRootRecord(ConfiguredObjectRecord vhostRecord)
        {
            return null;
        }

        @Override
        public Collection<? extends RemoteReplicationNode> getRemoteReplicationNodes()
        {
            return null;
        }
    }
}
