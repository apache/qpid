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

import static org.apache.qpid.server.model.ReplicationNode.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.replication.ReplicationGroupListener;
import org.apache.qpid.server.store.berkeleydb.EnvironmentFacade;
import org.apache.qpid.server.store.berkeleydb.replication.RemoteReplicationNode;
import org.apache.qpid.server.store.berkeleydb.replication.RemoteReplicationNodeFactory;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacade;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacadeFactory;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.rep.InsufficientReplicasException;
import com.sleepycat.je.rep.ReplicatedEnvironment.State;
import com.sleepycat.je.rep.ReplicationConfig;
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
    private static final String TEST_DURABILITY = Durability.parse("NO_SYNC,NO_SYNC,SIMPLE_MAJORITY").toString();
    private static final boolean TEST_DESIGNATED_PRIMARY = false;
    private static final boolean TEST_COALESCING_SYNC = true;
    private static final int TEST_PRIORITY = 10;
    private static final int TEST_ELECTABLE_GROUP_OVERRIDE = 0;

    private File _storePath;
    private final Map<String, ReplicatedEnvironmentFacade> _nodes = new HashMap<String, ReplicatedEnvironmentFacade>();
    private VirtualHost _virtualHost = mock(VirtualHost.class);

    private RemoteReplicationNodeFactory _remoteReplicationNodeFactory = new ReplicatedEnvironmentFacadeFactory.RemoteReplicationNodeFactoryImpl(_virtualHost);

    public void setUp() throws Exception
    {
        super.setUp();

        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        when(taskExecutor.isTaskExecutorThread()).thenReturn(true);
        when(_virtualHost.getTaskExecutor()).thenReturn(taskExecutor);

        _storePath = TestFileUtils.createTestDirectory("bdb", true);

        when(_virtualHost.getAttribute(VirtualHost.REMOTE_REPLICATION_NODE_MONITOR_INTERVAL)).thenReturn(100L);
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

    public void testOpenDatabases() throws Exception
    {
        EnvironmentFacade ef = createMaster();
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        ef.openDatabases(dbConfig, "test1", "test2");
        Database test1 = ef.getOpenDatabase("test1");
        Database test2 = ef.getOpenDatabase("test2");

        assertEquals("Unexpected name for open database test1", "test1" , test1.getDatabaseName());
        assertEquals("Unexpected name for open database test2", "test2" , test2.getDatabaseName());
    }

    public void testGetOpenDatabaseForNonExistingDatabase() throws Exception
    {
        EnvironmentFacade ef = createMaster();
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        ef.openDatabases(dbConfig, "test1");
        Database test1 = ef.getOpenDatabase("test1");
        assertEquals("Unexpected name for open database test1", "test1" , test1.getDatabaseName());
        try
        {
            ef.getOpenDatabase("test2");
            fail("An exception should be thrown for the non existing database");
        }
        catch(IllegalArgumentException e)
        {
            assertEquals("Unexpected exception message", "Database with name 'test2' has never been requested to be opened", e.getMessage());
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

    public void testGetDurability() throws Exception
    {
        assertEquals("Unexpected durability", TEST_DURABILITY.toString(), createMaster().getDurability());
    }

    public void testIsCoalescingSync() throws Exception
    {
        assertEquals("Unexpected coalescing sync", TEST_COALESCING_SYNC, createMaster().isCoalescingSync());
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

        addReplica(nodeName2, node2NodeHostPort, listener);

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
        ReplicatedEnvironmentFacade replicatedEnvironmentFacade = addNode(State.MASTER, stateChangeListener, listener);
        assertTrue("Master was not started", stateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        assertEquals("Unexpected number of nodes at start of test", 1, replicatedEnvironmentFacade.getNumberOfElectableGroupMembers());

        String node2Name = TEST_NODE_NAME + "_2";
        String node2NodeHostPort = "localhost" + ":" + getNextAvailable(TEST_NODE_PORT + 1);
        addReplica(node2Name, node2NodeHostPort);

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
        ReplicatedEnvironmentFacade replicatedEnvironmentFacade = addNode(State.MASTER, stateChangeListener, listener);
        assertTrue("Master was not started", stateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        String node2Name = TEST_NODE_NAME + "_2";
        String node2NodeHostPort = "localhost" + ":" + getNextAvailable(TEST_NODE_PORT + 1);
        addReplica(node2Name, node2NodeHostPort);

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

        final CountDownLatch nodeAddedLatch = new CountDownLatch(1);
        final AtomicReference<ReplicationNode> nodeRef = new AtomicReference<ReplicationNode>();
        ReplicationGroupListener listener = new NoopReplicationGroupListener()
        {
            @Override
            public void onReplicationNodeAddedToGroup(ReplicationNode node)
            {
                nodeRef.set(node);
                nodeAddedLatch.countDown();
            }
        };

        TestStateChangeListener stateChangeListener = new TestStateChangeListener(State.MASTER);
        ReplicatedEnvironmentFacade replicatedEnvironmentFacade = addNode(State.MASTER, stateChangeListener, listener);
        assertTrue("Master was not started", stateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        String node2Name = TEST_NODE_NAME + "_2";
        String node2NodeHostPort = "localhost" + ":" + getNextAvailable(TEST_NODE_PORT + 1);
        addReplica(node2Name, node2NodeHostPort);

        assertEquals("Unexpected number of nodes at start of test", 2, replicatedEnvironmentFacade.getNumberOfElectableGroupMembers());

        assertTrue("Node add not fired within timeout", nodeAddedLatch.await(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        RemoteReplicationNode remoteNode = (RemoteReplicationNode)nodeRef.get();
        assertEquals("Unexpcted node name", node2Name, remoteNode.getName());

        // Need to poll to await the remote node updating itself
        long timeout = System.currentTimeMillis() + 5000;
        while(!State.REPLICA.name().equals(remoteNode.getAttribute(ReplicationNode.ROLE)) && System.currentTimeMillis() < timeout)
        {
            Thread.sleep(200);
        }

        assertEquals("Unexpcted node role (after waiting)", State.REPLICA.name(), remoteNode.getAttribute(ReplicationNode.ROLE));
        assertNotNull("Replica node " + ReplicationNode.JOIN_TIME + " attribute is not set", remoteNode.getAttribute(ReplicationNode.JOIN_TIME));
        assertNotNull("Replica node " + ReplicationNode.LAST_KNOWN_REPLICATION_TRANSACTION_ID + " attribute is not set", remoteNode.getAttribute(ReplicationNode.LAST_KNOWN_REPLICATION_TRANSACTION_ID));
    }

    public void testRemoveNodeFromGroup() throws Exception
    {
        ReplicatedEnvironmentFacade environmentFacade = createMaster();

        String node2Name = TEST_NODE_NAME + "_2";
        String node2NodeHostPort = "localhost:" + getNextAvailable(TEST_NODE_PORT + 1);
        ReplicatedEnvironmentFacade ref2 = addReplica(node2Name, node2NodeHostPort);

        assertEquals("Unexpected group members count", 2, environmentFacade.getNumberOfElectableGroupMembers());
        ref2.close();

        environmentFacade.removeNodeFromGroup(node2Name);
        assertEquals("Unexpected group members count", 1, environmentFacade.getNumberOfElectableGroupMembers());
    }

    public void testEnvironmentRestartOnInsufficientReplicas() throws Exception
    {
        long startTime = System.currentTimeMillis();

        ReplicatedEnvironmentFacade master = createMaster();

        int replica1Port = getNextAvailable(TEST_NODE_PORT + 1);
        String replica1NodeName = TEST_NODE_NAME + "_1";
        String replica1NodeHostPort = "localhost:" + replica1Port;
        ReplicatedEnvironmentFacade replica1 = addReplica(replica1NodeName, replica1NodeHostPort);

        int replica2Port = getNextAvailable(replica1Port + 1);
        String replica2NodeName = TEST_NODE_NAME + "_2";
        String replica2NodeHostPort = "localhost:" + replica2Port;
        ReplicatedEnvironmentFacade replica2 = addReplica(replica2NodeName, replica2NodeHostPort);
        
        long setUpTime = System.currentTimeMillis();
        LOGGER.debug("XXX Start Up Time " + (setUpTime - startTime));
        String databaseName = "test";

        DatabaseConfig dbConfig = createDatabase(master, databaseName);

        // close replicas
        replica1.close();
        replica2.close();

        long closeTime = System.currentTimeMillis();
        LOGGER.debug("XXX Env close  Time " + (closeTime - setUpTime));
        Environment e = master.getEnvironment();
        Database db = master.getOpenDatabase(databaseName);
        try
        {
            master.openDatabases(dbConfig, "test2");
            fail("Opening of new database without quorum should fail");
        }
        catch(InsufficientReplicasException ex)
        {
            master.handleDatabaseException(null, ex);
        }
        long openDatabaseTime = System.currentTimeMillis();
        LOGGER.debug("XXX Open db Time " + (openDatabaseTime - closeTime ));

        replica1 = addReplica(replica1NodeName, replica1NodeHostPort);
        replica2 = addReplica(replica2NodeName, replica2NodeHostPort);

        long reopenTime = System.currentTimeMillis();
        LOGGER.debug("XXX Restart Time " + (reopenTime - openDatabaseTime ));
        // Need to poll to await the remote node updating itself
        long timeout = System.currentTimeMillis() + 5000;
        while(!(State.REPLICA.name().equals(master.getNodeState()) || State.MASTER.name().equals(master.getNodeState()) ) && System.currentTimeMillis() < timeout)
        {
            Thread.sleep(200);
        }
        long recoverTime = System.currentTimeMillis();
        LOGGER.debug("XXX Recover Time " + (recoverTime - reopenTime));
        assertTrue("The node could not rejoin the cluster. State is " + master.getNodeState(),
                State.REPLICA.name().equals(master.getNodeState()) || State.MASTER.name().equals(master.getNodeState()) );

        Environment e2 = master.getEnvironment();
        assertNotSame("Environment has not been restarted", e2, e);

        Database db1 = master.getOpenDatabase(databaseName);
        assertNotSame("Database should be the re-created", db1, db);
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

        addNode(State.MASTER, stateChangeListener, new NoopReplicationGroupListener());
        assertTrue("Master was not started", masterLatch.await(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        int replica1Port = getNextAvailable(TEST_NODE_PORT + 1);
        String node1NodeHostPort = "localhost:" + replica1Port;
        int replica2Port = getNextAvailable(replica1Port + 1);
        String node2NodeHostPort = "localhost:" + replica2Port;

        ReplicatedEnvironmentFacade replica1 = addReplica(TEST_NODE_NAME + "_1", node1NodeHostPort);
        ReplicatedEnvironmentFacade replica2 = addReplica(TEST_NODE_NAME + "_2", node2NodeHostPort);

        // close replicas
        replica1.close();
        replica2.close();

        assertTrue("Environment should be recreated and go into unknown state",
                unknownLatch.await(WAIT_STATE_CHANGE_TIMEOUT, TimeUnit.SECONDS));

        assertEquals("Node made master an unexpected number of times", 1, masterStateChangeCount.get());
        assertEquals("Node made unknown an unexpected number of times", 1, unknownStateChangeCount.get());
    }

    public void testEnvironmentFacadeDetectsRemovalOfRemoteNode() throws Exception
    {
        final CountDownLatch nodeRemovedLatch = new CountDownLatch(1);
        final CountDownLatch nodeAddedLatch = new CountDownLatch(1);
        final AtomicReference<ReplicationNode> addedNodeRef = new AtomicReference<ReplicationNode>();
        final AtomicReference<ReplicationNode> removedNodeRef = new AtomicReference<ReplicationNode>();
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
        };

        TestStateChangeListener stateChangeListener = new TestStateChangeListener(State.MASTER);
        final ReplicatedEnvironmentFacade masterEnvironment = addNode(State.MASTER, stateChangeListener, listener);
        assertTrue("Master was not started", stateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        masterEnvironment.setDesignatedPrimary(true);

        int replica1Port = getNextAvailable(TEST_NODE_PORT + 1);
        String node1NodeHostPort = "localhost:" + replica1Port;

        String replicaName = TEST_NODE_NAME + "_1";
        addReplica(replicaName, node1NodeHostPort);

        assertTrue("Node should be added", nodeAddedLatch.await(WAIT_STATE_CHANGE_TIMEOUT, TimeUnit.SECONDS));

        ReplicationNode node = addedNodeRef.get();
        assertEquals("Unexpected node name", replicaName, node.getName());

        // Need to poll to await the remote node updating itself
        long timeout = System.currentTimeMillis() + 5000;
        while(!State.REPLICA.name().equals(node.getAttribute(ReplicationNode.ROLE)) && System.currentTimeMillis() < timeout)
        {
            Thread.sleep(200);
        }
        assertEquals("Unexpected node role", State.REPLICA.name(), node.getAttribute(ReplicationNode.ROLE));

        // removing remote node
        node.setDesiredState(node.getActualState(), org.apache.qpid.server.model.State.DELETED);

        assertTrue("Node deleting is undetected by the environment facade", nodeRemovedLatch.await(WAIT_STATE_CHANGE_TIMEOUT, TimeUnit.SECONDS));
        assertEquals("Unexpected node is deleted", node, removedNodeRef.get());

        //TODO: need a way to shut down the remote environment when the corresponding remote node is deleted.
        // It is unclear whether it is possible
    }

    public void testCloseStateTransitions() throws Exception
    {
        ReplicatedEnvironmentFacade replicatedEnvironmentFacade = createMaster();

        assertEquals("Unexpected state " + replicatedEnvironmentFacade.getFacadeState(), ReplicatedEnvironmentFacade.State.OPEN, replicatedEnvironmentFacade.getFacadeState());
        replicatedEnvironmentFacade.close();
        assertEquals("Unexpected state " + replicatedEnvironmentFacade.getFacadeState(), ReplicatedEnvironmentFacade.State.CLOSED, replicatedEnvironmentFacade.getFacadeState());
    }

    private ReplicatedEnvironmentFacade createMaster() throws Exception
    {
        TestStateChangeListener stateChangeListener = new TestStateChangeListener(State.MASTER);
        ReplicatedEnvironmentFacade env = addNode(State.MASTER, stateChangeListener, new NoopReplicationGroupListener());
        assertTrue("Environment was not created", stateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS));
        return env;
    }

    private ReplicatedEnvironmentFacade addReplica(String nodeName, String nodeHostPort) throws Exception
    {
        return addReplica(nodeName, nodeHostPort, new NoopReplicationGroupListener());
    }

    private ReplicatedEnvironmentFacade addReplica(String nodeName, String nodeHostPort, ReplicationGroupListener replicationGroupListener)
            throws Exception
    {
        TestStateChangeListener testStateChangeListener = new TestStateChangeListener(State.REPLICA);
        ReplicatedEnvironmentFacade replicaEnvironmentFacade = addNode(nodeName, nodeHostPort, TEST_DESIGNATED_PRIMARY, State.REPLICA, testStateChangeListener, replicationGroupListener);
        assertTrue("Replica " + nodeName + " was not started", testStateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS));
        return replicaEnvironmentFacade;
    }

    private ReplicatedEnvironmentFacade addNode(String nodeName, String nodeHostPort, boolean designatedPrimary,
            State desiredState, StateChangeListener stateChangeListener, ReplicationGroupListener replicationGroupListener)
    {
        LocalReplicationNode node = createReplicationNodeMock(nodeName, nodeHostPort, designatedPrimary);
        ReplicatedEnvironmentFacade ref = new ReplicatedEnvironmentFacade(node, _remoteReplicationNodeFactory);
        ref.setReplicationGroupListener(replicationGroupListener);
        ref.setStateChangeListener(stateChangeListener);
        _nodes.put(nodeName, ref);
        return ref;
    }

    private ReplicatedEnvironmentFacade addNode(State desiredState, StateChangeListener stateChangeListener, ReplicationGroupListener groupChangeListener)
    {
        return addNode(TEST_NODE_NAME, TEST_NODE_HOST_PORT, TEST_DESIGNATED_PRIMARY, desiredState, stateChangeListener, groupChangeListener);
    }

    private DatabaseConfig createDatabase(ReplicatedEnvironmentFacade environmentFacade, String databaseName) throws AMQStoreException
    {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        environmentFacade.openDatabases(dbConfig,  databaseName);
        return dbConfig;
    }

    private LocalReplicationNode createReplicationNodeMock(String nodeName, String nodeHostPort, boolean designatedPrimary)
    {
        LocalReplicationNode node =  mock(LocalReplicationNode.class);
        when(node.getAttribute(NAME)).thenReturn(nodeName);
        when(node.getName()).thenReturn(nodeName);
        when(node.getAttribute(HOST_PORT)).thenReturn(nodeHostPort);
        when(node.getAttribute(DESIGNATED_PRIMARY)).thenReturn(designatedPrimary);
        when(node.getAttribute(QUORUM_OVERRIDE)).thenReturn(TEST_ELECTABLE_GROUP_OVERRIDE);
        when(node.getAttribute(PRIORITY)).thenReturn(TEST_PRIORITY);
        when(node.getAttribute(GROUP_NAME)).thenReturn(TEST_GROUP_NAME);
        when(node.getAttribute(HELPER_HOST_PORT)).thenReturn(TEST_NODE_HELPER_HOST_PORT);
        when(node.getAttribute(DURABILITY)).thenReturn(TEST_DURABILITY);
        when(node.getAttribute(COALESCING_SYNC)).thenReturn(TEST_COALESCING_SYNC);

        // TODO REF contract with LRN is too complicated.
        when(node.getActualAttribute(HOST_PORT)).thenReturn(nodeHostPort);
        when(node.getActualAttribute(DESIGNATED_PRIMARY)).thenReturn(designatedPrimary);
        when(node.getActualAttribute(QUORUM_OVERRIDE)).thenReturn(TEST_ELECTABLE_GROUP_OVERRIDE);
        when(node.getActualAttribute(PRIORITY)).thenReturn(TEST_PRIORITY);
        when(node.getActualAttribute(GROUP_NAME)).thenReturn(TEST_GROUP_NAME);
        when(node.getActualAttribute(HELPER_HOST_PORT)).thenReturn(TEST_NODE_HELPER_HOST_PORT);
        when(node.getActualAttribute(DURABILITY)).thenReturn(TEST_DURABILITY);
        when(node.getActualAttribute(COALESCING_SYNC)).thenReturn(TEST_COALESCING_SYNC);

        Map<String, String> repConfig = new HashMap<String, String>();
        repConfig.put(ReplicationConfig.REPLICA_ACK_TIMEOUT, "2 s");
        repConfig.put(ReplicationConfig.INSUFFICIENT_REPLICAS_TIMEOUT, "2 s");
        when(node.getActualAttribute(REPLICATION_PARAMETERS)).thenReturn(repConfig);

        when(node.getAttribute(STORE_PATH)).thenReturn(new File(_storePath, nodeName).getAbsolutePath());
        return node;
    }
}
