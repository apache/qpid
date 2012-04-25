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
package org.apache.qpid.management.jmx;

import java.util.List;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.BasicMessageConsumer;
import org.apache.qpid.management.common.mbeans.ManagedConnection;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

/** NEW ONE */
public class NewStatisticsSystemTest extends QpidBrokerTestCase
{
    private static final String TEST_USER = "admin";
    private static final String TEST_PASSWORD = "admin";

    private JMXTestUtils _jmxUtils;
    private Connection _test1, _test2, _dev;

    protected Destination _queue;
    protected String _brokerUrl;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _jmxUtils = new JMXTestUtils(this, TEST_USER, TEST_PASSWORD);
        _jmxUtils.setUp();
        _brokerUrl = getBroker().toString();

        _test1 = new AMQConnection(_brokerUrl, TEST_USER, TEST_PASSWORD, "clientid", "test");
        //_test2 = new AMQConnection(_brokerUrl, TEST_USER, TEST_PASSWORD, "clientid", "test");
        _dev = new AMQConnection(_brokerUrl, TEST_USER, TEST_PASSWORD, "clientid", "development");

        _test1.start();
        //_test2.start();
        _dev.start();

        _jmxUtils.open();
    }

    @Override
    public void tearDown() throws Exception
    {
        _jmxUtils.close();

        super.tearDown();
    }

    public void testStats() throws Exception
    {
        Session testSession = _test1.createSession(false, Session.SESSION_TRANSACTED);
        //Session devSession = _dev.createSession(false, Session.SESSION_TRANSACTED);

        //Create queue
        Queue testQueue = testSession.createQueue(getTestQueueName());
        MessageConsumer testConsumer = testSession.createConsumer(testQueue);
        testConsumer.close();

        //Check initial values
        checkSingleConnectionOnVHostStatistics("test", 0, 0, 0, 0);
        checkSingleConnectionOnVHostStatistics("development", 0, 0, 0, 0);

        //Send messages via test session (test vhost) and sync
        sendMessage(testSession, testQueue, 5);
        ((AMQSession<?,?>)testSession).sync();

        //Check values
        checkSingleConnectionOnVHostStatistics("test", 5, 0, 5 * DEFAULT_MESSAGE_SIZE, 0);
        checkSingleConnectionOnVHostStatistics("development", 0, 0, 0, 0);
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
}
