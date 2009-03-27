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

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** This is an abstract base class to handle */
public abstract class FlowableBaseQueueEntryList implements QueueEntryList
{
    protected static final Logger _log = Logger.getLogger(FlowableBaseQueueEntryList.class);

    private final AtomicInteger _atomicQueueCount = new AtomicInteger(0);
    private final AtomicLong _atomicQueueSize = new AtomicLong(0L);
    protected final AtomicLong _atomicQueueInMemory = new AtomicLong(0L);
    /** The maximum amount of memory that is allocated to this queue. Beyond this the queue will flow to disk. */

    protected long _memoryUsageMaximum = -1L;

    /** The minimum amount of memory that is allocated to this queue. If the queueDepth hits this level then more flowed data can be read in. */
    protected long _memoryUsageMinimum = 0;
    private volatile AtomicBoolean _flowed;
    private QueueBackingStore _backingStore;
    protected AMQQueue _queue;
    private Executor _inhaler;
    private Executor _purger;
    private AtomicBoolean _stopped;
    private AtomicReference<MessageInhaler> _asynchronousInhaler = new AtomicReference(null);
    protected boolean _disableFlowToDisk;
    private AtomicReference<MessagePurger> _asynchronousPurger = new AtomicReference(null);
    private static final int BATCH_PROCESS_COUNT = 100;
    protected FlowableBaseQueueEntryList _parentQueue;

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
        _purger = ReferenceCountingExecutorService.getInstance().acquireExecutorService();
        _disableFlowToDisk = true;
    }

    public void setFlowed(boolean flowed)
    {
        if (_flowed.get() != flowed)
        {
            _log.warn("Marking Queue(" + _queue.getName() + ") as flowed (" + flowed + ")");
            _flowed.set(flowed);
        }
    }

    protected void showUsage()
    {
        showUsage("");
    }

    protected void showUsage(String prefix)
    {
        if (_log.isTraceEnabled())
        {
            _log.trace(prefix + " Queue(" + _queue.getName() + ") usage:" + memoryUsed()
                       + "/" + getMemoryUsageMinimum() + "<>" + getMemoryUsageMaximum()
                       + "/" + dataSize());
        }
    }

    public boolean isFlowed()
    {
        if (_parentQueue != null)
        {
            return _parentQueue.isFlowed();
        }
        else
        {
            return _flowed.get();
        }
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

        if (maximumMemoryUsage >= 0)
        {
            _disableFlowToDisk = false;
        }

        // Don't attempt to start the inhaler/purger unless we have a minimum value specified.
        if (_memoryUsageMaximum >= 0)
        {
            setMemoryUsageMinimum(_memoryUsageMaximum / 2);

            // if we have now have to much memory in use we need to purge.
            if (_memoryUsageMaximum < _atomicQueueInMemory.get())
            {
                setFlowed(true);
                startPurger();
            }
        }
        else
        {
            if (_log.isInfoEnabled())
            {
                _log.info("Disabling Flow to Disk for queue:" + _queue.getName());
            }
            _disableFlowToDisk = true;
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
            checkAndStartInhaler();
        }
    }

    private void checkAndStartInhaler()
    {
        // If we've increased the minimum memory above what we have in memory then
        // we need to inhale more if there is more
        if (!_disableFlowToDisk && _atomicQueueInMemory.get() < _memoryUsageMinimum && _atomicQueueSize.get() > 0)
        {
            startInhaler();
        }
    }

    private void startInhaler()
    {
        MessageInhaler inhaler = new MessageInhaler();

        if (_asynchronousInhaler.compareAndSet(null, inhaler))
        {
            _inhaler.execute(inhaler);
        }
    }

    private void startPurger()
    {
        MessagePurger purger = new MessagePurger();

        if (_asynchronousPurger.compareAndSet(null, purger))
        {
            _purger.execute(purger);
        }
    }

    public long getMemoryUsageMinimum()
    {
        return _memoryUsageMinimum;
    }

    /**
     * Only to be called by the QueueEntry
     *
     * @param queueEntry the entry to unload
     */
    public void entryUnloadedUpdateMemory(QueueEntry queueEntry)
    {
        if (_parentQueue != null)
        {
            _parentQueue.entryUnloadedUpdateMemory(queueEntry);
        }
        else
        {
            if (!_disableFlowToDisk && _atomicQueueInMemory.addAndGet(-queueEntry.getSize()) < 0)
            {
                _log.error("InMemory Count just went below 0:" + queueEntry.debugIdentity());
            }

            checkAndStartInhaler();
        }
    }

    /**
     * Only to be called from the QueueEntry
     *
     * @param queueEntry the entry to load
     */
    public void entryLoadedUpdateMemory(QueueEntry queueEntry)
    {
        if (_parentQueue != null)
        {
            _parentQueue.entryLoadedUpdateMemory(queueEntry);
        }
        else
        {
            if (!_disableFlowToDisk && _atomicQueueInMemory.addAndGet(queueEntry.getSize()) > _memoryUsageMaximum)
            {
                _log.error("Loaded to much data!:" + _atomicQueueInMemory.get() + "/" + _memoryUsageMaximum);
                setFlowed(true);
                startPurger();
            }
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
            ReferenceCountingExecutorService.getInstance().releaseExecutorService();

            _backingStore.close();
        }
    }

    /**
     * Mark this queue as part of another QueueEntryList for accounting purposes.
     *
     * All Calls from the QueueEntry to the QueueEntryList need to check if there is
     * a parent QueueEntrylist upon which the action should take place.
     *
     * @param queueEntryList The parent queue that is performing accounting.
     */    
    public void setParentQueueEntryList(FlowableBaseQueueEntryList queueEntryList)
    {
        _parentQueue = queueEntryList;
    }

    protected void incrementCounters(final QueueEntryImpl queueEntry)
    {
        if (_parentQueue != null)
        {
            _parentQueue.incrementCounters(queueEntry);
        }
        else
        {
            _atomicQueueCount.incrementAndGet();
            _atomicQueueSize.addAndGet(queueEntry.getSize());
            long inUseMemory = _atomicQueueInMemory.addAndGet(queueEntry.getSize());

            if (!_disableFlowToDisk && inUseMemory > _memoryUsageMaximum)
            {
                setFlowed(true);
                queueEntry.unload();
            }
        }
    }

    protected void dequeued(QueueEntryImpl queueEntry)
    {
        if (_parentQueue != null)
        {
            _parentQueue.dequeued(queueEntry);
        }
        else
        {
            _atomicQueueCount.decrementAndGet();
            _atomicQueueSize.addAndGet(-queueEntry.getSize());
            if (!queueEntry.isFlowed())
            {
                if (_atomicQueueInMemory.addAndGet(-queueEntry.getSize()) < 0)
                {
                    _log.error("InMemory Count just went below 0 on dequeue.");
                }
            }
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
        if (_log.isInfoEnabled())
        {
            _log.info("Inhaler Running:" + _queue.getName());
            showUsage("Inhaler Running:" + _queue.getName());
        }
        // If in memory count is at or over max then we can't inhale
        if (_atomicQueueInMemory.get() >= _memoryUsageMaximum)
        {
            if (_log.isDebugEnabled())
            {
                _log.debug("Unable to start inhaling as we are already over quota:" +
                           _atomicQueueInMemory.get() + ">=" + _memoryUsageMaximum);
            }
            return;
        }

        _asynchronousInhaler.compareAndSet(messageInhaler, null);
        int inhaled = 1;

        while ((_atomicQueueInMemory.get() < _memoryUsageMaximum) // we havn't filled our max memory
               && (_atomicQueueInMemory.get() < _atomicQueueSize.get()) // we haven't loaded all that is available
               && (inhaled < BATCH_PROCESS_COUNT) // limit the number of runs we do
               && (inhaled > 0) // ensure we could inhale something
               && _asynchronousInhaler.compareAndSet(null, messageInhaler)) // Ensure we are the running inhaler
        {
            inhaled = 0;
            QueueEntryIterator iterator = iterator();

            // If the inhaler is running and delivery rate picks up ensure that we just don't chase the delivery thread.
            while ((_atomicQueueInMemory.get() < _memoryUsageMaximum)
                   && !iterator.getNode().isAvailable() && iterator.advance())
            {
                //Find first AVAILABLE node
            }

            // Because the above loop checks then moves on to the next entry a check for atTail will return true but
            // we won't have checked the last entry to see if we can load it. So create atEndofList and update it based
            // on the return from advance() which returns true if it can advance.
            boolean atEndofList = false;
            while ((_atomicQueueInMemory.get() < _memoryUsageMaximum) // we havn't filled our max memory
                   && (inhaled < BATCH_PROCESS_COUNT) // limit the number of runs we do
                   && !atEndofList) // We have reached end of list QueueEntries
            {
                QueueEntry entry = iterator.getNode();

                if (entry.isAvailable() && entry.isFlowed())
                {
                    if (_atomicQueueInMemory.get() + entry.getSize() > _memoryUsageMaximum)
                    {
                        // We don't have space for this message so we need to stop inhaling.
                        if (_log.isDebugEnabled())
                        {
                            _log.debug("Entry won't fit in memory stopping inhaler:" + entry.debugIdentity());
                        }
                        inhaled = BATCH_PROCESS_COUNT;
                    }
                    else
                    {
                        entry.load();
                        inhaled++;
                    }
                }

                atEndofList = !iterator.advance();
            }

            if (iterator.atTail())
            {
                setFlowed(false);
            }

            _asynchronousInhaler.set(null);
        }

        if (_log.isInfoEnabled())
        {
            _log.info("Inhaler Stopping:" + _queue.getName());
            showUsage("Inhaler Stopping:" + _queue.getName());
        }

        //If we have become flowed or have more capacity since we stopped then schedule the thread to run again.
        if (_flowed.get() && _atomicQueueInMemory.get() < _memoryUsageMaximum)
        {
            if (_log.isInfoEnabled())
            {
                _log.info("Rescheduling Inhaler:" + _queue.getName());
            }
            _inhaler.execute(messageInhaler);
        }

    }

    private class MessagePurger implements Runnable
    {
        public void run()
        {
            String threadName = Thread.currentThread().getName();
            Thread.currentThread().setName("Purger-" + _queue.getVirtualHost().getName() + "-" + _queue.getName());
            try
            {
                purgeList(this);
            }
            finally
            {
                Thread.currentThread().setName(threadName);
            }
        }
    }

    private void purgeList(MessagePurger messagePurger)
    {
        // If in memory count is at or over max then we can't inhale
        if (_atomicQueueInMemory.get() <= _memoryUsageMinimum)
        {
            if (_log.isDebugEnabled())
            {
                _log.debug("Unable to start purging as we are already below our minimum cache level:" +
                           _atomicQueueInMemory.get() + "<=" + _memoryUsageMinimum);
            }
            return;
        }

        if (_log.isInfoEnabled())
        {
            _log.info("Purger Running:" + _queue.getName());
            showUsage("Purger Running:" + _queue.getName());
        }

        _asynchronousPurger.compareAndSet(messagePurger, null);
        int purged = 0;

        while ((_atomicQueueInMemory.get() > _memoryUsageMinimum)
               && purged < BATCH_PROCESS_COUNT
               && _asynchronousPurger.compareAndSet(null, messagePurger))
        {
            QueueEntryIterator iterator = iterator();

            //There are potentially AQUIRED messages that can be purged but we can't purge the last AQUIRED message
            // as it may have just become AQUIRED and not yet delivered.

            //To be safe only purge available messages. This should be fine as long as we have a small prefetch.
            while (!iterator.getNode().isAvailable() && iterator.advance())
            {
                //Find first AVAILABLE node
            }

            // Count up the memory usage to find our minimum point
            long memoryUsage = 0;
            boolean atTail = false;
            while ((memoryUsage < _memoryUsageMaximum) && !atTail)
            {
                QueueEntry entry = iterator.getNode();

                if (entry.isAvailable() && !entry.isFlowed())
                {
                    memoryUsage += entry.getSize();
                }

                atTail = !iterator.advance();
            }

            //Purge remainging mesages on queue
            while (!atTail && (purged < BATCH_PROCESS_COUNT))
            {
                QueueEntry entry = iterator.getNode();

                if (entry.isAvailable() && !entry.isFlowed())
                {
                    entry.unload();
                    purged++;
                }

                atTail = !iterator.advance();
            }

            _asynchronousPurger.set(null);
        }

        if (_log.isInfoEnabled())
        {
            _log.info("Purger Stopping:" + _queue.getName());
            showUsage("Purger Stopping:" + _queue.getName());
        }

        //If we are still flowed and are over the minimum value then schedule to run again.
        if (_flowed.get() && _atomicQueueInMemory.get() > _memoryUsageMinimum)
        {
            if (_log.isInfoEnabled())
            {
                _log.info("Rescheduling Purger:" + _queue.getName());
            }
            _purger.execute(messagePurger);
        }
    }
}
