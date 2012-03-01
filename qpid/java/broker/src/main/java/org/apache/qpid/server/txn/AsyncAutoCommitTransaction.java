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

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStore.StoreFuture;

import java.util.Collection;
import java.util.List;

/**
 * An implementation of ServerTransaction where each enqueue/dequeue
 * operation takes place within it own transaction.
 * 
 * Since there is no long-lived transaction, the commit and rollback methods of
 * this implementation are empty.
 */
public class AsyncAutoCommitTransaction implements ServerTransaction
{
    protected static final Logger _logger = Logger.getLogger(AsyncAutoCommitTransaction.class);

    private final MessageStore _messageStore;
    private final FutureRecorder _futureRecorder;

    public static interface FutureRecorder
    {
        public void recordFuture(StoreFuture future, Action action);

    }

    public AsyncAutoCommitTransaction(MessageStore transactionLog, FutureRecorder recorder)
    {
        _messageStore = transactionLog;
        _futureRecorder = recorder;
    }

    public long getTransactionStartTime()
    {
        return 0L;
    }

    /**
     * Since AutoCommitTransaction have no concept of a long lived transaction, any Actions registered
     * by the caller are executed immediately.
     */
    public void addPostTransactionAction(final Action immediateAction)
    {
        addFuture(MessageStore.IMMEDIATE_FUTURE, immediateAction);

    }

    public void dequeue(BaseQueue queue, EnqueableMessage message, Action postTransactionAction)
    {
        MessageStore.Transaction txn = null;
        try
        {
            MessageStore.StoreFuture future;
            if(message.isPersistent() && queue.isDurable())
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Dequeue of message number " + message.getMessageNumber() + " from transaction log. Queue : " + queue.getNameShortString());
                }

                txn = _messageStore.newTransaction();
                txn.dequeueMessage(queue, message);
                future = txn.commitTranAsync();
                
                txn = null;
            }
            else
            {
                future = MessageStore.IMMEDIATE_FUTURE;
            }
            addFuture(future, postTransactionAction);
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

    private void addFuture(final MessageStore.StoreFuture future, final Action action)
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

    public void dequeue(Collection<QueueEntry> queueEntries, Action postTransactionAction)
    {
        MessageStore.Transaction txn = null;
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
                        txn = _messageStore.newTransaction();
                    }

                    txn.dequeueMessage(queue, message);
                }

            }
            MessageStore.StoreFuture future;
            if(txn != null)
            {
                future = txn.commitTranAsync();
                txn = null;
            }
            else
            {
                future = MessageStore.IMMEDIATE_FUTURE;    
            }
            addFuture(future, postTransactionAction);
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
        MessageStore.Transaction txn = null;
        try
        {
            MessageStore.StoreFuture future;
            if(message.isPersistent() && queue.isDurable())
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Enqueue of message number " + message.getMessageNumber() + " to transaction log. Queue : " + queue.getNameShortString());
                }

                txn = _messageStore.newTransaction();
                txn.enqueueMessage(queue, message);
                future = txn.commitTranAsync();
                txn = null;
            }
            else
            {
                future = MessageStore.IMMEDIATE_FUTURE;
            }
            addFuture(future, postTransactionAction);
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

    public void enqueue(List<? extends BaseQueue> queues, EnqueableMessage message, Action postTransactionAction, long currentTime)
    {
        MessageStore.Transaction txn = null;
        try
        {

            if(message.isPersistent())
            {
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
                            txn = _messageStore.newTransaction();
                        }
                        
                        txn.enqueueMessage(queue, message);


                    }
                }
                
            }
            MessageStore.StoreFuture future;
            if (txn != null)
            {
                future = txn.commitTranAsync();
                txn = null;
            }
            else
            {
                future = MessageStore.IMMEDIATE_FUTURE;
            }
            addFuture(future, postTransactionAction);
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


    public void commit(final Runnable immediatePostTransactionAction)
    {
        if(immediatePostTransactionAction != null)
        {
            addFuture(MessageStore.IMMEDIATE_FUTURE, new Action()
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

    private void rollbackIfNecessary(Action postTransactionAction, MessageStore.Transaction txn)
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
