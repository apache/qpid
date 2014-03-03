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

import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.MessageStore;

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Map;

@ManagedObject( managesChildren = true )
public interface VirtualHost<X extends VirtualHost<X>> extends ConfiguredObject<X>
{

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
    String CONFIG_STORE_TYPE                    = "configStoreType";
    String CONFIG_STORE_PATH                    = "configStorePath";
    String SUPPORTED_EXCHANGE_TYPES             = "supportedExchangeTypes";
    String SUPPORTED_QUEUE_TYPES                = "supportedQueueTypes";
    String DURABLE                              = "durable";
    String LIFETIME_POLICY                      = "lifetimePolicy";
    String CONFIG_PATH                          = "configPath";

    // Attributes

    int CURRENT_CONFIG_VERSION = 4;

    @ManagedAttribute
    Collection<String> getSupportedExchangeTypes();

    @ManagedAttribute
    Collection<String> getSupportedQueueTypes();

    @ManagedAttribute
    boolean isQueue_deadLetterQueueEnabled();

    @ManagedAttribute
    long getHousekeepingCheckPeriod();

    @ManagedAttribute
    int getQueue_maximumDeliveryAttempts();

    @ManagedAttribute
    long getQueue_flowControlSizeBytes();

    @ManagedAttribute
    long getQueue_flowResumeSizeBytes();

    @ManagedAttribute
    String getConfigStoreType();

    @ManagedAttribute
    String getConfigStorePath();

    @ManagedAttribute
    String getStoreType();

    @ManagedAttribute
    String getStorePath();

    @ManagedAttribute
    long getStoreTransactionIdleTimeoutClose();

    @ManagedAttribute
    long getStoreTransactionIdleTimeoutWarn();

    @ManagedAttribute
    long getStoreTransactionOpenTimeoutClose();

    @ManagedAttribute
    long getStoreTransactionOpenTimeoutWarn();

    @ManagedAttribute
    long getQueue_alertRepeatGap();

    @ManagedAttribute
    long getQueue_alertThresholdMessageAge();

    @ManagedAttribute
    long getQueue_alertThresholdMessageSize();

    @ManagedAttribute
    long getQueue_alertThresholdQueueDepthBytes();

    @ManagedAttribute
    long getQueue_alertThresholdQueueDepthMessages();

    @ManagedAttribute
    String getConfigPath();

    @ManagedStatistic
    long getQueueCount();

    @ManagedStatistic
    long getExchangeCount();

    @ManagedStatistic
    long getConnectionCount();

    @ManagedStatistic
    long getBytesIn();

    @ManagedStatistic
    long getBytesOut();

    @ManagedStatistic
    long getMessagesIn();

    @ManagedStatistic
    long getMessagesOut();


    //children
    Collection<VirtualHostAlias> getAliases();
    Collection<Connection> getConnections();
    Collection<Queue> getQueues();
    Collection<Exchange> getExchanges();

    Exchange createExchange(String name, State initialState, boolean durable,
                            LifetimePolicy lifetime, String type, Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException;

    Queue createQueue(Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException;

    Collection<String> getExchangeTypes();

    public static interface Transaction
    {
        void dequeue(MessageInstance entry);

        void copy(MessageInstance entry, Queue queue);

        void move(MessageInstance entry, Queue queue);

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

    // TODO - remove this
    TaskExecutor getTaskExecutor();

    MessageStore getMessageStore();

    String getType();
}
