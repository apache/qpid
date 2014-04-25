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
package org.apache.qpid.server.configuration.updater;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.server.model.State;

public class CurrentThreadTaskExecutor implements TaskExecutor
{
    private final AtomicReference<Thread> _thread = new AtomicReference<>();
    private State _state;

    @Override
    public State getState()
    {
        return null;
    }

    @Override
    public void start()
    {
        if(!_thread.compareAndSet(null, Thread.currentThread()))
        {
            checkThread();
        }
        _state = State.ACTIVE;
    }

    @Override
    public void stopImmediately()
    {
        checkThread();
        _state = State.STOPPED;

    }

    private void checkThread()
    {
        if(_thread.get() != Thread.currentThread())
        {
            throw new IllegalArgumentException("Can only access the thread executor from a single thread");
        }
    }

    @Override
    public void stop()
    {
        stopImmediately();
    }

    @Override
    public void run(final VoidTask task) throws CancellationException
    {
        checkThread();
        task.execute();
    }

    @Override
    public <T, E extends Exception> T run(final TaskWithException<T, E> task) throws CancellationException, E
    {
        checkThread();
        return task.execute();
    }

    @Override
    public <E extends Exception> void run(final VoidTaskWithException<E> task) throws CancellationException, E
    {
        checkThread();
        task.execute();
    }

    @Override
    public <T> T run(final Task<T> task) throws CancellationException
    {
        checkThread();
        return task.execute();
    }

}
