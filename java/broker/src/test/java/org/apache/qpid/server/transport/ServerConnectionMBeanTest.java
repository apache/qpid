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

import org.apache.qpid.management.common.mbeans.ManagedConnection;
import org.apache.qpid.server.configuration.MockConnectionConfig;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.util.InternalBrokerBaseCase;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.Binary;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.Session;

import javax.management.JMException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class ServerConnectionMBeanTest extends InternalBrokerBaseCase
{
    private ServerConnection _serverConnection;
    private ServerSessionMock _serverSession;
    private ServerConnectionMBean _mbean;
    private List<Session> _sessions = new ArrayList<Session>();

    @Override
    public  void setUp() throws Exception
    {
        super.setUp();

        final VirtualHost vhost = ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost("test");
        _serverConnection = new ServerConnection(1)
        {
            protected Collection<Session> getChannels()
            {
                return _sessions;
            }
            public Session getSession(int channelId)
            {
                for(Session session : _sessions)
                {
                    if (session.getChannel() == channelId)
                    {
                        return session;
                    }
                }
                return null;
            }
            @Override
            public AtomicLong getLastIoTime()
            {
                return new AtomicLong(1);
            }
        };
        final MockConnectionConfig config = new MockConnectionConfig(UUID.randomUUID(), null, null,
                                                                    false, 1, vhost, "address", Boolean.TRUE, Boolean.TRUE, Boolean.TRUE,
                                                                    "authid", "remoteProcessName", new Integer(1967), new Integer(1970), vhost.getConfigStore(), Boolean.FALSE);
        _serverConnection.setConnectionConfig(config);
        _serverConnection.setVirtualHost(vhost);
        _serverConnection.setConnectionDelegate(new ServerConnectionDelegate(getRegistry(), ""));
        _serverSession = new ServerSessionMock(_serverConnection, 1);
        _mbean = (ServerConnectionMBean) _serverConnection.getManagedObject();
    }

    public void testChannels() throws Exception
    {
        // check the channel count is correct
        TabularData tabularData = _mbean.channels();

        int channelCount = tabularData.size();
        assertEquals("Unexpected number of channels",1,channelCount);
        _sessions.add(new ServerSession(_serverConnection, new ServerSessionDelegate(),
                        new Binary(getName().getBytes()), 2 , _serverConnection.getConfig()));

        channelCount = _mbean.channels().size();
        assertEquals("Unexpected number of channels",2,channelCount);

        final CompositeData chanresult = tabularData.get(new Integer[]{1});
        assertNotNull(chanresult);
        assertEquals("Unexpected channel id", new Integer(1),(Integer)chanresult.get(ManagedConnection.CHAN_ID));
        assertNull("Unexpected default queue", chanresult.get(ManagedConnection.DEFAULT_QUEUE));
        assertFalse("Unexpected transactional flag", (Boolean)chanresult.get(ManagedConnection.TRANSACTIONAL));
        assertFalse("Flow should have been blocked", (Boolean)chanresult.get(ManagedConnection.FLOW_BLOCKED));
        assertEquals("Unexpected unack'd count", new Integer(1967), (Integer)chanresult.get(ManagedConnection.UNACKED_COUNT));
    }

    public void testMaxChannels() throws Exception
    {
        _serverConnection.getConnectionDelegate().setChannelMax(10001);
        assertEquals("Max channels not got correctly", new Long(10001), _mbean.getMaximumNumberOfChannels());
    }

    public void testRollback() throws Exception
    {
        _mbean.rollbackTransactions(1);
        assertFalse("Rollback performed despite not being transacted", _serverSession.isRolledback());

        _serverSession.setTransactional(true);
        _mbean.rollbackTransactions(1);
        assertTrue("Rollback not performed", _serverSession.isRolledback());

        try
        {
            _mbean.rollbackTransactions(2);
            fail("Exception expected");
        }
        catch (JMException jme)
        {
            //pass
        }
    }

    public void testCommit() throws Exception
    {
        _mbean.commitTransactions(1);
        assertFalse("Commit performed despite not being transacted", _serverSession.isCommitted());

        _serverSession.setTransactional(true);
        _mbean.commitTransactions(1);
        assertTrue("Commit not performed", _serverSession.isCommitted());

        try
        {
            _mbean.commitTransactions(2);
            fail("Exception expected");
        }
        catch (JMException jme)
        {
            //pass
        }
    }

    public void testGetName()
    {
        assertEquals("Unexpected Object Instance Name", "\"address\"", _mbean.getObjectInstanceName());
    }

    public void testEnableStatistics()
    {
        assertFalse("Unexpected statistics enable flag", _mbean.isStatisticsEnabled());
        _mbean.setStatisticsEnabled(true);
        assertTrue("Unexpected statistics enable flag", _mbean.isStatisticsEnabled());
    }

    public void testLastIOTime()
    {
        assertEquals("Unexpected last IO time", new Date(1), _mbean.getLastIoTime());
    }

    private class ServerSessionMock extends ServerSession
    {
        private int _channelId = 0;
        private boolean _committed = false;
        private boolean _rolledback = false;
        private boolean _transacted = false;

        ServerSessionMock(Connection connection, int channelId)
        {
            super(connection, new ServerSessionDelegate(), new Binary(String.valueOf(channelId).getBytes()), 1 , _serverConnection.getConfig());
            _channelId = channelId;
            _sessions.add(this);
        }

        public int getChannel()
        {
            return _channelId;
        }

        @Override
        public void commit()
        {
            _committed = true;
        }

        @Override
        public void rollback()
        {
            _rolledback = true;
        }

        public boolean isCommitted()
        {
            return _committed;
        }

        public boolean isRolledback()
        {
            return _rolledback;
        }

        @Override
        public int getUnacknowledgedMessageCount()
        {
            return 1967;
        }

        public boolean isTransactional()
        {
            return _transacted;
        }

        public void setTransactional(boolean transacted)
        {
            _transacted = transacted;
        }
    }
}
