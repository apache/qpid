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
 *
 */
package org.apache.qpid.systest.management.jmx;

import java.util.List;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.management.common.mbeans.ManagedConnection;
import org.apache.qpid.management.common.mbeans.ServerInformation;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class StatisticsTest extends QpidBrokerTestCase
{
    private static final String TEST_VIRTUALHOST1 = "test1";
    private static final String TEST_VIRTUALHOST2 = "test2";

    private static final String TEST_USER = "admin";
    private static final String TEST_PASSWORD = "admin";
    private static final int MESSAGE_COUNT_TEST = 5;
    private static final int MESSAGE_COUNT_DEV = 9;

    private JMXTestUtils _jmxUtils;
    private Connection _vhost1Connection, _vhost2Connection;
    private Session _vhost1Session, _vhost2Session;
    private Queue _vhost1Queue, _vhost2Queue;
    protected String _brokerUrl;

    @Override
    public void setUp() throws Exception
    {
        createTestVirtualHost(0, TEST_VIRTUALHOST1);
        createTestVirtualHost(0, TEST_VIRTUALHOST2);

        _jmxUtils = new JMXTestUtils(this, TEST_USER, TEST_PASSWORD);
        _jmxUtils.setUp();

        super.setUp();

        _brokerUrl = getBroker().toString();
        _vhost1Connection = new AMQConnection(_brokerUrl, TEST_USER, TEST_PASSWORD, "clientid", TEST_VIRTUALHOST1);
        _vhost2Connection = new AMQConnection(_brokerUrl, TEST_USER, TEST_PASSWORD, "clientid", TEST_VIRTUALHOST2);
        _vhost1Connection.start();
        _vhost2Connection.start();

        _vhost1Session = _vhost1Connection.createSession(true, Session.SESSION_TRANSACTED);
        _vhost2Session = _vhost2Connection.createSession(true, Session.SESSION_TRANSACTED);

        _vhost1Queue = _vhost2Session.createQueue(getTestQueueName());
        _vhost2Queue = _vhost1Session.createQueue(getTestQueueName());

        //Create queues by opening and closing consumers
        final MessageConsumer vhost1Consumer = _vhost1Session.createConsumer(_vhost2Queue);
        vhost1Consumer.close();
        final MessageConsumer vhost2Consumer = _vhost2Session.createConsumer(_vhost1Queue);
        vhost2Consumer.close();

        _jmxUtils.open();
    }

    @Override
    public void tearDown() throws Exception
    {
        _jmxUtils.close();

        super.tearDown();
    }

    public void testInitialStatisticValues() throws Exception
    {
        //Check initial values
        checkSingleConnectionOnVHostStatistics(TEST_VIRTUALHOST1, 0, 0, 0, 0);
        checkVHostStatistics(TEST_VIRTUALHOST1, 0, 0, 0, 0);
        checkSingleConnectionOnVHostStatistics(TEST_VIRTUALHOST2, 0, 0, 0, 0);
        checkVHostStatistics(TEST_VIRTUALHOST2, 0, 0, 0, 0);
        checkBrokerStatistics(0, 0, 0, 0);
    }

    public void testSendOnSingleVHost() throws Exception
    {
        sendMessagesAndSync(_vhost1Session, _vhost2Queue, MESSAGE_COUNT_TEST);

        //Check values
        checkSingleConnectionOnVHostStatistics(TEST_VIRTUALHOST1, MESSAGE_COUNT_TEST, 0, MESSAGE_COUNT_TEST * DEFAULT_MESSAGE_SIZE, 0);
        checkVHostStatistics(TEST_VIRTUALHOST1, MESSAGE_COUNT_TEST, 0, MESSAGE_COUNT_TEST * DEFAULT_MESSAGE_SIZE, 0);
        checkSingleConnectionOnVHostStatistics(TEST_VIRTUALHOST2, 0, 0, 0, 0);
        checkVHostStatistics(TEST_VIRTUALHOST2, 0, 0, 0, 0);
        checkBrokerStatistics(MESSAGE_COUNT_TEST, 0, MESSAGE_COUNT_TEST * DEFAULT_MESSAGE_SIZE, 0);
    }

    public void testSendOnTwoVHosts() throws Exception
    {
        sendMessagesAndSync(_vhost1Session, _vhost2Queue, MESSAGE_COUNT_TEST);
        sendMessagesAndSync(_vhost2Session, _vhost1Queue, MESSAGE_COUNT_DEV);

        //Check values
        checkSingleConnectionOnVHostStatistics(TEST_VIRTUALHOST1, MESSAGE_COUNT_TEST, 0, MESSAGE_COUNT_TEST * DEFAULT_MESSAGE_SIZE, 0);
        checkVHostStatistics(TEST_VIRTUALHOST1, MESSAGE_COUNT_TEST, 0, MESSAGE_COUNT_TEST * DEFAULT_MESSAGE_SIZE, 0);
        checkSingleConnectionOnVHostStatistics(TEST_VIRTUALHOST2,  MESSAGE_COUNT_DEV, 0, MESSAGE_COUNT_DEV * DEFAULT_MESSAGE_SIZE, 0);
        checkVHostStatistics(TEST_VIRTUALHOST2,  MESSAGE_COUNT_DEV, 0, MESSAGE_COUNT_DEV * DEFAULT_MESSAGE_SIZE, 0);
        checkBrokerStatistics(MESSAGE_COUNT_TEST + MESSAGE_COUNT_DEV, 0, (MESSAGE_COUNT_TEST + MESSAGE_COUNT_DEV) * DEFAULT_MESSAGE_SIZE, 0);
    }

    public void testSendAndConsumeOnSingleVHost() throws Exception
    {
        sendMessagesAndSync(_vhost1Session, _vhost2Queue, MESSAGE_COUNT_TEST);
        consumeMessages(_vhost1Session, _vhost2Queue, MESSAGE_COUNT_TEST);

        //Check values
        checkSingleConnectionOnVHostStatistics(TEST_VIRTUALHOST1, MESSAGE_COUNT_TEST, MESSAGE_COUNT_TEST, MESSAGE_COUNT_TEST * DEFAULT_MESSAGE_SIZE, MESSAGE_COUNT_TEST * DEFAULT_MESSAGE_SIZE);
        checkVHostStatistics(TEST_VIRTUALHOST1, MESSAGE_COUNT_TEST, MESSAGE_COUNT_TEST, MESSAGE_COUNT_TEST * DEFAULT_MESSAGE_SIZE, MESSAGE_COUNT_TEST * DEFAULT_MESSAGE_SIZE);
        checkSingleConnectionOnVHostStatistics(TEST_VIRTUALHOST2, 0, 0, 0, 0);
        checkVHostStatistics(TEST_VIRTUALHOST2, 0, 0, 0, 0);
        checkBrokerStatistics(MESSAGE_COUNT_TEST, MESSAGE_COUNT_TEST, MESSAGE_COUNT_TEST * DEFAULT_MESSAGE_SIZE, MESSAGE_COUNT_TEST * DEFAULT_MESSAGE_SIZE);
    }

    public void testSendAndConsumeOnTwoVHosts() throws Exception
    {
        sendMessagesAndSync(_vhost1Session, _vhost2Queue, MESSAGE_COUNT_TEST);
        sendMessagesAndSync(_vhost2Session, _vhost1Queue, MESSAGE_COUNT_DEV);
        consumeMessages(_vhost1Session, _vhost2Queue, MESSAGE_COUNT_TEST);
        consumeMessages(_vhost2Session, _vhost1Queue, MESSAGE_COUNT_DEV);

        //Check values
        checkSingleConnectionOnVHostStatistics(TEST_VIRTUALHOST1, MESSAGE_COUNT_TEST, MESSAGE_COUNT_TEST, MESSAGE_COUNT_TEST * DEFAULT_MESSAGE_SIZE, MESSAGE_COUNT_TEST * DEFAULT_MESSAGE_SIZE);
        checkVHostStatistics(TEST_VIRTUALHOST1, MESSAGE_COUNT_TEST, MESSAGE_COUNT_TEST, MESSAGE_COUNT_TEST * DEFAULT_MESSAGE_SIZE, MESSAGE_COUNT_TEST * DEFAULT_MESSAGE_SIZE);
        checkSingleConnectionOnVHostStatistics(TEST_VIRTUALHOST2,  MESSAGE_COUNT_DEV, MESSAGE_COUNT_DEV, MESSAGE_COUNT_DEV * DEFAULT_MESSAGE_SIZE, MESSAGE_COUNT_DEV * DEFAULT_MESSAGE_SIZE);
        checkVHostStatistics(TEST_VIRTUALHOST2,  MESSAGE_COUNT_DEV, MESSAGE_COUNT_DEV, MESSAGE_COUNT_DEV * DEFAULT_MESSAGE_SIZE, MESSAGE_COUNT_DEV * DEFAULT_MESSAGE_SIZE);
        checkBrokerStatistics(MESSAGE_COUNT_TEST + MESSAGE_COUNT_DEV, MESSAGE_COUNT_TEST + MESSAGE_COUNT_DEV, (MESSAGE_COUNT_TEST + MESSAGE_COUNT_DEV) * DEFAULT_MESSAGE_SIZE, (MESSAGE_COUNT_TEST + MESSAGE_COUNT_DEV) * DEFAULT_MESSAGE_SIZE);
    }

    private void sendMessagesAndSync(Session session, Queue queue, int numberOfMessages) throws Exception
    {
        //Send messages via connection on and sync
        sendMessage(session, queue, numberOfMessages);
        ((AMQSession<?,?>)session).sync();
    }

    private void consumeMessages(Session session, Queue queue, int numberOfMessages) throws Exception
    {
        //consume the messages on the virtual host
        final MessageConsumer consumer = session.createConsumer(queue);
        for (int i = 0 ; i < numberOfMessages ; i++)
        {
            assertNotNull("an expected message was not received", consumer.receive(1500));
        }
        session.commit();
        consumer.close();
    }

    private void checkSingleConnectionOnVHostStatistics(String vHostName, long messagesSent, long messagesReceived, long dataSent, long dataReceived)
    {
        List<ManagedConnection> managedConnections = _jmxUtils.getManagedConnections(vHostName);
        assertEquals(1, managedConnections.size());

        ManagedConnection managedConnection = managedConnections.get(0);

        assertEquals(messagesSent, managedConnection.getTotalMessagesReceived());
        assertEquals(messagesReceived, managedConnection.getTotalMessagesDelivered());

        assertEquals(dataSent, managedConnection.getTotalDataReceived());
        assertEquals(dataReceived, managedConnection.getTotalDataDelivered());
    }

    private void checkVHostStatistics(String vHostName, long messagesSent, long messagesReceived, long dataSent, long dataReceived)
    {
        ManagedBroker vhost = _jmxUtils.getManagedBroker(vHostName);

        assertEquals(messagesSent, vhost.getTotalMessagesReceived());
        assertEquals(messagesReceived, vhost.getTotalMessagesDelivered());

        assertEquals(dataSent, vhost.getTotalDataReceived());
        assertEquals(dataReceived, vhost.getTotalDataDelivered());
    }

    private void checkBrokerStatistics(long messagesSent, long messagesReceived, long dataSent, long dataReceived)
    {
        ServerInformation broker = _jmxUtils.getServerInformation();

        assertEquals(messagesSent, broker.getTotalMessagesReceived());
        assertEquals(messagesReceived, broker.getTotalMessagesDelivered());

        assertEquals(dataSent, broker.getTotalDataReceived());
        assertEquals(dataReceived, broker.getTotalDataDelivered());
    }
}
