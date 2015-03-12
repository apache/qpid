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

import org.apache.commons.lang.NotImplementedException;
import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.NullMessageStore;
import org.apache.qpid.server.util.FutureResult;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

/**
 * Mock implementation of a (Store) Transaction allow its state to be observed.
 * Also provide a factory method to produce TestTransactionLog objects suitable
 * for unit test use.
 *
 */
class MockStoreTransaction implements Transaction
{
    enum TransactionState {NOT_STARTED, STARTED, COMMITTED, ABORTED};

    private TransactionState _state = TransactionState.NOT_STARTED;

    private int _numberOfEnqueuedMessages = 0;
    private int _numberOfDequeuedMessages = 0;
    private boolean _throwExceptionOnQueueOp;

    public MockStoreTransaction(boolean throwExceptionOnQueueOp)
    {
        _throwExceptionOnQueueOp = throwExceptionOnQueueOp;
    }

    public void setState(TransactionState state)
    {
        _state = state;
    }

    public TransactionState getState()
    {
        return _state;
    }

    public void enqueueMessage(TransactionLogResource queue, EnqueueableMessage message)
    {
        if (_throwExceptionOnQueueOp)
        {

            throw new ServerScopedRuntimeException("Mocked exception");
        }

        _numberOfEnqueuedMessages++;
    }

    public int getNumberOfDequeuedMessages()
    {
        return _numberOfDequeuedMessages;
    }

    public int getNumberOfEnqueuedMessages()
    {
        return _numberOfEnqueuedMessages;
    }

    public void dequeueMessage(TransactionLogResource queue, EnqueueableMessage message)
    {
        if (_throwExceptionOnQueueOp)
        {
            throw new ServerScopedRuntimeException("Mocked exception");
        }

        _numberOfDequeuedMessages++;
    }

    public void commitTran()
    {
        _state = TransactionState.COMMITTED;
    }

    public FutureResult commitTranAsync()
    {
        throw new NotImplementedException();
    }

    public void abortTran()
    {
        _state = TransactionState.ABORTED;
    }

    public void removeXid(long format, byte[] globalId, byte[] branchId)
    {
    }

    public void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues, Record[] dequeues)
    {
    }

    public static MessageStore createTestTransactionLog(final MockStoreTransaction storeTransaction)
    {
        return new NullMessageStore()
        {
            @Override
            public Transaction newTransaction()
            {
                storeTransaction.setState(TransactionState.STARTED);
                return storeTransaction;
            }
       };
    }
}
