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
package org.apache.qpid.systest.management.jmx;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.management.JMException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.TabularData;

import org.apache.commons.lang.StringUtils;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.management.common.mbeans.ManagedConnection;
import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class ConnectionManagementTest extends QpidBrokerTestCase
{
    private static final String VIRTUAL_HOST_NAME = "test";

    private JMXTestUtils _jmxUtils;
    private Connection _connection;

    public void setUp() throws Exception
    {
        getBrokerConfiguration().addJmxManagementConfiguration();

        _jmxUtils = new JMXTestUtils(this);

        super.setUp();
        _jmxUtils.open();
    }

    public void tearDown() throws Exception
    {
        try
        {
            if (_jmxUtils != null)
            {
                _jmxUtils.close();
            }
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testNumberOfManagedConnectionsMatchesNumberOfClientConnections() throws Exception
    {
        assertEquals("Expected no managed connections", 0, getManagedConnections().size());

        _connection = getConnection();
        assertEquals("Expected one managed connection", 1, getManagedConnections().size());

        _connection.close();

        // On the 0-10 path, the connection close ok is sent *before* the model is updated, so we need poll
        // to reliable detect the state change.
        int counter = 0;
        while(getManagedConnections().size() > 0 && counter < 50)
        {
            sleep();
            counter++;
        }

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

    public void testNonTransactedSession() throws Exception
    {
        _connection = getConnection();

        boolean transactional = false;
        boolean flowBlocked = false;

        _connection.createSession(transactional, Session.AUTO_ACKNOWLEDGE);

        final ManagedConnection mBean = getConnectionMBean();
        final CompositeDataSupport row = getTheOneChannelRow(mBean);
        assertChannelRowData(row, 0, transactional, flowBlocked);
    }

    public void testTransactedSessionWithUnackMessages() throws Exception
    {
        _connection = getConnection();
        _connection.start();

        boolean transactional = true;
        int numberOfMessages = 2;
        final Session session = _connection.createSession(transactional, Session.SESSION_TRANSACTED);
        final Destination destination = session.createQueue(getTestQueueName());
        final MessageConsumer consumer = session.createConsumer(destination);

        sendMessage(session, destination, numberOfMessages);
        receiveMessagesWithoutCommit(consumer, numberOfMessages);

        final ManagedConnection mBean = getConnectionMBean();
        final CompositeDataSupport row = getTheOneChannelRow(mBean);
        boolean flowBlocked = false;
        assertChannelRowData(row, numberOfMessages, transactional, flowBlocked);

        // check that commit advances the lastIoTime
        final Date initialLastIOTime = mBean.getLastIoTime();
        session.commit();
        assertTrue("commit should have caused last IO time to advance", mBean.getLastIoTime().after(initialLastIOTime));

        // check that channels() now returns one session with no unacknowledged messages
        final CompositeDataSupport rowAfterCommit = getTheOneChannelRow(mBean);
        final Number unackCountAfterCommit = (Number) rowAfterCommit.get(ManagedConnection.UNACKED_COUNT);
        assertEquals("Unexpected number of unacknowledged messages", 0, unackCountAfterCommit);
    }


    public void testProducerFlowBlocked() throws Exception
    {
        _connection = getConnection();
        _connection.start();

        String queueName = getTestQueueName();
        Session session = _connection.createSession(true, Session.SESSION_TRANSACTED);
        Queue queue = session.createQueue(queueName);
        createQueueOnBroker(session, queue);

        ManagedQueue managedQueue = _jmxUtils.getManagedQueue(queueName);
        managedQueue.setFlowResumeCapacity(DEFAULT_MESSAGE_SIZE * 2l);
        managedQueue.setCapacity(DEFAULT_MESSAGE_SIZE * 3l);

        final ManagedConnection managedConnection = getConnectionMBean();

        // Check that producer flow is not block before test
        final CompositeDataSupport rowBeforeSend = getTheOneChannelRow(managedConnection);
        assertFlowBlocked(rowBeforeSend, false);


        // Check that producer flow does not become block too soon
        sendMessage(session, queue, 3);
        final CompositeDataSupport rowBeforeFull = getTheOneChannelRow(managedConnection);
        assertFlowBlocked(rowBeforeFull, false);

        // Fourth message will over-fill the queue (but as we are not sending more messages, client thread wont't block)
        sendMessage(session, queue, 1);
        final CompositeDataSupport rowAfterFull = getTheOneChannelRow(managedConnection);
        assertFlowBlocked(rowAfterFull, true);

        // Consume two to bring the queue down to the resume capacity
        MessageConsumer consumer = session.createConsumer(queue);
        assertNotNull("Could not receive first message", consumer.receive(1000));
        assertNotNull("Could not receive second message", consumer.receive(1000));
        session.commit();

        // Check that producer flow is no longer blocked
        final CompositeDataSupport rowAfterReceive = getTheOneChannelRow(managedConnection);
        assertFlowBlocked(rowAfterReceive, false);
    }

    private void createQueueOnBroker(Session session, Destination destination) throws JMSException
    {
        session.createConsumer(destination).close(); // Create a consumer only to cause queue creation
    }

    private void assertChannelRowData(final CompositeData row, int unacknowledgedMessages, boolean isTransactional, boolean flowBlocked)
    {
        assertNotNull(row);
        assertEquals("Unexpected transactional flag", isTransactional, row.get(ManagedConnection.TRANSACTIONAL));
        assertEquals("Unexpected unacknowledged message count", unacknowledgedMessages, row.get(ManagedConnection.UNACKED_COUNT));
        assertEquals("Unexpected flow blocked", flowBlocked, row.get(ManagedConnection.FLOW_BLOCKED));
    }

    private void assertFlowBlocked(final CompositeData row, boolean flowBlocked)
    {
        assertNotNull(row);
        assertEquals("Unexpected flow blocked", flowBlocked, row.get(ManagedConnection.FLOW_BLOCKED));
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

    private List<ManagedConnection> getManagedConnections()
    {
        return _jmxUtils.getManagedConnections(VIRTUAL_HOST_NAME);
    }

    private CompositeDataSupport getTheOneChannelRow(final ManagedConnection mBean) throws Exception
    {
        TabularData channelsData = getChannelsDataWithRetry(mBean);

        assertEquals("Unexpected number of rows in channel table", 1, channelsData.size());

        @SuppressWarnings("unchecked")
        final Iterator<CompositeDataSupport> rowItr = (Iterator<CompositeDataSupport>) channelsData.values().iterator();
        final CompositeDataSupport row = rowItr.next();
        return row;
    }

    private void receiveMessagesWithoutCommit(final MessageConsumer consumer, int numberOfMessages) throws Exception
    {
        for (int i = 0; i < numberOfMessages; i++)
        {
            final Message m = consumer.receive(1000l);
            assertNotNull("Message " + i + " is not received", m);
        }
    }

    private TabularData getChannelsDataWithRetry(final ManagedConnection mBean)
            throws IOException, JMException
    {
        TabularData channelsData = mBean.channels();
        int retries = 0;
        while(channelsData.size() == 0 && retries < 5)
        {
            sleep();
            channelsData = mBean.channels();
            retries++;
        }
        return channelsData;
    }

    private void sleep()
    {
        try
        {
            Thread.sleep(50);
        }
        catch (InterruptedException ie)
        {
            Thread.currentThread().interrupt();
        }
    }}
