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
package org.apache.qpid.server.queue;

import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This is an abstract base class to handle
 */
public abstract class FlowableBaseQueueEntryList implements FlowableQueueEntryList
{
    private static final Logger _log = Logger.getLogger(FlowableBaseQueueEntryList.class);

    private final AtomicInteger _atomicQueueCount = new AtomicInteger(0);
    private final AtomicLong _atomicQueueSize = new AtomicLong(0L);
    private final AtomicLong _atomicQueueInMemory = new AtomicLong(0L);
    /** The maximum amount of memory that is allocated to this queue. Beyond this the queue will flow to disk. */

    private long _memoryUsageMaximum = 0;

    /** The minimum amount of memory that is allocated to this queue. If the queueDepth hits this level then more flowed data can be read in. */
    private long _memoryUsageMinimum = 0;
    private AtomicBoolean _flowed;
    private QueueBackingStore _backingStore;
    protected AMQQueue _queue;

    FlowableBaseQueueEntryList(AMQQueue queue)
    {
        _queue = queue;
        _flowed = new AtomicBoolean(false);
        VirtualHost vhost = queue.getVirtualHost();
        if (vhost != null)
        {
            _backingStore = vhost.getQueueBackingStore();
        }
    }

    public void setFlowed(boolean flowed)
    {
        if (_flowed.get() != flowed)
        {
            _log.info("Marking Queue(" + _queue.getName() + ") as flowed (" + flowed + ")");
            _flowed.set(flowed);
        }
    }

    public boolean isFlowed()
    {
        return _flowed.get();
    }

    public int size()
    {
        return _atomicQueueCount.get();
    }

    public long dataSize()
    {
        return _atomicQueueSize.get();
    }

    public long memoryUsed()
    {
        return _atomicQueueInMemory.get();
    }

    public void setMemoryUsageMaximum(long maximumMemoryUsage)
    {
        _memoryUsageMaximum = maximumMemoryUsage;

        // Don't attempt to start the inhaler/purger unless we have a minimum value specified.
        if (_memoryUsageMaximum > 0)
        {
            // If we've increased the max memory above what we have in memory then we can inhale more
            if (_memoryUsageMaximum > _atomicQueueInMemory.get())
            {
                //TODO start inhaler
            }
            else // if we have now have to much memory in use we need to purge.
            {
                //TODO start purger
            }
        }
    }

    public long getMemoryUsageMaximum()
    {
        return _memoryUsageMaximum;
    }

    public void setMemoryUsageMinimum(long minimumMemoryUsage)
    {
        _memoryUsageMinimum = minimumMemoryUsage;

        // Don't attempt to start the inhaler unless we have a minimum value specified.
        if (_memoryUsageMinimum > 0)
        {
            // If we've increased the minimum memory above what we have in memory then we need to inhale more
            if (_memoryUsageMinimum >= _atomicQueueInMemory.get())
            {
                //TODO start inhaler
            }
        }
    }

    public long getMemoryUsageMinimum()
    {
        return _memoryUsageMinimum;
    }

    protected boolean willCauseFlowToDisk(QueueEntryImpl queueEntry)
    {
        return _memoryUsageMaximum != 0 && memoryUsed() + queueEntry.getSize() > _memoryUsageMaximum;
    }

    protected void incrementCounters(final QueueEntryImpl queueEntry)
    {
        _atomicQueueCount.incrementAndGet();
        _atomicQueueSize.addAndGet(queueEntry.getSize());
        if (!willCauseFlowToDisk(queueEntry))
        {
            _atomicQueueInMemory.addAndGet(queueEntry.getSize());
        }
        else
        {
            setFlowed(true);
            flowingToDisk(queueEntry);
        }
    }

    /**
     * Called when we are now flowing to disk
     * @param queueEntry the entry that is being flowed to disk
     */
    protected void flowingToDisk(QueueEntryImpl queueEntry)
    {
        try
        {
            queueEntry.flow();
        }
        catch (UnableToFlowMessageException e)
        {
            _atomicQueueInMemory.addAndGet(queueEntry.getSize());
        }
    }

    protected void dequeued(QueueEntryImpl queueEntry)
    {
        _atomicQueueCount.decrementAndGet();
        _atomicQueueSize.addAndGet(-queueEntry.getSize());
        if (!queueEntry.isFlowed())
        {
            _atomicQueueInMemory.addAndGet(-queueEntry.getSize());
        }
    }

    public QueueBackingStore getBackingStore()
    {
        return _backingStore;
    }

}
