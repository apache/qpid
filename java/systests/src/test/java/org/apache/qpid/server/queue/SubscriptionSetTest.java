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

public class SubscriptionSetTest extends TestCase
{
    /**
     * A SubscriptionSet that counts the number of items scanned.
     */
    static class TestSubscriptionSet extends SubscriptionSet
    {
        private int scanned = 0;

        void resetScanned()
        {
            scanned = 0;
        }

        protected void subscriberScanned()
        {
            ++scanned;
        }

        int getScanned()
        {
            return scanned;
        }
    }

    final SubscriptionTestHelper sub1 = new SubscriptionTestHelper("1");
    final SubscriptionTestHelper sub2 = new SubscriptionTestHelper("2");
    final SubscriptionTestHelper sub3 = new SubscriptionTestHelper("3");

    final SubscriptionTestHelper suspendedSub1 = new SubscriptionTestHelper("sus1", true);
    final SubscriptionTestHelper suspendedSub2 = new SubscriptionTestHelper("sus2", true);
    final SubscriptionTestHelper suspendedSub3 = new SubscriptionTestHelper("sus3", true);

    public void testNextMessage()
    {
        SubscriptionSet ss = new SubscriptionSet();
        assertNull(ss.nextSubscriber(null));
        assertEquals(0, ss.getCurrentSubscriber());

        ss.addSubscriber(sub1);
        assertEquals(sub1, ss.nextSubscriber(null));
        assertEquals(1, ss.getCurrentSubscriber());
        assertEquals(sub1, ss.nextSubscriber(null));
        assertEquals(1, ss.getCurrentSubscriber());

        ss.addSubscriber(sub2);
        ss.addSubscriber(sub3);

        assertEquals(sub2, ss.nextSubscriber(null));
        assertEquals(2, ss.getCurrentSubscriber());

        assertEquals(sub3, ss.nextSubscriber(null));
        assertEquals(3, ss.getCurrentSubscriber());
    }

    public void testNextMessageWhenAllSuspended()
    {
        SubscriptionSet ss = createAllSuspendedSubscriptionSet();
        assertNull(ss.nextSubscriber(null));
        assertEquals(3, ss.getCurrentSubscriber());

        assertNull(ss.nextSubscriber(null));
        assertEquals(3, ss.getCurrentSubscriber());
    }

    private TestSubscriptionSet createAllSuspendedSubscriptionSet()
    {
        TestSubscriptionSet ss = new TestSubscriptionSet();
        ss.addSubscriber(suspendedSub1);
        ss.addSubscriber(suspendedSub2);
        ss.addSubscriber(suspendedSub3);
        return ss;
    }

    public void testNextMessageAfterRemove()
    {
        SubscriptionSet ss = new SubscriptionSet();
        ss.addSubscriber(suspendedSub1);
        ss.addSubscriber(suspendedSub2);
        ss.addSubscriber(sub3);
        assertEquals(sub3, ss.nextSubscriber(null));
        assertEquals(3, ss.getCurrentSubscriber());

        assertEquals(suspendedSub1, ss.removeSubscriber(suspendedSub1));

        assertEquals(sub3, ss.nextSubscriber(null)); // Current implementation handles OutOfBoundsException here.
        assertEquals(2, ss.getCurrentSubscriber());
    }

    public void testNextMessageOverScanning()
    {
        TestSubscriptionSet ss = new TestSubscriptionSet();
        SubscriptionTestHelper sub = new SubscriptionTestHelper("test");
        ss.addSubscriber(suspendedSub1);
        ss.addSubscriber(sub);
        ss.addSubscriber(suspendedSub3);
        assertEquals(sub, ss.nextSubscriber(null));
        assertEquals(2, ss.getCurrentSubscriber());
        assertEquals(2, ss.getScanned());

        ss.resetScanned();
        sub.setSuspended(true);
        assertNull(ss.nextSubscriber(null));
        assertEquals(3, ss.getCurrentSubscriber());
         // Current implementation overscans by one item here.
        assertEquals(ss.size() + 1, ss.getScanned());
    }

    public void testNextMessageOverscanWorstCase() {
        TestSubscriptionSet ss = createAllSuspendedSubscriptionSet();
        ss.nextSubscriber(null);
        // Scans the subscriptions twice.
        assertEquals(ss.size() * 2, ss.getScanned());
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(SubscriptionSetTest.class);
    }
}
