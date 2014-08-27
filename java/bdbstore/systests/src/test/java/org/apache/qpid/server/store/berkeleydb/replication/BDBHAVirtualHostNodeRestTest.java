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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import com.sleepycat.je.Durability;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;
import org.apache.qpid.server.model.RemoteReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.virtualhost.berkeleydb.BDBHAVirtualHost;
import org.apache.qpid.server.virtualhostnode.AbstractVirtualHostNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHARemoteReplicationNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNode;
import org.apache.qpid.systest.rest.Asserts;
import org.apache.qpid.systest.rest.QpidRestTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class BDBHAVirtualHostNodeRestTest extends QpidRestTestCase
{
    private static final String NODE1 = "node1";
    private static final String NODE2 = "node2";
    private static final String NODE3 = "node3";

    private int _node1HaPort;
    private int _node2HaPort;
    private int _node3HaPort;

    private String _hostName;
    private String _baseNodeRestUrl;

    @Override
    public void setUp() throws Exception
    {
        setTestSystemProperty(ReplicatedEnvironmentFacade.REMOTE_NODE_MONITOR_INTERVAL_PROPERTY_NAME, "1000");

        super.setUp();
        _hostName = getTestName();
        _baseNodeRestUrl = "virtualhostnode/";

        _node1HaPort = findFreePort();
        _node2HaPort = getNextAvailable(_node1HaPort + 1);
        _node3HaPort = getNextAvailable(_node2HaPort + 1);


    }

    @Override
    protected void customizeConfiguration() throws IOException
    {
        super.customizeConfiguration();
        TestBrokerConfiguration config = getBrokerConfiguration();
        config.removeObjectConfiguration(VirtualHostNode.class, TEST2_VIRTUALHOST);
        config.removeObjectConfiguration(VirtualHostNode.class, TEST3_VIRTUALHOST);
    }

    public void testCreate3NodeGroup() throws Exception
    {
        createHANode(NODE1, _node1HaPort, _node1HaPort);
        assertNode(NODE1, _node1HaPort, _node1HaPort, NODE1);
        createHANode(NODE2, _node2HaPort, _node1HaPort);
        assertNode(NODE2, _node2HaPort, _node1HaPort, NODE1);
        createHANode(NODE3, _node3HaPort, _node1HaPort);
        assertNode(NODE3, _node3HaPort, _node1HaPort, NODE1);
        assertRemoteNodes(NODE1, NODE2, NODE3);
    }

    public void testMutateStateOfOneNode() throws Exception
    {
        createHANode(NODE1, _node1HaPort, _node1HaPort);
        createHANode(NODE2, _node2HaPort, _node1HaPort);
        createHANode(NODE3, _node3HaPort, _node1HaPort);

        String node1Url = _baseNodeRestUrl + NODE1;
        String node2Url = _baseNodeRestUrl + NODE2;
        String node3Url = _baseNodeRestUrl + NODE3;

        assertActualAndDesiredStates(node1Url, "ACTIVE", "ACTIVE");
        assertActualAndDesiredStates(node2Url, "ACTIVE", "ACTIVE");
        assertActualAndDesiredStates(node3Url, "ACTIVE", "ACTIVE");

        mutateDesiredState(node1Url, "STOPPED");

        assertActualAndDesiredStates(node1Url, "STOPPED", "STOPPED");
        assertActualAndDesiredStates(node2Url, "ACTIVE", "ACTIVE");
        assertActualAndDesiredStates(node3Url, "ACTIVE", "ACTIVE");

        List<Map<String, Object>> remoteNodes = getRestTestHelper().getJsonAsList("replicationnode/" + NODE2);
        assertEquals("Unexpected number of remote nodes on " + NODE2, 2, remoteNodes.size());

        Map<String, Object> remoteNode1 = findRemoteNodeByName(remoteNodes, NODE1);

        assertEquals("Node 1 observed from node 2 is in the wrong state",
                "UNAVAILABLE", remoteNode1.get(BDBHARemoteReplicationNode.STATE));
        assertEquals("Node 1 observed from node 2 has the wrong role",
                     "UNKNOWN", remoteNode1.get(BDBHARemoteReplicationNode.ROLE));

    }

    public void testNewMasterElectedWhenVirtualHostIsStopped() throws Exception
    {
        createHANode(NODE1, _node1HaPort, _node1HaPort);
        createHANode(NODE2, _node2HaPort, _node1HaPort);
        createHANode(NODE3, _node3HaPort, _node1HaPort);

        String node1Url = _baseNodeRestUrl + NODE1;
        String node2Url = _baseNodeRestUrl + NODE2;
        String node3Url = _baseNodeRestUrl + NODE3;

        assertActualAndDesiredStates(node1Url, "ACTIVE", "ACTIVE");
        assertActualAndDesiredStates(node2Url, "ACTIVE", "ACTIVE");
        assertActualAndDesiredStates(node3Url, "ACTIVE", "ACTIVE");

        // Put virtualhost in STOPPED state
        String virtualHostRestUrl = "virtualhost/" + NODE1 + "/" + _hostName;
        assertActualAndDesiredStates(virtualHostRestUrl, "ACTIVE", "ACTIVE");
        mutateDesiredState(virtualHostRestUrl, "STOPPED");
        assertActualAndDesiredStates(virtualHostRestUrl, "STOPPED", "STOPPED");

        // Now stop node 1 to cause an election between nodes 2 & 3
        mutateDesiredState(node1Url, "STOPPED");
        assertActualAndDesiredStates(node1Url, "STOPPED", "STOPPED");

        Map<String, Object> newMasterData = awaitNewMaster(node2Url, node3Url);

        //Check the virtual host of the new master is in the stopped state
        String newMasterVirtualHostRestUrl = "virtualhost/" + newMasterData.get(BDBHAVirtualHostNode.NAME) + "/" + _hostName;
        assertActualAndDesiredStates(newMasterVirtualHostRestUrl, "STOPPED", "STOPPED");
    }

    public void testDeleteReplicaNode() throws Exception
    {
        createHANode(NODE1, _node1HaPort, _node1HaPort);
        createHANode(NODE2, _node2HaPort, _node1HaPort);
        createHANode(NODE3, _node3HaPort, _node1HaPort);

        assertRemoteNodes(NODE1, NODE2, NODE3);

        List<Map<String,Object>> data = getRestTestHelper().getJsonAsList("replicationnode/" + NODE1);
        assertEquals("Unexpected number of remote nodes on " + NODE1, 2, data.size());

        int responseCode = getRestTestHelper().submitRequest(_baseNodeRestUrl + NODE2, "DELETE");
        assertEquals("Unexpected response code on deletion of virtual host node " + NODE2, 200, responseCode);

        int counter = 0;
        while (data.size() != 1 && counter<50)
        {
            data = getRestTestHelper().getJsonAsList("replicationnode/" + NODE1);
            if (data.size() != 1)
            {
                Thread.sleep(100l);
            }
            counter++;
        }
        assertEquals("Unexpected number of remote nodes on " + NODE1, 1, data.size());
    }

    public void testDeleteMasterNode() throws Exception
    {
        createHANode(NODE1, _node1HaPort, _node1HaPort);
        createHANode(NODE2, _node2HaPort, _node1HaPort);
        createHANode(NODE3, _node3HaPort, _node1HaPort);

        assertNode(NODE1, _node1HaPort, _node1HaPort, NODE1);
        assertRemoteNodes(NODE1, NODE2, NODE3);

        // change priority to make Node2 a master
        int responseCode = getRestTestHelper().submitRequest(_baseNodeRestUrl + NODE2, "PUT", Collections.<String,Object>singletonMap(BDBHAVirtualHostNode.PRIORITY, 100));
        assertEquals("Unexpected response code on priority update of virtual host node " + NODE2, 200, responseCode);

        List<Map<String,Object>> data = getRestTestHelper().getJsonAsList("replicationnode/" + NODE2);
        assertEquals("Unexpected number of remote nodes on " + NODE2, 2, data.size());

        // delete master
        responseCode = getRestTestHelper().submitRequest(_baseNodeRestUrl + NODE1, "DELETE");
        assertEquals("Unexpected response code on deletion of virtual host node " + NODE1, 200, responseCode);

        // wait for new master
        waitForAttributeChanged(_baseNodeRestUrl + NODE2 + "?depth=0", BDBHAVirtualHostNode.ROLE, "MASTER");

        // delete remote node
        responseCode = getRestTestHelper().submitRequest("replicationnode/" + NODE2 + "/" + NODE1, "DELETE");
        assertEquals("Unexpected response code on deletion of remote node " + NODE1, 200, responseCode);

        int counter = 0;
        while (data.size() != 1 && counter<50)
        {
            data = getRestTestHelper().getJsonAsList("replicationnode/" + NODE2);
            if (data.size() != 1)
            {
                Thread.sleep(100l);
            }
            counter++;
        }
        assertEquals("Unexpected number of remote nodes on " + NODE2, 1, data.size());
    }

    public void testIntruderBDBHAVHNNotAllowedToConnect() throws Exception
    {
        createHANode(NODE1, _node1HaPort, _node1HaPort);
        assertNode(NODE1, _node1HaPort, _node1HaPort, NODE1);

        // add permitted node
        Map<String, Object> node3Data = createNodeAttributeMap(NODE3, _node3HaPort, _node1HaPort);
        getRestTestHelper().submitRequest(_baseNodeRestUrl + NODE3, "PUT", node3Data, 201);
        assertNode(NODE3, _node3HaPort, _node1HaPort, NODE1);
        assertRemoteNodes(NODE1, NODE3);

        int intruderPort = getNextAvailable(_node3HaPort + 1);

        // try to add not permitted node
        Map<String, Object> nodeData = createNodeAttributeMap(NODE2, intruderPort, _node1HaPort);
        getRestTestHelper().submitRequest(_baseNodeRestUrl + NODE2, "PUT", nodeData, 409);

        assertRemoteNodes(NODE1, NODE3);
    }

    public void testIntruderProtection() throws Exception
    {
        createHANode(NODE1, _node1HaPort, _node1HaPort);
        assertNode(NODE1, _node1HaPort, _node1HaPort, NODE1);

        Map<String,Object> nodeData = getRestTestHelper().getJsonAsSingletonList(_baseNodeRestUrl + NODE1);
        String node1StorePath = (String)nodeData.get(BDBHAVirtualHostNode.STORE_PATH);
        long transactionId =  ((Number)nodeData.get(BDBHAVirtualHostNode.LAST_KNOWN_REPLICATION_TRANSACTION_ID)).longValue();

        // add permitted node
        Map<String, Object> node3Data = createNodeAttributeMap(NODE3, _node3HaPort, _node1HaPort);
        getRestTestHelper().submitRequest(_baseNodeRestUrl + NODE3, "PUT", node3Data, 201);
        assertNode(NODE3, _node3HaPort, _node1HaPort, NODE1);
        assertRemoteNodes(NODE1, NODE3);

        // Ensure PINGDB is created
        // in order to exclude hanging of environment
        // when environment.close is called whilst PINGDB is created.
        // On node joining, a record is updated in PINGDB
        // if lastTransactionId is incremented then node ping task was executed
        int counter = 0;
        long newTransactionId = transactionId;
        while(newTransactionId == transactionId && counter<50)
        {
            nodeData = getRestTestHelper().getJsonAsSingletonList(_baseNodeRestUrl + NODE1);
            newTransactionId =  ((Number)nodeData.get(BDBHAVirtualHostNode.LAST_KNOWN_REPLICATION_TRANSACTION_ID)).longValue();
            if (newTransactionId != transactionId)
            {
                break;
            }
            counter++;
            Thread.sleep(100l);
        }

        //connect intruder node
        String nodeName = NODE2;
        String nodeHostPort = "localhost:" + getNextAvailable(_node3HaPort + 1);
        File environmentPathFile = new File(node1StorePath, nodeName);
        environmentPathFile.mkdirs();
        ReplicationConfig replicationConfig = new ReplicationConfig((String)nodeData.get(BDBHAVirtualHostNode.GROUP_NAME), nodeName, nodeHostPort);
        replicationConfig.setHelperHosts((String)nodeData.get(BDBHAVirtualHostNode.ADDRESS));
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        envConfig.setDurability(Durability.parse((String)nodeData.get(BDBHAVirtualHostNode.DURABILITY)));

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

        waitForAttributeChanged(_baseNodeRestUrl + NODE1, VirtualHostNode.STATE, State.ERRORED.name());
        waitForAttributeChanged(_baseNodeRestUrl + NODE3, VirtualHostNode.STATE, State.ERRORED.name());
    }

    private void createHANode(String nodeName, int nodePort, int helperPort) throws Exception
    {
        Map<String, Object> nodeData = createNodeAttributeMap(nodeName, nodePort, helperPort);

        int responseCode = getRestTestHelper().submitRequest(_baseNodeRestUrl + nodeName, "PUT", nodeData);
        assertEquals("Unexpected response code for virtual host node " + nodeName + " creation request", 201, responseCode);
        String hostExpectedState = nodePort == helperPort ? State.ACTIVE.name(): State.UNAVAILABLE.name();
        waitForAttributeChanged("virtualhost/" + nodeName + "/" + _hostName, BDBHAVirtualHost.STATE, hostExpectedState);
    }

    private Map<String, Object> createNodeAttributeMap(String nodeName, int nodePort, int helperPort) throws Exception
    {
        Map<String, Object> nodeData = new HashMap<String, Object>();
        nodeData.put(BDBHAVirtualHostNode.NAME, nodeName);
        nodeData.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        nodeData.put(BDBHAVirtualHostNode.GROUP_NAME, _hostName);
        nodeData.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + nodePort);
        nodeData.put(BDBHAVirtualHostNode.HELPER_ADDRESS, "localhost:" + helperPort);
        nodeData.put(BDBHAVirtualHostNode.HELPER_NODE_NAME, NODE1);
        Map<String,String> context = new HashMap<>();
        nodeData.put(BDBHAVirtualHostNode.CONTEXT, context);
        if (nodePort == helperPort)
        {
            nodeData.put(BDBHAVirtualHostNode.PERMITTED_NODES, GroupCreator.getPermittedNodes("localhost", _node1HaPort, _node2HaPort, _node3HaPort));
        }
        String bluePrint = GroupCreator.getBlueprint();
        context.put(AbstractVirtualHostNode.VIRTUALHOST_BLUEPRINT_CONTEXT_VAR, bluePrint);
        return nodeData;
    }

    private void assertNode(String nodeName, int nodePort, int nodeHelperPort, String masterNode) throws Exception
    {
        boolean isMaster = nodeName.equals(masterNode);
        String expectedRole = isMaster? "MASTER" : "REPLICA";
        waitForAttributeChanged(_baseNodeRestUrl + nodeName + "?depth=0", BDBHAVirtualHostNode.ROLE, expectedRole);

        Map<String, Object> nodeData = getRestTestHelper().getJsonAsSingletonList(_baseNodeRestUrl + nodeName + "?depth=0");
        assertEquals("Unexpected name", nodeName, nodeData.get(BDBHAVirtualHostNode.NAME));
        assertEquals("Unexpected type", "BDB_HA", nodeData.get(BDBHAVirtualHostNode.TYPE));
        assertEquals("Unexpected address", "localhost:" + nodePort, nodeData.get(BDBHAVirtualHostNode.ADDRESS));
        assertEquals("Unexpected helper address", "localhost:" + nodeHelperPort, nodeData.get(BDBHAVirtualHostNode.HELPER_ADDRESS));
        assertEquals("Unexpected group name", _hostName, nodeData.get(BDBHAVirtualHostNode.GROUP_NAME));
        assertEquals("Unexpected role", expectedRole, nodeData.get(BDBHAVirtualHostNode.ROLE));

        Integer lastKnownTransactionId = (Integer) nodeData.get(BDBHAVirtualHostNode.LAST_KNOWN_REPLICATION_TRANSACTION_ID);
        assertNotNull("Unexpected lastKnownReplicationId", lastKnownTransactionId);
        assertTrue("Unexpected lastKnownReplicationId " + lastKnownTransactionId, lastKnownTransactionId > 0);

        Long joinTime = (Long) nodeData.get(BDBHAVirtualHostNode.JOIN_TIME);
        assertNotNull("Unexpected joinTime", joinTime);
        assertTrue("Unexpected joinTime " + joinTime, joinTime > 0);

        if (isMaster)
        {
            waitForAttributeChanged("virtualhost/" + masterNode + "/" + _hostName + "?depth=0", VirtualHost.STATE, State.ACTIVE.name());
        }

    }

    private void assertRemoteNodes(String masterNode, String... replicaNodes) throws Exception
    {
        List<String> clusterNodes = new ArrayList<String>(Arrays.asList(replicaNodes));
        clusterNodes.add(masterNode);

        for (String clusterNodeName : clusterNodes)
        {
            List<String> remotes = new ArrayList<String>(clusterNodes);
            remotes.remove(clusterNodeName);
            for (String remote : remotes)
            {
                String remoteUrl = "replicationnode/" + clusterNodeName + "/" + remote;
                Map<String, Object> nodeData = waitForAttributeChanged(remoteUrl, BDBHARemoteReplicationNode.ROLE, remote.equals(masterNode) ? "MASTER" : "REPLICA");
                assertRemoteNodeData(remote, nodeData);
            }
        }
    }

    private void assertRemoteNodeData(String name, Map<String, Object> nodeData)
    {
        assertEquals("Remote node " + name + " has unexpected name", name, nodeData.get(BDBHAVirtualHostNode.NAME));

        Integer lastKnownTransactionId = (Integer) nodeData.get(BDBHAVirtualHostNode.LAST_KNOWN_REPLICATION_TRANSACTION_ID);
        assertNotNull("Node " + name + " has unexpected lastKnownReplicationId", lastKnownTransactionId);
        assertTrue("Node " + name + " has unexpected lastKnownReplicationId " + lastKnownTransactionId, lastKnownTransactionId > 0);

        Long joinTime = (Long) nodeData.get(BDBHAVirtualHostNode.JOIN_TIME);
        assertNotNull("Node " + name + " has unexpected joinTime", joinTime);
        assertTrue("Node " + name + " has unexpected joinTime " + joinTime, joinTime > 0);
     }

    private void assertActualAndDesiredStates(final String restUrl,
                                              final String expectedDesiredState,
                                              final String expectedActualState) throws IOException
    {
        Map<String, Object> objectData = getRestTestHelper().getJsonAsSingletonList(restUrl);
        Asserts.assertActualAndDesiredState(expectedDesiredState, expectedActualState, objectData);
    }

    private void mutateDesiredState(final String restUrl, final String newState) throws IOException
    {
        Map<String, Object> newAttributes = new HashMap<String, Object>();
        newAttributes.put(VirtualHostNode.DESIRED_STATE, newState);

        getRestTestHelper().submitRequest(restUrl, "PUT", newAttributes, HttpServletResponse.SC_OK);
    }

    private Map<String, Object> findRemoteNodeByName(final List<Map<String, Object>> remoteNodes, final String nodeName)
    {
        Map<String, Object> foundNode = null;
        for (Map<String, Object> remoteNode : remoteNodes)
        {
            if (nodeName.equals(remoteNode.get(RemoteReplicationNode.NAME)))
            {
                foundNode = remoteNode;
                break;
            }
        }
        assertNotNull("Could not find node with name " + nodeName + " amongst remote nodes.");
        return foundNode;
    }

    private Map<String, Object> awaitNewMaster(final String... nodeUrls)
            throws IOException, InterruptedException
    {
        Map<String, Object> newMasterData = null;
        int counter = 0;
        while (newMasterData == null && counter < 50)
        {
            for(String nodeUrl: nodeUrls)
            {
                Map<String, Object> nodeData = getRestTestHelper().getJsonAsSingletonList(nodeUrl);
                if ("MASTER".equals(nodeData.get(BDBHAVirtualHostNode.ROLE)))
                {
                    newMasterData = nodeData;
                    break;
                }
            }
            if (newMasterData == null)
            {
                Thread.sleep(100l);
                counter++;
            }
        }
        assertNotNull("Could not find new master", newMasterData);
        return newMasterData;
    }


}
