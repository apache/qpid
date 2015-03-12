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
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public interface TaskExecutor
{
    boolean isRunning();

    void start();

    void stopImmediately();

    void stop();

    void run(VoidTask task) throws CancellationException;

    <T, E extends Exception> T run(TaskWithException<T, E> task) throws CancellationException, E;

    <E extends Exception> void run(VoidTaskWithException<E> task) throws CancellationException, E;

    <T> T run(Task<T> task) throws CancellationException;

    <T> Future<T> submit(Task<T> task) throws CancellationException;

    boolean isTaskExecutorThread();

    Executor getExecutor();
}
