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

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.SUBSCRIPTION_FORMAT;

import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.filter.JMSSelectorFilter;
import org.apache.qpid.server.filter.MessageFilter;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.messages.SubscriptionMessages;
import org.apache.qpid.server.logging.subjects.QueueLogSubject;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.MessageConverterRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.util.StateChangeListener;

class QueueConsumerImpl
    extends AbstractConfiguredObject<QueueConsumerImpl>
        implements QueueConsumer<QueueConsumerImpl>, LogSubject
{


    private static final Logger _logger = Logger.getLogger(QueueConsumerImpl.class);
    private final AtomicBoolean _targetClosed = new AtomicBoolean(false);
    private final AtomicBoolean _closed = new AtomicBoolean(false);
    private final long _consumerNumber;
    private final long _createTime = System.currentTimeMillis();
    private final MessageInstance.ConsumerAcquiredState<QueueConsumerImpl> _owningState = new MessageInstance.ConsumerAcquiredState<QueueConsumerImpl>(this);
    private final boolean _acquires;
    private final boolean _seesRequeues;
    private final boolean _isTransient;
    private final AtomicLong _deliveredCount = new AtomicLong(0);
    private final AtomicLong _deliveredBytes = new AtomicLong(0);
    private final FilterManager _filters;
    private final Class<? extends ServerMessage> _messageClass;
    private final Object _sessionReference;
    private final AbstractQueue _queue;

    static final EnumMap<ConsumerTarget.State, State> STATE_MAP =
            new EnumMap<ConsumerTarget.State, State>(ConsumerTarget.State.class);

    static
    {
        STATE_MAP.put(ConsumerTarget.State.ACTIVE, State.ACTIVE);
        STATE_MAP.put(ConsumerTarget.State.SUSPENDED, State.QUIESCED);
        STATE_MAP.put(ConsumerTarget.State.CLOSED, State.DELETED);
    }

    private final ConsumerTarget _target;
    private final StateChangeListener<ConsumerTarget, ConsumerTarget.State>
            _listener;
    private volatile QueueContext _queueContext;
    private StateChangeListener<? super QueueConsumerImpl, State> _stateListener = new StateChangeListener<QueueConsumerImpl, State>()
    {
        public void stateChanged(QueueConsumerImpl sub, State oldState, State newState)
        {
            getEventLogger().message(QueueConsumerImpl.this, SubscriptionMessages.STATE(newState.toString()));
        }
    };
    @ManagedAttributeField
    private boolean _exclusive;
    @ManagedAttributeField
    private boolean _noLocal;
    @ManagedAttributeField
    private String _distributionMode;
    @ManagedAttributeField
    private String _settlementMode;
    @ManagedAttributeField
    private String _selector;

    QueueConsumerImpl(final AbstractQueue queue,
                      ConsumerTarget target, final String consumerName,
                      final FilterManager filters,
                      final Class<? extends ServerMessage> messageClass,
                      EnumSet<Option> optionSet)
    {
        super(parentsMap(queue, target.getSessionModel().getModelObject()),
              createAttributeMap(consumerName, filters, optionSet));
        _messageClass = messageClass;
        _sessionReference = target.getSessionModel().getConnectionReference();
        _consumerNumber = CONSUMER_NUMBER_GENERATOR.getAndIncrement();
        _filters = filters;
        _acquires = optionSet.contains(Option.ACQUIRES);
        _seesRequeues = optionSet.contains(Option.SEES_REQUEUES);
        _isTransient = optionSet.contains(Option.TRANSIENT);
        _target = target;
        _queue = queue;

        // Access control
        authoriseCreate(this);

        open();

        setupLogging();

        _listener = new StateChangeListener<ConsumerTarget, ConsumerTarget.State>()
        {
            @Override
            public void stateChanged(final ConsumerTarget object,
                                     final ConsumerTarget.State oldState,
                                     final ConsumerTarget.State newState)
            {
                targetStateChanged(oldState, newState);
            }
        };
        _target.addStateListener(_listener);
    }

    @Override
    protected SecurityManager getSecurityManager()
    {
        return _queue.getVirtualHost().getSecurityManager();
    }

    private static Map<String, Object> createAttributeMap(String name, FilterManager filters, EnumSet<Option> optionSet)
    {
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(ID, UUID.randomUUID());
        attributes.put(NAME, name);
        attributes.put(EXCLUSIVE, optionSet.contains(Option.EXCLUSIVE));
        attributes.put(NO_LOCAL, optionSet.contains(Option.NO_LOCAL));
        attributes.put(DISTRIBUTION_MODE, optionSet.contains(Option.ACQUIRES) ? "MOVE" : "COPY");
        attributes.put(DURABLE,optionSet.contains(Option.DURABLE));
        attributes.put(LIFETIME_POLICY, LifetimePolicy.DELETE_ON_SESSION_END);
        if(filters != null)
        {
            Iterator<MessageFilter> iter = filters.filters();
            while(iter.hasNext())
            {
                MessageFilter filter = iter.next();
                if(filter instanceof JMSSelectorFilter)
                {
                    attributes.put(SELECTOR, ((JMSSelectorFilter) filter).getSelector());
                    break;
                }
            }
        }

        return attributes;
    }

    private void targetStateChanged(final ConsumerTarget.State oldState, final ConsumerTarget.State newState)
    {
        if(oldState != newState)
        {
            if(newState == ConsumerTarget.State.CLOSED)
            {
                if(_targetClosed.compareAndSet(false,true))
                {
                    getEventLogger().message(getLogSubject(), SubscriptionMessages.CLOSE());
                }
            }
            else
            {
                getEventLogger().message(getLogSubject(), SubscriptionMessages.STATE(newState.toString()));
            }
        }

        if(newState == ConsumerTarget.State.CLOSED && oldState != newState && !_closed.get())
        {
            close();
        }
        final StateChangeListener<? super QueueConsumerImpl, State> stateListener = getStateListener();
        if(stateListener != null)
        {
            stateListener.stateChanged(this, STATE_MAP.get(oldState), STATE_MAP.get(newState));
        }
    }

    @Override
    public ConsumerTarget getTarget()
    {
        return _target;
    }

    @Override
    public void externalStateChange()
    {
        _queue.deliverAsync();
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
    protected void onClose()
    {
        if(_closed.compareAndSet(false,true))
        {
            _target.getSendLock();
            try
            {
                _target.consumerRemoved(this);
                _target.removeStateChangeListener(_listener);
                _queue.unregisterConsumer(this);
                deleted();
            }
            finally
            {
                _target.releaseSendLock();
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

    public boolean wouldSuspend(final QueueEntry msg)
    {
        return !_target.allocateCredit(msg.getMessage());
    }

    public void restoreCredit(final QueueEntry queueEntry)
    {
        _target.restoreCredit(queueEntry.getMessage());
    }

    public void queueEmpty()
    {
        _target.queueEmpty();
    }

    @Override
    public State getState()
    {
        return STATE_MAP.get(_target.getState());
    }

    public final AMQQueue getQueue()
    {
        return _queue;
    }

    private void setupLogging()
    {
        final String filterLogString = getFilterLogString();
        getEventLogger().message(this,
                                 SubscriptionMessages.CREATE(filterLogString, _queue.isDurable() && _exclusive,
                                                             filterLogString.length() > 0));
    }

    protected final LogSubject getLogSubject()
    {
        return this;
    }

    @Override
    public final void flush()
    {
        _queue.flushConsumer(this);
    }

    public boolean resend(final QueueEntry entry)
    {
        return getQueue().resend(entry, this);
    }

    public final long getConsumerNumber()
    {
        return _consumerNumber;
    }

    public final StateChangeListener<? super QueueConsumerImpl, State> getStateListener()
    {
        return _stateListener;
    }

    public final void setStateListener(StateChangeListener<? super QueueConsumerImpl, State> listener)
    {
        _stateListener = listener;
    }

    public final QueueContext getQueueContext()
    {
        return _queueContext;
    }

    final void setQueueContext(QueueContext queueContext)
    {
        _queueContext = queueContext;
    }

    public final boolean isActive()
    {
        return getState() == State.ACTIVE;
    }

    public final boolean isClosed()
    {
        return getState() == State.DELETED;
    }

    public final boolean hasInterest(QueueEntry entry)
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
        return getTarget().trySendLock();
    }

    public final void getSendLock()
    {
        getTarget().getSendLock();
    }

    public final void releaseSendLock()
    {
        getTarget().releaseSendLock();
    }

    public final long getCreateTime()
    {
        return _createTime;
    }

    public final MessageInstance.ConsumerAcquiredState<QueueConsumerImpl> getOwningState()
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

    public final void send(final QueueEntry entry, final boolean batch)
    {
        _deliveredCount.incrementAndGet();
        long size = _target.send(this, entry, batch);
        _deliveredBytes.addAndGet(size);
    }

    @Override
    public void acquisitionRemoved(final QueueEntry node)
    {
        _target.acquisitionRemoved(node);
        _queue.decrementUnackedMsgCount(node);
    }

    @Override
    public String getDistributionMode()
    {
        return _distributionMode;
    }

    @Override
    public String getSettlementMode()
    {
        return _settlementMode;
    }

    @Override
    public boolean isExclusive()
    {
        return _exclusive;
    }

    @Override
    public boolean isNoLocal()
    {
        return _noLocal;
    }

    @Override
    public String getSelector()
    {
        return _selector;
    }

    @Override
    public String toLogString()
    {
        String logString;
        if(_queue == null)
        {
            logString = "[" + MessageFormat.format(SUBSCRIPTION_FORMAT, getConsumerNumber())
                        + "(UNKNOWN)"
                        + "] ";
        }
        else
        {
            String queueString = new QueueLogSubject(_queue).toLogString();
            logString = "[" + MessageFormat.format(SUBSCRIPTION_FORMAT, getConsumerNumber())
                                     + "("
                                     // queueString is [vh(/{0})/qu({1}) ] so need to trim
                                     //                ^                ^^
                                     + queueString.substring(1,queueString.length() - 3)
                                     + ")"
                                     + "] ";

        }

        return logString;
    }

    private EventLogger getEventLogger()
    {
        return _queue.getEventLogger();
    }
}
