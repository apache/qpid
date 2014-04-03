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

import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.server.message.EnqueueableMessage;

/** A simple message store that stores the messages in a thread-safe structure in memory. */
abstract public class AbstractMemoryMessageStore extends NullMessageStore
{
    private final AtomicLong _messageId = new AtomicLong(1);

    private static final Transaction IN_MEMORY_TRANSACTION = new Transaction()
    {
        @Override
        public StoreFuture commitTranAsync()
        {
            return StoreFuture.IMMEDIATE_FUTURE;
        }

        @Override
        public void enqueueMessage(TransactionLogResource queue, EnqueueableMessage message)
        {
        }

        @Override
        public void dequeueMessage(TransactionLogResource  queue, EnqueueableMessage message)
        {
        }

        @Override
        public void commitTran()
        {
        }

        @Override
        public void abortTran()
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

    private final EventManager _eventManager = new EventManager();


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
    public void addEventListener(EventListener eventListener, Event... events)
    {
        _eventManager.addEventListener(eventListener, events);
    }

}
