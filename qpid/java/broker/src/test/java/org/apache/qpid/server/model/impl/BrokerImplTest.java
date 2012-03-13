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

package org.apache.qpid.server.model.impl;

import static org.apache.qpid.server.model.impl.AbstractConfiguredObject.EMPTY_ATTRIBUTE_MAP;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;

public class BrokerImplTest extends TestCase
{
    private Broker _broker;
    private UUID _brokerUuid = UUID.randomUUID();
    private ConfigurationChangeListener _childAddedRemovedListener = mock(ConfigurationChangeListener.class);

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        createBroker();
    }

    public void testVirtualHostChildAddedAndDeletedNotifications()
    {
        _broker.addChangeListener(_childAddedRemovedListener);

        VirtualHost createdVirtualHost = _broker.createVirtualHost("vhost", State.INITIALISING, true, LifetimePolicy.PERMANENT, 0, EMPTY_ATTRIBUTE_MAP);

        verify(_childAddedRemovedListener).childAdded(_broker, createdVirtualHost);
        verifyNoMoreInteractions(_childAddedRemovedListener);

        _broker.deleteVirtualHost(createdVirtualHost);
        verify(_childAddedRemovedListener).childRemoved(_broker, createdVirtualHost);

        verifyNoMoreInteractions(_childAddedRemovedListener);
    }

    public void testVirtualHostDeleteUnknownDisallowed()
    {
        try
        {
            _broker.deleteVirtualHost(mock(VirtualHost.class));
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException iae)
        {
            // PASS
        }
    }

    public void testVirtualHostDeletedTwiceDisallowed()
    {
        VirtualHost createdVirtualHost = _broker.createVirtualHost("vhost", State.INITIALISING, true, LifetimePolicy.PERMANENT, 0, EMPTY_ATTRIBUTE_MAP);
        _broker.deleteVirtualHost(createdVirtualHost);

        try
        {
            _broker.deleteVirtualHost(createdVirtualHost);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException iae)
        {
            // PASS
        }
    }

    private void createBroker()
    {
        _broker = new BrokerImpl(_brokerUuid,
                "broker1",
                State.INITIALISING,
                true,
                LifetimePolicy.PERMANENT,
                0l,
                EMPTY_ATTRIBUTE_MAP);
    }
}
