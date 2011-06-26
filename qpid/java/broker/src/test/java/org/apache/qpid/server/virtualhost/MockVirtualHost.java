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

import java.util.UUID;

import org.apache.qpid.server.binding.BindingFactory;
import org.apache.qpid.server.configuration.BrokerConfig;
import org.apache.qpid.server.configuration.ConfigStore;
import org.apache.qpid.server.configuration.ConfiguredObject;
import org.apache.qpid.server.configuration.VirtualHostConfig;
import org.apache.qpid.server.configuration.VirtualHostConfigType;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.federation.BrokerLink;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TransactionLog;

public class MockVirtualHost implements VirtualHost
{
    private String _name;

    public MockVirtualHost(String name)
    {
        _name = name;
    }

    public void close()
    {

    }

    public void createBrokerConnection(String transport, String host, int port,
            String vhost, boolean durable, String authMechanism,
            String username, String password)
    {

    }

    public IApplicationRegistry getApplicationRegistry()
    {
        return null;
    }

    public AuthenticationManager getAuthenticationManager()
    {
        return null;
    }

    public BindingFactory getBindingFactory()
    {
        return null;
    }

    public UUID getBrokerId()
    {
        return null;
    }

    public ConfigStore getConfigStore()
    {
        return null;
    }

    public VirtualHostConfiguration getConfiguration()
    {
        return null;
    }

    public IConnectionRegistry getConnectionRegistry()
    {
        return null;
    }

    public DurableConfigurationStore getDurableConfigurationStore()
    {
        return null;
    }

    public ExchangeFactory getExchangeFactory()
    {
        return null;
    }

    public ExchangeRegistry getExchangeRegistry()
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

    public ManagedObject getManagedObject()
    {
        return null;
    }

    public MessageStore getMessageStore()
    {
        return null;
    }

    public String getName()
    {
        return _name;
    }

    public QueueRegistry getQueueRegistry()
    {
        return null;
    }

    public SecurityManager getSecurityManager()
    {
        return null;
    }

    public TransactionLog getTransactionLog()
    {
        return null;
    }

    public void removeBrokerConnection(BrokerLink brokerLink)
    {

    }

    public void scheduleHouseKeepingTask(long period, HouseKeepingTask task)
    {

    }

    public void setHouseKeepingPoolSize(int newSize)
    {

    }

    public BrokerConfig getBroker()
    {
        return null;
    }

    public String getFederationTag()
    {
        return null;
    }

    public void setBroker(BrokerConfig brokerConfig)
    {

    }

    public VirtualHostConfigType getConfigType()
    {
        return null;
    }

    public long getCreateTime()
    {
        return 0;
    }

    public UUID getId()
    {
        return null;
    }

    public ConfiguredObject<VirtualHostConfigType, VirtualHostConfig> getParent()
    {
        return null;
    }

    public boolean isDurable()
    {
        return false;
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

    public boolean isStatisticsEnabled()
    {
        return false;
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

    public void setStatisticsEnabled(boolean enabled)
    {

    }
}