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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutorImpl;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.MessageLogger;
import org.apache.qpid.server.logging.messages.VirtualHostMessages;
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
import org.apache.qpid.server.virtualhost.berkeleydb.BDBHAVirtualHost;
import org.apache.qpid.server.virtualhost.berkeleydb.BDBHAVirtualHostImpl;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHARemoteReplicationNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHARemoteReplicationNodeImpl;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNodeImpl;
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
                    node.delete();
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
        UUID id = UUID.randomUUID();

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(BDBHAVirtualHostNode.TYPE, BDBHAVirtualHostNodeImpl.VIRTUAL_HOST_NODE_TYPE);
        attributes.put(BDBHAVirtualHostNode.ID, id);
        attributes.put(BDBHAVirtualHostNode.NAME, nodeName);
        attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        attributes.put(BDBHAVirtualHostNode.ADDRESS, nodeHostPort);
        attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperHostPort);
        attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath);
        attributes.put(BDBHAVirtualHostNode.CONTEXT,
                Collections.singletonMap(ReplicationConfig.REP_STREAM_TIMEOUT, repStreamTimeout));

        BDBHAVirtualHostNode<?> node = createHaVHN(attributes);

        final CountDownLatch virtualHostAddedLatch = new CountDownLatch(1);
        node.addChangeListener(new NoopConfigurationChangeListener()
        {
            @Override
            public void childAdded(ConfiguredObject<?> object, ConfiguredObject<?> child)
            {
                if (child instanceof VirtualHost)
                {
                    child.addChangeListener(this);
                    virtualHostAddedLatch.countDown();
                }
            }
        });

        node.start();
        assertNodeRole(node, "MASTER", "REPLICA");
        assertEquals("Unexpected node state", State.ACTIVE, node.getState());

        DurableConfigurationStore store = node.getConfigurationStore();
        assertNotNull(store);

        BDBConfigurationStore bdbConfigurationStore = (BDBConfigurationStore) store;
        ReplicatedEnvironment environment = (ReplicatedEnvironment) bdbConfigurationStore.getEnvironmentFacade().getEnvironment();
        ReplicationConfig replicationConfig = environment.getRepConfig();

        assertEquals(nodeName, environment.getNodeName());
        assertEquals(groupName, environment.getGroup().getName());
        assertEquals(nodeHostPort, replicationConfig.getNodeHostPort());
        assertEquals(helperHostPort, replicationConfig.getHelperHosts());

        assertEquals("SYNC,NO_SYNC,SIMPLE_MAJORITY", environment.getConfig().getDurability().toString());
        assertEquals("Unexpected JE replication stream timeout", repStreamTimeout, replicationConfig.getConfigParam(ReplicationConfig.REP_STREAM_TIMEOUT));

        assertTrue("Virtual host child has not been added", virtualHostAddedLatch.await(30, TimeUnit.SECONDS));
        VirtualHost<?, ?, ?> virtualHost = node.getVirtualHost();
        assertNotNull("Virtual host child was not added", virtualHost);
        assertEquals("Unexpected virtual host name", groupName, virtualHost.getName());
        assertEquals("Unexpected virtual host store", bdbConfigurationStore.getMessageStore(), virtualHost.getMessageStore());
        assertEquals("Unexpected virtual host state", State.ACTIVE, virtualHost.getState());

        node.stop();
        assertEquals("Unexpected state returned after stop", State.STOPPED, node.getState());
        assertEquals("Unexpected state", State.STOPPED, node.getState());

        assertNull("Virtual host is not destroyed", node.getVirtualHost());

        node.delete();
        assertEquals("Unexpected state returned after delete", State.DELETED, node.getState());
        assertEquals("Unexpected state", State.DELETED, node.getState());
        assertFalse("Store still exists " + _bdbStorePath, _bdbStorePath.exists());
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

        BDBHAVirtualHostNode<?> node = createAndStartHaVHN(attributes);

        BDBConfigurationStore bdbConfigurationStore = (BDBConfigurationStore) node.getConfigurationStore();
        ReplicatedEnvironment environment = (ReplicatedEnvironment) bdbConfigurationStore.getEnvironmentFacade().getEnvironment();

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
        createAndStartHaVHN(node1Attributes);

        int node2PortNumber = getNextAvailable(node1PortNumber+1);

        Map<String, Object> node2Attributes = new HashMap<String, Object>();
        node2Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node2Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node2Attributes.put(BDBHAVirtualHostNode.NAME, "node2");
        node2Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node2Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node2PortNumber);
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node2Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "2");
        createAndStartHaVHN(node2Attributes);

        int node3PortNumber = getNextAvailable(node2PortNumber+1);
        Map<String, Object> node3Attributes = new HashMap<String, Object>();
        node3Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node3Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node3Attributes.put(BDBHAVirtualHostNode.NAME, "node3");
        node3Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node3Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node3PortNumber);
        node3Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node3Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "3");
        createAndStartHaVHN(node3Attributes);

        BDBHAVirtualHostNode<?> replica = awaitAndFindNodeInRole("REPLICA");

        replica.setAttribute(BDBHAVirtualHostNode.ROLE, "REPLICA", "MASTER");

        assertNodeRole(replica, "MASTER");
    }

    public void testTransferMasterToRemoteReplica() throws Exception
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

        BDBHAVirtualHostNode<?> node1 = createAndStartHaVHN(node1Attributes);

        final AtomicReference<RemoteReplicationNode<?>> lastSeenReplica = new AtomicReference<>();
        final CountDownLatch remoteNodeLatch = new CountDownLatch(2);
        node1.addChangeListener(new NoopConfigurationChangeListener()
        {
            @Override
            public void childAdded(ConfiguredObject<?> object, ConfiguredObject<?> child)
            {
                if (child instanceof RemoteReplicationNode)
                {
                    remoteNodeLatch.countDown();
                    lastSeenReplica.set((RemoteReplicationNode<?>)child);
                }
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

        BDBHAVirtualHostNode<?> node2 = createAndStartHaVHN(node2Attributes);

        int node3PortNumber = getNextAvailable(node2PortNumber+1);
        Map<String, Object> node3Attributes = new HashMap<String, Object>();
        node3Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node3Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node3Attributes.put(BDBHAVirtualHostNode.NAME, "node3");
        node3Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node3Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node3PortNumber);
        node3Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node3Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "3");
        BDBHAVirtualHostNode<?> node3 = createAndStartHaVHN(node3Attributes);

        assertTrue("Replication nodes have not been seen during 5s", remoteNodeLatch.await(5, TimeUnit.SECONDS));

        BDBHARemoteReplicationNodeImpl replicaRemoteNode = (BDBHARemoteReplicationNodeImpl)lastSeenReplica.get();
        awaitForAttributeChange(replicaRemoteNode, BDBHARemoteReplicationNodeImpl.ROLE, "REPLICA");

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

        BDBHAVirtualHostNode<?> node = createAndStartHaVHN(node1Attributes);
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


    public void testRemoveReplicaNode() throws Exception
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
        createAndStartHaVHN(node1Attributes);

        int node2PortNumber = getNextAvailable(node1PortNumber+1);

        Map<String, Object> node2Attributes = new HashMap<String, Object>();
        node2Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node2Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node2Attributes.put(BDBHAVirtualHostNode.NAME, "node2");
        node2Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node2Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node2PortNumber);
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node2Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "2");
        createAndStartHaVHN(node2Attributes);

        int node3PortNumber = getNextAvailable(node2PortNumber+1);
        Map<String, Object> node3Attributes = new HashMap<String, Object>();
        node3Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node3Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node3Attributes.put(BDBHAVirtualHostNode.NAME, "node3");
        node3Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node3Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node3PortNumber);
        node3Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node3Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "3");
        createAndStartHaVHN(node3Attributes);

        BDBHAVirtualHostNode<?> master = awaitAndFindNodeInRole("MASTER");
        awaitRemoteNodes(master, 2);

        BDBHAVirtualHostNode<?> replica = awaitAndFindNodeInRole("REPLICA");

        assertNotNull("Remote node " + replica.getName() + " is not found", findRemoteNode( master, replica.getName()));
        replica.delete();

        awaitRemoteNodes(master, 1);

        assertNull("Remote node " + replica.getName() + " is not found", findRemoteNode( master, replica.getName()));
    }


    public void testSetSynchronizationPolicyAttributesOnVirtualHost() throws Exception
    {
        int node1PortNumber = findFreePort();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";

        Map<String, Object> nodeAttributes = new HashMap<String, Object>();
        nodeAttributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        nodeAttributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        nodeAttributes.put(BDBHAVirtualHostNode.NAME, "node1");
        nodeAttributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        nodeAttributes.put(BDBHAVirtualHostNode.ADDRESS, helperAddress);
        nodeAttributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        nodeAttributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "1");
        BDBHAVirtualHostNode<?> node = createHaVHN(nodeAttributes);

        final CountDownLatch virtualHostAddedLatch = new CountDownLatch(1);
        node.addChangeListener(new NoopConfigurationChangeListener()
        {
            @Override
            public void childAdded(ConfiguredObject<?> object, ConfiguredObject<?> child)
            {
                if (child instanceof VirtualHost)
                {
                    child.addChangeListener(this);
                    virtualHostAddedLatch.countDown();
                }
            }
        });

        node.start();
        assertNodeRole(node, "MASTER", "REPLICA");
        assertEquals("Unexpected node state", State.ACTIVE, node.getState());

        assertTrue("Virtual host child has not been added", virtualHostAddedLatch.await(30, TimeUnit.SECONDS));
        BDBHAVirtualHostImpl virtualHost = (BDBHAVirtualHostImpl)node.getVirtualHost();
        assertNotNull("Virtual host is not created", virtualHost);

        awaitForAttributeChange(virtualHost, BDBHAVirtualHostImpl.COALESCING_SYNC, true);

        assertEquals("Unexpected local transaction synchronization policy", "SYNC", virtualHost.getLocalTransactionSynchronizationPolicy());
        assertEquals("Unexpected remote transaction synchronization policy", "NO_SYNC", virtualHost.getRemoteTransactionSynchronizationPolicy());
        assertTrue("CoalescingSync is not ON", virtualHost.isCoalescingSync());

        Map<String, Object> virtualHostAttributes = new HashMap<String,Object>();
        virtualHostAttributes.put(BDBHAVirtualHost.LOCAL_TRANSACTION_SYNCHRONIZATION_POLICY, "WRITE_NO_SYNC");
        virtualHostAttributes.put(BDBHAVirtualHost.REMOTE_TRANSACTION_SYNCHRONIZATION_POLICY, "SYNC");
        virtualHost.setAttributes(virtualHostAttributes);

        virtualHost.stop();
        virtualHost.start();

        assertEquals("Unexpected local transaction synchronization policy", "WRITE_NO_SYNC", virtualHost.getLocalTransactionSynchronizationPolicy());
        assertEquals("Unexpected remote transaction synchronization policy", "SYNC", virtualHost.getRemoteTransactionSynchronizationPolicy());
        assertFalse("CoalescingSync is not OFF", virtualHost.isCoalescingSync());
        try
        {
            virtualHost.setAttributes(Collections.<String, Object>singletonMap(BDBHAVirtualHost.LOCAL_TRANSACTION_SYNCHRONIZATION_POLICY, "INVALID"));
            fail("Invalid syncronization policy is set");
        }
        catch(IllegalArgumentException e)
        {
            //pass
        }

        try
        {
            virtualHost.setAttributes(Collections.<String, Object>singletonMap(BDBHAVirtualHost.REMOTE_TRANSACTION_SYNCHRONIZATION_POLICY, "INVALID"));
            fail("Invalid syncronization policy is set");
        }
        catch(IllegalArgumentException e)
        {
            //pass
        }

    }

    public void testIntruderProtection() throws Exception
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

        BDBHAVirtualHostNode<?> node1 = createAndStartHaVHN(node1Attributes);
        BDBHAVirtualHost<?> host = (BDBHAVirtualHost<?>)node1.getVirtualHost();

        List<String> permittedNodes = new ArrayList<String>();
        int node2PortNumber = getNextAvailable(node1PortNumber+1);
        permittedNodes.add(helperAddress);
        permittedNodes.add("localhost:" + node2PortNumber);
        host.setAttributes(Collections.<String, Object>singletonMap(BDBHAVirtualHost.PERMITTED_NODES, permittedNodes));

        Map<String, Object> node2Attributes = new HashMap<String, Object>();
        node2Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node2Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node2Attributes.put(BDBHAVirtualHostNode.NAME, "node2");
        node2Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node2Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node2PortNumber);
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node2Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "2");
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_NODE_NAME, "node1");
        node2Attributes.put(BDBHAVirtualHostNode.PRIORITY, 0);

        BDBHAVirtualHostNode<?> node2 = createAndStartHaVHN(node2Attributes);

        int node3PortNumber = getNextAvailable(node2PortNumber+1);
        Map<String, Object> node3Attributes = new HashMap<String, Object>();
        node3Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node3Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node3Attributes.put(BDBHAVirtualHostNode.NAME, "node3");
        node3Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node3Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node3PortNumber);
        node3Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node3Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "3");
        node3Attributes.put(BDBHAVirtualHostNode.HELPER_NODE_NAME, "node1");
        node3Attributes.put(BDBHAVirtualHostNode.PRIORITY, 0);

        try
        {
            createHaVHN(node3Attributes);
            fail("The VHN should not be permitted to join the group");
        }
        catch(IllegalConfigurationException e)
        {
            assertEquals("Unexpected exception message", String.format("Node from '%s' is not permitted!", "localhost:" + node3PortNumber), e.getMessage());
        }

        // join node by skipping a step retrieving node state and checking the permitted hosts
        node3Attributes.remove(BDBHAVirtualHostNode.HELPER_NODE_NAME);

        final CountDownLatch stopLatch = new CountDownLatch(1);
        ConfigurationChangeListener listener = new NoopConfigurationChangeListener()
        {
            @Override
            public void stateChanged(ConfiguredObject<?> object, State oldState, State newState)
            {
                if (newState == State.ERRORED)
                {
                    stopLatch.countDown();
                }
            }
        };
        node1.addChangeListener(listener);

        createHaVHN(node3Attributes);

        assertTrue("Intruder protection was not triggered during expected timeout", stopLatch.await(10, TimeUnit.SECONDS));
    }

    public void testIntruderProtectionInManagementMode() throws Exception
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

        BDBHAVirtualHostNode<?> node1 = createAndStartHaVHN(node1Attributes);

        int node2PortNumber = getNextAvailable(node1PortNumber+1);
        Map<String, Object> node2Attributes = new HashMap<String, Object>();
        node2Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node2Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node2Attributes.put(BDBHAVirtualHostNode.NAME, "node2");
        node2Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node2Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node2PortNumber);
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node2Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "2");
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_NODE_NAME, "node1");
        node2Attributes.put(BDBHAVirtualHostNode.PRIORITY, 0);

        BDBHAVirtualHostNode<?> node2 = createAndStartHaVHN(node2Attributes);

        final CountDownLatch stopLatch = new CountDownLatch(1);
        ConfigurationChangeListener listener = new NoopConfigurationChangeListener()
        {
            @Override
            public void stateChanged(ConfiguredObject<?> object, State oldState, State newState)
            {
                if (newState == State.ERRORED)
                {
                    stopLatch.countDown();
                }
            }
        };
        node1.addChangeListener(listener);

        BDBHAVirtualHost<?> host = (BDBHAVirtualHost<?>)node1.getVirtualHost();

        List<String> permittedNodes = new ArrayList<String>();
        permittedNodes.add(helperAddress);
        host.setAttributes(Collections.<String, Object>singletonMap(BDBHAVirtualHost.PERMITTED_NODES, permittedNodes));

        assertTrue("Intruder protection was not triggered during expected timeout", stopLatch.await(10, TimeUnit.SECONDS));

        when(_broker.isManagementMode()).thenReturn(true);
        node1.start();

        awaitRemoteNodes(node1, 1);

        BDBHARemoteReplicationNode<?> remote = findRemoteNode(node1, node2.getName());
        remote.delete();
    }

    public void testIntruderConnectedBeforePermittedNodesAreSet() throws Exception
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

        BDBHAVirtualHostNode<?> node1 = createAndStartHaVHN(node1Attributes);

        int node2PortNumber = getNextAvailable(node1PortNumber+1);
        Map<String, Object> node2Attributes = new HashMap<String, Object>();
        node2Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node2Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node2Attributes.put(BDBHAVirtualHostNode.NAME, "node2");
        node2Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node2Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node2PortNumber);
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node2Attributes.put(BDBHAVirtualHostNode.STORE_PATH, _bdbStorePath + File.separator + "2");
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_NODE_NAME, "node1");

        createAndStartHaVHN(node2Attributes);

        final CountDownLatch stopLatch = new CountDownLatch(1);
        ConfigurationChangeListener listener = new NoopConfigurationChangeListener()
        {
            @Override
            public void stateChanged(ConfiguredObject<?> object, State oldState, State newState)
            {
                if (newState == State.ERRORED)
                {
                    stopLatch.countDown();
                }
            }
        };
        node1.addChangeListener(listener);

        BDBHAVirtualHost<?> host = (BDBHAVirtualHost<?>)node1.getVirtualHost();
        List<String> permittedNodes = new ArrayList<String>();
        permittedNodes.add(helperAddress);
        host.setAttributes(Collections.<String, Object>singletonMap(BDBHAVirtualHost.PERMITTED_NODES, permittedNodes));

        assertTrue("Intruder protection was not triggered during expected timeout", stopLatch.await(20, TimeUnit.SECONDS));
    }

    private BDBHARemoteReplicationNode<?> findRemoteNode(BDBHAVirtualHostNode<?> node, String name)
    {
        for (RemoteReplicationNode<?> remoteNode : node.getRemoteReplicationNodes())
        {
            if (remoteNode.getName().equals(name))
            {
                return (BDBHARemoteReplicationNode<?>)remoteNode;
            }
        }
        return null;
    }

    private void awaitRemoteNodes(BDBHAVirtualHostNode<?> node, int expectedNodeNumber) throws InterruptedException
    {
        int counter = 0;

        @SuppressWarnings("rawtypes")
        Collection<? extends RemoteReplicationNode> remoteNodes = null;
        do
        {
            remoteNodes = node.getRemoteReplicationNodes();
            if (counter > 0)
            {
                Thread.sleep(100);
            }
            counter++;
        }
        while(remoteNodes.size() != expectedNodeNumber && counter<100);
        assertEquals("Unexpected node number", expectedNodeNumber, node.getRemoteReplicationNodes().size());
    }

    private void awaitForAttributeChange(ConfiguredObject<?> object, String name, Object expectedValue) throws InterruptedException
    {
        int awaitCounter = 0;
        while(!object.equals(object.getAttribute(name)) && awaitCounter < 50)
        {
            Thread.sleep(100);
            awaitCounter++;
        }
        assertEquals("Unexpected attribute " + name + " on " + object, expectedValue, object.getAttribute(name) );
    }

    private BDBHAVirtualHostNode<?> awaitAndFindNodeInRole(String role) throws InterruptedException
    {
        BDBHAVirtualHostNode<?> replica = null;
        int findReplicaCount = 0;
        while(replica == null)
        {
            replica = findNodeInRole(role);
            if (replica == null)
            {
                Thread.sleep(100);
            }
            if (findReplicaCount > 50)
            {
                fail("Could not find a node in replica role");
            }
            findReplicaCount++;
        }
        return replica;
    }

    private BDBHAVirtualHostNode<?> findNodeInRole(String role)
    {
        for (BDBHAVirtualHostNode<?> node : _nodes)
        {
            if (role.equals(node.getRole()))
            {
                return node;
            }
        }
        return null;
    }

    private BDBHAVirtualHostNode<?> createHaVHN(Map<String, Object> attributes)
    {
        @SuppressWarnings("unchecked")
        BDBHAVirtualHostNode<?> node = (BDBHAVirtualHostNode<?>) _objectFactory.create(VirtualHostNode.class, attributes, _broker);
        _nodes.add(node);
        return node;
    }

    private void assertNodeRole(BDBHAVirtualHostNode<?> node, String... roleName) throws InterruptedException
    {
        int iterationCounter = 0;
        boolean inRole =false;
        do
        {
            for (String role : roleName)
            {
                if (role.equals(node.getRole()))
                {
                    inRole = true;
                    break;
                }
            }
            if (!inRole)
            {
                Thread.sleep(100);
            }
            iterationCounter++;
        }
        while(!inRole && iterationCounter<50);
        assertTrue("Node " + node.getName() + " did not transit into role " + Arrays.toString(roleName), inRole);
    }

    private BDBHAVirtualHostNode<?> createAndStartHaVHN(Map<String, Object> attributes)  throws InterruptedException
    {
        BDBHAVirtualHostNode<?> node = createHaVHN(attributes);
        node.start();
        assertNodeRole(node, "MASTER", "REPLICA");
        assertEquals("Unexpected node state", State.ACTIVE, node.getState());
        return node;
    }

    class NoopConfigurationChangeListener implements ConfigurationChangeListener
    {

        @Override
        public void stateChanged(ConfiguredObject<?> object, State oldState, State newState)
        {
        }

        @Override
        public void childAdded(ConfiguredObject<?> object, ConfiguredObject<?> child)
        {
        }

        @Override
        public void childRemoved(ConfiguredObject<?> object, ConfiguredObject<?> child)
        {
        }

        @Override
        public void attributeSet(ConfiguredObject<?> object, String attributeName, Object oldAttributeValue,
                Object newAttributeValue)
        {
        }
    }
}


