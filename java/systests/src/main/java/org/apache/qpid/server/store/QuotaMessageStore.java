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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.message.MessageContentSource;
import org.apache.qpid.server.model.VirtualHost;

public class
        QuotaMessageStore extends NullMessageStore
{
    private final AtomicLong _messageId = new AtomicLong(1);
    private final AtomicBoolean _closed = new AtomicBoolean(false);

    private long _totalStoreSize;;
    private boolean _limitBusted;
    private long _persistentSizeLowThreshold;
    private long _persistentSizeHighThreshold;

    private final StateManager _stateManager;
    private final EventManager _eventManager = new EventManager();

    public QuotaMessageStore()
    {
        _stateManager = new StateManager(_eventManager);
    }

    @Override
    public void configureConfigStore(VirtualHost virtualHost, ConfigurationRecoveryHandler recoveryHandler)
            throws Exception
    {
        Object overfullAttr = virtualHost.getAttribute(MessageStoreConstants.OVERFULL_SIZE_ATTRIBUTE);
        _persistentSizeHighThreshold = overfullAttr == null
                                       ? Long.MAX_VALUE
                                       : overfullAttr instanceof Number
                                         ? ((Number)overfullAttr).longValue()
                                         : Long.parseLong(overfullAttr.toString());

        Object underfullAttr = virtualHost.getAttribute(MessageStoreConstants.UNDERFULL_SIZE_ATTRIBUTE);

        _persistentSizeLowThreshold =  overfullAttr == null
                                       ? _persistentSizeHighThreshold
                                       : underfullAttr instanceof Number
                                         ? ((Number)underfullAttr).longValue()
                                         : Long.parseLong(underfullAttr.toString());


        if (_persistentSizeLowThreshold > _persistentSizeHighThreshold || _persistentSizeLowThreshold < 0l)
        {
            _persistentSizeLowThreshold = _persistentSizeHighThreshold;
        }
        _stateManager.attainState(State.INITIALISING);
    }

    @Override
    public void configureMessageStore(VirtualHost virtualHost, MessageStoreRecoveryHandler recoveryHandler,
                                      TransactionLogRecoveryHandler tlogRecoveryHandler) throws Exception
    {
        _stateManager.attainState(State.INITIALISED);
    }

    @Override
    public void activate() throws Exception
    {
        _stateManager.attainState(State.ACTIVATING);
        _stateManager.attainState(State.ACTIVE);
    }

    @SuppressWarnings("unchecked")
    @Override
    public StoredMessage<StorableMessageMetaData> addMessage(StorableMessageMetaData metaData)
    {
        final long id = _messageId.getAndIncrement();
        return new StoredMemoryMessage(id, metaData);
    }

    @Override
    public Transaction newTransaction()
    {
        return new Transaction()
        {
            private AtomicLong _storeSizeIncrease = new AtomicLong();

            @Override
            public StoreFuture commitTranAsync() throws AMQStoreException
            {
                QuotaMessageStore.this.storedSizeChange(_storeSizeIncrease.intValue());
                return StoreFuture.IMMEDIATE_FUTURE;
            }

            @Override
            public void enqueueMessage(TransactionLogResource queue, EnqueueableMessage message) throws AMQStoreException
            {
                _storeSizeIncrease.addAndGet(((MessageContentSource)message).getSize());
            }

            @Override
            public void dequeueMessage(TransactionLogResource  queue, EnqueueableMessage message) throws AMQStoreException
            {
                _storeSizeIncrease.addAndGet(-((MessageContentSource)message).getSize());
            }

            @Override
            public void commitTran() throws AMQStoreException
            {
                QuotaMessageStore.this.storedSizeChange(_storeSizeIncrease.intValue());
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
    }

    @Override
    public boolean isPersistent()
    {
        return true;
    }

    @Override
    public void close() throws Exception
    {
        if (_closed.compareAndSet(false, true))
        {
            _stateManager.attainState(State.CLOSING);
            _stateManager.attainState(State.CLOSED);
        }
    }

    @Override
    public void addEventListener(EventListener eventListener, Event... events)
    {
        _eventManager.addEventListener(eventListener, events);
    }

    private void storedSizeChange(final int delta)
    {
        if(_persistentSizeHighThreshold > 0)
        {
            synchronized (this)
            {
                long newSize = _totalStoreSize += delta;
                if(!_limitBusted &&  newSize > _persistentSizeHighThreshold)
                {
                    _limitBusted = true;
                    _eventManager.notifyEvent(Event.PERSISTENT_MESSAGE_SIZE_OVERFULL);
                }
                else if(_limitBusted && newSize < _persistentSizeHighThreshold)
                {
                    _limitBusted = false;
                    _eventManager.notifyEvent(Event.PERSISTENT_MESSAGE_SIZE_UNDERFULL);
                }
            }
        }
    }

    @Override
    public String getStoreType()
    {
        return "QUOTA";
    }
}
