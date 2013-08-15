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

import java.util.Map;
import java.util.UUID;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.model.VirtualHost;

public abstract class NullMessageStore implements MessageStore, DurableConfigurationStore
{
    @Override
    public void configureConfigStore(String name,
                                     ConfigurationRecoveryHandler recoveryHandler,
                                     VirtualHost virtualHost) throws Exception
    {
    }

    @Override
    public void update(UUID id, String type, Map<String, Object> attributes)
    {
    }

    @Override
    public void update(ConfiguredObjectRecord... records) throws AMQStoreException
    {
    }

    @Override
    public void update(boolean createIfNecessary, ConfiguredObjectRecord... records) throws AMQStoreException
    {
    }


    @Override
    public void remove(UUID id, String type)
    {
    }

    @Override
    public UUID[] removeConfiguredObjects(final UUID... objects) throws AMQStoreException
    {
        return objects;
    }

    @Override
    public void create(UUID id, String type, Map<String, Object> attributes)
    {
    }

    @Override
    public void configureMessageStore(String name,
                                      MessageStoreRecoveryHandler recoveryHandler,
                                      TransactionLogRecoveryHandler tlogRecoveryHandler) throws Exception
    {
    }

    @Override
    public void close() throws Exception
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
    public void activate() throws Exception
    {
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
}
