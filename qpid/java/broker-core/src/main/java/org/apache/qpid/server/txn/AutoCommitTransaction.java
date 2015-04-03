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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.store.MessageEnqueueRecord;
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
    protected static final Logger _logger = LoggerFactory.getLogger(AutoCommitTransaction.class);

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

    public void dequeue(MessageEnqueueRecord record, Action postTransactionAction)
    {
        Transaction txn = null;
        try
        {
            if(record != null)
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Dequeue of message number " + record.getMessageNumber() + " from transaction log. Queue : " + record.getQueueId());
                }

                txn = _messageStore.newTransaction();
                txn.dequeueMessage(record);
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
                MessageEnqueueRecord enqueueRecord = entry.getEnqueueRecord();
                if(enqueueRecord != null)
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Dequeue of message number " + enqueueRecord.getMessageNumber() + " from transaction log. Queue : " + enqueueRecord.getQueueId());
                    }

                    if(txn == null)
                    {
                        txn = _messageStore.newTransaction();
                    }

                    txn.dequeueMessage(enqueueRecord);
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


    public void enqueue(TransactionLogResource queue, EnqueueableMessage message, EnqueueAction postTransactionAction)
    {
        Transaction txn = null;
        try
        {
            final MessageEnqueueRecord record;
            if(queue.getMessageDurability().persist(message.isPersistent()))
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Enqueue of message number " + message.getMessageNumber() + " to transaction log. Queue : " + queue.getName());
                }

                txn = _messageStore.newTransaction();
                record = txn.enqueueMessage(queue, message);
                txn.commitTran();
                txn = null;
            }
            else
            {
                record = null;
            }
            if(postTransactionAction != null)
            {
                postTransactionAction.postCommit(record);
            }
            postTransactionAction = null;
        }
        finally
        {
            final EnqueueAction underlying = postTransactionAction;
            rollbackIfNecessary(new Action()
            {
                @Override
                public void postCommit()
                {

                }

                @Override
                public void onRollback()
                {
                    if(underlying != null)
                    {
                        underlying.onRollback();
                    }
                }
            }, txn);
        }


    }

    public void enqueue(List<? extends BaseQueue> queues, EnqueueableMessage message, EnqueueAction postTransactionAction)
    {
        Transaction txn = null;
        try
        {
            MessageEnqueueRecord[] enqueueRecords = new MessageEnqueueRecord[queues.size()];
            int i = 0;
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
                    enqueueRecords[i] = txn.enqueueMessage(queue, message);


                }
                i++;
            }
            if (txn != null)
            {
                txn.commitTran();
                txn = null;
            }

            if(postTransactionAction != null)
            {
                postTransactionAction.postCommit(enqueueRecords);
            }
            postTransactionAction = null;


        }
        finally
        {
            final EnqueueAction underlying = postTransactionAction;
            rollbackIfNecessary(new Action()
            {
                @Override
                public void postCommit()
                {

                }

                @Override
                public void onRollback()
                {
                    if(underlying != null)
                    {
                        underlying.onRollback();
                    }
                }
            }, txn);
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
