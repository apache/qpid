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
package org.apache.qpid.server.store.berkeleydb.replication;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNode;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.test.utils.TestUtils;

/**
 * The HA black box tests test the BDB cluster as a opaque unit.  Client connects to
 * the cluster via a failover url
 */
public class MultiNodeTest extends QpidBrokerTestCase
{
    protected static final Logger LOGGER = Logger.getLogger(MultiNodeTest.class);

    private static final String VIRTUAL_HOST = "test";
    private static final int NUMBER_OF_NODES = 3;

    private final GroupCreator _groupCreator = new GroupCreator(this, VIRTUAL_HOST, NUMBER_OF_NODES);

    private FailoverAwaitingListener _failoverListener;

    /** Used when expectation is client will (re)-connect */
    private ConnectionURL _positiveFailoverUrl;

    /** Used when expectation is client will not (re)-connect */
    private ConnectionURL _negativeFailoverUrl;

    @Override
    protected void setUp() throws Exception
    {
        _brokerType = BrokerType.SPAWNED;

        assertTrue(isJavaBroker());
        assertTrue(isBrokerStorePersistent());

        setSystemProperty("java.util.logging.config.file", "etc" + File.separator + "log.properties");

        _groupCreator.configureClusterNodes();

        _positiveFailoverUrl = _groupCreator.getConnectionUrlForAllClusterNodes();
        _negativeFailoverUrl = _groupCreator.getConnectionUrlForAllClusterNodes(200, 0, 2);

        _groupCreator.startCluster();
        _failoverListener = new FailoverAwaitingListener();

        super.setUp();
    }

    @Override
    public void startBroker() throws Exception
    {
        // Don't start default broker provided by QBTC.
    }

    public void testLossOfMasterNodeCausesClientToFailover() throws Exception
    {
        final Connection connection = getConnection(_positiveFailoverUrl);

        ((AMQConnection)connection).setConnectionListener(_failoverListener);

        final int activeBrokerPort = _groupCreator.getBrokerPortNumberFromConnection(connection);
        LOGGER.info("Active connection port " + activeBrokerPort);

        _groupCreator.stopNode(activeBrokerPort);
        LOGGER.info("Node is stopped");
        _failoverListener.awaitFailoverCompletion(20000);
        LOGGER.info("Listener has finished");
        // any op to ensure connection remains
        connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    public void testLossOfReplicaNodeDoesNotCauseClientToFailover() throws Exception
    {
        final Connection connection = getConnection(_positiveFailoverUrl);

        ((AMQConnection)connection).setConnectionListener(_failoverListener);
        final int activeBrokerPort = _groupCreator.getBrokerPortNumberFromConnection(connection);
        LOGGER.info("Active connection port " + activeBrokerPort);
        final int inactiveBrokerPort = _groupCreator.getPortNumberOfAnInactiveBroker(connection);

        LOGGER.info("Stopping inactive broker on port " + inactiveBrokerPort);

        _groupCreator.stopNode(inactiveBrokerPort);

        _failoverListener.assertNoFailoverCompletionWithin(2000);

        assertProducingConsuming(connection);
    }

    public void testLossOfQuorumCausesClientDisconnection() throws Exception
    {
        final Connection connection = getConnection(_negativeFailoverUrl);

        ((AMQConnection)connection).setConnectionListener(_failoverListener);

        Set<Integer> ports = _groupCreator.getBrokerPortNumbersForNodes();

        final int activeBrokerPort = _groupCreator.getBrokerPortNumberFromConnection(connection);
        ports.remove(activeBrokerPort);

        // Stop all other nodes
        for (Integer p : ports)
        {
            _groupCreator.stopNode(p);
        }

        try
        {
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Destination destination = session.createQueue(getTestQueueName());
            session.createConsumer(destination).close();
            fail("Exception not thrown - creating durable queue should fail without quorum");
        }
        catch(JMSException jms)
        {
            // PASS
        }

        // New connections should now fail as vhost will be unavailable
        try
        {
            getConnection(_negativeFailoverUrl);
            fail("Exception not thrown");
        }
        catch (JMSException je)
        {
            // PASS
        }
    }

    public void testPersistentMessagesAvailableAfterFailover() throws Exception
    {
        final Connection connection = getConnection(_positiveFailoverUrl);

        ((AMQConnection)connection).setConnectionListener(_failoverListener);

        final int activeBrokerPort = _groupCreator.getBrokerPortNumberFromConnection(connection);

        Session producingSession = connection.createSession(true, Session.SESSION_TRANSACTED);
        Destination queue = producingSession.createQueue(getTestQueueName());
        producingSession.createConsumer(queue).close();
        sendMessage(producingSession, queue, 10);

        _groupCreator.stopNode(activeBrokerPort);
        LOGGER.info("Old master (broker port " + activeBrokerPort + ") is stopped");

        _failoverListener.awaitFailoverCompletion(20000);
        LOGGER.info("Failover has finished");

        final int activeBrokerPortAfterFailover = _groupCreator.getBrokerPortNumberFromConnection(connection);
        LOGGER.info("New master (broker port " + activeBrokerPort + ") after failover");

        Session consumingSession = connection.createSession(true, Session.SESSION_TRANSACTED);
        MessageConsumer consumer = consumingSession.createConsumer(queue);

        connection.start();
        for(int i = 0; i < 10; i++)
        {
            Message m = consumer.receive(RECEIVE_TIMEOUT);
            assertNotNull("Message " + i + "  is not received", m);
            assertEquals("Unexpected message received", i, m.getIntProperty(INDEX));
        }
        consumingSession.commit();
    }

    public void testTransferMasterFromLocalNode() throws Exception
    {
        final Connection connection = getConnection(_positiveFailoverUrl);

        ((AMQConnection)connection).setConnectionListener(_failoverListener);

        final int activeBrokerPort = _groupCreator.getBrokerPortNumberFromConnection(connection);
        LOGGER.info("Active connection port " + activeBrokerPort);

        final int inactiveBrokerPort = _groupCreator.getPortNumberOfAnInactiveBroker(connection);
        LOGGER.info("Update role attribute on inactive broker on port " + inactiveBrokerPort);

        Map<String, Object> attributes = _groupCreator.getNodeAttributes(inactiveBrokerPort);
        assertEquals("Inactive broker has unexpected role", "REPLICA", attributes.get(BDBHAVirtualHostNode.ROLE));
        _groupCreator.setNodeAttributes(inactiveBrokerPort,
                                          Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.ROLE, "MASTER"));

        _failoverListener.awaitFailoverCompletion(20000);
        LOGGER.info("Listener has finished");

        attributes = _groupCreator.getNodeAttributes(inactiveBrokerPort);
        assertEquals("Inactive broker has unexpected role", "MASTER", attributes.get(BDBHAVirtualHostNode.ROLE));

        assertProducingConsuming(connection);

        _groupCreator.awaitNodeToAttainRole(activeBrokerPort, "REPLICA");
    }

    public void testTransferMasterFromRemoteNode() throws Exception
    {
        final Connection connection = getConnection(_positiveFailoverUrl);

        ((AMQConnection)connection).setConnectionListener(_failoverListener);

        final int activeBrokerPort = _groupCreator.getBrokerPortNumberFromConnection(connection);
        LOGGER.info("Active connection port " + activeBrokerPort);

        final int inactiveBrokerPort = _groupCreator.getPortNumberOfAnInactiveBroker(connection);
        LOGGER.info("Update role attribute on inactive broker on port " + inactiveBrokerPort);

        _groupCreator.awaitNodeToAttainRole(activeBrokerPort, inactiveBrokerPort, "REPLICA");
        Map<String, Object> attributes = _groupCreator.getNodeAttributes(activeBrokerPort, inactiveBrokerPort);
        assertEquals("Inactive broker has unexpected role", "REPLICA", attributes.get(BDBHAVirtualHostNode.ROLE));

        _groupCreator.setNodeAttributes(activeBrokerPort, inactiveBrokerPort, Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.ROLE, "MASTER"));

        _failoverListener.awaitFailoverCompletion(20000);
        LOGGER.info("Listener has finished");

        attributes = _groupCreator.getNodeAttributes(inactiveBrokerPort);
        assertEquals("Inactive broker has unexpected role", "MASTER", attributes.get(BDBHAVirtualHostNode.ROLE));

        assertProducingConsuming(connection);

        _groupCreator.awaitNodeToAttainRole(activeBrokerPort, "REPLICA");
    }

    public void testQuorumOverride() throws Exception
    {
        final Connection connection = getConnection(_positiveFailoverUrl);

        Set<Integer> ports = _groupCreator.getBrokerPortNumbersForNodes();

        final int activeBrokerPort = _groupCreator.getBrokerPortNumberFromConnection(connection);
        ports.remove(activeBrokerPort);

        // Stop all other nodes
        for (Integer p : ports)
        {
            _groupCreator.stopNode(p);
        }

        Map<String, Object> attributes = _groupCreator.getNodeAttributes(activeBrokerPort);
        assertEquals("Broker has unexpected quorum override", new Integer(0), attributes.get(BDBHAVirtualHostNode.QUORUM_OVERRIDE));
        _groupCreator.setNodeAttributes(activeBrokerPort, Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.QUORUM_OVERRIDE, 1));

        attributes = _groupCreator.getNodeAttributes(activeBrokerPort);
        assertEquals("Broker has unexpected quorum override", new Integer(1), attributes.get(BDBHAVirtualHostNode.QUORUM_OVERRIDE));

        assertProducingConsuming(connection);
    }

    public void testPriority() throws Exception
    {
        final Connection connection = getConnection(_positiveFailoverUrl);

        ((AMQConnection)connection).setConnectionListener(_failoverListener);

        final int activeBrokerPort = _groupCreator.getBrokerPortNumberFromConnection(connection);
        LOGGER.info("Active connection port " + activeBrokerPort);

        int priority = 1;
        Integer highestPriorityBrokerPort = null;
        Set<Integer> ports = _groupCreator.getBrokerPortNumbersForNodes();
        for (Integer port : ports)
        {
            if (activeBrokerPort != port.intValue())
            {
                priority = priority + 1;
                highestPriorityBrokerPort = port;
                _groupCreator.setNodeAttributes(port, port, Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.PRIORITY, priority));
                Map<String, Object> attributes = _groupCreator.getNodeAttributes(port, port);
                assertEquals("Broker has unexpected priority", priority, attributes.get(BDBHAVirtualHostNode.PRIORITY));
            }
        }

        LOGGER.info("Broker on port " + highestPriorityBrokerPort + " has the highest priority of " + priority);

        LOGGER.info("Shutting down the MASTER");
        _groupCreator.stopNode(activeBrokerPort);

        _failoverListener.awaitFailoverCompletion(20000);
        LOGGER.info("Listener has finished");

        Map<String, Object> attributes = _groupCreator.getNodeAttributes(highestPriorityBrokerPort, highestPriorityBrokerPort);
        assertEquals("Inactive broker has unexpected role", "MASTER", attributes.get(BDBHAVirtualHostNode.ROLE));

        assertProducingConsuming(connection);
    }

    private final class FailoverAwaitingListener implements ConnectionListener
    {
        private final CountDownLatch _failoverCompletionLatch = new CountDownLatch(1);

        @Override
        public boolean preResubscribe()
        {
            return true;
        }

        @Override
        public boolean preFailover(boolean redirect)
        {
            return true;
        }

        public void awaitFailoverCompletion(long delay) throws InterruptedException
        {
            if (!_failoverCompletionLatch.await(delay, TimeUnit.MILLISECONDS))
            {
                LOGGER.warn("Test thread dump:\n\n" + TestUtils.dumpThreads() + "\n");
            }
            assertEquals("Failover did not occur", 0, _failoverCompletionLatch.getCount());
        }

        public void assertNoFailoverCompletionWithin(long delay) throws InterruptedException
        {
            _failoverCompletionLatch.await(delay, TimeUnit.MILLISECONDS);
            assertEquals("Failover occurred unexpectedly", 1L, _failoverCompletionLatch.getCount());
        }

        @Override
        public void failoverComplete()
        {
            _failoverCompletionLatch.countDown();
        }

        @Override
        public void bytesSent(long count)
        {
        }

        @Override
        public void bytesReceived(long count)
        {
        }
    }

}
