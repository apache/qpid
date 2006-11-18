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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * We share the executor service among several PoolingFilters. This class reference counts
 * how many filter chains are using the executor service and destroys the service, thus
 * freeing up its threads, when the count reaches zero. It recreates the service when
 * the count is incremented.
 *
 * This is particularly important on the client where failing to destroy the executor
 * service prevents the JVM from shutting down due to the existence of non-daemon threads.
 *
 */
public class ReferenceCountingExecutorService
{
    private static final int MINIMUM_POOL_SIZE = 4;
    private static final int NUM_CPUS = Runtime.getRuntime().availableProcessors();
    private static final int DEFAULT_POOL_SIZE = Math.max(NUM_CPUS, MINIMUM_POOL_SIZE);

    /**
     * We need to be able to check the current reference count and if necessary
     * create the executor service atomically.
     */
    private static final ReferenceCountingExecutorService _instance = new ReferenceCountingExecutorService();

    private final Object _lock = new Object();

    private ExecutorService _pool;

    private int _refCount = 0;

    private int _poolSize = Integer.getInteger("amqj.read_write_pool_size", DEFAULT_POOL_SIZE);

    public static ReferenceCountingExecutorService getInstance()
    {
        return _instance;
    }

    private ReferenceCountingExecutorService()
    {
    }

    ExecutorService acquireExecutorService()
    {
        synchronized (_lock)
        {
            if (_refCount++ == 0)
            {
                _pool = Executors.newFixedThreadPool(_poolSize);
            }
            return _pool;
        }
    }

    void releaseExecutorService()
    {
        synchronized (_lock)
        {
            if (--_refCount == 0)
            {
                _pool.shutdownNow();
            }
        }
    }

    /**
     * The filters that use the executor service should call this method to get access
     * to the service. Note that this method does not alter the reference count.
     *
     * @return the underlying executor service
     */
    public ExecutorService getPool()
    {
        return _pool;
    }
}
