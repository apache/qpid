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

import org.apache.log4j.Logger;
import org.apache.qpid.pool.ReferenceCountingExecutorService;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** This is an abstract base class to handle */
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
    private volatile AtomicBoolean _flowed;
    private QueueBackingStore _backingStore;
    protected AMQQueue _queue;
    private Executor _inhaler;
    private AtomicBoolean _stopped;
    private AtomicReference<MessageInhaler> _asynchronousInhaler = new AtomicReference(null);

    FlowableBaseQueueEntryList(AMQQueue queue)
    {
        _queue = queue;
        _flowed = new AtomicBoolean(false);
        VirtualHost vhost = queue.getVirtualHost();
        if (vhost != null)
        {
            _backingStore = vhost.getQueueBackingStoreFactory().createBacking(queue);
        }

        _stopped = new AtomicBoolean(false);
        _inhaler = ReferenceCountingExecutorService.getInstance().acquireExecutorService();
    }

    public void setFlowed(boolean flowed)
    {
        if (_flowed.get() != flowed)
        {
            _log.warn("Marking Queue(" + _queue.getName() + ") as flowed (" + flowed + ")");
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
            if (_memoryUsageMinimum == 0)
            {
                setMemoryUsageMinimum(_memoryUsageMaximum / 2);
            }

            // if we have now have to much memory in use we need to purge.
            if (_memoryUsageMaximum < _atomicQueueInMemory.get())
            {
                startPurger();
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
            checkAndStartLoader();
        }
    }

    private void checkAndStartLoader()
    {
        // If we've increased the minimum memory above what we have in memory then we need to inhale more
        if (_atomicQueueInMemory.get() <= _memoryUsageMinimum)
        {
            startInhaler();
        }
    }

    private void startInhaler()
    {
        if (_flowed.get())
        {
            MessageInhaler inhaler = new MessageInhaler();

            if (_asynchronousInhaler.compareAndSet(null, inhaler))
            {
                _inhaler.execute(inhaler);
            }
        }
    }

    private void startPurger()
    {
       //TODO create purger, used when maxMemory is reduced creating over memory situation.
       _log.warn("Requested Purger Start.. purger TBC.");
       //_purger.execute(new MessagePurger(this));
    }

    public long getMemoryUsageMinimum()
    {
        return _memoryUsageMinimum;
    }

    public void unloadEntry(QueueEntry queueEntry)
    {
        try
        {
            queueEntry.unload();
            _atomicQueueInMemory.addAndGet(-queueEntry.getSize());
            checkAndStartLoader();
        }
        catch (UnableToFlowMessageException e)
        {
            _atomicQueueInMemory.addAndGet(queueEntry.getSize());
        }
    }

    public void loadEntry(QueueEntry queueEntry)
    {
        queueEntry.load();
        if( _atomicQueueInMemory.addAndGet(queueEntry.getSize()) > _memoryUsageMaximum)
        {
            _log.error("Loaded to much data!:"+_atomicQueueInMemory.get()+"/"+_memoryUsageMaximum);
        }
    }

    public void stop()
    {
        if (!_stopped.getAndSet(true))
        {
            // The SimpleAMQQueue keeps running when stopped so we should just release the services
            // rather than actively shutdown our threads.
            //Shutdown thread for inhaler.
            ReferenceCountingExecutorService.getInstance().releaseExecutorService();
        }
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
     *
     * @param queueEntry the entry that is being flowed to disk
     */
    protected void flowingToDisk(QueueEntryImpl queueEntry)
    {
        try
        {
            queueEntry.unload();
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

    private class MessageInhaler implements Runnable
    {
        public void run()
        {
            String threadName = Thread.currentThread().getName();
            Thread.currentThread().setName("Inhaler-" + _queue.getVirtualHost().getName() + "-" + _queue.getName());
            try
            {
                inhaleList(this);
            }
            finally
            {
                Thread.currentThread().setName(threadName);
            }
        }
    }

    private void inhaleList(MessageInhaler messageInhaler)
    {
        _log.info("Inhaler Running");
        // If in memory count is at or over max then we can't inhale
        if (_atomicQueueInMemory.get() >= _memoryUsageMaximum)
        {
            _log.debug("Unable to start inhaling as we are already over quota:" +
                       _atomicQueueInMemory.get() + ">=" + _memoryUsageMaximum);
            return;
        }

        _asynchronousInhaler.compareAndSet(messageInhaler, null);
        while ((_atomicQueueInMemory.get() < _memoryUsageMaximum) && _asynchronousInhaler.compareAndSet(null, messageInhaler))
        {
            QueueEntryIterator iterator = iterator();

            while (!iterator.getNode().isAvailable() && iterator.advance())
            {
                //Find first AVAILABLE node
            }

            while ((_atomicQueueInMemory.get() < _memoryUsageMaximum) && !iterator.atTail())
            {
                QueueEntry entry = iterator.getNode();

                if (entry.isAvailable() && entry.isFlowed())
                {
                    loadEntry(entry);
                }

                iterator.advance();
            }

            if (iterator.atTail())
            {
                setFlowed(false);
            }

            _asynchronousInhaler.set(null);
        }

        //If we have become flowed or have more capacity since we stopped then schedule the thread to run again.
        if (_flowed.get() && _atomicQueueInMemory.get() < _memoryUsageMaximum)
        {
            _inhaler.execute(messageInhaler);

        }

    }

}
