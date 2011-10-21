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
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.store.TransactionLog;

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

    private final TransactionLog _transactionLog;

    public AutoCommitTransaction(TransactionLog transactionLog)
    {
        _transactionLog = transactionLog;
    }

    public long getTransactionStartTime()
    {
        return 0L;
    }

    /**
     * Since AutoCommitTransaction have no concept of a long lived transaction, any Actions registered
     * by the caller are executed immediately.
     */
    public void addPostTransactionAction(Action immediateAction)
    {
        immediateAction.postCommit();
    }

    public void dequeue(BaseQueue queue, EnqueableMessage message, Action postTransactionAction)
    {
        TransactionLog.Transaction txn = null;
        try
        {
            if(message.isPersistent() && queue.isDurable())
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Dequeue of message number " + message.getMessageNumber() + " from transaction log. Queue : " + queue.getNameShortString());
                }

                txn = _transactionLog.newTransaction();
                txn.dequeueMessage(queue, message.getMessageNumber());
                txn.commitTran();
                txn = null;
            }
            postTransactionAction.postCommit();
            postTransactionAction = null;
        }
        catch (AMQException e)
        {
            _logger.error("Error during message dequeue", e);
            throw new RuntimeException("Error during message dequeue", e);
        }
        finally
        {
            rollbackIfNecessary(postTransactionAction, txn);
        }

    }

    public void dequeue(Collection<QueueEntry> queueEntries, Action postTransactionAction)
    {
        TransactionLog.Transaction txn = null;
        try
        {
            for(QueueEntry entry : queueEntries)
            {
                ServerMessage message = entry.getMessage();
                BaseQueue queue = entry.getQueue();

                if(message.isPersistent() && queue.isDurable())
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Dequeue of message number " + message.getMessageNumber() + " from transaction log. Queue : " + queue.getNameShortString());
                    }

                    if(txn == null)
                    {
                        txn = _transactionLog.newTransaction();
                    }

                    txn.dequeueMessage(queue, message.getMessageNumber());
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
        catch (AMQException e)
        {
            _logger.error("Error during message dequeues", e);
            throw new RuntimeException("Error during message dequeues", e);
        }
        finally
        {
            rollbackIfNecessary(postTransactionAction, txn);
        }

    }


    public void enqueue(BaseQueue queue, EnqueableMessage message, Action postTransactionAction)
    {
        TransactionLog.Transaction txn = null;
        try
        {
            if(message.isPersistent() && queue.isDurable())
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Enqueue of message number " + message.getMessageNumber() + " to transaction log. Queue : " + queue.getNameShortString());
                }

                txn = _transactionLog.newTransaction();
                txn.enqueueMessage(queue, message.getMessageNumber());
                txn.commitTran();
                txn = null;
            }
            postTransactionAction.postCommit();
            postTransactionAction = null;
        }
        catch (AMQException e)
        {
            _logger.error("Error during message enqueue", e);
            throw new RuntimeException("Error during message enqueue", e);
        }
        finally
        {
            rollbackIfNecessary(postTransactionAction, txn);
        }


    }

    public void enqueue(List<? extends BaseQueue> queues, EnqueableMessage message, Action postTransactionAction)
    {
        TransactionLog.Transaction txn = null;
        try
        {

            if(message.isPersistent())
            {
                Long id = message.getMessageNumber();
                for(BaseQueue queue : queues)
                {
                    if (queue.isDurable())
                    {
                        if (_logger.isDebugEnabled())
                        {
                            _logger.debug("Enqueue of message number " + message.getMessageNumber() + " to transaction log. Queue : " + queue.getNameShortString());
                        }
                        if (txn == null)
                        {
                            txn = _transactionLog.newTransaction();
                        }
                        
                        txn.enqueueMessage(queue, id);
                    }
                }
                
                if (txn != null)
                {
                    txn.commitTran();
                    txn = null;

                }
            }
            postTransactionAction.postCommit();
            postTransactionAction = null;
        }
        catch (AMQException e)
        {
            _logger.error("Error during message enqueues", e);
            throw new RuntimeException("Error during message enqueues", e);
        }
        finally
        {
            rollbackIfNecessary(postTransactionAction, txn);
        }

    }


    public void commit()
    {
    }

    public void rollback()
    {
    }

    private void rollbackIfNecessary(Action postTransactionAction, TransactionLog.Transaction txn)
    {
        if (txn != null)
        {
            try
            {
                txn.abortTran();
            }
            catch (AMQStoreException e)
            {
                _logger.error("Abort transaction failed", e);
                // Deliberate decision not to re-throw this exception.  Rationale:  we can only reach here if a previous
                // TransactionLog method has ended in Exception.  If we were to re-throw here, we would prevent
                // our caller from receiving the original exception (which is likely to be more revealing of the underlying error).
            }
        }
        if (postTransactionAction != null)
        {
            postTransactionAction.onRollback();
        }
    }

}
