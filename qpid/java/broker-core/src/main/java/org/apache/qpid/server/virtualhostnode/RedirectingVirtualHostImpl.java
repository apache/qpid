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
package org.apache.qpid.server.virtualhostnode;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHostAlias;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.LinkRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.txn.DtxRegistry;
import org.apache.qpid.server.virtualhost.ExchangeIsAlternateException;
import org.apache.qpid.server.virtualhost.HouseKeepingTask;
import org.apache.qpid.server.virtualhost.RequiredExchangeException;

@ManagedObject( category = false, type = RedirectingVirtualHostImpl.TYPE, register = false )
class RedirectingVirtualHostImpl
    extends AbstractConfiguredObject<RedirectingVirtualHostImpl>
        implements RedirectingVirtualHost<RedirectingVirtualHostImpl>
{
    public static final String TYPE = "REDIRECTOR";
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

    @ManagedAttributeField
    private List<String> _enabledConnectionValidators;

    @ManagedAttributeField
    private List<String> _disabledConnectionValidators;

    @ManagedAttributeField
    private List<String> _globalAddressDomains;

    @ManagedObjectFactoryConstructor
    public RedirectingVirtualHostImpl(final Map<String, Object> attributes, VirtualHostNode<?> virtualHostNode)
    {
        super(parentsMap(virtualHostNode), attributes);

        _messagesDelivered = new StatisticsCounter("messages-delivered-" + getName());
        _dataDelivered = new StatisticsCounter("bytes-delivered-" + getName());
        _messagesReceived = new StatisticsCounter("messages-received-" + getName());
        _dataReceived = new StatisticsCounter("bytes-received-" + getName());
        setState(State.UNAVAILABLE);
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);

        throwUnsupportedForRedirector();
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
        throwUnsupportedForRedirector();
        return null;
    }

    @Override
    public ExchangeImpl createExchange(final Map<String, Object> attributes)
    {
        throwUnsupportedForRedirector();
        return null;
    }

    @Override
    public void removeExchange(final ExchangeImpl<?> exchange, final boolean force)
            throws ExchangeIsAlternateException, RequiredExchangeException
    {
        throwUnsupportedForRedirector();
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
        throwUnsupportedForRedirector();
        return null;
    }

    @Override
    public void executeTransaction(final TransactionalOperation op)
    {
        throwUnsupportedForRedirector();
    }

    @Override
    public Collection<String> getExchangeTypeNames()
    {
        return getObjectFactory().getSupportedTypes(Exchange.class);
    }

    @Override
    public String getRedirectHost(final AmqpPort<?> port)
    {
        return ((RedirectingVirtualHostNode<?>)(getParent(VirtualHostNode.class))).getRedirects().get(port);
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
        throwUnsupportedForRedirector();
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
    public SecurityManager getSecurityManager()
    {
        return super.getSecurityManager();
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
        throwUnsupportedForRedirector();
        return null;
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
        throwUnsupportedForRedirector();
    }

    @Override
    public void registerMessageDelivered(final long messageSize)
    {
        throwUnsupportedForRedirector();
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

    @Override
    public boolean authoriseCreateConnection(final AMQConnectionModel<?, ?> connection)
    {
        return false;
    }

    @Override
    public List<String> getEnabledConnectionValidators()
    {
        return _enabledConnectionValidators;
    }

    @Override
    public List<String> getDisabledConnectionValidators()
    {
        return _disabledConnectionValidators;
    }

    @Override
    public List<String> getGlobalAddressDomains()
    {
        return _globalAddressDomains;
    }

    @Override
    public String getLocalAddress(final String routingAddress)
    {
        String localAddress = routingAddress;
        if(getGlobalAddressDomains() != null)
        {
            for(String domain : getGlobalAddressDomains())
            {
                if(localAddress.length() > routingAddress.length() - domain.length() && routingAddress.startsWith(domain + "/"))
                {
                    localAddress = routingAddress.substring(domain.length());
                }
            }
        }
        return localAddress;
    }

    private void throwUnsupportedForRedirector()
    {
        throw new IllegalStateException("The virtual host state of " + getState()
                                        + " does not permit this operation.");
    }


}
