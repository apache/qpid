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

package org.apache.qpid.server.jmx.mbeans;

import org.apache.qpid.management.common.mbeans.ManagedConnection;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperationParameter;
import org.apache.qpid.server.jmx.AMQManagedObject;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.model.Connection;

import javax.management.JMException;
import javax.management.openmbean.TabularData;
import java.io.IOException;
import java.util.Date;

public class ConnectionMBean extends AMQManagedObject implements ManagedConnection
{
    private final VirtualHostMBean _virtualHostMBean;
    private final Connection _connection;

    public ConnectionMBean(Connection conn, VirtualHostMBean virtualHostMBean) throws JMException
    {
        super(ManagedConnection.class, ManagedConnection.TYPE, virtualHostMBean.getRegistry());
        _virtualHostMBean = virtualHostMBean;
        _connection = conn;
        register();
    }

    public String getObjectInstanceName()
    {
        return _connection.getName();
    }

    @Override
    public ManagedObject getParentObject()
    {
        return _virtualHostMBean;
    }

    public String getClientId()
    {
        return null;  // TODO - Implement
    }

    public String getAuthorizedId()
    {
        return null;  // TODO - Implement
    }

    public String getVersion()
    {
        return null;  // TODO - Implement
    }

    public String getRemoteAddress()
    {
        return null;  // TODO - Implement
    }

    public Date getLastIoTime()
    {
        return null;  // TODO - Implement
    }

    public Long getMaximumNumberOfChannels()
    {
        return null;  // TODO - Implement
    }

    public TabularData channels() throws IOException, JMException
    {
        return null;  // TODO - Implement
    }

    public void commitTransactions(
            @MBeanOperationParameter(name = "channel Id", description = "channel Id") int channelId) throws JMException
    {
        // TODO - Implement
    }

    public void rollbackTransactions(
            @MBeanOperationParameter(name = "channel Id", description = "channel Id") int channelId) throws JMException
    {
        // TODO - Implement
    }

    public void closeConnection() throws Exception
    {
        // TODO - Implement
    }

    public void resetStatistics() throws Exception
    {
        // TODO - Implement
    }

    public double getPeakMessageDeliveryRate()
    {
        return 0;  // TODO - Implement
    }

    public double getPeakDataDeliveryRate()
    {
        return 0;  // TODO - Implement
    }

    public double getMessageDeliveryRate()
    {
        return 0;  // TODO - Implement
    }

    public double getDataDeliveryRate()
    {
        return 0;  // TODO - Implement
    }

    public long getTotalMessagesDelivered()
    {
        return 0;  // TODO - Implement
    }

    public long getTotalDataDelivered()
    {
        return 0;  // TODO - Implement
    }

    public double getPeakMessageReceiptRate()
    {
        return 0;  // TODO - Implement
    }

    public double getPeakDataReceiptRate()
    {
        return 0;  // TODO - Implement
    }

    public double getMessageReceiptRate()
    {
        return 0;  // TODO - Implement
    }

    public double getDataReceiptRate()
    {
        return 0;  // TODO - Implement
    }

    public long getTotalMessagesReceived()
    {
        return 0;  // TODO - Implement
    }

    public long getTotalDataReceived()
    {
        return 0;  // TODO - Implement
    }

    public boolean isStatisticsEnabled()
    {
        return false;  // TODO - Implement
    }

    public void setStatisticsEnabled(boolean enabled)
    {
        // TODO - Implement
    }
}
