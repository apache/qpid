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

import java.io.IOException;
import java.util.Date;
import javax.management.JMException;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;
import org.apache.qpid.management.common.mbeans.ManagedConnection;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.model.Connection;

public class ConnectionMBean extends AbstractStatisticsGatheringMBean<Connection> implements ManagedConnection
{
    private final VirtualHostMBean _virtualHostMBean;

    public ConnectionMBean(Connection conn, VirtualHostMBean virtualHostMBean) throws JMException
    {
        super(ManagedConnection.class, ManagedConnection.TYPE, virtualHostMBean.getRegistry(), conn);
        _virtualHostMBean = virtualHostMBean;
        register();
    }

    public String getObjectInstanceName()
    {
        return ObjectName.quote(getRemoteAddress());
    }

    @Override
    public ManagedObject getParentObject()
    {
        return _virtualHostMBean;
    }

    public String getClientId()
    {
        return (String) getConfiguredObject().getAttribute(Connection.CLIENT_ID);
    }

    public String getAuthorizedId()
    {
        return (String) getConfiguredObject().getAttribute(Connection.PRINCIPAL);
    }

    public String getVersion()
    {
        return (String) getConfiguredObject().getAttribute(Connection.CLIENT_VERSION);
    }

    public String getRemoteAddress()
    {
        return (String) getConfiguredObject().getAttribute(Connection.REMOTE_ADDRESS);
    }

    public Date getLastIoTime()
    {
        return null;  // TODO - Implement
    }

    public Long getMaximumNumberOfChannels()
    {
        return (Long) getConfiguredObject().getAttribute(Connection.SESSION_COUNT_LIMIT);
    }

    public TabularData channels() throws IOException, JMException
    {
        return null;  // TODO - Implement
    }

    public void commitTransactions(int channelId) throws JMException
    {
        // TODO - Implement
    }

    public void rollbackTransactions(int channelId) throws JMException
    {
        // TODO - Implement
    }

    public void closeConnection() throws Exception
    {
        // TODO - Implement
    }


    public void setStatisticsEnabled(boolean enabled)
    {
        // TODO - Implement
        updateStats();
    }
}
