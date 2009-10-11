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

import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.AMQException;

public interface TransactionLog
{
    /**
     * Places a message onto a specified queue, in a given transactional context.
     *
     * @param context   The transactional context for the operation.
     * @param queue     The queue to place the message on.
     * @param messageId The message to enqueue.
     * @throws org.apache.qpid.AMQException If the operation fails for any reason.
     */
    void enqueueMessage(StoreContext context, AMQQueue queue, Long messageId) throws AMQException;

    /**
     * Extracts a message from a specified queue, in a given transactional context.
     *
     * @param context   The transactional context for the operation.
     * @param queue     The queue to place the message on.
     * @param messageId The message to dequeue.
     * @throws org.apache.qpid.AMQException If the operation fails for any reason, or if the specified message does not exist.
     */
    void dequeueMessage(StoreContext context, AMQQueue queue, Long messageId) throws AMQException;

    /**
     * Begins a transactional context.
     *
     * @param context The transactional context to begin.
     *
     * @throws org.apache.qpid.AMQException If the operation fails for any reason.
     */
    void beginTran(StoreContext context) throws AMQException;

    /**
     * Commits all operations performed within a given transactional context.
     *
     * @param context The transactional context to commit all operations for.
     *
     * @throws org.apache.qpid.AMQException If the operation fails for any reason.
     */
    void commitTran(StoreContext context) throws AMQException;

    /**
     * Commits all operations performed within a given transactional context.
     *
     * @param context The transactional context to commit all operations for.
     *
     * @throws org.apache.qpid.AMQException If the operation fails for any reason.
     */
    StoreFuture commitTranAsync(StoreContext context) throws AMQException;

    /**
     * Abandons all operations performed within a given transactional context.
     *
     * @param context The transactional context to abandon.
     *
     * @throws org.apache.qpid.AMQException If the operation fails for any reason.
     */
    void abortTran(StoreContext context) throws AMQException;

    /**
     * Tests a transactional context to see if it has been begun but not yet committed or aborted.
     *
     * @param context The transactional context to test.
     *
     * @return <tt>true</tt> if the transactional context is live, <tt>false</tt> otherwise.
     */
    boolean inTran(StoreContext context);

    public static interface StoreFuture
    {
        boolean isComplete();

        void waitForCompletion();
    }
}
