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
package org.apache.qpid.server.protocol.v1_0;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.qpid.AMQException;
import org.apache.qpid.amqp_1_0.codec.ValueHandler;
import org.apache.qpid.amqp_1_0.messaging.SectionEncoder;
import org.apache.qpid.amqp_1_0.messaging.SectionEncoderImpl;
import org.apache.qpid.amqp_1_0.transport.SendingLinkEndpoint;
import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.DeliveryState;
import org.apache.qpid.amqp_1_0.type.Outcome;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.codec.AMQPDescribedTypeRegistry;
import org.apache.qpid.amqp_1_0.type.messaging.Accepted;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.Modified;
import org.apache.qpid.amqp_1_0.type.messaging.Released;
import org.apache.qpid.amqp_1_0.type.messaging.Source;
import org.apache.qpid.amqp_1_0.type.messaging.StdDistMode;
import org.apache.qpid.amqp_1_0.type.transaction.TransactionalState;
import org.apache.qpid.amqp_1_0.type.transport.SenderSettleMode;
import org.apache.qpid.amqp_1_0.type.transport.Transfer;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.txn.ServerTransaction;

class Subscription_1_0 implements Subscription
{
    private SendingLink_1_0 _link;

    private AMQQueue _queue;

    private final AtomicReference<State> _state = new AtomicReference<State>(State.SUSPENDED);

    private final QueueEntry.SubscriptionAcquiredState _owningState = new QueueEntry.SubscriptionAcquiredState(this);
    private final QueueEntry.SubscriptionAssignedState _assignedState = new QueueEntry.SubscriptionAssignedState(this);
    private final long _id;
    private final boolean _acquires;
    private AMQQueue.Context _queueContext;
    private Map<String, Object> _properties = new ConcurrentHashMap<String, Object>();
    private ReentrantLock _stateChangeLock = new ReentrantLock();

    private boolean _noLocal;
    private FilterManager _filters;

    private long _deliveryTag = 0L;
    private StateListener _stateListener;

    private Binary _transactionId;
    private final AMQPDescribedTypeRegistry _typeRegistry = AMQPDescribedTypeRegistry.newInstance()
                                                                                     .registerTransportLayer()
                                                                                     .registerMessagingLayer()
                                                                                     .registerTransactionLayer()
                                                                                     .registerSecurityLayer();
    private SectionEncoder _sectionEncoder = new SectionEncoderImpl(_typeRegistry);

    public Subscription_1_0(final SendingLink_1_0 link, final QueueDestination destination)
    {
        this(link, destination, ((Source)link.getEndpoint().getSource()).getDistributionMode() != StdDistMode.COPY);
    }

    public Subscription_1_0(final SendingLink_1_0 link, final QueueDestination destination, boolean acquires)
    {
        _link = link;
        _queue = destination.getQueue();
        _id = getEndpoint().getLocalHandle().longValue();
        _acquires = acquires;
    }

    private SendingLinkEndpoint getEndpoint()
    {
        return _link.getEndpoint();
    }

    public LogActor getLogActor()
    {
        return null;  //TODO
    }

    public boolean isTransient()
    {
        return true;  //TODO
    }

    public AMQQueue getQueue()
    {
        return _queue;
    }

    public QueueEntry.SubscriptionAcquiredState getOwningState()
    {
        return _owningState;
    }

    public QueueEntry.SubscriptionAssignedState getAssignedState()
    {
        return _assignedState;
    }

    public void setQueue(final AMQQueue queue, final boolean exclusive)
    {
        //TODO
    }

    public void setNoLocal(final boolean noLocal)
    {
        _noLocal = noLocal;
    }

    public boolean isNoLocal()
    {
        return _noLocal;
    }

    public long getSubscriptionID()
    {
        return _id;
    }

    public boolean isSuspended()
    {
        return !isActive();// || !getEndpoint().hasCreditToSend();

    }

    public boolean hasInterest(final QueueEntry entry)
    {
        return !(_noLocal && (entry.getMessage() instanceof Message_1_0)
                          && ((Message_1_0)entry.getMessage()).getSession() == getSession())
               && checkFilters(entry);

    }

    private boolean checkFilters(final QueueEntry entry)
    {
        return (_filters == null) || _filters.allAllow(entry);
    }

    public boolean isClosed()
    {
        return !getEndpoint().isAttached();
    }

    public boolean acquires()
    {
        return _acquires;
    }

    public boolean seesRequeues()
    {
        // TODO
        return acquires();
    }

    public void close()
    {
        getEndpoint().detach();
    }

    public void send(QueueEntry entry, boolean batch) throws AMQException
    {
        // TODO
        send(entry);
    }

    public void flushBatched()
    {
        // TODO
    }

    public void send(final QueueEntry queueEntry) throws AMQException
    {
        //TODO
        ServerMessage serverMessage = queueEntry.getMessage();
        if(serverMessage instanceof Message_1_0)
        {
            Message_1_0 message = (Message_1_0) serverMessage;
            Transfer transfer = new Transfer();
            //TODO


            List<ByteBuffer> fragments = message.getFragments();
            ByteBuffer payload;
            if(fragments.size() == 1)
            {
                payload = fragments.get(0);
            }
            else
            {
                int size = 0;
                for(ByteBuffer fragment : fragments)
                {
                    size += fragment.remaining();
                }

                payload = ByteBuffer.allocate(size);

                for(ByteBuffer fragment : fragments)
                {
                    payload.put(fragment.duplicate());
                }

                payload.flip();
            }

            if(queueEntry.getDeliveryCount() != 0)
            {
                payload = payload.duplicate();
                ValueHandler valueHandler = new ValueHandler(_typeRegistry);

                Header oldHeader = null;
                try
                {
                    ByteBuffer encodedBuf = payload.duplicate();
                    Object value = valueHandler.parse(payload);
                    if(value instanceof Header)
                    {
                        oldHeader = (Header) value;
                    }
                    else
                    {
                        payload.position(0);
                    }
                }
                catch (AmqpErrorException e)
                {
                    //TODO
                    throw new RuntimeException(e);
                }

                Header header = new Header();
                if(oldHeader != null)
                {
                    header.setDurable(oldHeader.getDurable());
                    header.setPriority(oldHeader.getPriority());
                    header.setTtl(oldHeader.getTtl());
                }
                header.setDeliveryCount(UnsignedInteger.valueOf(queueEntry.getDeliveryCount()));
                _sectionEncoder.reset();
                _sectionEncoder.encodeObject(header);
                Binary encodedHeader = _sectionEncoder.getEncoding();

                ByteBuffer oldPayload = payload;
                payload = ByteBuffer.allocate(oldPayload.remaining() + encodedHeader.getLength());
                payload.put(encodedHeader.getArray(),encodedHeader.getArrayOffset(),encodedHeader.getLength());
                payload.put(oldPayload);
                payload.flip();
            }

            transfer.setPayload(payload);
            byte[] data = new byte[8];
            ByteBuffer.wrap(data).putLong(_deliveryTag++);
            final Binary tag = new Binary(data);

            transfer.setDeliveryTag(tag);

            synchronized(_link.getLock())
            {
                if(_link.isAttached())
                {
                    if(SenderSettleMode.SETTLED.equals(getEndpoint().getSendingSettlementMode()))
                    {
                        transfer.setSettled(true);
                    }
                    else
                    {
                        UnsettledAction action = _acquires
                                                 ? new DispositionAction(tag, queueEntry)
                                                 : new DoNothingAction(tag, queueEntry);

                        _link.addUnsettled(tag, action, queueEntry);
                    }

                    if(_transactionId != null)
                    {
                        TransactionalState state = new TransactionalState();
                        state.setTxnId(_transactionId);
                        transfer.setState(state);
                    }
                    // TODO - need to deal with failure here
                    if(_acquires && _transactionId != null)
                    {
                        ServerTransaction txn = _link.getTransaction(_transactionId);
                        if(txn != null)
                        {
                            txn.addPostTransactionAction(new ServerTransaction.Action(){

                                public void postCommit()
                                {
                                    //To change body of implemented methods use File | Settings | File Templates.
                                }

                                public void onRollback()
                                {
                                    if(queueEntry.isAcquiredBy(Subscription_1_0.this))
                                    {
                                        queueEntry.release();
                                        _link.getEndpoint().updateDisposition(tag, (DeliveryState)null, true);


                                    }
                                }
                            });
                        }

                    }

                    getEndpoint().transfer(transfer);
                }
                else
                {
                    queueEntry.release();
                }
            }
        }

    }

    public void queueDeleted(final AMQQueue queue)
    {
        //TODO
        getEndpoint().setSource(null);
        getEndpoint().detach();
    }

    public synchronized boolean wouldSuspend(final QueueEntry msg)
    {
        final boolean hasCredit = _link.isAttached() && getEndpoint().hasCreditToSend();
        if(!hasCredit && getState() == State.ACTIVE)
        {
            suspend();
        }

        return !hasCredit;
    }

    public boolean trySendLock()
    {
        return _stateChangeLock.tryLock();
    }

    public synchronized void suspend()
    {
        if(_state.compareAndSet(State.ACTIVE, State.SUSPENDED))
        {
            _stateListener.stateChange(this, State.ACTIVE, State.SUSPENDED);
        }
    }

    public void getSendLock()
    {
        _stateChangeLock.lock();
    }

    public void releaseSendLock()
    {
        _stateChangeLock.unlock();
    }

    public void releaseQueueEntry(QueueEntry queueEntryImpl)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }


    public void onDequeue(final QueueEntry queueEntry)
    {
        //TODO
    }

    public void restoreCredit(final QueueEntry queueEntry)
    {
        //TODO
    }

    public void setStateListener(final StateListener listener)
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

    public boolean isSessionTransactional()
    {
        return false;  //TODO
    }

    public synchronized void queueEmpty()
    {
        if(_link.drained())
        {
            if(_state.compareAndSet(State.ACTIVE, State.SUSPENDED))
            {
                _stateListener.stateChange(this, State.ACTIVE, State.SUSPENDED);
            }
        }
    }

    public synchronized void flowStateChanged()
    {
        if(isSuspended() && getEndpoint() != null)
        {
            if(_state.compareAndSet(State.SUSPENDED, State.ACTIVE))
            {
                _stateListener.stateChange(this, State.SUSPENDED, State.ACTIVE);
            }
            _transactionId = _link.getTransactionId();
        }
    }

    public Session_1_0 getSession()
    {
        return _link.getSession();
    }

    private class DispositionAction implements UnsettledAction
    {

        private final QueueEntry _queueEntry;
        private final Binary _deliveryTag;

        public DispositionAction(Binary tag, QueueEntry queueEntry)
        {
            _deliveryTag = tag;
            _queueEntry = queueEntry;
        }

        public boolean process(DeliveryState state, final Boolean settled)
        {

            Binary transactionId = null;
            final Outcome outcome;
            // If disposition is settled this overrides the txn?
            if(state instanceof TransactionalState)
            {
                transactionId = ((TransactionalState)state).getTxnId();
                outcome = ((TransactionalState)state).getOutcome();
            }
            else if (state instanceof Outcome)
            {
                outcome = (Outcome) state;
            }
            else
            {
                outcome = null;
            }


            ServerTransaction txn = _link.getTransaction(transactionId);

            if(outcome instanceof Accepted)
            {
                txn.dequeue(_queueEntry.getQueue(), _queueEntry.getMessage(),
                        new ServerTransaction.Action()
                        {

                            public void postCommit()
                            {
                                if(_queueEntry.isAcquiredBy(Subscription_1_0.this))
                                {
                                    _queueEntry.discard();
                                }
                            }

                            public void onRollback()
                            {

                            }
                        });
                txn.addPostTransactionAction(new ServerTransaction.Action()
                    {
                        public void postCommit()
                        {
                            //_link.getEndpoint().settle(_deliveryTag);
                            _link.getEndpoint().updateDisposition(_deliveryTag, (DeliveryState)outcome, true);
                            _link.getEndpoint().sendFlowConditional();
                        }

                        public void onRollback()
                        {
                            if(Boolean.TRUE.equals(settled))
                            {
                                final Modified modified = new Modified();
                                modified.setDeliveryFailed(true);
                                _link.getEndpoint().updateDisposition(_deliveryTag, modified, true);
                                _link.getEndpoint().sendFlowConditional();
                            }
                        }
                    });
            }
            else if(outcome instanceof Released)
            {
                txn.addPostTransactionAction(new ServerTransaction.Action()
                {
                    public void postCommit()
                    {

                        _queueEntry.release();
                        _link.getEndpoint().settle(_deliveryTag);
                    }

                    public void onRollback()
                    {
                        _link.getEndpoint().settle(_deliveryTag);
                    }
                });
            }

            else if(outcome instanceof Modified)
            {
                txn.addPostTransactionAction(new ServerTransaction.Action()
                {
                    public void postCommit()
                    {

                        _queueEntry.release();
                        if(Boolean.TRUE.equals(((Modified)outcome).getDeliveryFailed()))
                        {
                            _queueEntry.incrementDeliveryCount();
                        }
                        _link.getEndpoint().settle(_deliveryTag);
                    }

                    public void onRollback()
                    {
                        if(Boolean.TRUE.equals(settled))
                        {
                            final Modified modified = new Modified();
                            modified.setDeliveryFailed(true);
                            _link.getEndpoint().updateDisposition(_deliveryTag, modified, true);
                            _link.getEndpoint().sendFlowConditional();
                        }
                    }
                });
            }

            return (transactionId == null && outcome != null);
        }
    }

    private class DoNothingAction implements UnsettledAction
    {
        public DoNothingAction(final Binary tag,
                               final QueueEntry queueEntry)
        {
        }

        public boolean process(final DeliveryState state, final Boolean settled)
        {
            Binary transactionId = null;
            Outcome outcome = null;
            // If disposition is settled this overrides the txn?
            if(state instanceof TransactionalState)
            {
                transactionId = ((TransactionalState)state).getTxnId();
                outcome = ((TransactionalState)state).getOutcome();
            }
            else if (state instanceof Outcome)
            {
                outcome = (Outcome) state;
            }
            return true;
        }
    }

    public FilterManager getFilters()
    {
        return _filters;
    }

    public void setFilters(final FilterManager filters)
    {
        _filters = filters;
    }
}
