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

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.rep.NodeState;
import com.sleepycat.je.rep.ReplicaWriteException;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicatedEnvironment.State;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.ReplicationNode;
import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.store.berkeleydb.EnvironmentFacade;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.test.utils.PortHelper;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;

public class ReplicatedEnvironmentFacadeTest extends QpidTestCase
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplicatedEnvironmentFacadeTest.class);
    private static final int LISTENER_TIMEOUT = 5;
    private static final int WAIT_STATE_CHANGE_TIMEOUT = 30;

    private final PortHelper _portHelper = new PortHelper();

    private final String TEST_GROUP_NAME = "testGroupName";
    private final String TEST_NODE_NAME = "testNodeName";
    private final int TEST_NODE_PORT = _portHelper.getNextAvailable();
    private final String TEST_NODE_HOST_PORT = "localhost:" + TEST_NODE_PORT;
    private final String TEST_NODE_HELPER_HOST_PORT = TEST_NODE_HOST_PORT;
    private final Durability TEST_DURABILITY = Durability.parse("SYNC,NO_SYNC,SIMPLE_MAJORITY");
    private final boolean TEST_DESIGNATED_PRIMARY = false;
    private final int TEST_PRIORITY = 1;
    private final int TEST_ELECTABLE_GROUP_OVERRIDE = 0;

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

        _portHelper.waitUntilAllocatedPortsAreFree();
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

    public void testOpenDatabaseWhenFacadeIsNotOpened() throws Exception
    {
        DatabaseConfig createIfAbsentDbConfig = DatabaseConfig.DEFAULT.setAllowCreate(true);

        EnvironmentFacade ef = createMaster();
        ef.close();

        try
        {
            ef.openDatabase("myDatabase", createIfAbsentDbConfig );
            fail("Database open should fail");
        }
        catch(ConnectionScopedRuntimeException e)
        {
            assertEquals("Unexpected exception", "Environment facade is not in opened state", e.getMessage());
        }
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
        assertTrue("Unexpected coalescing sync", master.isCoalescingSync());

        master.setMessageStoreDurability(Durability.SyncPolicy.WRITE_NO_SYNC, Durability.SyncPolicy.SYNC, Durability.ReplicaAckPolicy.ALL);
        assertEquals("Unexpected message store durability",
                new Durability(Durability.SyncPolicy.WRITE_NO_SYNC, Durability.SyncPolicy.SYNC, Durability.ReplicaAckPolicy.ALL),
                master.getRealMessageStoreDurability());
        assertFalse("Coalescing sync committer is still running", master.isCoalescingSync());
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
        int port = _portHelper.getNextAvailable();
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
        String node2NodeHostPort = "localhost" + ":" + _portHelper.getNextAvailable();
        replicatedEnvironmentFacade.setPermittedNodes(Arrays.asList(replicatedEnvironmentFacade.getHostPort(), node2NodeHostPort));
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
        String node2NodeHostPort = "localhost" + ":" + _portHelper.getNextAvailable();
        replicatedEnvironmentFacade.setPermittedNodes(Arrays.asList(replicatedEnvironmentFacade.getHostPort(), node2NodeHostPort));
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

        String node2NodeHostPort = "localhost" + ":" + _portHelper.getNextAvailable();
        replicatedEnvironmentFacade.setPermittedNodes(Arrays.asList(replicatedEnvironmentFacade.getHostPort(), node2NodeHostPort));
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
        TestStateChangeListener stateChangeListener = new TestStateChangeListener(State.MASTER);
        ReplicatedEnvironmentFacade environmentFacade = addNode(TEST_NODE_NAME, TEST_NODE_HOST_PORT, true, stateChangeListener, new NoopReplicationGroupListener());
        assertTrue("Environment was not created", stateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS));


        String node2Name = TEST_NODE_NAME + "_2";
        String node2NodeHostPort = "localhost:" + _portHelper.getNextAvailable();
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

        int replica1Port = _portHelper.getNextAvailable();
        String node1NodeHostPort = "localhost:" + replica1Port;
        masterEnvironment.setPermittedNodes(Arrays.asList(masterEnvironment.getHostPort(), node1NodeHostPort));
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

        int replica1Port = _portHelper.getNextAvailable();
        String node1NodeHostPort = "localhost:" + replica1Port;
        int replica2Port = _portHelper.getNextAvailable();
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

        int replica1Port = _portHelper.getNextAvailable();
        String node1NodeHostPort = "localhost:" + replica1Port;
        ReplicatedEnvironmentFacade secondNode = createReplica(TEST_NODE_NAME + "_1", node1NodeHostPort, new NoopReplicationGroupListener());
        assertEquals("Unexpected state", ReplicatedEnvironment.State.REPLICA.name(), secondNode.getNodeState());

        int replica2Port = _portHelper.getNextAvailable();
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

        int replica1Port = _portHelper.getNextAvailable();
        String node1NodeHostPort = "localhost:" + replica1Port;
        ReplicatedEnvironmentFacade secondNode = createReplica(TEST_NODE_NAME + "_1", node1NodeHostPort, new NoopReplicationGroupListener());
        assertEquals("Unexpected state", ReplicatedEnvironment.State.REPLICA.name(), secondNode.getNodeState());

        int replica2Port = _portHelper.getNextAvailable();
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
        permittedNodes.add("localhost:" + _portHelper.getNextAvailable());
        firstNode.setPermittedNodes(permittedNodes);

        ReplicatedEnvironmentFacade.ReplicationNodeImpl replicationNode = new ReplicatedEnvironmentFacade.ReplicationNodeImpl(TEST_NODE_NAME, TEST_NODE_HOST_PORT);
        NodeState nodeState = ReplicatedEnvironmentFacade.getRemoteNodeState(TEST_GROUP_NAME, replicationNode, 5000);

        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> settings = objectMapper.readValue(nodeState.getAppState(), Map.class);
        Collection<String> appStatePermittedNodes =  (Collection<String>)settings.get(ReplicatedEnvironmentFacade.PERMITTED_NODE_LIST);
        assertEquals("Unexpected permitted nodes", permittedNodes, new HashSet<String>(appStatePermittedNodes));
    }

    public void testPermittedNodeIsAllowedToConnect() throws Exception
    {
        ReplicatedEnvironmentFacade firstNode = createMaster();

        int replica1Port = _portHelper.getNextAvailable();
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

    public void testIntruderNodeIsDetected() throws Exception
    {
        final CountDownLatch intruderLatch = new CountDownLatch(1);
        ReplicationGroupListener listener = new NoopReplicationGroupListener()
        {
            @Override
            public boolean onIntruderNode(ReplicationNode node)
            {
                intruderLatch.countDown();
                return true;
            }
        };
        ReplicatedEnvironmentFacade firstNode = createMaster(listener);
        int replica1Port = _portHelper.getNextAvailable();
        String node1NodeHostPort = "localhost:" + replica1Port;

        Set<String> permittedNodes = new HashSet<String>();
        permittedNodes.add("localhost:" + TEST_NODE_PORT);

        firstNode.setPermittedNodes(permittedNodes);

        String nodeName = TEST_NODE_NAME + "_1";
        createIntruder(nodeName, node1NodeHostPort);
        assertTrue("Intruder node was not detected", intruderLatch.await(10, TimeUnit.SECONDS));
    }

    public void testNodeRolledback()  throws Exception
    {
        DatabaseConfig createConfig = new DatabaseConfig();
        createConfig.setAllowCreate(true);
        createConfig.setTransactional(true);

        ReplicatedEnvironmentFacade node1 = createMaster();

        String replicaNodeHostPort = "localhost:" + _portHelper.getNextAvailable();

        String replicaName = TEST_NODE_NAME + 1;
        ReplicatedEnvironmentFacade node2 = createReplica(replicaName, replicaNodeHostPort, new NoopReplicationGroupListener());

        node1.setDesignatedPrimary(true);

        Transaction txn = node1.beginTransaction();
        Database db = node1.getEnvironment().openDatabase(txn, "mydb", createConfig);
        txn.commit();

        // Put a record (that will be replicated)
        putRecord(node1, db, 1, "value1");

        node2.close();

        // Put a record (that will be only on node1 as node2 is now offline)
        putRecord(node1, db, 2, "value2");

        db.close();

        // Stop node1
        node1.close();

        // Restart the node2, making it primary so it becomes master
        TestStateChangeListener node2StateChangeListener = new TestStateChangeListener(State.MASTER);
        node2 = addNode(replicaName, replicaNodeHostPort, true, node2StateChangeListener, new NoopReplicationGroupListener());
        boolean awaitForStateChange = node2StateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS);
        assertTrue(replicaName + " did not go into desired state; current actual state is "
                   + node2StateChangeListener.getCurrentActualState(), awaitForStateChange);

        txn = node2.beginTransaction();
        db = node2.getEnvironment().openDatabase(txn, "mydb", DatabaseConfig.DEFAULT);
        txn.commit();

        // Do a transaction on node2. The two environments will have diverged
        putRecord(node2, db, 3, "diverged");

        // Now restart node1 and ensure that it realises it needs to rollback before it can rejoin.
        TestStateChangeListener node1StateChangeListener = new TestStateChangeListener(State.REPLICA);
        final CountDownLatch _replicaRolledback = new CountDownLatch(1);
        node1 = addNode(node1StateChangeListener, new NoopReplicationGroupListener()
        {
            @Override
            public void onNodeRolledback()
            {
                _replicaRolledback.countDown();
            }
        });
        assertTrue("Node 1 did not go into desired state and remained in state " + node1.getNodeState(),
                   node1StateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("Node 1 did not experience rollback within timeout",
                   _replicaRolledback.await(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        // Finally do one more transaction through the master
        putRecord(node2, db, 4, "value4");
        db.close();

        node1.close();
        node2.close();
    }

    public void testReplicaTransactionBeginsImmediately()  throws Exception
    {
        ReplicatedEnvironmentFacade master = createMaster();
        String nodeName2 = TEST_NODE_NAME + "_2";
        String host = "localhost";
        int port = _portHelper.getNextAvailable();
        String node2NodeHostPort = host + ":" + port;

        final ReplicatedEnvironmentFacade replica = createReplica(nodeName2, node2NodeHostPort, new NoopReplicationGroupListener() );

        // close the master
        master.close();

        // try to create a transaction in a separate thread
        // and make sure that transaction is created immediately.
        ExecutorService service =  Executors.newSingleThreadExecutor();
        try
        {

            Future<Transaction> future = service.submit(new Callable<Transaction>(){

                @Override
                public Transaction call() throws Exception
                {
                    return  replica.getEnvironment().beginTransaction(null, null);
                }
            });
            Transaction transaction = future.get(5, TimeUnit.SECONDS);
            assertNotNull("Transaction was not created during expected time", transaction);
            transaction.abort();
        }
        finally
        {
            service.shutdown();
        }
    }

    public void testReplicaWriteExceptionIsConvertedIntoConnectionScopedRuntimeException()  throws Exception
    {
        ReplicatedEnvironmentFacade master = createMaster();
        String nodeName2 = TEST_NODE_NAME + "_2";
        String host = "localhost";
        int port = _portHelper.getNextAvailable();
        String node2NodeHostPort = host + ":" + port;

        final ReplicatedEnvironmentFacade replica = createReplica(nodeName2, node2NodeHostPort, new NoopReplicationGroupListener() );

        // close the master
        master.close();

        try
        {
            replica.openDatabase("test", DatabaseConfig.DEFAULT.setAllowCreate(true) );
            fail("Replica write operation should fail");
        }
        catch(ReplicaWriteException e)
        {
            RuntimeException handledException = master.handleDatabaseException("test", e);
            assertTrue("Unexpected exception", handledException instanceof ConnectionScopedRuntimeException);
        }
    }

    private void putRecord(final ReplicatedEnvironmentFacade master, final Database db, final int keyValue,
                           final String dataValue)
    {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        Transaction txn = master.beginTransaction();
        IntegerBinding.intToEntry(keyValue, key);
        StringBinding.stringToEntry(dataValue, data);

        db.put(txn, key, data);
        txn.commit();
    }


    private void createIntruder(String nodeName, String node1NodeHostPort)
    {
        File environmentPathFile = new File(_storePath, nodeName);
        environmentPathFile.mkdirs();

        ReplicationConfig replicationConfig = new ReplicationConfig(TEST_GROUP_NAME, nodeName, node1NodeHostPort);
        replicationConfig.setHelperHosts(TEST_NODE_HOST_PORT);

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        envConfig.setDurability(TEST_DURABILITY);
        ReplicatedEnvironment intruder = null;
        try
        {
            intruder = new ReplicatedEnvironment(environmentPathFile, replicationConfig, envConfig);
        }
        finally
        {
            if (intruder != null)
            {
                intruder.close();
            }
        }
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
        when(node.getHelperNodeName()).thenReturn(TEST_NODE_NAME);

        when(node.getFacadeParameter(eq(ReplicatedEnvironmentFacade.MASTER_TRANSFER_TIMEOUT_PROPERTY_NAME), anyInt())).thenReturn(60000);
        when(node.getFacadeParameter(eq(ReplicatedEnvironmentFacade.DB_PING_SOCKET_TIMEOUT_PROPERTY_NAME), anyInt())).thenReturn(10000);
        when(node.getFacadeParameter(eq(ReplicatedEnvironmentFacade.REMOTE_NODE_MONITOR_INTERVAL_PROPERTY_NAME), anyInt())).thenReturn(1000);
        when(node.getFacadeParameter(eq(ReplicatedEnvironmentFacade.ENVIRONMENT_RESTART_RETRY_LIMIT_PROPERTY_NAME), anyInt())).thenReturn(3);
        when(node.getFacadeParameter(eq(ReplicatedEnvironmentFacade.EXECUTOR_SHUTDOWN_TIMEOUT_PROPERTY_NAME), anyInt())).thenReturn(10000);

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
        public boolean onIntruderNode(ReplicationNode node)
        {
            LOGGER.warn("Intruder node " + node);
            return true;
        }

        @Override
        public void onNoMajority()
        {
        }

        @Override
        public void onNodeRolledback()
        {
        }

        @Override
        public void onException(Exception e)
        {
        }

    }
}
