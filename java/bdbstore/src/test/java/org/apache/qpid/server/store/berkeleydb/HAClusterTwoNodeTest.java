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

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

import com.sleepycat.je.rep.ReplicationConfig;

public class HAClusterTwoNodeTest extends QpidBrokerTestCase
{
    private static final long RECEIVE_TIMEOUT = 5000l;

    private static final String VIRTUAL_HOST = "test";

    private static final String MANAGED_OBJECT_QUERY = "org.apache.qpid:type=BDBHAMessageStore,name=" + VIRTUAL_HOST;
    private static final int NUMBER_OF_NODES = 2;

    private final HATestClusterCreator _clusterCreator = new HATestClusterCreator(this, VIRTUAL_HOST, NUMBER_OF_NODES);
    private final JMXTestUtils _jmxUtils = new JMXTestUtils(this);

    private ConnectionURL _brokerFailoverUrl;

    @Override
    protected void setUp() throws Exception
    {
        _brokerType = BrokerType.SPAWNED;

        assertTrue(isJavaBroker());
        assertTrue(isBrokerStorePersistent());
        _jmxUtils.setUp();

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

    private void startCluster(boolean autoDesignedPrimary) throws Exception
    {
        setSystemProperty("java.util.logging.config.file", "etc" + File.separator + "log.properties");

        String vhostPrefix = "virtualhosts.virtualhost." + VIRTUAL_HOST;

        setConfigurationProperty(vhostPrefix + ".store.repConfig(0).name", ReplicationConfig.INSUFFICIENT_REPLICAS_TIMEOUT);
        setConfigurationProperty(vhostPrefix + ".store.repConfig(0).value", "2 s");

        setConfigurationProperty(vhostPrefix + ".store.repConfig(1).name", ReplicationConfig.ELECTIONS_PRIMARY_RETRIES);
        setConfigurationProperty(vhostPrefix + ".store.repConfig(1).value", "0");

        _clusterCreator.configureClusterNodes();
        _clusterCreator.setAutoDesignatedPrimary(autoDesignedPrimary);
        _brokerFailoverUrl = _clusterCreator.getConnectionUrlForAllClusterNodes();
        _clusterCreator.startCluster();
    }

    /**
     * Tests that a two node cluster, in which the master CAN automatically designate itself primary
     * (after becoming master) continues to operate after being shut down and restarted.
     *
     * The test does not concern itself with which broker becomes master at any given point
     * (which is likely to swap during the test).
     */
    public void testClusterRestartWithAutoDesignatedPrimary() throws Exception
    {
        testClusterRestartImpl(true);
    }

    /**
     * Tests that a two node cluster, in which the master can NOT automatically designate itself
     * primary (after becoming master) continues to operate after being shut down and restarted.
     *
     * The test does not concern itself with which broker becomes master at any given point
     * (which is likely to swap during the test).
     */
    public void testClusterRestartWithoutAutoDesignatedPrimary() throws Exception
    {
        testClusterRestartImpl(false);
    }

    private void testClusterRestartImpl(boolean autoDesignatedPrimary) throws Exception
    {
        startCluster(autoDesignatedPrimary);
        final Connection initialConnection = getConnection(_brokerFailoverUrl);
        assertProducingConsuming(initialConnection);
        initialConnection.close();
        _clusterCreator.stopCluster();
        _clusterCreator.startClusterParallel();
        final Connection secondConnection = getConnection(_brokerFailoverUrl);
        assertProducingConsuming(secondConnection);
        secondConnection.close();
    }

    /**
     * This test make sure than JMS operations are still working after stopping replica
     * when master is designated primary (which is by default).
     * <p>
     * When master is not designated primary this test should fail.
     */
    public void testAutoDesignatedPrimaryContinuesAfterSecondaryStopped() throws Exception
    {
        startCluster(true);
        _clusterCreator.stopNode(_clusterCreator.getBrokerPortNumberOfSecondaryNode());
        final Connection connection = getConnection(_brokerFailoverUrl);
        assertNotNull("Expected to get a valid connection to primary", connection);
        assertProducingConsuming(connection);
    }

    public void testPersistentOperationsFailOnNonAutoDesignatedPrimarysAfterSecondaryStopped() throws Exception
    {
        
        startCluster(false);
        _clusterCreator.stopNode(_clusterCreator.getBrokerPortNumberOfSecondaryNode());
        final Connection connection = getConnection(_brokerFailoverUrl);
        assertNotNull("Expected to get a valid connection to primary", connection);
        try
        {
            assertProducingConsuming(connection);
            fail("JMS peristent operations succeded on Master 'not designated primary' buy they should fail as replica is not available");
        }
        catch(JMSException e)
        {
            // JMSException should be thrown on transaction start/commit
        }
    }

    public void testSecondaryDoesNotBecomePrimaryWhenAutoDesignatedPrimaryStopped() throws Exception
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
        final ManagedBDBHAMessageStore primaryStoreBean = getStoreBeanForNodeAtBrokerPort(_clusterCreator.getBrokerPortNumberOfPrimary());
        assertTrue("Expected primary node to be set as designated primary", primaryStoreBean.getDesignatedPrimary());

        final ManagedBDBHAMessageStore secondaryStoreBean = getStoreBeanForNodeAtBrokerPort(_clusterCreator.getBrokerPortNumberOfSecondaryNode());
        assertFalse("Expected secondary node to NOT be set as designated primary", secondaryStoreBean.getDesignatedPrimary());
    }

    public void testSecondaryDesignatedAsPrimaryAfterOrginalPrimaryStopped() throws Exception
    {
        startCluster(true);
        _clusterCreator.stopNode(_clusterCreator.getBrokerPortNumberOfPrimary());
        final ManagedBDBHAMessageStore storeBean = getStoreBeanForNodeAtBrokerPort(_clusterCreator.getBrokerPortNumberOfSecondaryNode());

        assertFalse("Expected node to NOT be set as designated primary", storeBean.getDesignatedPrimary());
        storeBean.setDesignatedPrimary(true);
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

    private void assertProducingConsuming(final Connection connection) throws JMSException, Exception
    {
        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        Destination destination = session.createQueue(getTestQueueName());
        MessageConsumer consumer = session.createConsumer(destination);
        sendMessage(session, destination, 1);
        connection.start();
        Message m1 = consumer.receive(RECEIVE_TIMEOUT);
        assertNotNull("Message 1 is not received", m1);
        assertEquals("Unexpected first message received", 0, m1.getIntProperty(INDEX));
        session.commit();
    }

}
