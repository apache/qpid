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
package org.apache.qpid.server.store;

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.message.EnqueableMessage;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** A simple message store that stores the messages in a thread-safe structure in memory. */
public class MemoryMessageStore extends NullMessageStore
{
    public static final String TYPE = "Memory";
    private final AtomicLong _messageId = new AtomicLong(1);
    private final AtomicBoolean _closed = new AtomicBoolean(false);

    private static final Transaction IN_MEMORY_TRANSACTION = new Transaction()
    {
        @Override
        public StoreFuture commitTranAsync() throws AMQStoreException
        {
            return StoreFuture.IMMEDIATE_FUTURE;
        }

        @Override
        public void enqueueMessage(TransactionLogResource queue, EnqueableMessage message) throws AMQStoreException
        {
        }

        @Override
        public void dequeueMessage(TransactionLogResource  queue, EnqueableMessage message) throws AMQStoreException
        {
        }

        @Override
        public void commitTran() throws AMQStoreException
        {
        }

        @Override
        public void abortTran() throws AMQStoreException
        {
        }

        @Override
        public void removeXid(long format, byte[] globalId, byte[] branchId)
        {
        }

        @Override
        public void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues, Record[] dequeues)
        {
        }
    };

    private final StateManager _stateManager;
    private final EventManager _eventManager = new EventManager();

    public MemoryMessageStore()
    {
        _stateManager = new StateManager(_eventManager);
    }

    @Override
    public void configureConfigStore(String name, ConfigurationRecoveryHandler recoveryHandler, Configuration config) throws Exception
    {
        _stateManager.attainState(State.INITIALISING);
    }

    @Override
    public void configureMessageStore(String name, MessageStoreRecoveryHandler recoveryHandler, TransactionLogRecoveryHandler tlogRecoveryHandler, Configuration config) throws Exception
    {
        _stateManager.attainState(State.INITIALISED);
    }

    @Override
    public void activate() throws Exception
    {
        _stateManager.attainState(State.ACTIVATING);
        
        _stateManager.attainState(State.ACTIVE);
    }

    @Override
    public StoredMessage addMessage(StorableMessageMetaData metaData)
    {
        final long id = _messageId.getAndIncrement();
        StoredMemoryMessage message = new StoredMemoryMessage(id, metaData);

        return message;
    }

    @Override
    public Transaction newTransaction()
    {
        return IN_MEMORY_TRANSACTION;
    }

    @Override
    public boolean isPersistent()
    {
        return false;
    }

    @Override
    public void close() throws Exception
    {
        _stateManager.attainState(State.CLOSING);
        _closed.getAndSet(true);
        _stateManager.attainState(State.CLOSED);
    }

    @Override
    public void addEventListener(EventListener eventListener, Event... events)
    {
        _eventManager.addEventListener(eventListener, events);
    }

    @Override
    public String getStoreType()
    {
        return TYPE;
    }
}
