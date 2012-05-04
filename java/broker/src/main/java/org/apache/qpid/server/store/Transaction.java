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

import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.message.EnqueableMessage;

public interface Transaction
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

    void recordXid(long format, byte[] globalId, byte[] branchId, Transaction.Record[] enqueues, Transaction.Record[] dequeues)
            throws AMQStoreException;
}