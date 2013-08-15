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
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.MessageStore;

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
    public static final String QUEUE_COUNT = "queueCount";
    public static final String EXCHANGE_COUNT = "exchangeCount";
    public static final String CONNECTION_COUNT = "connectionCount";

    public static final Collection<String> AVAILABLE_STATISTICS =
            Collections.unmodifiableList(
                    Arrays.asList(BYTES_IN, BYTES_OUT, BYTES_RETAINED, LOCAL_TRANSACTION_BEGINS,
                            LOCAL_TRANSACTION_ROLLBACKS, MESSAGES_IN, MESSAGES_OUT, MESSAGES_RETAINED, STATE_CHANGED,
                            XA_TRANSACTION_BRANCH_ENDS, XA_TRANSACTION_BRANCH_STARTS, XA_TRANSACTION_BRANCH_SUSPENDS,
                            QUEUE_COUNT, EXCHANGE_COUNT, CONNECTION_COUNT));

    String QUEUE_ALERT_REPEAT_GAP                     = "queue.alertRepeatGap";
    String QUEUE_ALERT_THRESHOLD_MESSAGE_AGE          = "queue.alertThresholdMessageAge";
    String QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE         = "queue.alertThresholdMessageSize";
    String QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES    = "queue.alertThresholdQueueDepthBytes";
    String QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES = "queue.alertThresholdQueueDepthMessages";
    String QUEUE_DEAD_LETTER_QUEUE_ENABLED            = "queue.deadLetterQueueEnabled";
    String QUEUE_MAXIMUM_DELIVERY_ATTEMPTS            = "queue.maximumDeliveryAttempts";
    String QUEUE_FLOW_CONTROL_SIZE_BYTES              = "queue.flowControlSizeBytes";
    String QUEUE_FLOW_RESUME_SIZE_BYTES               = "queue.flowResumeSizeBytes";

    String HOUSEKEEPING_CHECK_PERIOD            = "housekeepingCheckPeriod";
    String STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE = "storeTransactionIdleTimeoutClose";
    String STORE_TRANSACTION_IDLE_TIMEOUT_WARN  = "storeTransactionIdleTimeoutWarn";
    String STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE = "storeTransactionOpenTimeoutClose";
    String STORE_TRANSACTION_OPEN_TIMEOUT_WARN  = "storeTransactionOpenTimeoutWarn";
    String STORE_TYPE                           = "storeType";
    String STORE_PATH                           = "storePath";
    String SUPPORTED_EXCHANGE_TYPES             = "supportedExchangeTypes";
    String SUPPORTED_QUEUE_TYPES                = "supportedQueueTypes";
    String CREATED                              = "created";
    String DURABLE                              = "durable";
    String ID                                   = "id";
    String LIFETIME_POLICY                      = "lifetimePolicy";
    String NAME                                 = "name";
    String STATE                                = "state";
    String TIME_TO_LIVE                         = "timeToLive";
    String TYPE                                 = "type";
    String UPDATED                              = "updated";
    String CONFIG_PATH                          = "configPath";

    // Attributes
    public static final Collection<String> AVAILABLE_ATTRIBUTES =
            Collections.unmodifiableList(
                    Arrays.asList(
                            ID,
                            NAME,
                            TYPE,
                            STATE,
                            DURABLE,
                            LIFETIME_POLICY,
                            TIME_TO_LIVE,
                            CREATED,
                            UPDATED,
                            SUPPORTED_EXCHANGE_TYPES,
                            SUPPORTED_QUEUE_TYPES,
                            QUEUE_DEAD_LETTER_QUEUE_ENABLED,
                            HOUSEKEEPING_CHECK_PERIOD,
                            QUEUE_MAXIMUM_DELIVERY_ATTEMPTS,
                            QUEUE_FLOW_CONTROL_SIZE_BYTES,
                            QUEUE_FLOW_RESUME_SIZE_BYTES,
                            STORE_TYPE,
                            STORE_PATH,
                            STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE,
                            STORE_TRANSACTION_IDLE_TIMEOUT_WARN,
                            STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE,
                            STORE_TRANSACTION_OPEN_TIMEOUT_WARN,
                            QUEUE_ALERT_REPEAT_GAP,
                            QUEUE_ALERT_THRESHOLD_MESSAGE_AGE,
                            QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE,
                            QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES,
                            QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES,
                            CONFIG_PATH));

    int CURRENT_CONFIG_VERSION = 2;

    //children
    Collection<VirtualHostAlias> getAliases();
    Collection<Connection> getConnections();
    Collection<Queue> getQueues();
    Collection<Exchange> getExchanges();

    Exchange createExchange(String name, State initialState, boolean durable,
                            LifetimePolicy lifetime, long ttl, String type, Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException;

    Queue createQueue(String name, State initialState, boolean durable,
                      boolean exclusive, LifetimePolicy lifetime, long ttl, Map<String, Object> attributes)
                    throws AccessControlException, IllegalArgumentException;

    Collection<String> getExchangeTypes();

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

    /**
     * A temporary hack to expose host security manager.
     * TODO We need to add and implement an authorization provider configured object instead
     */
    SecurityManager getSecurityManager();

    MessageStore getMessageStore();

    String getType();
}
