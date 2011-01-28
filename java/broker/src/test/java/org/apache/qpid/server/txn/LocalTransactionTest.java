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
 * A unit test ensuring that LocalTransactionTest creates a long-lived store transaction
 * that spans many dequeue/enqueue operations of enlistable messages.  Verifies
 * that the long-lived transaction is properly committed and rolled back, and that
 * post transaction actions are correctly fired.
 *
 */
public class LocalTransactionTest extends QpidTestCase
{
    private ServerTransaction _transaction = null;  // Class under test
    
    private AMQQueue _queue;
    private List<AMQQueue> _queues;
    private Collection<QueueEntry> _queueEntries;
    private ServerMessage _message;
    private MockAction _action1;
    private MockAction _action2;
    private MockStoreTransaction _storeTransaction;
    private TransactionLog _transactionLog;


    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        
        _storeTransaction = createTestStoreTransaction(false);
        _transactionLog = MockStoreTransaction.createTestTransactionLog(_storeTransaction);
        _action1 = new MockAction();
        _action2 = new MockAction();
        
        _transaction = new LocalTransaction(_transactionLog);
        
    }


    /**
     * Tests the enqueue of a non persistent message to a single non durable queue.
     * Asserts that a store transaction has not been started.
     */
    public void testEnqueueToNonDurableQueueOfNonPersistentMessage() throws Exception
    {
        _message = createTestMessage(false);
        _queue = createTestAMQQueue(false);
        
        _transaction.enqueue(_queue, _message, _action1);

        assertEquals("Enqueue of non-persistent message must not cause message to be enqueued", 0, _storeTransaction.getNumberOfEnqueuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
        assertNotFired(_action1);
    }

    /**
     * Tests the enqueue of a persistent message to a durable queue.
     * Asserts that a store transaction has been started.
     */
    public void testEnqueueToDurableQueueOfPersistentMessage() throws Exception
    {
        _message = createTestMessage(true);
        _queue = createTestAMQQueue(true);
        
        _transaction.enqueue(_queue, _message, _action1);

        assertEquals("Enqueue of persistent message to durable queue must cause message to be enqueued", 1, _storeTransaction.getNumberOfEnqueuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.STARTED, _storeTransaction.getState());
        assertNotFired(_action1);
    }

    /**
     * Tests the case where the store operation throws an exception.
     * Asserts that the transaction is aborted.
     */
    public void testStoreEnqueueCausesException() throws Exception
    {
        _message = createTestMessage(true);
        _queue = createTestAMQQueue(true);
        
        _storeTransaction = createTestStoreTransaction(true);
        _transactionLog = MockStoreTransaction.createTestTransactionLog(_storeTransaction);
        _transaction = new LocalTransaction(_transactionLog);
        
        try
        {
            _transaction.enqueue(_queue, _message, _action1);
            fail("Exception not thrown");
        }
        catch (RuntimeException re)
        {
            // PASS
        } 

        assertTrue("Rollback action must be fired", _action1.isRollbackActionFired());
        assertEquals("Unexpected transaction state", TransactionState.ABORTED, _storeTransaction.getState());
        
        assertFalse("Post commit action must not be fired", _action1.isPostCommitActionFired());
        
    }
    
    /**
     * Tests the enqueue of a non persistent message to a many non durable queues.
     * Asserts that a store transaction has not been started.
     */
    public void testEnqueueToManyNonDurableQueuesOfNonPersistentMessage() throws Exception
    {
        _message = createTestMessage(false);
        _queues = createTestBaseQueues(new boolean[] {false, false, false});
        
        _transaction.enqueue(_queues, _message, _action1);

        assertEquals("Enqueue of non-persistent message must not cause message to be enqueued", 0, _storeTransaction.getNumberOfEnqueuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
        assertNotFired(_action1);
    }
    
    /**
     * Tests the enqueue of a persistent message to a many non durable queues.
     * Asserts that a store transaction has not been started.
     */
    public void testEnqueueToManyNonDurableQueuesOfPersistentMessage() throws Exception
    {
        _message = createTestMessage(true);
        _queues = createTestBaseQueues(new boolean[] {false, false, false});
        
        _transaction.enqueue(_queues, _message, _action1);
  
        assertEquals("Enqueue of persistent message to non-durable queues must not cause message to be enqueued", 0, _storeTransaction.getNumberOfEnqueuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
        assertNotFired(_action1);

    }

    /**
     * Tests the enqueue of a persistent message to many queues, some durable others not.
     * Asserts that a store transaction has been started.
     */
    public void testEnqueueToDurableAndNonDurableQueuesOfPersistentMessage() throws Exception
    {
        _message = createTestMessage(true);
        _queues = createTestBaseQueues(new boolean[] {false, true, false, true});
        
        _transaction.enqueue(_queues, _message, _action1);

        assertEquals("Enqueue of persistent message to durable/non-durable queues must cause messages to be enqueued", 2, _storeTransaction.getNumberOfEnqueuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.STARTED, _storeTransaction.getState());
        assertNotFired(_action1);

    }

    /**
     * Tests the case where the store operation throws an exception.
     * Asserts that the transaction is aborted.
     */
    public void testStoreEnqueuesCausesExceptions() throws Exception
    {
        _message = createTestMessage(true);
        _queues = createTestBaseQueues(new boolean[] {true, true});
        
        _storeTransaction = createTestStoreTransaction(true);
        _transactionLog = MockStoreTransaction.createTestTransactionLog(_storeTransaction);
        _transaction = new LocalTransaction(_transactionLog);
        
        try
        {
            _transaction.enqueue(_queues, _message, _action1);
            fail("Exception not thrown");
        }
        catch (RuntimeException re)
        {
            // PASS
        }

        assertTrue("Rollback action must be fired", _action1.isRollbackActionFired());
        assertEquals("Unexpected transaction state", TransactionState.ABORTED, _storeTransaction.getState());
        assertFalse("Post commit action must not be fired", _action1.isPostCommitActionFired());
    }

    /**
     * Tests the dequeue of a non persistent message from a single non durable queue.
     * Asserts that a store transaction has not been started.
     */
    public void testDequeueFromNonDurableQueueOfNonPersistentMessage() throws Exception
    {
        _message = createTestMessage(false);
        _queue = createTestAMQQueue(false);
        
        _transaction.dequeue(_queue, _message, _action1);

        assertEquals("Dequeue of non-persistent message must not cause message to be enqueued", 0, _storeTransaction.getNumberOfEnqueuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
        assertNotFired(_action1);

    }

    /**
     * Tests the dequeue of a persistent message from a single non durable queue.
     * Asserts that a store transaction has not been started.
     */
    public void testDequeueFromDurableQueueOfPersistentMessage() throws Exception
    {
        _message = createTestMessage(true);
        _queue = createTestAMQQueue(true);
        
        _transaction.dequeue(_queue, _message, _action1);

        assertEquals("Dequeue of non-persistent message must cause message to be dequeued", 1, _storeTransaction.getNumberOfDequeuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.STARTED, _storeTransaction.getState());
        assertNotFired(_action1);
    }

    /**
     * Tests the case where the store operation throws an exception.
     * Asserts that the transaction is aborted.
     */
    public void testStoreDequeueCausesException() throws Exception
    {
        _message = createTestMessage(true);
        _queue = createTestAMQQueue(true);
        
        _storeTransaction = createTestStoreTransaction(true);
        _transactionLog = MockStoreTransaction.createTestTransactionLog(_storeTransaction);
        _transaction = new LocalTransaction(_transactionLog);
        
        try
        {
            _transaction.dequeue(_queue, _message, _action1);
            fail("Exception not thrown");
        }
        catch (RuntimeException re)
        {
            // PASS
        }        
        
        assertTrue("Rollback action must be fired", _action1.isRollbackActionFired());
        assertEquals("Unexpected transaction state", TransactionState.ABORTED, _storeTransaction.getState());
        assertFalse("Post commit action must not be fired", _action1.isPostCommitActionFired());

    }

    /**
     * Tests the dequeue of a non persistent message from many non durable queues.
     * Asserts that a store transaction has not been started.
     */
    public void testDequeueFromManyNonDurableQueuesOfNonPersistentMessage() throws Exception
    {
        _queueEntries = createTestQueueEntries(new boolean[] {false, false, false}, new boolean[] {false, false, false});
        
        _transaction.dequeue(_queueEntries, _action1);

        assertEquals("Dequeue of non-persistent messages must not cause message to be dequeued", 0, _storeTransaction.getNumberOfDequeuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
        assertNotFired(_action1);
  
    }
    
    /**
     * Tests the dequeue of a persistent message from a many non durable queues.
     * Asserts that a store transaction has not been started.
     */
    public void testDequeueFromManyNonDurableQueuesOfPersistentMessage() throws Exception
    {
        _queueEntries = createTestQueueEntries(new boolean[] {false, false, false}, new boolean[] {true, true, true});
        
        _transaction.dequeue(_queueEntries, _action1);

        assertEquals("Dequeue of persistent message from non-durable queues must not cause message to be enqueued", 0, _storeTransaction.getNumberOfDequeuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
        assertNotFired(_action1);
    }

    /**
     * Tests the dequeue of a persistent message from many queues, some durable others not.
     * Asserts that a store transaction has not been started.
     */
    public void testDequeueFromDurableAndNonDurableQueuesOfPersistentMessage() throws Exception
    {
        // A transaction will exist owing to the 1st and 3rd.
        _queueEntries = createTestQueueEntries(new boolean[] {true, false, true, true}, new boolean[] {true, true, true, false});
        
        _transaction.dequeue(_queueEntries, _action1);

        assertEquals("Dequeue of persistent messages from durable/non-durable queues must cause messages to be dequeued", 2, _storeTransaction.getNumberOfDequeuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.STARTED, _storeTransaction.getState());
        assertNotFired(_action1);
    }
    
    /**
     * Tests the case where the store operation throws an exception.
     * Asserts that the transaction is aborted.
     */
    public void testStoreDequeuesCauseExceptions() throws Exception
    {
        // Transactions will exist owing to the 1st and 3rd queue entries in the collection
        _queueEntries = createTestQueueEntries(new boolean[] {true}, new boolean[] {true});
        
        _storeTransaction = createTestStoreTransaction(true);
        _transactionLog = MockStoreTransaction.createTestTransactionLog(_storeTransaction);
        _transaction = new LocalTransaction(_transactionLog);
        
        try
        {
            _transaction.dequeue(_queueEntries, _action1);
            fail("Exception not thrown");
        }
        catch (RuntimeException re)
        {
            // PASS
        }

        assertEquals("Unexpected transaction state", TransactionState.ABORTED, _storeTransaction.getState());
        assertTrue("Rollback action must be fired", _action1.isRollbackActionFired());
        assertFalse("Post commit action must not be fired",  _action1.isPostCommitActionFired());
    }
    
    /** 
     * Tests the add of a post-commit action.  Unlike AutoCommitTranctions, the post transaction actions 
     * is added to a list to be fired on commit or rollback.
     */
    public void testAddingPostCommitActionNotFiredImmediately() throws Exception
    {
        
        _transaction.addPostTransactionAction(_action1);

        assertNotFired(_action1);
    }
    
    
    /**
     * Tests committing a transaction without work accepted without error and without causing store
     * enqueues or dequeues.
     */
    public void testCommitNoWork() throws Exception
    {
        
        _transaction.commit();
        
        assertEquals("Unexpected number of store dequeues", 0, _storeTransaction.getNumberOfDequeuedMessages());
        assertEquals("Unexpected number of store enqueues", 0, _storeTransaction.getNumberOfEnqueuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
    }
    
    /**
     * Tests rolling back a transaction without work accepted without error and without causing store
     * enqueues or dequeues.
     */
    public void testRollbackNoWork() throws Exception
    {
        
        _transaction.rollback();

        assertEquals("Unexpected number of store dequeues", 0, _storeTransaction.getNumberOfDequeuedMessages());
        assertEquals("Unexpected number of store enqueues", 0, _storeTransaction.getNumberOfEnqueuedMessages());
        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
    }
    
    /** 
     * Tests the dequeuing of a message with a commit.  Test ensures that the underlying store transaction is 
     * correctly controlled and the post commit action is fired.
     */
    public void testCommitWork() throws Exception
    {
        
        _message = createTestMessage(true);
        _queue = createTestAMQQueue(true);

        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
        assertFalse("Post commit action must not be fired yet", _action1.isPostCommitActionFired());
        
        _transaction.dequeue(_queue, _message, _action1);
        assertEquals("Unexpected transaction state", TransactionState.STARTED, _storeTransaction.getState());
        assertFalse("Post commit action must not be fired yet", _action1.isPostCommitActionFired());
        
        _transaction.commit();
        
        assertEquals("Unexpected transaction state", TransactionState.COMMITTED, _storeTransaction.getState());
        assertTrue("Post commit action must be fired", _action1.isPostCommitActionFired());
    }
    
    /** 
     * Tests the dequeuing of a message with a rollback.  Test ensures that the underlying store transaction is 
     * correctly controlled and the post rollback action is fired.
     */
    public void testRollbackWork() throws Exception
    {
        
        _message = createTestMessage(true);
        _queue = createTestAMQQueue(true);


        assertEquals("Unexpected transaction state", TransactionState.NOT_STARTED, _storeTransaction.getState());
        assertFalse("Rollback action must not be fired yet", _action1.isRollbackActionFired());

        _transaction.dequeue(_queue, _message, _action1);
        
        assertEquals("Unexpected transaction state", TransactionState.STARTED, _storeTransaction.getState());
        assertFalse("Rollback action must not be fired yet", _action1.isRollbackActionFired());

        _transaction.rollback();
        
        assertEquals("Unexpected transaction state", TransactionState.ABORTED, _storeTransaction.getState());
        assertTrue("Rollback action must be fired", _action1.isRollbackActionFired());

    }
    
    /**
     * Variation of testCommitWork with an additional post transaction action.
     * 
     */
    public void testCommitWorkWithAdditionalPostAction() throws Exception
    {
        
        _message = createTestMessage(true);
        _queue = createTestAMQQueue(true);
        
        _transaction.addPostTransactionAction(_action1);
        _transaction.dequeue(_queue, _message, _action2);
        _transaction.commit();
        
        assertEquals("Unexpected transaction state", TransactionState.COMMITTED, _storeTransaction.getState());

        assertTrue("Post commit action1 must be fired", _action1.isPostCommitActionFired());
        assertTrue("Post commit action2 must be fired", _action2.isPostCommitActionFired());
        
        assertFalse("Rollback action1 must not be fired", _action1.isRollbackActionFired());
        assertFalse("Rollback action2 must not be fired", _action1.isRollbackActionFired());
    }

    /**
     * Variation of testRollbackWork with an additional post transaction action.
     * 
     */
    public void testRollbackWorkWithAdditionalPostAction() throws Exception
    {
        
        _message = createTestMessage(true);
        _queue = createTestAMQQueue(true);
        
        _transaction.addPostTransactionAction(_action1);
        _transaction.dequeue(_queue, _message, _action2);
        _transaction.rollback();
        
        assertEquals("Unexpected transaction state", TransactionState.ABORTED, _storeTransaction.getState());

        assertFalse("Post commit action1 must not be fired", _action1.isPostCommitActionFired());
        assertFalse("Post commit action2 must not be fired", _action2.isPostCommitActionFired());
        
        assertTrue("Rollback action1 must be fired", _action1.isRollbackActionFired());
        assertTrue("Rollback action2 must be fired", _action1.isRollbackActionFired());
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

    private void assertNotFired(MockAction action)
    {
        assertFalse("Rollback action must not be fired", action.isRollbackActionFired());
        assertFalse("Post commit action must not be fired", action.isPostCommitActionFired());
    }

}
