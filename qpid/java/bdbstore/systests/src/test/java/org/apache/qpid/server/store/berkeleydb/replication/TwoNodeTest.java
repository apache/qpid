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

import javax.jms.Connection;
import javax.jms.JMSException;

import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNode;
import org.apache.qpid.test.utils.BrokerHolder;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class TwoNodeTest extends QpidBrokerTestCase
{
    private static final String VIRTUAL_HOST = "test";

    private static final int NUMBER_OF_NODES = 2;

    private final GroupCreator _groupCreator = new GroupCreator(this, VIRTUAL_HOST, NUMBER_OF_NODES);

    /** Used when expectation is client will not (re)-connect */
    private ConnectionURL _positiveFailoverUrl;

    /** Used when expectation is client will not (re)-connect */
    private ConnectionURL _negativeFailoverUrl;

    @Override
    protected void setUp() throws Exception
    {
        _brokerType = BrokerHolder.BrokerType.SPAWNED;

        setTestClientSystemProperty("log4j.configuration", getBrokerCommandLog4JFile().toURI().toString());

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
        _groupCreator.configureClusterNodes();
        _groupCreator.setDesignatedPrimaryOnFirstBroker(designedPrimary);
        _positiveFailoverUrl = _groupCreator.getConnectionUrlForAllClusterNodes();
        _negativeFailoverUrl = _groupCreator.getConnectionUrlForAllClusterNodes(200, 0, 2);
        _groupCreator.startCluster();
    }

    public void testMasterDesignatedPrimaryCanBeRestartedWithoutReplica() throws Exception
    {
        startCluster(true);
        final Connection initialConnection = getConnection(_positiveFailoverUrl);
        int masterPort = _groupCreator.getBrokerPortNumberFromConnection(initialConnection);
        assertProducingConsuming(initialConnection);
        initialConnection.close();
        _groupCreator.stopCluster();
        _groupCreator.startNode(masterPort);
        final Connection secondConnection = getConnection(_positiveFailoverUrl);
        assertProducingConsuming(secondConnection);
        secondConnection.close();
    }

    public void testClusterRestartWithoutDesignatedPrimary() throws Exception
    {
        startCluster(false);
        final Connection initialConnection = getConnection(_positiveFailoverUrl);
        assertProducingConsuming(initialConnection);
        initialConnection.close();
        _groupCreator.stopCluster();
        _groupCreator.startClusterParallel();
        final Connection secondConnection = getConnection(_positiveFailoverUrl);
        assertProducingConsuming(secondConnection);
        secondConnection.close();
    }

    public void testDesignatedPrimaryContinuesAfterSecondaryStopped() throws Exception
    {
        startCluster(true);
        _groupCreator.stopNode(_groupCreator.getBrokerPortNumberOfSecondaryNode());
        final Connection connection = getConnection(_positiveFailoverUrl);
        assertNotNull("Expected to get a valid connection to primary", connection);
        assertProducingConsuming(connection);
    }

    public void testPersistentOperationsFailOnNonDesignatedPrimaryAfterSecondaryStopped() throws Exception
    {
        startCluster(false);
        _groupCreator.stopNode(_groupCreator.getBrokerPortNumberOfSecondaryNode());

        try
        {
            Connection connection = getConnection(_negativeFailoverUrl);
            assertProducingConsuming(connection);
            fail("Exception not thrown");
        }
        catch(JMSException e)
        {
            // JMSException should be thrown either on getConnection, or produce/consume
            // depending on whether the relative timing of the node discovering that the
            // secondary has gone.
        }
    }

    public void testSecondaryDoesNotBecomePrimaryWhenDesignatedPrimaryStopped() throws Exception
    {
        startCluster(true);
        _groupCreator.stopNode(_groupCreator.getBrokerPortNumberOfPrimary());

        try
        {
            getConnection(_negativeFailoverUrl);
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

        Map<String, Object> primaryNodeAttributes = _groupCreator.getNodeAttributes(_groupCreator.getBrokerPortNumberOfPrimary());
        assertTrue("Expected primary node to be set as designated primary",
                   (Boolean) primaryNodeAttributes.get(BDBHAVirtualHostNode.DESIGNATED_PRIMARY));

        Map<String, Object> secondaryNodeAttributes = _groupCreator.getNodeAttributes(_groupCreator.getBrokerPortNumberOfSecondaryNode());
        assertFalse("Expected secondary node to NOT be set as designated primary",
                    (Boolean) secondaryNodeAttributes.get(BDBHAVirtualHostNode.DESIGNATED_PRIMARY));
    }

    public void testSecondaryDesignatedAsPrimaryAfterOriginalPrimaryStopped() throws Exception
    {
        startCluster(true);

        _groupCreator.stopNode(_groupCreator.getBrokerPortNumberOfPrimary());

        Map<String, Object> secondaryNodeAttributes = _groupCreator.getNodeAttributes(_groupCreator.getBrokerPortNumberOfSecondaryNode());
        assertFalse("Expected node to NOT be set as designated primary", (Boolean) secondaryNodeAttributes.get(BDBHAVirtualHostNode.DESIGNATED_PRIMARY));

        _groupCreator.setNodeAttributes(_groupCreator.getBrokerPortNumberOfSecondaryNode(), Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.DESIGNATED_PRIMARY, true));

        int timeout = 5000;
        long limit = System.currentTimeMillis() + timeout;
        while( !((Boolean)secondaryNodeAttributes.get(BDBHAVirtualHostNode.DESIGNATED_PRIMARY)) && System.currentTimeMillis() < limit)
        {
            Thread.sleep(100);
            secondaryNodeAttributes = _groupCreator.getNodeAttributes(_groupCreator.getBrokerPortNumberOfSecondaryNode());
        }
        assertTrue("Expected secondary to transition to primary within " + timeout, (Boolean) secondaryNodeAttributes.get(BDBHAVirtualHostNode.DESIGNATED_PRIMARY));

        final Connection connection = getConnection(_positiveFailoverUrl);
        assertNotNull("Expected to get a valid connection to new primary", connection);
        assertProducingConsuming(connection);
    }

}
