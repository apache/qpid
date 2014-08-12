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
package org.apache.qpid.server.store.berkeleydb;

import java.io.File;
import java.util.Set;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.log4j.Logger;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.url.URLSyntaxException;

/**
 * The HA white box tests test the BDB cluster where the test retains the knowledge of the
 * individual test nodes.  It uses this knowledge to examine the nodes  to ensure that they
 * remain in the correct state throughout the test.
 *
 * @see HAClusterBlackboxTest
 */
public class HAClusterWhiteboxTest extends QpidBrokerTestCase
{
    protected static final Logger LOGGER = Logger.getLogger(HAClusterWhiteboxTest.class);

    private static final String VIRTUAL_HOST = "test";

    private final int NUMBER_OF_NODES = 3;
    private final HATestClusterCreator _clusterCreator = new HATestClusterCreator(this, VIRTUAL_HOST, NUMBER_OF_NODES);

    @Override
    protected void setUp() throws Exception
    {
        _brokerType = BrokerType.SPAWNED;

        assertTrue(isJavaBroker());
        assertTrue(isBrokerStorePersistent());

        setSystemProperty("java.util.logging.config.file", "etc" + File.separator + "log.properties");

        _clusterCreator.configureClusterNodes();
        _clusterCreator.startCluster();

        super.setUp();
    }

    @Override
    public void startBroker() throws Exception
    {
        // Don't start default broker provided by QBTC.
    }

    public void testClusterPermitsConnectionToOnlyOneNode() throws Exception
    {
        int connectionSuccesses = 0;
        int connectionFails = 0;

        for (int brokerPortNumber : getBrokerPortNumbers())
        {
            try
            {
                getConnection(_clusterCreator.getConnectionUrlForSingleNodeWithoutRetry(brokerPortNumber));
                connectionSuccesses++;
            }
            catch(JMSException e)
            {
                assertTrue(e.getMessage().contains("Virtual host '" + VIRTUAL_HOST + "' is not active"));
                connectionFails++;
            }
        }

        assertEquals("Unexpected number of failed connections", NUMBER_OF_NODES - 1, connectionFails);
        assertEquals("Unexpected number of successful connections", 1, connectionSuccesses);
    }

    public void testClusterThatLosesNodeStillAllowsConnection() throws Exception
    {
        final Connection initialConnection = getConnectionToNodeInCluster();
        assertNotNull(initialConnection);

        closeConnectionAndKillBroker(initialConnection);

        final Connection subsequentConnection = getConnectionToNodeInCluster();
        assertNotNull(subsequentConnection);

        // verify that JMS persistence operations are working
        assertProducingConsuming(subsequentConnection);

        closeConnection(initialConnection);
    }

    public void testClusterThatLosesAllButOneNodeRefusesConnection() throws Exception
    {
        final Connection initialConnection = getConnectionToNodeInCluster();
        assertNotNull(initialConnection);

        closeConnectionAndKillBroker(initialConnection);

        final Connection subsequentConnection = getConnectionToNodeInCluster();
        assertNotNull(subsequentConnection);
        final int subsequentPortNumber = _clusterCreator.getBrokerPortNumberFromConnection(subsequentConnection);

        killBroker(subsequentPortNumber);

        final Connection finalConnection = getConnectionToNodeInCluster();
        assertNull(finalConnection);

        closeConnection(initialConnection);
    }

    public void testClusterWithRestartedNodeStillAllowsConnection() throws Exception
    {
        final Connection connection = getConnectionToNodeInCluster();
        assertNotNull(connection);

        final int brokerPortNumber = _clusterCreator.getBrokerPortNumberFromConnection(connection);
        connection.close();

        _clusterCreator.stopNode(brokerPortNumber);
        _clusterCreator.startNode(brokerPortNumber);

        final Connection subsequentConnection = getConnectionToNodeInCluster();
        assertNotNull(subsequentConnection);
    }

    public void testClusterLosingNodeRetainsData() throws Exception
    {
        final Connection initialConnection = getConnectionToNodeInCluster();

        final String queueNamePrefix = getTestQueueName();
        final String inbuiltExchangeQueueUrl = "direct://amq.direct/" + queueNamePrefix + "1/" + queueNamePrefix + "1?durable='true'";
        final String customExchangeQueueUrl = "direct://my.exchange/" + queueNamePrefix + "2/" + queueNamePrefix + "2?durable='true'";

        populateBrokerWithData(initialConnection, inbuiltExchangeQueueUrl, customExchangeQueueUrl);

        closeConnectionAndKillBroker(initialConnection);

        final Connection subsequentConnection = getConnectionToNodeInCluster();

        assertNotNull("no valid connection obtained", subsequentConnection);

        checkBrokerData(subsequentConnection, inbuiltExchangeQueueUrl, customExchangeQueueUrl);
    }

    public void xtestRecoveryOfOutOfDateNode() throws Exception
    {
        /*
         * TODO: Implement
         *
         * Cant yet find a way to control cleaning in a deterministic way to allow provoking
         * a node to become out of date. We do now know that even a new joiner to the group
         * can throw the InsufficientLogException, so ensuring an existing cluster of nodes has
         * done *any* cleaning and then adding a new node should be sufficient to cause this.
         */
    }

    private void populateBrokerWithData(final Connection connection, final String... queueUrls) throws JMSException, Exception
    {
        populateBrokerWithData(connection, 1, queueUrls);
    }

    private void populateBrokerWithData(final Connection connection, int noOfMessages, final String... queueUrls) throws JMSException, Exception
    {
        final Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        for (final String queueUrl : queueUrls)
        {
            final Queue queue = session.createQueue(queueUrl);
            session.createConsumer(queue).close();
            sendMessage(session, queue, noOfMessages);
        }
    }

    private void checkBrokerData(final Connection connection, final String... queueUrls) throws JMSException
    {
        connection.start();
        final Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        for (final String queueUrl : queueUrls)
        {
            final Queue queue = session.createQueue(queueUrl);
            final MessageConsumer consumer = session.createConsumer(queue);
            final Message message = consumer.receive(1000);
            session.commit();
            assertNotNull("Queue " + queue + " should have message", message);
            assertEquals("Queue " + queue + " message has unexpected content", 0, message.getIntProperty(INDEX));
        }
    }

    private Connection getConnectionToNodeInCluster() throws URLSyntaxException
    {
        Connection connection = null;
        Set<Integer> runningBrokerPorts = getBrokerPortNumbers();

        for (int brokerPortNumber : runningBrokerPorts)
        {
            try
            {
                connection = getConnection(_clusterCreator.getConnectionUrlForSingleNodeWithRetry(brokerPortNumber));
                break;
            }
            catch(JMSException je)
            {
                assertTrue(je.getMessage().contains("Virtual host '" + VIRTUAL_HOST + "' is not active"));
            }
        }
        return connection;
    }

    private void closeConnectionAndKillBroker(final Connection initialConnection) throws Exception
    {
        final int initialPortNumber = _clusterCreator.getBrokerPortNumberFromConnection(initialConnection);
        initialConnection.close();

        killBroker(initialPortNumber); // kill awaits the death of the child
    }

    private void closeConnection(final Connection initialConnection)
    {
        try
        {
            initialConnection.close();
        }
        catch(Exception e)
        {
            // ignore.
            // java.net.SocketException is seen sometimes on active connection
        }
    }
}
