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
package org.apache.qpid.server.protocol.v0_8;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.filter.FilterManagerFactory;
import org.apache.qpid.server.filter.Filterable;
import org.apache.qpid.server.flow.FlowCreditManager;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.SubscriptionActor;
import org.apache.qpid.server.logging.messages.SubscriptionMessages;
import org.apache.qpid.server.logging.subjects.SubscriptionLogSubject;
import org.apache.qpid.server.protocol.MessageConverterRegistry;
import org.apache.qpid.server.protocol.v0_8.output.ProtocolOutputConverter;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.subscription.ClientDeliveryMethod;
import org.apache.qpid.server.subscription.RecordDeliveryMethod;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Encapsulation of a supscription to a queue. <p/> Ties together the protocol session of a subscriber, the consumer tag
 * that was given out by the broker and the channel id. <p/>
 */
public abstract class SubscriptionImpl implements Subscription, FlowCreditManager.FlowCreditManagerListener
{

    private StateListener _stateListener = new StateListener()
                                            {

                                                public void stateChange(Subscription sub, State oldState, State newState)
                                                {

                                                }
                                            };


    private final AtomicReference<State> _state = new AtomicReference<State>(State.ACTIVE);
    private volatile AMQQueue.Context _queueContext;

    private final ClientDeliveryMethod _deliveryMethod;
    private final RecordDeliveryMethod _recordMethod;

    private final QueueEntry.SubscriptionAcquiredState _owningState = new QueueEntry.SubscriptionAcquiredState(this);

    private final Map<String, Object> _properties = new ConcurrentHashMap<String, Object>();

    private final Lock _stateChangeLock;

    private final long _subscriptionID;
    private LogSubject _logSubject;
    private LogActor _logActor;
    private final AtomicLong _deliveredCount = new AtomicLong(0);
    private final AtomicLong _deliveredBytes = new AtomicLong(0);

    private final AtomicLong _unacknowledgedCount = new AtomicLong(0);
    private final AtomicLong _unacknowledgedBytes = new AtomicLong(0);

    private long _createTime = System.currentTimeMillis();


    static final class BrowserSubscription extends SubscriptionImpl
    {
        public BrowserSubscription(AMQChannel channel, AMQProtocolSession protocolSession,
                                   AMQShortString consumerTag, FieldTable filters,
                                   boolean noLocal, FlowCreditManager creditManager,
                                   ClientDeliveryMethod deliveryMethod,
                                   RecordDeliveryMethod recordMethod)
            throws AMQException
        {
            super(channel, protocolSession, consumerTag, filters, noLocal, creditManager, deliveryMethod, recordMethod);
        }


        public boolean isBrowser()
        {
            return true;
        }

        /**
         * This method can be called by each of the publisher threads. As a result all changes to the channel object must be
         * thread safe.
         *
         *
         * @param entry
         * @param batch
         * @throws AMQException
         */
        @Override
        public void send(QueueEntry entry, boolean batch) throws AMQException
        {
            // We don't decrement the reference here as we don't want to consume the message
            // but we do want to send it to the client.

            synchronized (getChannel())
            {
                long deliveryTag = getChannel().getNextDeliveryTag();
                sendToClient(entry, deliveryTag);
            }

        }

        @Override
        public boolean wouldSuspend(QueueEntry msg)
        {
            return false;
        }

    }

    public static class NoAckSubscription extends SubscriptionImpl
    {
        private volatile AutoCommitTransaction _txn;

        public NoAckSubscription(AMQChannel channel, AMQProtocolSession protocolSession,
                                 AMQShortString consumerTag, FieldTable filters,
                                 boolean noLocal, FlowCreditManager creditManager,
                                 ClientDeliveryMethod deliveryMethod,
                                 RecordDeliveryMethod recordMethod)
            throws AMQException
        {
            super(channel, protocolSession, consumerTag, filters, noLocal, creditManager, deliveryMethod, recordMethod);
        }


        public boolean isBrowser()
        {
            return false;
        }

        @Override
        public boolean isExplicitAcknowledge()
        {
            return false;
        }

        /**
         * This method can be called by each of the publisher threads. As a result all changes to the channel object must be
         * thread safe.
         *
         *
         * @param entry   The message to send
         * @param batch
         * @throws AMQException
         */
        @Override
        public void send(QueueEntry entry, boolean batch) throws AMQException
        {
            // if we do not need to wait for client acknowledgements
            // we can decrement the reference count immediately.

            // By doing this _before_ the send we ensure that it
            // doesn't get sent if it can't be dequeued, preventing
            // duplicate delivery on recovery.

            // The send may of course still fail, in which case, as
            // the message is unacked, it will be lost.
            if(_txn == null)
            {
                _txn = new AutoCommitTransaction(getQueue().getVirtualHost().getMessageStore());
            }
            _txn.dequeue(getQueue(), entry.getMessage(), NOOP);

            entry.dequeue();

            synchronized (getChannel())
            {
                getChannel().getProtocolSession().setDeferFlush(batch);
                long deliveryTag = getChannel().getNextDeliveryTag();

                sendToClient(entry, deliveryTag);

            }
            entry.dispose();


        }

        @Override
        public boolean wouldSuspend(QueueEntry msg)
        {
            return false;
        }

        private static final ServerTransaction.Action NOOP =
                new ServerTransaction.Action()
                {
                    @Override
                    public void postCommit()
                    {
                    }

                    @Override
                    public void onRollback()
                    {
                    }
                };
    }

    /**
     * NoAck Subscription for use with BasicGet method.
     */
    public static final class GetNoAckSubscription extends SubscriptionImpl.NoAckSubscription
    {
        public GetNoAckSubscription(AMQChannel channel, AMQProtocolSession protocolSession,
                                    AMQShortString consumerTag, FieldTable filters,
                                    boolean noLocal, FlowCreditManager creditManager,
                                    ClientDeliveryMethod deliveryMethod,
                                    RecordDeliveryMethod recordMethod)
            throws AMQException
        {
            super(channel, protocolSession, consumerTag, filters, noLocal, creditManager, deliveryMethod, recordMethod);
        }

        public boolean isTransient()
        {
            return true;
        }

        public boolean wouldSuspend(QueueEntry msg)
        {
            return !getCreditManager().useCreditForMessage(msg.getMessage().getSize());
        }

    }

    static final class AckSubscription extends SubscriptionImpl
    {
        public AckSubscription(AMQChannel channel, AMQProtocolSession protocolSession,
                               AMQShortString consumerTag, FieldTable filters,
                               boolean noLocal, FlowCreditManager creditManager,
                               ClientDeliveryMethod deliveryMethod,
                               RecordDeliveryMethod recordMethod)
            throws AMQException
        {
            super(channel, protocolSession, consumerTag, filters, noLocal, creditManager, deliveryMethod, recordMethod);
        }


        public boolean isBrowser()
        {
            return false;
        }


        /**
         * This method can be called by each of the publisher threads. As a result all changes to the channel object must be
         * thread safe.
         *
         *
         * @param entry   The message to send
         * @param batch
         * @throws AMQException
         */
        @Override
        public void send(QueueEntry entry, boolean batch) throws AMQException
        {


            synchronized (getChannel())
            {
                getChannel().getProtocolSession().setDeferFlush(batch);
                long deliveryTag = getChannel().getNextDeliveryTag();

                addUnacknowledgedMessage(entry);
                recordMessageDelivery(entry, deliveryTag);
                sendToClient(entry, deliveryTag);


            }
        }



    }


    private static final Logger _logger = Logger.getLogger(SubscriptionImpl.class);

    private final AMQChannel _channel;

    private final AMQShortString _consumerTag;


    private boolean _noLocal;

    private final FlowCreditManager _creditManager;

    private FilterManager _filters;

    private final Boolean _autoClose;

    private AMQQueue _queue;
    private final AtomicBoolean _deleted = new AtomicBoolean(false);




    public SubscriptionImpl(AMQChannel channel, AMQProtocolSession protocolSession,
                            AMQShortString consumerTag, FieldTable arguments,
                            boolean noLocal, FlowCreditManager creditManager,
                            ClientDeliveryMethod deliveryMethod,
                            RecordDeliveryMethod recordMethod)
            throws AMQException
    {
        _subscriptionID = SUB_ID_GENERATOR.getAndIncrement();
        _channel = channel;
        _consumerTag = consumerTag;

        _creditManager = creditManager;
        creditManager.addStateListener(this);

        _noLocal = noLocal;


        _filters = FilterManagerFactory.createManager(FieldTable.convertToMap(arguments));

        _deliveryMethod = deliveryMethod;
        _recordMethod = recordMethod;


        _stateChangeLock = new ReentrantLock();


        if (arguments != null)
        {
            Object autoClose = arguments.get(AMQPFilterTypes.AUTO_CLOSE.getValue());
            if (autoClose != null)
            {
                _autoClose = (Boolean) autoClose;
            }
            else
            {
                _autoClose = false;
            }
        }
        else
        {
            _autoClose = false;
        }

    }

    public AMQSessionModel getSessionModel()
    {
        return _channel;
    }

    public Long getDelivered()
    {
        return _deliveredCount.get();
    }

    public synchronized void setQueue(AMQQueue queue, boolean exclusive)
    {
        if(getQueue() != null)
        {
            throw new IllegalStateException("Attempt to set queue for subscription " + this + " to " + queue + "when already set to " + getQueue());
        }
        _queue = queue;

        _logSubject = new SubscriptionLogSubject(this);
        _logActor = new SubscriptionActor(CurrentActor.get().getRootMessageLogger(), this);

        if (CurrentActor.get().getRootMessageLogger().
                isMessageEnabled(CurrentActor.get(), _logSubject, SubscriptionMessages.CREATE_LOG_HIERARCHY))
        {
            // Get the string value of the filters
            String filterLogString = null;
            if (_filters != null && _filters.hasFilters())
            {
                filterLogString = _filters.toString();
            }

            if (isAutoClose())
            {
                if (filterLogString == null)
                {
                    filterLogString = "";
                }
                else
                {
                    filterLogString += ",";
                }
                filterLogString += "AutoClose";
            }

            if (isBrowser())
            {
                // We do not need to check for null here as all Browsers are AutoClose
                filterLogString +=",Browser";
            }

            CurrentActor.get().
                    message(_logSubject,
                            SubscriptionMessages.CREATE(filterLogString,
                                                          queue.isDurable() && exclusive,
                                                          filterLogString != null));
        }
    }

    public String toString()
    {
        String subscriber = "[channel=" + _channel +
                            ", consumerTag=" + _consumerTag +
                            ", session=" + getProtocolSession().getKey()  ;

        return subscriber + "]";
    }

    /**
     * This method can be called by each of the publisher threads. As a result all changes to the channel object must be
     * thread safe.
     *
     *
     * @param entry
     * @param batch
     * @throws AMQException
     */
    abstract public void send(QueueEntry entry, boolean batch) throws AMQException;


    public boolean isSuspended()
    {
        return !isActive() || _channel.isSuspended() || _deleted.get() || _channel.getConnectionModel().isStopped();
    }

    /**
     * Callback indicating that a queue has been deleted.
     *
     * @param queue The queue to delete
     */
    public void queueDeleted(AMQQueue queue)
    {
        _deleted.set(true);
    }

    public boolean hasInterest(QueueEntry entry)
    {
        //check that the message hasn't been rejected
        if (entry.isRejectedBy(getSubscriptionID()))
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Subscription:" + this + " rejected message:" + entry);
            }
        }

        if(entry.getMessage() instanceof AMQMessage)
        {
            if (_noLocal)
            {
                AMQMessage message = (AMQMessage) entry.getMessage();

                final Object publisherReference = message.getConnectionReference();

                // We don't want local messages so check to see if message is one we sent
                Object localReference = getProtocolSession().getReference();

                if(publisherReference != null && publisherReference.equals(localReference))
                {
                    return false;
                }
            }
        }
        else
        {
            // No interest in messages we can't convert to AMQMessage
            if(MessageConverterRegistry.getConverter(entry.getMessage().getClass(), AMQMessage.class)==null)
            {
                return false;
            }
        }


        if (_logger.isDebugEnabled())
        {
            _logger.debug("(" + this + ") checking filters for message (" + entry);
        }
        return checkFilters(entry);

    }

    private boolean checkFilters(QueueEntry msg)
    {
        return (_filters == null) || _filters.allAllow(msg.asFilterable());
    }

    public boolean isAutoClose()
    {
        return _autoClose;
    }

    public FlowCreditManager getCreditManager()
    {
        return _creditManager;
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
        }
        finally
        {
            _stateChangeLock.unlock();
        }
        //Log Subscription closed
        CurrentActor.get().message(_logSubject, SubscriptionMessages.CLOSE());
    }

    public boolean isClosed()
    {
        return getState() == State.CLOSED;
    }


    public boolean wouldSuspend(QueueEntry msg)
    {
        return !_creditManager.useCreditForMessage(msg.getMessage().getSize());
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

    public AMQChannel getChannel()
    {
        return _channel;
    }

    public AMQShortString getConsumerTag()
    {
        return _consumerTag;
    }

    public String getConsumerName()
    {
        return _consumerTag == null ? null : _consumerTag.asString();
    }

    public long getSubscriptionID()
    {
        return _subscriptionID;
    }

    public AMQProtocolSession getProtocolSession()
    {
        return _channel.getProtocolSession();
    }

    public LogActor getLogActor()
    {
        return _logActor;
    }

    public AMQQueue getQueue()
    {
        return _queue;
    }

    public void onDequeue(final QueueEntry queueEntry)
    {
        restoreCredit(queueEntry);
    }

    public void releaseQueueEntry(final QueueEntry queueEntry)
    {
        restoreCredit(queueEntry);
    }

    public void restoreCredit(final QueueEntry queueEntry)
    {
        _creditManager.restoreCredit(1, queueEntry.getSize());
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
        CurrentActor.get().message(_logSubject,SubscriptionMessages.STATE(_state.get().toString()));
    }

    public State getState()
    {
        return _state.get();
    }


    public void setStateListener(final StateListener listener)
    {
        _stateListener = listener;
    }


    public AMQQueue.Context getQueueContext()
    {
        return _queueContext;
    }

    public void setQueueContext(AMQQueue.Context context)
    {
        _queueContext = context;
    }


    protected void sendToClient(final QueueEntry entry, final long deliveryTag)
            throws AMQException
    {
        _deliveryMethod.deliverToClient(this,entry,deliveryTag);
        _deliveredCount.incrementAndGet();
        _deliveredBytes.addAndGet(entry.getSize());
    }


    protected void recordMessageDelivery(final QueueEntry entry, final long deliveryTag)
    {
        _recordMethod.recordMessageDelivery(this,entry,deliveryTag);
    }


    public boolean isActive()
    {
        return getState() == State.ACTIVE;
    }

    public QueueEntry.SubscriptionAcquiredState getOwningState()
    {
        return _owningState;
    }

    public void confirmAutoClose()
    {
        ProtocolOutputConverter converter = getChannel().getProtocolSession().getProtocolOutputConverter();
        converter.confirmConsumerAutoClose(getChannel().getChannelId(), getConsumerTag());
    }

    public boolean acquires()
    {
        return !isBrowser();
    }

    public boolean seesRequeues()
    {
        return !isBrowser();
    }

    public boolean isTransient()
    {
        return false;
    }

    public void set(String key, Object value)
    {
        _properties.put(key, value);
    }

    public Object get(String key)
    {
        return _properties.get(key);
    }


    public void setNoLocal(boolean noLocal)
    {
        _noLocal = noLocal;
    }

    abstract boolean isBrowser();

    public String getCreditMode()
    {
        return "WINDOW";
    }

    public boolean isBrowsing()
    {
        return isBrowser();
    }

    public boolean isExplicitAcknowledge()
    {
        return true;
    }

    public boolean isDurable()
    {
        return false;
    }

    public boolean isExclusive()
    {
        return getQueue().hasExclusiveSubscriber();
    }

    public String getName()
    {
        return String.valueOf(_consumerTag);
    }

    public Map<String, Object> getArguments()
    {
        return null;
    }

    public boolean isSessionTransactional()
    {
        return _channel.isTransactional();
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    public void queueEmpty() throws AMQException
    {
        if (isAutoClose())
        {
            _queue.unregisterSubscription(this);

            confirmAutoClose();
        }
    }

    public void flushBatched()
    {
        _channel.getProtocolSession().setDeferFlush(false);

        _channel.getProtocolSession().flushBatched();
    }

    public long getBytesOut()
    {
        return _deliveredBytes.longValue();
    }

    public long getMessagesOut()
    {
        return _deliveredCount.longValue();
    }


    protected void addUnacknowledgedMessage(QueueEntry entry)
    {
        final long size = entry.getSize();
        _unacknowledgedBytes.addAndGet(size);
        _unacknowledgedCount.incrementAndGet();
        entry.addStateChangeListener(new QueueEntry.StateChangeListener()
        {
            public void stateChanged(QueueEntry entry, QueueEntry.State oldState, QueueEntry.State newState)
            {
                if(oldState.equals(QueueEntry.State.ACQUIRED) && !newState.equals(QueueEntry.State.ACQUIRED))
                {
                    _unacknowledgedBytes.addAndGet(-size);
                    _unacknowledgedCount.decrementAndGet();
                    entry.removeStateChangeListener(this);
                }
            }
        });
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
