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
package org.apache.qpid.server.store.berkeleydb.replication;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sleepycat.je.rep.ReplicatedEnvironment.State;
import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;

class TestStateChangeListener implements StateChangeListener
{
    private final Set<State> _expectedStates;
    private final CountDownLatch _latch;
    private final AtomicReference<State> _currentActualState = new AtomicReference<State>();

    public TestStateChangeListener(State expectedState)
    {
        this(Collections.singleton(expectedState));
    }

    public TestStateChangeListener(Set<State> expectedStates)
    {
        _expectedStates = new HashSet<State>(expectedStates);
        _latch = new CountDownLatch(1);
    }

    @Override
    public void stateChange(StateChangeEvent stateChangeEvent) throws RuntimeException
    {
        _currentActualState.set(stateChangeEvent.getState());
        if (_expectedStates.contains(stateChangeEvent.getState()))
        {
            _latch.countDown();
        }
    }

    public boolean awaitForStateChange(long timeout, TimeUnit timeUnit) throws InterruptedException
    {
        return _latch.await(timeout, timeUnit);
    }

    public State getCurrentActualState()
    {
        return _currentActualState.get();
    }
}