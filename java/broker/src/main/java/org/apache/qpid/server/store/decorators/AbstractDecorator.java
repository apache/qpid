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
package org.apache.qpid.server.store.decorators;

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.federation.Bridge;
import org.apache.qpid.server.federation.BrokerLink;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler;
import org.apache.qpid.server.store.Event;
import org.apache.qpid.server.store.EventListener;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.store.TransactionLogRecoveryHandler;

/**
 * AbstractDecorator.  All methods <bMUST</b> perform simple
 * delegation to their equivalent decorated counterpart without
 * change.
 */
public class AbstractDecorator implements MessageStore
{
    protected final MessageStore _decoratedStore;

    public AbstractDecorator(MessageStore store)
    {
        _decoratedStore = store;
    }

    @Override
    public void configureMessageStore(String name,
            MessageStoreRecoveryHandler messageRecoveryHandler,
            TransactionLogRecoveryHandler tlogRecoveryHandler,
            Configuration config) throws Exception
    {
        _decoratedStore.configureMessageStore(name, messageRecoveryHandler,
                tlogRecoveryHandler, config);
    }

    @Override
    public void configureConfigStore(String name,
            ConfigurationRecoveryHandler recoveryHandler, Configuration config) throws Exception
    {
        _decoratedStore.configureConfigStore(name, recoveryHandler, config);
    }

    @Override
    public void activate() throws Exception
    {
        _decoratedStore.activate();
    }

    @Override
    public void close() throws Exception
    {
        _decoratedStore.close();
    }

    @Override
    public <T extends StorableMessageMetaData> StoredMessage<T> addMessage(
            T metaData)
    {
        return _decoratedStore.addMessage(metaData);
    }

    @Override
    public void createExchange(Exchange exchange) throws AMQStoreException
    {
        _decoratedStore.createExchange(exchange);
    }

    @Override
    public boolean isPersistent()
    {
        return _decoratedStore.isPersistent();
    }

    @Override
    public Transaction newTransaction()
    {
        return _decoratedStore.newTransaction();
    }

    @Override
    public void removeExchange(Exchange exchange) throws AMQStoreException
    {
        _decoratedStore.removeExchange(exchange);
    }

    @Override
    public void addEventListener(EventListener eventListener, Event event)
    {
        _decoratedStore.addEventListener(eventListener, event);
    }

    @Override
    public void bindQueue(Exchange exchange, AMQShortString routingKey,
            AMQQueue queue, FieldTable args) throws AMQStoreException
    {
        _decoratedStore.bindQueue(exchange, routingKey, queue, args);
    }

    @Override
    public void unbindQueue(Exchange exchange, AMQShortString routingKey,
            AMQQueue queue, FieldTable args) throws AMQStoreException
    {
        _decoratedStore.unbindQueue(exchange, routingKey, queue, args);
    }

    @Override
    public void createQueue(AMQQueue queue) throws AMQStoreException
    {
        _decoratedStore.createQueue(queue);
    }

    @Override
    public void createQueue(AMQQueue queue, FieldTable arguments)
            throws AMQStoreException
    {
        _decoratedStore.createQueue(queue, arguments);
    }

    @Override
    public void removeQueue(AMQQueue queue) throws AMQStoreException
    {
        _decoratedStore.removeQueue(queue);
    }

    @Override
    public void updateQueue(AMQQueue queue) throws AMQStoreException
    {
        _decoratedStore.updateQueue(queue);
    }

    @Override
    public void createBrokerLink(BrokerLink link) throws AMQStoreException
    {
        _decoratedStore.createBrokerLink(link);
    }

    @Override
    public void deleteBrokerLink(BrokerLink link) throws AMQStoreException
    {
        _decoratedStore.deleteBrokerLink(link);
    }

    @Override
    public void createBridge(Bridge bridge) throws AMQStoreException
    {
        _decoratedStore.createBridge(bridge);
    }

    @Override
    public void deleteBridge(Bridge bridge) throws AMQStoreException
    {
        _decoratedStore.deleteBridge(bridge);
    }

    @Override
    public MessageStore getUnderlyingStore()
    {
        return _decoratedStore.getUnderlyingStore();
    }
}
