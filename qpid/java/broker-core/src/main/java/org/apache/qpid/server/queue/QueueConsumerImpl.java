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
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.GenericActor;
import org.apache.qpid.server.logging.messages.SubscriptionMessages;
import org.apache.qpid.server.logging.subjects.QueueLogSubject;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.MessageConverterRegistry;
import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.util.StateChangeListener;

import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.SUBSCRIPTION_FORMAT;

class QueueConsumerImpl<T extends ConsumerTarget, E extends QueueEntryImpl<E,Q,L>, Q extends SimpleAMQQueue<E,Q,L>, L extends SimpleQueueEntryList<E,Q,L>> implements QueueConsumer<T,E,Q,L>
{


    private static final Logger _logger = Logger.getLogger(QueueConsumerImpl.class);
    private final AtomicBoolean _targetClosed = new AtomicBoolean(false);
    private final AtomicBoolean _closed = new AtomicBoolean(false);
    private final long _id;
    private final Lock _stateChangeLock = new ReentrantLock();
    private final long _createTime = System.currentTimeMillis();
    private final MessageInstance.ConsumerAcquiredState<QueueConsumer<T,E,Q,L>> _owningState = new MessageInstance.ConsumerAcquiredState<QueueConsumer<T,E,Q,L>>(this);
    private final boolean _acquires;
    private final boolean _seesRequeues;
    private final String _consumerName;
    private final boolean _isTransient;
    private final AtomicLong _deliveredCount = new AtomicLong(0);
    private final AtomicLong _deliveredBytes = new AtomicLong(0);
    private final FilterManager _filters;
    private final Class<? extends ServerMessage> _messageClass;
    private final Object _sessionReference;
    private final Q _queue;
    private GenericActor _logActor = new GenericActor("[" + MessageFormat.format(SUBSCRIPTION_FORMAT, getId())
                                                      + "(UNKNOWN)"
                                                      + "] ");

    static final EnumMap<ConsumerTarget.State, State> STATE_MAP =
            new EnumMap<ConsumerTarget.State, State>(ConsumerTarget.State.class);

    static
    {
        STATE_MAP.put(ConsumerTarget.State.ACTIVE, State.ACTIVE);
        STATE_MAP.put(ConsumerTarget.State.SUSPENDED, State.SUSPENDED);
        STATE_MAP.put(ConsumerTarget.State.CLOSED, State.CLOSED);
    }

    private final T _target;
    private final SubFlushRunner _runner = new SubFlushRunner(this);
    private volatile QueueContext<E,Q,L> _queueContext;
    private StateChangeListener<? super QueueConsumerImpl<T,E,Q,L>, State> _stateListener = new StateChangeListener<QueueConsumerImpl<T,E,Q,L>, State>()
    {
        public void stateChanged(QueueConsumerImpl sub, State oldState, State newState)
        {
            CurrentActor.get().message(SubscriptionMessages.STATE(newState.toString()));
        }
    };
    private final boolean _noLocal;

    QueueConsumerImpl(final Q queue,
                      T target, final String consumerName,
                      final FilterManager filters,
                      final Class<? extends ServerMessage> messageClass,
                      EnumSet<Option> optionSet)
    {

        _messageClass = messageClass;
        _sessionReference = target.getSessionModel().getConnectionReference();
        _id = SUB_ID_GENERATOR.getAndIncrement();
        _filters = filters;
        _acquires = optionSet.contains(Option.ACQUIRES);
        _seesRequeues = optionSet.contains(Option.SEES_REQUEUES);
        _consumerName = consumerName;
        _isTransient = optionSet.contains(Option.TRANSIENT);
        _target = target;
        _queue = queue;
        _noLocal = optionSet.contains(Option.NO_LOCAL);
        setupLogging(optionSet.contains(Option.EXCLUSIVE));

        // Access control
        _queue.getVirtualHost().getSecurityManager().authoriseCreateConsumer(this);


        _target.setStateListener(
                new StateChangeListener<ConsumerTarget, ConsumerTarget.State>()
                    {
                        @Override
                        public void stateChanged(final ConsumerTarget object,
                                                 final ConsumerTarget.State oldState,
                                                 final ConsumerTarget.State newState)
                        {
                            targetStateChanged(oldState, newState);
                        }
                    });
    }

    private void targetStateChanged(final ConsumerTarget.State oldState, final ConsumerTarget.State newState)
    {
        if(oldState != newState)
        {
            if(newState == ConsumerTarget.State.CLOSED)
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

        if(newState == ConsumerTarget.State.CLOSED && oldState != newState && !_closed.get())
        {
            close();
        }
        final StateChangeListener<? super QueueConsumerImpl<T,E,Q,L>, State> stateListener = getStateListener();
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
    public MessageSource getMessageSource()
    {
        return _queue;
    }

    @Override
    public boolean isSuspended()
    {
        return _target.isSuspended();
    }

    @Override
    public void close()
    {
        if(_closed.compareAndSet(false,true))
        {
            getSendLock();
            try
            {
                _target.close();
                _target.consumerRemoved(this);
                _queue.unregisterConsumer(this);
            }
            finally
            {
                releaseSendLock();
            }

        }
    }

    public void flushBatched()
    {
        _target.flushBatched();
    }

    public void queueDeleted()
    {
        _target.queueDeleted();
    }

    public boolean wouldSuspend(final E msg)
    {
        return !_target.allocateCredit(msg.getMessage());
    }

    public void restoreCredit(final E queueEntry)
    {
        _target.restoreCredit(queueEntry.getMessage());
    }

    public void queueEmpty()
    {
        _target.queueEmpty();
    }

    State getState()
    {
        return STATE_MAP.get(_target.getState());
    }

    public final Q getQueue()
    {
        return _queue;
    }

    private void setupLogging(final boolean exclusive)
    {
        String queueString = new QueueLogSubject(_queue).toLogString();

        _logActor = new GenericActor("[" + MessageFormat.format(SUBSCRIPTION_FORMAT, getId())
                             + "("
                             // queueString is [vh(/{0})/qu({1}) ] so need to trim
                             //                ^                ^^
                             + queueString.substring(1,queueString.length() - 3)
                             + ")"
                             + "] ");


        if (CurrentActor.get().getRootMessageLogger().isMessageEnabled(_logActor, _logActor.getLogSubject(), SubscriptionMessages.CREATE_LOG_HIERARCHY))
        {
            final String filterLogString = getFilterLogString();
            CurrentActor.get().message(_logActor.getLogSubject(), SubscriptionMessages.CREATE(filterLogString, _queue.isDurable() && exclusive,
                                                                                filterLogString.length() > 0));
        }
    }

    protected final LogSubject getLogSubject()
    {
        return _logActor.getLogSubject();
    }

    final LogActor getLogActor()
    {
        return _logActor;
    }


    @Override
    public final void flush()
    {
        getQueue().flushConsumer(this);
    }

    public boolean resend(final E entry)
    {
        return getQueue().resend(entry, this);
    }

    public final SubFlushRunner getRunner()
    {
        return _runner;
    }

    public final long getId()
    {
        return _id;
    }

    public final StateChangeListener<? super QueueConsumerImpl<T,E,Q,L>, State> getStateListener()
    {
        return _stateListener;
    }

    public final void setStateListener(StateChangeListener<? super QueueConsumerImpl<T,E,Q,L>, State> listener)
    {
        _stateListener = listener;
    }

    public final QueueContext<E,Q,L> getQueueContext()
    {
        return _queueContext;
    }

    final void setQueueContext(QueueContext<E,Q,L> queueContext)
    {
        _queueContext = queueContext;
    }

    public final boolean isActive()
    {
        return getState() == State.ACTIVE;
    }

    public final boolean isClosed()
    {
        return getState() == State.CLOSED;
    }

    public final boolean hasInterest(E entry)
    {
       //check that the message hasn't been rejected
        if (entry.isRejectedBy(this))
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

    public final MessageInstance.ConsumerAcquiredState<QueueConsumer<T,E,Q,L>> getOwningState()
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

    public final void send(final E entry, final boolean batch)
    {
        _deliveredCount.incrementAndGet();
        ServerMessage message = entry.getMessage();
        _deliveredBytes.addAndGet(message.getSize());
        _target.send(entry, batch);
    }
}
