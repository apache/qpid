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
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;

public class VirtualHostImplTest extends TestCase
{
    private VirtualHost _virtualHost;
    private UUID _brokerUuid = UUID.randomUUID();
    private ConfigurationChangeListener _childAddedRemovedListener = mock(ConfigurationChangeListener.class);

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        createVirtualHost();
    }

    public void testQueueChildAddedAndDeletedNotifications()
    {
        _virtualHost.addChangeListener(_childAddedRemovedListener);

        Queue queue = _virtualHost.createQueue("queue", State.INITIALISING, true, LifetimePolicy.PERMANENT, 0, EMPTY_ATTRIBUTE_MAP);

        verify(_childAddedRemovedListener).childAdded(_virtualHost, queue);
        verifyNoMoreInteractions(_childAddedRemovedListener);

        _virtualHost.deleteQueue(queue);

        verify(_childAddedRemovedListener).childRemoved(_virtualHost, queue);
        verifyNoMoreInteractions(_childAddedRemovedListener);
    }

    public void testExchangeChildAddedNotifications()
    {
        _virtualHost.addChangeListener(_childAddedRemovedListener);

        Exchange exchange = _virtualHost.createExchange("exchange", State.INITIALISING, true, LifetimePolicy.PERMANENT, 0L, "direct", EMPTY_ATTRIBUTE_MAP);

        verify(_childAddedRemovedListener).childAdded(_virtualHost, exchange);
        verifyNoMoreInteractions(_childAddedRemovedListener);
    }

    public void testQueueDeletedTwiceDisallowed()
    {
        Queue queue = _virtualHost.createQueue("queue", State.INITIALISING, true, LifetimePolicy.PERMANENT, 0, EMPTY_ATTRIBUTE_MAP);

        _virtualHost.deleteQueue(queue);

        try
        {
            _virtualHost.deleteQueue(queue);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException iae)
        {
            // PASS
        }
    }

    private void createVirtualHost()
    {
        Broker broker = new BrokerImpl(_brokerUuid,
                "broker1",
                State.INITIALISING,
                true,
                LifetimePolicy.PERMANENT,
                0l,
                EMPTY_ATTRIBUTE_MAP);

        _virtualHost = broker.createVirtualHost("vhost1", State.INITIALISING, true, LifetimePolicy.PERMANENT, 0, EMPTY_ATTRIBUTE_MAP);
    }
}
