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
package org.apache.qpid.server.subscription;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.SubscriptionMessages;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.protocol.MessageConverterRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.util.StateChangeListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AbstractSubscription implements Subscription
{
    private final long _subscriptionID;
    private final AtomicReference<State> _state = new AtomicReference<State>(State.ACTIVE);
    private final Lock _stateChangeLock = new ReentrantLock();
    private final long _createTime = System.currentTimeMillis();
    private final QueueEntry.SubscriptionAcquiredState _owningState = new QueueEntry.SubscriptionAcquiredState(this);
    private final boolean _acquires;
    private final boolean _seesRequeues;
    private final String _consumerName;
    private final boolean _isTransient;


    private final AtomicLong _deliveredCount = new AtomicLong(0);
    private final AtomicLong _deliveredBytes = new AtomicLong(0);

    private final FilterManager _filters;

    private volatile AMQQueue.Context _queueContext;


    private StateChangeListener<? extends Subscription, State> _stateListener = new StateChangeListener<Subscription, State>()
    {
        public void stateChanged(Subscription sub, State oldState, State newState)
        {
            CurrentActor.get().message(SubscriptionMessages.STATE(newState.toString()));
        }
    };

    private final Class<? extends ServerMessage> _messageClass;
    private final Object _sessionReference;
    private boolean _noLocal;

    protected AbstractSubscription(FilterManager filters,
                                   final Class<? extends ServerMessage> messageClass,
                                   final Object sessionReference,
                                   final boolean acquires,
                                   final boolean seesRequeues,
                                   final String consumerName, final boolean isTransient)
    {
        _messageClass = messageClass;
        _sessionReference = sessionReference;
        _subscriptionID = SUB_ID_GENERATOR.getAndIncrement();
        _filters = filters;
        _acquires = acquires;
        _seesRequeues = seesRequeues;
        _consumerName = consumerName;
        _isTransient = isTransient;
    }

    public final long getSubscriptionID()
    {
        return _subscriptionID;
    }


    public final StateChangeListener<? extends Subscription, State> getStateListener()
    {
        return _stateListener;
    }

    public final void setStateListener(StateChangeListener<? extends Subscription, State> listener)
    {
        _stateListener = listener;
    }


    public final AMQQueue.Context getQueueContext()
    {
        return _queueContext;
    }

    public final void setQueueContext(AMQQueue.Context queueContext)
    {
        _queueContext = queueContext;
    }


    public State getState()
    {
        return _state.get();
    }

    protected boolean updateState(State from, State to)
    {
        return _state.compareAndSet(from, to);
    }

    public final boolean isActive()
    {
        return getState() == State.ACTIVE;
    }

    public final boolean isClosed()
    {
        return getState() == State.CLOSED;
    }


    public final void setNoLocal(boolean noLocal)
    {
        _noLocal = noLocal;
    }


    public final boolean hasInterest(QueueEntry entry)
    {
       //check that the message hasn't been rejected
        if (entry.isRejectedBy(getSubscriptionID()))
        {

            return false;
        }

        if (entry.getMessage().getClass() == _messageClass)
        {
            if(_noLocal)
            {
                Object connectionRef = entry.getMessage().getConnectionReference();
                if (connectionRef != null && connectionRef == _sessionReference)
                {
                    return false;
                }
            }
        }
        else
        {
            // no interest in messages we can't convert
            if(_messageClass != null && MessageConverterRegistry.getConverter(entry.getMessage().getClass(), _messageClass)==null)
            {
                return false;
            }
        }
        return (_filters == null) || _filters.allAllow(entry.asFilterable());
    }


    protected String getFilterLogString()
    {
        StringBuilder filterLogString = new StringBuilder();
        String delimiter = ", ";
        boolean hasEntries = false;
        if (_filters != null && _filters.hasFilters())
        {
            filterLogString.append(_filters.toString());
            hasEntries = true;
        }

        if (!acquires())
        {
            if (hasEntries)
            {
                filterLogString.append(delimiter);
            }
            filterLogString.append("Browser");
            hasEntries = true;
        }

        return filterLogString.toString();
    }


    public final boolean trySendLock()
    {
        return _stateChangeLock.tryLock();
    }

    public final void getSendLock()
    {
        _stateChangeLock.lock();
    }

    public final void releaseSendLock()
    {
        _stateChangeLock.unlock();
    }


    public final long getCreateTime()
    {
        return _createTime;
    }


    public final QueueEntry.SubscriptionAcquiredState getOwningState()
    {
        return _owningState;
    }


    public final boolean acquires()
    {
        return _acquires;
    }

    public final boolean seesRequeues()
    {
        return _seesRequeues;
    }

    public final String getName()
    {
        return _consumerName;
    }

    public final boolean isTransient()
    {
        return _isTransient;
    }


    public final long getBytesOut()
    {
        return _deliveredBytes.longValue();
    }

    public final long getMessagesOut()
    {
        return _deliveredCount.longValue();
    }

    public final void send(final QueueEntry entry, final boolean batch) throws AMQException
    {
        _deliveredCount.incrementAndGet();
        _deliveredBytes.addAndGet(entry.getMessage().getSize());
        doSend(entry, batch);
    }

    protected abstract void doSend(final QueueEntry entry, final boolean batch) throws AMQException;

}
