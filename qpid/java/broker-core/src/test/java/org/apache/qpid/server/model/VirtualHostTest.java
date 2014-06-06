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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.TestMemoryVirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

public class VirtualHostTest extends QpidTestCase
{
    private Broker _broker;
    private TaskExecutor _taskExecutor;
    private VirtualHostNode<?> _virtualHostNode;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _broker = BrokerTestHelper.createBrokerMock();
        _taskExecutor = new CurrentThreadTaskExecutor();
        _taskExecutor.start();
        when(_broker.getTaskExecutor()).thenReturn(_taskExecutor);

        _virtualHostNode = mock(VirtualHostNode.class);
        when(_virtualHostNode.getConfigurationStore()).thenReturn(mock(DurableConfigurationStore.class));
        when(_virtualHostNode.getParent(Broker.class)).thenReturn(_broker);
        ConfiguredObjectFactory objectFactory = _broker.getObjectFactory();
        when(_virtualHostNode.getModel()).thenReturn(objectFactory.getModel());
        when(_virtualHostNode.getObjectFactory()).thenReturn(objectFactory);
        when(_virtualHostNode.getTaskExecutor()).thenReturn(_taskExecutor);

        when(((VirtualHostNode)_virtualHostNode).getCategoryClass()).thenReturn(VirtualHostNode.class);
    }


    @Override
    public void tearDown() throws Exception
    {
        _taskExecutor.stopImmediately();
        super.tearDown();
    }

    public void testActiveState()
    {
        VirtualHost<?,?,?> host = createHost();


        host.start();
        assertEquals("Unexpected state", State.ACTIVE, host.getAttribute(VirtualHost.STATE));
    }

    public void testDeletedState()
    {
        VirtualHost<?,?,?> host = createHost();

        host.delete();
        assertEquals("Unexpected state", State.DELETED, host.getAttribute(VirtualHost.STATE));
    }

    public void testCreateQueueChildHavingMessageGroupingAttributes()
    {
        VirtualHost<?,?,?> host = createHost();

        host.start();

        String queueName = getTestName();
        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put(Queue.MESSAGE_GROUP_KEY, "mykey");
        arguments.put(Queue.MESSAGE_GROUP_SHARED_GROUPS, true);
        arguments.put(Queue.NAME, queueName);

        host.createChild(Queue.class, arguments);

        Queue<?> queue = host.getChildByName(Queue.class, queueName);
        Object messageGroupKey = queue.getAttribute(Queue.MESSAGE_GROUP_KEY);
        assertEquals("Unexpected message group key attribute", "mykey", messageGroupKey);

        Object sharedGroups = queue.getAttribute(Queue.MESSAGE_GROUP_SHARED_GROUPS);
        assertEquals("Unexpected shared groups attribute", true, sharedGroups);

    }

    private VirtualHost<?,?,?> createHost()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(VirtualHost.NAME, getName());
        attributes.put(VirtualHost.TYPE, TestMemoryVirtualHost.VIRTUAL_HOST_TYPE);

        VirtualHost<?,?,?> host = createHost(attributes);
        return host;
    }

    private VirtualHost<?,?,?> createHost(Map<String, Object> attributes)
    {
        attributes = new HashMap<String, Object>(attributes);
        attributes.put(VirtualHost.ID, UUID.randomUUID());
        TestMemoryVirtualHost host= new TestMemoryVirtualHost(attributes, _virtualHostNode);
        host.create();
        return host;
    }

}
