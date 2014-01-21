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
package org.apache.qpid.server.store.berkeleydb;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;
import com.sleepycat.je.rep.ReplicatedEnvironment.State;

class TestStateChangeListener implements StateChangeListener
{
    private final State _expectedState;
    private final CountDownLatch _latch;

    public TestStateChangeListener(State expectedState)
    {
        _expectedState = expectedState;
        _latch = new CountDownLatch(1);
    }

    @Override
    public void stateChange(StateChangeEvent stateChangeEvent) throws RuntimeException
    {
        if (stateChangeEvent.getState() == _expectedState)
        {
            _latch.countDown();
        }
    }

    public boolean awaitForStateChange(long timeout, TimeUnit timeUnit) throws InterruptedException
    {
        return _latch.await(timeout, timeUnit);
    }
}