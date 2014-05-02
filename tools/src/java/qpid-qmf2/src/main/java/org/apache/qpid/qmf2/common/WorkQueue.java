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
package org.apache.qpid.qmf2.common;

// Misc Imports
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * This is an implementation of a QMF2 WorkQueue. In practice this is likely to be used by an Agent or Console.
 *
 * @author Fraser Adams
 */
public class WorkQueue
{
    /**
     * Used to implement a thread safe queue of WorkItem objects
     */
    private BlockingQueue<WorkItem> _workQueue = new LinkedBlockingQueue<WorkItem>();

    /**
     * Return the count of pending WorkItems that can be retrieved.
     * @return the count of pending WorkItems that can be retrieved.
     */
    public int size()
    {
        return _workQueue.size();
    }

    /**
     * Obtains the next pending work item - blocking version
     *
     * @return the next pending work item, or null if none available.
     */
    public WorkItem getNextWorkitem()
    {
        try
        {
            return _workQueue.take();
        }
        catch (InterruptedException ie)
        {
            return null;
        }
    }

    /**
     * Obtains the next pending work item - balking version
     *
     * @param timeout the timeout in seconds. If timeout = 0 it returns immediately with either a WorkItem or null
     * @return the next pending work item, or null if none available.
     */
    public WorkItem getNextWorkitem(long timeout)
    {
        try
        {
            return _workQueue.poll(timeout, TimeUnit.SECONDS);
        }
        catch (InterruptedException ie)
        {
            return null;
        }
    }

    /**
     * Adds a WorkItem to the WorkQueue. 
     *
     * @param item the WorkItem passed to the WorkQueue
     */
    public void addWorkItem(WorkItem item)
    {
        // We wrap the blocking put() method in a loop "just in case" InterruptedException occurs
        // if it does we retry the put otherwise we carry on, notify then exit.
        while (true)
        {
            try
            {
                _workQueue.put(item);
                break;
            }
            catch (InterruptedException ie)
            {
                continue;
            }
        }
    }
}
