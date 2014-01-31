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
    private File _storeFile;
    private String _hostName;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _hostName = getTestName();

        _storeFile = new File(TMP_FOLDER, "store-" + _hostName + "-" + System.currentTimeMillis());
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
            if (_storeFile != null)
            {
                FileUtils.delete(_storeFile, true);
            }
        }
    }

    public void testPutCreateHAVirtualHost() throws Exception
    {
        Map<String, Object> hostData = new HashMap<String, Object>();
        hostData.put(VirtualHost.NAME, _hostName);
        hostData.put(VirtualHost.TYPE, BDBHAVirtualHostFactory.TYPE);
        hostData.put(VirtualHost.DESIRED_STATE, State.QUIESCED);

        int responseCode = getRestTestHelper().submitRequest("/rest/virtualhost/" + _hostName, "PUT", hostData);
        assertEquals("Unexpected response code for virtual host creation request", 201, responseCode);

        Map<String, Object> hostDetails = getRestTestHelper().getJsonAsSingletonList("/rest/virtualhost/" + _hostName);
        assertEquals("Virtual host in unexpected desired state ", State.QUIESCED.name(), hostDetails.get(VirtualHost.DESIRED_STATE));
        assertEquals("Virtual host in unexpected actual state ", State.QUIESCED.name(), hostDetails.get(VirtualHost.STATE));

        String storeLocation = _storeFile.getAbsolutePath();
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

        String createNodeUrl = "/rest/replicationnode/" + _hostName + "/" + nodeName;
        responseCode = getRestTestHelper().submitRequest(createNodeUrl, "PUT", nodeData);
        assertEquals("Unexpected response code for node creation request", 201, responseCode);

        hostData.clear();
        hostData.put(VirtualHost.DESIRED_STATE, State.ACTIVE);
        responseCode = getRestTestHelper().submitRequest("/rest/virtualhost/" + _hostName, "PUT", hostData);
        assertEquals("Unexpected response code for virtual host update status", 200, responseCode);

        waitForVirtualHostActivation(_hostName, 10000l);

        Map<String, Object> replicationNodeDetails = getRestTestHelper().getJsonAsSingletonList("/rest/replicationnode/" + _hostName + "/" + nodeName);
        assertLocalNode(nodeData, replicationNodeDetails);

        // make sure that the host is saved in the broker store
        restartBroker();

        hostDetails = waitForVirtualHostActivation(_hostName, 10000l);
        Asserts.assertVirtualHost(_hostName, hostDetails);
        assertEquals("Unexpected virtual host type", BDBHAVirtualHostFactory.TYPE.toString(), hostDetails.get(VirtualHost.TYPE));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) hostDetails.get(VIRTUALHOST_NODES_ATTRIBUTE);
        assertEquals("Unexpected number of nodes", 1, nodes.size());
        assertLocalNode(nodeData, nodes.get(0));

        // verify that that node rest interface returns the same node attributes
        replicationNodeDetails = getRestTestHelper().getJsonAsSingletonList("/rest/replicationnode/" + _hostName + "/" + nodeName);
        assertLocalNode(nodeData, replicationNodeDetails);
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

    private Map<String, Object> waitForVirtualHostActivation(String hostName, long timeout) throws Exception
    {
        Map<String, Object> hostDetails = null;
        long startTime = System.currentTimeMillis();
        boolean isActive = false;
        do
        {
            hostDetails = getRestTestHelper().getJsonAsSingletonList("/rest/virtualhost/" + hostName);
            isActive = hostDetails.get(VirtualHost.STATE).equals(State.ACTIVE.name());
            Thread.sleep(100l);
        }
        while(!isActive && System.currentTimeMillis() - startTime < timeout );
        assertTrue("Unexpected virtual host state:" + hostDetails.get(VirtualHost.STATE), isActive);
        return hostDetails;
    }
}
