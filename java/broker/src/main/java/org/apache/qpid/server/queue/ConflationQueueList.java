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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;

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
    public QueueEntry add(final ServerMessage message)
    {
        ConflationQueueEntry entry = (ConflationQueueEntry) (super.add(message));
        AtomicReference<QueueEntry> latestValueReference = null;

        Object value = message.getMessageHeader().getHeader(_conflationKey);
        if(value != null)
        {
            latestValueReference = _latestValuesMap.get(value);
            if(latestValueReference == null)
            {
                _latestValuesMap.putIfAbsent(value, new AtomicReference<QueueEntry>(entry));
                latestValueReference = _latestValuesMap.get(value);
            }
            QueueEntry oldEntry;

            do
            {
                oldEntry = latestValueReference.get();
            }
            while(oldEntry.compareTo(entry) < 0 && !latestValueReference.compareAndSet(oldEntry, entry));

            if(oldEntry.compareTo(entry) < 0)
            {
                // We replaced some other entry to become the newest value
                if(oldEntry.acquire())
                {
                    discardEntry(oldEntry);
                }
            }
            else if (oldEntry.compareTo(entry) > 0)
            {
                // A newer entry came along
                discardEntry(entry);

            }
        }

        entry.setLatestValueReference(latestValueReference);
        return entry;
    }

    private void discardEntry(final QueueEntry entry)
    {
        if(entry.acquire())
        {
            ServerTransaction txn = new AutoCommitTransaction(getQueue().getVirtualHost().getTransactionLog());
            txn.dequeue(entry.getQueue(),entry.getMessage(),
                                    new ServerTransaction.Action()
                                {
                                    public void postCommit()
                                    {
                                        entry.discard();
                                    }

                                    public void onRollback()
                                    {

                                    }
                                });
        }
    }

    private final class ConflationQueueEntry extends QueueEntryImpl
    {


        private AtomicReference<QueueEntry> _latestValueReference;

        public ConflationQueueEntry(SimpleQueueEntryList queueEntryList, ServerMessage message)
        {
            super(queueEntryList, message);
        }


        public void release()
        {
            super.release();

            if(_latestValueReference != null)
            {
                if(_latestValueReference.get() != this)
                {
                    discardEntry(this);
                }
            }

        }

        public void setLatestValueReference(final AtomicReference<QueueEntry> latestValueReference)
        {
            _latestValueReference = latestValueReference;
        }
    }

    static class Factory implements QueueEntryListFactory
    {
        private final String _conflationKey;

        Factory(String conflationKey)
        {
            _conflationKey = conflationKey;
        }

        public QueueEntryList createQueueEntryList(AMQQueue queue)
        {
            return new ConflationQueueList(queue, _conflationKey);
        }
    }

}
