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
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
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
 *
 * @see HAClusterWhiteboxTest
 */
public class HAClusterBlackboxTest extends QpidBrokerTestCase
{
    protected static final Logger LOGGER = Logger.getLogger(HAClusterBlackboxTest.class);

    private static final String VIRTUAL_HOST = "test";
    private static final int NUMBER_OF_NODES = 3;

    private final HATestClusterCreator _clusterCreator = new HATestClusterCreator(this, VIRTUAL_HOST, NUMBER_OF_NODES);

    private FailoverAwaitingListener _failoverListener;
    private ConnectionURL _brokerFailoverUrl;

    @Override
    protected void setUp() throws Exception
    {
        _brokerType = BrokerType.SPAWNED;

        assertTrue(isJavaBroker());
        assertTrue(isBrokerStorePersistent());

        setSystemProperty("java.util.logging.config.file", "etc" + File.separator + "log.properties");

        _clusterCreator.configureClusterNodes();

        _brokerFailoverUrl = _clusterCreator.getConnectionUrlForAllClusterNodes();

        _clusterCreator.startCluster();
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
        final Connection connection = getConnection(_brokerFailoverUrl);

        ((AMQConnection)connection).setConnectionListener(_failoverListener);

        final int activeBrokerPort = _clusterCreator.getBrokerPortNumberFromConnection(connection);
        LOGGER.info("Active connection port " + activeBrokerPort);

        _clusterCreator.stopNode(activeBrokerPort);
        LOGGER.info("Node is stopped");
        _failoverListener.awaitFailoverCompletion(20000);
        LOGGER.info("Listener has finished");
        // any op to ensure connection remains
        connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    public void testLossOfReplicaNodeDoesNotCauseClientToFailover() throws Exception
    {
        LOGGER.info("Connecting to " + _brokerFailoverUrl);
        final Connection connection = getConnection(_brokerFailoverUrl);
        LOGGER.info("Got connection to cluster");

        ((AMQConnection)connection).setConnectionListener(_failoverListener);
        final int activeBrokerPort = _clusterCreator.getBrokerPortNumberFromConnection(connection);
        LOGGER.info("Active connection port " + activeBrokerPort);
        final int inactiveBrokerPort = _clusterCreator.getPortNumberOfAnInactiveBroker(connection);

        LOGGER.info("Stopping inactive broker on port " + inactiveBrokerPort);

        _clusterCreator.stopNode(inactiveBrokerPort);

        _failoverListener.assertNoFailoverCompletionWithin(2000);

        // any op to ensure connection remains
        connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    public void testTransferMasterFromLocalNode() throws Exception
    {
        final Connection connection = getConnection(_brokerFailoverUrl);

        ((AMQConnection)connection).setConnectionListener(_failoverListener);

        final int activeBrokerPort = _clusterCreator.getBrokerPortNumberFromConnection(connection);
        LOGGER.info("Active connection port " + activeBrokerPort);

        final int inactiveBrokerPort = _clusterCreator.getPortNumberOfAnInactiveBroker(connection);
        LOGGER.info("Update role attribute on inactive broker on port " + inactiveBrokerPort);

        Map<String, Object> attributes = _clusterCreator.getNodeAttributes(inactiveBrokerPort);
        assertEquals("Inactive broker has unexpected role", "REPLICA", attributes.get(BDBHAVirtualHostNode.ROLE));
        _clusterCreator.setNodeAttributes(inactiveBrokerPort, Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.ROLE, "MASTER"));

        _failoverListener.awaitFailoverCompletion(20000);
        LOGGER.info("Listener has finished");

        attributes = _clusterCreator.getNodeAttributes(inactiveBrokerPort);
        assertEquals("Inactive broker has unexpected role", "MASTER", attributes.get(BDBHAVirtualHostNode.ROLE));

        assertProducingConsuming(connection);

        _clusterCreator.awaitNodeToAttainRole(activeBrokerPort, "REPLICA");
    }

    public void testTransferMasterFromRemoteNode() throws Exception
    {
        final Connection connection = getConnection(_brokerFailoverUrl);

        ((AMQConnection)connection).setConnectionListener(_failoverListener);

        final int activeBrokerPort = _clusterCreator.getBrokerPortNumberFromConnection(connection);
        LOGGER.info("Active connection port " + activeBrokerPort);

        final int inactiveBrokerPort = _clusterCreator.getPortNumberOfAnInactiveBroker(connection);
        LOGGER.info("Update role attribute on inactive broker on port " + inactiveBrokerPort);

        _clusterCreator.awaitNodeToAttainRole(activeBrokerPort, inactiveBrokerPort, "REPLICA");
        Map<String, Object> attributes = _clusterCreator.getNodeAttributes(activeBrokerPort, inactiveBrokerPort);
        assertEquals("Inactive broker has unexpected role", "REPLICA", attributes.get(BDBHAVirtualHostNode.ROLE));

        _clusterCreator.setNodeAttributes(activeBrokerPort, inactiveBrokerPort, Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.ROLE, "MASTER"));

        _failoverListener.awaitFailoverCompletion(20000);
        LOGGER.info("Listener has finished");

        attributes = _clusterCreator.getNodeAttributes(inactiveBrokerPort);
        assertEquals("Inactive broker has unexpected role", "MASTER", attributes.get(BDBHAVirtualHostNode.ROLE));

        assertProducingConsuming(connection);

        _clusterCreator.awaitNodeToAttainRole(activeBrokerPort, "REPLICA");
    }

    public void testQuorumOverride() throws Exception
    {
        final Connection connection = getConnection(_brokerFailoverUrl);

        ((AMQConnection)connection).setConnectionListener(_failoverListener);

        Set<Integer> ports = _clusterCreator.getBrokerPortNumbersForNodes();

        final int activeBrokerPort = _clusterCreator.getBrokerPortNumberFromConnection(connection);
        ports.remove(activeBrokerPort);

        // Stop all other nodes
        for (Integer p : ports)
        {
            _clusterCreator.stopNode(p);
        }

        Map<String, Object> attributes = _clusterCreator.getNodeAttributes(activeBrokerPort);
        assertEquals("Broker has unexpected quorum override", new Integer(0), attributes.get(BDBHAVirtualHostNode.QUORUM_OVERRIDE));
        _clusterCreator.setNodeAttributes(activeBrokerPort, Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.QUORUM_OVERRIDE, 1));

        attributes = _clusterCreator.getNodeAttributes(activeBrokerPort);
        assertEquals("Broker has unexpected quorum override", new Integer(1), attributes.get(BDBHAVirtualHostNode.QUORUM_OVERRIDE));

        assertProducingConsuming(connection);
    }

    public void testPriority() throws Exception
    {
        final Connection connection = getConnection(_brokerFailoverUrl);

        ((AMQConnection)connection).setConnectionListener(_failoverListener);

        final int activeBrokerPort = _clusterCreator.getBrokerPortNumberFromConnection(connection);
        LOGGER.info("Active connection port " + activeBrokerPort);

        int priority = 1;
        Integer highestPriorityBrokerPort = null;
        Set<Integer> ports = _clusterCreator.getBrokerPortNumbersForNodes();
        for (Integer port : ports)
        {
            if (activeBrokerPort != port.intValue())
            {
                priority = priority + 1;
                highestPriorityBrokerPort = port;
                _clusterCreator.setNodeAttributes(port, port, Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.PRIORITY, priority));
                Map<String, Object> attributes = _clusterCreator.getNodeAttributes(port, port);
                assertEquals("Broker has unexpected priority", priority, attributes.get(BDBHAVirtualHostNode.PRIORITY));
            }
        }

        LOGGER.info("Broker on port " + highestPriorityBrokerPort + " has the highest priority of " + priority);

        LOGGER.info("Shutting down the MASTER");
        _clusterCreator.stopNode(activeBrokerPort);

        _failoverListener.awaitFailoverCompletion(20000);
        LOGGER.info("Listener has finished");

        Map<String, Object> attributes = _clusterCreator.getNodeAttributes(highestPriorityBrokerPort, highestPriorityBrokerPort);
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
