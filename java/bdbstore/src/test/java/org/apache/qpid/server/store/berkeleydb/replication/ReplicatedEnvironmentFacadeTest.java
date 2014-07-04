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
package org.apache.qpid.server.store.berkeleydb.replication;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.store.berkeleydb.EnvironmentFacade;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.rep.NodeState;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicatedEnvironment.State;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.ReplicationNode;
import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;

public class ReplicatedEnvironmentFacadeTest extends QpidTestCase
{
    private static final Logger LOGGER = Logger.getLogger(ReplicatedEnvironmentFacadeTest.class);
    private static final int TEST_NODE_PORT = new QpidTestCase().findFreePort();
    private static final int LISTENER_TIMEOUT = 5;
    private static final int WAIT_STATE_CHANGE_TIMEOUT = 30;
    private static final String TEST_GROUP_NAME = "testGroupName";
    private static final String TEST_NODE_NAME = "testNodeName";
    private static final String TEST_NODE_HOST_PORT = "localhost:" + TEST_NODE_PORT;
    private static final String TEST_NODE_HELPER_HOST_PORT = TEST_NODE_HOST_PORT;
    private static final Durability TEST_DURABILITY = Durability.parse("SYNC,NO_SYNC,SIMPLE_MAJORITY");
    private static final boolean TEST_DESIGNATED_PRIMARY = false;
    private static final int TEST_PRIORITY = 1;
    private static final int TEST_ELECTABLE_GROUP_OVERRIDE = 0;

    private File _storePath;
    private final Map<String, ReplicatedEnvironmentFacade> _nodes = new HashMap<String, ReplicatedEnvironmentFacade>();

    public void setUp() throws Exception
    {
        super.setUp();

        _storePath = TestFileUtils.createTestDirectory("bdb", true);

        setTestSystemProperty(ReplicatedEnvironmentFacade.DB_PING_SOCKET_TIMEOUT_PROPERTY_NAME, "100");
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            for (EnvironmentFacade ef : _nodes.values())
            {
                ef.close();
            }
        }
        finally
        {
            try
            {
                if (_storePath != null)
                {
                    FileUtils.delete(_storePath, true);
                }
            }
            finally
            {
                super.tearDown();
            }
        }
    }
    public void testEnvironmentFacade() throws Exception
    {
        EnvironmentFacade ef = createMaster();
        assertNotNull("Environment should not be null", ef);
        Environment e = ef.getEnvironment();
        assertTrue("Environment is not valid", e.isValid());
    }

    public void testClose() throws Exception
    {
        EnvironmentFacade ef = createMaster();
        ef.close();
        Environment e = ef.getEnvironment();

        assertNull("Environment should be null after facade close", e);
    }

    public void testOpenDatabaseReusesCachedHandle() throws Exception
    {
        DatabaseConfig createIfAbsentDbConfig = DatabaseConfig.DEFAULT.setAllowCreate(true);

        EnvironmentFacade ef = createMaster();
        Database handle1 = ef.openDatabase("myDatabase", createIfAbsentDbConfig);
        assertNotNull(handle1);

        Database handle2 = ef.openDatabase("myDatabase", createIfAbsentDbConfig);
        assertSame("Database handle should be cached", handle1, handle2);

        ef.closeDatabase("myDatabase");

        Database handle3 = ef.openDatabase("myDatabase", createIfAbsentDbConfig);
        assertNotSame("Expecting a new handle after database closure", handle1, handle3);
    }

    public void testGetGroupName() throws Exception
    {
        assertEquals("Unexpected group name", TEST_GROUP_NAME, createMaster().getGroupName());
    }

    public void testGetNodeName() throws Exception
    {
        assertEquals("Unexpected group name", TEST_NODE_NAME, createMaster().getNodeName());
    }

    public void testLastKnownReplicationTransactionId() throws Exception
    {
        ReplicatedEnvironmentFacade master = createMaster();
        long lastKnownReplicationTransactionId = master.getLastKnownReplicationTransactionId();
        assertTrue("Unexpected LastKnownReplicationTransactionId " + lastKnownReplicationTransactionId, lastKnownReplicationTransactionId > 0);
    }

    public void testGetNodeHostPort() throws Exception
    {
        assertEquals("Unexpected node host port", TEST_NODE_HOST_PORT, createMaster().getHostPort());
    }

    public void testGetHelperHostPort() throws Exception
    {
        assertEquals("Unexpected node helper host port", TEST_NODE_HELPER_HOST_PORT, createMaster().getHelperHostPort());
    }

    public void testSetMessageStoreDurability() throws Exception
    {
        ReplicatedEnvironmentFacade master = createMaster();
        assertEquals("Unexpected message store durability",
                new Durability(Durability.SyncPolicy.NO_SYNC, Durability.SyncPolicy.NO_SYNC, Durability.ReplicaAckPolicy.SIMPLE_MAJORITY),
                master.getRealMessageStoreDurability());
        assertEquals("Unexpected durability", TEST_DURABILITY, master.getMessageStoreDurability());
        assertTrue("Unexpected coalescing syn", master.isCoalescingSync());

        try
        {
            master.setMessageStoreDurability(TEST_DURABILITY.getLocalSync(), TEST_DURABILITY.getReplicaSync(), TEST_DURABILITY.getReplicaAck());
            fail("Cannot set message store durability twice");
        }
        catch(IllegalStateException e)
        {
            // pass
        }
    }

    public void testGetNodeState() throws Exception
    {
        assertEquals("Unexpected state", State.MASTER.name(), createMaster().getNodeState());
    }

    public void testPriority() throws Exception
    {
        ReplicatedEnvironmentFacade facade = createMaster();
        assertEquals("Unexpected priority", TEST_PRIORITY, facade.getPriority());
        Future<Void> future = facade.setPriority(TEST_PRIORITY + 1);
        future.get(5, TimeUnit.SECONDS);
        assertEquals("Unexpected priority after change", TEST_PRIORITY + 1, facade.getPriority());
    }

    public void testDesignatedPrimary()  throws Exception
    {
        ReplicatedEnvironmentFacade master = createMaster();
        assertEquals("Unexpected designated primary", TEST_DESIGNATED_PRIMARY, master.isDesignatedPrimary());
        Future<Void> future = master.setDesignatedPrimary(!TEST_DESIGNATED_PRIMARY);
        future.get(5, TimeUnit.SECONDS);
        assertEquals("Unexpected designated primary after change", !TEST_DESIGNATED_PRIMARY, master.isDesignatedPrimary());
    }

    public void testElectableGroupSizeOverride() throws Exception
    {
        ReplicatedEnvironmentFacade facade = createMaster();
        assertEquals("Unexpected Electable Group Size Override", TEST_ELECTABLE_GROUP_OVERRIDE, facade.getElectableGroupSizeOverride());
        Future<Void> future = facade.setElectableGroupSizeOverride(TEST_ELECTABLE_GROUP_OVERRIDE + 1);
        future.get(5, TimeUnit.SECONDS);
        assertEquals("Unexpected Electable Group Size Override after change", TEST_ELECTABLE_GROUP_OVERRIDE + 1, facade.getElectableGroupSizeOverride());
    }

    public void testReplicationGroupListenerHearsAboutExistingRemoteReplicationNodes() throws Exception
    {
        ReplicatedEnvironmentFacade master = createMaster();
        String nodeName2 = TEST_NODE_NAME + "_2";
        String host = "localhost";
        int port = getNextAvailable(TEST_NODE_PORT + 1);
        String node2NodeHostPort = host + ":" + port;

        final AtomicInteger invocationCount = new AtomicInteger();
        final CountDownLatch nodeRecoveryLatch = new CountDownLatch(1);
        ReplicationGroupListener listener = new NoopReplicationGroupListener()
        {
            @Override
            public void onReplicationNodeRecovered(ReplicationNode node)
            {
                nodeRecoveryLatch.countDown();
                invocationCount.incrementAndGet();
            }
        };

        createReplica(nodeName2, node2NodeHostPort, listener);

        assertEquals("Unexpected number of nodes", 2, master.getNumberOfElectableGroupMembers());

        assertTrue("Listener not fired within timeout", nodeRecoveryLatch.await(LISTENER_TIMEOUT, TimeUnit.SECONDS));
        assertEquals("Unexpected number of listener invocations", 1, invocationCount.get());
    }

    public void testReplicationGroupListenerHearsNodeAdded() throws Exception
    {
        final CountDownLatch nodeAddedLatch = new CountDownLatch(1);
        final AtomicInteger invocationCount = new AtomicInteger();
        ReplicationGroupListener listener = new NoopReplicationGroupListener()
        {
            @Override
            public void onReplicationNodeAddedToGroup(ReplicationNode node)
            {
                invocationCount.getAndIncrement();
                nodeAddedLatch.countDown();
            }
        };

        TestStateChangeListener stateChangeListener = new TestStateChangeListener(State.MASTER);
        ReplicatedEnvironmentFacade replicatedEnvironmentFacade = addNode(stateChangeListener, listener);
        assertTrue("Master was not started", stateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        assertEquals("Unexpected number of nodes at start of test", 1, replicatedEnvironmentFacade.getNumberOfElectableGroupMembers());

        String node2Name = TEST_NODE_NAME + "_2";
        String node2NodeHostPort = "localhost" + ":" + getNextAvailable(TEST_NODE_PORT + 1);
        createReplica(node2Name, node2NodeHostPort, new NoopReplicationGroupListener());

        assertTrue("Listener not fired within timeout", nodeAddedLatch.await(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        assertEquals("Unexpected number of nodes", 2, replicatedEnvironmentFacade.getNumberOfElectableGroupMembers());

        assertEquals("Unexpected number of listener invocations", 1, invocationCount.get());
    }

    public void testReplicationGroupListenerHearsNodeRemoved() throws Exception
    {
        final CountDownLatch nodeDeletedLatch = new CountDownLatch(1);
        final CountDownLatch nodeAddedLatch = new CountDownLatch(1);
        final AtomicInteger invocationCount = new AtomicInteger();
        ReplicationGroupListener listener = new NoopReplicationGroupListener()
        {
            @Override
            public void onReplicationNodeRecovered(ReplicationNode node)
            {
                nodeAddedLatch.countDown();
            }

            @Override
            public void onReplicationNodeAddedToGroup(ReplicationNode node)
            {
                nodeAddedLatch.countDown();
            }

            @Override
            public void onReplicationNodeRemovedFromGroup(ReplicationNode node)
            {
                invocationCount.getAndIncrement();
                nodeDeletedLatch.countDown();
            }
        };

        TestStateChangeListener stateChangeListener = new TestStateChangeListener(State.MASTER);
        ReplicatedEnvironmentFacade replicatedEnvironmentFacade = addNode(stateChangeListener, listener);
        assertTrue("Master was not started", stateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        String node2Name = TEST_NODE_NAME + "_2";
        String node2NodeHostPort = "localhost" + ":" + getNextAvailable(TEST_NODE_PORT + 1);
        createReplica(node2Name, node2NodeHostPort, new NoopReplicationGroupListener());

        assertEquals("Unexpected number of nodes at start of test", 2, replicatedEnvironmentFacade.getNumberOfElectableGroupMembers());

        // Need to await the listener hearing the addition of the node to the model.
        assertTrue("Node add not fired within timeout", nodeAddedLatch.await(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        // Now remove the node and ensure we hear the event
        replicatedEnvironmentFacade.removeNodeFromGroup(node2Name);

        assertTrue("Node delete not fired within timeout", nodeDeletedLatch.await(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        assertEquals("Unexpected number of nodes after node removal", 1, replicatedEnvironmentFacade.getNumberOfElectableGroupMembers());

        assertEquals("Unexpected number of listener invocations", 1, invocationCount.get());
    }

    public void testMasterHearsRemoteNodeRoles() throws Exception
    {
        final String node2Name = TEST_NODE_NAME + "_2";
        final CountDownLatch nodeAddedLatch = new CountDownLatch(1);
        final AtomicReference<ReplicationNode> nodeRef = new AtomicReference<ReplicationNode>();
        final CountDownLatch stateLatch = new CountDownLatch(1);
        final AtomicReference<NodeState> stateRef = new AtomicReference<NodeState>();
        ReplicationGroupListener listener = new NoopReplicationGroupListener()
        {
            @Override
            public void onReplicationNodeAddedToGroup(ReplicationNode node)
            {
                nodeRef.set(node);
                nodeAddedLatch.countDown();
            }

            @Override
            public void onNodeState(ReplicationNode node, NodeState nodeState)
            {
                if (node2Name.equals(node.getName()))
                {
                    stateRef.set(nodeState);
                    stateLatch.countDown();
                }
            }
        };

        TestStateChangeListener stateChangeListener = new TestStateChangeListener(State.MASTER);
        ReplicatedEnvironmentFacade replicatedEnvironmentFacade = addNode(stateChangeListener, listener);
        assertTrue("Master was not started", stateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        String node2NodeHostPort = "localhost" + ":" + getNextAvailable(TEST_NODE_PORT + 1);
        createReplica(node2Name, node2NodeHostPort, new NoopReplicationGroupListener());

        assertEquals("Unexpected number of nodes at start of test", 2, replicatedEnvironmentFacade.getNumberOfElectableGroupMembers());

        assertTrue("Node add not fired within timeout", nodeAddedLatch.await(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        ReplicationNode remoteNode = (ReplicationNode)nodeRef.get();
        assertEquals("Unexpected node name", node2Name, remoteNode.getName());

        assertTrue("Node state not fired within timeout", stateLatch.await(LISTENER_TIMEOUT, TimeUnit.SECONDS));
        assertEquals("Unexpected node state", State.REPLICA, stateRef.get().getNodeState());
    }

    public void testRemoveNodeFromGroup() throws Exception
    {
        ReplicatedEnvironmentFacade environmentFacade = createMaster();

        String node2Name = TEST_NODE_NAME + "_2";
        String node2NodeHostPort = "localhost:" + getNextAvailable(TEST_NODE_PORT + 1);
        ReplicatedEnvironmentFacade ref2 = createReplica(node2Name, node2NodeHostPort, new NoopReplicationGroupListener());

        assertEquals("Unexpected group members count", 2, environmentFacade.getNumberOfElectableGroupMembers());
        ref2.close();

        environmentFacade.removeNodeFromGroup(node2Name);
        assertEquals("Unexpected group members count", 1, environmentFacade.getNumberOfElectableGroupMembers());
    }


    public void testEnvironmentFacadeDetectsRemovalOfRemoteNode() throws Exception
    {
        final String replicaName = TEST_NODE_NAME + "_1";
        final CountDownLatch nodeRemovedLatch = new CountDownLatch(1);
        final CountDownLatch nodeAddedLatch = new CountDownLatch(1);
        final AtomicReference<ReplicationNode> addedNodeRef = new AtomicReference<ReplicationNode>();
        final AtomicReference<ReplicationNode> removedNodeRef = new AtomicReference<ReplicationNode>();
        final CountDownLatch stateLatch = new CountDownLatch(1);
        final AtomicReference<NodeState> stateRef = new AtomicReference<NodeState>();

        ReplicationGroupListener listener = new NoopReplicationGroupListener()
        {
            @Override
            public void onReplicationNodeAddedToGroup(ReplicationNode node)
            {
                if (addedNodeRef.compareAndSet(null, node))
                {
                    nodeAddedLatch.countDown();
                }
            }

            @Override
            public void onReplicationNodeRemovedFromGroup(ReplicationNode node)
            {
                removedNodeRef.set(node);
                nodeRemovedLatch.countDown();
            }

            @Override
            public void onNodeState(ReplicationNode node, NodeState nodeState)
            {
                if (replicaName.equals(node.getName()))
                {
                    stateRef.set(nodeState);
                    stateLatch.countDown();
                }
            }
        };

        TestStateChangeListener stateChangeListener = new TestStateChangeListener(State.MASTER);
        final ReplicatedEnvironmentFacade masterEnvironment = addNode(stateChangeListener, listener);
        assertTrue("Master was not started", stateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        masterEnvironment.setDesignatedPrimary(true);

        int replica1Port = getNextAvailable(TEST_NODE_PORT + 1);
        String node1NodeHostPort = "localhost:" + replica1Port;

        ReplicatedEnvironmentFacade replica = createReplica(replicaName, node1NodeHostPort, new NoopReplicationGroupListener());

        assertTrue("Node should be added", nodeAddedLatch.await(WAIT_STATE_CHANGE_TIMEOUT, TimeUnit.SECONDS));

        ReplicationNode node = addedNodeRef.get();
        assertEquals("Unexpected node name", replicaName, node.getName());

        assertTrue("Node state was not heard", stateLatch.await(WAIT_STATE_CHANGE_TIMEOUT, TimeUnit.SECONDS));
        assertEquals("Unexpected node role", State.REPLICA, stateRef.get().getNodeState());
        assertEquals("Unexpected node name", replicaName, stateRef.get().getNodeName());

        replica.close();
        masterEnvironment.removeNodeFromGroup(node.getName());

        assertTrue("Node deleting is undetected by the environment facade", nodeRemovedLatch.await(WAIT_STATE_CHANGE_TIMEOUT, TimeUnit.SECONDS));
        assertEquals("Unexpected node is deleted", node, removedNodeRef.get());
    }

    public void testCloseStateTransitions() throws Exception
    {
        ReplicatedEnvironmentFacade replicatedEnvironmentFacade = createMaster();

        assertEquals("Unexpected state " + replicatedEnvironmentFacade.getFacadeState(), ReplicatedEnvironmentFacade.State.OPEN, replicatedEnvironmentFacade.getFacadeState());
        replicatedEnvironmentFacade.close();
        assertEquals("Unexpected state " + replicatedEnvironmentFacade.getFacadeState(), ReplicatedEnvironmentFacade.State.CLOSED, replicatedEnvironmentFacade.getFacadeState());
    }

    public void testEnvironmentAutomaticallyRestartsAndBecomesUnknownOnInsufficientReplicas() throws Exception
    {
        final CountDownLatch masterLatch = new CountDownLatch(1);
        final AtomicInteger masterStateChangeCount = new AtomicInteger();
        final CountDownLatch unknownLatch = new CountDownLatch(1);
        final AtomicInteger unknownStateChangeCount = new AtomicInteger();
        StateChangeListener stateChangeListener = new StateChangeListener()
        {
            @Override
            public void stateChange(StateChangeEvent stateChangeEvent) throws RuntimeException
            {
                if (stateChangeEvent.getState() == State.MASTER)
                {
                    masterStateChangeCount.incrementAndGet();
                    masterLatch.countDown();
                }
                else if (stateChangeEvent.getState() == State.UNKNOWN)
                {
                    unknownStateChangeCount.incrementAndGet();
                    unknownLatch.countDown();
                }
            }
        };

        addNode(stateChangeListener, new NoopReplicationGroupListener());
        assertTrue("Master was not started", masterLatch.await(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        int replica1Port = getNextAvailable(TEST_NODE_PORT + 1);
        String node1NodeHostPort = "localhost:" + replica1Port;
        int replica2Port = getNextAvailable(replica1Port + 1);
        String node2NodeHostPort = "localhost:" + replica2Port;

        ReplicatedEnvironmentFacade replica1 = createReplica(TEST_NODE_NAME + "_1", node1NodeHostPort, new NoopReplicationGroupListener());
        ReplicatedEnvironmentFacade replica2 = createReplica(TEST_NODE_NAME + "_2", node2NodeHostPort, new NoopReplicationGroupListener());

        // close replicas
        replica1.close();
        replica2.close();

        assertTrue("Environment should be recreated and go into unknown state",
                unknownLatch.await(WAIT_STATE_CHANGE_TIMEOUT, TimeUnit.SECONDS));

        assertEquals("Node made master an unexpected number of times", 1, masterStateChangeCount.get());
        assertEquals("Node made unknown an unexpected number of times", 1, unknownStateChangeCount.get());
    }

    public void testTransferMasterToSelf() throws Exception
    {
        final CountDownLatch firstNodeReplicaStateLatch = new CountDownLatch(1);
        final CountDownLatch firstNodeMasterStateLatch = new CountDownLatch(1);
        StateChangeListener stateChangeListener = new StateChangeListener(){

            @Override
            public void stateChange(StateChangeEvent event) throws RuntimeException
            {
                ReplicatedEnvironment.State state = event.getState();
                if (state == ReplicatedEnvironment.State.REPLICA)
                {
                    firstNodeReplicaStateLatch.countDown();
                }
                if (state == ReplicatedEnvironment.State.MASTER)
                {
                    firstNodeMasterStateLatch.countDown();
                }
            }
        };
        ReplicatedEnvironmentFacade firstNode = addNode(stateChangeListener, new NoopReplicationGroupListener());
        assertTrue("Environment did not become a master", firstNodeMasterStateLatch.await(10, TimeUnit.SECONDS));

        int replica1Port = getNextAvailable(TEST_NODE_PORT + 1);
        String node1NodeHostPort = "localhost:" + replica1Port;
        ReplicatedEnvironmentFacade secondNode = createReplica(TEST_NODE_NAME + "_1", node1NodeHostPort, new NoopReplicationGroupListener());
        assertEquals("Unexpected state", ReplicatedEnvironment.State.REPLICA.name(), secondNode.getNodeState());

        int replica2Port = getNextAvailable(replica1Port + 1);
        String node2NodeHostPort = "localhost:" + replica2Port;
        final CountDownLatch replicaStateLatch = new CountDownLatch(1);
        final CountDownLatch masterStateLatch = new CountDownLatch(1);
        StateChangeListener testStateChangeListener = new StateChangeListener()
        {
            @Override
            public void stateChange(StateChangeEvent event) throws RuntimeException
            {
                ReplicatedEnvironment.State state = event.getState();
                if (state == ReplicatedEnvironment.State.REPLICA)
                {
                    replicaStateLatch.countDown();
                }
                if (state == ReplicatedEnvironment.State.MASTER)
                {
                    masterStateLatch.countDown();
                }
            }
        };
        ReplicatedEnvironmentFacade thirdNode = addNode(TEST_NODE_NAME + "_2", node2NodeHostPort, TEST_DESIGNATED_PRIMARY,
                                                        testStateChangeListener, new NoopReplicationGroupListener());
        assertTrue("Environment did not become a replica", replicaStateLatch.await(10, TimeUnit.SECONDS));
        assertEquals(3, thirdNode.getNumberOfElectableGroupMembers());

        thirdNode.transferMasterToSelfAsynchronously();
        assertTrue("Environment did not become a master", masterStateLatch.await(10, TimeUnit.SECONDS));
        assertTrue("First node environment did not become a replica", firstNodeReplicaStateLatch.await(10, TimeUnit.SECONDS));
        assertEquals("Unexpected state", ReplicatedEnvironment.State.REPLICA.name(), firstNode.getNodeState());
    }

    public void testTransferMasterAnotherNode() throws Exception
    {
        final CountDownLatch firstNodeReplicaStateLatch = new CountDownLatch(1);
        final CountDownLatch firstNodeMasterStateLatch = new CountDownLatch(1);
        StateChangeListener stateChangeListener = new StateChangeListener(){

            @Override
            public void stateChange(StateChangeEvent event) throws RuntimeException
            {
                ReplicatedEnvironment.State state = event.getState();
                if (state == ReplicatedEnvironment.State.REPLICA)
                {
                    firstNodeReplicaStateLatch.countDown();
                }
                if (state == ReplicatedEnvironment.State.MASTER)
                {
                    firstNodeMasterStateLatch.countDown();
                }
            }
        };
        ReplicatedEnvironmentFacade firstNode = addNode(stateChangeListener, new NoopReplicationGroupListener());
        assertTrue("Environment did not become a master", firstNodeMasterStateLatch.await(10, TimeUnit.SECONDS));

        int replica1Port = getNextAvailable(TEST_NODE_PORT + 1);
        String node1NodeHostPort = "localhost:" + replica1Port;
        ReplicatedEnvironmentFacade secondNode = createReplica(TEST_NODE_NAME + "_1", node1NodeHostPort, new NoopReplicationGroupListener());
        assertEquals("Unexpected state", ReplicatedEnvironment.State.REPLICA.name(), secondNode.getNodeState());

        int replica2Port = getNextAvailable(replica1Port + 1);
        String node2NodeHostPort = "localhost:" + replica2Port;
        final CountDownLatch replicaStateLatch = new CountDownLatch(1);
        final CountDownLatch masterStateLatch = new CountDownLatch(1);
        StateChangeListener testStateChangeListener = new StateChangeListener()
        {
            @Override
            public void stateChange(StateChangeEvent event) throws RuntimeException
            {
                ReplicatedEnvironment.State state = event.getState();
                if (state == ReplicatedEnvironment.State.REPLICA)
                {
                    replicaStateLatch.countDown();
                }
                if (state == ReplicatedEnvironment.State.MASTER)
                {
                    masterStateLatch.countDown();
                }
            }
        };
        String thirdNodeName = TEST_NODE_NAME + "_2";
        ReplicatedEnvironmentFacade thirdNode = addNode(thirdNodeName, node2NodeHostPort, TEST_DESIGNATED_PRIMARY,
                                                        testStateChangeListener, new NoopReplicationGroupListener());
        assertTrue("Environment did not become a replica", replicaStateLatch.await(10, TimeUnit.SECONDS));
        assertEquals(3, thirdNode.getNumberOfElectableGroupMembers());

        firstNode.transferMasterAsynchronously(thirdNodeName);
        assertTrue("Environment did not become a master", masterStateLatch.await(10, TimeUnit.SECONDS));
        assertTrue("First node environment did not become a replica", firstNodeReplicaStateLatch.await(10, TimeUnit.SECONDS));
        assertEquals("Unexpected state", ReplicatedEnvironment.State.REPLICA.name(), firstNode.getNodeState());
    }

    public void testBeginTransaction() throws Exception
    {
        ReplicatedEnvironmentFacade facade = createMaster();
        Transaction txn = null;
        try
        {
            txn = facade.beginTransaction();
            assertNotNull("Transaction is not created", txn);
            txn.commit();
            txn = null;
        }
        finally
        {
            if (txn != null)
            {
                txn.abort();
            }
        }
    }

    public void testSetPermittedNodes() throws Exception
    {
        ReplicatedEnvironmentFacade firstNode = createMaster();

        Set<String> permittedNodes = new HashSet<String>();
        permittedNodes.add("localhost:" + TEST_NODE_PORT);
        permittedNodes.add("localhost:" + getNextAvailable(TEST_NODE_PORT + 1));
        firstNode.setPermittedNodes(permittedNodes);

        NodeState nodeState = firstNode.getRemoteNodeState(new ReplicatedEnvironmentFacade.ReplicationNodeImpl(TEST_NODE_NAME, TEST_NODE_HOST_PORT));

        Collection<String> appStatePermittedNodes = firstNode.bytesToPermittedNodeList(nodeState.getAppState());
        assertEquals("Unexpected permitted nodes", permittedNodes, new HashSet<String>(appStatePermittedNodes));
    }

    public void testPermittedNodeIsAllowedToConnect() throws Exception
    {
        ReplicatedEnvironmentFacade firstNode = createMaster();

        int replica1Port = getNextAvailable(TEST_NODE_PORT + 1);
        String node1NodeHostPort = "localhost:" + replica1Port;

        Set<String> permittedNodes = new HashSet<String>();
        permittedNodes.add("localhost:" + TEST_NODE_PORT);
        permittedNodes.add(node1NodeHostPort);
        firstNode.setPermittedNodes(permittedNodes);

        ReplicatedEnvironmentConfiguration configuration =  createReplicatedEnvironmentConfiguration(TEST_NODE_NAME + "_1", node1NodeHostPort, false);
        when(configuration.getHelperNodeName()).thenReturn(TEST_NODE_NAME);

        TestStateChangeListener stateChangeListener = new TestStateChangeListener(State.REPLICA);
        ReplicatedEnvironmentFacade secondNode = createReplicatedEnvironmentFacade(TEST_NODE_NAME + "_1",
                stateChangeListener, new NoopReplicationGroupListener(), configuration);
        assertTrue("Environment was not created", stateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS));
        assertEquals("Unexpected state", State.REPLICA.name(), secondNode.getNodeState());
    }

    public void testNotPermittedNodeIsNotAllowedToConnect() throws Exception
    {
        ReplicatedEnvironmentFacade firstNode = createMaster();

        int replica1Port = getNextAvailable(TEST_NODE_PORT + 1);
        String node1NodeHostPort = "localhost:" + replica1Port;

        Set<String> permittedNodes = new HashSet<String>();
        permittedNodes.add("localhost:" + TEST_NODE_PORT);

        firstNode.setPermittedNodes(permittedNodes);

        ReplicatedEnvironmentConfiguration configuration =  createReplicatedEnvironmentConfiguration(TEST_NODE_NAME + "_1", node1NodeHostPort, false);
        when(configuration.getHelperNodeName()).thenReturn(TEST_NODE_NAME);

        try
        {
            createReplicatedEnvironmentFacade(TEST_NODE_NAME + "_1", new TestStateChangeListener(State.REPLICA), new NoopReplicationGroupListener(), configuration);
            fail("Node is not allowed to connect from " + node1NodeHostPort + " but environment was successfully created");
        }
        catch (IllegalConfigurationException e)
        {
            assertEquals("Unexpected exception message", String.format("Node from '%s' is not permitted!",
                    node1NodeHostPort), e.getMessage());
        }
    }

    public void testIntruderNodeIsDetected() throws Exception
    {
        final CountDownLatch intruderLatch = new CountDownLatch(1);
        ReplicationGroupListener listener = new NoopReplicationGroupListener()
        {
            @Override
            public void onIntruderNode(ReplicationNode node)
            {
                intruderLatch.countDown();
            }
        };
        ReplicatedEnvironmentFacade firstNode = createMaster(listener);
        int replica1Port = getNextAvailable(TEST_NODE_PORT + 1);
        String node1NodeHostPort = "localhost:" + replica1Port;

        Set<String> permittedNodes = new HashSet<String>();
        permittedNodes.add("localhost:" + TEST_NODE_PORT);

        firstNode.setPermittedNodes(permittedNodes);

        String nodeName = TEST_NODE_NAME + "_1";
        File environmentPathFile = new File(_storePath, nodeName);
        environmentPathFile.mkdirs();

        ReplicationConfig replicationConfig = new ReplicationConfig(TEST_GROUP_NAME, TEST_NODE_NAME + "_1", node1NodeHostPort);
        replicationConfig.setHelperHosts(TEST_NODE_HOST_PORT);

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        envConfig.setDurability(TEST_DURABILITY);
        ReplicatedEnvironment intruder = new ReplicatedEnvironment(environmentPathFile, replicationConfig, envConfig);
        assertTrue("Intruder node was not detected", intruderLatch.await(10, TimeUnit.SECONDS));
    }

    private ReplicatedEnvironmentFacade createMaster() throws Exception
    {
        return createMaster(new NoopReplicationGroupListener());
    }

    private ReplicatedEnvironmentFacade createMaster(ReplicationGroupListener replicationGroupListener) throws Exception
    {
        TestStateChangeListener stateChangeListener = new TestStateChangeListener(State.MASTER);
        ReplicatedEnvironmentFacade env = addNode(stateChangeListener, replicationGroupListener);
        assertTrue("Environment was not created", stateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS));
        return env;
    }

    private ReplicatedEnvironmentFacade createReplica(String nodeName, String nodeHostPort, ReplicationGroupListener replicationGroupListener) throws Exception
    {
        TestStateChangeListener testStateChangeListener = new TestStateChangeListener(State.REPLICA);
        return createReplica(nodeName, nodeHostPort, testStateChangeListener, replicationGroupListener);
    }

    private ReplicatedEnvironmentFacade createReplica(String nodeName, String nodeHostPort,
            TestStateChangeListener testStateChangeListener, ReplicationGroupListener replicationGroupListener)
            throws InterruptedException
    {
        ReplicatedEnvironmentFacade replicaEnvironmentFacade = addNode(nodeName, nodeHostPort, TEST_DESIGNATED_PRIMARY,
                                                                       testStateChangeListener, replicationGroupListener);
        boolean awaitForStateChange = testStateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS);
        assertTrue("Replica " + nodeName + " did not go into desired state; current actual state is " + testStateChangeListener.getCurrentActualState(), awaitForStateChange);
        return replicaEnvironmentFacade;
    }

    private ReplicatedEnvironmentFacade addNode(String nodeName, String nodeHostPort, boolean designatedPrimary,
                                                StateChangeListener stateChangeListener, ReplicationGroupListener replicationGroupListener)
    {
        ReplicatedEnvironmentConfiguration config = createReplicatedEnvironmentConfiguration(nodeName, nodeHostPort, designatedPrimary);
        return createReplicatedEnvironmentFacade(nodeName, stateChangeListener, replicationGroupListener, config);
    }

    private ReplicatedEnvironmentFacade createReplicatedEnvironmentFacade(String nodeName, StateChangeListener stateChangeListener, ReplicationGroupListener replicationGroupListener, ReplicatedEnvironmentConfiguration config) {
        ReplicatedEnvironmentFacade ref = new ReplicatedEnvironmentFacade(config);
        ref.setStateChangeListener(stateChangeListener);
        ref.setReplicationGroupListener(replicationGroupListener);
        ref.setMessageStoreDurability(TEST_DURABILITY.getLocalSync(), TEST_DURABILITY.getReplicaSync(), TEST_DURABILITY.getReplicaAck());
        _nodes.put(nodeName, ref);
        return ref;
    }

    private ReplicatedEnvironmentFacade addNode(StateChangeListener stateChangeListener,
                                                ReplicationGroupListener replicationGroupListener)
    {
        return addNode(TEST_NODE_NAME, TEST_NODE_HOST_PORT, TEST_DESIGNATED_PRIMARY,
                       stateChangeListener, replicationGroupListener);
    }

    private ReplicatedEnvironmentConfiguration createReplicatedEnvironmentConfiguration(String nodeName, String nodeHostPort, boolean designatedPrimary)
    {
        ReplicatedEnvironmentConfiguration node = mock(ReplicatedEnvironmentConfiguration.class);
        when(node.getName()).thenReturn(nodeName);
        when(node.getHostPort()).thenReturn(nodeHostPort);
        when(node.isDesignatedPrimary()).thenReturn(designatedPrimary);
        when(node.getQuorumOverride()).thenReturn(TEST_ELECTABLE_GROUP_OVERRIDE);
        when(node.getPriority()).thenReturn(TEST_PRIORITY);
        when(node.getGroupName()).thenReturn(TEST_GROUP_NAME);
        when(node.getHelperHostPort()).thenReturn(TEST_NODE_HELPER_HOST_PORT);

        Map<String, String> repConfig = new HashMap<String, String>();
        repConfig.put(ReplicationConfig.REPLICA_ACK_TIMEOUT, "2 s");
        repConfig.put(ReplicationConfig.INSUFFICIENT_REPLICAS_TIMEOUT, "2 s");
        when(node.getReplicationParameters()).thenReturn(repConfig);
        when(node.getStorePath()).thenReturn(new File(_storePath, nodeName).getAbsolutePath());
        return node;
    }

    class NoopReplicationGroupListener implements ReplicationGroupListener
    {

        @Override
        public void onReplicationNodeAddedToGroup(ReplicationNode node)
        {
        }

        @Override
        public void onReplicationNodeRecovered(ReplicationNode node)
        {
        }

        @Override
        public void onReplicationNodeRemovedFromGroup(ReplicationNode node)
        {
        }

        @Override
        public void onNodeState(ReplicationNode node, NodeState nodeState)
        {
        }

        @Override
        public void onIntruderNode(ReplicationNode node)
        {
            LOGGER.warn("Intruder node " + node);
        }

    }
}
