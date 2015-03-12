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
package org.apache.qpid.server.virtualhost;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutorImpl;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.SystemConfig;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.test.utils.QpidTestCase;

public class AbstractVirtualHostTest extends QpidTestCase
{
    private TaskExecutor _taskExecutor;
    private VirtualHostNode _node;
    private MessageStore _failingStore;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        SystemConfig systemConfig = mock(SystemConfig.class);
        when(systemConfig.getEventLogger()).thenReturn(mock(EventLogger.class));
        Broker<?> broker = mock(Broker.class);
        when(broker.getParent(SystemConfig.class)).thenReturn(systemConfig);
        when(broker.getModel()).thenReturn(BrokerModel.getInstance());
        when(broker.getSecurityManager()).thenReturn(new SecurityManager(broker, false));

        _taskExecutor = new TaskExecutorImpl();
        _taskExecutor.start();
        when(broker.getTaskExecutor()).thenReturn(_taskExecutor);

        _node = mock(VirtualHostNode.class);
        when(_node.getParent(Broker.class)).thenReturn(broker);
        when(_node.getModel()).thenReturn(BrokerModel.getInstance());
        when(_node.getTaskExecutor()).thenReturn(_taskExecutor);
        when(_node.getConfigurationStore()).thenReturn(mock(DurableConfigurationStore.class));
        when(_node.getCategoryClass()).thenReturn(VirtualHostNode.class);

        _failingStore = mock(MessageStore.class);
        doThrow(new RuntimeException("Cannot open store")).when(_failingStore).openMessageStore(any(ConfiguredObject.class));
    }

    @Override
    public void  tearDown() throws Exception
    {
        try
        {
            if (_taskExecutor != null)
            {
                _taskExecutor.stop();
            }
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testValidateOnCreateFails()
    {
        Map<String,Object> attributes = Collections.<String, Object>singletonMap(AbstractVirtualHost.NAME, getTestName());

        AbstractVirtualHost host = new AbstractVirtualHost(attributes, _node)
        {
            @Override
            protected MessageStore createMessageStore()
            {
                return _failingStore;
            }
        };

        try
        {
            host.validateOnCreate();
            fail("Validation on creation should fail");
        }
        catch(IllegalConfigurationException e)
        {
            assertTrue("Unexpected exception " + e.getMessage(), e.getMessage().startsWith("Cannot open virtual host message store"));
        }
    }

    public void testValidateOnCreateSucceeds()
    {
        Map<String,Object> attributes = Collections.<String, Object>singletonMap(AbstractVirtualHost.NAME, getTestName());
        final MessageStore store = mock(MessageStore.class);
        AbstractVirtualHost host = new AbstractVirtualHost(attributes, _node)
        {
            @Override
            protected MessageStore createMessageStore()
            {
                return store;
            }
        };

        host.validateOnCreate();
        verify(store).openMessageStore(host);
        verify(store).closeMessageStore();
    }

    public void testOpenFails()
    {
        Map<String,Object> attributes = Collections.<String, Object>singletonMap(AbstractVirtualHost.NAME, getTestName());

        AbstractVirtualHost host = new AbstractVirtualHost(attributes, _node)
        {
            @Override
            protected MessageStore createMessageStore()
            {
                return _failingStore;
            }
        };

        host.open();
        assertEquals("Unexpected host state", State.ERRORED, host.getState());
    }

    public void testOpenSucceeds()
    {
        Map<String,Object> attributes = Collections.<String, Object>singletonMap(AbstractVirtualHost.NAME, getTestName());
        final MessageStore store = mock(MessageStore.class);
        AbstractVirtualHost host = new AbstractVirtualHost(attributes, _node)
        {
            @Override
            protected MessageStore createMessageStore()
            {
                return  store;
            }
        };

        host.open();
        assertEquals("Unexpected host state", State.ACTIVE, host.getState());
        verify(store).openMessageStore(host);

        // make sure that method AbstractVirtualHost.onExceptionInOpen was not called
        verify(store, times(0)).closeMessageStore();
    }

    public void testDeleteInErrorStateAfterOpen() throws Exception
    {
        Map<String,Object> attributes = Collections.<String, Object>singletonMap(AbstractVirtualHost.NAME, getTestName());
        AbstractVirtualHost host = new AbstractVirtualHost(attributes, _node)
        {
            @Override
            protected MessageStore createMessageStore()
            {
                return  _failingStore;
            }
        };

        host.open();

        assertEquals("Unexpected state", State.ERRORED, host.getState());

        host.delete();
        assertEquals("Unexpected state", State.DELETED, host.getState());
    }

    public void testActivateInErrorStateAfterOpen() throws Exception
    {
        Map<String,Object> attributes = Collections.<String, Object>singletonMap(AbstractVirtualHost.NAME, getTestName());
        final MessageStore store = mock(MessageStore.class);
        doThrow(new RuntimeException("Cannot open store")).when(store).openMessageStore(any(ConfiguredObject.class));
        AbstractVirtualHost host = new AbstractVirtualHost(attributes, _node)
        {
            @Override
            protected MessageStore createMessageStore()
            {
                return  store;
            }
        };

        host.open();
        assertEquals("Unexpected state", State.ERRORED, host.getState());

        doNothing().when(store).openMessageStore(any(ConfiguredObject.class));

        host.setAttributes(Collections.<String, Object>singletonMap(VirtualHost.DESIRED_STATE, State.ACTIVE));
        assertEquals("Unexpected state", State.ACTIVE, host.getState());
    }

    public void testStartInErrorStateAfterOpen() throws Exception
    {
        Map<String,Object> attributes = Collections.<String, Object>singletonMap(AbstractVirtualHost.NAME, getTestName());
        final MessageStore store = mock(MessageStore.class);
        doThrow(new RuntimeException("Cannot open store")).when(store).openMessageStore(any(ConfiguredObject.class));
        AbstractVirtualHost host = new AbstractVirtualHost(attributes, _node)
        {
            @Override
            protected MessageStore createMessageStore()
            {
                return  store;
            }
        };

        host.open();
        assertEquals("Unexpected state", State.ERRORED, host.getState());

        doNothing().when(store).openMessageStore(any(ConfiguredObject.class));

        host.start();
        assertEquals("Unexpected state", State.ACTIVE, host.getState());
    }
}
