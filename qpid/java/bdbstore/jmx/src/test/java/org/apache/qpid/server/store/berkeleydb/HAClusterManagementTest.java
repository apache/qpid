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

import static com.sleepycat.je.rep.ReplicatedEnvironment.State.DETACHED;
import static com.sleepycat.je.rep.ReplicatedEnvironment.State.MASTER;
import static com.sleepycat.je.rep.ReplicatedEnvironment.State.REPLICA;
import static com.sleepycat.je.rep.ReplicatedEnvironment.State.UNKNOWN;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.jms.Connection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import org.apache.log4j.Logger;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.server.store.berkeleydb.jmx.ManagedBDBHAMessageStore;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

import com.sleepycat.je.EnvironmentFailureException;

/**
 * System test verifying the ability to control a cluster via the Management API.
 *
 * @see HAClusterBlackboxTest
 */
public class HAClusterManagementTest extends QpidBrokerTestCase
{
    protected static final Logger LOGGER = Logger.getLogger(HAClusterManagementTest.class);

    private static final Set<String> NON_MASTER_STATES = new HashSet<String>(Arrays.asList(REPLICA.toString(), DETACHED.toString(), UNKNOWN.toString()));;
    private static final String VIRTUAL_HOST = "test";

    private static final String MANAGED_OBJECT_QUERY = "org.apache.qpid:type=BDBHAMessageStore,name=" + ObjectName.quote(VIRTUAL_HOST);
    private static final int NUMBER_OF_NODES = 4;

    private final HATestClusterCreator _clusterCreator = new HATestClusterCreator(this, VIRTUAL_HOST, NUMBER_OF_NODES);
    private final JMXTestUtils _jmxUtils = new JMXTestUtils(this);

    private ConnectionURL _brokerFailoverUrl;

    @Override
    protected void setUp() throws Exception
    {
        _brokerType = BrokerType.SPAWNED;

        _clusterCreator.configureClusterNodes();
        _brokerFailoverUrl = _clusterCreator.getConnectionUrlForAllClusterNodes();
        _clusterCreator.startCluster();

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

    public void testReadonlyMBeanAttributes() throws Exception
    {
        final int brokerPortNumber = getBrokerPortNumbers().iterator().next();
        final int bdbPortNumber = _clusterCreator.getBdbPortForBrokerPort(brokerPortNumber);

        ManagedBDBHAMessageStore storeBean = getStoreBeanForNodeAtBrokerPort(brokerPortNumber);
        assertEquals("Unexpected store group name", _clusterCreator.getGroupName(), storeBean.getGroupName());
        assertEquals("Unexpected store node name", _clusterCreator.getNodeNameForNodeAt(bdbPortNumber), storeBean.getNodeName());
        assertEquals("Unexpected store node host port",_clusterCreator.getNodeHostPortForNodeAt(bdbPortNumber), storeBean.getNodeHostPort());
        assertEquals("Unexpected store helper host port", _clusterCreator.getHelperHostPort(), storeBean.getHelperHostPort());
        // As we have chosen an arbitrary broker from the cluster, we cannot predict its state
        assertNotNull("Store state must not be null", storeBean.getNodeState());
    }

    public void testStateOfActiveBrokerIsMaster() throws Exception
    {
        final Connection activeConnection = getConnection(_brokerFailoverUrl);
        final int activeBrokerPortNumber = _clusterCreator.getBrokerPortNumberFromConnection(activeConnection);

        ManagedBDBHAMessageStore storeBean = getStoreBeanForNodeAtBrokerPort(activeBrokerPortNumber);
        assertEquals("Unexpected store state", MASTER.toString(), storeBean.getNodeState());
    }

    public void testStateOfNonActiveBrokerIsNotMaster() throws Exception
    {
        final Connection activeConnection = getConnection(_brokerFailoverUrl);
        final int inactiveBrokerPortNumber = _clusterCreator.getPortNumberOfAnInactiveBroker(activeConnection);
        ManagedBDBHAMessageStore storeBean = getStoreBeanForNodeAtBrokerPort(inactiveBrokerPortNumber);
        final String nodeState = storeBean.getNodeState();
        assertTrue("Unexpected store state : " + nodeState, NON_MASTER_STATES.contains(nodeState));
    }

    public void testGroupMembers() throws Exception
    {
        final int brokerPortNumber = getBrokerPortNumbers().iterator().next();

        ManagedBDBHAMessageStore storeBean = getStoreBeanForNodeAtBrokerPort(brokerPortNumber);
        awaitAllNodesJoiningGroup(storeBean, NUMBER_OF_NODES);

        final TabularData groupMembers = storeBean.getAllNodesInGroup();
        assertNotNull(groupMembers);

        for(int bdbPortNumber : _clusterCreator.getBdbPortNumbers())
        {
            final String nodeName = _clusterCreator.getNodeNameForNodeAt(bdbPortNumber);
            final String nodeHostPort = _clusterCreator.getNodeHostPortForNodeAt(bdbPortNumber);

            CompositeData row = groupMembers.get(new Object[] {nodeName});
            assertNotNull("Table does not contain row for node name " + nodeName, row);
            assertEquals(nodeHostPort, row.get(BDBHAMessageStore.GRP_MEM_COL_NODE_HOST_PORT));
        }
    }

    public void testRemoveNodeFromGroup() throws Exception
    {
        final Iterator<Integer> brokerPortNumberIterator = getBrokerPortNumbers().iterator();
        final int brokerPortNumberToMakeObservation = brokerPortNumberIterator.next();
        final int brokerPortNumberToBeRemoved = brokerPortNumberIterator.next();
        final ManagedBDBHAMessageStore storeBean = getStoreBeanForNodeAtBrokerPort(brokerPortNumberToMakeObservation);
        awaitAllNodesJoiningGroup(storeBean, NUMBER_OF_NODES);

        final String removedNodeName = _clusterCreator.getNodeNameForNodeAt(_clusterCreator.getBdbPortForBrokerPort(brokerPortNumberToBeRemoved));
        _clusterCreator.stopNode(brokerPortNumberToBeRemoved);
        storeBean.removeNodeFromGroup(removedNodeName);

        final int numberOfDataRowsAfterRemoval = storeBean.getAllNodesInGroup().size();
        assertEquals("Unexpected number of data rows before test", NUMBER_OF_NODES - 1,numberOfDataRowsAfterRemoval);
    }

    /**
     * Updates the address of a node.
     *
     * If the broker (node) can subsequently start without error then the update was a success, hence no need for an explicit
     * assert.
     *
     * @see #testRestartNodeWithNewPortNumberWithoutFirstCallingUpdateAddressThrowsAnException() for converse case
     */
    public void testUpdateAddress() throws Exception
    {
        final Iterator<Integer> brokerPortNumberIterator = getBrokerPortNumbers().iterator();
        final int brokerPortNumberToPerformUpdate = brokerPortNumberIterator.next();
        final int brokerPortNumberToBeMoved = brokerPortNumberIterator.next();
        final ManagedBDBHAMessageStore storeBean = getStoreBeanForNodeAtBrokerPort(brokerPortNumberToPerformUpdate);

        _clusterCreator.stopNode(brokerPortNumberToBeMoved);

        final int oldBdbPort = _clusterCreator.getBdbPortForBrokerPort(brokerPortNumberToBeMoved);
        final int newBdbPort = getNextAvailable(oldBdbPort + 1);

        storeBean.updateAddress(_clusterCreator.getNodeNameForNodeAt(oldBdbPort), _clusterCreator.getIpAddressOfBrokerHost(), newBdbPort);

        _clusterCreator.modifyClusterNodeBdbAddress(brokerPortNumberToBeMoved, newBdbPort);

        _clusterCreator.startNode(brokerPortNumberToBeMoved);
    }

    /**
     * @see #testUpdateAddress()
     */
    public void testRestartNodeWithNewPortNumberWithoutFirstCallingUpdateAddressThrowsAnException() throws Exception
    {
        final Iterator<Integer> brokerPortNumberIterator = getBrokerPortNumbers().iterator();
        final int brokerPortNumberToBeMoved = brokerPortNumberIterator.next();

        _clusterCreator.stopNode(brokerPortNumberToBeMoved);

        final int oldBdbPort = _clusterCreator.getBdbPortForBrokerPort(brokerPortNumberToBeMoved);
        final int newBdbPort = getNextAvailable(oldBdbPort + 1);

        // now deliberately don't call updateAddress

        _clusterCreator.modifyClusterNodeBdbAddress(brokerPortNumberToBeMoved, newBdbPort);

        try
        {
            _clusterCreator.startNode(brokerPortNumberToBeMoved);
            fail("Exception not thrown");
        }
        catch(RuntimeException rte)
        {
            //check cause was BDBs EnvironmentFailureException
            assertTrue(rte.getMessage().contains(EnvironmentFailureException.class.getName()));
            // PASS
        }
    }

    public void testVirtualHostOperationsDeniedForNonMasterNode() throws Exception
    {
        final Connection activeConnection = getConnection(_brokerFailoverUrl);
        final int inactiveBrokerPortNumber = _clusterCreator.getPortNumberOfAnInactiveBroker(activeConnection);

        ManagedBroker inactiveBroker = getManagedBrokerBeanForNodeAtBrokerPort(inactiveBrokerPortNumber);

        try
        {
            inactiveBroker.createNewQueue(getTestQueueName(), null, true);
            fail("Exception not thrown");
        }
        catch  (Exception e)
        {
            String message = e.getMessage();
            assertEquals(message, "The virtual hosts state of INITIALISING does not permit this operation.");
        }

        try
        {
            inactiveBroker.createNewExchange(getName(), "direct", true);
            fail("Exception not thrown");
        }
        catch  (Exception e)
        {
            String message = e.getMessage();
            assertEquals(message, "The virtual hosts state of INITIALISING does not permit this operation.");
        }
    }

    private ManagedBDBHAMessageStore getStoreBeanForNodeAtBrokerPort(final int brokerPortNumber) throws Exception
    {
        _jmxUtils.open(brokerPortNumber);

        return _jmxUtils.getManagedObject(ManagedBDBHAMessageStore.class, MANAGED_OBJECT_QUERY);
    }

    private ManagedBroker getManagedBrokerBeanForNodeAtBrokerPort(final int brokerPortNumber) throws Exception
    {
        _jmxUtils.open(brokerPortNumber);

        return _jmxUtils.getManagedBroker(VIRTUAL_HOST);
    }

    private void awaitAllNodesJoiningGroup(ManagedBDBHAMessageStore storeBean, int expectedNumberOfNodes) throws Exception
    {
        long totalTimeWaited = 0l;
        long waitInterval = 100l;
        long maxWaitTime = 10000;

        int currentNumberOfNodes = storeBean.getAllNodesInGroup().size();
        while (expectedNumberOfNodes > currentNumberOfNodes || totalTimeWaited > maxWaitTime)
        {
            LOGGER.debug("Still awaiting nodes to join group; expecting "
                + expectedNumberOfNodes + " node(s) but only have " + currentNumberOfNodes
                + " after " + totalTimeWaited + " ms.");

            totalTimeWaited += waitInterval;
            Thread.sleep(waitInterval);

            currentNumberOfNodes = storeBean.getAllNodesInGroup().size();
        }

        assertEquals("Unexpected number of nodes in group after " + totalTimeWaited + " ms",
                expectedNumberOfNodes ,currentNumberOfNodes);
    }
}
