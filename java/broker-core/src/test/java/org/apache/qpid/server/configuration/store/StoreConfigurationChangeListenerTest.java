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

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.test.utils.QpidTestCase;

public class StoreConfigurationChangeListenerTest extends QpidTestCase
{
    private DurableConfigurationStore _store;
    private StoreConfigurationChangeListener _listener;

    protected void setUp() throws Exception
    {
        super.setUp();
        _store = mock(DurableConfigurationStore.class);
        _listener = new StoreConfigurationChangeListener(_store);
    }

    public void testStateChanged()
    {
        notifyBrokerStarted();
        UUID id = UUID.randomUUID();
        ConfiguredObject object = mock(VirtualHost.class);
        when(object.getId()).thenReturn(id);
        ConfiguredObjectRecord record = mock(ConfiguredObjectRecord.class);
        when(object.asObjectRecord()).thenReturn(record);
        _listener.stateChanged(object, State.ACTIVE, State.DELETED);
        verify(_store).remove(record);
    }

    public void testChildAdded()
    {
        notifyBrokerStarted();
        Broker broker = mock(Broker.class);
        when(broker.getCategoryClass()).thenReturn(Broker.class);
        VirtualHost child = mock(VirtualHost.class);
        when(child.getCategoryClass()).thenReturn(VirtualHost.class);
        _listener.childAdded(broker, child);
        verify(_store).update(eq(true), any(ConfiguredObjectRecord.class));
    }

    public void testAttributeSet()
    {
        notifyBrokerStarted();
        Broker broker = mock(Broker.class);
        when(broker.getCategoryClass()).thenReturn(Broker.class);
        _listener.attributeSet(broker, Broker.CONNECTION_SESSION_COUNT_LIMIT, null, 1);
        verify(_store).update(eq(false),any(ConfiguredObjectRecord.class));
    }

    public void testChildAddedForVirtualHostNode()
    {
        notifyBrokerStarted();

        VirtualHostNode<?> object = mock(VirtualHostNode.class);
        VirtualHost<?,?,?> virtualHost = mock(VirtualHost.class);
        _listener.childAdded(object, virtualHost);
        verifyNoMoreInteractions(_store);
    }

    private void notifyBrokerStarted()
    {
        Broker broker = mock(Broker.class);
        _listener.stateChanged(broker, State.UNINITIALIZED, State.ACTIVE);
    }
}
