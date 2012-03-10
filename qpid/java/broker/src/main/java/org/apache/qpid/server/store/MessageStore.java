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
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.message.EnqueableMessage;

/**
 * MessageStore defines the interface to a storage area, which can be used to preserve the state of messages.
 *
 */
public interface MessageStore
{
    StoreFuture IMMEDIATE_FUTURE = new StoreFuture()
        {
            public boolean isComplete()
            {
                return true;
            }

            public void waitForCompletion()
            {

            }
        };


    /**
     * Called after instantiation in order to configure the message store. A particular implementation can define
     * whatever parameters it wants.
     *
     * @param name             The name to be used by this storem
     * @param recoveryHandler  Handler to be called as the store recovers on start up
     * @param config           The apache commons configuration object.
     *
     * @throws Exception If any error occurs that means the store is unable to configure itself.
     */
    void configureMessageStore(String name,
                               MessageStoreRecoveryHandler recoveryHandler,
                               Configuration config,
                               LogSubject logSubject) throws Exception;

    /**
     * Called to close and cleanup any resources used by the message store.
     *
     * @throws Exception If the close fails.
     */
    void close() throws Exception;


    public <T extends StorableMessageMetaData> StoredMessage<T> addMessage(T metaData);


    /**
     * Is this store capable of persisting the data
     *
     * @return true if this store is capable of persisting data
     */
    boolean isPersistent();



    public static interface Transaction
    {
        /**
         * Places a message onto a specified queue, in a given transactional context.
         *
         *
         *
         * @param queue     The queue to place the message on.
         * @param message
         * @throws org.apache.qpid.AMQStoreException If the operation fails for any reason.
         */
        void enqueueMessage(TransactionLogResource queue, EnqueableMessage message) throws AMQStoreException;

        /**
         * Extracts a message from a specified queue, in a given transactional context.
         *
         * @param queue     The queue to place the message on.
         * @param message The message to dequeue.
         * @throws AMQStoreException If the operation fails for any reason, or if the specified message does not exist.
         */
        void dequeueMessage(TransactionLogResource queue, EnqueableMessage message) throws AMQStoreException;


        /**
         * Commits all operations performed within a given transactional context.
         *
         * @throws AMQStoreException If the operation fails for any reason.
         */
        void commitTran() throws AMQStoreException;

        /**
         * Commits all operations performed within a given transactional context.
         *
         * @throws AMQStoreException If the operation fails for any reason.
         */
        StoreFuture commitTranAsync() throws AMQStoreException;

        /**
         * Abandons all operations performed within a given transactional context.
         *
         * @throws AMQStoreException If the operation fails for any reason.
         */
        void abortTran() throws AMQStoreException;


        public static interface Record
        {
            TransactionLogResource getQueue();
            EnqueableMessage getMessage();
        }

        void removeXid(long format, byte[] globalId, byte[] branchId) throws AMQStoreException;

        void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues, Record[] dequeues)
                throws AMQStoreException;
    }

    public void configureTransactionLog(String name,
                      TransactionLogRecoveryHandler recoveryHandler,
                      Configuration storeConfiguration,
                      LogSubject logSubject) throws Exception;

    Transaction newTransaction();



    public static interface StoreFuture
    {
        boolean isComplete();

        void waitForCompletion();
    }

}
