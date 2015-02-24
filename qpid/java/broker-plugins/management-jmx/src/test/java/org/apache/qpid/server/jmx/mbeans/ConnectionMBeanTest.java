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
package org.apache.qpid.server.jmx.mbeans;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;

import javax.management.JMException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import junit.framework.TestCase;

import org.apache.qpid.management.common.mbeans.ManagedConnection;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Session;

public class ConnectionMBeanTest extends TestCase
{
    private ConnectionMBean _connectionMBean;
    private Connection _mockConnection;
    private VirtualHostMBean _mockVirtualHostMBean;
    private ManagedObjectRegistry _mockManagedObjectRegistry;

    @Override
    protected void setUp() throws Exception
    {
        _mockConnection = mock(Connection.class);
        _mockVirtualHostMBean = mock(VirtualHostMBean.class);

        _mockManagedObjectRegistry = mock(ManagedObjectRegistry.class);
        when(_mockVirtualHostMBean.getRegistry()).thenReturn(_mockManagedObjectRegistry);

        _connectionMBean = new ConnectionMBean(_mockConnection, _mockVirtualHostMBean);
    }

    public void testMBeanRegistersItself() throws Exception
    {
        ConnectionMBean connectionMBean = new ConnectionMBean(_mockConnection, _mockVirtualHostMBean);
        verify(_mockManagedObjectRegistry).registerObject(connectionMBean);
    }

    public void testCloseConnection() throws Exception
    {
        _connectionMBean.closeConnection();
        verify(_mockConnection).delete();
    }

    public void testCommitTransactions()
    {
        try
        {
            _connectionMBean.commitTransactions(0);
            fail("Exception not thrown");
        }
        catch(JMException e)
        {
            assertTrue("Cause should be an UnsupportedOperationException", e.getCause() instanceof UnsupportedOperationException);
        }
    }

    public void testRollbackTransactions()
    {
        try
        {
            _connectionMBean.rollbackTransactions(0);
            fail("Exception not thrown");
        }
        catch(JMException e)
        {
            assertTrue("Cause should be an UnsupportedOperationException", e.getCause() instanceof UnsupportedOperationException);
        }
    }

    public void testChannelsWithSingleTransactionalSession() throws Exception
    {
        int channelId = 10;
        int unacknowledgedMessages = 2;
        long localTransactionBegins = 1;
        boolean transactional = true;
        boolean blocked = false;

        Session mockSession = createMockedSession(channelId, unacknowledgedMessages, localTransactionBegins, blocked);

        when(_mockConnection.getSessions()).thenReturn(Collections.singletonList(mockSession));

        TabularData table = _connectionMBean.channels();
        assertEquals("Unexpected number of rows in table", 1, table.size());

        final CompositeData row = table.get(new Integer[] {channelId} );
        assertChannelRow(row, channelId, unacknowledgedMessages, transactional, blocked);
    }

    public void testChannelsWithSingleNonTransactionalSession() throws Exception
    {
        int channelId = 10;
        int unacknowledgedMessages = 2;
        long localTransactionBegins = 0;
        boolean transactional  = false;
        boolean blocked = false;

        Session mockSession = createMockedSession(channelId, unacknowledgedMessages, localTransactionBegins, blocked);

        when(_mockConnection.getSessions()).thenReturn(Collections.singletonList(mockSession));

        TabularData table = _connectionMBean.channels();
        assertEquals("Unexpected number of rows in table", 1, table.size());

        final CompositeData row = table.get(new Integer[] {channelId} );
        assertChannelRow(row, channelId, unacknowledgedMessages, transactional, blocked);
    }

    public void testChannelsWithSessionBlocked() throws Exception
    {
        int channelId = 10;
        int unacknowledgedMessages = 2;
        long localTransactionBegins = 0;
        boolean transactional  = false;
        boolean blocked = true;

        Session mockSession = createMockedSession(channelId, unacknowledgedMessages, localTransactionBegins, blocked);

        when(_mockConnection.getSessions()).thenReturn(Collections.singletonList(mockSession));

        TabularData table = _connectionMBean.channels();
        assertEquals("Unexpected number of rows in table", 1, table.size());

        final CompositeData row = table.get(new Integer[] {channelId} );
        assertChannelRow(row, channelId, unacknowledgedMessages, transactional, blocked);
    }

    public void testParentObjectIsVirtualHost()
    {
        ManagedObject parent = _connectionMBean.getParentObject();
        assertEquals(_mockVirtualHostMBean, parent);
    }

    public void testGetObjectInstanceName()
    {
        String name = "[1] 127.0.0.1:5555";
        String quotedRemoteAddress = "\"" + name +"\"";
        when(_mockConnection.getName()).thenReturn(name);
        String objectInstanceName = _connectionMBean.getObjectInstanceName();
        assertEquals(quotedRemoteAddress, objectInstanceName);
    }

    public void testGetAuthorizedId() throws Exception
    {
        assertAttribute("authorizedId", "testAuthorizedId", Connection.PRINCIPAL);
    }

    public void testGetClientId() throws Exception
    {
        assertAttribute("clientId", "testClientId", Connection.CLIENT_ID);
    }

    public void testGetVersion() throws Exception
    {
        assertAttribute("version", "testVersion", Connection.CLIENT_VERSION);
    }

    public void testGetRemoteAddress() throws Exception
    {
        assertAttribute("remoteAddress", "testRemoteAddress", Connection.REMOTE_ADDRESS);
    }

    public void testGetLastIoTime()
    {
        when(_mockConnection.getLastIoTime()).thenReturn(1l);


        Object actualValue = _connectionMBean.getLastIoTime();
        assertEquals("Unexpected lastIoTime", new Date(1L), actualValue);
    }

    public void testGetMaximumNumberOfChannels() throws Exception
    {
        assertAttribute("maximumNumberOfChannels", 10l, Connection.SESSION_COUNT_LIMIT);
    }

    public void testIsStatisticsEnabledAlwaysTrue() throws Exception
    {
        assertTrue(_connectionMBean.isStatisticsEnabled());
    }

    private void assertAttribute(String jmxAttributeName, Object expectedValue, String underlyingAttributeName) throws Exception
    {
        when(_mockConnection.getAttribute(underlyingAttributeName)).thenReturn(expectedValue);
        MBeanTestUtils.assertMBeanAttribute(_connectionMBean, jmxAttributeName, expectedValue);
    }

    private void assertChannelRow(final CompositeData row, int channelId, int unacknowledgedMessages, boolean isTransactional, boolean flowBlocked)
    {
        assertNotNull("No row for channel id " + channelId, row);
        assertEquals("Unexpected channel id", channelId, row.get(ManagedConnection.CHAN_ID));
        assertEquals("Unexpected transactional flag", isTransactional, row.get(ManagedConnection.TRANSACTIONAL));
        assertEquals("Unexpected unacknowledged message count", unacknowledgedMessages, row.get(ManagedConnection.UNACKED_COUNT));
        assertEquals("Unexpected flow blocked", flowBlocked, row.get(ManagedConnection.FLOW_BLOCKED));
    }

    private Session createMockedSession(int channelId, int unacknowledgedMessages, long localTransactionBegins, boolean blocked)
    {
        Session mockSession = mock(Session.class);
        when(mockSession.getLocalTransactionBegins()).thenReturn(localTransactionBegins);
        when(mockSession.getUnacknowledgedMessages()).thenReturn((long)unacknowledgedMessages);

        when(mockSession.getStatistics()).thenReturn(Collections.emptyMap());
        when(mockSession.getAttribute(Session.CHANNEL_ID)).thenReturn(channelId);
        when(mockSession.getAttribute(Session.PRODUCER_FLOW_BLOCKED)).thenReturn(blocked);
        return mockSession;
    }
}
