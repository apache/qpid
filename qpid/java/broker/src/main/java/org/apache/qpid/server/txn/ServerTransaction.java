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
package org.apache.qpid.server.txn;

import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.QueueEntry;

import java.util.Collection;
import java.util.List;


/**
 * The ServerTransaction interface allows a set enqueue/dequeue operations to be
 * performed against the transaction belonging the underlying TransactionLog object.
 * 
 * Typically all ServerTransaction implementations decide if a message should be enlisted
 * into a store transaction by examining the durable property of the queue, and the persistence
 * property of the message. 
 * 
 * A caller may register a list of post transaction Actions to be
 * performed on commit() (or rollback()).
 * 
 */
public interface ServerTransaction
{
    /** 
     * Represents an action to be performed on transaction commit or rollback
     */
    public static interface Action
    {
        public void postCommit();

        public void onRollback();
    }

    /**
     * Return the time the current transaction started.
     * 
     * @return the time this transaction started or 0 if not in a transaction
     */
    long getTransactionStartTime();

    /** 
     * Register an Action for execution after transaction commit or rollback.  Actions
     * will be executed in the order in which they are registered.
     */
    void addPostTransactionAction(Action postTransactionAction);

    /** 
     * Dequeue a message from a queue registering a post transaction action.
     * 
     * A store operation will result only for a persistent message on a durable queue.
     */
    void dequeue(BaseQueue queue, EnqueableMessage message, Action postTransactionAction);

    /** 
     * Dequeue a message(s) from queue(s) registering a post transaction action.
     * 
     * Store operations will result only for a persistent messages on durable queues.
     */
    void dequeue(Collection<QueueEntry> messages, Action postTransactionAction);

    /** 
     * Enqueue a message to a queue registering a post transaction action.
     * 
     * A store operation will result only for a persistent message on a durable queue.
     */
    void enqueue(BaseQueue queue, EnqueableMessage message, Action postTransactionAction);

    /** 
     * Enqueue a message(s) to queue(s) registering a post transaction action.
     * 
     * Store operations will result only for a persistent messages on durable queues.
     */
    void enqueue(List<? extends BaseQueue> queues, EnqueableMessage message, Action postTransactionAction);

    /** 
     * Commit the transaction represented by this object.
     * 
     * If the caller has registered one or more Actions, the postCommit() method on each will
     * be executed immediately after the underlying transaction has committed. 
     */
    void commit();

    /** Rollback the transaction represented by this object.
     * 
     * If the caller has registered one or more Actions, the onRollback() method on each will
     * be executed immediately after the underlying transaction has rolled-back. 
     */
    void rollback();
}
