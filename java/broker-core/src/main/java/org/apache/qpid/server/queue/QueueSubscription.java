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
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.GenericActor;
import org.apache.qpid.server.logging.messages.SubscriptionMessages;
import org.apache.qpid.server.logging.subjects.QueueLogSubject;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.MessageConverterRegistry;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.subscription.SubscriptionTarget;
import org.apache.qpid.server.util.StateChangeListener;

import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.SUBSCRIPTION_FORMAT;

class QueueSubscription<T extends SubscriptionTarget> implements Subscription
{
    private static final Logger _logger = Logger.getLogger(QueueSubscription.class);
    private final AtomicBoolean _targetClosed = new AtomicBoolean(false);
    private final AtomicBoolean _closed = new AtomicBoolean(false);
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
    private final Class<? extends ServerMessage> _messageClass;
    private final Object _sessionReference;
    private SimpleAMQQueue _queue;
    private GenericActor _logActor;

    static final EnumMap<SubscriptionTarget.State, State> STATE_MAP =
            new EnumMap<SubscriptionTarget.State, State>(SubscriptionTarget.State.class);

    static
    {
        STATE_MAP.put(SubscriptionTarget.State.ACTIVE, State.ACTIVE);
        STATE_MAP.put(SubscriptionTarget.State.SUSPENDED, State.SUSPENDED);
        STATE_MAP.put(SubscriptionTarget.State.CLOSED, State.CLOSED);
    }

    private final T _target;
    private final SubFlushRunner _runner = new SubFlushRunner(this);
    private volatile QueueContext _queueContext;
    private StateChangeListener<? extends Subscription, State> _stateListener = new StateChangeListener<Subscription, State>()
    {
        public void stateChanged(Subscription sub, State oldState, State newState)
        {
            CurrentActor.get().message(SubscriptionMessages.STATE(newState.toString()));
        }
    };
    private boolean _noLocal;

    QueueSubscription(final FilterManager filters,
                      final Class<? extends ServerMessage> messageClass,
                      final boolean acquires,
                      final boolean seesRequeues,
                      final String consumerName,
                      final boolean isTransient,
                      T target)
    {
        _messageClass = messageClass;
        _sessionReference = target.getSessionModel().getConnectionReference();
        _subscriptionID = SUB_ID_GENERATOR.getAndIncrement();
        _filters = filters;
        _acquires = acquires;
        _seesRequeues = seesRequeues;
        _consumerName = consumerName;
        _isTransient = isTransient;
        _target = target;
        _target.setStateListener(
                new StateChangeListener<SubscriptionTarget, SubscriptionTarget.State>()
                    {
                        @Override
                        public void stateChanged(final SubscriptionTarget object,
                                                 final SubscriptionTarget.State oldState,
                                                 final SubscriptionTarget.State newState)
                        {
                            targetStateChanged(oldState, newState);
                        }
                    });
    }

    private void targetStateChanged(final SubscriptionTarget.State oldState, final SubscriptionTarget.State newState)
    {
        if(oldState != newState)
        {
            if(newState == SubscriptionTarget.State.CLOSED)
            {
                if(_targetClosed.compareAndSet(false,true))
                {
                    CurrentActor.get().message(getLogSubject(), SubscriptionMessages.CLOSE());
                }
            }
            else
            {
                CurrentActor.get().message(getLogSubject(),SubscriptionMessages.STATE(newState.toString()));
            }
        }

        if(newState == SubscriptionTarget.State.CLOSED && oldState != newState && !_closed.get())
        {
            try
            {
                close();
            }
            catch (AMQException e)
            {
                _logger.error("Unable to remove to remove subscription", e);
                throw new RuntimeException(e);
            }
        }
        final StateChangeListener<Subscription, State> stateListener =
                (StateChangeListener<Subscription, State>) getStateListener();
        if(stateListener != null)
        {
            stateListener.stateChanged(this, STATE_MAP.get(oldState), STATE_MAP.get(newState));
        }
    }

    public T getTarget()
    {
        return _target;
    }

    @Override
    public void externalStateChange()
    {
        getQueue().deliverAsync(this);
    }

    @Override
    public long getUnacknowledgedBytes()
    {
        return _target.getUnacknowledgedBytes();
    }

    @Override
    public long getUnacknowledgedMessages()
    {
        return _target.getUnacknowledgedMessages();
    }

    @Override
    public AMQSessionModel getSessionModel()
    {
        return _target.getSessionModel();
    }

    @Override
    public boolean isSuspended()
    {
        return _target.isSuspended();
    }

    @Override
    public void close() throws AMQException
    {
        if(_closed.compareAndSet(false,true))
        {
            getSendLock();
            try
            {
                _target.close();
                _target.subscriptionRemoved(this);
                _queue.unregisterSubscription(this);
            }
            finally
            {
                releaseSendLock();
            }

        }
    }

    @Override
    public void flushBatched()
    {
        _target.flushBatched();
    }

    @Override
    public void queueDeleted()
    {
        _target.queueDeleted();
    }

    @Override
    public boolean wouldSuspend(final QueueEntry msg)
    {
        return !_target.allocateCredit(msg);
    }

    @Override
    public void restoreCredit(final QueueEntry queueEntry)
    {
        _target.restoreCredit(queueEntry);
    }

    @Override
    public void queueEmpty() throws AMQException
    {
        _target.queueEmpty();
    }

    @Override
    public State getState()
    {
        return STATE_MAP.get(_target.getState());
    }

    public final SimpleAMQQueue getQueue()
    {
        return _queue;
    }

    final void setQueue(SimpleAMQQueue queue, boolean exclusive)
    {
        if(getQueue() != null)
        {
            throw new IllegalStateException("Attempt to set queue for subscription " + this + " to " + queue + "when already set to " + getQueue());
        }
        _queue = queue;

        String queueString = new QueueLogSubject(_queue).toLogString();

        _logActor = new GenericActor("[" + MessageFormat.format(SUBSCRIPTION_FORMAT, getSubscriptionID())
                             + "("
                             // queueString is [vh(/{0})/qu({1}) ] so need to trim
                             //                ^                ^^
                             + queueString.substring(1,queueString.length() - 3)
                             + ")"
                             + "] ");


        if (CurrentActor.get().getRootMessageLogger().isMessageEnabled(_logActor, _logActor.getLogSubject(), SubscriptionMessages.CREATE_LOG_HIERARCHY))
        {
            final String filterLogString = getFilterLogString();
            CurrentActor.get().message(_logActor.getLogSubject(), SubscriptionMessages.CREATE(filterLogString, queue.isDurable() && exclusive,
                                                                                filterLogString.length() > 0));
        }
    }

    protected final LogSubject getLogSubject()
    {
        return _logActor.getLogSubject();
    }

    public final LogActor getLogActor()
    {
        return _logActor;
    }


    @Override
    public final void flush() throws AMQException
    {
        getQueue().flushSubscription(this);
    }

    @Override
    public boolean resend(final QueueEntry entry) throws AMQException
    {
        return getQueue().resend(entry, this);
    }

    final SubFlushRunner getRunner()
    {
        return _runner;
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

    final QueueContext getQueueContext()
    {
        return _queueContext;
    }

    final void setQueueContext(QueueContext queueContext)
    {
        _queueContext = queueContext;
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
            if(_messageClass != null && MessageConverterRegistry.getConverter(entry.getMessage().getClass(),
                                                                              _messageClass)==null)
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
        _target.send(entry, batch);
    }
}
