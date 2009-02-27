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
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.log4j.Logger;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArraySet;


public class QueueEntryImpl implements QueueEntry
{

    /**
     * Used for debugging purposes.
     */
    private static final Logger _log = Logger.getLogger(QueueEntryImpl.class);

    private final SimpleQueueEntryList _queueEntryList;

    private AMQMessage _message;

    private boolean _redelivered;

    private Set<Subscription> _rejectedBy = null;

    private volatile EntryState _state = AVAILABLE_STATE;

    private static final
        AtomicReferenceFieldUpdater<QueueEntryImpl, EntryState>
            _stateUpdater =
        AtomicReferenceFieldUpdater.newUpdater
        (QueueEntryImpl.class, EntryState.class, "_state");


    private volatile Set<StateChangeListener> _stateChangeListeners;

    private static final
        AtomicReferenceFieldUpdater<QueueEntryImpl, Set>
                _listenersUpdater =
        AtomicReferenceFieldUpdater.newUpdater
        (QueueEntryImpl.class, Set.class, "_stateChangeListeners");


    private static final
        AtomicLongFieldUpdater<QueueEntryImpl>
            _entryIdUpdater =
        AtomicLongFieldUpdater.newUpdater
        (QueueEntryImpl.class, "_entryId");


    private volatile long _entryId;

    volatile QueueEntryImpl _next;

    private long _messageSize;
    private QueueBackingStore _backingStore;
    private AtomicBoolean _flowed;
    private Long _messageId;

    private byte _flags = 0;

    private long _expiration;

    private static final byte IMMEDIATE_AND_DELIVERED = (byte) (IMMEDIATE | DELIVERED_TO_CONSUMER);


    QueueEntryImpl(SimpleQueueEntryList queueEntryList)
    {
        this(queueEntryList,null,Long.MIN_VALUE);
        _state = DELETED_STATE;
    }


    public QueueEntryImpl(SimpleQueueEntryList queueEntryList, AMQMessage message, final long entryId)
    {
        this(queueEntryList,message);

        _entryIdUpdater.set(this, entryId);
    }

    public QueueEntryImpl(SimpleQueueEntryList queueEntryList, AMQMessage message)
    {
        _queueEntryList = queueEntryList;
        _message = message;
        if (message != null)
        {
            _messageId = message.getMessageId();
            _messageSize = message.getSize();
            
            if(message.isImmediate())
            {
                _flags |= IMMEDIATE;
            }
            _expiration = message.getExpiration();
        }
        _backingStore = queueEntryList.getBackingStore();
        _flowed = new AtomicBoolean(false);
    }

    protected void setEntryId(long entryId)
    {
        _entryIdUpdater.set(this, entryId);
    }

    protected long getEntryId()
    {
        return _entryId;
    }

    public AMQQueue getQueue()
    {
        return _queueEntryList.getQueue();
    }

    public AMQMessage getMessage()
    {
        return _message;
    }

    public long getSize()
    {
        return _messageSize;
    }

    public boolean getDeliveredToConsumer()
    {
        return (_flags & DELIVERED_TO_CONSUMER) != 0;
    }

    public void setDeliveredToConsumer()
    {
        _flags |= DELIVERED_TO_CONSUMER;
    }

    public boolean expired() throws AMQException
    {
        if (_expiration != 0L)
        {
            long now = System.currentTimeMillis();

            return (now > _expiration);
        }

        return false;
    }

    public void setExpiration(final long expiration)
    {
        _expiration = expiration;
    }

    public boolean isAcquired()
    {
        return _state.getState() == State.ACQUIRED;
    }

    public boolean acquire()
    {
        return acquire(NON_SUBSCRIPTION_ACQUIRED_STATE);
    }

    private boolean acquire(final EntryState state)
    {
        boolean acquired = _stateUpdater.compareAndSet(this,AVAILABLE_STATE, state);
        if(acquired && _stateChangeListeners != null)
        {
            notifyStateChange(State.AVAILABLE, State.ACQUIRED);
        }

        return acquired;
    }

    public boolean acquire(Subscription sub)
    {
        return acquire(sub.getOwningState());
    }

    public boolean acquiredBySubscription()
    {

        return (_state instanceof SubscriptionAcquiredState);
    }

    public void setDeliveredToSubscription()
    {
        _flags |= DELIVERED_TO_CONSUMER;
    }

    public void release()
    {
        _stateUpdater.set(this,AVAILABLE_STATE);
    }

    public String debugIdentity()
    {
        return getMessage().debugIdentity();
    }


    public boolean immediateAndNotDelivered() 
    {
        return (_flags & IMMEDIATE_AND_DELIVERED) == IMMEDIATE;
    }

    public ContentHeaderBody getContentHeaderBody() throws AMQException
    {
        return _message.getContentHeaderBody();
    }

    public boolean isPersistent() throws AMQException
    {
        return _message.isPersistent();
    }

    public boolean isRedelivered()
    {
        return _redelivered;
    }

    public void setRedelivered(boolean redelivered)
    {
        _redelivered = redelivered;
        // todo - here we could record this message as redelivered on this queue in the transactionLog
        // so we don't have to mark all messages on recover as redelivered.
    }

    public Subscription getDeliveredSubscription()
    {
            EntryState state = _state;
            if (state instanceof SubscriptionAcquiredState)
            {
                return ((SubscriptionAcquiredState) state).getSubscription();
            }
            else
            {
                return null;
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
        getQueue().requeue(storeContext, this);
        if(_stateChangeListeners != null)
        {
            notifyStateChange(QueueEntry.State.ACQUIRED, QueueEntry.State.AVAILABLE);
        }
    }

    public void dequeue(final StoreContext storeContext) throws FailedDequeueException
    {
        EntryState state = _state;

        if((state.getState() == State.ACQUIRED) &&_stateUpdater.compareAndSet(this, state, DEQUEUED_STATE))
        {
            if (state instanceof SubscriptionAcquiredState)
            {
                Subscription s = ((SubscriptionAcquiredState) state).getSubscription();
                s.restoreCredit(this);
            }

            _queueEntryList.dequeued(this);

            getQueue().dequeue(storeContext, this);

            if (_stateChangeListeners != null)
            {
                notifyStateChange(state.getState(), QueueEntry.State.DEQUEUED);
            }
        }
    }

    private void notifyStateChange(final State oldState, final State newState)
    {
        for(StateChangeListener l : _stateChangeListeners)
        {
            l.stateChanged(this, oldState, newState);
        }
    }

    public void dequeueAndDelete(StoreContext storeContext) throws FailedDequeueException
    {
        //if the queue is null (i.e. queue.delete()'d) then the message is waiting to be acked, but has already be delete()'d;
        if (getQueue() != null)
        {
            dequeue(storeContext);
        }

        delete();
    }

    public boolean isQueueDeleted()
    {
        return getQueue().isDeleted();
    }

    public void addStateChangeListener(StateChangeListener listener)
    {
        Set<StateChangeListener> listeners = _stateChangeListeners;
        if(listeners == null)
        {
            _listenersUpdater.compareAndSet(this, null, new CopyOnWriteArraySet<StateChangeListener>());
            listeners = _stateChangeListeners;
        }

        listeners.add(listener);
    }

    public boolean removeStateChangeListener(StateChangeListener listener)
    {
        Set<StateChangeListener> listeners = _stateChangeListeners;
        if(listeners != null)
        {
            return listeners.remove(listener);
        }

        return false;
    }

    public void flow() throws UnableToFlowMessageException
    {
        if (_message != null && _backingStore != null)
        {
            if(_log.isDebugEnabled())
            {
                _log.debug("Flowing message:" + _message.debugIdentity());
            }
            _backingStore.flow(_message);
            _message = null;
            _flowed.getAndSet(true);            
        }
    }

    public void recover()
    {
        if (_messageId != null && _backingStore != null)
        {
            _message = _backingStore.recover(_messageId);
            _flowed.getAndSet(false);
        }
    }

    public boolean isFlowed()
    {
        return _flowed.get();
    }


    public int compareTo(final QueueEntry o)
    {
        QueueEntryImpl other = (QueueEntryImpl)o;
        return getEntryId() > other.getEntryId() ? 1 : getEntryId() < other.getEntryId() ? -1 : 0;
    }

    public QueueEntryImpl getNext()
    {

        QueueEntryImpl next = nextNode();
        while(next != null && next.isDeleted())
        {

            final QueueEntryImpl newNext = next.nextNode();
            if(newNext != null)
            {
                SimpleQueueEntryList._nextUpdater.compareAndSet(this,next, newNext);
                next = nextNode();
            }
            else
            {
                next = null;
            }

        }
        return next;
    }

    QueueEntryImpl nextNode()
    {
        return _next;
    }

    public boolean isDeleted()
    {
        return _state == DELETED_STATE;
    }

    public boolean delete()
    {
        EntryState state = _state;

        if(state != DELETED_STATE && _stateUpdater.compareAndSet(this,state,DELETED_STATE))
        {
            _queueEntryList.advanceHead();
            if (_backingStore != null)
            {
                _backingStore.delete(_messageId);
            }
            return true;
        }
        else
        {
            return false;
        }
    }

    public QueueEntryList getQueueEntryList()
    {
        return _queueEntryList;
    }


}
