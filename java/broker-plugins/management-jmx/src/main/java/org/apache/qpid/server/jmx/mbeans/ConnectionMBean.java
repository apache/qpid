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
import java.util.Collection;
import java.util.Date;
import javax.management.JMException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.apache.qpid.management.common.mbeans.ManagedConnection;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Session;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class ConnectionMBean extends AbstractStatisticsGatheringMBean<Connection> implements ManagedConnection
{
    private static final OpenType[] CHANNEL_ATTRIBUTE_TYPES =
            { SimpleType.INTEGER, SimpleType.BOOLEAN, SimpleType.STRING, SimpleType.INTEGER, SimpleType.BOOLEAN };
    private static final CompositeType CHANNEL_TYPE;
    private static final TabularType CHANNELS_TYPE;

    static
    {
        try
        {
            CHANNEL_TYPE = new CompositeType("Channel", "Channel Details", COMPOSITE_ITEM_NAMES_DESC.toArray(new String[COMPOSITE_ITEM_NAMES_DESC.size()]),
                                             COMPOSITE_ITEM_NAMES_DESC.toArray(new String[COMPOSITE_ITEM_NAMES_DESC.size()]),
                                             CHANNEL_ATTRIBUTE_TYPES);
            CHANNELS_TYPE = new TabularType("Channels", "Channels", CHANNEL_TYPE, (String[]) TABULAR_UNIQUE_INDEX.toArray(new String[TABULAR_UNIQUE_INDEX.size()]));
        }
        catch (JMException ex)
        {
            // This is not expected to ever occur.
            throw new ServerScopedRuntimeException("Got JMException in static initializer.", ex);
        }
    }


    private final VirtualHostMBean _virtualHostMBean;

    public ConnectionMBean(Connection conn, VirtualHostMBean virtualHostMBean) throws JMException
    {
        super(ManagedConnection.class, ManagedConnection.TYPE, virtualHostMBean.getRegistry(), conn);
        _virtualHostMBean = virtualHostMBean;
        register();
    }

    @Override
    protected long getBytesOut()
    {
        return getConfiguredObject().getBytesOut();
    }

    @Override
    protected long getBytesIn()
    {
        return getConfiguredObject().getBytesIn();
    }

    @Override
    protected long getMessagesOut()
    {
        return getConfiguredObject().getMessagesOut();
    }

    @Override
    protected long getMessagesIn()
    {
        return getConfiguredObject().getMessagesIn();
    }

    public String getObjectInstanceName()
    {
        return ObjectName.quote(getConfiguredObject().getName());
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
        return new Date(getConfiguredObject().getLastIoTime());
    }

    public Long getMaximumNumberOfChannels()
    {
        return (Long) getConfiguredObject().getAttribute(Connection.SESSION_COUNT_LIMIT);
    }

    public TabularData channels() throws IOException, JMException
    {
        TabularDataSupport sessionTable = new TabularDataSupport(CHANNELS_TYPE);
        Collection<Session> list = getConfiguredObject().getSessions();

        for (Session session : list)
        {

            Long txnBegins = session.getLocalTransactionBegins();
            Integer channelId = (Integer) session.getAttribute(Session.CHANNEL_ID);
            int unacknowledgedSize = (int) session.getUnacknowledgedMessages();
            boolean blocked = (Boolean) session.getAttribute(Session.PRODUCER_FLOW_BLOCKED);
            boolean isTransactional = (txnBegins>0l);

            Object[] itemValues =
                    {
                            channelId,
                            isTransactional,
                            null, // TODO - default queue (which is meaningless)
                            unacknowledgedSize,
                            blocked
                    };

            CompositeData sessionData = new CompositeDataSupport(CHANNEL_TYPE,
                                                                 COMPOSITE_ITEM_NAMES_DESC.toArray(new String[COMPOSITE_ITEM_NAMES_DESC.size()]), itemValues);
            sessionTable.put(sessionData);
        }

        return sessionTable;
    }

    public void commitTransactions(int channelId) throws JMException
    {
        throw buildUnsupportedException();
    }

    public void rollbackTransactions(int channelId) throws JMException
    {
        throw buildUnsupportedException();
    }

    public void closeConnection() throws Exception
    {
        getConfiguredObject().delete();
    }

    public boolean isStatisticsEnabled()
    {
        return true;
    }

    public void setStatisticsEnabled(boolean enabled)
    {
        updateStats();
    }

    private JMException buildUnsupportedException() throws JMException
    {
        String msg = "Operation not supported";
        JMException jmException = new JMException(msg);
        jmException.initCause(new UnsupportedOperationException(msg));
        return jmException;
    }
}
