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

import static java.util.Arrays.asList;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.configuration.store.StoreConfigurationChangeListener;
import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.connection.IConnectionRegistry.RegistryChangeListener;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.TestMemoryVirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

public class VirtualHostTest extends QpidTestCase
{
    private final SecurityManager _mockSecurityManager = mock(SecurityManager.class);
    private Broker _broker;
    private TaskExecutor _taskExecutor;
    private VirtualHostNode _virtualHostNode;
    private DurableConfigurationStore _configStore;
    private VirtualHost<?, ?, ?> _virtualHost;
    private StoreConfigurationChangeListener _storeConfigurationChangeListener;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _broker = BrokerTestHelper.createBrokerMock();

        _taskExecutor = new CurrentThreadTaskExecutor();
        _taskExecutor.start();
        when(_broker.getTaskExecutor()).thenReturn(_taskExecutor);
        when(_broker.getChildExecutor()).thenReturn(_taskExecutor);

        _virtualHostNode = mock(VirtualHostNode.class);
        when(_virtualHostNode.getParent(Broker.class)).thenReturn(_broker);
        when(_virtualHostNode.getCategoryClass()).thenReturn(VirtualHostNode.class);
        when(_virtualHostNode.isDurable()).thenReturn(true);
        _configStore = mock(DurableConfigurationStore.class);
        _storeConfigurationChangeListener = new StoreConfigurationChangeListener(_configStore);

        when(_virtualHostNode.getConfigurationStore()).thenReturn(_configStore);


        // Virtualhost needs the EventLogger from the SystemContext.
        when(_virtualHostNode.getParent(Broker.class)).thenReturn(_broker);

        ConfiguredObjectFactory objectFactory = _broker.getObjectFactory();
        when(_virtualHostNode.getModel()).thenReturn(objectFactory.getModel());
        when(_virtualHostNode.getTaskExecutor()).thenReturn(_taskExecutor);
        when(_virtualHostNode.getChildExecutor()).thenReturn(_taskExecutor);

    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            try
            {
                _taskExecutor.stopImmediately();
            }
            finally
            {
                if (_virtualHost != null)
                {
                    _virtualHost.close();
                }
            }
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

        verify(_configStore).update(eq(true),matchesRecord(virtualHost.getId(), virtualHost.getType()));
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

        verify(_configStore, times(1)).update(eq(true), matchesRecord(virtualHost.getId(), virtualHost.getType()));
        verify(_configStore, times(2)).update(eq(false), matchesRecord(virtualHost.getId(), virtualHost.getType()));
    }

    public void testRestartingVirtualHostRecoversChildren()
    {
        String virtualHostName = getName();

        VirtualHost<?,?,?> virtualHost = createVirtualHost(virtualHostName);
        assertEquals("Unexpected state", State.ACTIVE, virtualHost.getState());
        final ConfiguredObjectRecord virtualHostCor = virtualHost.asObjectRecord();

        // Give virtualhost a queue and an exchange
        Queue queue = virtualHost.createChild(Queue.class, Collections.<String, Object>singletonMap(Queue.NAME, "myQueue"));
        final ConfiguredObjectRecord queueCor = queue.asObjectRecord();

        Map<String, Object> exchangeArgs = new HashMap<>();
        exchangeArgs.put(Exchange.NAME, "myExchange");
        exchangeArgs.put(Exchange.TYPE, ExchangeDefaults.DIRECT_EXCHANGE_CLASS);

        Exchange exchange = virtualHost.createChild(Exchange.class, exchangeArgs);
        final ConfiguredObjectRecord exchangeCor = exchange.asObjectRecord();

        assertEquals("Unexpected number of queues before stop", 1, virtualHost.getChildren(Queue.class).size());
        assertEquals("Unexpected number of exchanges before stop", 5, virtualHost.getChildren(Exchange.class).size());

        virtualHost.stop();
        assertEquals("Unexpected state", State.STOPPED, virtualHost.getState());
        assertEquals("Unexpected number of queues after stop", 0, virtualHost.getChildren(Queue.class).size());
        assertEquals("Unexpected number of exchanges after stop", 0, virtualHost.getChildren(Exchange.class).size());

        // Setup an answer that will return the configured object records
        doAnswer(new Answer()
        {
            final Iterator<ConfiguredObjectRecord> corIterator = asList(queueCor, exchangeCor, virtualHostCor).iterator();

            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable
            {
                ConfiguredObjectRecordHandler handler = (ConfiguredObjectRecordHandler) invocation.getArguments()[0];
                boolean handlerContinue = true;
                while(corIterator.hasNext() && handlerContinue)
                {
                    handlerContinue = handler.handle(corIterator.next());
                }

                return null;
            }
        }).when(_configStore).visitConfiguredObjectRecords(any(ConfiguredObjectRecordHandler.class));

        virtualHost.start();
        assertEquals("Unexpected state", State.ACTIVE, virtualHost.getState());

        assertEquals("Unexpected number of queues after restart", 1, virtualHost.getChildren(Queue.class).size());
        assertEquals("Unexpected number of exchanges after restart", 5, virtualHost.getChildren(Exchange.class).size());
    }


    public void testStopVirtualHost_ClosesConnections()
    {
        String virtualHostName = getName();

        VirtualHost<?, ?, ?> virtualHost = createVirtualHost(virtualHostName);
        assertEquals("Unexpected state", State.ACTIVE, virtualHost.getState());

        AMQConnectionModel connection = createMockProtocolConnection(virtualHost);

        assertEquals("Unexpected number of connections before connection registered", 0, virtualHost.getChildren(Connection.class).size());

        ((RegistryChangeListener)virtualHost).connectionRegistered(connection);

        assertEquals("Unexpected number of connections after connection registered", 1, virtualHost.getChildren(
                Connection.class).size());

        virtualHost.stop();
        assertEquals("Unexpected state", State.STOPPED, virtualHost.getState());

        assertEquals("Unexpected number of connections after virtualhost stopped",
                     0,
                     virtualHost.getChildren(Connection.class).size());

        verify(connection).closeAsync(AMQConstant.CONNECTION_FORCED, "Connection closed by external action");
    }

    public void testDeleteVirtualHost_ClosesConnections()
    {
        String virtualHostName = getName();

        VirtualHost<?, ?, ?> virtualHost = createVirtualHost(virtualHostName);
        assertEquals("Unexpected state", State.ACTIVE, virtualHost.getState());

        AMQConnectionModel connection = createMockProtocolConnection(virtualHost);

        assertEquals("Unexpected number of connections before connection registered", 0, virtualHost.getChildren(Connection.class).size());

        ((RegistryChangeListener)virtualHost).connectionRegistered(connection);

        assertEquals("Unexpected number of connections after connection registered", 1, virtualHost.getChildren(Connection.class).size());

        virtualHost.delete();
        assertEquals("Unexpected state", State.DELETED, virtualHost.getState());

        assertEquals("Unexpected number of connections after virtualhost deleted",
                     0,
                     virtualHost.getChildren(Connection.class).size());

        verify(connection).closeAsync(AMQConstant.CONNECTION_FORCED, "Connection closed by external action");
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

        verify(_configStore).update(eq(true),matchesRecord(queue.getId(), queue.getType()));
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

    // ***************  VH Access Control Tests  ***************

    public void testUpdateDeniedByACL()
    {
        when(_broker.getSecurityManager()).thenReturn(_mockSecurityManager);

        String virtualHostName = getName();
        VirtualHost<?,?,?> virtualHost = createVirtualHost(virtualHostName);

        doThrow(new AccessControlException("mocked ACL exception")).when(_mockSecurityManager).authoriseUpdate(virtualHost);

        assertNull(virtualHost.getDescription());

        try
        {
            virtualHost.setAttribute(VirtualHost.DESCRIPTION, null, "My description");
            fail("Exception not thrown");
        }
        catch (AccessControlException ace)
        {
            // PASS
        }

        verify(_configStore, never()).update(eq(false), matchesRecord(virtualHost.getId(), virtualHost.getType()));
    }

    public void testStopDeniedByACL()
    {
        when(_broker.getSecurityManager()).thenReturn(_mockSecurityManager);

        String virtualHostName = getName();
        VirtualHost<?,?,?> virtualHost = createVirtualHost(virtualHostName);

        doThrow(new AccessControlException("mocked ACL exception")).when(_mockSecurityManager).authoriseUpdate(virtualHost);

        try
        {
            virtualHost.stop();
            fail("Exception not thrown");
        }
        catch (AccessControlException ace)
        {
            // PASS
        }

        verify(_configStore, never()).update(eq(false), matchesRecord(virtualHost.getId(), virtualHost.getType()));
    }

    public void testDeleteDeniedByACL()
    {
        when(_broker.getSecurityManager()).thenReturn(_mockSecurityManager);

        String virtualHostName = getName();
        VirtualHost<?,?,?> virtualHost = createVirtualHost(virtualHostName);

        doThrow(new AccessControlException("mocked ACL exception")).when(_mockSecurityManager).authoriseDelete(virtualHost);

        try
        {
            virtualHost.delete();
            fail("Exception not thrown");
        }
        catch (AccessControlException ace)
        {
            // PASS
        }

        verify(_configStore, never()).remove(matchesRecord(virtualHost.getId(), virtualHost.getType()));
    }

    private VirtualHost<?,?,?> createVirtualHost(final String virtualHostName)
    {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(VirtualHost.NAME, virtualHostName);
        attributes.put(VirtualHost.TYPE, TestMemoryVirtualHost.VIRTUAL_HOST_TYPE);

        TestMemoryVirtualHost host = new TestMemoryVirtualHost(attributes, _virtualHostNode);
        host.addChangeListener(_storeConfigurationChangeListener);
        host.create();
        // Fire the child added event on the node
        _storeConfigurationChangeListener.childAdded(_virtualHostNode,host);
        _virtualHost = host;
        return host;
    }

    private AMQConnectionModel createMockProtocolConnection(final VirtualHost<?, ?, ?> virtualHost)
    {
        final AMQConnectionModel connection = mock(AMQConnectionModel.class);
        final List<Action<?>> tasks = new ArrayList<>();
        final ArgumentCaptor<Action> deleteTaskCaptor = ArgumentCaptor.forClass(Action.class);
        Answer answer = new Answer()
        {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable
            {
                return tasks.add(deleteTaskCaptor.getValue());
            }
        };
        doAnswer(answer).when(connection).addDeleteTask(deleteTaskCaptor.capture());
        when(connection.getVirtualHost()).thenReturn(virtualHost);
        doAnswer(new Answer()
        {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable
            {
                for(Action action : tasks)
                {
                    action.performAction(connection);
                }
                return null;
            }
        }).when(connection).closeAsync(any(AMQConstant.class),anyString());
        when(connection.getRemoteAddressString()).thenReturn("peer:1234");
        return connection;
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
