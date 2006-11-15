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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import junit.framework.JUnit4TestAdapter;

public class SubscriptionManagerTest
{
    private final SubscriptionSet mgr = new SubscriptionSet();

    @Test
    public void basicSubscriptionManagement()
    {
        assertTrue(mgr.isEmpty());
        assertFalse(mgr.hasActiveSubscribers());
        TestSubscription s1 = new TestSubscription("S1");
        mgr.addSubscriber(s1);
        assertFalse(mgr.isEmpty());
        assertTrue(mgr.hasActiveSubscribers());

        TestSubscription s2 = new TestSubscription("S2");
        mgr.addSubscriber(s2);

        s2.setSuspended(true);
        assertFalse(mgr.isEmpty());
        assertTrue(mgr.hasActiveSubscribers());
        assertTrue(s2.isSuspended());
        assertFalse(s1.isSuspended());

        s1.setSuspended(true);
        assertFalse(mgr.hasActiveSubscribers());

        mgr.removeSubscriber(new TestSubscription("S1"));
        assertFalse(mgr.isEmpty());
        mgr.removeSubscriber(new TestSubscription("S2"));
        assertTrue(mgr.isEmpty());
    }

    @Test
    public void roundRobin()
    {
        TestSubscription a = new TestSubscription("A");
        TestSubscription b = new TestSubscription("B");
        TestSubscription c = new TestSubscription("C");
        TestSubscription d = new TestSubscription("D");
        mgr.addSubscriber(a);
        mgr.addSubscriber(b);
        mgr.addSubscriber(c);
        mgr.addSubscriber(d);

        for (int i = 0; i < 3; i++)
        {
            assertEquals(a, mgr.nextSubscriber(null));
            assertEquals(b, mgr.nextSubscriber(null));
            assertEquals(c, mgr.nextSubscriber(null));
            assertEquals(d, mgr.nextSubscriber(null));
        }

        c.setSuspended(true);

        for (int i = 0; i < 3; i++)
        {
            assertEquals(a, mgr.nextSubscriber(null));
            assertEquals(b, mgr.nextSubscriber(null));
            assertEquals(d, mgr.nextSubscriber(null));
        }

        mgr.removeSubscriber(a);
        d.setSuspended(true);
        c.setSuspended(false);
        Subscription e = new TestSubscription("D");
        mgr.addSubscriber(e);

        for (int i = 0; i < 3; i++)
        {
            assertEquals(b, mgr.nextSubscriber(null));
            assertEquals(c, mgr.nextSubscriber(null));
            assertEquals(e, mgr.nextSubscriber(null));
        }
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(SubscriptionManagerTest.class);
    }
}
