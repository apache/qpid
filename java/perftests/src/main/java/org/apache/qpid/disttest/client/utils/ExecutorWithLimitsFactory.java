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

import java.util.concurrent.Callable;

public class ExecutorWithLimitsFactory
{
    /**
     * Creates an {@link ExecutorWithLimits} that will permit the execution of {@link Callable} implementations until
     * until <code>allowedTimeInMillis</code> milliseconds have elapsed beyond <code>startTime</code>.
     * If <code>allowedTimeInMillis</code> is less than or equal to zero, a {@link ExecutorWithNoLimits}
     * is created that enforces no time-limit.
     *
     * @param startTime start time (milliseconds)
     * @param allowedTimeInMillis allowed time (milliseconds)
     *
     * @return ExecutionLimiter implementation
     */
    public static ExecutorWithLimits createExecutorWithLimit(long startTime, long allowedTimeInMillis)
    {
        if (allowedTimeInMillis > 0)
        {
            return new ExecutorWithTimeLimit(startTime, allowedTimeInMillis);
        }
        else
        {
            return new ExecutorWithNoLimits();
        }
    }

}
