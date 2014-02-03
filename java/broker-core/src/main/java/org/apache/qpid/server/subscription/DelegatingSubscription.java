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

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.SubscriptionMessages;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.util.StateChangeListener;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class DelegatingSubscription<T extends SubscriptionTarget> extends AbstractSubscription
{
    private static final Logger _logger = Logger.getLogger(DelegatingSubscription.class);
    private final AtomicBoolean _closed = new AtomicBoolean(false);

    static final EnumMap<SubscriptionTarget.State, State> STATE_MAP =
            new EnumMap<SubscriptionTarget.State, State>(SubscriptionTarget.State.class);

    static
    {
        STATE_MAP.put(SubscriptionTarget.State.ACTIVE, State.ACTIVE);
        STATE_MAP.put(SubscriptionTarget.State.SUSPENDED, State.SUSPENDED);
        STATE_MAP.put(SubscriptionTarget.State.CLOSED, State.CLOSED);
    }

    private final T _target;

    public DelegatingSubscription(final FilterManager filters,
                                  final Class<? extends ServerMessage> messageClass,
                                  final boolean acquires,
                                  final boolean seesRequeues,
                                  final String consumerName,
                                  final boolean isTransient,
                                  T target)
    {
        super(filters, messageClass, target.getSessionModel().getConnectionReference(),
              acquires, seesRequeues, consumerName, isTransient);
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
                if(_closed.compareAndSet(false,true))
                {
                    CurrentActor.get().message(getLogSubject(), SubscriptionMessages.CLOSE());
                }
            }
            else
            {
                CurrentActor.get().message(getLogSubject(),SubscriptionMessages.STATE(newState.toString()));
            }
        }

        if(newState == SubscriptionTarget.State.CLOSED && oldState != newState)
        {
            try
            {
                getQueue().unregisterSubscription(this);
            }
            catch (AMQException e)
            {
                _logger.error("Unable to remove to remove subscription", e);
                throw new RuntimeException(e);
            }
        }
        final StateChangeListener<Subscription, State> stateListener = getStateListener();
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
    public void close()
    {
        _target.close();
        _target.subscriptionRemoved(this);
    }

    @Override
    protected void doSend(final QueueEntry entry, final boolean batch) throws AMQException
    {
        _target.send(entry, batch);
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
}
