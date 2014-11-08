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

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.handler.DistributedTransactionHandler;
import org.apache.qpid.server.store.handler.MessageHandler;
import org.apache.qpid.server.store.handler.MessageInstanceHandler;

/** A simple message store that stores the messages in a thread-safe structure in memory. */
public class MemoryMessageStore implements MessageStore
{
    public static final String TYPE = "Memory";

    private final AtomicLong _messageId = new AtomicLong(1);

    private final ConcurrentMap<Long, StoredMemoryMessage> _messages = new ConcurrentHashMap<Long, StoredMemoryMessage>();
    private final Object _transactionLock = new Object();
    private final Map<UUID, Set<Long>> _messageInstances = new HashMap<UUID, Set<Long>>();
    private final Map<Xid, DistributedTransactionRecords> _distributedTransactions = new HashMap<Xid, DistributedTransactionRecords>();


    private final class MemoryMessageStoreTransaction implements Transaction
    {
        private Map<UUID, Set<Long>> _localEnqueueMap = new HashMap<UUID, Set<Long>>();
        private Map<UUID, Set<Long>> _localDequeueMap = new HashMap<UUID, Set<Long>>();

        private Map<Xid, DistributedTransactionRecords> _localDistributedTransactionsRecords = new HashMap<Xid, DistributedTransactionRecords>();
        private Set<Xid> _localDistributedTransactionsRemoves = new HashSet<Xid>();

        @Override
        public StoreFuture commitTranAsync()
        {
            return StoreFuture.IMMEDIATE_FUTURE;
        }

        @Override
        public void enqueueMessage(TransactionLogResource queue, EnqueueableMessage message)
        {

            if(message.getStoredMessage() instanceof StoredMemoryMessage)
            {
                _messages.putIfAbsent(message.getMessageNumber(), (StoredMemoryMessage) message.getStoredMessage());
            }

            Set<Long> messageIds = _localEnqueueMap.get(queue.getId());
            if (messageIds == null)
            {
                messageIds = new HashSet<Long>();
                _localEnqueueMap.put(queue.getId(), messageIds);
            }
            messageIds.add(message.getMessageNumber());
        }

        @Override
        public void dequeueMessage(TransactionLogResource queue, EnqueueableMessage message)
        {
            Set<Long> messageIds = _localDequeueMap.get(queue.getId());
            if (messageIds == null)
            {
                messageIds = new HashSet<Long>();
                _localDequeueMap.put(queue.getId(), messageIds);
            }
            messageIds.add(message.getMessageNumber());
        }

        @Override
        public void commitTran()
        {
            commitTransactionInternal(this);
            _localEnqueueMap.clear();
            _localDequeueMap.clear();
        }

        @Override
        public void abortTran()
        {
            _localEnqueueMap.clear();
            _localDequeueMap.clear();
        }

        @Override
        public void removeXid(long format, byte[] globalId, byte[] branchId)
        {
            _localDistributedTransactionsRemoves.add(new Xid(format, globalId, branchId));
        }

        @Override
        public void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues, Record[] dequeues)
        {
            _localDistributedTransactionsRecords.put(new Xid(format, globalId, branchId), new DistributedTransactionRecords(enqueues, dequeues));
        }
    }

    private static final class DistributedTransactionRecords
    {
        private Transaction.Record[] _enqueues;
        private Transaction.Record[] _dequeues;

        public DistributedTransactionRecords(Transaction.Record[] enqueues, Transaction.Record[] dequeues)
        {
            super();
            _enqueues = enqueues;
            _dequeues = dequeues;
        }

        public Transaction.Record[] getEnqueues()
        {
            return _enqueues;
        }

        public Transaction.Record[] getDequeues()
        {
            return _dequeues;
        }
    }

    private void commitTransactionInternal(MemoryMessageStoreTransaction transaction)
    {
        synchronized (_transactionLock )
        {
            for (Map.Entry<UUID, Set<Long>> localEnqueuedEntry : transaction._localEnqueueMap.entrySet())
            {
                Set<Long> messageIds = _messageInstances.get(localEnqueuedEntry.getKey());
                if (messageIds == null)
                {
                    messageIds = new HashSet<Long>();
                    _messageInstances.put(localEnqueuedEntry.getKey(), messageIds);
                }
                messageIds.addAll(localEnqueuedEntry.getValue());
            }

            for (Map.Entry<UUID, Set<Long>> loacalDequeueEntry : transaction._localDequeueMap.entrySet())
            {
                Set<Long> messageIds = _messageInstances.get(loacalDequeueEntry.getKey());
                if (messageIds != null)
                {
                    messageIds.removeAll(loacalDequeueEntry.getValue());
                    if (messageIds.isEmpty())
                    {
                        _messageInstances.remove(loacalDequeueEntry.getKey());
                    }
                }
            }

            for (Map.Entry<Xid, DistributedTransactionRecords> entry : transaction._localDistributedTransactionsRecords.entrySet())
            {
                _distributedTransactions.put(entry.getKey(), entry.getValue());
            }

            for (Xid removed : transaction._localDistributedTransactionsRemoves)
            {
                _distributedTransactions.remove(removed);
            }

        }


    }


    @Override
    public void openMessageStore(final ConfiguredObject<?> parent)
    {
    }

    @Override
    public void upgradeStoreStructure() throws StoreException
    {

    }

    @Override
    public <T extends StorableMessageMetaData> StoredMessage<T> addMessage(final T metaData)
    {
        long id = getNextMessageId();

        StoredMemoryMessage<T> storedMemoryMessage = new StoredMemoryMessage<T>(id, metaData)
        {

            @Override
            public void remove()
            {
                _messages.remove(getMessageNumber());
                super.remove();
            }

        };

        return storedMemoryMessage;

    }

    @Override
    public long getNextMessageId()
    {
        return _messageId.getAndIncrement();
    }

    @Override
    public boolean isPersistent()
    {
        return false;
    }

    @Override
    public Transaction newTransaction()
    {
        return new MemoryMessageStoreTransaction();
    }

    @Override
    public void closeMessageStore()
    {
        _messages.clear();
        synchronized (_transactionLock)
        {
            _messageInstances.clear();
            _distributedTransactions.clear();
        }
    }

    @Override
    public void addEventListener(final EventListener eventListener, final Event... events)
    {
    }

    @Override
    public String getStoreLocation()
    {
        return null;
    }

    @Override
    public File getStoreLocationAsFile()
    {
        return null;
    }

    @Override
    public void onDelete(ConfiguredObject<?> parent)
    {
    }

    @Override
    public void visitMessages(final MessageHandler handler) throws StoreException
    {
        for (StoredMemoryMessage message : _messages.values())
        {
            if(!handler.handle(message))
            {
                break;
            }
        }
    }

    @Override
    public StoredMessage<?> getMessage(final long messageId)
    {
        return _messages.get(messageId);
    }

    @Override
    public void visitMessageInstances(final MessageInstanceHandler handler) throws StoreException
    {
        synchronized (_transactionLock)
        {
            for (Map.Entry<UUID, Set<Long>> enqueuedEntry : _messageInstances.entrySet())
            {
                UUID resourceId = enqueuedEntry.getKey();
                for (Long messageId : enqueuedEntry.getValue())
                {
                    if (!handler.handle(resourceId, messageId))
                    {
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void visitMessageInstances(TransactionLogResource queue, final MessageInstanceHandler handler) throws StoreException
    {
        synchronized (_transactionLock)
        {
            Set<Long> ids = _messageInstances.get(queue.getId());
            if(ids != null)
            {
                for (long id : ids)
                {
                    if (!handler.handle(queue.getId(), id))
                    {
                        return;
                    }

                }
            }
        }
    }


    @Override
    public void visitDistributedTransactions(final DistributedTransactionHandler handler) throws StoreException
    {
        synchronized (_transactionLock)
        {
            for (Map.Entry<Xid, DistributedTransactionRecords> entry : _distributedTransactions.entrySet())
            {
                Xid xid = entry.getKey();
                DistributedTransactionRecords records = entry.getValue();
                if (!handler.handle(xid.getFormat(),
                                    xid.getGlobalId(),
                                    xid.getBranchId(),
                                    records.getEnqueues(),
                                    records.getDequeues()))
                {
                    break;
                }
            }
        }
    }
}
