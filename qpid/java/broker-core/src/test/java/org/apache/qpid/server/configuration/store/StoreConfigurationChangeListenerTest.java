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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

public class StoreConfigurationChangeListenerTest extends QpidTestCase
{
    private ConfigurationEntryStore _store;
    private StoreConfigurationChangeListener _listener;

    protected void setUp() throws Exception
    {
        super.setUp();
        _store = mock(ConfigurationEntryStore.class);
        _listener = new StoreConfigurationChangeListener(_store);
    }

    public void testStateChanged()
    {
        notifyBrokerStarted();
        UUID id = UUID.randomUUID();
        ConfiguredObject object = mock(VirtualHost.class);
        when(object.getId()).thenReturn(id);
        _listener.stateChanged(object, State.ACTIVE, State.DELETED);
        verify(_store).remove(id);
    }

    public void testChildAdded()
    {
        notifyBrokerStarted();
        Broker broker = mock(Broker.class);
        when(broker.getCategoryClass()).thenReturn(Broker.class);
        VirtualHost child = mock(VirtualHost.class);
        when(child.getCategoryClass()).thenReturn(VirtualHost.class);
        _listener.childAdded(broker, child);
        verify(_store).save(any(ConfigurationEntry.class), any(ConfigurationEntry.class));
    }

    public void testChildRemoved()
    {
        notifyBrokerStarted();
        Broker broker = mock(Broker.class);
        when(broker.getCategoryClass()).thenReturn(Broker.class);
        VirtualHost child = mock(VirtualHost.class);
        when(child.getCategoryClass()).thenReturn(VirtualHost.class);
        _listener.childRemoved(broker, child);
        verify(_store).save(any(ConfigurationEntry.class));
    }

    public void testAttributeSet()
    {
        notifyBrokerStarted();
        Broker broker = mock(Broker.class);
        when(broker.getCategoryClass()).thenReturn(Broker.class);
        _listener.attributeSet(broker, Broker.QUEUE_FLOW_CONTROL_SIZE_BYTES, null, 1);
        verify(_store).save(any(ConfigurationEntry.class));
    }

    public void testChildAddedForVirtualHost()
    {
        notifyBrokerStarted();

        VirtualHost object = mock(VirtualHost.class);
        Queue queue = mock(Queue.class);
        _listener.childAdded(object, queue);
        verifyNoMoreInteractions(_store);
    }

    private void notifyBrokerStarted()
    {
        Broker broker = mock(Broker.class);
        _listener.stateChanged(broker, State.INITIALISING, State.ACTIVE);
    }
}
