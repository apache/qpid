/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpid.server.messageStore;

import java.util.ArrayList;

/**
 * Created by Arnaud Simon
 * Date: 15-May-2007
 * Time: 10:11:49
 */
public abstract class Pool
{
    // The maximum size of the pool.
    private int _maxPoolSize = -1;
    // The current size of the pool.
    private int _currentPoolSize = 0;
    // The pool objects.
    private volatile ArrayList<Object> _poolObjects = new ArrayList<Object>();
    //The current number of created instances.
    private int _instanceCount = 0;

    /**
     * Create a pool of specified size. Negative or null pool sizes are
     * disallowed.
     *
     * @param poolSize      The size of the pool to create. Should be 1 or
     *                      greater.
     * @throws Exception    If the pool size is less than 1.
     */
    public Pool(int poolSize) throws Exception
    {
        if (poolSize <= 0)
        {
            throw new Exception("pool size is less than 1: " + poolSize);
        }
        _maxPoolSize = poolSize;
    }

    /**
     * Return the maximum size of this pool.
     *
     * @return  The maximum size of this pool.
     */
    public final int maxSize()
    {
        return _maxPoolSize;
    }

    /**
     * Return the current number of created instances.
     *
     * @return The current number of created instances in this pool.
     */
    public final int numberOfInstances()
    {
        return _instanceCount;
    }

    /**
     * Extending classes MUST define how to create an instance of the object
     * that they pool.
     *
     * @return              An instance of the pooled object.
     * @throws Exception    In case of internal error.
     */
    abstract protected Object createInstance() throws Exception;

    /**
     * Remove the next available object from the pool or wait for one to become
     * available.
     *
     * @return           The next available instance.
     * @throws Exception If the call is interrupted
     */
    public final synchronized Object acquireInstance() throws Exception
    {
        while (_currentPoolSize == _maxPoolSize)
        {
            try
            {
                this.wait();
            }
            catch (InterruptedException e)
            {
                  throw new Exception("pool wait threw interrupted exception", e);
            }
        }
        if (_poolObjects.size() == 0)
        {
            _poolObjects.add(createInstance());
            _instanceCount++;
        }
        _currentPoolSize++;
        return _poolObjects.remove(0);
    }

    /**
     * Return an object back into this pool.
     *
     * @param object    The returning object.
     */
    public synchronized void releaseInstance(Object object)
    {
        _poolObjects.add(object);
        _currentPoolSize--;
        this.notify();
    }

    /**
     * Return a dead object back into this pool.
     *
     */
    public synchronized void releaseDeadInstance()
    {
       _instanceCount--;
       _currentPoolSize--;
        this.notify();
    }
}
