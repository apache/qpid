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
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.federation.Bridge;
import org.apache.qpid.server.federation.BrokerLink;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.store.TransactionLogRecoveryHandler;
import org.apache.qpid.server.store.TransactionLogResource;

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

    public void removeXid(long format, byte[] globalId, byte[] branchId)
    {
    }

    public void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues, Record[] dequeues)
    {
    }

    public static MessageStore createTestTransactionLog(final MockStoreTransaction storeTransaction)
    {
        return new MessageStore()
        {
            public void configureMessageStore(final String name,
                                              final MessageStoreRecoveryHandler recoveryHandler,
                                              TransactionLogRecoveryHandler tlogRecoveryHandler,
                                              final Configuration config, final LogSubject logSubject) throws Exception
            {
            }

            public void close() throws Exception
            {
            }

            public <T extends StorableMessageMetaData> StoredMessage<T> addMessage(final T metaData)
            {
                return null;
            }

            public boolean isPersistent()
            {
                return false;
            }

            public Transaction newTransaction()
            {
                storeTransaction.setState(TransactionState.STARTED);
                return storeTransaction;
            }

            @Override
            public void configureConfigStore(String name,
                    ConfigurationRecoveryHandler recoveryHandler,
                    Configuration config, LogSubject logSubject)
                    throws Exception
            {
            }

            @Override
            public void createExchange(Exchange exchange)
                    throws AMQStoreException
            {
            }

            @Override
            public void removeExchange(Exchange exchange)
                    throws AMQStoreException
            {
            }

            @Override
            public void bindQueue(Exchange exchange, AMQShortString routingKey,
                    AMQQueue queue, FieldTable args) throws AMQStoreException
            {
            }

            @Override
            public void unbindQueue(Exchange exchange,
                    AMQShortString routingKey, AMQQueue queue, FieldTable args)
                    throws AMQStoreException
            {
            }

            @Override
            public void createQueue(AMQQueue queue) throws AMQStoreException
            {
            }

            @Override
            public void createQueue(AMQQueue queue, FieldTable arguments)
                    throws AMQStoreException
            {
            }

            @Override
            public void removeQueue(AMQQueue queue) throws AMQStoreException
            {
            }

            @Override
            public void updateQueue(AMQQueue queue) throws AMQStoreException
            {
            }

            @Override
            public void createBrokerLink(BrokerLink link)
                    throws AMQStoreException
            {
            }

            @Override
            public void deleteBrokerLink(BrokerLink link)
                    throws AMQStoreException
            {
            }

            @Override
            public void createBridge(Bridge bridge) throws AMQStoreException
            {
            }

            @Override
            public void deleteBridge(Bridge bridge) throws AMQStoreException
            {
            }
            
        };
    }
}