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

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.management.ObjectName;

import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.server.store.berkeleydb.jmx.ManagedBDBHAMessageStore;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class TwoNodeTest extends QpidBrokerTestCase
{
    private static final String VIRTUAL_HOST = "test";

    private static final String MANAGED_OBJECT_QUERY = "org.apache.qpid:type=BDBHAMessageStore,name=" + ObjectName.quote(VIRTUAL_HOST);
    private static final int NUMBER_OF_NODES = 2;

    private final GroupCreator _groupCreator = new GroupCreator(this, VIRTUAL_HOST, NUMBER_OF_NODES);
    private final JMXTestUtils _jmxUtils = new JMXTestUtils(this);

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
    protected void tearDown() throws Exception
    {
        try
        {
            _jmxUtils.close();
        }
        finally
        {
            super.tearDown();
        }
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
        _brokerFailoverUrl = _groupCreator.getConnectionUrlForAllClusterNodes();
        _groupCreator.startCluster();
    }

    public void testMasterDesignatedPrimaryCanBeRestartedWithoutReplica() throws Exception
    {
        startCluster(true);
        final Connection initialConnection = getConnection(_brokerFailoverUrl);
        int masterPort = _groupCreator.getBrokerPortNumberFromConnection(initialConnection);
        assertProducingConsuming(initialConnection);
        initialConnection.close();
        _groupCreator.stopCluster();
        _groupCreator.startNode(masterPort);
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
        _groupCreator.stopCluster();
        _groupCreator.startClusterParallel();
        final Connection secondConnection = getConnection(_brokerFailoverUrl);
        assertProducingConsuming(secondConnection);
        secondConnection.close();
    }

    public void testDesignatedPrimaryContinuesAfterSecondaryStopped() throws Exception
    {
        startCluster(true);
        _groupCreator.stopNode(_groupCreator.getBrokerPortNumberOfSecondaryNode());
        final Connection connection = getConnection(_brokerFailoverUrl);
        assertNotNull("Expected to get a valid connection to primary", connection);
        assertProducingConsuming(connection);
    }

    public void testPersistentOperationsFailOnNonDesignatedPrimaryAfterSecondaryStopped() throws Exception
    {
        startCluster(false);
        _groupCreator.stopNode(_groupCreator.getBrokerPortNumberOfSecondaryNode());

        try
        {
            Connection connection = getConnection(_brokerFailoverUrl);
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
        final ManagedBDBHAMessageStore primaryStoreBean = getStoreBeanForNodeAtBrokerPort(_groupCreator.getBrokerPortNumberOfPrimary());
        assertTrue("Expected primary node to be set as designated primary", primaryStoreBean.getDesignatedPrimary());

        final ManagedBDBHAMessageStore secondaryStoreBean = getStoreBeanForNodeAtBrokerPort(_groupCreator.getBrokerPortNumberOfSecondaryNode());
        assertFalse("Expected secondary node to NOT be set as designated primary", secondaryStoreBean.getDesignatedPrimary());
    }

    public void testSecondaryDesignatedAsPrimaryAfterOriginalPrimaryStopped() throws Exception
    {
        startCluster(true);
        final ManagedBDBHAMessageStore storeBean = getStoreBeanForNodeAtBrokerPort(_groupCreator.getBrokerPortNumberOfSecondaryNode());
        _groupCreator.stopNode(_groupCreator.getBrokerPortNumberOfPrimary());

        assertFalse("Expected node to NOT be set as designated primary", storeBean.getDesignatedPrimary());
        storeBean.setDesignatedPrimary(true);

        long limit = System.currentTimeMillis() + 5000;
        while( !storeBean.getDesignatedPrimary() && System.currentTimeMillis() < limit)
        {
            Thread.sleep(100);
        }
        assertTrue("Expected node to now be set as designated primary", storeBean.getDesignatedPrimary());

        final Connection connection = getConnection(_brokerFailoverUrl);
        assertNotNull("Expected to get a valid connection to new primary", connection);
        assertProducingConsuming(connection);
    }

    private ManagedBDBHAMessageStore getStoreBeanForNodeAtBrokerPort(
            final int activeBrokerPortNumber) throws Exception
    {
        _jmxUtils.open(activeBrokerPortNumber);

        ManagedBDBHAMessageStore storeBean = _jmxUtils.getManagedObject(ManagedBDBHAMessageStore.class, MANAGED_OBJECT_QUERY);
        return storeBean;
    }

}
