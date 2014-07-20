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

import static java.util.Collections.*;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.RemoteReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.virtualhost.berkeleydb.BDBHAVirtualHost;
import org.apache.qpid.server.virtualhost.berkeleydb.BDBHAVirtualHostImpl;
import org.apache.qpid.server.virtualhostnode.AbstractVirtualHostNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHARemoteReplicationNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHARemoteReplicationNodeImpl;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNodeTestHelper;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNodeImpl;
import org.apache.qpid.test.utils.QpidTestCase;

public class BDBHAVirtualHostNodeTest extends QpidTestCase
{
    private BDBHAVirtualHostNodeTestHelper _helper;
    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _helper = new BDBHAVirtualHostNodeTestHelper(getTestName());
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            _helper.tearDown();
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testCreateAndActivateVirtualHostNode() throws Exception
    {
        String messageStorePath = _helper.getMessageStorePath();
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
        attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath);
        attributes.put(BDBHAVirtualHostNode.CONTEXT,
                singletonMap(ReplicationConfig.REP_STREAM_TIMEOUT, repStreamTimeout));

        BDBHAVirtualHostNode<?> node = _helper.createHaVHN(attributes);

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
        _helper.assertNodeRole(node, "MASTER", "REPLICA");
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
        assertFalse("Store still exists " + messageStorePath, new File(messageStorePath).exists());
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
        attributes.put(BDBHAVirtualHostNode.STORE_PATH, _helper.getMessageStorePath());

        BDBHAVirtualHostNode<?> node = _helper.createAndStartHaVHN(attributes);

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
        String messageStorePath = _helper.getMessageStorePath();
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
        node1Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "1");
        _helper.createAndStartHaVHN(node1Attributes);

        int node2PortNumber = getNextAvailable(node1PortNumber+1);

        Map<String, Object> node2Attributes = new HashMap<String, Object>();
        node2Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node2Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node2Attributes.put(BDBHAVirtualHostNode.NAME, "node2");
        node2Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node2Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node2PortNumber);
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node2Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "2");
        _helper.createAndStartHaVHN(node2Attributes);

        int node3PortNumber = getNextAvailable(node2PortNumber+1);
        Map<String, Object> node3Attributes = new HashMap<String, Object>();
        node3Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node3Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node3Attributes.put(BDBHAVirtualHostNode.NAME, "node3");
        node3Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node3Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node3PortNumber);
        node3Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node3Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "3");
        _helper.createAndStartHaVHN(node3Attributes);

        BDBHAVirtualHostNode<?> replica = _helper.awaitAndFindNodeInRole("REPLICA");

        replica.setAttribute(BDBHAVirtualHostNode.ROLE, "REPLICA", "MASTER");

        _helper.assertNodeRole(replica, "MASTER");
    }

    public void testTransferMasterToRemoteReplica() throws Exception
    {
        int node1PortNumber = findFreePort();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String messageStorePath = _helper.getMessageStorePath();

        Map<String, Object> node1Attributes = new HashMap<String, Object>();
        node1Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node1Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node1Attributes.put(BDBHAVirtualHostNode.NAME, "node1");
        node1Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node1Attributes.put(BDBHAVirtualHostNode.ADDRESS, helperAddress);
        node1Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node1Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "1");

        BDBHAVirtualHostNode<?> node1 = _helper.createAndStartHaVHN(node1Attributes);

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
        node2Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "2");

        BDBHAVirtualHostNode<?> node2 = _helper.createAndStartHaVHN(node2Attributes);

        int node3PortNumber = getNextAvailable(node2PortNumber+1);
        Map<String, Object> node3Attributes = new HashMap<String, Object>();
        node3Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node3Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node3Attributes.put(BDBHAVirtualHostNode.NAME, "node3");
        node3Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node3Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node3PortNumber);
        node3Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node3Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "3");
        BDBHAVirtualHostNode<?> node3 = _helper.createAndStartHaVHN(node3Attributes);

        assertTrue("Replication nodes have not been seen during 5s", remoteNodeLatch.await(5, TimeUnit.SECONDS));

        BDBHARemoteReplicationNodeImpl replicaRemoteNode = (BDBHARemoteReplicationNodeImpl)lastSeenReplica.get();
        _helper.awaitForAttributeChange(replicaRemoteNode, BDBHARemoteReplicationNodeImpl.ROLE, "REPLICA");

        replicaRemoteNode.setAttributes(Collections.<String,Object>singletonMap(BDBHARemoteReplicationNode.ROLE, "MASTER"));

        BDBHAVirtualHostNode<?> replica = replicaRemoteNode.getName().equals(node2.getName())? node2 : node3;
        _helper.assertNodeRole(replica, "MASTER");
    }

    public void testMutatingRoleWhenNotReplica_IsDisallowed() throws Exception
    {
        int nodePortNumber = findFreePort();
        String helperAddress = "localhost:" + nodePortNumber;
        String groupName = "group";
        String messageStorePath = _helper.getMessageStorePath();

        Map<String, Object> node1Attributes = new HashMap<String, Object>();
        node1Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node1Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node1Attributes.put(BDBHAVirtualHostNode.NAME, "node1");
        node1Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node1Attributes.put(BDBHAVirtualHostNode.ADDRESS, helperAddress);
        node1Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node1Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "1");

        BDBHAVirtualHostNode<?> node = _helper.createAndStartHaVHN(node1Attributes);
        _helper.assertNodeRole(node, "MASTER");

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
        String messageStorePath = _helper.getMessageStorePath();
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
        node1Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "1");
        _helper.createAndStartHaVHN(node1Attributes);

        int node2PortNumber = getNextAvailable(node1PortNumber+1);

        Map<String, Object> node2Attributes = new HashMap<String, Object>();
        node2Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node2Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node2Attributes.put(BDBHAVirtualHostNode.NAME, "node2");
        node2Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node2Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node2PortNumber);
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node2Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "2");
        _helper.createAndStartHaVHN(node2Attributes);

        int node3PortNumber = getNextAvailable(node2PortNumber+1);
        Map<String, Object> node3Attributes = new HashMap<String, Object>();
        node3Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node3Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node3Attributes.put(BDBHAVirtualHostNode.NAME, "node3");
        node3Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node3Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node3PortNumber);
        node3Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node3Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "3");
        _helper.createAndStartHaVHN(node3Attributes);

        BDBHAVirtualHostNode<?> master = _helper.awaitAndFindNodeInRole("MASTER");
        _helper.awaitRemoteNodes(master, 2);

        BDBHAVirtualHostNode<?> replica = _helper.awaitAndFindNodeInRole("REPLICA");

        assertNotNull("Remote node " + replica.getName() + " is not found", _helper.findRemoteNode(master, replica.getName()));
        replica.delete();

        _helper.awaitRemoteNodes(master, 1);

        assertNull("Remote node " + replica.getName() + " is not found", _helper.findRemoteNode(master, replica.getName()));
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
        nodeAttributes.put(BDBHAVirtualHostNode.STORE_PATH, _helper.getMessageStorePath() + File.separator + "1");
        BDBHAVirtualHostNode<?> node = _helper.createHaVHN(nodeAttributes);

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
        _helper.assertNodeRole(node, "MASTER", "REPLICA");
        assertEquals("Unexpected node state", State.ACTIVE, node.getState());

        assertTrue("Virtual host child has not been added", virtualHostAddedLatch.await(30, TimeUnit.SECONDS));
        BDBHAVirtualHostImpl virtualHost = (BDBHAVirtualHostImpl)node.getVirtualHost();
        assertNotNull("Virtual host is not created", virtualHost);

        _helper.awaitForAttributeChange(virtualHost, BDBHAVirtualHostImpl.COALESCING_SYNC, true);

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
        String messageStorePath = _helper.getMessageStorePath();
        int node1PortNumber = findFreePort();
        int node2PortNumber = getNextAvailable(node1PortNumber+1);
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";

        List<String> permittedNodes = new ArrayList<>();
        permittedNodes.add(helperAddress);
        String node2Address = "localhost:" + node2PortNumber;
        permittedNodes.add(node2Address);

        String blueprint = String.format("{ \"%s\" : [ \"%s\", \"%s\" ] } ", BDBHAVirtualHost.PERMITTED_NODES, helperAddress, node2Address);

        Map<String, Object> node1Attributes = new HashMap<>();
        node1Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node1Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node1Attributes.put(BDBHAVirtualHostNode.NAME, "node1");
        node1Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node1Attributes.put(BDBHAVirtualHostNode.ADDRESS, helperAddress);
        node1Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node1Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "1");
        Map<String, String> contextMap = singletonMap(AbstractVirtualHostNode.VIRTUALHOST_BLUEPRINT_CONTEXT_VAR, blueprint);
        node1Attributes.put(BDBHAVirtualHostNode.CONTEXT, contextMap);

        BDBHAVirtualHostNode<?> node1 = _helper.createAndStartHaVHN(node1Attributes);

        Map<String, Object> node2Attributes = new HashMap<String, Object>();
        node2Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node2Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node2Attributes.put(BDBHAVirtualHostNode.NAME, "node2");
        node2Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node2Attributes.put(BDBHAVirtualHostNode.ADDRESS, node2Address);
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node2Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "2");
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_NODE_NAME, "node1");
        node2Attributes.put(BDBHAVirtualHostNode.PRIORITY, 0);

        BDBHAVirtualHostNode<?> node2 = _helper.createAndStartHaVHN(node2Attributes);

        int node3PortNumber = getNextAvailable(node2PortNumber+1);
        Map<String, Object> node3Attributes = new HashMap<String, Object>();
        node3Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node3Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node3Attributes.put(BDBHAVirtualHostNode.NAME, "node3");
        node3Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node3Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node3PortNumber);
        node3Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node3Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "3");
        node3Attributes.put(BDBHAVirtualHostNode.HELPER_NODE_NAME, "node1");
        node3Attributes.put(BDBHAVirtualHostNode.PRIORITY, 0);

        try
        {
            _helper.createHaVHN(node3Attributes);
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

        _helper.createHaVHN(node3Attributes);

        assertTrue("Intruder protection was not triggered during expected timeout", stopLatch.await(10, TimeUnit.SECONDS));
    }

    public void testIntruderProtectionInManagementMode() throws Exception
    {
        String messageStorePath = _helper.getMessageStorePath();
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
        node1Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "1");

        BDBHAVirtualHostNode<?> node1 = _helper.createAndStartHaVHN(node1Attributes);

        int node2PortNumber = getNextAvailable(node1PortNumber+1);
        Map<String, Object> node2Attributes = new HashMap<String, Object>();
        node2Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node2Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node2Attributes.put(BDBHAVirtualHostNode.NAME, "node2");
        node2Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node2Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node2PortNumber);
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node2Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "2");
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_NODE_NAME, "node1");
        node2Attributes.put(BDBHAVirtualHostNode.PRIORITY, 0);

        BDBHAVirtualHostNode<?> node2 = _helper.createAndStartHaVHN(node2Attributes);

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

        when(_helper.getBroker().isManagementMode()).thenReturn(true);
        node1.start();

        _helper.awaitRemoteNodes(node1, 1);

        BDBHARemoteReplicationNode<?> remote = _helper.findRemoteNode(node1, node2.getName());
        remote.delete();
    }

    public void testIntruderConnectedBeforePermittedNodesAreSet() throws Exception
    {
        String messageStorePath = _helper.getMessageStorePath();
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
        node1Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "1");

        BDBHAVirtualHostNode<?> node1 = _helper.createAndStartHaVHN(node1Attributes);

        int node2PortNumber = getNextAvailable(node1PortNumber+1);
        Map<String, Object> node2Attributes = new HashMap<String, Object>();
        node2Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node2Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node2Attributes.put(BDBHAVirtualHostNode.NAME, "node2");
        node2Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node2Attributes.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + node2PortNumber);
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node2Attributes.put(BDBHAVirtualHostNode.STORE_PATH, messageStorePath + File.separator + "2");
        node2Attributes.put(BDBHAVirtualHostNode.HELPER_NODE_NAME, "node1");

        _helper.createAndStartHaVHN(node2Attributes);

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

}


