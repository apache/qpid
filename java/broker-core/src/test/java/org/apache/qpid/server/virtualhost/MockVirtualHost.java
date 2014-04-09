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
package org.apache.qpid.server.virtualhost;

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHostAlias;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.protocol.LinkRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.txn.DtxRegistry;

public class MockVirtualHost implements VirtualHostImpl<MockVirtualHost, AMQQueue<?>, ExchangeImpl<?>>
{
    private String _name;

    public MockVirtualHost(String name)
    {
        _name = name;
    }

    public void close()
    {

    }

    @Override
    public VirtualHostRegistry getVirtualHostRegistry()
    {
        return null;
    }

    public AuthenticationManager getAuthenticationManager()
    {
        return null;
    }

    public DtxRegistry getDtxRegistry()
    {
        return null;
    }

    public IConnectionRegistry getConnectionRegistry()
    {
        return null;
    }

    public int getHouseKeepingActiveCount()
    {
        return 0;
    }

    public long getHouseKeepingCompletedTaskCount()
    {
        return 0;
    }

    public int getHouseKeepingPoolSize()
    {
        return 0;
    }

    public long getHouseKeepingTaskCount()
    {
        return 0;
    }

    public MessageStore getMessageStore()
    {
        return null;
    }

    @Override
    public String getType()
    {
        return null;
    }

    @Override
    public Map<String, String> getContext()
    {
        return null;
    }

    @Override
    public String getLastUpdatedBy()
    {
        return null;
    }

    @Override
    public long getLastUpdatedTime()
    {
        return 0;
    }

    @Override
    public String getCreatedBy()
    {
        return null;
    }

    @Override
    public long getCreatedTime()
    {
        return 0;
    }

    @Override
    public String setName(final String currentName, final String desiredName)
            throws IllegalStateException, AccessControlException
    {
        return null;
    }

    @Override
    public State getDesiredState()
    {
        return null;
    }

    @Override
    public State setDesiredState(final State currentState, final State desiredState)
            throws IllegalStateTransitionException, AccessControlException
    {
        return null;
    }

    @Override
    public State getState()
    {
        return null;
    }

    @Override
    public void addChangeListener(final ConfigurationChangeListener listener)
    {

    }

    @Override
    public boolean removeChangeListener(final ConfigurationChangeListener listener)
    {
        return false;
    }

    @Override
    public <T extends ConfiguredObject> T getParent(final Class<T> clazz)
    {
        return null;
    }

    public DurableConfigurationStore getDurableConfigurationStore()
    {
        return null;
    }

    public String getName()
    {
        return _name;
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    public QueueRegistry getQueueRegistry()
    {
        return null;
    }

    @Override
    public AMQQueue<?> getQueue(String name)
    {
        return null;
    }

    @Override
    public MessageSource getMessageSource(final String name)
    {
        return null;
    }

    @Override
    public AMQQueue<?> getQueue(UUID id)
    {
        return null;
    }

    @Override
    public Collection<String> getSupportedExchangeTypes()
    {
        return null;
    }

    @Override
    public Collection<String> getSupportedQueueTypes()
    {
        return null;
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
    public int getQueue_maximumDeliveryAttempts()
    {
        return 0;
    }

    @Override
    public long getQueue_flowControlSizeBytes()
    {
        return 0;
    }

    @Override
    public long getQueue_flowResumeSizeBytes()
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
    public long getQueue_alertRepeatGap()
    {
        return 0;
    }

    @Override
    public long getQueue_alertThresholdMessageAge()
    {
        return 0;
    }

    @Override
    public long getQueue_alertThresholdMessageSize()
    {
        return 0;
    }

    @Override
    public long getQueue_alertThresholdQueueDepthBytes()
    {
        return 0;
    }

    @Override
    public long getQueue_alertThresholdQueueDepthMessages()
    {
        return 0;
    }

    @Override
    public String getSecurityAcl()
    {
        return null;
    }

    @Override
    public int getHouseKeepingThreadCount()
    {
        return 0;
    }

    @Override
    public Map<String, Object> getMessageStoreSettings()
    {
        return null;
    }

    @Override
    public Map<String, Object> getConfigurationStoreSettings()
    {
        return null;
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
        return null;
    }

    @Override
    public Collection<Connection> getConnections()
    {
        return null;
    }

    @Override
    public Collection<AMQQueue<?>> getQueues()
    {
        return null;
    }

    @Override
    public int removeQueue(AMQQueue<?> queue)
    {
        return 0;
    }

    @Override
    public AMQQueue<?> createQueue(Map<String, Object> arguments)
    {
        return null;
    }

    @Override
    public Collection<String> getExchangeTypeNames()
    {
        return null;
    }

    @Override
    public void executeTransaction(final TransactionalOperation op)
    {

    }

    @Override
    public ExchangeImpl createExchange(Map<String,Object> attributes)
    {
        return null;
    }

    @Override
    public void removeExchange(ExchangeImpl exchange, boolean force)
    {
    }

    @Override
    public MessageDestination getMessageDestination(final String name)
    {
        return null;
    }

    @Override
    public ExchangeImpl getExchange(String name)
    {
        return null;
    }

    @Override
    public ExchangeImpl getExchange(UUID id)
    {
        return null;
    }

    @Override
    public ExchangeImpl getDefaultDestination()
    {
        return null;
    }

    @Override
    public Collection<ExchangeImpl<?>> getExchanges()
    {
        return null;
    }

    @Override
    public ExchangeImpl<?> createExchange(final String name,
                                          final State initialState,
                                          final boolean durable,
                                          final LifetimePolicy lifetime,
                                          final String type,
                                          final Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        return null;
    }

    @Override
    public Collection<ExchangeType<? extends ExchangeImpl>> getExchangeTypes()
    {
        return null;
    }

    public SecurityManager getSecurityManager()
    {
        return null;
    }

    @Override
    public void addVirtualHostListener(VirtualHostListener listener)
    {
    }

    public LinkRegistry getLinkRegistry(String remoteContainerId)
    {
        return null;
    }

    public ScheduledFuture<?> scheduleTask(long delay, Runnable timeoutTask)
    {
        return null;
    }

    public void scheduleHouseKeepingTask(long period, HouseKeepingTask task)
    {

    }

    public void setHouseKeepingPoolSize(int newSize)
    {

    }


    public long getCreateTime()
    {
        return 0;
    }

    public UUID getId()
    {
        return null;
    }

    public boolean isDurable()
    {
        return false;
    }

    @Override
    public void setDurable(final boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {

    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return null;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(final LifetimePolicy expected, final LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return null;
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return null;
    }

    @Override
    public Object getAttribute(final String name)
    {
        return null;
    }

    @Override
    public <T> T getAttribute(final AbstractConfiguredObject.Attribute<? super MockVirtualHost, T> attr)
    {
        return null;
    }

    @Override
    public Map<String, Object> getActualAttributes()
    {
        return null;
    }

    @Override
    public Object setAttribute(final String name, final Object expected, final Object desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return null;
    }

    @Override
    public Map<String, Number> getStatistics()
    {
        return null;
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(final Class<C> clazz)
    {
        return null;
    }

    @Override
    public <C extends ConfiguredObject> C createChild(final Class<C> childClass,
                                                      final Map<String, Object> attributes,
                                                      final ConfiguredObject... otherParents)
    {
        return null;
    }

    @Override
    public void setAttributes(final Map<String, Object> attributes)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {

    }

    @Override
    public Class<? extends ConfiguredObject> getCategoryClass()
    {
        return null;
    }

    @Override
    public <C extends ConfiguredObject<C>> C findConfiguredObject(final Class<C> clazz, final String name)
    {
        return null;
    }

    @Override
    public ConfiguredObjectRecord asObjectRecord()
    {
        return null;
    }

    @Override
    public void open()
    {

    }

    @Override
    public void validate()
    {

    }

    public StatisticsCounter getDataDeliveryStatistics()
    {
        return null;
    }

    public StatisticsCounter getDataReceiptStatistics()
    {
        return null;
    }

    public StatisticsCounter getMessageDeliveryStatistics()
    {
        return null;
    }

    public StatisticsCounter getMessageReceiptStatistics()
    {
        return null;
    }

    public void initialiseStatistics()
    {

    }

    public void registerMessageDelivered(long messageSize)
    {

    }

    public void registerMessageReceived(long messageSize, long timestamp)
    {

    }

    public void resetStatistics()
    {

    }

    public VirtualHostState getVirtualHostState()
    {
        return VirtualHostState.ACTIVE;
    }

    public void block()
    {
    }

    public void unblock()
    {
    }

    @Override
    public long getDefaultAlertThresholdMessageAge()
    {
        return 0;
    }

    @Override
    public long getDefaultAlertThresholdMessageSize()
    {
        return 0;
    }

    @Override
    public long getDefaultAlertThresholdQueueDepthMessages()
    {
        return 0;
    }

    @Override
    public long getDefaultAlertThresholdQueueDepthBytes()
    {
        return 0;
    }

    @Override
    public long getDefaultAlertRepeatGap()
    {
        return 0;
    }

    @Override
    public long getDefaultQueueFlowControlSizeBytes()
    {
        return 0;
    }

    @Override
    public long getDefaultQueueFlowResumeSizeBytes()
    {
        return 0;
    }

    @Override
    public int getDefaultMaximumDeliveryAttempts()
    {
        return 0;
    }

    @Override
    public TaskExecutor getTaskExecutor()
    {
        return null;
    }

    @Override
    public org.apache.qpid.server.model.VirtualHost getModel()
    {
        return null;
    }

    @Override
    public EventLogger getEventLogger()
    {
        return null;
    }

    @Override
    public boolean getDefaultDeadLetterQueueEnabled()
    {
        return false;
    }
}
