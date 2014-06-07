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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    private DurableConfigurationStore _configStore;
    private ConfiguredObjectRecord _record;
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
    public void testActivationOpensConfigStoreWithExistingVirtualHostRecord() throws Exception
    {
        UUID virtualHostId = UUID.randomUUID();
        _record = createMockVirtualHostCOR(virtualHostId);

        _configStore = new NullMessageStore(){

            @Override
            public void visitConfiguredObjectRecords(ConfiguredObjectRecordHandler handler) throws StoreException
            {
                handler.begin();
                handler.handle(_record);
                handler.end();
            }
        };

        Map<String, Object> nodeAttributes = new HashMap<String, Object>();
        nodeAttributes.put(VirtualHostNode.NAME, TEST_VIRTUAL_HOST_NODE_NAME);
        nodeAttributes.put(VirtualHostNode.ID, _nodeId);

        VirtualHostNode<?> node = new TestVirtualHostNode(_broker, nodeAttributes, _configStore);
        node.open();
        node.start();

        VirtualHost<?, ?, ?> virtualHost = node.getVirtualHost();
        assertNotNull("Virtual host was not recovered", virtualHost);
        assertEquals("Unexpected virtual host name", TEST_VIRTUAL_HOST_NAME, virtualHost.getName());
        assertEquals("Unexpected virtual host state", State.ACTIVE, virtualHost.getState());
        assertEquals("Unexpected virtual host id", virtualHostId, virtualHost.getId());
    }

    public void testActivationOpensConfigStoreWithoutVirtualHostRecord() throws Exception
    {
        _configStore = new NullMessageStore() {

            @Override
            public void visitConfiguredObjectRecords(ConfiguredObjectRecordHandler handler) throws StoreException
            {
                handler.begin();
                // No records
                handler.end();
            }
        };

        Map<String, Object> nodeAttributes = new HashMap<String, Object>();
        nodeAttributes.put(VirtualHostNode.NAME, TEST_VIRTUAL_HOST_NODE_NAME);
        nodeAttributes.put(VirtualHostNode.ID, _nodeId);

        VirtualHostNode<?> node = new TestVirtualHostNode(_broker, nodeAttributes, _configStore);
        node.open();
        node.start();

        VirtualHost<?, ?, ?> virtualHost = node.getVirtualHost();
        assertNull("Virtual host should not be automatically created", virtualHost);

    }

    private ConfiguredObjectRecord createMockVirtualHostCOR(UUID virtualHostId)
    {
        Map<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put(VirtualHost.NAME, TEST_VIRTUAL_HOST_NAME);
        virtualHostAttributes.put(VirtualHost.TYPE, TestMemoryVirtualHost.VIRTUAL_HOST_TYPE);
        virtualHostAttributes.put(VirtualHost.MODEL_VERSION, BrokerModel.MODEL_VERSION);

        ConfiguredObjectRecord record = mock(ConfiguredObjectRecord.class);
        when(record.getId()).thenReturn(virtualHostId);
        when(record.getAttributes()).thenReturn(virtualHostAttributes);
        when(record.getType()).thenReturn(VirtualHost.class.getSimpleName());
        return record;
    }
}
