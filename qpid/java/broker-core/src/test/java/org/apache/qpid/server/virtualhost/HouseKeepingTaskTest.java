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

import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

public class HouseKeepingTaskTest extends QpidTestCase
{

    public void testThreadNameIsSetForDurationOfTask() throws Exception
    {
        String originalThreadName = Thread.currentThread().getName();

        String vhostName = "HouseKeepingTaskTestVhost";

        String expectedThreadNameDuringExecution = vhostName + ":" + "ThreadNameRememberingTask";

        VirtualHost virtualHost = mock(VirtualHost.class);
        when(virtualHost.getName()).thenReturn(vhostName);
        ThreadNameRememberingTask testTask = new ThreadNameRememberingTask(virtualHost);

        testTask.run();

        assertEquals("Thread name should have been set during execution", expectedThreadNameDuringExecution, testTask.getThreadNameDuringExecution());
        assertEquals("Thread name should have been reverted after task has run", originalThreadName, Thread.currentThread().getName());

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
