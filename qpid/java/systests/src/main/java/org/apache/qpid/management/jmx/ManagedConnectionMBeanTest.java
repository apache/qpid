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
package org.apache.qpid.management.jmx;

import org.apache.commons.lang.StringUtils;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.management.common.mbeans.ManagedConnection;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.management.JMException;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.TabularData;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class ManagedConnectionMBeanTest extends QpidBrokerTestCase
{
    private static final String VIRTUAL_HOST_NAME = "test";

    private JMXTestUtils _jmxUtils;
    private Connection _connection;

    public void setUp() throws Exception
    {
        _jmxUtils = new JMXTestUtils(this);
        _jmxUtils.setUp(); // modifies broker config therefore must be done before super.setUp()
        super.setUp();
        _jmxUtils.open();
    }

    public void tearDown() throws Exception
    {
        if (_jmxUtils != null)
        {
            _jmxUtils.close();
        }
        super.tearDown();
    }

    public void testTransactedSession() throws Exception
    {
        _connection = getConnection();
        final Session session = _connection.createSession(true, Session.SESSION_TRANSACTED);

        receiveTwoMessagesWithoutCommit(session);

        final ManagedConnection mBean = getConnectionMBean();

        // check session details in the row
        final CompositeDataSupport row = getTheOneChannelRow(mBean);
        final Number unackCount = (Number) row.get(ManagedConnection.UNACKED_COUNT);
        final Boolean transactional = (Boolean) row.get(ManagedConnection.TRANSACTIONAL);
        final Boolean flowBlocked = (Boolean) row.get(ManagedConnection.FLOW_BLOCKED);
        assertEquals("Unexpected number of unacknowledged messages", 2, unackCount);
        assertTrue("Unexpected transaction flag", transactional);
        assertFalse("Unexpected value of flow blocked flag", flowBlocked);

        // check that commit advances the lastIoTime
        final Date initialLastIOTime = mBean.getLastIoTime();
        session.commit();
        assertTrue("commit should have caused last IO time to advance", mBean.getLastIoTime().after(initialLastIOTime));

        // check that channels() now returns one session with no unacknowledged messages
        final CompositeDataSupport rowAfterCommit = getTheOneChannelRow(mBean);
        final Number unackCountAfterCommit = (Number) rowAfterCommit.get(ManagedConnection.UNACKED_COUNT);
        assertEquals("Unexpected number of unacknowledged messages", 0, unackCountAfterCommit);
    }

    public void testNumberOfManagedConnectionsMatchesNumberOfClientConnections() throws Exception
    {
        assertEquals("Expected no managed connections", 0, getManagedConnections().size());

        _connection = getConnection();
        assertEquals("Expected one managed connection", 1, getManagedConnections().size());

        _connection.close();
        assertEquals("Expected no managed connections after client connection closed", 0, getManagedConnections().size());
    }

    public void testGetAttributes() throws Exception
    {
        _connection = getConnection();
        final ManagedConnection mBean = getConnectionMBean();

        checkAuthorisedId(mBean);
        checkClientVersion(mBean);
        checkClientId(mBean);
    }

    private void checkAuthorisedId(ManagedConnection mBean) throws Exception
    {
        assertEquals("Unexpected authorized id", GUEST_USERNAME, mBean.getAuthorizedId());
    }

    private void checkClientVersion(ManagedConnection mBean) throws Exception
    {
        String expectedVersion = QpidProperties.getReleaseVersion();
        assertTrue(StringUtils.isNotBlank(expectedVersion));

        assertEquals("Unexpected version", expectedVersion, mBean.getVersion());
    }

    private void checkClientId(ManagedConnection mBean) throws Exception
    {
        String expectedClientId = _connection.getClientID();
        assertTrue(StringUtils.isNotBlank(expectedClientId));

        assertEquals("Unexpected ClientId", expectedClientId, mBean.getClientId());
    }

    private ManagedConnection getConnectionMBean()
    {
        List<ManagedConnection> connections = getManagedConnections();
        assertNotNull("Connection MBean is not found", connections);
        assertEquals("Unexpected number of connection mbeans", 1, connections.size());
        final ManagedConnection mBean = connections.get(0);
        assertNotNull("Connection MBean is null", mBean);
        return mBean;
    }

    private CompositeDataSupport getTheOneChannelRow(final ManagedConnection mBean) throws IOException, JMException
    {
        TabularData channelsData = mBean.channels();
        assertEquals("Unexpected number of rows in channel table", 1, channelsData.size());

        @SuppressWarnings("unchecked")
        final Iterator<CompositeDataSupport> rowItr = (Iterator<CompositeDataSupport>) channelsData.values().iterator();
        final CompositeDataSupport row = rowItr.next();
        return row;
    }

    private void receiveTwoMessagesWithoutCommit(final Session session) throws JMSException, Exception
    {
        final Destination destination = session.createQueue(getTestQueueName());
        final MessageConsumer consumer = session.createConsumer(destination);

        // send two messages ...
        final int numberOfMessages = 2;
        sendMessage(session, destination, numberOfMessages);
        _connection.start();

        // receive both messages but don't commit
        for (int i = 0; i < numberOfMessages; i++)
        {
            final Message m = consumer.receive(1000l);
            assertNotNull("Message " + i + " is not received", m);
        }
    }

    private List<ManagedConnection> getManagedConnections()
    {
        return _jmxUtils.getManagedConnections(VIRTUAL_HOST_NAME);
    }

}
