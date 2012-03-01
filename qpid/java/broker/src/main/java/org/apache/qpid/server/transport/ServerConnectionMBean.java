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
package org.apache.qpid.server.transport;

import org.apache.qpid.management.common.mbeans.annotations.MBeanConstructor;
import org.apache.qpid.management.common.mbeans.annotations.MBeanDescription;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.ManagementActor;
import org.apache.qpid.server.management.AbstractAMQManagedConnectionObject;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.protocol.AMQSessionModel;

import javax.management.JMException;
import javax.management.NotCompliantMBeanException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * This MBean class implements the management interface. In order to make more attributes, operations and notifications
 * available over JMX simply augment the ManagedConnection interface and add the appropriate implementation here.
 */
@MBeanDescription("Management Bean for an AMQ Broker 0-10 Connection")
public class ServerConnectionMBean extends AbstractAMQManagedConnectionObject
{
    private final ServerConnection _serverConnection;

    @MBeanConstructor("Creates an MBean exposing an AMQ Broker 0-10 Connection")
    protected ServerConnectionMBean(final ServerConnection serverConnection) throws NotCompliantMBeanException
    {
        super(serverConnection.getConfig().getAddress());
        _serverConnection = serverConnection;
    }

    @Override
    public ManagedObject getParentObject()
    {
        return _serverConnection.getVirtualHost().getManagedObject();
    }

    @Override
    public String getClientId()
    {
        return _serverConnection.getClientId();
    }

    @Override
    public String getAuthorizedId()
    {
        return _serverConnection.getAuthorizedPrincipal().getName();
    }

    @Override
    public String getVersion()
    {
        return String.valueOf(_serverConnection.getClientVersion());
    }

    @Override
    public String getRemoteAddress()
    {
        return _serverConnection.getConfig().getAddress();
    }

    @Override
    public Date getLastIoTime()
    {
        return new Date(_serverConnection.getLastIoTime().longValue());
    }

    @Override
    public Long getMaximumNumberOfChannels()
    {
        return (long) _serverConnection.getConnectionDelegate().getChannelMax();
    }

    @Override
    public TabularData channels() throws IOException, JMException
    {
        final TabularDataSupport channelsList = new TabularDataSupport(_channelsType);
        final List<AMQSessionModel> list = _serverConnection.getSessionModels();

        for (final AMQSessionModel channel : list)
        {
            final ServerSession session = (ServerSession)channel;
            Object[] itemValues =
                {
                    session.getChannel(),
                    session.isTransactional(),
                    null,
                    session.getUnacknowledgedMessageCount(),
                    session.getBlocking()
                };

            final CompositeData channelData = new CompositeDataSupport(_channelType,
                    COMPOSITE_ITEM_NAMES_DESC.toArray(new String[COMPOSITE_ITEM_NAMES_DESC.size()]), itemValues);
            channelsList.put(channelData);
        }
        return channelsList;
    }

    @Override
    public void commitTransactions(int channelId) throws JMException
    {
        final ServerSession session = (ServerSession)_serverConnection.getSession(channelId);
        if (session == null)
        {
            throw new JMException("The channel (channel Id = " + channelId + ") does not exist");
        }
        else if (session.isTransactional())
        {
            CurrentActor.set(new ManagementActor(getLogActor().getRootMessageLogger()));
            try
            {
                session.commit();
            }
            finally
            {
                CurrentActor.remove();
            }
        }
    }

    @Override
    public void rollbackTransactions(int channelId) throws JMException
    {
        final ServerSession session = (ServerSession)_serverConnection.getSession(channelId);
        if (session == null)
        {
            throw new JMException("The channel (channel Id = " + channelId + ") does not exist");
        }
        else if (session.isTransactional())
        {
            CurrentActor.set(new ManagementActor(getLogActor().getRootMessageLogger()));
            try
            {
                session.rollback();
            }
            finally
            {
                CurrentActor.remove();
            }
        }
    }

    @Override
    public void closeConnection() throws Exception
    {
        _serverConnection.mgmtClose();
    }

    @Override
    public void resetStatistics() throws Exception
    {
        _serverConnection.resetStatistics();
    }

    @Override
    public double getPeakMessageDeliveryRate()
    {
        return _serverConnection.getMessageDeliveryStatistics().getPeak();
    }

    @Override
    public double getPeakDataDeliveryRate()
    {
        return _serverConnection.getDataDeliveryStatistics().getPeak();
    }

    @Override
    public double getMessageDeliveryRate()
    {
        return _serverConnection.getMessageDeliveryStatistics().getRate();
    }

    @Override
    public double getDataDeliveryRate()
    {
        return _serverConnection.getDataDeliveryStatistics().getRate();
    }

    @Override
    public long getTotalMessagesDelivered()
    {
        return _serverConnection.getMessageDeliveryStatistics().getTotal();
    }

    @Override
    public long getTotalDataDelivered()
    {
        return _serverConnection.getDataDeliveryStatistics().getTotal();
    }

    @Override
    public double getPeakMessageReceiptRate()
    {
        return _serverConnection.getMessageReceiptStatistics().getPeak();
    }

    @Override
    public double getPeakDataReceiptRate()
    {
        return _serverConnection.getDataReceiptStatistics().getPeak();
    }

    @Override
    public double getMessageReceiptRate()
    {
        return _serverConnection.getMessageReceiptStatistics().getRate();
    }

    @Override
    public double getDataReceiptRate()
    {
        return _serverConnection.getDataReceiptStatistics().getRate();
    }

    @Override
    public long getTotalMessagesReceived()
    {
        return _serverConnection.getMessageReceiptStatistics().getTotal();
    }

    @Override
    public long getTotalDataReceived()
    {
        return _serverConnection.getDataReceiptStatistics().getTotal();
    }

    @Override
    public boolean isStatisticsEnabled()
    {
        return _serverConnection.isStatisticsEnabled();
    }

    @Override
    public void setStatisticsEnabled(boolean enabled)
    {
        _serverConnection.setStatisticsEnabled(enabled);
    }
}
