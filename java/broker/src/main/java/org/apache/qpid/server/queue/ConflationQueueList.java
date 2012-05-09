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

import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class ConflationQueueList extends SimpleQueueEntryList
{

    private final String _conflationKey;
    private final ConcurrentHashMap<Object, AtomicReference<QueueEntry>> _latestValuesMap =
        new ConcurrentHashMap<Object, AtomicReference<QueueEntry>>();

    public ConflationQueueList(AMQQueue queue, String conflationKey)
    {
        super(queue);
        _conflationKey = conflationKey;
    }

    public String getConflationKey()
    {
        return _conflationKey;
    }

    @Override
    protected ConflationQueueEntry createQueueEntry(ServerMessage message)
    {
        return new ConflationQueueEntry(this, message);
    }

    @Override
    public ConflationQueueEntry add(final ServerMessage message)
    {
        final ConflationQueueEntry entry = (ConflationQueueEntry) (super.add(message));

        final Object keyValue = message.getMessageHeader().getHeader(_conflationKey);
        if (keyValue != null)
        {
            final AtomicReference<QueueEntry> referenceToEntry = new AtomicReference<QueueEntry>(entry);
            AtomicReference<QueueEntry> latestValueReference = null;
            QueueEntry oldEntry;

            // Iterate until we have got a valid atomic reference object and either the referent is newer than the current
            // entry, or the current entry has replaced it in the reference. Note that the head represents a special value
            // indicating that the reference object is no longer valid (it is being removed from the map).
            do
            {
                latestValueReference = getOrPutIfAbsent(keyValue, referenceToEntry);
                oldEntry = latestValueReference == null ? null : latestValueReference.get();
            }
            while(oldEntry != null
                    && oldEntry.compareTo(entry) < 0
                    && oldEntry != getHead()
                    && !latestValueReference.compareAndSet(oldEntry, entry));

            if (oldEntry == null)
            {
                // Unlikely: A newer entry came along and was consumed (and entry removed from map)
                // during our processing of getOrPutIfAbsent().  In this case we know our entry has been superseded.
                discardEntry(entry);
            }
            else if (oldEntry.compareTo(entry) > 0)
            {
                // A newer entry came along
                discardEntry(entry);
            }
            else if (oldEntry.compareTo(entry) < 0)
            {
                // We replaced some other entry to become the newest value
                discardEntry(oldEntry);
            }

            entry.setLatestValueReference(latestValueReference);
        }

        return entry;
    }

    private AtomicReference<QueueEntry> getOrPutIfAbsent(final Object key, final AtomicReference<QueueEntry> referenceToValue)
    {
        AtomicReference<QueueEntry> latestValueReference = _latestValuesMap.putIfAbsent(key, referenceToValue);
        if(latestValueReference == null)
        {
            latestValueReference = _latestValuesMap.get(key);
        }
        return latestValueReference;
    }

    private void discardEntry(final QueueEntry entry)
    {
        if(entry.acquire())
        {
            ServerTransaction txn = new AutoCommitTransaction(getQueue().getVirtualHost().getMessageStore());
            txn.dequeue(entry.getQueue(),entry.getMessage(),
                                    new ServerTransaction.Action()
                                {
                                    @Override
                                    public void postCommit()
                                    {
                                        entry.discard();
                                    }

                                    @Override
                                    public void onRollback()
                                    {

                                    }
                                });
        }
    }

    private final class ConflationQueueEntry extends SimpleQueueEntryImpl
    {

        private AtomicReference<QueueEntry> _latestValueReference;

        public ConflationQueueEntry(SimpleQueueEntryList queueEntryList, ServerMessage message)
        {
            super(queueEntryList, message);
        }

        @Override
        public void release()
        {
            super.release();

            discardIfReleasedEntryIsNoLongerLatest();
        }

        @Override
        public boolean delete()
        {
            if(super.delete())
            {
                if(_latestValueReference != null && _latestValueReference.compareAndSet(this, getHead()))
                {
                    Object key = getMessageHeader().getHeader(_conflationKey);
                    _latestValuesMap.remove(key,_latestValueReference);
                }
                return true;
            }
            else
            {
                return false;
            }
        }

        public void setLatestValueReference(final AtomicReference<QueueEntry> latestValueReference)
        {
            _latestValueReference = latestValueReference;
        }

        private void discardIfReleasedEntryIsNoLongerLatest()
        {
            if(_latestValueReference != null)
            {
                if(_latestValueReference.get() != this)
                {
                    discardEntry(this);
                }
            }
        }

    }

    /**
     * Exposed purposes of unit test only.
     */
    Map<Object, AtomicReference<QueueEntry>> getLatestValuesMap()
    {
        return Collections.unmodifiableMap(_latestValuesMap);
    }

    static class Factory implements QueueEntryListFactory
    {
        private final String _conflationKey;

        Factory(String conflationKey)
        {
            _conflationKey = conflationKey;
        }

        public ConflationQueueList createQueueEntryList(AMQQueue queue)
        {
            return new ConflationQueueList(queue, _conflationKey);
        }
    }
}
