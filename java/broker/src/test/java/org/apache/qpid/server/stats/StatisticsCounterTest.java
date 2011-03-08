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
package org.apache.qpid.server.stats;

import junit.framework.TestCase;

/**
 * Unit tests for the {@link StatisticsCounter} class.
 */
public class StatisticsCounterTest extends TestCase
{
    /**
     * Check that statistics counters are created correctly.
     */
    public void testCreate()
    {
        long before = System.currentTimeMillis();
        StatisticsCounter counter = new StatisticsCounter("name", 1234L);
        long after = System.currentTimeMillis();
        
        assertTrue(before <= counter.getStart());
        assertTrue(after >= counter.getStart());
        assertTrue(counter.getName().startsWith("name-"));
        assertEquals(1234L, counter.getPeriod());
    }
 
    /**
     * Check that totals add up correctly.
     */
    public void testTotal()
    {
        StatisticsCounter counter = new StatisticsCounter("test", 1000L);
        long start = counter.getStart();
        for (int i = 0; i < 100; i++)
        {
            counter.registerEvent(i, start + i);
        }
        assertEquals(99 * 50, counter.getTotal()); // cf. Gauss
    }
 
    /**
     * Test totals add up correctly even when messages are delivered
     * out-of-order.
     */
    public void testTotalOutOfOrder()
    {
        StatisticsCounter counter = new StatisticsCounter("test", 1000L);
        long start = counter.getStart();
        assertEquals(0, counter.getTotal());
        counter.registerEvent(10, start + 2500);
        assertEquals(10, counter.getTotal());
        counter.registerEvent(20, start + 1500);
        assertEquals(30, counter.getTotal());
        counter.registerEvent(10, start + 500);
        assertEquals(40, counter.getTotal());
    }
 
    /**
     * Test that the peak rate is reported correctly.
     */
    public void testPeak() throws Exception
    {
        StatisticsCounter counter = new StatisticsCounter("test", 1000L);
        long start = counter.getStart();
        assertEquals(0.0, counter.getPeak());
        Thread.sleep(500);
        counter.registerEvent(1000, start + 500);
        Thread.sleep(1000);
        assertEquals(1000.0, counter.getPeak());
        counter.registerEvent(2000, start + 1500);
        Thread.sleep(1000);
        assertEquals(2000.0, counter.getPeak());
        counter.registerEvent(1000, start + 2500);
        Thread.sleep(1000);
        assertEquals(2000.0, counter.getPeak());
    }
 
    /**
     * Test that peak rate is reported correctly for out-of-order messages,
     * and the total is also unaffected.
     */
    public void testPeakOutOfOrder() throws Exception
    {
        StatisticsCounter counter = new StatisticsCounter("test", 1000L);
        long start = counter.getStart();
        assertEquals(0.0, counter.getPeak());
        counter.registerEvent(1000, start + 2500);
        Thread.sleep(1500);
        assertEquals(0.0, counter.getPeak());
        counter.registerEvent(2000, start + 1500);
        Thread.sleep(1000L);
        assertEquals(0.0, counter.getPeak());
        counter.registerEvent(1000, start + 500);
        Thread.sleep(1500);
        assertEquals(4000.0, counter.getPeak());
        Thread.sleep(2000);
        assertEquals(4000.0, counter.getPeak());
        counter.registerEvent(1000, start + 500);
        assertEquals(4000.0, counter.getPeak());
        Thread.sleep(2000);
        counter.registerEvent(1000);
        assertEquals(4000.0, counter.getPeak());
        assertEquals(6000, counter.getTotal());
    }
 
    /**
     * Test the current rate is generated correctly.
     */
    public void testRate() throws Exception
    {
        StatisticsCounter counter = new StatisticsCounter("test", 1000L);
        assertEquals(0.0, counter.getRate());
        Thread.sleep(500);
        counter.registerEvent(1000);
        Thread.sleep(1000);
        assertEquals(1000.0, counter.getRate());
        counter.registerEvent(2000);
        Thread.sleep(1000);
        assertEquals(2000.0, counter.getRate());
        counter.registerEvent(1000);
        Thread.sleep(1000);
        assertEquals(1000.0, counter.getRate());
        Thread.sleep(1000);
        assertEquals(0.0, counter.getRate());
    }
}
