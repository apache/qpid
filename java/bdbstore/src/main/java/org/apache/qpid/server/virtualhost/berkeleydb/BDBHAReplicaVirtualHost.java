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
 */

package org.apache.qpid.server.virtualhost.berkeleydb;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostAlias;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.protocol.LinkRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.txn.DtxRegistry;
import org.apache.qpid.server.virtualhost.ExchangeIsAlternateException;
import org.apache.qpid.server.virtualhost.HouseKeepingTask;
import org.apache.qpid.server.virtualhost.RequiredExchangeException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

/**
  Object that represents the VirtualHost whilst the VirtualHostNode is in the replica role.  The
  real virtualhost will be elsewhere in the group.
 */
@ManagedObject( category = false, type = "BDB_HA_REPLICA" )
public class BDBHAReplicaVirtualHost extends AbstractConfiguredObject<BDBHAReplicaVirtualHost>
    implements VirtualHostImpl<BDBHAReplicaVirtualHost, AMQQueue<?>, ExchangeImpl<?>>,
               VirtualHost<BDBHAReplicaVirtualHost,AMQQueue<?>, ExchangeImpl<?>>
{
    private final StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;

    @ManagedAttributeField
    private boolean _queue_deadLetterQueueEnabled;

    @ManagedAttributeField
    private long _housekeepingCheckPeriod;

    @ManagedAttributeField
    private long _storeTransactionIdleTimeoutClose;

    @ManagedAttributeField
    private long _storeTransactionIdleTimeoutWarn;

    @ManagedAttributeField
    private long _storeTransactionOpenTimeoutClose;

    @ManagedAttributeField
    private long _storeTransactionOpenTimeoutWarn;
    @ManagedAttributeField
    private int _housekeepingThreadCount;

    @ManagedObjectFactoryConstructor
    public BDBHAReplicaVirtualHost(final Map<String, Object> attributes, VirtualHostNode<?> virtualHostNode)
    {
        super(parentsMap(virtualHostNode), attributes);

        _messagesDelivered = new StatisticsCounter("messages-delivered-" + getName());
        _dataDelivered = new StatisticsCounter("bytes-delivered-" + getName());
        _messagesReceived = new StatisticsCounter("messages-received-" + getName());
        _dataReceived = new StatisticsCounter("bytes-received-" + getName());
    }

    @Override
    public String getModelVersion()
    {
        return BrokerModel.MODEL_VERSION;
    }

    @Override
    protected <C extends ConfiguredObject> C addChild(final Class<C> childClass,
                                                      final Map<String, Object> attributes,
                                                      final ConfiguredObject... otherParents)
    {
        throwUnsupportedForReplica();
        return null;
    }

    @Override
    public ExchangeImpl createExchange(final Map<String, Object> attributes)
    {
        throwUnsupportedForReplica();
        return null;
    }

    @Override
    public void removeExchange(final ExchangeImpl<?> exchange, final boolean force)
            throws ExchangeIsAlternateException, RequiredExchangeException
    {
        throwUnsupportedForReplica();
    }

    @Override
    public MessageDestination getMessageDestination(final String name)
    {
        return null;
    }

    @Override
    public ExchangeImpl<?> getExchange(final String name)
    {
        return null;
    }

    @Override
    public AMQQueue<?> createQueue(final Map<String, Object> attributes)
    {
        throwUnsupportedForReplica();
        return null;
    }

    @Override
    public void executeTransaction(final TransactionalOperation op)
    {
        throwUnsupportedForReplica();
    }

    @Override
    public State getState()
    {
        return State.UNAVAILABLE;
    }

    @Override
    public Collection<String> getExchangeTypeNames()
    {
        return getObjectFactory().getSupportedTypes(Exchange.class);
    }

    @Override
    public Collection<String> getSupportedExchangeTypes()
    {
        return getObjectFactory().getSupportedTypes(Exchange.class);
    }

    @Override
    public Collection<String> getSupportedQueueTypes()
    {
        return getObjectFactory().getSupportedTypes(Queue.class);
    }

    @Override
    public boolean isQueue_deadLetterQueueEnabled()
    {
        return false;
    }

    @Override
    public long getHousekeepingCheckPeriod()
    {
        return 0;
    }

    @Override
    public long getStoreTransactionIdleTimeoutClose()
    {
        return 0;
    }

    @Override
    public long getStoreTransactionIdleTimeoutWarn()
    {
        return 0;
    }

    @Override
    public long getStoreTransactionOpenTimeoutClose()
    {
        return 0;
    }

    @Override
    public long getStoreTransactionOpenTimeoutWarn()
    {
        return 0;
    }

    @Override
    public int getHousekeepingThreadCount()
    {
        return 0;
    }

    @Override
    public long getQueueCount()
    {
        return 0;
    }

    @Override
    public long getExchangeCount()
    {
        return 0;
    }

    @Override
    public long getConnectionCount()
    {
        return 0;
    }

    @Override
    public long getBytesIn()
    {
        return 0;
    }

    @Override
    public long getBytesOut()
    {
        return 0;
    }

    @Override
    public long getMessagesIn()
    {
        return 0;
    }

    @Override
    public long getMessagesOut()
    {
        return 0;
    }

    @Override
    public Collection<VirtualHostAlias> getAliases()
    {
        return Collections.emptyList();
    }

    @Override
    public Collection<Connection> getConnections()
    {
        return Collections.emptyList();
    }

    @Override
    public IConnectionRegistry getConnectionRegistry()
    {
        return null;
    }

    @Override
    public AMQQueue<?> getQueue(final String name)
    {
        return null;
    }

    @Override
    public MessageSource getMessageSource(final String name)
    {
        return null;
    }

    @Override
    public AMQQueue<?> getQueue(final UUID id)
    {
        return null;
    }

    @Override
    public Collection<AMQQueue<?>> getQueues()
    {
        return Collections.emptyList();
    }

    @Override
    public int removeQueue(final AMQQueue<?> queue)
    {
        throwUnsupportedForReplica();
        return 0;
    }

    @Override
    public Collection<ExchangeImpl<?>> getExchanges()
    {
        return Collections.emptyList();
    }

    @Override
    public DurableConfigurationStore getDurableConfigurationStore()
    {
        return null;
    }

    @Override
    public ExchangeImpl<?> getExchange(final UUID id)
    {
        return null;
    }

    @Override
    public MessageDestination getDefaultDestination()
    {
        return null;
    }

    @Override
    public MessageStore getMessageStore()
    {
        return null;
    }

    @Override
    public void setTargetSize(final long targetSize)
    {

    }

    @Override
    public long getTotalQueueDepthBytes()
    {
        return 0l;
    }

    @Override
    public org.apache.qpid.server.security.SecurityManager getSecurityManager()
    {
        return null;
    }

    @Override
    public void scheduleHouseKeepingTask(final long period, final HouseKeepingTask task)
    {
    }

    @Override
    public long getHouseKeepingTaskCount()
    {
        return 0;
    }

    @Override
    public long getHouseKeepingCompletedTaskCount()
    {
        return 0;
    }

    @Override
    public int getHouseKeepingPoolSize()
    {
        return 0;
    }

    @Override
    public void setHouseKeepingPoolSize(final int newSize)
    {
    }

    @Override
    public int getHouseKeepingActiveCount()
    {
        return 0;
    }

    @Override
    public DtxRegistry getDtxRegistry()
    {
        return null;
    }

    @Override
    public LinkRegistry getLinkRegistry(final String remoteContainerId)
    {
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleTask(final long delay, final Runnable timeoutTask)
    {
        throwUnsupportedForReplica();
        return null;
    }

    @Override
    public void block()
    {
        throwUnsupportedForReplica();
    }

    @Override
    public void unblock()
    {
        throwUnsupportedForReplica();
    }

    @Override
    public boolean getDefaultDeadLetterQueueEnabled()
    {
        return false;
    }

    @Override
    public EventLogger getEventLogger()
    {
        return null;
    }

    @Override
    public void registerMessageReceived(final long messageSize, final long timestamp)
    {
        throwUnsupportedForReplica();
    }

    @Override
    public void registerMessageDelivered(final long messageSize)
    {
        throwUnsupportedForReplica();
    }

    @Override
    public StatisticsCounter getMessageDeliveryStatistics()
    {
        return _messagesDelivered;
    }

    @Override
    public StatisticsCounter getMessageReceiptStatistics()
    {
        return _messagesReceived;
    }

    @Override
    public StatisticsCounter getDataDeliveryStatistics()
    {
        return _dataDelivered;
    }

    @Override
    public StatisticsCounter getDataReceiptStatistics()
    {
        return _dataReceived;
    }

    @Override
    public void resetStatistics()
    {
    }

    private void throwUnsupportedForReplica()
    {
        throw new IllegalStateException("The virtual host state of " + getState()
                                        + " does not permit this operation.");
    }

}
