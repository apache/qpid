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

import junit.framework.JUnit4TestAdapter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class SubscriptionSetTest
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

    final TestSubscription sub1 = new TestSubscription("1");
    final TestSubscription sub2 = new TestSubscription("2");
    final TestSubscription sub3 = new TestSubscription("3");

    final TestSubscription suspendedSub1 = new TestSubscription("sus1", true);
    final TestSubscription suspendedSub2 = new TestSubscription("sus2", true);
    final TestSubscription suspendedSub3 = new TestSubscription("sus3", true);

    @Test
    public void nextMessage()
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

    @Test
    public void nextMessageWhenAllSuspended()
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

    @Test
    public void nextMessageAfterRemove()
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

    @Test
    public void nextMessageOverScanning()
    {
        TestSubscriptionSet ss = new TestSubscriptionSet();
        TestSubscription sub = new TestSubscription("test");
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

    @Test
    public void nextMessageOverscanWorstCase() {
        TestSubscriptionSet ss = createAllSuspendedSubscriptionSet();
        ss.nextSubscriber(null);
        // Scans the subscriptions twice.
        assertEquals(ss.size() * 2, ss.getScanned());
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(SubscriptionSetTest.class);
    }
}
