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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacade;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHARemoteReplicationNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNode;
import org.apache.qpid.systest.rest.QpidRestTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.util.FileUtils;

public class BDBHAVirtualHostNodeRestTest extends QpidRestTestCase
{
    private static final String NODE1 = "node1";
    private static final String NODE2 = "node2";
    private static final String NODE3 = "node3";

    private int _node1HaPort;
    private int _node2HaPort;
    private int _node3HaPort;

    private String _hostName;
    private File _storeBaseDir;
    private String _baseNodeRestUrl;

    @Override
    public void setUp() throws Exception
    {
        setTestSystemProperty(ReplicatedEnvironmentFacade.REMOTE_NODE_MONITOR_INTERVAL_PROPERTY_NAME, "1000");

        super.setUp();
        _hostName = getTestName();
        _baseNodeRestUrl = "virtualhostnode/";

        _storeBaseDir = new File(TMP_FOLDER, "store-" + _hostName + "-" + System.currentTimeMillis());

        _node1HaPort = findFreePort();
        _node2HaPort = getNextAvailable(_node1HaPort + 1);
        _node3HaPort = getNextAvailable(_node2HaPort + 1);

    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            if (_storeBaseDir != null)
            {
                FileUtils.delete(_storeBaseDir, true);
            }
        }
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
        }
        assertEquals("Unexpected number of remote nodes on " + NODE2, 1, data.size());
    }

    private void createHANode(String nodeName, int nodePort, int helperPort) throws Exception
    {
        Map<String, Object> nodeData = new HashMap<String, Object>();
        nodeData.put(BDBHAVirtualHostNode.NAME, nodeName);
        nodeData.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        nodeData.put(BDBHAVirtualHostNode.STORE_PATH, _storeBaseDir.getPath() + File.separator + nodeName);
        nodeData.put(BDBHAVirtualHostNode.GROUP_NAME, _hostName);
        nodeData.put(BDBHAVirtualHostNode.ADDRESS, "localhost:" + nodePort);
        nodeData.put(BDBHAVirtualHostNode.HELPER_ADDRESS, "localhost:" + helperPort);

        int responseCode = getRestTestHelper().submitRequest(_baseNodeRestUrl + nodeName, "PUT", nodeData);
        assertEquals("Unexpected response code for virtual host node " + nodeName + " creation request", 201, responseCode);
    }

    private void assertNode(String nodeName, int nodePort, int nodeHelperPort, String masterNode) throws Exception
    {
        boolean isMaster = nodeName.equals(masterNode);
        String expectedRole = isMaster? "MASTER" : "REPLICA";
        waitForAttributeChanged(_baseNodeRestUrl + nodeName + "?depth=0", BDBHAVirtualHostNode.ROLE, expectedRole);

        Map<String, Object> nodeData = getRestTestHelper().getJsonAsSingletonList(_baseNodeRestUrl + nodeName + "?depth=0");
        assertEquals("Unexpected name", nodeName, nodeData.get(BDBHAVirtualHostNode.NAME));
        assertEquals("Unexpected type", "BDB_HA", nodeData.get(BDBHAVirtualHostNode.TYPE));
        assertEquals("Unexpected path", new File(_storeBaseDir, nodeName).getPath(), nodeData.get(BDBHAVirtualHostNode.STORE_PATH));
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

    private Map<String, Object> waitForAttributeChanged(String url, String attributeName, Object newValue) throws Exception
    {
        List<Map<String, Object>> nodeAttributes = getRestTestHelper().getJsonAsList(url);
        long limit = System.currentTimeMillis() + 5000;
        while(System.currentTimeMillis() < limit && (nodeAttributes.size() == 0 || !newValue.equals(nodeAttributes.get(0).get(attributeName))))
        {
            Thread.sleep(100l);
            nodeAttributes = getRestTestHelper().getJsonAsList(url);
        }
        Map<String, Object> nodeData = nodeAttributes.get(0);
        assertEquals("Unexpected attribute " + attributeName, newValue, nodeData.get(attributeName));
        return nodeData;
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
}
