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
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.store.TransactionLogResource;

/**
 * An implementation of ServerTransaction where each enqueue/dequeue
 * operation takes place within it own transaction.
 *
 * Since there is no long-lived transaction, the commit and rollback methods of
 * this implementation are empty.
 */
public class AutoCommitTransaction implements ServerTransaction
{
    protected static final Logger _logger = Logger.getLogger(AutoCommitTransaction.class);

    private final MessageStore _messageStore;

    public AutoCommitTransaction(MessageStore transactionLog)
    {
        _messageStore = transactionLog;
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
        immediateAction.postCommit();
    }

    public void dequeue(TransactionLogResource queue, EnqueueableMessage message, Action postTransactionAction)
    {
        Transaction txn = null;
        try
        {
            if(queue.getMessageDurability().persist(message.isPersistent()))
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Dequeue of message number " + message.getMessageNumber() + " from transaction log. Queue : " + queue.getName());
                }

                txn = _messageStore.newTransaction();
                txn.dequeueMessage(queue, message);
                txn.commitTran();
                txn = null;
            }
            postTransactionAction.postCommit();
            postTransactionAction = null;
        }
        finally
        {
            rollbackIfNecessary(postTransactionAction, txn);
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
            if(txn != null)
            {
                txn.commitTran();
                txn = null;
            }
            postTransactionAction.postCommit();
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
            if(queue.getMessageDurability().persist(message.isPersistent()))
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Enqueue of message number " + message.getMessageNumber() + " to transaction log. Queue : " + queue.getName());
                }

                txn = _messageStore.newTransaction();
                txn.enqueueMessage(queue, message);
                txn.commitTran();
                txn = null;
            }
            postTransactionAction.postCommit();
            postTransactionAction = null;
        }
        finally
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
            if (txn != null)
            {
                txn.commitTran();
                txn = null;
            }

            postTransactionAction.postCommit();
            postTransactionAction = null;


        }finally
        {
            rollbackIfNecessary(postTransactionAction, txn);
        }

    }


    public void commit(final Runnable immediatePostTransactionAction)
    {
        immediatePostTransactionAction.run();
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
