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

import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.mockito.ArgumentMatcher;

import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.TestMemoryVirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

public class VirtualHostTest extends QpidTestCase
{
    private Broker _broker;
    private TaskExecutor _taskExecutor;
    private VirtualHostNode<?> _virtualHostNode;
    private DurableConfigurationStore _configStore;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _broker = BrokerTestHelper.createBrokerMock();

        _taskExecutor = new CurrentThreadTaskExecutor();
        _taskExecutor.start();
        when(_broker.getTaskExecutor()).thenReturn(_taskExecutor);

        _virtualHostNode = mock(VirtualHostNode.class);
        _configStore = mock(DurableConfigurationStore.class);
        when(_virtualHostNode.getConfigurationStore()).thenReturn(_configStore);

        // Virtualhost needs the EventLogger from the SystemContext.
        when(_virtualHostNode.getParent(Broker.class)).thenReturn(_broker);

        ConfiguredObjectFactory objectFactory = _broker.getObjectFactory();
        when(_virtualHostNode.getModel()).thenReturn(objectFactory.getModel());
        when(_virtualHostNode.getTaskExecutor()).thenReturn(_taskExecutor);
    }

    @Override
    public void tearDown() throws Exception
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

    public void testNewVirtualHost()
    {
        String virtualHostName = getName();
        VirtualHost<?,?,?> virtualHost = createVirtualHost(virtualHostName);

        assertNotNull("Unexpected id", virtualHost.getId());
        assertEquals("Unexpected name", virtualHostName, virtualHost.getName());
        assertEquals("Unexpected state", State.ACTIVE, virtualHost.getState());

        verify(_configStore).create(matchesRecord(virtualHost.getId(), virtualHost.getType()));
    }

    public void testDeleteVirtualHost()
    {
        VirtualHost<?,?,?> virtualHost = createVirtualHost(getName());
        assertEquals("Unexpected state", State.ACTIVE, virtualHost.getState());

        virtualHost.delete();

        assertEquals("Unexpected state", State.DELETED, virtualHost.getState());
        verify(_configStore).remove(matchesRecord(virtualHost.getId(), virtualHost.getType()));
    }

    public void testDeleteDefaultVirtualHostIsDisallowed()
    {
        String virtualHostName = getName();
        when(_broker.getDefaultVirtualHost()).thenReturn(virtualHostName);

        VirtualHost<?,?,?> virtualHost = createVirtualHost(virtualHostName);

        try
        {
            virtualHost.delete();
            fail("Exception not thrown");
        }
        catch(IntegrityViolationException ive)
        {
            // PASS
        }

        assertEquals("Unexpected state", State.ACTIVE, virtualHost.getState());
        verify(_configStore, never()).remove(matchesRecord(virtualHost.getId(), virtualHost.getType()));
    }

    public void testStopAndStartVirtualHost()
    {
        String virtualHostName = getName();

        VirtualHost<?,?,?> virtualHost = createVirtualHost(virtualHostName);
        assertEquals("Unexpected state", State.ACTIVE, virtualHost.getState());

        virtualHost.stop();
        assertEquals("Unexpected state", State.STOPPED, virtualHost.getState());

        virtualHost.start();
        assertEquals("Unexpected state", State.ACTIVE, virtualHost.getState());

        verify(_configStore, times(1)).create(matchesRecord(virtualHost.getId(), virtualHost.getType()));
        verify(_configStore, times(2)).update(eq(false), matchesRecord(virtualHost.getId(), virtualHost.getType()));
    }

    public void testCreateDurableQueue()
    {
        String virtualHostName = getName();
        VirtualHost<?,?,?> virtualHost = createVirtualHost(virtualHostName);

        String queueName = "myQueue";
        Map<String, Object> arguments = new HashMap<>();
        arguments.put(Queue.NAME, queueName);
        arguments.put(Queue.DURABLE, Boolean.TRUE);

        Queue queue = virtualHost.createChild(Queue.class, arguments);
        assertNotNull(queue.getId());
        assertEquals(queueName, queue.getName());

        verify(_configStore).create(matchesRecord(queue.getId(), queue.getType()));
    }

    public void testCreateNonDurableQueue()
    {
        String virtualHostName = getName();
        VirtualHost<?,?,?> virtualHost = createVirtualHost(virtualHostName);

        String queueName = "myQueue";
        Map<String, Object> arguments = new HashMap<>();
        arguments.put(Queue.NAME, queueName);
        arguments.put(Queue.DURABLE, Boolean.FALSE);

        Queue queue = virtualHost.createChild(Queue.class, arguments);
        assertNotNull(queue.getId());
        assertEquals(queueName, queue.getName());

        verify(_configStore, never()).create(matchesRecord(queue.getId(), queue.getType()));
    }

    private VirtualHost<?,?,?> createVirtualHost(final String virtualHostName)
    {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(VirtualHost.NAME, virtualHostName);
        attributes.put(VirtualHost.TYPE, TestMemoryVirtualHost.VIRTUAL_HOST_TYPE);

        TestMemoryVirtualHost host = new TestMemoryVirtualHost(attributes, _virtualHostNode);
        host.create();
        return host;
    }

    private static ConfiguredObjectRecord matchesRecord(UUID id, String type)
    {
        return argThat(new MinimalConfiguredObjectRecordMatcher(id, type));
    }

    private static class MinimalConfiguredObjectRecordMatcher extends ArgumentMatcher<ConfiguredObjectRecord>
    {
        private final UUID _id;
        private final String _type;

        private MinimalConfiguredObjectRecordMatcher(UUID id, String type)
        {
            _id = id;
            _type = type;
        }

        @Override
        public boolean matches(Object argument)
        {
            ConfiguredObjectRecord rhs = (ConfiguredObjectRecord) argument;
            return (_id.equals(rhs.getId()) || _type.equals(rhs.getType()));
        }
    }
}
