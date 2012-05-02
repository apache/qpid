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

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.federation.Bridge;
import org.apache.qpid.server.federation.BrokerLink;
import org.apache.qpid.server.queue.AMQQueue;

public class NullMessageStore implements MessageStore
{
    @Override
    public void configureConfigStore(String name,
                                     ConfigurationRecoveryHandler recoveryHandler,
                                     Configuration config) throws Exception
    {
    }

    @Override
    public void createExchange(Exchange exchange) throws AMQStoreException
    {
    }

    @Override
    public void removeExchange(Exchange exchange) throws AMQStoreException
    {
    }

    @Override
    public void bindQueue(Binding binding) throws AMQStoreException
    {
    }

    @Override
    public void unbindQueue(Binding binding) throws AMQStoreException
    {
    }

    @Override
    public void createQueue(AMQQueue queue) throws AMQStoreException
    {
    }

    @Override
    public void createQueue(AMQQueue queue, FieldTable arguments) throws AMQStoreException
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
    public void createBrokerLink(final BrokerLink link) throws AMQStoreException
    {
    }

    @Override
    public void deleteBrokerLink(final BrokerLink link) throws AMQStoreException
    {
    }

    @Override
    public void createBridge(final Bridge bridge) throws AMQStoreException
    {
    }

    @Override
    public void deleteBridge(final Bridge bridge) throws AMQStoreException
    {
    }

    @Override
    public void configureMessageStore(String name,
            MessageStoreRecoveryHandler recoveryHandler,
            TransactionLogRecoveryHandler tlogRecoveryHandler, Configuration config) throws Exception
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

}