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
package org.apache.qpid.server.model;

import org.apache.qpid.server.queue.QueueEntry;
import java.security.AccessControlException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public interface VirtualHost extends ConfiguredObject
{
    // Statistics

    public static final String BYTES_IN = "bytesIn";
    public static final String BYTES_OUT = "bytesOut";
    public static final String BYTES_RETAINED = "bytesRetained";
    public static final String LOCAL_TRANSACTION_BEGINS = "localTransactionBegins";
    public static final String LOCAL_TRANSACTION_ROLLBACKS = "localTransactionRollbacks";
    public static final String MESSAGES_IN = "messagesIn";
    public static final String MESSAGES_OUT = "messagesOut";
    public static final String MESSAGES_RETAINED = "messagesRetained";
    public static final String STATE_CHANGED = "stateChanged";
    public static final String XA_TRANSACTION_BRANCH_ENDS = "xaTransactionBranchEnds";
    public static final String XA_TRANSACTION_BRANCH_STARTS = "xaTransactionBranchStarts";
    public static final String XA_TRANSACTION_BRANCH_SUSPENDS = "xaTransactionBranchSuspends";

    public static final Collection<String> AVAILABLE_STATISTICS =
            Collections.unmodifiableList(
                    Arrays.asList(BYTES_IN, BYTES_OUT, BYTES_RETAINED, LOCAL_TRANSACTION_BEGINS,
                            LOCAL_TRANSACTION_ROLLBACKS, MESSAGES_IN, MESSAGES_OUT, MESSAGES_RETAINED, STATE_CHANGED,
                            XA_TRANSACTION_BRANCH_ENDS, XA_TRANSACTION_BRANCH_STARTS, XA_TRANSACTION_BRANCH_SUSPENDS));

    // Attributes



    String getReplicationGroupName();

    //children
    Collection<VirtualHostAlias> getAliases();
    Collection<Connection> getConnections();
    Collection<Queue> getQueues();
    Collection<Exchange> getExchanges();

    Exchange createExchange(String name, State initialState, boolean durable,
                            LifetimePolicy lifetime, long ttl, String type, Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException;

    Queue createQueue(String name, State initialState, boolean durable,
                      LifetimePolicy lifetime, long ttl, Map<String, Object> attributes)
                    throws AccessControlException, IllegalArgumentException;

    void deleteQueue(Queue queue) throws AccessControlException, IllegalStateException;

    public static interface Transaction
    {
        void dequeue(QueueEntry entry);

        void copy(QueueEntry entry, Queue queue);
        
        void move(QueueEntry entry, Queue queue);

    }

    public static interface TransactionalOperation
    {
        void withinTransaction(Transaction txn);
    }

    void executeTransaction(TransactionalOperation op);
}
