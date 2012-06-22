/*
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
 */
package org.apache.qpid.server.jmx.mbeans;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collections;

import javax.management.OperationsException;

import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.VirtualHost;

import junit.framework.TestCase;

public class QueueMBeanTest extends TestCase
{
    private static final String QUEUE_NAME = "QUEUE_NAME";
    private static final String QUEUE_DESCRIPTION = "QUEUE_DESCRIPTION";
    private static final String  QUEUE_TYPE = "QUEUE_TYPE";
    private static final String QUEUE_ALTERNATE_EXCHANGE = "QUEUE_ALTERNATE_EXCHANGE";

    private Queue _mockQueue;
    private VirtualHostMBean _mockVirtualHostMBean;
    private ManagedObjectRegistry _mockManagedObjectRegistry;
    private QueueMBean _queueMBean;

    @Override
    protected void setUp() throws Exception
    {
        _mockQueue = mock(Queue.class);
        _mockVirtualHostMBean = mock(VirtualHostMBean.class);

        _mockManagedObjectRegistry = mock(ManagedObjectRegistry.class);
        when(_mockVirtualHostMBean.getRegistry()).thenReturn(_mockManagedObjectRegistry);

        _queueMBean = new QueueMBean(_mockQueue, _mockVirtualHostMBean);
    }

    public void testQueueName()
    {
        when(_mockQueue.getName()).thenReturn(QUEUE_NAME);

        assertEquals(QUEUE_NAME, _queueMBean.getName());
    }

    public void testGetQueueDescription()
    {
        when(_mockQueue.getAttribute(Queue.DESCRIPTION)).thenReturn(QUEUE_DESCRIPTION);

        assertEquals(QUEUE_DESCRIPTION, _queueMBean.getDescription());
    }

    public void testSetQueueDescription()
    {
        _queueMBean.setDescription(QUEUE_DESCRIPTION);
        verify(_mockQueue).setAttribute(Queue.DESCRIPTION, null, QUEUE_DESCRIPTION);
    }

    public void testQueueType()
    {
        when(_mockQueue.getAttribute(Queue.TYPE)).thenReturn(QUEUE_TYPE);

        assertEquals(QUEUE_TYPE, _queueMBean.getQueueType());
    }

    public void testGetAlternateExchange()
    {
        Exchange mockAlternateExchange = mock(Exchange.class);
        when(mockAlternateExchange.getName()).thenReturn(QUEUE_ALTERNATE_EXCHANGE);

        when(_mockQueue.getAttribute(Queue.ALTERNATE_EXCHANGE)).thenReturn(mockAlternateExchange);

        assertEquals(QUEUE_ALTERNATE_EXCHANGE, _queueMBean.getAlternateExchange());
    }

    public void testGetAlternateExchangeWhenQueueHasNone()
    {
        when(_mockQueue.getAttribute(Queue.ALTERNATE_EXCHANGE)).thenReturn(null);

        assertNull(_queueMBean.getAlternateExchange());
    }

    public void testSetAlternateExchange() throws Exception
    {
        Exchange mockExchange1 = mock(Exchange.class);
        when(mockExchange1.getName()).thenReturn("exchange1");

        Exchange mockExchange2 = mock(Exchange.class);
        when(mockExchange2.getName()).thenReturn("exchange2");

        Exchange mockExchange3 = mock(Exchange.class);
        when(mockExchange3.getName()).thenReturn("exchange3");

        VirtualHost mockVirtualHost = mock(VirtualHost.class);
        when(mockVirtualHost.getExchanges()).thenReturn(Arrays.asList(new Exchange[] {mockExchange1, mockExchange2, mockExchange3}));
        when(_mockQueue.getParent(VirtualHost.class)).thenReturn(mockVirtualHost);

        _queueMBean.setAlternateExchange("exchange2");
        verify(_mockQueue).setAttribute(Queue.ALTERNATE_EXCHANGE, null, mockExchange2);
    }

    public void testSetAlternateExchangeWithUnknownExchangeName() throws Exception
    {
        Exchange mockExchange = mock(Exchange.class);
        when(mockExchange.getName()).thenReturn("exchange1");

        VirtualHost mockVirtualHost = mock(VirtualHost.class);
        when(mockVirtualHost.getExchanges()).thenReturn(Collections.singletonList(mockExchange));
        when(_mockQueue.getParent(VirtualHost.class)).thenReturn(mockVirtualHost);

        try
        {
            _queueMBean.setAlternateExchange("notknown");
            fail("Exception not thrown");
        }
        catch(OperationsException oe)
        {
            // PASS
        }
    }

    public void testRemoveAlternateExchange() throws Exception
    {
        _queueMBean.setAlternateExchange("");
        verify(_mockQueue).setAttribute(Queue.ALTERNATE_EXCHANGE, null, null);
    }
}
