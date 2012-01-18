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

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.management.JMException;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.TabularData;

import org.apache.qpid.client.AMQSession;
import org.apache.qpid.management.common.mbeans.ManagedConnection;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManagedConnectionMBeanTest extends QpidBrokerTestCase
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedConnectionMBeanTest.class);

    /**
     * JMX helper.
     */
    private JMXTestUtils _jmxUtils;
    private Connection _connection;

    public void setUp() throws Exception
    {
        _jmxUtils = new JMXTestUtils(this);
        _jmxUtils.setUp();
        super.setUp();
        _jmxUtils.open();
        _connection = getConnection();
    }

    public void tearDown() throws Exception
    {
        if (_jmxUtils != null)
        {
            _jmxUtils.close();
        }
        super.tearDown();
    }

    public void testChannels() throws Exception
    {
        final String queueName = getTestQueueName();

        final Session session = _connection.createSession(true, Session.SESSION_TRANSACTED);
        final Destination destination = session.createQueue(queueName);
        final MessageConsumer consumer = session.createConsumer(destination);

        final int numberOfMessages = 2;
        sendMessage(session, destination, numberOfMessages);
        _connection.start();

        for (int i = 0; i < numberOfMessages; i++)
        {
            final Message m = consumer.receive(1000l);
            assertNotNull("Message " + i + " is not received", m);
        }

        List<ManagedConnection> connections = _jmxUtils.getManagedConnections("test");
        assertNotNull("Connection MBean is not found", connections);
        assertEquals("Unexpected number of connection mbeans", 1, connections.size());
        final ManagedConnection mBean = connections.get(0);
        assertNotNull("Connection MBean is null", mBean);

        TabularData channelsData = mBean.channels();
        assertNotNull("Channels data are null", channelsData);
        assertEquals("Unexpected number of rows in channel table", 1, channelsData.size());

        final Iterator<CompositeDataSupport> rowItr = (Iterator<CompositeDataSupport>) channelsData.values().iterator();
        final CompositeDataSupport row = rowItr.next();
        Number unackCount = (Number) row.get(ManagedConnection.UNACKED_COUNT);
        final Boolean transactional = (Boolean) row.get(ManagedConnection.TRANSACTIONAL);
        final Boolean flowBlocked = (Boolean) row.get(ManagedConnection.FLOW_BLOCKED);
        assertNotNull("Channel should have unacknowledged messages", unackCount);
        assertEquals("Unexpected number of unacknowledged messages", 2, unackCount.intValue());
        assertNotNull("Channel should have transaction flag", transactional);
        assertTrue("Unexpected transaction flag", transactional);
        assertNotNull("Channel should have flow blocked flag", flowBlocked);
        assertFalse("Unexpected value of flow blocked flag", flowBlocked);

        final Date initialLastIOTime = mBean.getLastIoTime();
        session.commit();
        assertTrue("Last IO time should have been updated", mBean.getLastIoTime().after(initialLastIOTime));

        channelsData = mBean.channels();
        assertNotNull("Channels data are null", channelsData);
        assertEquals("Unexpected number of rows in channel table", 1, channelsData.size());

        final Iterator<CompositeDataSupport> rowItr2 = (Iterator<CompositeDataSupport>) channelsData.values().iterator();
        final CompositeDataSupport row2 = rowItr2.next();
        unackCount = (Number) row2.get(ManagedConnection.UNACKED_COUNT);
        assertNotNull("Channel should have unacknowledged messages", unackCount);
        assertEquals("Unexpected number of anacknowledged messages", 0, unackCount.intValue());

        _connection.close();

        LOGGER.debug("Querying JMX for number of open connections");
        connections = _jmxUtils.getManagedConnections("test");
        assertNotNull("Connection MBean is not found", connections);
        assertEquals("Unexpected number of connection mbeans after connection closed", 0, connections.size());
    }

    public void testCommit() throws Exception
    {
        final String queueName = getTestQueueName();

        final Session consumerSession = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final Session producerSession = _connection.createSession(true, Session.SESSION_TRANSACTED);
        final Destination destination = producerSession.createQueue(queueName);
        final MessageConsumer consumer = consumerSession.createConsumer(destination);
        final MessageProducer producer = producerSession.createProducer(destination);

        _connection.start();

        List<ManagedConnection> connections = _jmxUtils.getManagedConnections("test");
        assertNotNull("Connection MBean is not found", connections);
        assertEquals("Unexpected number of connection mbeans", 1, connections.size());
        final ManagedConnection mBean = connections.get(0);
        assertNotNull("Connection MBean is null", mBean);

        final int numberOfMessages = 2;
        for (int i = 0; i < numberOfMessages; i++)
        {
            producer.send(producerSession.createTextMessage("Test " + i));
        }

        // sync to make sure that messages are received on the broker
        // before we commit via JMX
        ((AMQSession<?, ?>) producerSession).sync();

        Message m = consumer.receive(500l);
        assertNull("Unexpected message received", m);

        Number channelId = getFirstTransactedChannelId(mBean, 2);
        mBean.commitTransactions(channelId.intValue());

        for (int i = 0; i < numberOfMessages; i++)
        {
            m = consumer.receive(1000l);
            assertNotNull("Message " + i + " is not received", m);
            assertEquals("Unexpected message received at " + i, "Test " + i, ((TextMessage) m).getText());
        }
        producerSession.commit();
        m = consumer.receive(500l);
        assertNull("Unexpected message received", m);
    }

    protected Number getFirstTransactedChannelId(final ManagedConnection mBean, int channelNumber) throws IOException, JMException
    {
        TabularData channelsData = mBean.channels();
        assertNotNull("Channels data are null", channelsData);
        assertEquals("Unexpected number of rows in channel table", channelNumber, channelsData.size());
        final Iterator<CompositeDataSupport> rowItr = (Iterator<CompositeDataSupport>) channelsData.values().iterator();
        while (rowItr.hasNext())
        {
            final CompositeDataSupport row = rowItr.next();
            Boolean transacted = (Boolean) row.get(ManagedConnection.TRANSACTIONAL);
            if (transacted.booleanValue())
            {
                return (Number) row.get(ManagedConnection.CHAN_ID);
            }
        }
        return null;
    }

    public void testRollback() throws Exception
    {
        final String queueName = getTestQueueName();

        final Session consumerSession = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final Session producerSession = _connection.createSession(true, Session.SESSION_TRANSACTED);
        final Destination destination = producerSession.createQueue(queueName);
        final MessageConsumer consumer = consumerSession.createConsumer(destination);
        final MessageProducer producer = producerSession.createProducer(destination);

        List<ManagedConnection> connections = _jmxUtils.getManagedConnections("test");
        assertNotNull("Connection MBean is not found", connections);
        assertEquals("Unexpected number of connection mbeans", 1, connections.size());
        final ManagedConnection mBean = connections.get(0);
        assertNotNull("Connection MBean is null", mBean);

        final int numberOfMessages = 2;
        for (int i = 0; i < numberOfMessages; i++)
        {
            producer.send(producerSession.createTextMessage("Test " + i));
        }

        // sync to make sure that messages are received on the broker
        // before we rollback via JMX
        ((AMQSession<?, ?>) producerSession).sync();

        Number channelId = getFirstTransactedChannelId(mBean, 2);
        mBean.rollbackTransactions(channelId.intValue());

        Message m = consumer.receive(1000l);
        assertNull("Unexpected message received", m);

        producerSession.commit();

        _connection.start();
        m = consumer.receive(1000l);
        assertNull("Unexpected message received", m);
    }

    public void testAuthorisedId() throws Exception
    {
        List<ManagedConnection> connections = _jmxUtils.getManagedConnections("test");
        assertNotNull("Connection MBean is not found", connections);
        assertEquals("Unexpected number of connection mbeans", 1, connections.size());
        final ManagedConnection mBean = connections.get(0);
        assertNotNull("Connection MBean is null", mBean);
        assertEquals("Unexpected authorized id", "guest", mBean.getAuthorizedId());
    }
}
