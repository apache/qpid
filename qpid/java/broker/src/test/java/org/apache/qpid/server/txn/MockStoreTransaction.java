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

import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.NotImplementedException;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.store.*;
import org.apache.qpid.server.store.MessageStore.StoreFuture;
import org.apache.qpid.server.store.MessageStore.Transaction;

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

    public void enqueueMessage(TransactionLogResource queue, EnqueableMessage message) throws AMQStoreException
    {
        if (_throwExceptionOnQueueOp)
        {
            
            throw new AMQStoreException("Mocked exception");
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

    public void dequeueMessage(TransactionLogResource queue, EnqueableMessage message) throws AMQStoreException
    {
        if (_throwExceptionOnQueueOp)
        {
            throw new AMQStoreException("Mocked exception");
        }
        
        _numberOfDequeuedMessages++;
    }

    public void commitTran() throws AMQStoreException
    {
        _state = TransactionState.COMMITTED;
    }

    public StoreFuture commitTranAsync() throws AMQStoreException
    {
        throw new NotImplementedException();
    }

    public void abortTran() throws AMQStoreException
    {
        _state = TransactionState.ABORTED;
    }

    public static MessageStore createTestTransactionLog(final MockStoreTransaction storeTransaction)
    {
        return new MessageStore()
        {
            public void configureMessageStore(final String name,
                                              final MessageStoreRecoveryHandler recoveryHandler,
                                              final Configuration config,
                                              final LogSubject logSubject) throws Exception
            {
                //TODO.
            }

            public void close() throws Exception
            {
                //TODO.
            }

            public <T extends StorableMessageMetaData> StoredMessage<T> addMessage(final T metaData)
            {
                return null;  //TODO.
            }

            public boolean isPersistent()
            {
                return false;  //TODO.
            }

            public void configureTransactionLog(String name, TransactionLogRecoveryHandler recoveryHandler,
                    Configuration storeConfiguration, LogSubject logSubject) throws Exception
            {
            }

            public Transaction newTransaction()
            {
                storeTransaction.setState(TransactionState.STARTED);
                return storeTransaction;
            }
            
        };
    }
}