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
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.MessageConverterRegistry;
import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.util.StateChangeListener;

import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.SUBSCRIPTION_FORMAT;

class QueueConsumer<T extends ConsumerTarget> implements Consumer
{

    public static enum State
    {
        ACTIVE,
        SUSPENDED,
        CLOSED
    }

    private static final Logger _logger = Logger.getLogger(QueueConsumer.class);
    private final AtomicBoolean _targetClosed = new AtomicBoolean(false);
    private final AtomicBoolean _closed = new AtomicBoolean(false);
    private final long _id;
    private final AtomicReference<State> _state = new AtomicReference<State>(State.ACTIVE);
    private final Lock _stateChangeLock = new ReentrantLock();
    private final long _createTime = System.currentTimeMillis();
    private final MessageInstance.ConsumerAcquiredState _owningState = new MessageInstance.ConsumerAcquiredState(this);
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
    private volatile QueueContext _queueContext;
    private StateChangeListener<? extends Consumer, State> _stateListener = new StateChangeListener<Consumer, State>()
    {
        public void stateChanged(Consumer sub, State oldState, State newState)
        {
            CurrentActor.get().message(SubscriptionMessages.STATE(newState.toString()));
        }
    };
    private boolean _noLocal;

    QueueConsumer(final FilterManager filters,
                  final Class<? extends ServerMessage> messageClass,
                  final boolean acquires,
                  final boolean seesRequeues,
                  final String consumerName,
                  final boolean isTransient,
                  T target)
    {
        _messageClass = messageClass;
        _sessionReference = target.getSessionModel().getConnectionReference();
        _id = SUB_ID_GENERATOR.getAndIncrement();
        _filters = filters;
        _acquires = acquires;
        _seesRequeues = seesRequeues;
        _consumerName = consumerName;
        _isTransient = isTransient;
        _target = target;
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
            try
            {
                close();
            }
            catch (AMQException e)
            {
                _logger.error("Unable to remove to remove consumer", e);
                throw new RuntimeException(e);
            }
        }
        final StateChangeListener<Consumer, State> stateListener =
                (StateChangeListener<Consumer, State>) getStateListener();
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
                _target.consumerRemoved(this);
                _queue.unregisterConsumer(this);
            }
            finally
            {
                releaseSendLock();
            }

        }
    }

    void flushBatched()
    {
        _target.flushBatched();
    }

    void queueDeleted()
    {
        _target.queueDeleted();
    }

    boolean wouldSuspend(final MessageInstance msg)
    {
        return !_target.allocateCredit(msg.getMessage());
    }

    void restoreCredit(final MessageInstance queueEntry)
    {
        _target.restoreCredit(queueEntry.getMessage());
    }

    void queueEmpty() throws AMQException
    {
        _target.queueEmpty();
    }

    State getState()
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
            throw new IllegalStateException("Attempt to set queue for consumer " + this + " to " + queue + "when already set to " + getQueue());
        }
        _queue = queue;

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
            CurrentActor.get().message(_logActor.getLogSubject(), SubscriptionMessages.CREATE(filterLogString, queue.isDurable() && exclusive,
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
    public final void flush() throws AMQException
    {
        getQueue().flushConsumer(this);
    }

    boolean resend(final MessageInstance entry) throws AMQException
    {
        return getQueue().resend((QueueEntry)entry, this);
    }

    final SubFlushRunner getRunner()
    {
        return _runner;
    }

    public final long getId()
    {
        return _id;
    }

    public final StateChangeListener<? extends Consumer, State> getStateListener()
    {
        return _stateListener;
    }

    public final void setStateListener(StateChangeListener<? extends Consumer, State> listener)
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

    public final boolean hasInterest(MessageInstance entry)
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

    final MessageInstance.ConsumerAcquiredState getOwningState()
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

    final void send(final QueueEntry entry, final boolean batch) throws AMQException
    {
        _deliveredCount.incrementAndGet();
        ServerMessage message = entry.getMessage();
        if(message == null)
        {
            throw new AMQException("message was null!");
        }
        _deliveredBytes.addAndGet(message.getSize());
        _target.send(entry, batch);
    }
}
