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
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.pool.ReferenceCountingExecutorService;
import org.apache.qpid.server.virtualhost.VirtualHost;

import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.AMQException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleAMQQueueThreadPoolTest extends TestCase
{

    public void test() throws AMQException
    {
        VirtualHost test = ApplicationRegistry.getInstance(1).getVirtualHostRegistry().getVirtualHost("test");

        try
        {
            SimpleAMQQueue queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(new AMQShortString("test"), false,
                                                                                       new AMQShortString("owner"),
                                                                                       false, test, null);

            assertTrue("Creation did not start Pool.", !ReferenceCountingExecutorService.getInstance().getPool().isShutdown());

            queue.stop();

            assertEquals("References still exist", 0, ReferenceCountingExecutorService.getInstance().getReferenceCount());

            assertTrue("Stop did not clean up.", ReferenceCountingExecutorService.getInstance().getPool().isShutdown());
        }
        finally
        {
            ApplicationRegistry.remove(1);
        }       
    }
}
