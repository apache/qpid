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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.systest.rest.Asserts;
import org.apache.qpid.systest.rest.QpidRestTestCase;
import org.apache.qpid.util.FileUtils;

public class VirtualHostRestTest extends QpidRestTestCase
{

    private static final String VIRTUALHOST_NODES_ATTRIBUTE = "replicationnodes";

    public void testPutCreateHAVirtualHost() throws Exception
    {
        Map<String, Object> hostData = new HashMap<String, Object>();
        String hostName = getTestName();
        hostData.put(VirtualHost.NAME, hostName);
        hostData.put(VirtualHost.TYPE, BDBHAVirtualHostFactory.TYPE);
        hostData.put(VirtualHost.STATE, State.QUIESCED);

        int responseCode = getRestTestHelper().submitRequest("/rest/virtualhost/" + hostName, "PUT", hostData);
        assertEquals("Unexpected response code for virtual host creation request", 201, responseCode);

        String storeLocation = new File(TMP_FOLDER, "store-" + hostName + "-" + System.currentTimeMillis()).getAbsolutePath();
        String nodeName = "node1";
        String groupName = "replication-group";
        int port = findFreePort();
        String hostPort = "localhost:" + port;

        Map<String, Object> nodeData = new HashMap<String, Object>();
        nodeData.put(ReplicationNode.NAME, nodeName);
        nodeData.put(ReplicationNode.GROUP_NAME, groupName);
        nodeData.put(ReplicationNode.HOST_PORT, hostPort);
        nodeData.put(ReplicationNode.HELPER_HOST_PORT, hostPort);
        nodeData.put(ReplicationNode.STORE_PATH, storeLocation);

        String createNodeUrl = "/rest/replicationnode/" + hostName + "/" + nodeName;
        responseCode = getRestTestHelper().submitRequest(createNodeUrl, "PUT", nodeData);
        assertEquals("Unexpected response code for node creation request", 201, responseCode);

        hostData.clear();
        hostData.put(VirtualHost.STATE, State.ACTIVE);
        responseCode = getRestTestHelper().submitRequest("/rest/virtualhost/" + hostName, "PUT", hostData);
        assertEquals("Unexpected response code for virtual host update status", 200, responseCode);

        Map<String, Object> replicationNodeDetails = getRestTestHelper().getJsonAsSingletonList("/rest/replicationnode/" + hostName + "/" + nodeName);
        assertLocalNode(nodeData, replicationNodeDetails);
        try
        {
            // make sure that the host is saved in the broker store
            restartBroker();

            Map<String, Object> hostDetails = getRestTestHelper().getJsonAsSingletonList("/rest/virtualhost/" + hostName);
            Asserts.assertVirtualHost(hostName, hostDetails);
            assertEquals("Unexpected virtual host type", BDBHAVirtualHostFactory.TYPE.toString(), hostDetails.get(VirtualHost.TYPE));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) hostDetails.get(VIRTUALHOST_NODES_ATTRIBUTE);
            assertEquals("Unexpected number of nodes", 1, nodes.size());
            assertLocalNode(nodeData, nodes.get(0));

            // verify that that node rest interface returns the same node attributes
            replicationNodeDetails = getRestTestHelper().getJsonAsSingletonList("/rest/replicationnode/" + hostName + "/" + nodeName);
            assertLocalNode(nodeData, replicationNodeDetails);
        }
        finally
        {
            if (storeLocation != null)
            {
                FileUtils.delete(new File(storeLocation), true);
            }
        }
    }

    private void assertLocalNode(Map<String, Object> expectedNodeAttributes, Map<String, Object> actualNodesAttributes)
    {
        for (Map.Entry<String, Object> entry : actualNodesAttributes.entrySet())
        {
            String name = entry.getKey();
            Object value = entry.getValue();
            assertEquals("Unexpected node attribute " + name + " value ", value, actualNodesAttributes.get(name));
        }
    }

}
