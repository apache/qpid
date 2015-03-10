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
 *
 */
package org.apache.qpid.server.jmx.mbeans;

import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;

import java.util.Collections;
import java.util.Map;

import javax.management.OperationsException;

import junit.framework.TestCase;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;

import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.queue.QueueArgumentsConverter;
import org.apache.qpid.server.virtualhost.QueueExistsException;

public class VirtualHostManagerMBeanTest extends TestCase
{
    private static final String TEST_QUEUE_NAME = "QUEUE_NAME";
    private static final String TEST_EXCHANGE_NAME = "EXCHANGE_NAME";
    private static final String TEST_OWNER = "OWNER";
    private static final String TEST_DESCRIPTION = "DESCRIPTION";
    private static final String TEST_EXCHANGE_TYPE = "EXCHANGE_TYPE";

    private static final Map<String, Object> EMPTY_ARGUMENT_MAP = Collections.emptyMap();
    public static final String QUEUE_1_NAME = "queue1";
    public static final String EXCHANGE_1_NAME = "exchange1";

    private VirtualHost _mockVirtualHost;
    private ManagedObjectRegistry _mockManagedObjectRegistry;
    private VirtualHostManagerMBean _virtualHostManagerMBean;

    @Override
    protected void setUp() throws Exception
    {
        _mockVirtualHost = mock(VirtualHost.class);
        when(_mockVirtualHost.getExchangeTypeNames()).thenReturn(Collections.singletonList(TEST_EXCHANGE_TYPE));

        _mockManagedObjectRegistry = mock(ManagedObjectRegistry.class);

        _virtualHostManagerMBean = new VirtualHostManagerMBean(new VirtualHostMBean(_mockVirtualHost, _mockManagedObjectRegistry));
    }

    public void testCreateQueueWithNoOwner() throws Exception
    {
        _virtualHostManagerMBean.createNewQueue(TEST_QUEUE_NAME, null, true);
        ArgumentCaptor<Map> argsCaptor = ArgumentCaptor.forClass(Map.class);

        verify(_mockVirtualHost).createQueue(argsCaptor.capture());

        Map actualAttributes = argsCaptor.getValue();
        assertEquals(TEST_QUEUE_NAME, actualAttributes.get(Queue.NAME));
        assertEquals(Boolean.TRUE,actualAttributes.get(Queue.DURABLE));
        assertEquals(null,actualAttributes.get(Queue.OWNER));

    }

    /**
     * Some users have been abusing the owner parameter as a description.  Decision has been taken to map this parameter
     * through to the description field (if the description field is passed, the owner is discarded).
     */
    public void testCreateQueueWithOwnerMappedThroughToDescription() throws Exception
    {
        _virtualHostManagerMBean.createNewQueue(TEST_QUEUE_NAME, TEST_OWNER, true);
        ArgumentCaptor<Map> argsCaptor = ArgumentCaptor.forClass(Map.class);

        verify(_mockVirtualHost).createQueue(argsCaptor.capture());

        Map actualAttributes = argsCaptor.getValue();
        assertEquals(TEST_QUEUE_NAME,actualAttributes.get(Queue.NAME));
        assertEquals(Boolean.TRUE,actualAttributes.get(Queue.DURABLE));
        assertEquals(null,actualAttributes.get(Queue.OWNER));
        assertEquals(TEST_OWNER, actualAttributes.get(Queue.DESCRIPTION));
    }

    public void testCreateQueueWithOwnerAndDescriptionDiscardsOwner() throws Exception
    {
        Map<String, Object> arguments = Collections.singletonMap(QueueArgumentsConverter.X_QPID_DESCRIPTION, (Object)TEST_DESCRIPTION);
        _virtualHostManagerMBean.createNewQueue(TEST_QUEUE_NAME, TEST_OWNER, true, arguments);

        ArgumentCaptor<Map> argsCaptor = ArgumentCaptor.forClass(Map.class);

        verify(_mockVirtualHost).createQueue(argsCaptor.capture());

        Map actualAttributes = argsCaptor.getValue();
        assertEquals(TEST_QUEUE_NAME,actualAttributes.get(Queue.NAME));
        assertEquals(Boolean.TRUE,actualAttributes.get(Queue.DURABLE));
        assertEquals(null,actualAttributes.get(Queue.OWNER));
        assertEquals(TEST_DESCRIPTION, actualAttributes.get(Queue.DESCRIPTION));
    }

    public void testCreateQueueThatAlreadyExists() throws Exception
    {
        doThrow(new QueueExistsException("mocked exception", null)).when(_mockVirtualHost).createQueue(any(Map.class));

        try
        {
            _virtualHostManagerMBean.createNewQueue(TEST_QUEUE_NAME, TEST_OWNER, true);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException iae)
        {
            // PASS
        }

    }

    public void testDeleteQueue() throws Exception
    {
        Queue mockQueue = mock(Queue.class);
        when(mockQueue.getName()).thenReturn(QUEUE_1_NAME);
        when(_mockVirtualHost.getQueues()).thenReturn(Collections.singletonList(mockQueue));
        when(_mockVirtualHost.getChildByName(eq(Queue.class), eq(QUEUE_1_NAME))).thenReturn(mockQueue);

        _virtualHostManagerMBean.deleteQueue(QUEUE_1_NAME);
        verify(mockQueue).delete();
    }

    public void testDeleteQueueWhenQueueDoesNotExist() throws Exception
    {
        Queue mockQueue = mock(Queue.class);
        when(mockQueue.getName()).thenReturn(QUEUE_1_NAME);
        when(_mockVirtualHost.getQueues()).thenReturn(Collections.singletonList(mockQueue));
        when(_mockVirtualHost.getChildByName(eq(Queue.class), eq(QUEUE_1_NAME))).thenReturn(mockQueue);

        try
        {
            _virtualHostManagerMBean.deleteQueue("unknownqueue");
            fail("Exception not thrown");
        }
        catch(OperationsException oe)
        {
            // PASS
            assertEquals("No such queue \"unknownqueue\"", oe.getMessage());
        }
        verify(mockQueue, never()).deleteAndReturnCount();
    }

    public void testCreateNewDurableExchange() throws Exception
    {
        _virtualHostManagerMBean.createNewExchange(TEST_EXCHANGE_NAME, TEST_EXCHANGE_TYPE, true);

        verify(_mockVirtualHost).createExchange(matchesMap(TEST_EXCHANGE_NAME, true, LifetimePolicy.PERMANENT, TEST_EXCHANGE_TYPE));
    }

    public void testCreateNewExchangeWithUnknownExchangeType() throws Exception
    {
        String exchangeType = "notknown";
        try
        {
            _virtualHostManagerMBean.createNewExchange(TEST_EXCHANGE_NAME, exchangeType, true);
            fail("Exception not thrown");
        }
        catch (OperationsException oe)
        {
            // PASS
        }
        verify(_mockVirtualHost, never()).createExchange(matchesMap(TEST_EXCHANGE_NAME,
                                                                    true,
                                                                    LifetimePolicy.PERMANENT,
                                                                    exchangeType));
    }

    public void testUnregisterExchange() throws Exception
    {
        Exchange mockExchange = mock(Exchange.class);
        when(mockExchange.getName()).thenReturn(EXCHANGE_1_NAME);
        when(_mockVirtualHost.getExchanges()).thenReturn(Collections.singletonList(mockExchange));
        when(_mockVirtualHost.getChildByName(eq(Exchange.class), eq(EXCHANGE_1_NAME))).thenReturn(mockExchange);


        _virtualHostManagerMBean.unregisterExchange(EXCHANGE_1_NAME);
        verify(mockExchange).delete();
    }

    public void testUnregisterExchangeWhenExchangeDoesNotExist() throws Exception
    {
        Exchange mockExchange = mock(Exchange.class);
        when(mockExchange.getName()).thenReturn(EXCHANGE_1_NAME);
        when(_mockVirtualHost.getExchanges()).thenReturn(Collections.singletonList(mockExchange));
        when(_mockVirtualHost.getChildByName(eq(Exchange.class), eq(EXCHANGE_1_NAME))).thenReturn(mockExchange);

        try
        {
            _virtualHostManagerMBean.unregisterExchange("unknownexchange");
            fail("Exception not thrown");
        }
        catch(OperationsException oe)
        {
            // PASS
            assertEquals("No such exchange \"unknownexchange\"", oe.getMessage());
        }

        verify(mockExchange, never()).deleteWithChecks();
    }

    private static Map<String,Object> matchesMap(final String name,
                                                 final boolean durable,
                                                 final LifetimePolicy lifetimePolicy,
                                                 final String exchangeType)
    {
        return argThat(new MapMatcher(name, durable, lifetimePolicy, exchangeType));
    }

    private static class MapMatcher extends ArgumentMatcher<Map<String,Object>>
    {

        private final String _name;
        private final boolean _durable;
        private final LifetimePolicy _lifetimePolicy;
        private final String _exchangeType;

        public MapMatcher(final String name,
                          final boolean durable,
                          final LifetimePolicy lifetimePolicy,
                          final String exchangeType)
        {
            _name = name;
            _durable = durable;
            _lifetimePolicy = lifetimePolicy;
            _exchangeType = exchangeType;

        }

        @Override
        public boolean matches(final Object o)
        {
            Map<String,Object> map = (Map<String,Object>)o;

            return _name.equals(map.get(Exchange.NAME))
                   && _durable == (Boolean) map.get(Exchange.DURABLE)
                   && _lifetimePolicy == map.get(Exchange.LIFETIME_POLICY)
                   && _exchangeType.equals(map.get(Exchange.TYPE));
        }
    }

}
