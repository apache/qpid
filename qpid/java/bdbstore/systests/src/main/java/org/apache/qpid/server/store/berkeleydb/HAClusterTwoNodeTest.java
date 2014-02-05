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
import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

import com.sleepycat.je.rep.ReplicationConfig;

public class HAClusterTwoNodeTest extends QpidBrokerTestCase
{
    private static final String VIRTUAL_HOST = "test";

    public static final long RECEIVE_TIMEOUT = 5000l;

    private static final int NUMBER_OF_NODES = 2;

    private final HATestClusterCreator _clusterCreator = new HATestClusterCreator(this, VIRTUAL_HOST, NUMBER_OF_NODES);

    private ConnectionURL _brokerFailoverUrl;

    @Override
    protected void setUp() throws Exception
    {
        _brokerType = BrokerType.SPAWNED;

        assertTrue(isJavaBroker());
        assertTrue(isBrokerStorePersistent());

        super.setUp();
    }

    @Override
    public void startBroker() throws Exception
    {
        // Don't start default broker provided by QBTC.
    }

    private void startCluster(boolean designedPrimary) throws Exception
    {
        setSystemProperty("java.util.logging.config.file", "etc" + File.separator + "log.properties");

        Map<String,String> replicationParameters = new HashMap<String, String>();
        replicationParameters.put(ReplicationConfig.INSUFFICIENT_REPLICAS_TIMEOUT, "2 s");
        replicationParameters.put(ReplicationConfig.ELECTIONS_PRIMARY_RETRIES, "0");

        _clusterCreator.configureClusterNodes(replicationParameters);
        _clusterCreator.configureDesignatedPrimaryOnFirstBroker(designedPrimary);
        _brokerFailoverUrl = _clusterCreator.getConnectionUrlForAllClusterNodes();
        _clusterCreator.startCluster();
    }

    public void testMasterDesignatedPrimaryCanBeRestartedWithoutReplica() throws Exception
    {
        startCluster(true);
        final Connection initialConnection = getConnection(_brokerFailoverUrl);
        int masterPort = _clusterCreator.getBrokerPortNumberFromConnection(initialConnection);
        assertProducingConsuming(initialConnection);
        initialConnection.close();
        _clusterCreator.stopCluster();
        _clusterCreator.startNode(masterPort);
        final Connection secondConnection = getConnection(_brokerFailoverUrl);
        assertProducingConsuming(secondConnection);
        secondConnection.close();
    }

    public void testClusterRestartWithoutDesignatedPrimary() throws Exception
    {
        startCluster(false);
        final Connection initialConnection = getConnection(_brokerFailoverUrl);
        assertProducingConsuming(initialConnection);
        initialConnection.close();
        _clusterCreator.stopCluster();
        _clusterCreator.startClusterParallel();
        final Connection secondConnection = getConnection(_brokerFailoverUrl);
        assertProducingConsuming(secondConnection);
        secondConnection.close();
    }

    public void testDesignatedPrimaryContinuesAfterSecondaryStopped() throws Exception
    {
        startCluster(true);
        _clusterCreator.stopNode(_clusterCreator.getBrokerPortNumberOfSecondaryNode());
        final Connection connection = getConnection(_brokerFailoverUrl);
        assertNotNull("Expected to get a valid connection to primary", connection);
        assertProducingConsuming(connection);
    }

    public void testPersistentOperationsFailOnNonDesignatedPrimarysAfterSecondaryStopped() throws Exception
    {
        startCluster(false);
        _clusterCreator.stopNode(_clusterCreator.getBrokerPortNumberOfSecondaryNode());
        final Connection connection = getConnection(_brokerFailoverUrl);
        assertNotNull("Expected to get a valid connection to primary", connection);
        try
        {
            assertProducingConsuming(connection);
            fail("JMS peristent operations succeded on Master 'not designated primary' but they should fail as replica is not available");
        }
        catch(JMSException e)
        {
            // JMSException should be thrown on transaction start/commit
        }
    }

    public void testSecondaryDoesNotBecomePrimaryWhenDesignatedPrimaryStopped() throws Exception
    {
        startCluster(true);
        _clusterCreator.stopNode(_clusterCreator.getBrokerPortNumberOfPrimary());

        try
        {
            getConnection(_brokerFailoverUrl);
            fail("Connection not expected");
        }
        catch (JMSException e)
        {
            // PASS
        }
    }

    public void testInitialDesignatedPrimaryStateOfNodes() throws Exception
    {
        startCluster(true);
        Map<String, Object> primaryAttributes = _clusterCreator.getReplicationNodeAttributes(_clusterCreator.getBrokerPortNumberOfPrimary());
        assertTrue("Expected primary node to be set as designated primary", (Boolean)primaryAttributes.get(ReplicationNode.DESIGNATED_PRIMARY));

        Map<String, Object> secondaryAttributes = _clusterCreator.getReplicationNodeAttributes(_clusterCreator.getBrokerPortNumberOfSecondaryNode());
        assertFalse("Expected secondary node to NOT be set as designated primary", (Boolean)secondaryAttributes.get(ReplicationNode.DESIGNATED_PRIMARY));
    }

    public void testSecondaryDesignatedAsPrimaryAfterOrginalPrimaryStopped() throws Exception
    {
        startCluster(true);
        _clusterCreator.stopNode(_clusterCreator.getBrokerPortNumberOfPrimary());

        int brokerPortNumberOfSecondaryNode = _clusterCreator.getBrokerPortNumberOfSecondaryNode();

        Map<String, Object> secondaryAttributes = _clusterCreator.getReplicationNodeAttributes(brokerPortNumberOfSecondaryNode);
        assertFalse("Expected node to NOT be set as designated primary", (Boolean)secondaryAttributes.get(ReplicationNode.DESIGNATED_PRIMARY));

        setDesignatedPrimary(brokerPortNumberOfSecondaryNode, true);

        secondaryAttributes = _clusterCreator.getReplicationNodeAttributes(brokerPortNumberOfSecondaryNode);
        assertTrue("Expected node to now be set as designated primary", (Boolean)secondaryAttributes.get(ReplicationNode.DESIGNATED_PRIMARY));

        final Connection connection = getConnection(_brokerFailoverUrl);
        assertNotNull("Expected to get a valid connection to new primary", connection);
        assertProducingConsuming(connection);
    }

    public void testMasterNotDesignatedPrimaryAssignedDesignatedPrimaryLaterOn() throws Exception
    {
        startCluster(false);

        // Shutdown replica
        _clusterCreator.stopNode(_clusterCreator.getBrokerPortNumberOfSecondaryNode());

        // Do transaction
        final Connection connection = getConnection(_brokerFailoverUrl);
        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        Destination destination = session.createQueue(getTestQueueName());
        try
        {
            session.createConsumer(destination);
            fail("Creation of durable queue should fail as there is no majority");
        }
        catch (JMSException je)
        {
            // pass
            je.printStackTrace();
        }

        int brokerPortNumberOfPrimary = _clusterCreator.getBrokerPortNumberOfPrimary();

        // Check master is in UNKNOWN
        awaitNodeToAttainRole(brokerPortNumberOfPrimary, "UNKNOWN");

        // Designate primary
       setDesignatedPrimary(brokerPortNumberOfPrimary, true);

        // Check master is MASTER
        awaitNodeToAttainRole(brokerPortNumberOfPrimary, "MASTER");

        final Connection connection2 = getConnection(_brokerFailoverUrl);
        assertProducingConsuming(connection2);
    }

    private void setDesignatedPrimary(int brokerPort, boolean designatedPrimary) throws Exception
    {
        _clusterCreator.setReplicationNodeAttributes(brokerPort, Collections.<String, Object>singletonMap(ReplicationNode.DESIGNATED_PRIMARY, designatedPrimary));
    }

    private void awaitNodeToAttainRole(int brokerPort, String desiredRole) throws Exception
    {
        String nodeName = _clusterCreator.getNodeNameForBrokerPort(brokerPort);
        _clusterCreator.awaitNodeToAttainRole(brokerPort, nodeName, desiredRole);
    }

}
