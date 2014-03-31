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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.qpid.server.store.berkeleydb.EnvironmentFacade;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.rep.ReplicatedEnvironment.State;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;

public class ReplicatedEnvironmentFacadeTest extends QpidTestCase
{

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

        addNode(State.MASTER, stateChangeListener);
        assertTrue("Master was not started", masterLatch.await(LISTENER_TIMEOUT, TimeUnit.SECONDS));

        int replica1Port = getNextAvailable(TEST_NODE_PORT + 1);
        String node1NodeHostPort = "localhost:" + replica1Port;
        int replica2Port = getNextAvailable(replica1Port + 1);
        String node2NodeHostPort = "localhost:" + replica2Port;

        ReplicatedEnvironmentFacade replica1 = createReplica(TEST_NODE_NAME + "_1", node1NodeHostPort);
        ReplicatedEnvironmentFacade replica2 = createReplica(TEST_NODE_NAME + "_2", node2NodeHostPort);

        // close replicas
        replica1.close();
        replica2.close();

        assertTrue("Environment should be recreated and go into unknown state",
                unknownLatch.await(WAIT_STATE_CHANGE_TIMEOUT, TimeUnit.SECONDS));

        assertEquals("Node made master an unexpected number of times", 1, masterStateChangeCount.get());
        assertEquals("Node made unknown an unexpected number of times", 1, unknownStateChangeCount.get());
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
        ReplicatedEnvironmentFacade env = addNode(State.MASTER, stateChangeListener);
        assertTrue("Environment was not created", stateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS));
        return env;
    }

    private ReplicatedEnvironmentFacade createReplica(String nodeName, String nodeHostPort) throws Exception
    {
        TestStateChangeListener testStateChangeListener = new TestStateChangeListener(State.REPLICA);
        ReplicatedEnvironmentFacade replicaEnvironmentFacade = addNode(nodeName, nodeHostPort, TEST_DESIGNATED_PRIMARY, State.REPLICA, testStateChangeListener);
        boolean awaitForStateChange = testStateChangeListener.awaitForStateChange(LISTENER_TIMEOUT, TimeUnit.SECONDS);
        assertTrue("Replica " + nodeName + " did not go into desired state; current actual state is " + testStateChangeListener.getCurrentActualState(), awaitForStateChange);
        return replicaEnvironmentFacade;
    }

    private ReplicatedEnvironmentFacade addNode(String nodeName, String nodeHostPort, boolean designatedPrimary,
            State desiredState, StateChangeListener stateChangeListener)
    {
        ReplicatedEnvironmentConfiguration config = createReplicatedEnvironmentConfiguration(nodeName, nodeHostPort, designatedPrimary);
        ReplicatedEnvironmentFacade ref = new ReplicatedEnvironmentFacade(config, null);
        ref.setStateChangeListener(stateChangeListener);
        _nodes.put(nodeName, ref);
        return ref;
    }

    private ReplicatedEnvironmentFacade addNode(State desiredState, StateChangeListener stateChangeListener)
    {
        return addNode(TEST_NODE_NAME, TEST_NODE_HOST_PORT, TEST_DESIGNATED_PRIMARY, desiredState, stateChangeListener);
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
        when(node.getDurability()).thenReturn(TEST_DURABILITY);
        when(node.isCoalescingSync()).thenReturn(TEST_COALESCING_SYNC);

        Map<String, String> repConfig = new HashMap<String, String>();
        repConfig.put(ReplicationConfig.REPLICA_ACK_TIMEOUT, "2 s");
        repConfig.put(ReplicationConfig.INSUFFICIENT_REPLICAS_TIMEOUT, "2 s");
        when(node.getReplicationParameters()).thenReturn(repConfig);
        when(node.getStorePath()).thenReturn(new File(_storePath, nodeName).getAbsolutePath());
        return node;
    }
}
