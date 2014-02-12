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
package org.apache.qpid.server.virtualhost;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.NullRootMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.TestLogActor;
import org.apache.qpid.test.utils.QpidTestCase;

import java.util.concurrent.CountDownLatch;

public class HouseKeepingTaskTest extends QpidTestCase
{
    private static final String HOUSE_KEEPING_TASK_TEST_VHOST = "HouseKeepingTaskTestVhost";
    private VirtualHost _host;

    public void setUp() throws Exception
    {
        super.setUp();
        _host = mock(VirtualHost.class);
        when(_host.getName()).thenReturn(HOUSE_KEEPING_TASK_TEST_VHOST);
    }

    /**
     * Tests that the abstract HouseKeepingTask properly cleans up any LogActor
     * it adds to the CurrentActor stack by verifying the CurrentActor set
     * before task execution is the CurrentActor after execution.
     */
    public void testCurrentActorStackBalance() throws Exception
    {
        //create and set a test actor
        LogActor testActor = new TestLogActor(new NullRootMessageLogger());
        CurrentActor.set(testActor);

        //verify it is returned correctly before executing a HouseKeepingTask
        assertEquals("Expected LogActor was not returned", testActor, CurrentActor.get());

        final CountDownLatch latch = new CountDownLatch(1);
        HouseKeepingTask testTask = new HouseKeepingTask(_host)
        {
            @Override
            public void execute()
            {
                latch.countDown();
            }
        };

        //run the test HouseKeepingTask using the current Thread to influence its CurrentActor stack
        testTask.run();

        assertEquals("The expected LogActor was not returned, the CurrentActor stack has become unbalanced",
                     testActor, CurrentActor.get());
        assertEquals("HouseKeepingTask execute() method was not run", 0, latch.getCount());

        //clean up the test actor
        CurrentActor.remove();
    }

    public void testThreadNameIsSetForDurationOfTask() throws Exception
    {
        //create and set a test actor
        LogActor testActor = new TestLogActor(new NullRootMessageLogger());
        CurrentActor.set(testActor);

        String originalThreadName = Thread.currentThread().getName();

        String expectedThreadNameDuringExecution = HOUSE_KEEPING_TASK_TEST_VHOST + ":" + "ThreadNameRememberingTask";

        ThreadNameRememberingTask testTask = new ThreadNameRememberingTask(_host);

        testTask.run();

        assertEquals("Thread name should have been set during execution", expectedThreadNameDuringExecution, testTask.getThreadNameDuringExecution());
        assertEquals("Thread name should have been reverted after task has run", originalThreadName, Thread.currentThread().getName());

        //clean up the test actor
        CurrentActor.remove();
    }


    private static final class ThreadNameRememberingTask extends HouseKeepingTask
    {
        private String _threadNameDuringExecution;

        private ThreadNameRememberingTask(VirtualHost vhost)
        {
            super(vhost);
        }

        @Override
        public void execute()
        {
            _threadNameDuringExecution = Thread.currentThread().getName(); // store current thread name so we can assert it later
            throw new RuntimeException("deliberate exception to check that thread name still gets reverted");
        }

        public String getThreadNameDuringExecution()
        {
            return _threadNameDuringExecution;
        }
    }
}
