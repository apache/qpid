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

import org.apache.qpid.AMQException;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.log4j.Logger;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;


public class QueueEntryImpl implements QueueEntry
{

    /**
     * Used for debugging purposes.
     */
    private static final Logger _log = Logger.getLogger(QueueEntryImpl.class);

    private final AMQQueue _queue;
    private final AMQMessage _message;


    private Set<Subscription> _rejectedBy = null;

    private final AtomicReference<Object> _owner = new AtomicReference<Object>();
    private final AtomicLong _entryId = new AtomicLong();


    public QueueEntryImpl(AMQQueue queue, AMQMessage message, final long entryId)
    {
        _queue = queue;
        _message = message;
        _entryId.set(entryId);
    }

    public QueueEntryImpl(AMQQueue queue, AMQMessage message)
    {
        _queue = queue;
        _message = message;
    }

    protected void setEntryId(long entryId)
    {
        _entryId.set(entryId);
    }

    protected long getEntryId()
    {
        return _entryId.get();
    }

    public AMQQueue getQueue()
    {
        return _queue;
    }

    public AMQMessage getMessage()
    {
        return _message;
    }

    public long getSize()
    {
        return getMessage().getSize();
    }

    public boolean getDeliveredToConsumer()
    {
        return getMessage().getDeliveredToConsumer();
    }

    public boolean expired() throws AMQException
    {
        return getMessage().expired(_queue);
    }

    public boolean isAcquired()
    {
        return _owner.get() != null;
    }

    public boolean acquire(Subscription sub)
    {
        return !(_owner.compareAndSet(null, sub == null ? this : sub));
    }

    public boolean acquiredBySubscription()
    {
        Object owner = _owner.get();
        return (owner != null) && (owner != this);
    }

    public void setDeliveredToSubscription()
    {
        getMessage().setDeliveredToConsumer();
    }

    public void release()
    {
        _owner.set(null);
    }

    public String debugIdentity()
    {
        return getMessage().debugIdentity();
    }


    public boolean immediateAndNotDelivered() 
    {
        return _message.immediateAndNotDelivered();
    }

    public void setRedelivered(boolean b)
    {
        getMessage().setRedelivered(b);
    }

    public Subscription getDeliveredSubscription()
    {
        synchronized (this)
        {
            Object owner = _owner.get();
            if (owner instanceof Subscription)
            {
                return (Subscription) owner;
            }
            else
            {
                return null;
            }
        }
    }

    public void reject()
    {
        reject(getDeliveredSubscription());
    }

    public void reject(Subscription subscription)
    {
        if (subscription != null)
        {
            if (_rejectedBy == null)
            {
                _rejectedBy = new HashSet<Subscription>();
            }

            _rejectedBy.add(subscription);
        }
        else
        {
            _log.warn("Requesting rejection by null subscriber:" + debugIdentity());
        }
    }

    public boolean isRejectedBy(Subscription subscription)
    {        

        if (_rejectedBy != null) // We have subscriptions that rejected this message
        {
            return _rejectedBy.contains(subscription);
        }
        else // This messasge hasn't been rejected yet.
        {
            return false;
        }
    }


    public void requeue(final StoreContext storeContext) throws AMQException
    {
        _queue.requeue(storeContext, this);
    }

    public void dequeue(final StoreContext storeContext) throws FailedDequeueException
    {
        getQueue().dequeue(storeContext, this);
    }

    public void dispose(final StoreContext storeContext) throws MessageCleanupException
    {
        getMessage().decrementReference(storeContext);
    }

    public void restoreCredit()
    {
        Object owner = _owner.get();
        if(owner instanceof Subscription)
        {
            Subscription s = (Subscription) owner;
            s.restoreCredit(this);
        }
    }

    public void discard(StoreContext storeContext) throws AMQException
    {
        //if the queue is null then the message is waiting to be acked, but has been removed.
        if (getQueue() != null)
        {
            dequeue(storeContext);
        }

        dispose(storeContext);
    }

    public boolean isQueueDeleted()
    {
        return getQueue().isDeleted();
    }

    public int compareTo(final QueueEntry o)
    {
        QueueEntryImpl other = (QueueEntryImpl)o;
        return getEntryId() > other.getEntryId() ? 1 : getEntryId() < other.getEntryId() ? -1 : 0;
    }
}
