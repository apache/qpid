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
package org.apache.qpid.server.store.berkeleydb;

import static org.apache.qpid.server.model.ReplicationNode.COALESCING_SYNC;
import static org.apache.qpid.server.model.ReplicationNode.DESIGNATED_PRIMARY;
import static org.apache.qpid.server.model.ReplicationNode.DURABILITY;
import static org.apache.qpid.server.model.ReplicationNode.GROUP_NAME;
import static org.apache.qpid.server.model.ReplicationNode.HELPER_HOST_PORT;
import static org.apache.qpid.server.model.ReplicationNode.HOST_PORT;
import static org.apache.qpid.server.model.ReplicationNode.NAME;
import static org.apache.qpid.server.model.ReplicationNode.REPLICATION_PARAMETERS;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.replication.ReplicationGroupListener;
import org.apache.qpid.server.store.berkeleydb.replication.RemoteReplicationNode;
import org.apache.qpid.server.store.berkeleydb.replication.RemoteReplicationNodeFactory;
import org.apache.qpid.test.utils.QpidTestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.rep.InsufficientReplicasException;
import com.sleepycat.je.rep.ReplicatedEnvironment.State;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;

public class ReplicatedEnvironmentFacadeTest extends EnvironmentFacadeTestCase
{

    private static class NoopReplicationGroupListener implements ReplicationGroupListener
    {
        @Override
        public void onReplicationNodeRecovered(ReplicationNode node)
        {
        }

        @Override
        public void onReplicationNodeAddedToGroup(ReplicationNode node)
        {
        }

        @Override
        public void onReplicationNodeRemovedFromGroup(ReplicationNode node)
        {
        }
    }

    private static final int TEST_NODE_PORT = new QpidTestCase().findFreePort();
    private static final TimeUnit WAIT_STATE_CHANGE_TIME_UNIT = TimeUnit.SECONDS;
    private static final int WAIT_STATE_CHANGE_TIMEOUT = 30;
    private static final String TEST_GROUP_NAME = "testGroupName";
    private static final String TEST_NODE_NAME = "testNodeName";
    private static final String TEST_NODE_HOST_PORT = "localhost:" + TEST_NODE_PORT;
    private static final String TEST_NODE_HELPER_HOST_PORT = TEST_NODE_HOST_PORT;
    private static final String TEST_DURABILITY = Durability.parse("NO_SYNC,NO_SYNC,SIMPLE_MAJORITY").toString();
    private static final boolean TEST_DESIGNATED_PRIMARY = true;
    private static final boolean TEST_COALESCING_SYNC = true;
    private final Map<String, ReplicatedEnvironmentFacade> _nodes = new HashMap<String, ReplicatedEnvironmentFacade>();
    private VirtualHost _virtualHost = mock(VirtualHost.class);
    private RemoteReplicationNodeFactory _remoteReplicationNodeFactory = new ReplicatedEnvironmentFacadeFactory.RemoteReplicationNodeFactoryImpl(_virtualHost);

    public void setUp() throws Exception
    {
        super.setUp();

        when(_virtualHost.getAttribute(VirtualHost.REMOTE_REPLICATION_NODE_MONITOR_INTERVAL)).thenReturn(100L);
        when(_virtualHost.getAttribute(VirtualHost.REMOTE_REPLICATION_NODE_MONITOR_TIMEOUT)).thenReturn(100L);
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
            super.tearDown();
        }
    }

    public void testGetName()
    {
        assertEquals("Unexpected name", getName(), getEnvironmentFacade().getName());
    }

    public void testGetGroupName()
    {
        assertEquals("Unexpected group name", TEST_GROUP_NAME, getEnvironmentFacade().getGroupName());
    }

    public void testGetNodeName()
    {
        assertEquals("Unexpected group name", TEST_NODE_NAME, getEnvironmentFacade().getNodeName());
    }

    public void testGetNodeHostPort()
    {
        assertEquals("Unexpected node host port", TEST_NODE_HOST_PORT, getEnvironmentFacade().getHostPort());
    }

    public void testGetHelperHostPort()
    {
        assertEquals("Unexpected node helper host port", TEST_NODE_HELPER_HOST_PORT, getEnvironmentFacade().getHelperHostPort());
    }

    public void testGetDurability()
    {
        assertEquals("Unexpected durability", TEST_DURABILITY.toString(), getEnvironmentFacade().getDurability());
    }

    public void testIsCoalescingSync()
    {
        assertEquals("Unexpected coalescing sync", TEST_COALESCING_SYNC, getEnvironmentFacade().isCoalescingSync());
    }

    public void testGetNodeState()
    {
        assertEquals("Unexpected state", State.MASTER.name(), getEnvironmentFacade().getNodeState());
    }

    public void testIsDesignatedPrimary()
    {
        assertEquals("Unexpected designated primary", TEST_DESIGNATED_PRIMARY, getEnvironmentFacade().isDesignatedPrimary());
    }

    public void testGetGroupMembers()
    {
        List<Map<String, String>> groupMembers = getEnvironmentFacade().getGroupMembers();
        Map<String, String> expectedMember = new HashMap<String, String>();
        expectedMember.put(ReplicatedEnvironmentFacade.GRP_MEM_COL_NODE_NAME, TEST_NODE_NAME);
        expectedMember.put(ReplicatedEnvironmentFacade.GRP_MEM_COL_NODE_HOST_PORT, TEST_NODE_HOST_PORT);
        Set<Map<String, String>> expectedGroupMembers = Collections.singleton(expectedMember);
        assertEquals("Unexpected group members", expectedGroupMembers, new HashSet<Map<String, String>>(groupMembers));
    }

    public void testReplicationGroupListenerHearsAboutExistingRemoteReplicationNodes() throws Exception
    {
        getEnvironmentFacade();
        String nodeName2 = TEST_NODE_NAME + "_2";
        String host = "localhost";
        int port = getNextAvailable(TEST_NODE_PORT + 1);
        String node2NodeHostPort = host + ":" + port;
        ReplicatedEnvironmentFacade replicatedEnvironmentFacade2 = joinReplica(nodeName2, node2NodeHostPort);

        List<Map<String, String>> groupMembers = replicatedEnvironmentFacade2.getGroupMembers();
        assertEquals("Unexpected number of nodes", 2, groupMembers.size());

        ReplicationGroupListener listener = mock(ReplicationGroupListener.class);
        replicatedEnvironmentFacade2.setReplicationGroupListener(listener);
        verify(listener).onReplicationNodeRecovered(any(RemoteReplicationNode.class));
    }

    public void testReplicationGroupListenerHearsNodeAdded() throws Exception
    {
        ReplicatedEnvironmentFacade replicatedEnvironmentFacade = getEnvironmentFacade();

        List<Map<String, String>> initialGroupMembers = replicatedEnvironmentFacade.getGroupMembers();
        assertEquals("Unexpected number of nodes at start of test", 1, initialGroupMembers.size());

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger invocationCount = new AtomicInteger();
        ReplicationGroupListener listener = new NoopReplicationGroupListener()
        {
            @Override
            public void onReplicationNodeAddedToGroup(ReplicationNode node)
            {
                invocationCount.getAndIncrement();
                latch.countDown();
            }
        };
        replicatedEnvironmentFacade.setReplicationGroupListener(listener);

        String node2Name = TEST_NODE_NAME + "_2";
        String node2NodeHostPort = "localhost" + ":" + getNextAvailable(TEST_NODE_PORT + 1);
        joinReplica(node2Name, node2NodeHostPort);

        assertTrue("Listener not fired within timeout", latch.await(5, TimeUnit.SECONDS));

        List<Map<String, String>> groupMembers = replicatedEnvironmentFacade.getGroupMembers();
        assertEquals("Unexpected number of nodes", 2, groupMembers.size());

        assertEquals("Unexpected number of listener invocations", 1, invocationCount.get());
    }

    public void testReplicationGroupListenerHearsNodeRemoved() throws Exception
    {
        ReplicatedEnvironmentFacade replicatedEnvironmentFacade = getEnvironmentFacade();
        String node2Name = TEST_NODE_NAME + "_2";
        String node2NodeHostPort = "localhost" + ":" + getNextAvailable(TEST_NODE_PORT + 1);
        joinReplica(node2Name, node2NodeHostPort);

        List<Map<String, String>> initialGroupMembers = replicatedEnvironmentFacade.getGroupMembers();
        assertEquals("Unexpected number of nodes at start of test", 2, initialGroupMembers.size());

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

        replicatedEnvironmentFacade.setReplicationGroupListener(listener);

        // Need to await the listener hearing the addition of the node to the model.
        assertTrue("Node add not fired within timeout", nodeAddedLatch.await(5, TimeUnit.SECONDS));

        // Now remove the node and ensure we hear the event
        replicatedEnvironmentFacade.removeNodeFromGroup(node2Name);

        assertTrue("Node delete not fired within timeout", nodeDeletedLatch.await(5, TimeUnit.SECONDS));

        List<Map<String, String>> groupMembers = replicatedEnvironmentFacade.getGroupMembers();
        assertEquals("Unexpected number of nodes after node removal", 1, groupMembers.size());

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

        ReplicatedEnvironmentFacade replicatedEnvironmentFacade = getEnvironmentFacade();
        replicatedEnvironmentFacade.setReplicationGroupListener(listener);

        String node2Name = TEST_NODE_NAME + "_2";
        String node2NodeHostPort = "localhost" + ":" + getNextAvailable(TEST_NODE_PORT + 1);
        joinReplica(node2Name, node2NodeHostPort);

        List<Map<String, String>> initialGroupMembers = replicatedEnvironmentFacade.getGroupMembers();
        assertEquals("Unexpected number of nodes at start of test", 2, initialGroupMembers.size());

        assertTrue("Node add not fired within timeout", nodeAddedLatch.await(5, TimeUnit.SECONDS));

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
        ReplicatedEnvironmentFacade environmentFacade = getEnvironmentFacade();
        String nodeName = TEST_NODE_NAME + "_2";
        ReplicatedEnvironmentFacade ref2 = joinReplica(nodeName, "localhost:" + getNextAvailable(TEST_NODE_PORT + 1));
        List<Map<String, String>> groupMembers = environmentFacade.getGroupMembers();
        assertEquals("Unexpected group members count", 2, groupMembers.size());
        ref2.close();

        environmentFacade.removeNodeFromGroup(nodeName);
        groupMembers = environmentFacade.getGroupMembers();
        assertEquals("Unexpected group members count", 1, groupMembers.size());
    }

    public void testSetDesignatedPrimary() throws AMQStoreException
    {
        ReplicatedEnvironmentFacade environmentFacade = getEnvironmentFacade();
        environmentFacade.setDesignatedPrimary(false);
        assertFalse("Unexpected designated primary", environmentFacade.isDesignatedPrimary());
    }

    public void testGetNodePriority()
    {
        assertEquals("Unexpected node priority", 1, getEnvironmentFacade().getPriority());
    }

    public void testGetElectableGroupSizeOverride()
    {
        assertEquals("Unexpected Electable Group Size Override", 0, getEnvironmentFacade().getElectableGroupSizeOverride());
    }

    public void testEnvironmentRestartOnInsufficientReplicas() throws Exception
    {
        ReplicatedEnvironmentFacade[] nodes = startClusterSequentially(3);
        ReplicatedEnvironmentFacade environmentFacade = nodes[0];

        String databaseName = "test";
        DatabaseConfig dbConfig = createDatabase(environmentFacade, databaseName);

        // close replicas
        nodes[1].close();
        nodes[2].close();

        final CountDownLatch nodeAwaitLatch = new CountDownLatch(1);
        Environment e = environmentFacade.getEnvironment();
        Database db = environmentFacade.getOpenDatabase(databaseName);
        try
        {
            environmentFacade.openDatabases(new String[] { "test2" }, dbConfig);
            fail("Opening of new database without quorum should fail");
        }
        catch(InsufficientReplicasException ex)
        {
            environmentFacade.handleDatabaseException(null, ex);
        }

        // restore quorum
        nodes[1] = joinReplica(TEST_NODE_NAME + "_1", nodes[1].getHostPort());
        nodes[2] = joinReplica(TEST_NODE_NAME + "_2", nodes[2].getHostPort());

        environmentFacade.setStateChangeListener(new StateChangeListener()
        {
            @Override
            public void stateChange(StateChangeEvent stateChangeEvent) throws RuntimeException
            {
                if (stateChangeEvent.getState() == State.MASTER || stateChangeEvent.getState() == State.REPLICA)
                {
                    nodeAwaitLatch.countDown();
                }
            }
        });

        assertTrue("The node could not rejoin the cluster",
                nodeAwaitLatch.await(WAIT_STATE_CHANGE_TIMEOUT, WAIT_STATE_CHANGE_TIME_UNIT));

        Environment e2 = environmentFacade.getEnvironment();
        assertNotSame("Environment has not been restarted", e2, e);

        Database db1 = environmentFacade.getOpenDatabase(databaseName);
        assertNotSame("Database should be the re-created", db1, db);
    }

    public void testEnvironmentIsRestartOnlyOnceOnInsufficientReplicas() throws Exception
    {
        ReplicatedEnvironmentFacade[] nodes = startClusterSequentially(3);
        final ReplicatedEnvironmentFacade environmentFacade = nodes[0];

        int numberOfThreads = 100;

        // restart counter
        final AtomicInteger numberOfTimesElected = new AtomicInteger();
        environmentFacade.setStateChangeListener(new StateChangeListener()
        {
            @Override
            public void stateChange(StateChangeEvent stateChangeEvent) throws RuntimeException
            {
                if (stateChangeEvent.getState() == State.MASTER)
                {
                    numberOfTimesElected.incrementAndGet();
                }
            }
        });

        String databaseName = "test";
        createDatabase(environmentFacade, databaseName);
        final CountDownLatch latch = new CountDownLatch(numberOfThreads);

        final Database db = environmentFacade.getOpenDatabase(databaseName);

        // close replicas
        nodes[1].close();
        nodes[2].close();

        // perform transactions in separate threads in order to provoke InsufficientReplicasException
        ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
        try
        {
            List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
            for (int i = 0; i < numberOfThreads; i++)
            {
                final int index = i;
                tasks.add(new Callable<Void>(){

                    @Override
                    public Void call() throws Exception
                    {
                        try
                        {
                            Transaction tx = environmentFacade.getEnvironment().beginTransaction(null, null);
                            DatabaseEntry key = new DatabaseEntry();
                            DatabaseEntry data = new DatabaseEntry();
                            IntegerBinding.intToEntry(index, key);
                            IntegerBinding.intToEntry(index, data);
                            db.put(tx, key, data);
                            tx.commit();
                        }
                        catch(DatabaseException e)
                        {
                            _environmentFacade.handleDatabaseException("Exception", e);
                        }
                        finally
                        {
                            latch.countDown();
                        }
                        return null;
                    }});
            }
            service.invokeAll(tasks);
            assertTrue("Not all tasks have been executed",
                    latch.await(WAIT_STATE_CHANGE_TIMEOUT, WAIT_STATE_CHANGE_TIME_UNIT));
        }
        finally
        {
            service.shutdown();
        }

        // restore quorum
        nodes[1] = joinReplica(TEST_NODE_NAME + "_1", nodes[1].getHostPort());
        nodes[2] = joinReplica(TEST_NODE_NAME + "_2", nodes[2].getHostPort());

        long start = System.currentTimeMillis();
        while(environmentFacade.getFacadeState() != ReplicatedEnvironmentFacade.State.OPEN && System.currentTimeMillis() - start < 10000l)
        {
            Thread.sleep(1000l);
        }
        assertEquals("EnvironmentFacade should be in open state", ReplicatedEnvironmentFacade.State.OPEN, environmentFacade.getFacadeState());

        // it should be elected twice: once on first start-up and second time after environment restart
        assertEquals("Elected master unexpected number of times", 2, numberOfTimesElected.get());
    }

    public void testFacadeStateTransitions() throws InterruptedException
    {
        String nodeName = "node1";
        final String nodePath = createNodeWorkingFolder(nodeName);
        ReplicatedEnvironmentFacade ref = null;
        try
        {
            ref = createReplicatedEnvironmentFacade(nodePath, nodeName, TEST_NODE_HOST_PORT, false);
            assertEquals("Unexpected state " + ref.getFacadeState(), ReplicatedEnvironmentFacade.State.OPENING, ref.getFacadeState());

            final CountDownLatch nodeAwaitLatch = new CountDownLatch(1);
            ref.setStateChangeListener(new StateChangeListener()
            {
                @Override
                public void stateChange(StateChangeEvent stateChangeEvent) throws RuntimeException
                {
                    if (stateChangeEvent.getState() == State.MASTER)
                    {
                        nodeAwaitLatch.countDown();
                    }
                }
            });
            assertTrue("Node did not join the cluster", nodeAwaitLatch.await(WAIT_STATE_CHANGE_TIMEOUT, WAIT_STATE_CHANGE_TIME_UNIT));
            assertEquals("Unexpected state " + ref.getFacadeState(), ReplicatedEnvironmentFacade.State.OPEN, ref.getFacadeState());
            ref.close();
            assertEquals("Unexpected state " + ref.getFacadeState(), ReplicatedEnvironmentFacade.State.CLOSED, ref.getFacadeState());
        }
        finally
        {
            if (ref != null)
            {
                ref.close();
            }
        }
    }

    @Override
    EnvironmentFacade createEnvironmentFacade()
    {
        try
        {
            return startNode(TEST_NODE_NAME, TEST_NODE_HOST_PORT, true, State.MASTER);
        }
        catch (InterruptedException e)
        {
            Thread.interrupted();
            throw new RuntimeException(e);
        }
    }

    @Override
    ReplicatedEnvironmentFacade getEnvironmentFacade()
    {
        return (ReplicatedEnvironmentFacade) super.getEnvironmentFacade();
    }

    private ReplicatedEnvironmentFacade joinReplica(final String nodeName, final String hostPort) throws InterruptedException
    {
        return startNode(nodeName, hostPort, false, State.REPLICA);
    }

    private ReplicatedEnvironmentFacade startNode(String nodeName, String nodeHostPort, boolean designatedPrimary, State targetState)
            throws InterruptedException
    {
        final String nodePath = createNodeWorkingFolder(nodeName);
        final CountDownLatch _nodeAwaitLatch = new CountDownLatch(1);
        ReplicatedEnvironmentFacade ref = join(nodeName, nodePath, nodeHostPort, designatedPrimary, _nodeAwaitLatch, targetState);
        assertTrue("Node did not join the cluster", _nodeAwaitLatch.await(WAIT_STATE_CHANGE_TIMEOUT, WAIT_STATE_CHANGE_TIME_UNIT));
        return ref;
    }

    private String createNodeWorkingFolder(String nodeName)
    {
        File nodeLocation = new File(_storePath, nodeName);
        nodeLocation.mkdirs();
        final String nodePath = nodeLocation.getAbsolutePath();
        return nodePath;
    }

    private ReplicatedEnvironmentFacade join(String nodeName, String nodePath, String nodeHostPort, boolean designatedPrimary,
            final CountDownLatch nodeAwaitLatch, final State expectedState)
    {
        ReplicatedEnvironmentFacade ref = createReplicatedEnvironmentFacade(nodePath, nodeName, nodeHostPort, designatedPrimary);

        if (expectedState == State.REPLICA)
        {
            _nodes.put(nodeName, ref);
        }
        ref.setStateChangeListener(new StateChangeListener()
        {
            @Override
            public void stateChange(StateChangeEvent stateChangeEvent) throws RuntimeException
            {
                if (stateChangeEvent.getState() == expectedState)
                {
                    nodeAwaitLatch.countDown();
                }
            }
        });
        return ref;
    }

    private ReplicatedEnvironmentFacade createReplicatedEnvironmentFacade(String nodePath, String nodeName, String nodeHostPort,
            boolean designatedPrimary)
    {
        ReplicationNode node = createReplicationNodeMock(nodeName, nodeHostPort, designatedPrimary);
        return new ReplicatedEnvironmentFacade(getName(), nodePath, node, _remoteReplicationNodeFactory);
    }

    private ReplicatedEnvironmentFacade[] startClusterSequentially(int nodeNumber) throws InterruptedException
    {
        // master
        ReplicatedEnvironmentFacade environmentFacade = getEnvironmentFacade();
        ReplicatedEnvironmentFacade[] nodes = new ReplicatedEnvironmentFacade[nodeNumber];
        nodes[0] = environmentFacade;

        int nodePort = TEST_NODE_PORT;
        for (int i = 1; i < nodeNumber; i++)
        {
            nodePort = getNextAvailable(nodePort + 1);
            nodes[i] = joinReplica(TEST_NODE_NAME + "_" + i, "localhost:" + nodePort);
        }
        return nodes;
    }

    private DatabaseConfig createDatabase(ReplicatedEnvironmentFacade environmentFacade, String databaseName) throws AMQStoreException
    {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        environmentFacade.openDatabases(new String[] { databaseName }, dbConfig);
        return dbConfig;
    }

    private ReplicationNode createReplicationNodeMock(String nodeName, String nodeHostPort, boolean designatedPrimary)
    {
        ReplicationNode node =  mock(ReplicationNode.class);
        when(node.getAttribute(NAME)).thenReturn(nodeName);
        when(node.getName()).thenReturn(nodeName);
        when(node.getAttribute(HOST_PORT)).thenReturn(nodeHostPort);
        when(node.getAttribute(DESIGNATED_PRIMARY)).thenReturn(designatedPrimary);
        when(node.getAttribute(GROUP_NAME)).thenReturn(TEST_GROUP_NAME);
        when(node.getAttribute(HELPER_HOST_PORT)).thenReturn(TEST_NODE_HELPER_HOST_PORT);
        when(node.getAttribute(DURABILITY)).thenReturn(TEST_DURABILITY);
        when(node.getAttribute(COALESCING_SYNC)).thenReturn(TEST_COALESCING_SYNC);

        Map<String, String> repConfig = new HashMap<String, String>();
        repConfig.put(ReplicationConfig.REPLICA_ACK_TIMEOUT, "2 s");
        repConfig.put(ReplicationConfig.INSUFFICIENT_REPLICAS_TIMEOUT, "2 s");
        when(node.getAttribute(REPLICATION_PARAMETERS)).thenReturn(repConfig);
        return node;
    }

}
