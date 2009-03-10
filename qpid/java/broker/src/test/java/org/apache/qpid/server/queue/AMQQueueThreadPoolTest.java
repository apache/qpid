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
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.pool.ReferenceCountingExecutorService;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class AMQQueueThreadPoolTest extends TestCase
{

    public void testSimpleAMQQueue() throws AMQException
    {
        int initialCount = ReferenceCountingExecutorService.getInstance().getReferenceCount();
        VirtualHost test = ApplicationRegistry.getInstance(1).getVirtualHostRegistry().getVirtualHost("test");

        try
        {
            SimpleAMQQueue queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(new AMQShortString("test"), false,
                                                                                       new AMQShortString("owner"),
                                                                                       false, test, null);

            assertFalse("Creation did not start Pool.", ReferenceCountingExecutorService.getInstance().getPool().isShutdown());

            //This is +2 because:
            // 1 - asyncDelivery Thread
            // 2 - queue InhalerThread
            // 3 - queue PurgerThread
            assertEquals("References not increased", initialCount + 3, ReferenceCountingExecutorService.getInstance().getReferenceCount());

            queue.stop();

            assertEquals("References not decreased", initialCount, ReferenceCountingExecutorService.getInstance().getReferenceCount());
        }
        finally
        {
            ApplicationRegistry.remove(1);
        }
    }

    public void testPriorityAMQQueue() throws AMQException
    {
        int initialCount = ReferenceCountingExecutorService.getInstance().getReferenceCount();
        VirtualHost test = ApplicationRegistry.getInstance(1).getVirtualHostRegistry().getVirtualHost("test");

        try
        {

            FieldTable arguements = new FieldTable();
            int priorities = 10;
            arguements.put(AMQQueueFactory.X_QPID_PRIORITIES, priorities);

            SimpleAMQQueue queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(new AMQShortString("test"), false,
                                                                                       new AMQShortString("owner"),
                                                                                       false, test, arguements);

            assertFalse("Creation did not start Pool.", ReferenceCountingExecutorService.getInstance().getPool().isShutdown());

            //This is +2 because:
            // 1 - asyncDelivery Thread
            // 2 + 3  - queue InhalerThread, PurgerThread for the Priority Queue
            // priorities * ( Inhaler , Purger) for each priority level
            assertEquals("References not increased", (initialCount + 3) + priorities * 2,
                         ReferenceCountingExecutorService.getInstance().getReferenceCount());

            queue.stop();

            assertEquals("References not decreased", initialCount, ReferenceCountingExecutorService.getInstance().getReferenceCount());
        }
        finally
        {
            ApplicationRegistry.remove(1);
        }
    }

}
