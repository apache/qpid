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

import org.apache.qpid.server.queue.SynchronizedDeliveryManager;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.DefaultQueueRegistry;
import org.apache.qpid.server.queue.DeliveryManagerTest;
import org.apache.qpid.AMQException;

import junit.framework.TestSuite;

public class SynchronizedDeliveryManagerTest extends DeliveryManagerTest
{
    public SynchronizedDeliveryManagerTest() throws Exception
    {
        try
        {
            System.setProperty("concurrentdeliverymanager","false");
            _mgr = new SynchronizedDeliveryManager(_subscriptions, new AMQQueue("myQ", false, "guest", false,
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
        TestSuite suite = new TestSuite();
        suite.addTestSuite(SynchronizedDeliveryManagerTest.class);
        return suite;
    }
}
