package org.apache.qpid.server.txn;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.store.TransactionLog;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.store.TransactionLog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private volatile TransactionLog.Transaction _transaction;
    private TransactionLog _transactionLog;
    private long _txnStartTime = 0L;

    public LocalTransaction(TransactionLog transactionLog)
    {
        _transactionLog = transactionLog;
    }
    
    public boolean inTransaction()
    {
        return _transaction != null;
    }
    
    public long getTransactionStartTime()
    {
        return _txnStartTime;
    }

    public void addPostTransactionAction(Action postTransactionAction)
    {
        _postTransactionActions.add(postTransactionAction);
    }

    public void dequeue(BaseQueue queue, EnqueableMessage message, Action postTransactionAction)
    {
        _postTransactionActions.add(postTransactionAction);

        if(message.isPersistent() && queue.isDurable())
        {
            try
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Dequeue of message number " + message.getMessageNumber() + " from transaction log. Queue : " + queue.getNameShortString());
                }

                beginTranIfNecessary();
                _transaction.dequeueMessage(queue, message.getMessageNumber());

            }
            catch(AMQException e)
            {
                _logger.error("Error during message dequeues", e);
                tidyUpOnError(e);
            }
        }
    }

    public void dequeue(Collection<QueueEntry> queueEntries, Action postTransactionAction)
    {
        _postTransactionActions.add(postTransactionAction);

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

                    beginTranIfNecessary();
                    _transaction.dequeueMessage(queue, message.getMessageNumber());
                }

            }
        }
        catch(AMQException e)
        {
            _logger.error("Error during message dequeues", e);
            tidyUpOnError(e);
        }
    }

    private void tidyUpOnError(Exception e)
    {
        try
        {
            for(Action action : _postTransactionActions)
            {
                action.onRollback();
            }
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
            catch (Exception abortException)
            {
                _logger.error("Abort transaction failed while trying to handle previous error", abortException);
            }
            finally
            {
		resetDetails();
            }
        }

        throw new RuntimeException(e);
    }

    private void beginTranIfNecessary()
    {

        if(_transaction == null)
        {
            try
            {
                _transaction = _transactionLog.newTransaction();
            }
            catch (Exception e)
            {
                tidyUpOnError(e);
            }
        }
    }

    public void enqueue(BaseQueue queue, EnqueableMessage message, Action postTransactionAction)
    {
        _postTransactionActions.add(postTransactionAction);

        if(message.isPersistent() && queue.isDurable())
        {
            try
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Enqueue of message number " + message.getMessageNumber() + " to transaction log. Queue : " + queue.getNameShortString());
                }
                
                beginTranIfNecessary();
                _transaction.enqueueMessage(queue, message.getMessageNumber());
            }
            catch (Exception e)
            {
                _logger.error("Error during message enqueue", e);

                tidyUpOnError(e);
            }
        }
    }

    public void enqueue(List<? extends BaseQueue> queues, EnqueableMessage message, Action postTransactionAction)
    {
        _postTransactionActions.add(postTransactionAction);

        if (_txnStartTime == 0L)
        {
            _txnStartTime = System.currentTimeMillis();
        }

        if(message.isPersistent())
        {
            try
            {
                for(BaseQueue queue : queues)
                {
                    if(queue.isDurable())
                    {
                        if (_logger.isDebugEnabled())
                        {
                            _logger.debug("Enqueue of message number " + message.getMessageNumber() + " to transaction log. Queue : " + queue.getNameShortString() );
                        }
                        
                        
                        beginTranIfNecessary();
                        _transaction.enqueueMessage(queue, message.getMessageNumber());
                    }
                }

            }
            catch (Exception e)
            {
                _logger.error("Error during message enqueue", e);

                tidyUpOnError(e);
            }
        }
    }

    public void commit()
    {
        try
        {
            if(_transaction != null)
            {
                _transaction.commitTran();
            }

            for(Action action : _postTransactionActions)
            {
                action.postCommit();
            }
        }
        catch (Exception e)
        {
            _logger.error("Failed to commit transaction", e);

            for(Action action : _postTransactionActions)
            {
                action.onRollback();
            }
            throw new RuntimeException("Failed to commit transaction", e);
        }
        finally
        {
            resetDetails();
        }
    }

    public void rollback()
    {
        try
        {
            if(_transaction != null)
            {
                _transaction.abortTran();
            }
        }
        catch (AMQException e)
        {
            _logger.error("Failed to rollback transaction", e);
            throw new RuntimeException("Failed to rollback transaction", e);
        }
        finally
        {
            try
            {
                for(Action action : _postTransactionActions)
                {
                    action.onRollback();
                }
            }
            finally
            {
                resetDetails();
            }
        }
    }
    
    private void resetDetails()
    {
        _transaction = null;
	_postTransactionActions.clear();
        _txnStartTime = 0L;
    }
}
