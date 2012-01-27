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
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.queue.AMQQueue;

/**
 * A message store that does nothing. Designed to be used in tests that do not want to use any message store
 * functionality.
 */
public class SkeletonMessageStore implements MessageStore
{
    public void configureConfigStore(String name,
                          ConfigurationRecoveryHandler recoveryHandler,
                          Configuration config,
                          LogSubject logSubject) throws Exception
    {
    }

    public void configureMessageStore(String name,
                                      MessageStoreRecoveryHandler recoveryHandler,
                                      Configuration config,
                                      LogSubject logSubject) throws Exception
    {
    }

    public void close() throws Exception
    {
    }

    public <M extends StorableMessageMetaData> StoredMessage<M> addMessage(M metaData)
    {
        return null;
    }


    public void createExchange(Exchange exchange) throws AMQStoreException
    {

    }

    public void removeExchange(Exchange exchange) throws AMQStoreException
    {

    }

    public void bindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQStoreException
    {

    }

    public void unbindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQStoreException
    {

    }

    public void createQueue(AMQQueue queue) throws AMQStoreException
    {
    }

    public void createQueue(AMQQueue queue, FieldTable arguments) throws AMQStoreException
    {
    }

    public boolean isPersistent()
    {
        return false;
    }

    public void removeQueue(final AMQQueue queue) throws AMQStoreException
    {

    }

    public void configureTransactionLog(String name,
                                        TransactionLogRecoveryHandler recoveryHandler,
                                        Configuration storeConfiguration,
                                        LogSubject logSubject) throws Exception
    {

    }

    public Transaction newTransaction()
    {
        return new Transaction()
        {

            public void enqueueMessage(TransactionLogResource queue, EnqueableMessage message) throws AMQStoreException
            {

            }

            public void dequeueMessage(TransactionLogResource  queue, EnqueableMessage message) throws AMQStoreException
            {

            }

            public void commitTran() throws AMQStoreException
            {

            }

            public StoreFuture commitTranAsync() throws AMQStoreException
            {
                return new StoreFuture()
                            {
                                public boolean isComplete()
                                {
                                    return true;
                                }

                                public void waitForCompletion()
                                {

                                }
                            };
            }

            public void abortTran() throws AMQStoreException
            {

            }
        };
    }

    public void updateQueue(AMQQueue queue) throws AMQStoreException
    {

    }

}
