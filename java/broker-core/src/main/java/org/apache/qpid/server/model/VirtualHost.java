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

import java.security.AccessControlException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.store.MessageStore;

@ManagedObject( defaultType = "ProvidedStore", description = VirtualHost.CLASS_DESCRIPTION)
public interface VirtualHost<X extends VirtualHost<X, Q, E>, Q extends Queue<?>, E extends Exchange<?> > extends ConfiguredObject<X>
{
    String CLASS_DESCRIPTION = "<p>A virtualhost is a namespace in which messaging is performed. Virtualhosts are "
                               + "independent; the messaging goes on a within a virtualhost is independent of any "
                               + "messaging that goes on in another virtualhost. For instance, a queue named <i>foo</i> "
                               + "defined in one virtualhost is completely independent of a queue named <i>foo</i> in "
                               + "another virtualhost.</p>"
                               + "<p>A virtualhost is backed by storage which is used to store the messages.</p>";

    String QUEUE_DEAD_LETTER_QUEUE_ENABLED            = "queue.deadLetterQueueEnabled";

    String HOUSEKEEPING_CHECK_PERIOD            = "housekeepingCheckPeriod";
    String STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE = "storeTransactionIdleTimeoutClose";
    String STORE_TRANSACTION_IDLE_TIMEOUT_WARN  = "storeTransactionIdleTimeoutWarn";
    String STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE = "storeTransactionOpenTimeoutClose";
    String STORE_TRANSACTION_OPEN_TIMEOUT_WARN  = "storeTransactionOpenTimeoutWarn";
    String HOUSE_KEEPING_THREAD_COUNT           = "houseKeepingThreadCount";
    String MODEL_VERSION                        = "modelVersion";
    String ENABLED_CONNECTION_VALIDATORS        = "enabledConnectionValidators";
    String DISABLED_CONNECTION_VALIDATORS       = "disabledConnectionValidators";
    String GLOBAL_ADDRESS_DOMAINS               = "globalAddressDomains";

    @ManagedContextDefault( name = "queue.deadLetterQueueEnabled")
    public static final boolean DEFAULT_DEAD_LETTER_QUEUE_ENABLED = false;

    @ManagedAttribute( defaultValue = "${queue.deadLetterQueueEnabled}")
    boolean isQueue_deadLetterQueueEnabled();

    @ManagedContextDefault( name = "virtualhost.housekeepingCheckPeriod")
    public static final long DEFAULT_HOUSEKEEPING_CHECK_PERIOD = 30000l;

    @ManagedAttribute( defaultValue = "${virtualhost.housekeepingCheckPeriod}")
    long getHousekeepingCheckPeriod();

    @ManagedContextDefault( name = "virtualhost.storeTransactionIdleTimeoutClose")
    public static final long DEFAULT_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE = 0l;

    @ManagedAttribute( defaultValue = "${virtualhost.storeTransactionIdleTimeoutClose}")
    long getStoreTransactionIdleTimeoutClose();

    @ManagedContextDefault( name = "virtualhost.storeTransactionIdleTimeoutWarn")
    public static final long DEFAULT_STORE_TRANSACTION_IDLE_TIMEOUT_WARN = 180000l;

    @ManagedAttribute( defaultValue = "${virtualhost.storeTransactionIdleTimeoutWarn}")
    long getStoreTransactionIdleTimeoutWarn();

    @ManagedContextDefault( name = "virtualhost.storeTransactionOpenTimeoutClose")
    public static final long DEFAULT_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE = 0l;

    @ManagedAttribute( defaultValue = "${virtualhost.storeTransactionOpenTimeoutClose}")
    long getStoreTransactionOpenTimeoutClose();

    @ManagedContextDefault( name = "virtualhost.storeTransactionOpenTimeoutWarn")
    public static final long DEFAULT_STORE_TRANSACTION_OPEN_TIMEOUT_WARN = 300000l;

    @ManagedAttribute( defaultValue = "${virtualhost.storeTransactionOpenTimeoutWarn}")
    long getStoreTransactionOpenTimeoutWarn();

    @ManagedContextDefault( name = "virtualhost.housekeepingThreadCount")
    public static final int DEFAULT_HOUSEKEEPING_THREAD_COUNT = 1;

    @ManagedAttribute( defaultValue = "${virtualhost.housekeepingThreadCount}")
    int getHousekeepingThreadCount();

    @DerivedAttribute( persist = true )
    String getModelVersion();

    @ManagedContextDefault( name = "virtualhost.enabledConnectionValidators")
    String DEFAULT_ENABLED_VALIDATORS = "[]";

    @ManagedAttribute( defaultValue = "${virtualhost.enabledConnectionValidators}")
    List<String> getEnabledConnectionValidators();

    @ManagedContextDefault( name = "virtualhost.disabledConnectionValidators")
    String DEFAULT_DISABLED_VALIDATORS = "[]";

    @ManagedAttribute( defaultValue = "${virtualhost.disabledConnectionValidators}")
    List<String> getDisabledConnectionValidators();

    @ManagedAttribute( defaultValue = "[]")
    List<String> getGlobalAddressDomains();

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
    Collection<Q> getQueues();
    Collection<E> getExchanges();

    E createExchange(Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException;

    Q createQueue(Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException;

    Collection<String> getExchangeTypeNames();

    void start();

    void stop();

    String getRedirectHost(AmqpPort<?> port);

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

    E getExchange(UUID id);

    MessageStore getMessageStore();

    String getType();

    void setTargetSize(long targetSize);

    long getTotalQueueDepthBytes();

}
