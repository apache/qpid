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

import junit.framework.TestCase;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.AMQException;

public class AMQQueueFactoryTest extends TestCase
{
    final int MAX_SIZE = 50;

    QueueRegistry _queueRegistry;
    VirtualHost _virtualHost;
    protected FieldTable _arguments;

    public void setUp()
    {
        ApplicationRegistry registry = (ApplicationRegistry) ApplicationRegistry.getInstance();

        _virtualHost = registry.getVirtualHostRegistry().getVirtualHost("test");

        _queueRegistry = _virtualHost.getQueueRegistry();

        assertEquals("Queues registered on an empty virtualhost", 0, _queueRegistry.getQueues().size());


        _arguments = new FieldTable();

        //Ensure we can call createQueue with a priority int value
        _arguments.put(AMQQueueFactory.QPID_POLICY_TYPE, AMQQueueFactory.QPID_FLOW_TO_DISK);
        // each message in the QBAAT is around 9-10 bytes each so only give space for half

        _arguments.put(AMQQueueFactory.QPID_MAX_SIZE, MAX_SIZE);
    }

    public void tearDown()
    {
        assertEquals("Queue was not registered in virtualhost", 1, _queueRegistry.getQueues().size());
        ApplicationRegistry.remove(1);
    }


    protected AMQQueue createQueue() throws AMQException
    {
        return AMQQueueFactory.createAMQQueueImpl(new AMQShortString(this.getName()), false, new AMQShortString("owner"), false,
                                               _virtualHost, _arguments);
    }


    public void testQueueRegistration()
    {
        try
        {
            AMQQueue queue = createQueue();
            assertEquals("Queue not a simple queue", SimpleAMQQueue.class, queue.getClass());
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

    public void testQueueValuesAfterCreation()
    {
        try
        {
            AMQQueue queue = createQueue();

            assertEquals("MemoryMaximumSize not set correctly:", MAX_SIZE, queue.getMemoryUsageMaximum());
            assertEquals("MemoryMinimumSize not defaulted to half maximum:", MAX_SIZE / 2, queue.getMemoryUsageMinimum());

        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

}
