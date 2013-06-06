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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.management.JMException;
import javax.management.OperationsException;

import junit.framework.TestCase;

import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.VirtualHost;

public class ExchangeMBeanTest extends TestCase
{
    private static final String EXCHANGE_NAME = "EXCHANGE_NAME";
    private static final String EXCHANGE_TYPE = "EXCHANGE_TYPE";
    private static final String QUEUE1_NAME = "QUEUE1_NAME";
    private static final String QUEUE2_NAME = "QUEUE2_NAME";
    private static final String BINDING1 = "BINDING1";
    private static final String BINDING2 = "BINDING2";

    private Exchange _mockExchange;
    private VirtualHostMBean _mockVirtualHostMBean;
    private ManagedObjectRegistry _mockManagedObjectRegistry;
    private ExchangeMBean _exchangeMBean;
    private Queue _mockQueue1;
    private Queue _mockQueue2;
    private Exchange _mockHeadersExchange;

    @Override
    protected void setUp() throws Exception
    {
        _mockExchange = mock(Exchange.class);
        when(_mockExchange.getName()).thenReturn(EXCHANGE_NAME);
        when(_mockExchange.getExchangeType()).thenReturn(EXCHANGE_TYPE);
        _mockVirtualHostMBean = mock(VirtualHostMBean.class);

        _mockManagedObjectRegistry = mock(ManagedObjectRegistry.class);
        when(_mockVirtualHostMBean.getRegistry()).thenReturn(_mockManagedObjectRegistry);

        _mockQueue1 = createMockQueue(QUEUE1_NAME);
        _mockQueue2 = createMockQueue(QUEUE2_NAME);

        VirtualHost mockVirtualHost = mock(VirtualHost.class);
        when(mockVirtualHost.getQueues()).thenReturn(Arrays.asList(new Queue[] {_mockQueue1, _mockQueue2}));
        when(_mockExchange.getParent(VirtualHost.class)).thenReturn(mockVirtualHost);

        _exchangeMBean = new ExchangeMBean(_mockExchange, _mockVirtualHostMBean);

        _mockHeadersExchange = mock(Exchange.class);
        when(_mockHeadersExchange.getExchangeType()).thenReturn(ExchangeMBean.HEADERS_EXCHANGE_TYPE);
        when(_mockHeadersExchange.getParent(VirtualHost.class)).thenReturn(mockVirtualHost);

    }

    public void testExchangeName()
    {
        assertEquals(EXCHANGE_NAME, _exchangeMBean.getName());
    }

    public void testExchangeType()
    {
        assertEquals(EXCHANGE_TYPE, _exchangeMBean.getExchangeType());
    }

    public void testNonHeadersExchangeCreateNewBinding() throws Exception
    {
        _exchangeMBean.createNewBinding(QUEUE1_NAME, BINDING1);
        verify(_mockExchange).createBinding(BINDING1, _mockQueue1, Collections.EMPTY_MAP, Collections.EMPTY_MAP);
    }

    public void testCreateNewBindingWhereQueueIsUnknown() throws Exception
    {
        try
        {
            _exchangeMBean.createNewBinding("unknown", BINDING1);
        }
        catch (OperationsException oe)
        {
            // PASS
            assertEquals("No such queue \"unknown\"", oe.getMessage());
        }
        verify(_mockExchange, never()).createBinding(anyString(), any(Queue.class), anyMap(), anyMap());
    }

    public void testCreateNewBindingWithArguments() throws Exception
    {
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("x-filter-jms-selector", "ID='test'");
        _exchangeMBean.createNewBinding(QUEUE1_NAME, BINDING1, arguments);
        verify(_mockExchange).createBinding(BINDING1, _mockQueue1, arguments, Collections.<String, Object>emptyMap());
    }

    public void testRemoveBinding() throws Exception
    {
        Binding mockBinding1 = createBindingOnQueue(BINDING1, _mockQueue1);
        Binding mockBinding2 = createBindingOnQueue(BINDING2, _mockQueue1);
        when(_mockExchange.getBindings()).thenReturn(Arrays.asList(new Binding[] {mockBinding1, mockBinding2}));

        _exchangeMBean.removeBinding(QUEUE1_NAME, BINDING1);
        verify(mockBinding1).delete();
    }

    public void testRemoveBindingWhereQueueIsUnknown() throws Exception
    {
        Binding mockBinding1 = createBindingOnQueue(BINDING1, _mockQueue1);
        when(_mockExchange.getBindings()).thenReturn(Arrays.asList(new Binding[] {mockBinding1}));

        try
        {
            _exchangeMBean.removeBinding("unknown", BINDING1);
            fail("Exception not thrown");
        }
        catch (OperationsException oe)
        {
            // PASS
            assertEquals("No such queue \"unknown\"", oe.getMessage());
        }
        verify(mockBinding1, never()).delete();
    }

    public void testRemoveBindingWhereBindingNameIsUnknown() throws Exception
    {
        Binding mockBinding1 = createBindingOnQueue(BINDING1, _mockQueue1);
        when(_mockExchange.getBindings()).thenReturn(Arrays.asList(new Binding[] {mockBinding1}));

        try
        {
            _exchangeMBean.removeBinding(QUEUE1_NAME, "unknown");
            fail("Exception not thrown");
        }
        catch (OperationsException oe)
        {
            // PASS
            assertEquals("No such binding \"unknown\" on queue \"" + QUEUE1_NAME + "\"", oe.getMessage());
        }
        verify(mockBinding1, never()).delete();
    }

    public void testHeadersExchangeCreateNewBinding() throws Exception
    {
        String binding = "key1=binding1,key2=binding2";
        Map<String, Object> expectedBindingMap = new HashMap<String, Object>()
        {{
            put("key1", "binding1");
            put("key2", "binding2");
        }};
        _exchangeMBean = new ExchangeMBean(_mockHeadersExchange, _mockVirtualHostMBean);

        _exchangeMBean.createNewBinding(QUEUE1_NAME, binding);
        verify(_mockHeadersExchange).createBinding(binding, _mockQueue1, expectedBindingMap, Collections.EMPTY_MAP);
    }

    public void testHeadersExchangeCreateNewBindingWithFieldWithoutValue() throws Exception
    {
        String binding = "key1=binding1,key2=";
        Map<String, Object> expectedBindingMap = new HashMap<String, Object>()
        {{
            put("key1", "binding1");
            put("key2", "");
        }};
        _exchangeMBean = new ExchangeMBean(_mockHeadersExchange, _mockVirtualHostMBean);

        _exchangeMBean.createNewBinding(QUEUE1_NAME, binding);
        verify(_mockHeadersExchange).createBinding(binding, _mockQueue1, expectedBindingMap, Collections.EMPTY_MAP);
    }

    public void testHeadersExchangeCreateNewBindingMalformed() throws Exception
    {
        String binding = "=binding1,=";
       _exchangeMBean = new ExchangeMBean(_mockHeadersExchange, _mockVirtualHostMBean);

       try
       {
           _exchangeMBean.createNewBinding(QUEUE1_NAME, binding);
           fail("Exception not thrown");
       }
       catch (JMException e)
       {
           assertEquals("Format for headers binding should be \"<attribute1>=<value1>,<attribute2>=<value2>\"", e.getMessage());
       }
    }

    private Binding createBindingOnQueue(String bindingName, Queue queue)
    {
        Binding mockBinding = mock(Binding.class);
        when(mockBinding.getParent(Queue.class)).thenReturn(queue);
        when(mockBinding.getName()).thenReturn(bindingName);
        return mockBinding;
    }

    private Queue createMockQueue(String queueName)
    {
        Queue mockQueue = mock(Queue.class);
        when(mockQueue.getName()).thenReturn(queueName);
        return mockQueue;
    }

}
