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

package org.apache.qpid.thread;

import junit.framework.TestCase;

/**
 * Tests the ThreadFactory.
 */
public class ThreadFactoryTest extends TestCase
{
    public void testThreadFactory()
    {
        Class<? extends ThreadFactory> threadFactoryClass = null;
        try
        {
            threadFactoryClass = Class.forName(System.getProperty("qpid.thread_factory",
                    "org.apache.qpid.thread.DefaultThreadFactory")).asSubclass(ThreadFactory.class);
        }
        // If the thread factory class was wrong it will flagged way before it gets here.
        catch(Exception e)
        {            
            fail("Invalid thread factory class");
        }
        
        assertEquals(threadFactoryClass, Threading.getThreadFactory().getClass());
    }

    /**
     * Tests creating a thread without a priority.   Also verifies that the factory sets the
     * uncaught exception handler so uncaught exceptions are logged to SLF4J.
     */
    public void testCreateThreadWithDefaultPriority()
    {
        Runnable r = createRunnable();
        
        Thread t = null;
        try
        {
            t = Threading.getThreadFactory().createThread(r);
        }
        catch(Exception e)
        {
            fail("Error creating thread using Qpid thread factory");
        }
        
        assertNotNull(t);
        assertEquals(Thread.NORM_PRIORITY, t.getPriority());
        assertTrue(t.getUncaughtExceptionHandler() instanceof LoggingUncaughtExceptionHandler);
    }

    /**
     * Tests creating thread with a priority.   Also verifies that the factory sets the
     * uncaught exception handler so uncaught exceptions are logged to SLF4J.
     */
    public void testCreateThreadWithSpecifiedPriority()
    {
        Runnable r = createRunnable();

        Thread t = null;
        try
        {
            t = Threading.getThreadFactory().createThread(r, 4);
        }
        catch(Exception e)
        {
            fail("Error creating thread using Qpid thread factory");
        }

        assertNotNull(t);
        assertEquals(4, t.getPriority());
        assertTrue(t.getUncaughtExceptionHandler() instanceof LoggingUncaughtExceptionHandler);
    }

    private Runnable createRunnable()
    {
        Runnable r = new Runnable(){

            public void run(){

            }
        };
        return r;
    }
}
