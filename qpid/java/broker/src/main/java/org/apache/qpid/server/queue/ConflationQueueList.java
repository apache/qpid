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

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.AMQChannel;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class ConflationQueueList extends SimpleQueueEntryList
{

    private final AMQShortString _conflationKey;
    private final ConcurrentHashMap<Object, AtomicReference<QueueEntry>> _latestValuesMap =
        new ConcurrentHashMap<Object, AtomicReference<QueueEntry>>();

    public ConflationQueueList(AMQQueue queue, AMQShortString conflationKey)
    {
        super(queue);
        _conflationKey = conflationKey;
    }

    @Override
    protected ConflationQueueEntry createQueueEntry(AMQMessage message)
    {
        return new ConflationQueueEntry(this, message);
    }


    @Override
    public QueueEntry add(final AMQMessage message, final StoreContext storeContext)
    {
        ConflationQueueEntry entry = (ConflationQueueEntry) (super.add(message, storeContext));
        AtomicReference<QueueEntry> latestValueReference = null;

        try
        {
            Object value = ((BasicContentHeaderProperties)message.getContentHeaderBody().properties).getHeaders().get(_conflationKey);            
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
                        oldEntry.discard(storeContext);
                    }
                }
                else if (oldEntry.compareTo(entry) > 0)
                {
                    // A newer entry came along
                    if(entry.acquire())
                    {
                        entry.discard(storeContext);
                    }
                }
            }
        }
        catch (AMQException e)
        {

            throw new RuntimeException(e);
        }

        entry.setLatestValueReference(latestValueReference);
        return entry;
    }

    private final class ConflationQueueEntry extends QueueEntryImpl
    {


        private AtomicReference<QueueEntry> _latestValueReference;

        public ConflationQueueEntry(SimpleQueueEntryList queueEntryList, AMQMessage message)
        {
            super(queueEntryList, message);
        }


        public void release()
        {
            Subscription sub = getDeliveredSubscription();

            StoreContext storeContext = null;
            if(sub != null)
            {
                AMQChannel channel = sub.getChannel();
                if(channel != null)
                {
                    storeContext = channel.getStoreContext();
                }
            }


            super.release();

            if(_latestValueReference != null)
            {
                if((_latestValueReference.get() != this) && acquire())
                {
                    if(storeContext == null)
                    {
                        storeContext = new StoreContext();
                    }
                    try
                    {
                        discard(storeContext);
                    }
                    catch (FailedDequeueException e)
                    {
                        throw new RuntimeException(e);
                    }
                    catch (MessageCleanupException e)
                    {
                        throw new RuntimeException(e);
                    }
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
        private final AMQShortString _conflationKey;

        Factory(AMQShortString conflationKey)
        {
            _conflationKey = conflationKey;
        }

        public QueueEntryList createQueueEntryList(AMQQueue queue)
        {
            return new ConflationQueueList(queue, _conflationKey);
        }
    }

}
