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
package org.apache.qpid.server.transport;

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.CHANNEL_FORMAT;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.message.MessageMetaData_0_10;
import org.apache.qpid.server.message.MessageTransferMessage;
import org.apache.qpid.server.txn.RollbackOnlyDtxException;
import org.apache.qpid.server.txn.TimeoutDtxException;
import static org.apache.qpid.util.Serial.gt;

import java.security.Principal;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.security.auth.Subject;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.ProtocolEngine;
import org.apache.qpid.server.configuration.ConfigStore;
import org.apache.qpid.server.configuration.ConfiguredObject;
import org.apache.qpid.server.configuration.ConnectionConfig;
import org.apache.qpid.server.configuration.SessionConfig;
import org.apache.qpid.server.configuration.SessionConfigType;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.GenericActor;
import org.apache.qpid.server.logging.messages.ChannelMessages;
import org.apache.qpid.server.logging.subjects.ChannelLogSubject;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.security.AuthorizationHolder;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.subscription.Subscription_0_10;
import org.apache.qpid.server.txn.AlreadyKnownDtxException;
import org.apache.qpid.server.txn.AsyncAutoCommitTransaction;
import org.apache.qpid.server.txn.DistributedTransaction;
import org.apache.qpid.server.txn.DtxNotSelectedException;
import org.apache.qpid.server.txn.IncorrectDtxStateException;
import org.apache.qpid.server.txn.JoinAndResumeDtxException;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.NotAssociatedDtxException;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.txn.SuspendAndFailDtxException;
import org.apache.qpid.server.txn.UnknownDtxBranchException;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerSession extends Session 
        implements AuthorizationHolder, SessionConfig, 
                   AMQSessionModel, LogSubject, AsyncAutoCommitTransaction.FutureRecorder
{
    private static final Logger _logger = LoggerFactory.getLogger(ServerSession.class);
    
    private static final String NULL_DESTINTATION = UUID.randomUUID().toString();
    private static final int PRODUCER_CREDIT_TOPUP_THRESHOLD = 1 << 30;
    private static final int UNFINISHED_COMMAND_QUEUE_THRESHOLD = 500;

    private final UUID _id;
    private ConnectionConfig _connectionConfig;
    private long _createTime = System.currentTimeMillis();
    private LogActor _actor = GenericActor.getInstance(this);

    private final Set<AMQQueue> _blockingQueues = new ConcurrentSkipListSet<AMQQueue>();

    private final AtomicBoolean _blocking = new AtomicBoolean(false);
    private ChannelLogSubject _logSubject;
    private final AtomicInteger _outstandingCredit = new AtomicInteger(UNLIMITED_CREDIT);

    public static interface MessageDispositionChangeListener
    {
        public void onAccept();

        public void onRelease(boolean setRedelivered);

        public void onReject();

        public boolean acquire();


    }

    public static interface Task
    {
        public void doTask(ServerSession session);
    }


    private final SortedMap<Integer, MessageDispositionChangeListener> _messageDispositionListenerMap =
            new ConcurrentSkipListMap<Integer, MessageDispositionChangeListener>();

    private ServerTransaction _transaction;
    
    private final AtomicLong _txnStarts = new AtomicLong(0);
    private final AtomicLong _txnCommits = new AtomicLong(0);
    private final AtomicLong _txnRejects = new AtomicLong(0);
    private final AtomicLong _txnCount = new AtomicLong(0);
    private final AtomicLong _txnUpdateTime = new AtomicLong(0);

    private Map<String, Subscription_0_10> _subscriptions = new ConcurrentHashMap<String, Subscription_0_10>();

    private final List<Task> _taskList = new CopyOnWriteArrayList<Task>();

    ServerSession(Connection connection, SessionDelegate delegate, Binary name, long expiry)
    {
        this(connection, delegate, name, expiry, ((ServerConnection)connection).getConfig());
    }

    public ServerSession(Connection connection, SessionDelegate delegate, Binary name, long expiry, ConnectionConfig connConfig)
    {
        super(connection, delegate, name, expiry);
        _connectionConfig = connConfig;        
        _transaction = new AsyncAutoCommitTransaction(this.getMessageStore(),this);
        _logSubject = new ChannelLogSubject(this);
        _id = getConfigStore().createId();
        getConfigStore().addConfiguredObject(this);
    }

    protected void setState(State state)
    {
        super.setState(state);

        if (state == State.OPEN)
        {
            _actor.message(ChannelMessages.CREATE());
        }
    }

    private ConfigStore getConfigStore()
    {
        return getConnectionConfig().getConfigStore();
    }


    @Override
    protected boolean isFull(int id)
    {
        return isCommandsFull(id);
    }

    public void enqueue(final ServerMessage message, final List<? extends BaseQueue> queues)
    {
        if(_outstandingCredit.get() != UNLIMITED_CREDIT
                && _outstandingCredit.decrementAndGet() == (Integer.MAX_VALUE - PRODUCER_CREDIT_TOPUP_THRESHOLD))
        {
            _outstandingCredit.addAndGet(PRODUCER_CREDIT_TOPUP_THRESHOLD);
            invoke(new MessageFlow("",MessageCreditUnit.MESSAGE, PRODUCER_CREDIT_TOPUP_THRESHOLD));
        }
        getConnectionModel().registerMessageReceived(message.getSize(), message.getArrivalTime());
        PostEnqueueAction postTransactionAction = new PostEnqueueAction(queues, message, isTransactional()) ;
        _transaction.enqueue(queues,message, postTransactionAction, 0L);
        incrementOutstandingTxnsIfNecessary();
        updateTransactionalActivity();
    }


    public void sendMessage(MessageTransfer xfr,
                            Runnable postIdSettingAction)
    {
        getConnectionModel().registerMessageDelivered(xfr.getBodySize());
        invoke(xfr, postIdSettingAction);
    }

    public void onMessageDispositionChange(MessageTransfer xfr, MessageDispositionChangeListener acceptListener)
    {
        _messageDispositionListenerMap.put(xfr.getId(), acceptListener);
    }


    private static interface MessageDispositionAction
    {
        void performAction(MessageDispositionChangeListener  listener);
    }

    public void accept(RangeSet ranges)
    {
        dispositionChange(ranges, new MessageDispositionAction()
        {
            public void performAction(MessageDispositionChangeListener listener)
            {
                listener.onAccept();
            }
        });
    }


    public void release(RangeSet ranges, final boolean setRedelivered)
    {
        dispositionChange(ranges, new MessageDispositionAction()
                                      {
                                          public void performAction(MessageDispositionChangeListener listener)
                                          {
                                              listener.onRelease(setRedelivered);
                                          }
                                      });
    }

    public void reject(RangeSet ranges)
    {
        dispositionChange(ranges, new MessageDispositionAction()
                                      {
                                          public void performAction(MessageDispositionChangeListener listener)
                                          {
                                              listener.onReject();
                                          }
                                      });
    }

    public RangeSet acquire(RangeSet transfers)
    {
        RangeSet acquired = RangeSetFactory.createRangeSet();

        if(!_messageDispositionListenerMap.isEmpty())
        {
            Iterator<Integer> unacceptedMessages = _messageDispositionListenerMap.keySet().iterator();
            Iterator<Range> rangeIter = transfers.iterator();

            if(rangeIter.hasNext())
            {
                Range range = rangeIter.next();

                while(range != null && unacceptedMessages.hasNext())
                {
                    int next = unacceptedMessages.next();
                    while(gt(next, range.getUpper()))
                    {
                        if(rangeIter.hasNext())
                        {
                            range = rangeIter.next();
                        }
                        else
                        {
                            range = null;
                            break;
                        }
                    }
                    if(range != null && range.includes(next))
                    {
                        MessageDispositionChangeListener changeListener = _messageDispositionListenerMap.get(next);
                        if(changeListener != null && changeListener.acquire())
                        {
                            acquired.add(next);
                        }
                    }


                }

            }


        }

        return acquired;
    }

    public void dispositionChange(RangeSet ranges, MessageDispositionAction action)
    {
        if(ranges != null)
        {

            if(ranges.size() == 1)
            {
                Range r = ranges.getFirst();
                for(int i = r.getLower(); i <= r.getUpper(); i++)
                {
                    MessageDispositionChangeListener changeListener = _messageDispositionListenerMap.remove(i);
                    if(changeListener != null)
                    {
                        action.performAction(changeListener);
                    }
                }
            }
            else if(!_messageDispositionListenerMap.isEmpty())
            {
                Iterator<Integer> unacceptedMessages = _messageDispositionListenerMap.keySet().iterator();
                Iterator<Range> rangeIter = ranges.iterator();

                if(rangeIter.hasNext())
                {
                    Range range = rangeIter.next();

                    while(range != null && unacceptedMessages.hasNext())
                    {
                        int next = unacceptedMessages.next();
                        while(gt(next, range.getUpper()))
                        {
                            if(rangeIter.hasNext())
                            {
                                range = rangeIter.next();
                            }
                            else
                            {
                                range = null;
                                break;
                            }
                        }
                        if(range != null && range.includes(next))
                        {
                            MessageDispositionChangeListener changeListener = _messageDispositionListenerMap.remove(next);
                            action.performAction(changeListener);
                        }


                    }

                }
            }
        }
    }

    public void removeDispositionListener(Method method)                               
    {
        _messageDispositionListenerMap.remove(method.getId());
    }

    public void onClose()
    {
        if(_transaction instanceof LocalTransaction)
        {
            _transaction.rollback();
        }
        else if(_transaction instanceof DistributedTransaction)
        {
            getVirtualHost().getDtxRegistry().endAssociations(this);
        }

        for(MessageDispositionChangeListener listener : _messageDispositionListenerMap.values())
        {
            listener.onRelease(true);
        }
        _messageDispositionListenerMap.clear();

        getConfigStore().removeConfiguredObject(this);

        for (Task task : _taskList)
        {
            task.doTask(this);
        }
        
        CurrentActor.get().message(getLogSubject(), ChannelMessages.CLOSE());
    }

    @Override
    protected void awaitClose()
    {
        // Broker shouldn't block awaiting close - thus do override this method to do nothing
    }

    public void acknowledge(final Subscription_0_10 sub, final QueueEntry entry)
    {
        _transaction.dequeue(entry.getQueue(), entry.getMessage(),
                             new ServerTransaction.Action()
                             {

                                 public void postCommit()
                                 {
                                     sub.acknowledge(entry);
                                 }

                                 public void onRollback()
                                 {
                                     // The client has acknowledge the message and therefore have seen it.
                                     // In the event of rollback, the message must be marked as redelivered.
                                     entry.setRedelivered();
                                     entry.release();
                                 }
                             });
	    updateTransactionalActivity();
    }

    public Collection<Subscription_0_10> getSubscriptions()
    {
        return _subscriptions.values();
    }

    public void register(String destination, Subscription_0_10 sub)
    {
        _subscriptions.put(destination == null ? NULL_DESTINTATION : destination, sub);
    }

    public Subscription_0_10 getSubscription(String destination)
    {
        return _subscriptions.get(destination == null ? NULL_DESTINTATION : destination);
    }

    public void unregister(Subscription_0_10 sub)
    {
        _subscriptions.remove(sub.getConsumerTag().toString());
        try
        {
            sub.getSendLock();
            AMQQueue queue = sub.getQueue();
            if(queue != null)
            {
                queue.unregisterSubscription(sub);
            }
        }
        catch (AMQException e)
        {
            // TODO
            _logger.error("Failed to unregister subscription :" + e.getMessage(), e);
        }
        finally
        {
            sub.releaseSendLock();
        }
    }

    public boolean isTransactional()
    {
        return _transaction.isTransactional();
    }
    
    public boolean inTransaction()
    {
        return isTransactional() && _txnUpdateTime.get() > 0 && _transaction.getTransactionStartTime() > 0;
    }

    public void selectTx()
    {
        _transaction = new LocalTransaction(this.getMessageStore());
        _txnStarts.incrementAndGet();
    }

    public void selectDtx()
    {
        _transaction = new DistributedTransaction(this, getMessageStore(), getVirtualHost());

    }


    public void startDtx(Xid xid, boolean join, boolean resume)
            throws JoinAndResumeDtxException,
                   UnknownDtxBranchException,
                   AlreadyKnownDtxException,
                   DtxNotSelectedException
    {
        DistributedTransaction distributedTransaction = assertDtxTransaction();
        distributedTransaction.start(xid, join, resume);
    }


    public void endDtx(Xid xid, boolean fail, boolean suspend)
            throws NotAssociatedDtxException,
            UnknownDtxBranchException,
            DtxNotSelectedException,
            SuspendAndFailDtxException, TimeoutDtxException
    {
        DistributedTransaction distributedTransaction = assertDtxTransaction();
        distributedTransaction.end(xid, fail, suspend);
    }


    public long getTimeoutDtx(Xid xid)
            throws UnknownDtxBranchException
    {
        return getVirtualHost().getDtxRegistry().getTimeout(xid);
    }


    public void setTimeoutDtx(Xid xid, long timeout)
            throws UnknownDtxBranchException
    {
        getVirtualHost().getDtxRegistry().setTimeout(xid, timeout);
    }


    public void prepareDtx(Xid xid)
            throws UnknownDtxBranchException,
            IncorrectDtxStateException, AMQStoreException, RollbackOnlyDtxException, TimeoutDtxException
    {
        getVirtualHost().getDtxRegistry().prepare(xid);
    }

    public void commitDtx(Xid xid, boolean onePhase)
            throws UnknownDtxBranchException,
            IncorrectDtxStateException, AMQStoreException, RollbackOnlyDtxException, TimeoutDtxException
    {
        getVirtualHost().getDtxRegistry().commit(xid, onePhase);
    }


    public void rollbackDtx(Xid xid)
            throws UnknownDtxBranchException,
            IncorrectDtxStateException, AMQStoreException, TimeoutDtxException
    {
        getVirtualHost().getDtxRegistry().rollback(xid);
    }


    public void forgetDtx(Xid xid) throws UnknownDtxBranchException, IncorrectDtxStateException
    {
        getVirtualHost().getDtxRegistry().forget(xid);
    }

    public List<Xid> recoverDtx()
    {
        return getVirtualHost().getDtxRegistry().recover();
    }

    private DistributedTransaction assertDtxTransaction() throws DtxNotSelectedException
    {
        if(_transaction instanceof DistributedTransaction)
        {
            return (DistributedTransaction) _transaction;
        }
        else
        {
            throw new DtxNotSelectedException();
        }
    }


    public void commit()
    {
        _transaction.commit();
        
        _txnCommits.incrementAndGet();
        _txnStarts.incrementAndGet();
        decrementOutstandingTxnsIfNecessary();
    }

    public void rollback()
    {
        _transaction.rollback();
        
        _txnRejects.incrementAndGet();
        _txnStarts.incrementAndGet();
        decrementOutstandingTxnsIfNecessary();
    }

    
    private void incrementOutstandingTxnsIfNecessary()
    {
        if(isTransactional())
        {
            //There can currently only be at most one outstanding transaction
            //due to only having LocalTransaction support. Set value to 1 if 0.
            _txnCount.compareAndSet(0,1);
        }
    }
    
    private void decrementOutstandingTxnsIfNecessary()
    {
        if(isTransactional())
        {
            //There can currently only be at most one outstanding transaction
            //due to only having LocalTransaction support. Set value to 0 if 1.
            _txnCount.compareAndSet(1,0);
        }
    }

    /**
     * Update last transaction activity timestamp
     */
    public void updateTransactionalActivity()
    {
        if (isTransactional())
        {
            _txnUpdateTime.set(System.currentTimeMillis());
        }
    }

    public Long getTxnStarts()
    {
        return _txnStarts.get();
    }

    public Long getTxnCommits()
    {
        return _txnCommits.get();
    }

    public Long getTxnRejects()
    {
        return _txnRejects.get();
    }

    public int getChannelId()
    {
        return getChannel();
    }

    public Long getTxnCount()
    {
        return _txnCount.get();
    }
    
    public Principal getAuthorizedPrincipal()
    {
        return getConnection().getAuthorizedPrincipal();
    }
    
    public Subject getAuthorizedSubject()
    {
        return getConnection().getAuthorizedSubject();
    }

    public void addSessionCloseTask(Task task)
    {
        _taskList.add(task);
    }

    public void removeSessionCloseTask(Task task)
    {
        _taskList.remove(task);
    }

    public Object getReference()
    {
        return getConnection().getReference();
    }

    public MessageStore getMessageStore()
    {
        return getVirtualHost().getMessageStore();
    }

    public VirtualHost getVirtualHost()
    {
        return (VirtualHost) _connectionConfig.getVirtualHost();
    }

    public UUID getId()
    {
        return _id;
    }

    public SessionConfigType getConfigType()
    {
        return SessionConfigType.getInstance();
    }

    public ConfiguredObject getParent()
    {
        return getVirtualHost();
    }

    public boolean isDurable()
    {
        return false;
    }

    public boolean isAttached()
    {
        return true;
    }

    public long getDetachedLifespan()
    {
        return 0;
    }

    public Long getExpiryTime()
    {
        return null;
    }

    public Long getMaxClientRate()
    {
        return null;
    }

    public ConnectionConfig getConnectionConfig()
    {
        return _connectionConfig;
    }

    public String getSessionName()
    {
        return getName().toString();
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    public void mgmtClose()
    {
        close();
    }

    public AMQConnectionModel getConnectionModel()
    {
        return getConnection();
    }

    public String getClientID()
    {
        return getConnection().getClientId();
    }

    @Override
    public ServerConnection getConnection()
    {
        return (ServerConnection) super.getConnection();
    }

    public LogActor getLogActor()
    {
        return _actor;
    }

    public LogSubject getLogSubject()
    {
        return (LogSubject) this;
    }

    public void checkTransactionStatus(long openWarn, long openClose, long idleWarn, long idleClose) throws AMQException
    {
        if (inTransaction())
        {
            long currentTime = System.currentTimeMillis();
            long openTime = currentTime - _transaction.getTransactionStartTime();
            long idleTime = currentTime - _txnUpdateTime.get();

            // Log a warning on idle or open transactions
            if (idleWarn > 0L && idleTime > idleWarn)
            {
                CurrentActor.get().message(getLogSubject(), ChannelMessages.IDLE_TXN(idleTime));
                _logger.warn("IDLE TRANSACTION ALERT " + getLogSubject().toString() + " " + idleTime + " ms");
            }
            else if (openWarn > 0L && openTime > openWarn)
            {
                CurrentActor.get().message(getLogSubject(), ChannelMessages.OPEN_TXN(openTime));
                _logger.warn("OPEN TRANSACTION ALERT " + getLogSubject().toString() + " " + openTime + " ms");
            }

            // Close connection for idle or open transactions that have timed out
            if (idleClose > 0L && idleTime > idleClose)
            {
                getConnectionModel().closeSession(this, AMQConstant.RESOURCE_ERROR, "Idle transaction timed out");
            }
            else if (openClose > 0L && openTime > openClose)
            {
                getConnectionModel().closeSession(this, AMQConstant.RESOURCE_ERROR, "Open transaction timed out");
            }
        }
    }

    public void block(AMQQueue queue)
    {

        if(_blockingQueues.add(queue))
        {

            if(_blocking.compareAndSet(false,true))
            {
                invoke(new MessageSetFlowMode("", MessageFlowMode.CREDIT));
                invoke(new MessageStop(""));
                _actor.message(_logSubject, ChannelMessages.FLOW_ENFORCED(queue.getNameShortString().toString()));
            }


        }
    }

    public void unblock(AMQQueue queue)
    {
        if(_blockingQueues.remove(queue) && _blockingQueues.isEmpty())
        {
            if(_blocking.compareAndSet(true,false) && !isClosing())
            {

                _actor.message(_logSubject, ChannelMessages.FLOW_REMOVED());
                MessageFlow mf = new MessageFlow();
                mf.setUnit(MessageCreditUnit.MESSAGE);
                mf.setDestination("");
                _outstandingCredit.set(Integer.MAX_VALUE);
                mf.setValue(Integer.MAX_VALUE);
                invoke(mf);


            }
        }
    }

    public boolean onSameConnection(InboundMessage inbound)
    {
        return ((inbound instanceof MessageTransferMessage)
                && ((MessageTransferMessage)inbound).getConnectionReference() == getConnection().getReference())
                || ((inbound instanceof MessageMetaData_0_10)
                    && (((MessageMetaData_0_10)inbound).getConnectionReference())== getConnection().getReference());
    }


    public String toLogString()
    {
        long connectionId = super.getConnection() instanceof ServerConnection
                            ? getConnection().getConnectionId()
                            : -1;

        String remoteAddress = _connectionConfig instanceof ProtocolEngine
                                ? ((ProtocolEngine) _connectionConfig).getRemoteAddress().toString()
                                : "";
        return "[" +
               MessageFormat.format(CHANNEL_FORMAT,
                                    connectionId,
                                   getClientID(),
                                   remoteAddress,
                                   getVirtualHost().getName(),
                                   getChannel())
            + "] ";
    }

    @Override
    public void close()
    {
        // unregister subscriptions in order to prevent sending of new messages
        // to subscriptions with closing session
        unregisterSubscriptions();
        super.close();
    }

    void unregisterSubscriptions()
    {
        final Collection<Subscription_0_10> subscriptions = getSubscriptions();
        for (Subscription_0_10 subscription_0_10 : subscriptions)
        {
            unregister(subscription_0_10);
        }
    }

    void stopSubscriptions()
    {
        final Collection<Subscription_0_10> subscriptions = getSubscriptions();
        for (Subscription_0_10 subscription_0_10 : subscriptions)
        {
            subscription_0_10.stop();
        }
    }


    public void receivedComplete()
    {
        final Collection<Subscription_0_10> subscriptions = getSubscriptions();
        for (Subscription_0_10 subscription_0_10 : subscriptions)
        {
            subscription_0_10.flushCreditState(false);
        }
        awaitCommandCompletion();
    }

    private class PostEnqueueAction implements ServerTransaction.Action
    {

        private List<? extends BaseQueue> _queues;
        private ServerMessage _message;
        private final boolean _transactional;

        public PostEnqueueAction(List<? extends BaseQueue> queues, ServerMessage message, final boolean transactional)
        {
            _transactional = transactional;
            setState(queues, message);
        }

        public void setState(List<? extends BaseQueue> queues, ServerMessage message)
        {
            _message = message;
            _queues = queues;
        }

        public void postCommit()
        {
            MessageReference<?> ref = _message.newReference();
            for(int i = 0; i < _queues.size(); i++)
            {
                try
                {
                    BaseQueue queue = _queues.get(i);
                    queue.enqueue(_message, _transactional, null);
                    if(queue instanceof AMQQueue)
                    {
                        ((AMQQueue)queue).checkCapacity(ServerSession.this);
                    }

                }
                catch (AMQException e)
                {
                    // TODO
                    throw new RuntimeException(e);
                }
            }
            ref.release();
        }

        public void onRollback()
        {
            // NO-OP
        }
    }

    public int getUnacknowledgedMessageCount()
    {
        return _messageDispositionListenerMap.size();
    }

    public boolean getBlocking()
    {
        return _blocking.get();
    }

    private final LinkedList<AsyncCommand> _unfinishedCommandsQueue = new LinkedList<AsyncCommand>();

    public void completeAsyncCommands()
    {
        AsyncCommand cmd;
        while((cmd = _unfinishedCommandsQueue.peek()) != null && cmd.isReadyForCompletion())
        {
            cmd.complete();
            _unfinishedCommandsQueue.poll();
        }
        while(_unfinishedCommandsQueue.size() > UNFINISHED_COMMAND_QUEUE_THRESHOLD)
        {
            cmd = _unfinishedCommandsQueue.poll();
            cmd.awaitReadyForCompletion();
            cmd.complete();
        }
    }


    public void awaitCommandCompletion()
    {
        AsyncCommand cmd;
        while((cmd = _unfinishedCommandsQueue.poll()) != null)
        {
            cmd.awaitReadyForCompletion();
            cmd.complete();
        }
    }


    public Object getAsyncCommandMark()
    {
        return _unfinishedCommandsQueue.isEmpty() ? null : _unfinishedCommandsQueue.getLast();
    }

    public void recordFuture(final MessageStore.StoreFuture future, final ServerTransaction.Action action)
    {
        _unfinishedCommandsQueue.add(new AsyncCommand(future, action));
    }

    private static class AsyncCommand
    {
        private final MessageStore.StoreFuture _future;
        private ServerTransaction.Action _action;

        public AsyncCommand(final MessageStore.StoreFuture future, final ServerTransaction.Action action)
        {
            _future = future;
            _action = action;
        }

        void awaitReadyForCompletion()
        {
            _future.waitForCompletion();
        }

        void complete()
        {
            if(!_future.isComplete())
            {
                _future.waitForCompletion();
            }
            _action.postCommit();
            _action = null;
        }

        boolean isReadyForCompletion()
        {
            return _future.isComplete();
        }
    }

    protected void setClose(boolean close)
    {
        super.setClose(close);
    }

    public int compareTo(AMQSessionModel session)
    {
        return getId().compareTo(session.getId());
    }

}
