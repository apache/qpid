/*
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
package org.apache.qpid.disttest.client.utils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Executes a {@link Callable} but limits the execution time. If the execution
 * time is exceeded the callable will be cancelled.
 */
public class ExecutorWithTimeLimit implements ExecutorWithLimits
{
    private final long _endTime;
    private final ExecutorService _singleThreadExecutor = Executors.newSingleThreadExecutor();

    public ExecutorWithTimeLimit(long startTime, long allowedTimeInMillis)
    {
        _endTime = startTime + allowedTimeInMillis;
    }

    @Override
    public <T> T execute(Callable<T> callback) throws CancellationException, Exception
    {
        final long timeRemaining = _endTime - System.currentTimeMillis();
        if (timeRemaining <= 0)
        {
            throw new CancellationException("Too little time remains to schedule callable");
        }

        List<Future<T>> l = _singleThreadExecutor.invokeAll(Collections.singletonList(callback), timeRemaining, TimeUnit.MILLISECONDS);
        return l.get(0).get();
    }

    @Override
    public void shutdown()
    {
        _singleThreadExecutor.shutdown();
    }


}
