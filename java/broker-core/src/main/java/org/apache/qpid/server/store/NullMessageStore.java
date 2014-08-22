/*
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

import java.util.UUID;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.server.store.handler.DistributedTransactionHandler;
import org.apache.qpid.server.store.handler.MessageHandler;
import org.apache.qpid.server.store.handler.MessageInstanceHandler;

public abstract class NullMessageStore implements MessageStore, DurableConfigurationStore, MessageStoreProvider
{
    private ConfiguredObjectRecord[] _initialRecords;

    @Override
    public MessageStore getMessageStore()
    {
        return this;
    }

    @Override
    public void openConfigurationStore(ConfiguredObject<?> parent,
                                       final boolean overwrite,
                                       final ConfiguredObjectRecord... initialRecords)
    {
        _initialRecords = initialRecords;
    }

    @Override
    public void update(boolean createIfNecessary, ConfiguredObjectRecord... records)
    {
    }

    @Override
    public UUID[] remove(final ConfiguredObjectRecord... objects)
    {
        final UUID[] removed = new UUID[objects.length];
        for(int i = 0; i < objects.length; i++)
        {
            removed[i] = objects[i].getId();
        }
        return removed;
    }

    @Override
    public void create(ConfiguredObjectRecord record)
    {
    }

    @Override
    public void openMessageStore(ConfiguredObject<?> parent)
    {
    }

    @Override
    public void upgradeStoreStructure() throws StoreException
    {

    }

    @Override
    public void closeMessageStore()
    {
    }

    @Override
    public void closeConfigurationStore()
    {
    }

    @Override
    public <T extends StorableMessageMetaData> StoredMessage<T> addMessage(T metaData)
    {
        return null;
    }

    @Override
    public boolean isPersistent()
    {
        return false;
    }

    @Override
    public Transaction newTransaction()
    {
        return null;
    }

    @Override
    public void addEventListener(EventListener eventListener, Event... events)
    {
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
    public void visitConfiguredObjectRecords(ConfiguredObjectRecordHandler handler) throws StoreException
    {
        handler.begin();
        if(_initialRecords != null)
        {
            for(ConfiguredObjectRecord record : _initialRecords)
            {
                if(!handler.handle(record))
                {
                    break;
                }
            }
        }
        handler.end();
    }

    @Override
    public void visitMessages(MessageHandler handler) throws StoreException
    {
    }

    @Override
    public void visitMessageInstances(TransactionLogResource queue, MessageInstanceHandler handler) throws StoreException
    {
    }

    @Override
    public void visitMessageInstances(MessageInstanceHandler handler) throws StoreException
    {
    }

    @Override
    public void visitDistributedTransactions(DistributedTransactionHandler handler) throws StoreException
    {
    }

    @Override
    public long getNextMessageId()
    {
        return 0;
    }

    @Override
    public StoredMessage<?> getMessage(final long messageId)
    {
        return null;
    }
}
