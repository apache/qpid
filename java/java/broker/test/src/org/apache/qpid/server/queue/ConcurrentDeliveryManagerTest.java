/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.queue;

import org.apache.qpid.server.queue.ConcurrentDeliveryManager;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.DefaultQueueRegistry;
import org.apache.qpid.server.queue.DeliveryManagerTest;
import org.apache.qpid.AMQException;
import junit.framework.JUnit4TestAdapter;

public class ConcurrentDeliveryManagerTest extends DeliveryManagerTest
{
    public ConcurrentDeliveryManagerTest() throws Exception
    {
        try
        {
            System.setProperty("concurrentdeliverymanager","true");
            _mgr = new ConcurrentDeliveryManager(_subscriptions, new AMQQueue("myQ", false, "guest", false,
                                                                              new DefaultQueueRegistry()));
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            throw new AMQException("Could not initialise delivery manager", t);
        }
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(ConcurrentDeliveryManagerTest.class);
    }
}
