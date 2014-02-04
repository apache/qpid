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
package org.apache.qpid.server.protocol.v0_10;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.flow.FlowCreditManager;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.GenericActor;
import org.apache.qpid.server.logging.messages.ChannelMessages;
import org.apache.qpid.server.logging.messages.SubscriptionMessages;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.protocol.MessageConverterRegistry;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageCreditUnit;
import org.apache.qpid.transport.MessageFlowMode;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.Method;
import org.apache.qpid.transport.Option;
import org.apache.qpid.transport.Struct;

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.QUEUE_FORMAT;
import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.SUBSCRIPTION_FORMAT;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Subscription_0_10 implements Subscription, FlowCreditManager.FlowCreditManagerListener, LogSubject
{
    private final long _subscriptionID;

    private final QueueEntry.SubscriptionAcquiredState _owningState = new QueueEntry.SubscriptionAcquiredState(this);

    private static final Option[] BATCHED = new Option[] { Option.BATCH };

    private final Lock _stateChangeLock = new ReentrantLock();

    private final AtomicReference<State> _state = new AtomicReference<State>(State.ACTIVE);
    private volatile AMQQueue.Context _queueContext;
    private final AtomicBoolean _deleted = new AtomicBoolean(false);


    private FlowCreditManager_0_10 _creditManager;

    private StateListener _stateListener = new StateListener()
                                            {

                                                public void stateChange(Subscription sub, State oldState, State newState)
                                                {
                                                    CurrentActor.get().message(SubscriptionMessages.STATE(newState.toString()));
                                                }
                                            };
    private AMQQueue _queue;
    private final String _destination;
    private boolean _noLocal;
    private final FilterManager _filters;
    private final MessageAcceptMode _acceptMode;
    private final MessageAcquireMode _acquireMode;
    private MessageFlowMode _flowMode;
    private final ServerSession _session;
    private final AtomicBoolean _stopped = new AtomicBoolean(true);
    private static final Struct[] EMPTY_STRUCT_ARRAY = new Struct[0];

    private LogActor _logActor;
    private final Map<String, Object> _properties = new ConcurrentHashMap<String, Object>();
    private String _traceExclude;
    private String _trace;
    private final long _createTime = System.currentTimeMillis();
    private final AtomicLong _deliveredCount = new AtomicLong(0);
    private final AtomicLong _deliveredBytes = new AtomicLong(0);
    private final AtomicLong _unacknowledgedCount = new AtomicLong(0);
    private final AtomicLong _unacknowledgedBytes = new AtomicLong(0);

    private final Map<String, Object> _arguments;
    private int _deferredMessageCredit;
    private long _deferredSizeCredit;


    public Subscription_0_10(ServerSession session, String destination, MessageAcceptMode acceptMode,
                             MessageAcquireMode acquireMode,
                             MessageFlowMode flowMode,
                             FlowCreditManager_0_10 creditManager,
                             FilterManager filters,Map<String, Object> arguments)
    {
        _subscriptionID = SUB_ID_GENERATOR.getAndIncrement();
        _session = session;
        _postIdSettingAction = new AddMessageDispositionListenerAction(session);
        _destination = destination;
        _acceptMode = acceptMode;
        _acquireMode = acquireMode;
        _creditManager = creditManager;
        _flowMode = flowMode;
        _filters = filters;
        _creditManager.addStateListener(this);
        _arguments = arguments == null ? Collections.<String, Object> emptyMap() :
                                         Collections.<String, Object> unmodifiableMap(arguments);
        _state.set(_creditManager.hasCredit() ? State.ACTIVE : State.SUSPENDED);

    }

    public void setNoLocal(boolean noLocal)
    {
        _noLocal = noLocal;
    }

    public AMQQueue getQueue()
    {
        return _queue;
    }

    public QueueEntry.SubscriptionAcquiredState getOwningState()
    {
        return _owningState;
    }

    public void setQueue(AMQQueue queue, boolean exclusive)
    {
        if(getQueue() != null)
        {
            throw new IllegalStateException("Attempt to set queue for subscription " + this + " to " + queue + "when already set to " + getQueue());
        }
        _queue = queue;

        _traceExclude = (String) queue.getAttribute(Queue.FEDERATION_EXCLUDES);
        _trace = (String) queue.getAttribute(Queue.FEDERATION_ID);
        String filterLogString = null;

        _logActor = GenericActor.getInstance(this);
        if (CurrentActor.get().getRootMessageLogger().isMessageEnabled(_logActor, this, SubscriptionMessages.CREATE_LOG_HIERARCHY))
        {
            filterLogString = getFilterLogString();
            CurrentActor.get().message(this, SubscriptionMessages.CREATE(filterLogString, queue.isDurable() && exclusive,
                    filterLogString.length() > 0));
        }
    }

    public String getConsumerName()
    {
        return _destination;
    }

    public boolean isSuspended()
    {
        return !isActive() || _deleted.get() || _session.isClosing() || _session.getConnectionModel().isStopped(); // TODO check for Session suspension
    }

    public boolean hasInterest(QueueEntry entry)
    {



        //check that the message hasn't been rejected
        if (entry.isRejectedBy(getSubscriptionID()))
        {

            return false;
        }

        if (entry.getMessage() instanceof MessageTransferMessage)
        {
            if(_noLocal)
            {
                Object connectionRef = ((MessageTransferMessage)entry.getMessage()).getConnectionReference();
                if (connectionRef != null && connectionRef == _session.getReference())
                {
                    return false;
                }
            }
        }
        else
        {
            // no interest in messages we can't convert
            if(MessageConverterRegistry.getConverter(entry.getMessage().getClass(), MessageTransferMessage.class)==null)
            {
                return false;
            }
        }


        return checkFilters(entry);


    }

    private boolean checkFilters(QueueEntry entry)
    {
        return (_filters == null) || _filters.allAllow(entry.asFilterable());
    }

    public boolean isClosed()
    {
        return getState() == State.CLOSED;
    }

    public boolean isBrowser()
    {
        return _acquireMode == MessageAcquireMode.NOT_ACQUIRED;
    }

    public boolean seesRequeues()
    {
        return _acquireMode != MessageAcquireMode.NOT_ACQUIRED || _acceptMode == MessageAcceptMode.EXPLICIT;
    }

    public void close()
    {
        boolean closed = false;
        State state = getState();

        _stateChangeLock.lock();
        try
        {
            while(!closed && state != State.CLOSED)
            {
                closed = _state.compareAndSet(state, State.CLOSED);
                if(!closed)
                {
                    state = getState();
                }
                else
                {
                    _stateListener.stateChange(this,state, State.CLOSED);
                }
            }
            _creditManager.removeListener(this);
            CurrentActor.get().message(getLogSubject(), SubscriptionMessages.CLOSE());
        }
        finally
        {
            _stateChangeLock.unlock();
        }



    }

    public Long getDelivered()
    {
        return _deliveredCount.get();
    }

    public void creditStateChanged(boolean hasCredit)
    {

        if(hasCredit)
        {
            if(_state.compareAndSet(State.SUSPENDED, State.ACTIVE))
            {
                _stateListener.stateChange(this, State.SUSPENDED, State.ACTIVE);
            }
            else
            {
                // this is a hack to get round the issue of increasing bytes credit
                _stateListener.stateChange(this, State.ACTIVE, State.ACTIVE);
            }
        }
        else
        {
            if(_state.compareAndSet(State.ACTIVE, State.SUSPENDED))
            {
                _stateListener.stateChange(this, State.ACTIVE, State.SUSPENDED);
            }
        }
    }


    public static class AddMessageDispositionListenerAction implements Runnable
    {
        private MessageTransfer _xfr;
        private ServerSession.MessageDispositionChangeListener _action;
        private ServerSession _session;

        public AddMessageDispositionListenerAction(ServerSession session)
        {
            _session = session;
        }

        public void setXfr(MessageTransfer xfr)
        {
            _xfr = xfr;
        }

        public void setAction(ServerSession.MessageDispositionChangeListener action)
        {
            _action = action;
        }

        public void run()
        {
            if(_action != null)
            {
                _session.onMessageDispositionChange(_xfr, _action);
            }
        }
    }

    private final AddMessageDispositionListenerAction _postIdSettingAction;

    public void send(final QueueEntry entry, boolean batch) throws AMQException
    {
        ServerMessage serverMsg = entry.getMessage();


        MessageTransfer xfr;

        DeliveryProperties deliveryProps;
        MessageProperties messageProps = null;

        MessageTransferMessage msg;

        if(serverMsg instanceof MessageTransferMessage)
        {

            msg = (MessageTransferMessage) serverMsg;

        }
        else
        {
            MessageConverter converter =
                    MessageConverterRegistry.getConverter(serverMsg.getClass(), MessageTransferMessage.class);


            msg = (MessageTransferMessage) converter.convert(serverMsg, getQueue().getVirtualHost());
        }
        DeliveryProperties origDeliveryProps = msg.getHeader() == null ? null : msg.getHeader().getDeliveryProperties();
        messageProps = msg.getHeader() == null ? null : msg.getHeader().getMessageProperties();

        deliveryProps = new DeliveryProperties();
        if(origDeliveryProps != null)
        {
            if(origDeliveryProps.hasDeliveryMode())
            {
                deliveryProps.setDeliveryMode(origDeliveryProps.getDeliveryMode());
            }
            if(origDeliveryProps.hasExchange())
            {
                deliveryProps.setExchange(origDeliveryProps.getExchange());
            }
            if(origDeliveryProps.hasExpiration())
            {
                deliveryProps.setExpiration(origDeliveryProps.getExpiration());
            }
            if(origDeliveryProps.hasPriority())
            {
                deliveryProps.setPriority(origDeliveryProps.getPriority());
            }
            if(origDeliveryProps.hasRoutingKey())
            {
                deliveryProps.setRoutingKey(origDeliveryProps.getRoutingKey());
            }
            if(origDeliveryProps.hasTimestamp())
            {
                deliveryProps.setTimestamp(origDeliveryProps.getTimestamp());
            }
            if(origDeliveryProps.hasTtl())
            {
                deliveryProps.setTtl(origDeliveryProps.getTtl());
            }


        }

        deliveryProps.setRedelivered(entry.isRedelivered());

        if(_trace != null && messageProps == null)
        {
            messageProps = new MessageProperties();
        }

        Header header = new Header(deliveryProps, messageProps, msg.getHeader() == null ? null : msg.getHeader().getNonStandardProperties());


        xfr = batch ? new MessageTransfer(_destination,_acceptMode,_acquireMode,header,msg.getBody(), BATCHED)
                    : new MessageTransfer(_destination,_acceptMode,_acquireMode,header,msg.getBody());

        boolean excludeDueToFederation = false;

        if(_trace != null)
        {
            if(!messageProps.hasApplicationHeaders())
            {
                messageProps.setApplicationHeaders(new HashMap<String,Object>());
            }
            Map<String,Object> appHeaders = messageProps.getApplicationHeaders();
            String trace = (String) appHeaders.get("x-qpid.trace");
            if(trace == null)
            {
                trace = _trace;
            }
            else
            {
                if(_traceExclude != null)
                {
                    excludeDueToFederation = Arrays.asList(trace.split(",")).contains(_traceExclude);
                }
                trace+=","+_trace;
            }
            appHeaders.put("x-qpid.trace",trace);
        }

        if(!excludeDueToFederation)
        {
            if(_acceptMode == MessageAcceptMode.NONE && _acquireMode != MessageAcquireMode.PRE_ACQUIRED)
            {
                xfr.setCompletionListener(new MessageAcceptCompletionListener(this, _session, entry, _flowMode == MessageFlowMode.WINDOW));
            }
            else if(_flowMode == MessageFlowMode.WINDOW)
            {
                xfr.setCompletionListener(new Method.CompletionListener()
                                            {
                                                public void onComplete(Method method)
                                                {
                                                    deferredAddCredit(1, entry.getSize());
                                                }
                                            });
            }


            _postIdSettingAction.setXfr(xfr);
            if(_acceptMode == MessageAcceptMode.EXPLICIT)
            {
                _postIdSettingAction.setAction(new ExplicitAcceptDispositionChangeListener(entry, this));
            }
            else if(_acquireMode != MessageAcquireMode.PRE_ACQUIRED)
            {
                _postIdSettingAction.setAction(new ImplicitAcceptDispositionChangeListener(entry, this));
            }
            else
            {
                _postIdSettingAction.setAction(null);
            }


            _session.sendMessage(xfr, _postIdSettingAction);
            entry.incrementDeliveryCount();
            _deliveredCount.incrementAndGet();
            _deliveredBytes.addAndGet(entry.getSize());
            if(_acceptMode == MessageAcceptMode.NONE && _acquireMode == MessageAcquireMode.PRE_ACQUIRED)
            {
                forceDequeue(entry, false);
            }
            else if(_acquireMode == MessageAcquireMode.PRE_ACQUIRED)
            {
                recordUnacknowledged(entry);
            }
        }
        else
        {
            forceDequeue(entry, _flowMode == MessageFlowMode.WINDOW);

        }
    }

    void recordUnacknowledged(QueueEntry entry)
    {
        _unacknowledgedCount.incrementAndGet();
        _unacknowledgedBytes.addAndGet(entry.getSize());
    }

    private void deferredAddCredit(final int deferredMessageCredit, final long deferredSizeCredit)
    {
        _deferredMessageCredit += deferredMessageCredit;
        _deferredSizeCredit += deferredSizeCredit;

    }

    public void flushCreditState(boolean strict)
    {
        if(strict || !isSuspended() || _deferredMessageCredit >= 200
          || !(_creditManager instanceof WindowCreditManager)
          || ((WindowCreditManager)_creditManager).getMessageCreditLimit() < 400 )
        {
            _creditManager.restoreCredit(_deferredMessageCredit, _deferredSizeCredit);
            _deferredMessageCredit = 0;
            _deferredSizeCredit = 0l;
        }
    }

    private void forceDequeue(final QueueEntry entry, final boolean restoreCredit)
    {
        AutoCommitTransaction dequeueTxn = new AutoCommitTransaction(getQueue().getVirtualHost().getMessageStore());
        dequeueTxn.dequeue(entry.getQueue(), entry.getMessage(),
                           new ServerTransaction.Action()
                           {
                               public void postCommit()
                               {
                                   if (restoreCredit)
                                   {
                                       restoreCredit(entry);
                                   }
                                   entry.delete();
                               }

                               public void onRollback()
                               {

                               }
                           });
   }

    void reject(final QueueEntry entry)
    {
        entry.setRedelivered();
        entry.routeToAlternate(null, null);
        if(entry.isAcquiredBy(this))
        {
            entry.delete();
        }
    }

    void release(final QueueEntry entry, final boolean setRedelivered)
    {
        if (setRedelivered)
        {
            entry.setRedelivered();
        }

        if (getSessionModel().isClosing() || !setRedelivered)
        {
            entry.decrementDeliveryCount();
        }

        if (isMaxDeliveryLimitReached(entry))
        {
            sendToDLQOrDiscard(entry);
        }
        else
        {
            entry.release();
        }
    }

    protected void sendToDLQOrDiscard(QueueEntry entry)
    {
        final LogActor logActor = CurrentActor.get();
        final ServerMessage msg = entry.getMessage();

        int requeues = entry.routeToAlternate(new BaseQueue.PostEnqueueAction()
                    {
                        @Override
                        public void onEnqueue(final QueueEntry requeueEntry)
                        {
                            logActor.message( ChannelMessages.DEADLETTERMSG(msg.getMessageNumber(),
                                                                            requeueEntry.getQueue().getName()));
                        }
                    }, null);

        if (requeues == 0)
        {
            final AMQQueue queue = entry.getQueue();
            final Exchange alternateExchange = queue.getAlternateExchange();

            if(alternateExchange != null)
            {
                logActor.message( ChannelMessages.DISCARDMSG_NOROUTE(msg.getMessageNumber(),
                                                                     alternateExchange.getName()));
            }
            else
            {
                logActor.message(ChannelMessages.DISCARDMSG_NOALTEXCH(msg.getMessageNumber(),
                                                                      queue.getName(),
                                                                      msg.getRoutingKey()));
            }
        }
    }

    private boolean isMaxDeliveryLimitReached(QueueEntry entry)
    {
        final int maxDeliveryLimit = entry.getQueue().getMaximumDeliveryCount();
        return (maxDeliveryLimit > 0 && entry.getDeliveryCount() >= maxDeliveryLimit);
    }

    public void queueDeleted(AMQQueue queue)
    {
        _deleted.set(true);
    }

    public boolean wouldSuspend(QueueEntry entry)
    {
        return !_creditManager.useCreditForMessage(entry.getMessage().getSize());
    }

    public boolean trySendLock()
    {
        return _stateChangeLock.tryLock();
    }


    public void getSendLock()
    {
        _stateChangeLock.lock();
    }

    public void releaseSendLock()
    {
        _stateChangeLock.unlock();
    }

    public void restoreCredit(QueueEntry queueEntry)
    {
        _creditManager.restoreCredit(1, queueEntry.getSize());
    }

    public void onDequeue(QueueEntry queueEntry)
    {
        // no-op for 0-10, credit restored by completing command.
    }

    public void releaseQueueEntry(QueueEntry queueEntry)
    {
        // no-op for 0-10, credit restored by completing command.
    }

    public void setStateListener(StateListener listener)
    {
        _stateListener = listener;
    }

    public State getState()
    {
        return _state.get();
    }

    public AMQQueue.Context getQueueContext()
    {
        return _queueContext;
    }

    public void setQueueContext(AMQQueue.Context queueContext)
    {
        _queueContext = queueContext;
    }

    public boolean isActive()
    {
        return getState() == State.ACTIVE;
    }

    public void set(String key, Object value)
    {
        _properties.put(key, value);
    }

    public Object get(String key)
    {
        return _properties.get(key);
    }


    public FlowCreditManager_0_10 getCreditManager()
    {
        return _creditManager;
    }


    public void stop()
    {
        try
        {
            getSendLock();

            if(_state.compareAndSet(State.ACTIVE, State.SUSPENDED))
            {
                _stateListener.stateChange(this, State.ACTIVE, State.SUSPENDED);
            }
            _stopped.set(true);
            FlowCreditManager_0_10 creditManager = getCreditManager();
            creditManager.clearCredit();
        }
        finally
        {
            releaseSendLock();
        }
    }

    public void addCredit(MessageCreditUnit unit, long value)
    {
        FlowCreditManager_0_10 creditManager = getCreditManager();

        switch (unit)
        {
            case MESSAGE:

                creditManager.addCredit(value, 0L);
                break;
            case BYTE:
                creditManager.addCredit(0l, value);
                break;
        }

        _stopped.set(false);

        if(creditManager.hasCredit())
        {
            if(_state.compareAndSet(State.SUSPENDED, State.ACTIVE))
            {
                _stateListener.stateChange(this, State.SUSPENDED, State.ACTIVE);
            }
        }

    }

    public void setFlowMode(MessageFlowMode flowMode)
    {


        _creditManager.removeListener(this);

        switch(flowMode)
        {
            case CREDIT:
                _creditManager = new CreditCreditManager(0l,0l);
                break;
            case WINDOW:
                _creditManager = new WindowCreditManager(0l,0l);
                break;
            default:
                throw new RuntimeException("Unknown message flow mode: " + flowMode);
        }
        _flowMode = flowMode;
        if(_state.compareAndSet(State.ACTIVE, State.SUSPENDED))
        {
            _stateListener.stateChange(this, State.ACTIVE, State.SUSPENDED);
        }

        _creditManager.addStateListener(this);

    }

    public boolean isStopped()
    {
        return _stopped.get();
    }

    public boolean acquires()
    {
        return _acquireMode == MessageAcquireMode.PRE_ACQUIRED;
    }

    public void acknowledge(QueueEntry entry)
    {
        // TODO Fix Store Context / cleanup
        if(entry.isAcquiredBy(this))
        {
            _unacknowledgedBytes.addAndGet(-entry.getSize());
            _unacknowledgedCount.decrementAndGet();
            entry.delete();
        }
    }

    public void flush() throws AMQException
    {
        flushCreditState(true);
        _queue.flushSubscription(this);
        stop();
    }

    public long getSubscriptionID()
    {
        return _subscriptionID;
    }

    public LogActor getLogActor()
    {
        return _logActor;
    }

    public boolean isTransient()
    {
        return false;
    }

    public ServerSession getSessionModel()
    {
        return _session;
    }

    public boolean isBrowsing()
    {
        return _acquireMode == MessageAcquireMode.NOT_ACQUIRED;
    }

    public boolean isExclusive()
    {
        return getQueue().hasExclusiveSubscriber();
    }

    public boolean isDurable()
    {
        return false;
    }


    public boolean isExplicitAcknowledge()
    {
        return _acceptMode == MessageAcceptMode.EXPLICIT;
    }

    public String getCreditMode()
    {
        return _flowMode.toString();
    }

    public String getName()
    {
        return _destination;
    }

    public Map<String, Object> getArguments()
    {
        return _arguments;
    }

    public boolean isSessionTransactional()
    {
        return _session.isTransactional();
    }

    public void queueEmpty()
    {
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    public String toLogString()
    {
        String queueInfo = MessageFormat.format(QUEUE_FORMAT, _queue.getVirtualHost().getName(),
                  _queue.getName());
        String result = "[" + MessageFormat.format(SUBSCRIPTION_FORMAT, getSubscriptionID()) + "("
                // queueString is "vh(/{0})/qu({1}) " so need to trim
                + queueInfo.substring(0, queueInfo.length() - 1) + ")" + "] ";
        return result;
    }

    private String getFilterLogString()
    {
        StringBuilder filterLogString = new StringBuilder();
        String delimiter = ", ";
        boolean hasEntries = false;
        if (_filters != null && _filters.hasFilters())
        {
            filterLogString.append(_filters.toString());
            hasEntries = true;
        }

        if (isBrowser())
        {
            if (hasEntries)
            {
                filterLogString.append(delimiter);
            }
            filterLogString.append("Browser");
            hasEntries = true;
        }

        if (isDurable())
        {
            if (hasEntries)
            {
                filterLogString.append(delimiter);
            }
            filterLogString.append("Durable");
            hasEntries = true;
        }

        return filterLogString.toString();
    }

    public LogSubject getLogSubject()
    {
        return (LogSubject) this;
    }


    public void flushBatched()
    {
        _session.getConnection().flush();
    }

    public long getBytesOut()
    {
        return _deliveredBytes.longValue();
    }

    public long getMessagesOut()
    {
        return _deliveredCount.longValue();
    }

    public long getUnacknowledgedBytes()
    {
        return _unacknowledgedBytes.longValue();
    }

    public long getUnacknowledgedMessages()
    {
        return _unacknowledgedCount.longValue();
    }
}
