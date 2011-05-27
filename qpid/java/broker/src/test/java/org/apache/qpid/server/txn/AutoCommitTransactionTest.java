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

import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MockAMQQueue;
import org.apache.qpid.server.queue.MockQueueEntry;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.store.TransactionLog;
import org.apache.qpid.server.txn.MockStoreTransaction.TransactionState;
import org.apache.qpid.test.utils.QpidTestCase;

/**
 * A unit test ensuring that AutoCommitTransaction creates a separate transaction for
 * each dequeue/enqueue operation that involves enlistable messages. Verifies
 * that the transaction is properly committed (or rolled-back in the case of exception),
 * and that post transaction actions are correctly fired.
 *
 */
public class AutoCommitTransactionTest extends QpidTestCase
{
    private ServerTransaction _transaction = null;  // Class under test
    
    private TransactionLog _transactionLog;
    private AMQQueue _queue;
    private List<AMQQueue> _queues;
    private Collection<QueueEntry> _queueEntries;
    private ServerMessage _message;
    private MockAction _action;
    private MockStoreTransaction _storeTransaction;


    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        
        _storeTransaction = createTestStoreTransaction(false);
        _transactionLog = MockStoreTransaction.createTestTransactionLog(_storeTransaction);
        _action = new MockAction();
        
        _transaction = new AutoCommitTransaction(_transactionLog);
    }

    /**
     * Tests the enqueue of a non persistent message to a single non durable queue.
     * Asserts that a store transaction has not been started and commit action fired.
     */
    public void testEnqueueToNonDurableQueueOfNonPersistentMessage() throws Exception
    {
        _message = createTestMessage(false);
        _queue = createTestAMQQueue(false);
        
        _transaction.enqueue(_queue, _message, _action);

        assertEquals("Enqueue of non-persistent message must not cause message to be enqueued", 0, _storeTransaction.getNumberOfEnqueuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
        assertFalse("Rollback action must not be fired", _action.isRollbackActionFired());
        assertTrue("Post commit action must be fired",  _action.isPostCommitActionFired());
        
    }

    /**
     * Tests the enqueue of a persistent message to a durable queue.
     * Asserts that a store transaction has been committed and commit action fired.
     */
    public void testEnqueueToDurableQueueOfPersistentMessage() throws Exception
    {
        _message = createTestMessage(true);
        _queue = createTestAMQQueue(true);
        
        _transaction.enqueue(_queue, _message, _action);

        assertEquals("Enqueue of persistent message to durable queue must cause message to be enqueued", 1, _storeTransaction.getNumberOfEnqueuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.COMMITTED, _storeTransaction.getState());
        assertFalse("Rollback action must not be fired", _action.isRollbackActionFired());
        assertTrue("Post commit action must be fired", _action.isPostCommitActionFired());
    }

    /**
     * Tests the case where the store operation throws an exception.
     * Asserts that the transaction is aborted and rollback action is fired.
     */
    public void testStoreEnqueueCausesException() throws Exception
    {
        _message = createTestMessage(true);
        _queue = createTestAMQQueue(true);
        
        _storeTransaction = createTestStoreTransaction(true);
        _transactionLog = MockStoreTransaction.createTestTransactionLog(_storeTransaction);
        _transaction = new AutoCommitTransaction(_transactionLog);
        
        try
        {
            _transaction.enqueue(_queue, _message, _action);
            fail("Exception not thrown");
        }
        catch (RuntimeException re)
        {
            // PASS
        }

        assertEquals("Unexpected transaction state", TransactionState.ABORTED, _storeTransaction.getState());
        assertTrue("Rollback action must be fired", _action.isRollbackActionFired());
        assertFalse("Post commit action must be fired", _action.isPostCommitActionFired());
    }
    
    /**
     * Tests the enqueue of a non persistent message to a many non durable queues.
     * Asserts that a store transaction has not been started and post commit action fired.
     */
    public void testEnqueueToManyNonDurableQueuesOfNonPersistentMessage() throws Exception
    {
        _message = createTestMessage(false);
        _queues = createTestBaseQueues(new boolean[] {false, false, false});
        
        _transaction.enqueue(_queues, _message, _action);

        assertEquals("Enqueue of non-persistent message must not cause message to be enqueued", 0, _storeTransaction.getNumberOfEnqueuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
        assertFalse("Rollback action must not be fired", _action.isRollbackActionFired());
        assertTrue("Post commit action must be fired", _action.isPostCommitActionFired());
  
    }
    
    
    /**
     * Tests the enqueue of a persistent message to a many non durable queues.
     * Asserts that a store transaction has not been started and post commit action
     * fired.
     */
    public void testEnqueueToManyNonDurableQueuesOfPersistentMessage() throws Exception
    {
        _message = createTestMessage(true);
        _queues = createTestBaseQueues(new boolean[] {false, false, false});
        
        _transaction.enqueue(_queues, _message, _action);

        assertEquals("Enqueue of persistent message to non-durable queues must not cause message to be enqueued", 0, _storeTransaction.getNumberOfEnqueuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
        assertFalse("Rollback action must not be fired", _action.isRollbackActionFired());
        assertTrue("Post commit action must be fired", _action.isPostCommitActionFired());
  
    }

    /**
     * Tests the enqueue of a persistent message to many queues, some durable others not.
     * Asserts that a store transaction has been committed and post commit action fired.
     */
    public void testEnqueueToDurableAndNonDurableQueuesOfPersistentMessage() throws Exception
    {
        _message = createTestMessage(true);
        _queues = createTestBaseQueues(new boolean[] {false, true, false, true});
        
        _transaction.enqueue(_queues, _message, _action);

        assertEquals("Enqueue of persistent message to durable/non-durable queues must cause messages to be enqueued", 2, _storeTransaction.getNumberOfEnqueuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.COMMITTED, _storeTransaction.getState());
        assertFalse("Rollback action must not be fired",  _action.isRollbackActionFired());
        assertTrue("Post commit action must be fired", _action.isPostCommitActionFired());
    }

    /**
     * Tests the case where the store operation throws an exception.
     * Asserts that the transaction is aborted and rollback action fired.
     */
    public void testStoreEnqueuesCausesExceptions() throws Exception
    {
        _message = createTestMessage(true);
        _queues = createTestBaseQueues(new boolean[] {true, true});
        
        _storeTransaction = createTestStoreTransaction(true);
        _transactionLog = MockStoreTransaction.createTestTransactionLog(_storeTransaction);
        _transaction = new AutoCommitTransaction(_transactionLog);
        
        try
        {
            _transaction.enqueue(_queues, _message, _action);
            fail("Exception not thrown");
        }
        catch (RuntimeException re)
        {
            // PASS
        }

        assertEquals("Unexpected transaction state", TransactionState.ABORTED, _storeTransaction.getState());
        assertTrue("Rollback action must be fired", _action.isRollbackActionFired());
        assertFalse("Post commit action must not be fired",  _action.isPostCommitActionFired());
    }
    
    /**
     * Tests the dequeue of a non persistent message from a single non durable queue.
     * Asserts that a store transaction has not been started and post commit action
     * fired.
     */
    public void testDequeueFromNonDurableQueueOfNonPersistentMessage() throws Exception
    {
        _message = createTestMessage(false);
        _queue = createTestAMQQueue(false);
        
        _transaction.dequeue(_queue, _message, _action);

        assertEquals("Dequeue of non-persistent message must not cause message to be dequeued", 0, _storeTransaction.getNumberOfDequeuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
        assertFalse("Rollback action must not be fired", _action.isRollbackActionFired());
        assertTrue("Post commit action must be fired", _action.isPostCommitActionFired());
        
    }

    /**
     * Tests the dequeue of a persistent message from a single non durable queue.
     * Asserts that a store transaction has not been started and post commit
     * action fired.
     */
    public void testDequeueFromDurableQueueOfPersistentMessage() throws Exception
    {
        _message = createTestMessage(true);
        _queue = createTestAMQQueue(true);
        
        _transaction.dequeue(_queue, _message, _action);

        assertEquals("Dequeue of persistent message to durable queue must cause message to be dequeued",1, _storeTransaction.getNumberOfDequeuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.COMMITTED, _storeTransaction.getState());
        assertFalse("Rollback action must not be fired", _action.isRollbackActionFired());
        assertTrue("Post commit action must be fired", _action.isPostCommitActionFired());
    }

    /**
     * Tests the case where the store operation throws an exception.
     * Asserts that the transaction is aborted and post rollback action
     * fired.
     */
    public void testStoreDequeueCausesException() throws Exception
    {
        _message = createTestMessage(true);
        _queue = createTestAMQQueue(true);
        
        _storeTransaction = createTestStoreTransaction(true);
        _transactionLog = MockStoreTransaction.createTestTransactionLog(_storeTransaction);
        _transaction = new AutoCommitTransaction(_transactionLog);
        
        try
        {
            _transaction.dequeue(_queue, _message, _action);
            fail("Exception not thrown");
        }
        catch (RuntimeException re)
        {
            // PASS
        }

        assertEquals("Unexpected transaction state", TransactionState.ABORTED, _storeTransaction.getState());
        
        assertTrue("Rollback action must be fired", _action.isRollbackActionFired());
        assertFalse("Post commit action must not be fired", _action.isPostCommitActionFired());
    }

    /**
     * Tests the dequeue of a non persistent message from many non durable queues.
     * Asserts that a store transaction has not been started and post commit action
     * fired.
     */
    public void testDequeueFromManyNonDurableQueuesOfNonPersistentMessage() throws Exception
    {
        _queueEntries = createTestQueueEntries(new boolean[] {false, false, false}, new boolean[] {false, false, false});
        
        _transaction.dequeue(_queueEntries, _action);

        assertEquals("Dequeue of non-persistent messages must not cause message to be dequeued", 0, _storeTransaction.getNumberOfDequeuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
        assertEquals("Rollback action must not be fired", false, _action.isRollbackActionFired());
        assertEquals("Post commit action must be fired", true, _action.isPostCommitActionFired());
  
    }
    
    
    /**
     * Tests the dequeue of a persistent message from a many non durable queues.
     * Asserts that a store transaction has not been started and post commit action
     * fired.
     */
    public void testDequeueFromManyNonDurableQueuesOfPersistentMessage() throws Exception
    {
        _queueEntries = createTestQueueEntries(new boolean[] {false, false, false}, new boolean[] {true, true, true});
        
        _transaction.dequeue(_queueEntries, _action);

        assertEquals("Dequeue of persistent message from non-durable queues must not cause message to be enqueued", 0, _storeTransaction.getNumberOfDequeuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
        assertFalse("Rollback action must not be fired", _action.isRollbackActionFired());
        assertTrue("Post commit action must be fired", _action.isPostCommitActionFired());
    }

    /**
     * Tests the dequeue of a persistent message from many queues, some durable others not.
     * Asserts that a store transaction has not been started and post commit action fired.
     */
    public void testDequeueFromDurableAndNonDurableQueuesOfPersistentMessage() throws Exception
    {
        // A transaction will exist owing to the 1st and 3rd.
        _queueEntries = createTestQueueEntries(new boolean[] {true, false, true, true}, new boolean[] {true, true, true, false});
        
        _transaction.dequeue(_queueEntries, _action);

        assertEquals("Dequeue of persistent messages from durable/non-durable queues must cause messages to be dequeued", 2, _storeTransaction.getNumberOfDequeuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.COMMITTED, _storeTransaction.getState());
        assertFalse("Rollback action must not be fired",  _action.isRollbackActionFired());
        assertTrue("Post commit action must be fired", _action.isPostCommitActionFired());
    }
    
    /**
     * Tests the case where the store operation throws an exception.
     * Asserts that the transaction is aborted and post rollback action fired.
     */
    public void testStoreDequeuesCauseExceptions() throws Exception
    {
        // Transactions will exist owing to the 1st and 3rd queue entries in the collection
        _queueEntries = createTestQueueEntries(new boolean[] {true}, new boolean[] {true});
        
        _storeTransaction = createTestStoreTransaction(true);
        _transactionLog = MockStoreTransaction.createTestTransactionLog(_storeTransaction);
        _transaction = new AutoCommitTransaction(_transactionLog);
        
        try
        {
            _transaction.dequeue(_queueEntries, _action);
            fail("Exception not thrown");
        }
        catch (RuntimeException re)
        {
            // PASS
        }

        assertEquals("Unexpected transaction state", TransactionState.ABORTED, _storeTransaction.getState());
        
        assertTrue("Rollback action must be fired", _action.isRollbackActionFired());
        assertFalse("Post commit action must not be fired",  _action.isPostCommitActionFired());
    }
    
    /** 
     * Tests the add of a post-commit action.  Since AutoCommitTranctions
     * have no long lived transactions, the post commit action is fired immediately.
     */
    public void testPostCommitActionFiredImmediately() throws Exception
    {
        
        _transaction.addPostTransactionAction(_action);

        assertTrue("Post commit action must be fired",  _action.isPostCommitActionFired());
        assertFalse("Rollback action must be fired",  _action.isRollbackActionFired());
    }  
    
    private Collection<QueueEntry> createTestQueueEntries(boolean[] queueDurableFlags, boolean[] messagePersistentFlags)
    {
        Collection<QueueEntry> queueEntries = new ArrayList<QueueEntry>();
        
        assertTrue("Boolean arrays must be the same length", queueDurableFlags.length == messagePersistentFlags.length);
        
        for(int i = 0; i < queueDurableFlags.length; i++)
        {
            final AMQQueue queue = createTestAMQQueue(queueDurableFlags[i]);
            final ServerMessage message = createTestMessage(messagePersistentFlags[i]);
            
            queueEntries.add(new MockQueueEntry()
            {

                @Override
                public ServerMessage getMessage()
                {
                    return message;
                }

                @Override
                public AMQQueue getQueue()
                {
                    return queue;
                }
                
            });
        }
        
        return queueEntries;
    }

    private MockStoreTransaction createTestStoreTransaction(boolean throwException)
    {
        return new MockStoreTransaction(throwException);
    }

    private List<AMQQueue> createTestBaseQueues(boolean[] durableFlags)
    {
        List<AMQQueue> queues = new ArrayList<AMQQueue>();
        for (boolean b: durableFlags)
        {
            queues.add(createTestAMQQueue(b));
        }
        
        return queues;
    }

    private AMQQueue createTestAMQQueue(final boolean durable)
    {
        return new MockAMQQueue("mockQueue")
        {
            @Override
            public boolean isDurable()
            {
                return durable;
            }
            
        };
    }

    private ServerMessage createTestMessage(final boolean persistent)
    {
        return new MockServerMessage(persistent);
    }
    
}
