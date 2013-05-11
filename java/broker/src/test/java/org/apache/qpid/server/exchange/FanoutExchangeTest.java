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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import junit.framework.TestCase;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInternalException;
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class FanoutExchangeTest extends TestCase
{
    private FanoutExchange _exchange;
    private VirtualHost _virtualHost;

    public void setUp() throws AMQException
    {
        CurrentActor.setDefault(mock(LogActor.class));

        _exchange = new FanoutExchange();
        _virtualHost = mock(VirtualHost.class);
        SecurityManager securityManager = mock(SecurityManager.class);
        when(_virtualHost.getSecurityManager()).thenReturn(securityManager);
        when(securityManager.authoriseBind(any(Exchange.class),any(AMQQueue.class),any(AMQShortString.class))).thenReturn(true);
        _exchange.initialise(UUID.randomUUID(), _virtualHost, AMQShortString.valueOf("test"), false, 0, false);
    }

    public void testIsBoundAMQShortStringFieldTableAMQQueueWhenQueueIsNull()
    {
        assertFalse("calling isBound(AMQShortString,FieldTable,AMQQueue) with null queue should return false",
                _exchange.isBound((AMQShortString) null, (FieldTable) null, (AMQQueue) null));
    }

    public void testIsBoundAMQShortStringAMQQueueWhenQueueIsNull()
    {
        assertFalse("calling isBound(AMQShortString,AMQQueue) with null queue should return false",
                _exchange.isBound((AMQShortString) null, (AMQQueue) null));
    }

    public void testIsBoundAMQQueueWhenQueueIsNull()
    {
        assertFalse("calling isBound(AMQQueue) with null queue should return false", _exchange.isBound((AMQQueue) null));
    }

    public void testIsBoundAMQShortStringFieldTableAMQQueue() throws AMQSecurityException, AMQInternalException
    {
        AMQQueue queue = bindQueue();
        assertTrue("Should return true for a bound queue",
                _exchange.isBound((AMQShortString) null, (FieldTable) null, queue));
    }

    public void testIsBoundAMQShortStringAMQQueue() throws AMQSecurityException, AMQInternalException
    {
        AMQQueue queue = bindQueue();
        assertTrue("Should return true for a bound queue",
                _exchange.isBound((AMQShortString) null, queue));
    }

    public void testIsBoundAMQQueue() throws AMQSecurityException, AMQInternalException
    {
        AMQQueue queue = bindQueue();
        assertTrue("Should return true for a bound queue",
                _exchange.isBound(queue));
    }

    private AMQQueue bindQueue() throws AMQSecurityException, AMQInternalException
    {
        AMQQueue queue = mock(AMQQueue.class);
        when(queue.getVirtualHost()).thenReturn(_virtualHost);
        _exchange.addBinding("does not matter", queue, null);
        return queue;
    }
}
