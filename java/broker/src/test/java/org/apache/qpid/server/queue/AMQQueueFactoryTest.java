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
package org.apache.qpid.server.queue;

import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.util.InternalBrokerBaseCase;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;

public class AMQQueueFactoryTest extends InternalBrokerBaseCase
{
    QueueRegistry _queueRegistry;
    VirtualHost _virtualHost;
    int _defaultQueueCount;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        ApplicationRegistry registry = (ApplicationRegistry) ApplicationRegistry.getInstance();

        _virtualHost = registry.getVirtualHostRegistry().getVirtualHost("test");

        _queueRegistry = _virtualHost.getQueueRegistry();

        _defaultQueueCount = _queueRegistry.getQueues().size();
    }

    @Override
    public void tearDown() throws Exception
    {
        assertEquals("Queue was not registered in virtualhost", _defaultQueueCount + 1, _queueRegistry.getQueues().size());
        super.tearDown();
    }


    public void testPriorityQueueRegistration() throws Exception
    {
        FieldTable fieldTable = new FieldTable();
        fieldTable.put(new AMQShortString(AMQQueueFactory.X_QPID_PRIORITIES), 5);


        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("testPriorityQueue"), false, new AMQShortString("owner"), false,
                                           false, _virtualHost, fieldTable);

        assertEquals("Queue not a priorty queue", AMQPriorityQueue.class, queue.getClass());
    }


    public void testSimpleQueueRegistration() throws Exception
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("testQueue"), false, new AMQShortString("owner"), false,
                                           false, _virtualHost, null);
        assertEquals("Queue not a simple queue", SimpleAMQQueue.class, queue.getClass());
    }
}
