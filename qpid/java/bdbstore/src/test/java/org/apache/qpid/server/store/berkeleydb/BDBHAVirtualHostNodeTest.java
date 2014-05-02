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

import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;

import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutorImpl;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.RemoteReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHARemoteReplicationNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHARemoteReplicationNodeImpl;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNode;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

public class BDBHAVirtualHostNodeTest extends QpidTestCase
{

    private Broker<?> _broker;
    private File _bdbStorePath;
    private TaskExecutor _taskExecutor;
    private final ConfiguredObjectFactory _objectFactory = BrokerModel.getInstance().getObjectFactory();
    private final Set<BDBHAVirtualHostNode<?>> _nodes = new HashSet<>();

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _broker = BrokerTestHelper.createBrokerMock();

        _taskExecutor = new TaskExecutorImpl();
        _taskExecutor.start();
        when(_broker.getTaskExecutor()).thenReturn(_taskExecutor);

        _bdbStorePath = new File(TMP_FOLDER, getTestName() + "." + System.currentTimeMillis());
        _bdbStorePath.deleteOnExit();
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            Exception firstException = null;
            for (VirtualHostNode<?> node : _nodes)
            {
                try
                {
                    node.setDesiredState(State.DELETED);
                }
                catch(Exception e)
                {
                    if (firstException != null)
                    {
                        firstException = e;
                    }
                }
                if (firstException != null)
                {
                    throw firstException;
                }
            }
        }
        finally
        {
            if (_taskExecutor != null)
            {
                _taskExecutor.stopImmediately();
            }
            if (_bdbStorePath != null)
            {
                FileUtils.delete(_bdbStorePath, true);
            }
            super.tearDown();
        }
    }

    public void testCreateAndActivateVirtualHostNode() throws Exception
    {
        String repStreamTimeout = "2 h";
        String nodeName = "node";
        String groupName = "group";
        String nodeHostPort = "localhost:" + findFreePort();
        String helperHostPort = nodeHostPort;
        String durability = "NO_SYNC,SYNC,NONE";
        UUID id = UUID.randomUUID();

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        attributes.put(BDBHAVirtualHostNode.ID, id);
        attributes.put(BDBHAVirtualHostNode.NAME, nodeName);
        attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        attributes.put(BDBHAVirtualHostNode.ADDRESS, nodeHostPort);
        attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperHostPort);
        attributes.put(BDBHAVirtualHostNode.DURABILITY, durability);
        attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath);
        attributes.put(BDBHAVirtualHostNode.REPLICATED_ENVIRONMENT_CONFIGURATION,
                Collections.singletonMap(ReplicationConfig.REP_STREAM_TIMEOUT, repStreamTimeout));

        VirtualHostNode<?> node = createHaVHN(attributes);

        final CountDownLatch virtualHostAddedLatch = new CountDownLatch(1);
        final CountDownLatch virtualHostStateChangeLatch = new CountDownLatch(1);
        node.addChangeListener(new ConfigurationChangeListener()
        {
            @Override
            public void stateChanged(ConfiguredObject object, State oldState, State newState)
            {
                if (object instanceof VirtualHost)
                {
                    virtualHostStateChangeLatch.countDown();
                }
            }

            @Override
            public void childRemoved(ConfiguredObject object, ConfiguredObject child)
            {
            }

            @Override
            public void childAdded(ConfiguredObject object, ConfiguredObject child)
            {
                if (child instanceof VirtualHost)
                {
                    child.addChangeListener(this);
                    virtualHostAddedLatch.countDown();
                }
            }

            @Override
            public void attributeSet(ConfiguredObject object, String attributeName, Object oldAttributeValue, Object newAttributeValue)
            {
            }
        });
        assertEquals(State.ACTIVE, node.setDesiredState(State.ACTIVE));

        DurableConfigurationStore store = node.getConfigurationStore();
        assertNotNull(store);

        BDBMessageStore bdbMessageStore = (BDBMessageStore) store;
        ReplicatedEnvironment environment = (ReplicatedEnvironment) bdbMessageStore.getEnvironmentFacade().getEnvironment();
        ReplicationConfig replicationConfig = environment.getRepConfig();

        assertEquals(nodeName, environment.getNodeName());
        assertEquals(groupName, environment.getGroup().getName());
        assertEquals(nodeHostPort, replicationConfig.getNodeHostPort());
        assertEquals(helperHostPort, replicationConfig.getHelperHosts());

        assertEquals(durability, environment.getConfig().getDurability().toString());
        assertEquals("Unexpected JE replication stream timeout", repStreamTimeout, replicationConfig.getConfigParam(ReplicationConfig.REP_STREAM_TIMEOUT));

        assertTrue("Virtual host child has not been added", virtualHostAddedLatch.await(30, TimeUnit.SECONDS));
        assertTrue("Virtual host child has not had a state change", virtualHostStateChangeLatch.await(30, TimeUnit.SECONDS));
        VirtualHost<?, ?, ?> virtualHost = node.getVirtualHost();
        assertNotNull("Virtual host child was not added", virtualHost);
        assertEquals("Unexpected virtual host name", groupName, virtualHost.getName());
        assertEquals("Unexpected virtual host store", store, virtualHost.getMessageStore());
        assertEquals("Unexpected virtual host state", State.ACTIVE, virtualHost.getState());

        State currentState = node.setDesiredState(State.STOPPED);
        assertEquals("Unexpected state returned after stop", State.STOPPED, currentState);
        assertEquals("Unexpected state", State.STOPPED, node.getState());

        assertNull("Virtual host is not destroyed", node.getVirtualHost());

        currentState = node.setDesiredState(State.DELETED);
        assertEquals("Unexpected state returned after delete", State.DELETED, currentState);
        assertEquals("Unexpected state", State.DELETED, node.getState());
        assertFalse("Store still exists", _bdbStorePath.exists());
    }

    public void testMutableAttributes() throws Exception
    {
        UUID id = UUID.randomUUID();
        String address = "localhost:" + findFreePort();

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        attributes.put(BDBHAVirtualHostNode.ID, id);
        attributes.put(BDBHAVirtualHostNode.NAME, "node");
        attributes.put(BDBHAVirtualHostNode.GROUP_NAME, "group");
        attributes.put(BDBHAVirtualHostNode.ADDRESS, address);
        attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, address);
        attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath);

        BDBHAVirtualHostNode<?> node = createHaVHN(attributes);

        assertEquals("Failed to activate node", State.ACTIVE, node.setDesiredState(State.ACTIVE));

        BDBMessageStore bdbMessageStore = (BDBMessageStore) node.getConfigurationStore();
        ReplicatedEnvironment environment = (ReplicatedEnvironment) bdbMessageStore.getEnvironmentFacade().getEnvironment();

        assertEquals("Unexpected node priority value before mutation", 1, environment.getRepMutableConfig().getNodePriority());
        assertFalse("Unexpected designated primary value before mutation", environment.getRepMutableConfig().getDesignatedPrimary());
        assertEquals("Unexpected electable group override value before mutation", 0, environment.getRepMutableConfig().getElectableGroupSizeOverride());

        node.setAttribute(BDBHAVirtualHostNode.PRIORITY, 1, 2);
        node.setAttribute(BDBHAVirtualHostNode.DESIGNATED_PRIMARY, false, true);
        node.setAttribute(BDBHAVirtualHostNode.QUORUM_OVERRIDE, 0, 1);

        assertEquals("Unexpected node priority value after mutation", 2, environment.getRepMutableConfig().getNodePriority());
        assertTrue("Unexpected designated primary value after mutation", environment.getRepMutableConfig().getDesignatedPrimary());
        assertEquals("Unexpected electable group override value after mutation", 1, environment.getRepMutableConfig().getElectableGroupSizeOverride());

        assertNotNull("Join time should be set", node.getJoinTime());
        assertNotNull("Last known replication transaction idshould be set", node.getLastKnownReplicationTransactionId());
    }

    public void testTransferMasterToSelf() throws Exception
    {
        int node1PortNumber = findFreePort();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";

        Map<String, Object> node1Attributes = new HashMap<String, Object>();
        node1Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node1Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node1Attributes.put(BDBHAVirtualHostNode.NAME, "node1");
        node1Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node1Attributes.put(BDBHAVirtualHostNode.ADDRESS, helperAddress);
        node1Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node1Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "1");

        BDBHAVirtualHostNode<?> node1 = createHaVHN(node1Attributes);
        assertEquals("Failed to activate node", State.ACTIVE, node1.setDesiredState(State.ACTIVE));

        int node2PortNumber = getNextAvailable(node1PortNumber+1);

        Map<String, Object> node2Attributes = new HashMap<String, Object>();
        node2Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node2Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node2Attributes.put(BDBHAVirtualHostNode.NAME, "node2");
        node2Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node2Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node2PortNumber);
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node2Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "2");

        BDBHAVirtualHostNode<?> node2 = createHaVHN(node2Attributes);
        assertEquals("Failed to activate node2", State.ACTIVE, node2.setDesiredState(State.ACTIVE));

        int node3PortNumber = getNextAvailable(node2PortNumber+1);
        Map<String, Object> node3Attributes = new HashMap<String, Object>();
        node3Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node3Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node3Attributes.put(BDBHAVirtualHostNode.NAME, "node3");
        node3Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node3Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node3PortNumber);
        node3Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node3Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "3");
        BDBHAVirtualHostNode<?> node3 = createHaVHN(node3Attributes);
        assertEquals("Failed to activate node3", State.ACTIVE, node3.setDesiredState(State.ACTIVE));

        BDBHAVirtualHostNode<?> replica = null;
        int findReplicaCount = 0;
        while(replica == null)
        {
            for (BDBHAVirtualHostNode<?> node : _nodes)
            {
                if ("REPLICA".equals(node.getRole()))
                {
                    replica = node;
                    break;
                }
            }

            Thread.sleep(100);
            if (findReplicaCount > 20)
            {
                fail("Could not find a node is replica role");
            }
            findReplicaCount++;
        }

        replica.setAttribute(BDBHAVirtualHostNode.ROLE, "REPLICA", "MASTER");

        assertNodeRole(replica, "MASTER");
    }

    public void testTransferMasterToReplica() throws Exception
    {
        int node1PortNumber = findFreePort();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";

        Map<String, Object> node1Attributes = new HashMap<String, Object>();
        node1Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node1Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node1Attributes.put(BDBHAVirtualHostNode.NAME, "node1");
        node1Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node1Attributes.put(BDBHAVirtualHostNode.ADDRESS, helperAddress);
        node1Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node1Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "1");

        BDBHAVirtualHostNode<?> node1 = createHaVHN(node1Attributes);
        assertEquals("Failed to activate node", State.ACTIVE, node1.setDesiredState(State.ACTIVE));

        final CountDownLatch remoteNodeLatch = new CountDownLatch(2);
        node1.addChangeListener(new ConfigurationChangeListener()
        {
            @Override
            public void stateChanged(ConfiguredObject object, State oldState, State newState)
            {
            }

            @Override
            public void childRemoved(ConfiguredObject object, ConfiguredObject child)
            {
            }

            @Override
            public void childAdded(ConfiguredObject object, ConfiguredObject child)
            {
                if (child instanceof RemoteReplicationNode)
                {
                    remoteNodeLatch.countDown();
                }
            }

            @Override
            public void attributeSet(ConfiguredObject object, String attributeName, Object oldAttributeValue,
                    Object newAttributeValue)
            {
            }
        });

        int node2PortNumber = getNextAvailable(node1PortNumber+1);

        Map<String, Object> node2Attributes = new HashMap<String, Object>();
        node2Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node2Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node2Attributes.put(BDBHAVirtualHostNode.NAME, "node2");
        node2Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node2Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node2PortNumber);
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node2Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "2");

        BDBHAVirtualHostNode<?> node2 = createHaVHN(node2Attributes);
        assertEquals("Failed to activate node2", State.ACTIVE, node2.setDesiredState(State.ACTIVE));

        int node3PortNumber = getNextAvailable(node2PortNumber+1);
        Map<String, Object> node3Attributes = new HashMap<String, Object>();
        node3Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node3Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node3Attributes.put(BDBHAVirtualHostNode.NAME, "node3");
        node3Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node3Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node3PortNumber);
        node3Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node3Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "3");
        BDBHAVirtualHostNode<?> node3 = createHaVHN(node3Attributes);
        assertEquals("Failed to activate node3", State.ACTIVE, node3.setDesiredState(State.ACTIVE));

        assertTrue("Replication nodes have not been seen during 5s", remoteNodeLatch.await(5, TimeUnit.SECONDS));

        Collection<? extends RemoteReplicationNode> remoteNodes = node1.getRemoteReplicationNodes();
        BDBHARemoteReplicationNodeImpl replicaRemoteNode = (BDBHARemoteReplicationNodeImpl)remoteNodes.iterator().next();

        long awaitReplicaRoleCount = 0;
        while(!"REPLICA".equals(replicaRemoteNode.getRole()))
        {
            Thread.sleep(100);
            if (awaitReplicaRoleCount > 50)
            {
                fail("Remote replication node is not in a REPLICA role");
            }
            awaitReplicaRoleCount++;
        }
        replicaRemoteNode.setAttributes(Collections.<String,Object>singletonMap(BDBHARemoteReplicationNode.ROLE, "MASTER"));

        BDBHAVirtualHostNode<?> replica = replicaRemoteNode.getName().equals(node2.getName())? node2 : node3;
        assertNodeRole(replica, "MASTER");
    }

    public void testMutatingRoleWhenNotReplica_IsDisallowed() throws Exception
    {
        int nodePortNumber = findFreePort();
        String helperAddress = "localhost:" + nodePortNumber;
        String groupName = "group";

        Map<String, Object> node1Attributes = new HashMap<String, Object>();
        node1Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node1Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node1Attributes.put(BDBHAVirtualHostNode.NAME, "node1");
        node1Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node1Attributes.put(BDBHAVirtualHostNode.ADDRESS, helperAddress);
        node1Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node1Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "1");

        BDBHAVirtualHostNode<?> node = createHaVHN(node1Attributes);
        assertEquals("Failed to activate node", State.ACTIVE, node.setDesiredState(State.ACTIVE));

        assertNodeRole(node, "MASTER");

        try
        {
            node.setAttributes(Collections.<String,Object>singletonMap(BDBHAVirtualHostNode.ROLE, "REPLICA"));
            fail("Role mutation should fail");
        }
        catch(IllegalStateException e)
        {
            // PASS
        }
    }


    private BDBHAVirtualHostNode<?> createHaVHN(Map<String, Object> attributes)
    {
        BDBHAVirtualHostNode<?> node = (BDBHAVirtualHostNode<?>) _objectFactory.create(VirtualHostNode.class, attributes, _broker);
        _nodes.add(node);
        return node;
    }

    private void assertNodeRole(BDBHAVirtualHostNode<?> node, String roleName) throws InterruptedException
    {
        int awaitMastershipCount = 0;
        while(!roleName.equals(node.getRole()))
        {
            Thread.sleep(100);
            if (awaitMastershipCount > 50)
            {
                fail("Node " + node.getName() + " did not transit into role " + roleName);
            }
            awaitMastershipCount++;
        }
    }
}


