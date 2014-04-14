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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.Transaction.Record;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.server.store.handler.DistributedTransactionHandler;
import org.apache.qpid.server.store.handler.MessageHandler;
import org.apache.qpid.server.store.handler.MessageInstanceHandler;

/** A simple message store that stores the messages in a thread-safe structure in memory. */
abstract class AbstractMemoryMessageStore implements MessageStore, DurableConfigurationStore
{
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

    private final AtomicLong _messageId = new AtomicLong(1);

    private final ConcurrentHashMap<UUID, ConfiguredObjectRecord> _configuredObjectRecords = new ConcurrentHashMap<UUID, ConfiguredObjectRecord>();

    protected ConcurrentHashMap<Long, StoredMemoryMessage> _messages = new ConcurrentHashMap<Long, StoredMemoryMessage>();

    private Object _transactionLock = new Object();
    private Map<UUID, Set<Long>> _messageInstances = new HashMap<UUID, Set<Long>>();
    private Map<Xid, DistributedTransactionRecords> _distributedTransactions = new HashMap<Xid, DistributedTransactionRecords>();

    @SuppressWarnings("unchecked")
    @Override
    public StoredMessage<StorableMessageMetaData> addMessage(final StorableMessageMetaData metaData)
    {
        long id = _messageId.getAndIncrement();

        if(metaData.isPersistent())
        {
            return new StoredMemoryMessage(id, metaData)
            {

                @Override
                public StoreFuture flushToStore()
                {
                    _messages.putIfAbsent(getMessageNumber(), this) ;
                    return super.flushToStore();
                }

                @Override
                public void remove()
                {
                    _messages.remove(getMessageNumber());
                    super.remove();
                }

            };
        }
        else
        {
            return new StoredMemoryMessage(id, metaData);
        }
    }

    private void commitTransactionInternal(MemoryMessageStoreTransaction transaction)
    {
        synchronized (_transactionLock )
        {
            for (Map.Entry<UUID, Set<Long>> loacalEnqueuedEntry : transaction._localEnqueueMap.entrySet())
            {
                Set<Long> messageIds = _messageInstances.get(loacalEnqueuedEntry.getKey());
                if (messageIds == null)
                {
                    messageIds = new HashSet<Long>();
                    _messageInstances.put(loacalEnqueuedEntry.getKey(), messageIds);
                }
                messageIds.addAll(loacalEnqueuedEntry.getValue());
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
    public Transaction newTransaction()
    {
        return new MemoryMessageStoreTransaction();
    }

    @Override
    public boolean isPersistent()
    {
        return false;
    }

    @Override
    public void addEventListener(EventListener eventListener, Event... events)
    {
    }

    @Override
    public void create(ConfiguredObjectRecord record)
    {
        if (_configuredObjectRecords.putIfAbsent(record.getId(), record) != null)
        {
            throw new StoreException("Record with id " + record.getId() + " is already present");
        }
    }

    @Override
    public void update(boolean createIfNecessary, ConfiguredObjectRecord... records)
    {
        for (ConfiguredObjectRecord record : records)
        {
            ConfiguredObjectRecord previousValue = _configuredObjectRecords.replace(record.getId(), record);
            if (previousValue == null && !createIfNecessary)
            {
                throw new StoreException("Record with id " + record.getId() + " does not exist");
            }
        }
    }

    @Override
    public UUID[] remove(final ConfiguredObjectRecord... objects)
    {
        List<UUID> removed = new ArrayList<UUID>();
        for (ConfiguredObjectRecord record : objects)
        {
            if (_configuredObjectRecords.remove(record.getId()) != null)
            {
                removed.add(record.getId());
            }
        }
        return removed.toArray(new UUID[removed.size()]);
    }

    @Override
    public void closeConfigurationStore()
    {
        _configuredObjectRecords.clear();
    }

    @Override
    public void openConfigurationStore(ConfiguredObject<?> parent, Map<String, Object> storeSettings)
    {
    }

    @Override
    public void visitConfiguredObjectRecords(ConfiguredObjectRecordHandler handler) throws StoreException
    {
        handler.begin();
        for (ConfiguredObjectRecord record : _configuredObjectRecords.values())
        {
            if (!handler.handle(record))
            {
                break;
            }
        }
        handler.end();
    }

    @Override
    public void openMessageStore(ConfiguredObject<?> parent, Map<String, Object> messageStoreSettings)
    {
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
    public String getStoreLocation()
    {
        return null;
    }

    @Override
    public void onDelete()
    {
    }

    @Override
    public void visitMessages(MessageHandler handler) throws StoreException
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
    public void visitMessageInstances(MessageInstanceHandler handler) throws StoreException
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
    public void visitDistributedTransactions(DistributedTransactionHandler handler) throws StoreException
    {
        synchronized (_transactionLock)
        {
            for (Map.Entry<Xid, DistributedTransactionRecords> entry : _distributedTransactions.entrySet())
            {
                Xid xid = entry.getKey();
                DistributedTransactionRecords records = entry.getValue();
                if (!handler.handle(xid.getFormat(), xid.getGlobalId(), xid.getBranchId(), records.getEnqueues(), records.getDequeues()))
                {
                    break;
                }
            }
        }
    }

    private static final class DistributedTransactionRecords
    {
        private Record[] _enqueues;
        private Record[] _dequeues;

        public DistributedTransactionRecords(Record[] enqueues, Record[] dequeues)
        {
            super();
            _enqueues = enqueues;
            _dequeues = dequeues;
        }

        public Record[] getEnqueues()
        {
            return _enqueues;
        }

        public Record[] getDequeues()
        {
            return _dequeues;
        }
    }
}
