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
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.subscription.Subscription;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class QueueEntryImpl implements QueueEntry
{

    /** Used for debugging purposes. */
    private static final Logger _log = Logger.getLogger(QueueEntryImpl.class);

    private final SimpleQueueEntryList _queueEntryList;

    private AtomicReference<AMQMessage> _messageRef;

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
    private boolean _persistent;
    private boolean _hasBeenUnloaded = false;

    QueueEntryImpl(SimpleQueueEntryList queueEntryList)
    {
        this(queueEntryList, null, Long.MIN_VALUE);
        _state = DELETED_STATE;
    }

    public QueueEntryImpl(SimpleQueueEntryList queueEntryList, AMQMessage message, final long entryId)
    {
        this(queueEntryList, message);

        _entryIdUpdater.set(this, entryId);
    }

    public QueueEntryImpl(SimpleQueueEntryList queueEntryList, AMQMessage message)
    {
        _queueEntryList = queueEntryList;
        _messageRef = new AtomicReference<AMQMessage>(message);
        if (message != null)
        {
            _messageId = message.getMessageId();
            _messageSize = message.getSize();

            if (message.isImmediate())
            {
                _flags |= IMMEDIATE;
            }
            _expiration = message.getExpiration();
            _persistent = message.isPersistent();
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
        return load();
    }

    public Long getMessageId()
    {
        return _messageId;
    }

    public long getSize()
    {
        return _messageSize;
    }

    public boolean getDeliveredToConsumer()
    {
        return (_flags & DELIVERED_TO_CONSUMER) != 0;
    }

    /**
     * Called when this message is delivered to a consumer. (used to implement the 'immediate' flag functionality).
     * And for selector efficiency.
     *
     * This is now also used to unload the message if this entry is on a flowed queue. As a result this method should
     * only be called after the message has been sent.
     */    
    public void setDeliveredToSubscription()
    {
        _flags |= DELIVERED_TO_CONSUMER;

        // We have delivered this message so we can unload it if we are flowed.
        if (_queueEntryList.isFlowed())
        {
            unload();
        }
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

    public boolean isAvailable()
    {
        return _state.getState() == State.AVAILABLE;
    }

    public boolean acquire()
    {
        return acquire(NON_SUBSCRIPTION_ACQUIRED_STATE);
    }

    private boolean acquire(final EntryState state)
    {
        boolean acquired = _stateUpdater.compareAndSet(this, AVAILABLE_STATE, state);
        if (acquired && _stateChangeListeners != null)
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

    public void release()
    {
        _stateUpdater.set(this, AVAILABLE_STATE);
    }

    public String debugIdentity()
    {
        String entry = "[State:" + _state.getState().name() + "]";

        AMQMessage message = _messageRef.get();

        if (message == null)
        {
            return entry + "(Message Unloaded ID:" + _messageId + ")";
        }
        else
        {

            return entry + message.debugIdentity();
        }
    }

    public boolean immediateAndNotDelivered()
    {
        return (_flags & IMMEDIATE_AND_DELIVERED) == IMMEDIATE;
    }

    public ContentHeaderBody getContentHeaderBody() throws AMQException
    {
        return getMessage().getContentHeaderBody();
    }

    public boolean isPersistent() throws AMQException
    {
        return _persistent;
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
        if (_stateChangeListeners != null)
        {
            notifyStateChange(QueueEntry.State.ACQUIRED, QueueEntry.State.AVAILABLE);
        }
    }

    public void dequeue(final StoreContext storeContext) throws FailedDequeueException
    {
        EntryState state = _state;

        if ((state.getState() == State.ACQUIRED) && _stateUpdater.compareAndSet(this, state, DEQUEUED_STATE))
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
        for (StateChangeListener l : _stateChangeListeners)
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
        if (listeners == null)
        {
            _listenersUpdater.compareAndSet(this, null, new CopyOnWriteArraySet<StateChangeListener>());
            listeners = _stateChangeListeners;
        }

        listeners.add(listener);
    }

    public boolean removeStateChangeListener(StateChangeListener listener)
    {
        Set<StateChangeListener> listeners = _stateChangeListeners;
        if (listeners != null)
        {
            return listeners.remove(listener);
        }

        return false;
    }

    public void unload()
    {
        //Get the currently loaded message
        AMQMessage message = _messageRef.get();

        // If we have a message in memory and we have a valid backingStore attempt to unload
        if (message != null && _backingStore != null)
        {
            try
            {
                // The backingStore will now handle concurrent calls to unload and safely synchronize to ensure
                // multiple initial unloads are unloads
                _backingStore.unload(message);
                _hasBeenUnloaded = true;
                _messageRef.set(null);

                if (_log.isDebugEnabled())
                {
                    _log.debug("Unloaded:" + debugIdentity());
                }


                // Clear the message reference if the loaded message is still the one we are processing.

                //Update the memoryState if this load call resulted in the message being purged from memory
                if (!_flowed.getAndSet(true))
                {
                    _queueEntryList.entryUnloadedUpdateMemory(this);
                }

            }
            catch (UnableToFlowMessageException utfme)
            {
                // There is no recovery needed as the memory states remain unchanged.
                if (_log.isDebugEnabled())
                {
                    _log.debug("Unable to Flow message:" + debugIdentity() + ", due to:" + utfme.getMessage());
                }
            }
        }
    }

    public AMQMessage load()
    {
        // MessageId and Backing store are null in test scenarios, normally this is not the case.
        if (_messageId != null && _backingStore != null)
        {
            // See if we have the message currently in memory to return
            AMQMessage message = _messageRef.get();
            // if we don't then we need to start a load process.
            if (message == null)
            {
                //Synchronize here to ensure only the first thread that attempts to load will perform the load from the
                // backing store.
                synchronized (this)
                {
                    // Check again to see if someone else ahead of us loaded the message
                    message = _messageRef.get();
                    // if we still don't have the message then we need to start a load process.
                    if (message == null)
                    {
                        // Load the message and keep a reference to it
                        message = _backingStore.load(_messageId);
                        // Set the message reference
                        _messageRef.set(message);
                    }
                    else
                    {
                        // If someone else loaded the message then we can jump out here as the Memory Updates will
                        // have been performed by the loading thread
                        return message;
                    }
                }

                if (_log.isDebugEnabled())
                {
                    _log.debug("Loaded:" + debugIdentity());
                }

                //Update the memoryState if this load call resulted in the message comming in to memory
                if (_flowed.getAndSet(false))
                {
                    _queueEntryList.entryLoadedUpdateMemory(this);
                }
            }

            // Return the message that was either already in memory or the value we just loaded.
            return message;
        }
        // This can be null but only in the case where we have no messageId
        // in the case where we have no backingStore then we will never have unloaded the message
        return _messageRef.get();
    }

    public boolean isFlowed()
    {
        return _flowed.get();
    }

    public int compareTo(final QueueEntry o)
    {
        QueueEntryImpl other = (QueueEntryImpl) o;
        return getEntryId() > other.getEntryId() ? 1 : getEntryId() < other.getEntryId() ? -1 : 0;
    }

    public QueueEntryImpl getNext()
    {

        QueueEntryImpl next = nextNode();
        while (next != null && next.isDeleted())
        {

            final QueueEntryImpl newNext = next.nextNode();
            if (newNext != null)
            {
                SimpleQueueEntryList._nextUpdater.compareAndSet(this, next, newNext);
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

        if (state != DELETED_STATE && _stateUpdater.compareAndSet(this, state, DELETED_STATE))
        {
            _queueEntryList.advanceHead();
            if (_backingStore != null && _hasBeenUnloaded)
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
