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
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.filter.Filterable;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.ServerTransaction;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public abstract class QueueEntryImpl implements QueueEntry
{
    private static final Logger _log = Logger.getLogger(QueueEntryImpl.class);

    private final QueueEntryList _queueEntryList;

    private MessageReference _message;

    private Set<Long> _rejectedBy = null;

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

    private final EntryInstanceProperties _instanceProperties = new EntryInstanceProperties();

    /** Number of times this message has been delivered */
    private volatile int _deliveryCount = 0;
    private static final AtomicIntegerFieldUpdater<QueueEntryImpl> _deliveryCountUpdater = AtomicIntegerFieldUpdater
                    .newUpdater(QueueEntryImpl.class, "_deliveryCount");
    private boolean _deliveredToConsumer;


    public QueueEntryImpl(QueueEntryList<?> queueEntryList)
    {
        this(queueEntryList,null,Long.MIN_VALUE);
        _state = DELETED_STATE;
    }


    public QueueEntryImpl(QueueEntryList<?> queueEntryList, ServerMessage message, final long entryId)
    {
        _queueEntryList = queueEntryList;

        _message = message == null ? null : message.newReference();

        _entryIdUpdater.set(this, entryId);
        populateInstanceProperties();
    }

    public QueueEntryImpl(QueueEntryList<?> queueEntryList, ServerMessage message)
    {
        _queueEntryList = queueEntryList;
        _message = message == null ? null :  message.newReference();
        populateInstanceProperties();
    }

    private void populateInstanceProperties()
    {
        if(_message != null)
        {
            _instanceProperties.setProperty(InstanceProperties.Property.PERSISTENT, _message.getMessage().isPersistent());
            _instanceProperties.setProperty(InstanceProperties.Property.EXPIRATION, _message.getMessage().getExpiration());
        }
    }

    public InstanceProperties getInstanceProperties()
    {
        return _instanceProperties;
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

    public ServerMessage getMessage()
    {
        return  _message == null ? null : _message.getMessage();
    }

    public long getSize()
    {
        return getMessage() == null ? 0 : getMessage().getSize();
    }

    public boolean getDeliveredToConsumer()
    {
        return _deliveredToConsumer;
    }

    public boolean expired() throws AMQException
    {
        ServerMessage message = getMessage();
        if(message != null)
        {
            long expiration = message.getExpiration();
            if (expiration != 0L)
            {
                long now = System.currentTimeMillis();

                return (now > expiration);
            }
        }
        return false;

    }

    public boolean isAvailable()
    {
        return _state == AVAILABLE_STATE;
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
        boolean acquired = _stateUpdater.compareAndSet(this, AVAILABLE_STATE, state);

        if(acquired && _stateChangeListeners != null)
        {
            notifyStateChange(State.AVAILABLE, State.ACQUIRED);
        }

        return acquired;
    }

    public boolean acquire(Subscription sub)
    {
        final boolean acquired = acquire(sub.getOwningState());
        if(acquired)
        {
            _deliveredToConsumer = true;
        }
        return acquired;
    }

    public boolean acquiredBySubscription()
    {

        return (_state instanceof SubscriptionAcquiredState);
    }

    public boolean isAcquiredBy(Subscription subscription)
    {
        EntryState state = _state;
        return state instanceof SubscriptionAcquiredState
               && ((SubscriptionAcquiredState)state).getSubscription() == subscription;
    }

    public void release()
    {
        EntryState state = _state;

        if((state.getState() == State.ACQUIRED) &&_stateUpdater.compareAndSet(this, state, AVAILABLE_STATE))
        {

            if(state instanceof SubscriptionAcquiredState)
            {
                getQueue().decrementUnackedMsgCount(this);
                Subscription subscription = ((SubscriptionAcquiredState)state).getSubscription();
                if (subscription != null)
                {
                    subscription.releaseQueueEntry(this);
                }
            }

            if(!getQueue().isDeleted())
            {
                getQueue().requeue(this);
                if(_stateChangeListeners != null)
                {
                    notifyStateChange(QueueEntry.State.ACQUIRED, QueueEntry.State.AVAILABLE);
                }

            }
            else if(acquire())
            {
                routeToAlternate();
            }
        }

    }

    public void setRedelivered()
    {
        _instanceProperties.setProperty(InstanceProperties.Property.REDELIVERED, Boolean.TRUE);
    }

    public boolean isRedelivered()
    {
        return Boolean.TRUE.equals(_instanceProperties.getProperty(InstanceProperties.Property.REDELIVERED));
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
        Subscription subscription = getDeliveredSubscription();

        if (subscription != null)
        {
            if (_rejectedBy == null)
            {
                _rejectedBy = new HashSet<Long>();
            }

            _rejectedBy.add(subscription.getSubscriptionID());
        }
        else
        {
            _log.warn("Requesting rejection by null subscriber:" + this);
        }
    }

    public boolean isRejectedBy(long subscriptionId)
    {

        if (_rejectedBy != null) // We have subscriptions that rejected this message
        {
            return _rejectedBy.contains(subscriptionId);
        }
        else // This messasge hasn't been rejected yet.
        {
            return false;
        }
    }

    private void dequeue()
    {
        EntryState state = _state;

        if((state.getState() == State.ACQUIRED) &&_stateUpdater.compareAndSet(this, state, DEQUEUED_STATE))
        {
            Subscription s = null;
            if (state instanceof SubscriptionAcquiredState)
            {
                getQueue().decrementUnackedMsgCount(this);
                s = ((SubscriptionAcquiredState) state).getSubscription();
                s.onDequeue(this);
            }

            getQueue().dequeue(this,s);
            if(_stateChangeListeners != null)
            {
                notifyStateChange(state.getState() , QueueEntry.State.DEQUEUED);
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

    private boolean dispose()
    {
        EntryState state = _state;

        if(state != DELETED_STATE && _stateUpdater.compareAndSet(this,state,DELETED_STATE))
        {
            _queueEntryList.entryDeleted(this);
            onDelete();
            _message.release();

            return true;
        }
        else
        {
            return false;
        }
    }

    public void delete()
    {
        dequeue();

        dispose();
    }

    public void routeToAlternate()
    {
        final AMQQueue currentQueue = getQueue();
        Exchange alternateExchange = currentQueue.getAlternateExchange();

        if (alternateExchange != null)
        {
            List<? extends BaseQueue> queues = alternateExchange.route(getMessage(), getInstanceProperties());
            final ServerMessage message = getMessage();
            if ((queues == null || queues.size() == 0) && alternateExchange.getAlternateExchange() != null)
            {
                queues = alternateExchange.getAlternateExchange().route(getMessage(), getInstanceProperties());
            }



            if (queues != null && queues.size() != 0)
            {
                final List<? extends BaseQueue> rerouteQueues = queues;
                ServerTransaction txn = new LocalTransaction(getQueue().getVirtualHost().getMessageStore());

                txn.enqueue(rerouteQueues, message, new ServerTransaction.Action()
                {
                    public void postCommit()
                    {
                        try
                        {
                            for (BaseQueue queue : rerouteQueues)
                            {
                                queue.enqueue(message);
                            }
                        }
                        catch (AMQException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }

                    public void onRollback()
                    {

                    }
                });

                txn.dequeue(currentQueue, message, new ServerTransaction.Action()
                {
                    public void postCommit()
                    {
                        delete();
                    }

                    public void onRollback()
                    {

                    }
                });

                txn.commit();
            }
        }
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


    public int compareTo(final QueueEntry o)
    {
        QueueEntryImpl other = (QueueEntryImpl)o;
        return getEntryId() > other.getEntryId() ? 1 : getEntryId() < other.getEntryId() ? -1 : 0;
    }

    protected void onDelete()
    {
    }

    public QueueEntryList getQueueEntryList()
    {
        return _queueEntryList;
    }

    public boolean isDeleted()
    {
        return _state.isDispensed();
    }

    public int getDeliveryCount()
    {
        return _deliveryCount;
    }

    public void incrementDeliveryCount()
    {
        _deliveryCountUpdater.incrementAndGet(this);
    }

    public void decrementDeliveryCount()
    {
        _deliveryCountUpdater.decrementAndGet(this);
    }

    @Override
    public Filterable asFilterable()
    {
        return Filterable.Factory.newInstance(getMessage(), getInstanceProperties());
    }

    public String toString()
    {
        return "QueueEntryImpl{" +
                "_entryId=" + _entryId +
                ", _state=" + _state +
                '}';
    }

    private static class EntryInstanceProperties implements InstanceProperties
    {
        private final EnumMap<Property, Object> _properties = new EnumMap<Property, Object>(Property.class);

        @Override
        public Object getProperty(final Property prop)
        {
            return _properties.get(prop);
        }

        private void setProperty(Property prop, Object value)
        {
            _properties.put(prop, value);
        }
    }

}
