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
package org.apache.qpid.pool;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;


public class ReferenceCountingExecutorServiceTest extends TestCase
{


    private ReferenceCountingExecutorService _executorService = ReferenceCountingExecutorService.getInstance();  // Class under test
    private ThreadFactory _beforeExecutorThreadFactory;


    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _beforeExecutorThreadFactory = _executorService.getThreadFactory();
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
        _executorService.setThreadFactory(_beforeExecutorThreadFactory);
    }



    /**
     * Tests that the ReferenceCountingExecutorService correctly manages the reference count.
     */
    public void testReferenceCounting() throws Exception
    {
        final int countBefore = _executorService.getReferenceCount();

        try
        {
            _executorService.acquireExecutorService();
            _executorService.acquireExecutorService();

            assertEquals("Reference count should now be +2", countBefore + 2, _executorService.getReferenceCount());
        }
        finally
        {
            _executorService.releaseExecutorService();
            _executorService.releaseExecutorService();
        }
        assertEquals("Reference count should have returned to the initial value", countBefore, _executorService.getReferenceCount());
    }

    /**
     * Tests that the executor creates and executes a task using the default thread pool.
     */
    public void testExecuteCommandWithDefaultExecutorThreadFactory() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);
        final Set<ThreadGroup> threadGroups = new HashSet<ThreadGroup>();

        _executorService.acquireExecutorService();

        try
        {
            _executorService.getPool().execute(createRunnable(latch, threadGroups));

            latch.await(3, TimeUnit.SECONDS);

            assertTrue("Expect that executor created a thread using default thread factory",
                    threadGroups.contains(Thread.currentThread().getThreadGroup()));
        }
        finally
        {
            _executorService.releaseExecutorService();
        }
    }

    /**
     * Tests that the executor creates and executes a task using an overridden thread pool.
     */
    public void testExecuteCommandWithOverriddenExecutorThreadFactory() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);
        final ThreadGroup expectedThreadGroup = new ThreadGroup("junit");
        _executorService.setThreadFactory(new ThreadGroupChangingThreadFactory(expectedThreadGroup));
        _executorService.acquireExecutorService();

        final Set<ThreadGroup> threadGroups = new HashSet<ThreadGroup>();

        try
        {
            _executorService.getPool().execute(createRunnable(latch, threadGroups));

            latch.await(3, TimeUnit.SECONDS);

            assertTrue("Expect that executor created a thread using overridden thread factory",
                    threadGroups.contains(expectedThreadGroup));
        }
        finally
        {
            _executorService.releaseExecutorService();
        }
    }

    private Runnable createRunnable(final CountDownLatch latch, final Set<ThreadGroup> threadGroups)
    {
        return new Runnable()
        {

            public void run()
            {
                threadGroups.add(Thread.currentThread().getThreadGroup());
                latch.countDown();
            }

        };
    }

   private final class ThreadGroupChangingThreadFactory implements ThreadFactory
   {
       private final ThreadGroup _newGroup;

       private ThreadGroupChangingThreadFactory(final ThreadGroup newGroup)
       {
           this._newGroup = newGroup;
       }

       public Thread newThread(Runnable r)
       {
           return new Thread(_newGroup, r);
       }
   }

}
