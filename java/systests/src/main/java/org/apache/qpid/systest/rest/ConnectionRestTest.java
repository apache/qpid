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
package org.apache.qpid.systest.rest;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.servlet.http.HttpServletResponse;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Session;

public class ConnectionRestTest extends QpidRestTestCase
{
    /**
     * Message number to publish into queue
     */
    private static final int MESSAGE_NUMBER = 5;
    private static final int MESSAGE_SIZE = 6;

    private static final String SESSIONS_ATTRIBUTE = "sessions";

    private javax.jms.Connection _connection;
    private javax.jms.Session _session;

    public void setUp() throws Exception
    {
        super.setUp();

        _connection = getConnection();
        _session = _connection.createSession(true, javax.jms.Session.SESSION_TRANSACTED);
        String queueName = getTestQueueName();
        Destination queue = _session.createQueue(queueName);
        MessageConsumer consumer = _session.createConsumer(queue);
        MessageProducer producer = _session.createProducer(queue);
        _connection.start();

        // send messages
        for (int i = 0; i < MESSAGE_NUMBER; i++)
        {
            producer.send(_session.createTextMessage("Test-" + i));
        }
        _session.commit();
        Message m = consumer.receive(1000l);
        assertNotNull("First message was not received", m);
        _session.commit();

        // receive the rest of messages for rollback
        for (int i = 0; i < MESSAGE_NUMBER - 1; i++)
        {
            m = consumer.receive(1000l);
            assertNotNull("Subsequent messages were not received", m);
        }
        _session.rollback();

        // receive them again
        for (int i = 0; i < MESSAGE_NUMBER - 1; i++)
        {
            m = consumer.receive(1000l);
            assertNotNull("Message was not received after rollback", m);
        }

        // Session left open
    }

    public void testGetAllConnections() throws Exception
    {
        List<Map<String, Object>> connections = getRestTestHelper().getJsonAsList("connection");
        assertEquals("Unexpected number of connections", 1, connections.size());
        Asserts.assertConnection(connections.get(0), (AMQConnection) _connection);
    }

    public void testGetVirtualHostConnections() throws Exception
    {
        List<Map<String, Object>> connections = getRestTestHelper().getJsonAsList("connection/test/test");
        assertEquals("Unexpected number of connections", 1, connections.size());
        Asserts.assertConnection(connections.get(0), (AMQConnection) _connection);
    }

    public void testGetConnectionByName() throws Exception
    {
        // get connection name
        String connectionName = getConnectionName();

        Map<String, Object> connectionDetails = getRestTestHelper().getJsonAsSingletonList("connection/test/test/"
                + URLDecoder.decode(connectionName, "UTF-8"));
        assertConnection(connectionDetails);
    }

    public void testDeleteConnection() throws Exception
    {
        // get connection name
        String connectionName = getConnectionName();

        List<Map<String, Object>> connections = getRestTestHelper().getJsonAsList("connection/test/test");
        assertEquals("Unexpected number of connections before deletion", 1, connections.size());

        String connectionUrl = "connection/test/test/" + URLDecoder.decode(connectionName, "UTF-8");
        getRestTestHelper().submitRequest(connectionUrl, "DELETE", HttpServletResponse.SC_OK);

        connections = getRestTestHelper().getJsonAsList("connection/test/test");
        assertEquals("Unexpected number of connections before deletion", 0, connections.size());

        try
        {
            _connection.createSession(true, javax.jms.Session.SESSION_TRANSACTED);
            fail("Exception not thrown");
        }
        catch (JMSException je)
        {
            // PASS
        }
    }

    public void testGetAllSessions() throws Exception
    {
        List<Map<String, Object>> sessions = getRestTestHelper().getJsonAsList("session");
        assertEquals("Unexpected number of sessions", 1, sessions.size());
        assertSession(sessions.get(0), (AMQSession<?, ?>) _session);
    }

    public void testGetVirtualHostSessions() throws Exception
    {
        List<Map<String, Object>> sessions = getRestTestHelper().getJsonAsList("session/test/test");
        assertEquals("Unexpected number of sessions", 1, sessions.size());
        assertSession(sessions.get(0), (AMQSession<?, ?>) _session);
    }

    public void testGetConnectionSessions() throws Exception
    {
        // get connection name
        String connectionName = getConnectionName();

        List<Map<String, Object>> sessions = getRestTestHelper().getJsonAsList("session/test/test/"
                + URLDecoder.decode(connectionName, "UTF-8"));
        assertEquals("Unexpected number of sessions", 1, sessions.size());
        assertSession(sessions.get(0), (AMQSession<?, ?>) _session);
    }

    public void testGetSessionByName() throws Exception
    {
        // get connection name
        String connectionName = getConnectionName();

        List<Map<String, Object>> sessions = getRestTestHelper().getJsonAsList("session/test/test/"
                + URLDecoder.decode(connectionName, "UTF-8") + "/" + ((AMQSession<?, ?>) _session).getChannelId());
        assertEquals("Unexpected number of sessions", 1, sessions.size());
        assertSession(sessions.get(0), (AMQSession<?, ?>) _session);
    }

    public void testProducerSessionOpenHasTransactionStartAndUpdateTimes() throws Exception
    {
        Destination queue = _session.createQueue(getTestQueueName());
        MessageProducer producer = _session.createProducer(queue);
        producer.send(_session.createMessage());
        // session left open
        ((AMQSession)_session).sync();
        String connectionName = getConnectionName();

        List<Map<String, Object>> sessions = getRestTestHelper().getJsonAsList("session/test/test/"
                                                                               + URLDecoder.decode(connectionName, "UTF-8") + "/" + ((AMQSession<?, ?>) _session).getChannelId());
        assertEquals("Unexpected number of sessions", 1, sessions.size());

        final Map<String, Object> sessionData = sessions.get(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) sessionData.get(Asserts.STATISTICS_ATTRIBUTE);

        long transactionStartTime = ((Number) statistics.get("transactionStartTime")).longValue();
        long transactionUpdateTime = ((Number) statistics.get("transactionUpdateTime")).longValue();

        assertTrue("Unexpected transaction start value for open transaction " + transactionStartTime, transactionStartTime > 0);
        assertTrue("Unexpected transaction update value for open transaction " + transactionUpdateTime, transactionUpdateTime > 0);
        assertTrue("Expected transaction update value " + transactionUpdateTime + " to be greater than transaction start time " + transactionStartTime, transactionUpdateTime >= transactionStartTime);


    }

    private void assertConnection(Map<String, Object> connectionDetails) throws JMSException
    {
        Asserts.assertConnection(connectionDetails, (AMQConnection) _connection);

        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) connectionDetails.get(Asserts.STATISTICS_ATTRIBUTE);
        assertEquals("Unexpected value of connection statistics attribute " + "bytesIn", MESSAGE_NUMBER
                * MESSAGE_SIZE, statistics.get("bytesIn"));
        assertEquals("Unexpected value of connection statistics attribute " + "bytesOut", MESSAGE_SIZE
                + ((MESSAGE_NUMBER - 1) * MESSAGE_SIZE) * 2, statistics.get("bytesOut"));
        assertEquals("Unexpected value of connection statistics attribute " + "messagesIn", MESSAGE_NUMBER,
                statistics.get("messagesIn"));
        assertEquals("Unexpected value of connection statistics attribute " + "messagesOut",
                MESSAGE_NUMBER * 2 - 1, statistics.get("messagesOut"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sessions = (List<Map<String, Object>>) connectionDetails.get(SESSIONS_ATTRIBUTE);
        assertNotNull("Sessions cannot be found", sessions);
        assertEquals("Unexpected number of sessions", 1, sessions.size());
        assertSession(sessions.get(0), (AMQSession<?, ?>) _session);
    }

    private void assertSession(Map<String, Object> sessionData, AMQSession<?, ?> session)
    {
        assertNotNull("Session map cannot be null", sessionData);
        Asserts.assertAttributesPresent(sessionData, BrokerModel.getInstance().getTypeRegistry().getAttributeNames(
                Session.class),
                                        ConfiguredObject.TYPE,
                                        ConfiguredObject.CREATED_BY,
                                        ConfiguredObject.CREATED_TIME,
                                        ConfiguredObject.LAST_UPDATED_BY,
                                        ConfiguredObject.LAST_UPDATED_TIME,
                                        ConfiguredObject.DESCRIPTION,
                                        ConfiguredObject.CONTEXT,
                                        ConfiguredObject.DESIRED_STATE,
                                        Session.STATE,
                                        Session.DURABLE,
                                        Session.LIFETIME_POLICY);
        assertEquals("Unexpected value of attribute " + Session.NAME, session.getChannelId() + "",
                sessionData.get(Session.NAME));
        assertEquals("Unexpected value of attribute " + Session.PRODUCER_FLOW_BLOCKED, Boolean.FALSE,
                sessionData.get(Session.PRODUCER_FLOW_BLOCKED));
        assertEquals("Unexpected value of attribute " + Session.CHANNEL_ID, session.getChannelId(),
                sessionData.get(Session.CHANNEL_ID));

        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) sessionData.get(Asserts.STATISTICS_ATTRIBUTE);
        Asserts.assertAttributesPresent(statistics, "consumerCount",
                                        "localTransactionBegins", "localTransactionOpen",
                                        "localTransactionRollbacks", "unacknowledgedMessages",
                                        "transactionStartTime", "transactionUpdateTime");

        assertEquals("Unexpected value of statistic attribute " + "unacknowledgedMessages", MESSAGE_NUMBER - 1,
                statistics.get("unacknowledgedMessages"));
        assertEquals("Unexpected value of statistic attribute " + "localTransactionBegins", 4,
                     statistics.get("localTransactionBegins"));
        assertEquals("Unexpected value of statistic attribute " + "localTransactionRollbacks", 1,
                     statistics.get("localTransactionRollbacks"));
        assertEquals("Unexpected value of statistic attribute " + "consumerCount", 1,
                     statistics.get("consumerCount"));
    }

    private String getConnectionName() throws IOException
    {
        Map<String, Object> hostDetails = getRestTestHelper().getJsonAsSingletonList("virtualhost/test/test");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> connections = (List<Map<String, Object>>) hostDetails
                .get(VirtualHostRestTest.VIRTUALHOST_CONNECTIONS_ATTRIBUTE);
        assertEquals("Unexpected number of connections", 1, connections.size());
        Map<String, Object> connection = connections.get(0);
        String connectionName = (String) connection.get(Connection.NAME);
        return connectionName;
    }
}
