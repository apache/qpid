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
package org.apache.qpid.server.exchange;

import static org.mockito.Matchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import junit.framework.TestCase;

import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class FanoutExchangeTest extends TestCase
{
    private FanoutExchange _exchange;
    private VirtualHost _virtualHost;

    public void setUp()
    {
        CurrentActor.setDefault(mock(LogActor.class));

        _exchange = new FanoutExchange();
        _virtualHost = mock(VirtualHost.class);
        SecurityManager securityManager = mock(SecurityManager.class);
        when(_virtualHost.getSecurityManager()).thenReturn(securityManager);

        _exchange.initialise(UUID.randomUUID(), _virtualHost, "test", false, false);
    }

    public void testIsBoundStringMapAMQQueueWhenQueueIsNull()
    {
        assertFalse("calling isBound(AMQShortString,FieldTable,AMQQueue) with null queue should return false",
                _exchange.isBound((String) null, (Map) null, (AMQQueue) null));
    }

    public void testIsBoundStringAMQQueueWhenQueueIsNull()
    {
        assertFalse("calling isBound(AMQShortString,AMQQueue) with null queue should return false",
                _exchange.isBound((String) null, (AMQQueue) null));
    }

    public void testIsBoundAMQQueueWhenQueueIsNull()
    {
        assertFalse("calling isBound(AMQQueue) with null queue should return false", _exchange.isBound((AMQQueue) null));
    }

    public void testIsBoundStringMapAMQQueue()
    {
        AMQQueue queue = bindQueue();
        assertTrue("Should return true for a bound queue",
                _exchange.isBound("matters", null, queue));
    }

    public void testIsBoundStringAMQQueue()
    {
        AMQQueue queue = bindQueue();
        assertTrue("Should return true for a bound queue",
                _exchange.isBound("matters", queue));
    }

    public void testIsBoundAMQQueue()
    {
        AMQQueue queue = bindQueue();
        assertTrue("Should return true for a bound queue",
                _exchange.isBound(queue));
    }

    private AMQQueue bindQueue()
    {
        AMQQueue queue = mockQueue();
        _exchange.addBinding("matters", queue, null);
        return queue;
    }

    private AMQQueue mockQueue()
    {
        AMQQueue queue = mock(AMQQueue.class);
        when(queue.getVirtualHost()).thenReturn(_virtualHost);
        return queue;
    }

    public void testRoutingWithSelectors() throws Exception
    {
        AMQQueue queue1 = mockQueue();
        AMQQueue queue2 = mockQueue();

        _exchange.addBinding("key",queue1, null);
        _exchange.addBinding("key",queue2, null);


        List<? extends BaseQueue> result = _exchange.route(mockMessage(true),InstanceProperties.EMPTY);

        assertEquals("Expected message to be routed to both queues", 2, result.size());
        assertTrue("Expected queue1 to be routed to", result.contains(queue1));
        assertTrue("Expected queue2 to be routed to", result.contains(queue2));

        _exchange.addBinding("key2",queue2, Collections.singletonMap(AMQPFilterTypes.JMS_SELECTOR.toString(),(Object)"select = True"));


        result = _exchange.route(mockMessage(true),InstanceProperties.EMPTY);

        assertEquals("Expected message to be routed to both queues", 2, result.size());
        assertTrue("Expected queue1 to be routed to", result.contains(queue1));
        assertTrue("Expected queue2 to be routed to", result.contains(queue2));

        _exchange.removeBinding("key",queue2,null);

        result = _exchange.route(mockMessage(true),InstanceProperties.EMPTY);

        assertEquals("Expected message to be routed to both queues", 2, result.size());
        assertTrue("Expected queue1 to be routed to", result.contains(queue1));
        assertTrue("Expected queue2 to be routed to", result.contains(queue2));


        result = _exchange.route(mockMessage(false),InstanceProperties.EMPTY);

        assertEquals("Expected message to be routed to queue1 only", 1, result.size());
        assertTrue("Expected queue1 to be routed to", result.contains(queue1));
        assertFalse("Expected queue2 not to be routed to", result.contains(queue2));

        _exchange.addBinding("key",queue2, Collections.singletonMap(AMQPFilterTypes.JMS_SELECTOR.toString(),(Object)"select = False"));


        result = _exchange.route(mockMessage(false),InstanceProperties.EMPTY);
        assertEquals("Expected message to be routed to both queues", 2, result.size());
        assertTrue("Expected queue1 to be routed to", result.contains(queue1));
        assertTrue("Expected queue2 to be routed to", result.contains(queue2));


    }

    private ServerMessage mockMessage(boolean val)
    {
        final AMQMessageHeader header = mock(AMQMessageHeader.class);
        when(header.containsHeader("select")).thenReturn(true);
        when(header.getHeader("select")).thenReturn(val);
        when(header.getHeaderNames()).thenReturn(Collections.singleton("select"));
        when(header.containsHeaders(anySet())).then(new Answer<Object>()
        {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable
            {
                final Set names = (Set) invocation.getArguments()[0];
                return names.size() == 1 && names.contains("select");

            }
        });
        final ServerMessage serverMessage = mock(ServerMessage.class);
        when(serverMessage.getMessageHeader()).thenReturn(header);
        return serverMessage;
    }
}
