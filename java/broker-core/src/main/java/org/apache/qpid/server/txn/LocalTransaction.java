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
package org.apache.qpid.server.txn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.util.FutureResult;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.store.TransactionLogResource;

/**
 * A concrete implementation of ServerTransaction where enqueue/dequeue
 * operations share a single long-lived transaction.
 *
 * The caller is responsible for invoking commit() (or rollback()) as necessary.
 */
public class LocalTransaction implements ServerTransaction
{
    protected static final Logger _logger = LoggerFactory.getLogger(LocalTransaction.class);

    private final List<Action> _postTransactionActions = new ArrayList<Action>();

    private volatile Transaction _transaction;
    private final ActivityTimeAccessor _activityTime;
    private final MessageStore _transactionLog;
    private volatile long _txnStartTime = 0L;
    private volatile long _txnUpdateTime = 0l;
    private FutureResult _asyncTran;

    public LocalTransaction(MessageStore transactionLog)
    {
        this(transactionLog, new ActivityTimeAccessor()
        {
            @Override
            public long getActivityTime()
            {
                return System.currentTimeMillis();
            }
        });
    }

    public LocalTransaction(MessageStore transactionLog, ActivityTimeAccessor activityTime)
    {
        _transactionLog = transactionLog;
        _activityTime = activityTime;
    }

    @Override
    public long getTransactionStartTime()
    {
        return _txnStartTime;
    }

    @Override
    public long getTransactionUpdateTime()
    {
        return _txnUpdateTime;
    }

    public void addPostTransactionAction(Action postTransactionAction)
    {
        sync();
        _postTransactionActions.add(postTransactionAction);
    }

    public void dequeue(TransactionLogResource queue, EnqueueableMessage message, Action postTransactionAction)
    {
        sync();
        _postTransactionActions.add(postTransactionAction);
        initTransactionStartTimeIfNecessaryAndAdvanceUpdateTime();

        if(queue.getMessageDurability().persist(message.isPersistent()))
        {
            try
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Dequeue of message number " + message.getMessageNumber() + " from transaction log. Queue : " + queue.getName());
                }

                beginTranIfNecessary();
                _transaction.dequeueMessage(queue, message);
            }
            catch(RuntimeException e)
            {
                tidyUpOnError(e);
            }
        }
    }

    public void dequeue(Collection<MessageInstance> queueEntries, Action postTransactionAction)
    {
        sync();
        _postTransactionActions.add(postTransactionAction);
        initTransactionStartTimeIfNecessaryAndAdvanceUpdateTime();

        try
        {
            for(MessageInstance entry : queueEntries)
            {
                ServerMessage message = entry.getMessage();
                TransactionLogResource queue = entry.getOwningResource();

                if(queue.getMessageDurability().persist(message.isPersistent()))
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Dequeue of message number " + message.getMessageNumber() + " from transaction log. Queue : " + queue.getName());
                    }

                    beginTranIfNecessary();
                    _transaction.dequeueMessage(queue, message);
                }
            }

        }
        catch(RuntimeException e)
        {
            tidyUpOnError(e);
        }
    }

    private void tidyUpOnError(RuntimeException e)
    {
        try
        {
            doRollbackActions();
        }
        finally
        {
            try
            {
                if (_transaction != null)
                {
                    _transaction.abortTran();
                }
            }
            finally
            {
                resetDetails();
            }
        }

        throw e;
    }
    private void beginTranIfNecessary()
    {

        if(_transaction == null)
        {
            _transaction = _transactionLog.newTransaction();
        }
    }

    public void enqueue(TransactionLogResource queue, EnqueueableMessage message, Action postTransactionAction)
    {
        sync();
        _postTransactionActions.add(postTransactionAction);
        initTransactionStartTimeIfNecessaryAndAdvanceUpdateTime();

        if(queue.getMessageDurability().persist(message.isPersistent()))
        {
            try
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Enqueue of message number " + message.getMessageNumber() + " to transaction log. Queue : " + queue.getName());
                }

                beginTranIfNecessary();
                _transaction.enqueueMessage(queue, message);
            }
            catch(RuntimeException e)
            {
                tidyUpOnError(e);
            }
        }
    }

    public void enqueue(List<? extends BaseQueue> queues, EnqueueableMessage message, Action postTransactionAction)
    {
        sync();
        _postTransactionActions.add(postTransactionAction);
        initTransactionStartTimeIfNecessaryAndAdvanceUpdateTime();

        try
        {
            for(BaseQueue queue : queues)
            {
                if(queue.getMessageDurability().persist(message.isPersistent()))
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Enqueue of message number " + message.getMessageNumber() + " to transaction log. Queue : " + queue.getName() );
                    }

                    beginTranIfNecessary();
                    _transaction.enqueueMessage(queue, message);

                }
            }
        }
        catch(RuntimeException e)
        {
            tidyUpOnError(e);
        }
    }

    public void commit()
    {
        sync();
        commit(null);
    }

    public void commit(Runnable immediateAction)
    {
        sync();
        try
        {
            if(_transaction != null)
            {
                _transaction.commitTran();
            }

            if(immediateAction != null)
            {
                immediateAction.run();
            }

            doPostTransactionActions();
        }
        finally
        {
            resetDetails();
        }
    }

    private void doRollbackActions()
    {
        for(Action action : _postTransactionActions)
        {
            action.onRollback();
        }
    }

    public FutureResult commitAsync(final Runnable deferred)
    {
        sync();
        FutureResult future = FutureResult.IMMEDIATE_FUTURE;
        if(_transaction != null)
        {
            future = new FutureResult()
                        {
                            private volatile boolean _completed = false;
                            private FutureResult _underlying = _transaction.commitTranAsync();

                            @Override
                            public boolean isComplete()
                            {
                                return _completed || checkUnderlyingCompletion();
                            }

                            @Override
                            public void waitForCompletion()
                            {
                                if(!_completed)
                                {
                                    _underlying.waitForCompletion();
                                    checkUnderlyingCompletion();
                                }
                            }

                            @Override
                            public void waitForCompletion(final long timeout) throws TimeoutException
                            {

                                if(!_completed)
                                {
                                    _underlying.waitForCompletion(timeout);
                                    checkUnderlyingCompletion();
                                }
                            }

                            private synchronized boolean checkUnderlyingCompletion()
                            {
                                if(!_completed && _underlying.isComplete())
                                {
                                    completeDeferredWork();
                                    _completed = true;
                                }
                                return _completed;

                            }

                            private void completeDeferredWork()
                            {
                                try
                                {
                                    doPostTransactionActions();
                                    deferred.run();
                                }
                                finally
                                {
                                    resetDetails();
                                }
                            }
            };
            _asyncTran = future;
        }
        else
        {
                try
                {
                    doPostTransactionActions();
                    deferred.run();
                }
                finally
                {
                    resetDetails();
                }
        }
        return future;
    }

    private void doPostTransactionActions()
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("Beginning " + _postTransactionActions.size() + " post transaction actions");
        }

        for(int i = 0; i < _postTransactionActions.size(); i++)
        {
            _postTransactionActions.get(i).postCommit();
        }

        if(_logger.isDebugEnabled())
        {
            _logger.debug("Completed post transaction actions");
        }
    }

    public void rollback()
    {
        sync();
        try
        {
            if(_transaction != null)
            {
                _transaction.abortTran();
            }
        }
        finally
        {
            try
            {
                doRollbackActions();
            }
            finally
            {
                resetDetails();
            }
        }
    }

    public void sync()
    {
        if(_asyncTran != null)
        {
            _asyncTran.waitForCompletion();
            _asyncTran = null;
        }
    }

    private void initTransactionStartTimeIfNecessaryAndAdvanceUpdateTime()
    {
        long currentTime = _activityTime.getActivityTime();

        if (_txnStartTime == 0)
        {
            _txnStartTime = currentTime;
        }
        _txnUpdateTime = currentTime;
    }

    private void resetDetails()
    {
        _asyncTran = null;
        _transaction = null;
        _postTransactionActions.clear();
        _txnStartTime = 0L;
        _txnUpdateTime = 0;
    }

    public boolean isTransactional()
    {
        return true;
    }

    public interface ActivityTimeAccessor
    {
        long getActivityTime();
    }

}
