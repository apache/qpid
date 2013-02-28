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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.apache.qpid.common.Closeable;
import org.apache.qpid.server.binding.BindingFactory;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.v1_0.LinkRegistry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.txn.DtxRegistry;

public interface VirtualHost extends DurableConfigurationStore.Source, Closeable, StatisticsGatherer
{
    IConnectionRegistry getConnectionRegistry();

    VirtualHostConfiguration getConfiguration();

    String getName();

    QueueRegistry getQueueRegistry();

    ExchangeRegistry getExchangeRegistry();

    ExchangeFactory getExchangeFactory();

    MessageStore getMessageStore();

    SecurityManager getSecurityManager();

    void close();

    UUID getId();

    void scheduleHouseKeepingTask(long period, HouseKeepingTask task);

    long getHouseKeepingTaskCount();

    public long getHouseKeepingCompletedTaskCount();

    int getHouseKeepingPoolSize();

    void setHouseKeepingPoolSize(int newSize);

    int getHouseKeepingActiveCount();

    VirtualHostRegistry getVirtualHostRegistry();

    BindingFactory getBindingFactory();

    DtxRegistry getDtxRegistry();

    LinkRegistry getLinkRegistry(String remoteContainerId);

    ScheduledFuture<?> scheduleTask(long delay, Runnable timeoutTask);

    State getState();

    public void block();

    public void unblock();
}
