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

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;

public class AMQQueueFactoryPriorityTest extends AMQQueueFactoryTest
{
    private static final int PRIORITIES = 5;

    @Override
    public void setUp()
    {
        super.setUp();
        _arguments.put(new AMQShortString(AMQQueueFactory.X_QPID_PRIORITIES), PRIORITIES);
    }

    @Override
    public void testQueueRegistration()
    {
        try
        {
            AMQQueue queue = createQueue();

            assertEquals("Queue not a priorty queue", AMQPriorityQueue.class, queue.getClass());

            assertEquals("Incorrect number of priorities set", PRIORITIES, ((AMQPriorityQueue) queue).getPriorities());
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

    @Override
    public void testQueueValuesAfterCreation()
    {
        try
        {
            AMQQueue queue = createQueue();

            assertEquals("MemoryMaximumSize not set correctly:", MAX_SIZE, queue.getMemoryUsageMaximum());
            //NOTE: Priority queue will show 0 as minimum as the minimum value is actually spread between its sub QELs
            assertEquals("MemoryMinimumSize not 0 as expected for a priority queue:", 0, queue.getMemoryUsageMinimum());
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }
}
