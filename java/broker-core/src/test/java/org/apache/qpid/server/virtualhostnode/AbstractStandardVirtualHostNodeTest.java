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

import static org.apache.qpid.server.virtualhostnode.AbstractStandardVirtualHostNode.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObjectFactoryImpl;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
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

    private UUID _nodeId = UUID.randomUUID();
    private Broker<?> _broker;
    private TaskExecutor _taskExecutor;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _broker = BrokerTestHelper.createBrokerMock();
        SystemContext<?> systemContext = _broker.getParent(SystemContext.class);
        when(systemContext.getObjectFactory()).thenReturn(new ConfiguredObjectFactoryImpl(mock(Model.class)));

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
        DurableConfigurationStore configStore = configStoreThatProducesNoRecords();

        String vhBlueprint = String.format("{ \"type\" : \"%s\", \"name\" : \"%s\"}",
                                           TestMemoryVirtualHost.VIRTUAL_HOST_TYPE,
                                           TEST_VIRTUAL_HOST_NAME);
        Map<String, String> context = Collections.singletonMap(VIRTUALHOST_BLUEPRINT_CONTEXT_VAR, vhBlueprint);

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

        Map<String, String> updatedContext = node.getContext();

        assertTrue("Context should now have utilised flag", updatedContext.containsKey(
                VIRTUALHOST_BLUEPRINT_UTILISED_CONTEXT_VAR));
        assertEquals("Utilised flag should be true",
                     Boolean.TRUE.toString(),
                     updatedContext.get(VIRTUALHOST_BLUEPRINT_UTILISED_CONTEXT_VAR));
    }

    /**
     *  Tests activating a virtualhostnode with blueprint context variable and the
     *  marked utilised flag.  Config store does not specify a virtualhost.
     *  Checks virtualhost is not recreated from the blueprint.
     */
    public void testActivateVHNWithVHBlueprintUsed_StoreHasNoVH() throws Exception
    {
        DurableConfigurationStore configStore = configStoreThatProducesNoRecords();

        String vhBlueprint = String.format("{ \"type\" : \"%s\", \"name\" : \"%s\"}",
                                           TestMemoryVirtualHost.VIRTUAL_HOST_TYPE,
                                           TEST_VIRTUAL_HOST_NAME);
        Map<String, String> context = new HashMap<>();
        context.put(VIRTUALHOST_BLUEPRINT_CONTEXT_VAR, vhBlueprint);
        context.put(VIRTUALHOST_BLUEPRINT_UTILISED_CONTEXT_VAR, Boolean.TRUE.toString());

        Map<String, Object> nodeAttributes = new HashMap<>();
        nodeAttributes.put(VirtualHostNode.NAME, TEST_VIRTUAL_HOST_NODE_NAME);
        nodeAttributes.put(VirtualHostNode.ID, _nodeId);
        nodeAttributes.put(VirtualHostNode.CONTEXT, context);

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
        Map<String, String> context = Collections.singletonMap(VIRTUALHOST_BLUEPRINT_CONTEXT_VAR, vhBlueprint);

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

}
