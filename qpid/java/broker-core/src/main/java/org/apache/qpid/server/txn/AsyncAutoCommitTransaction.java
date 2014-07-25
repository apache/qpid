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

import java.util.Collection;
import java.util.List;

import org.apache.log4j.Logger;

import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.store.TransactionLogResource;

/**
 * An implementation of ServerTransaction where each enqueue/dequeue
 * operation takes place within it own transaction.
 *
 * Since there is no long-lived transaction, the commit and rollback methods of
 * this implementation are empty.
 */
public class AsyncAutoCommitTransaction implements ServerTransaction
{
    static final String QPID_STRICT_ORDER_WITH_MIXED_DELIVERY_MODE = "qpid.strict_order_with_mixed_delivery_mode";

    protected static final Logger _logger = Logger.getLogger(AsyncAutoCommitTransaction.class);

    private final MessageStore _messageStore;
    private final FutureRecorder _futureRecorder;

    //Set true to ensure strict ordering when enqueuing messages with mixed delivery mode, i.e. disable async persistence
    private boolean _strictOrderWithMixedDeliveryMode = Boolean.getBoolean(QPID_STRICT_ORDER_WITH_MIXED_DELIVERY_MODE);

    public static interface FutureRecorder
    {
        public void recordFuture(StoreFuture future, Action action);

    }

    public AsyncAutoCommitTransaction(MessageStore transactionLog, FutureRecorder recorder)
    {
        _messageStore = transactionLog;
        _futureRecorder = recorder;
    }

    @Override
    public long getTransactionStartTime()
    {
        return 0L;
    }

    @Override
    public long getTransactionUpdateTime()
    {
        return 0L;
    }

    /**
     * Since AutoCommitTransaction have no concept of a long lived transaction, any Actions registered
     * by the caller are executed immediately.
     */
    public void addPostTransactionAction(final Action immediateAction)
    {
        addFuture(StoreFuture.IMMEDIATE_FUTURE, immediateAction);

    }

    public void dequeue(TransactionLogResource queue, EnqueueableMessage message, Action postTransactionAction)
    {
        Transaction txn = null;
        try
        {
            StoreFuture future;
            if(queue.getMessageDurability().persist(message.isPersistent()))
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Dequeue of message number " + message.getMessageNumber() + " from transaction log. Queue : " + queue.getName());
                }

                txn = _messageStore.newTransaction();
                txn.dequeueMessage(queue, message);
                future = txn.commitTranAsync();

                txn = null;
            }
            else
            {
                future = StoreFuture.IMMEDIATE_FUTURE;
            }
            addFuture(future, postTransactionAction);
            postTransactionAction = null;
        }
        finally
        {
            rollbackIfNecessary(postTransactionAction, txn);
        }

    }

    private void addFuture(final StoreFuture future, final Action action)
    {
        if(action != null)
        {
            if(future.isComplete())
            {
                action.postCommit();
            }
            else
            {
                _futureRecorder.recordFuture(future, action);
            }
        }
    }

    private void addEnqueueFuture(final StoreFuture future, final Action action, boolean persistent)
    {
        if(action != null)
        {
            // For persistent messages, do not synchronously invoke postCommit even if the future  is completed.
            // Otherwise, postCommit (which actually does the enqueuing) might be called on successive messages out of order.
            if(future.isComplete() && !persistent && !_strictOrderWithMixedDeliveryMode)
            {
                action.postCommit();
            }
            else
            {
                _futureRecorder.recordFuture(future, action);
            }
        }
    }

    public void dequeue(Collection<MessageInstance> queueEntries, Action postTransactionAction)
    {
        Transaction txn = null;
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

                    if(txn == null)
                    {
                        txn = _messageStore.newTransaction();
                    }

                    txn.dequeueMessage(queue, message);
                }

            }
            StoreFuture future;
            if(txn != null)
            {
                future = txn.commitTranAsync();
                txn = null;
            }
            else
            {
                future = StoreFuture.IMMEDIATE_FUTURE;
            }
            addFuture(future, postTransactionAction);
            postTransactionAction = null;
        }
        finally
        {
            rollbackIfNecessary(postTransactionAction, txn);
        }

    }


    public void enqueue(TransactionLogResource queue, EnqueueableMessage message, Action postTransactionAction)
    {
        Transaction txn = null;
        try
        {
            StoreFuture future;
            if(queue.getMessageDurability().persist(message.isPersistent()))
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Enqueue of message number " + message.getMessageNumber() + " to transaction log. Queue : " + queue.getName());
                }

                txn = _messageStore.newTransaction();
                txn.enqueueMessage(queue, message);
                future = txn.commitTranAsync();
                txn = null;
            }
            else
            {
                future = StoreFuture.IMMEDIATE_FUTURE;
            }
            addEnqueueFuture(future, postTransactionAction, message.isPersistent());
            postTransactionAction = null;
        }finally
        {
            rollbackIfNecessary(postTransactionAction, txn);
        }


    }

    public void enqueue(List<? extends BaseQueue> queues, EnqueueableMessage message, Action postTransactionAction)
    {
        Transaction txn = null;
        try
        {

            for(BaseQueue queue : queues)
            {
                if (queue.getMessageDurability().persist(message.isPersistent()))
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Enqueue of message number " + message.getMessageNumber() + " to transaction log. Queue : " + queue.getName());
                    }
                    if (txn == null)
                    {
                        txn = _messageStore.newTransaction();
                    }
                    txn.enqueueMessage(queue, message);


                }
            }

            StoreFuture future;
            if (txn != null)
            {
                future = txn.commitTranAsync();
                txn = null;
            }
            else
            {
                future = StoreFuture.IMMEDIATE_FUTURE;
            }
            addEnqueueFuture(future, postTransactionAction, message.isPersistent());
            postTransactionAction = null;


        }finally
        {
            rollbackIfNecessary(postTransactionAction, txn);
        }

    }


    public void commit(final Runnable immediatePostTransactionAction)
    {
        if(immediatePostTransactionAction != null)
        {
            addFuture(StoreFuture.IMMEDIATE_FUTURE, new Action()
            {
                public void postCommit()
                {
                    immediatePostTransactionAction.run();
                }

                public void onRollback()
                {
                }
            });
        }
    }

    public void commit()
    {
    }

    public void rollback()
    {
    }

    public boolean isTransactional()
    {
        return false;
    }

    private void rollbackIfNecessary(Action postTransactionAction, Transaction txn)
    {
        if (txn != null)
        {
            txn.abortTran();
        }
        if (postTransactionAction != null)
        {
            postTransactionAction.onRollback();
        }
    }

}
