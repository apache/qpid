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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.systest.rest.QpidRestTestCase;
import org.apache.qpid.util.FileUtils;

public class ReplicationNodeRestTest extends QpidRestTestCase
{
    private static final String NODE_NAME = "node1";
    private static final String GROUP_NAME = "replication-group";

    private String _hostName;
    private File _storeFile;
    private int _haPort;
    private String _nodeRestUrl;
    private String _hostRestUrl;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _hostName = getTestName();
        _nodeRestUrl = "/rest/replicationnode/" + _hostName + "/" + NODE_NAME;
        _hostRestUrl = "/rest/virtualhost/" + _hostName;

        _storeFile = new File(TMP_FOLDER, "store-" + _hostName + "-" + System.currentTimeMillis());
        _haPort = findFreePort();

        Map<String, Object> hostData = new HashMap<String, Object>();
        hostData.put(VirtualHost.NAME, _hostName);
        hostData.put(VirtualHost.TYPE, BDBHAVirtualHostFactory.TYPE);
        hostData.put(VirtualHost.DESIRED_STATE, State.QUIESCED);

        int responseCode = getRestTestHelper().submitRequest(_hostRestUrl, "PUT", hostData);
        assertEquals("Unexpected response code for virtual host creation request", 201, responseCode);

        String hostPort = "localhost:" + _haPort;
        Map<String, Object> nodeData = new HashMap<String, Object>();
        nodeData.put(ReplicationNode.NAME, NODE_NAME);
        nodeData.put(ReplicationNode.GROUP_NAME, GROUP_NAME);
        nodeData.put(ReplicationNode.HOST_PORT, hostPort);
        nodeData.put(ReplicationNode.HELPER_HOST_PORT, hostPort);
        nodeData.put(ReplicationNode.STORE_PATH, _storeFile.getAbsolutePath());

        responseCode = getRestTestHelper().submitRequest(_nodeRestUrl, "PUT", nodeData);
        assertEquals("Unexpected response code for node creation request", 201, responseCode);

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

    public void testChangePriority() throws Exception
    {
        assertReplicationNodeSetAttribute(ReplicationNode.PRIORITY, 1, 2, 3);
    }

    public void testChangeQuorumOverride() throws Exception
    {
        assertReplicationNodeSetAttribute(ReplicationNode.QUORUM_OVERRIDE, 0, 1, 2);
    }

    public void testChangeDesignatedPrimary() throws Exception
    {
        assertReplicationNodeSetAttribute(ReplicationNode.DESIGNATED_PRIMARY, false, true, false);
    }

    public void testCreationOfSecondLocalReplicationNodeFails() throws Exception
    {
        String hostPort = "localhost:" + _haPort;
        Map<String, Object> nodeData = new HashMap<String, Object>();
        nodeData.put(ReplicationNode.NAME, NODE_NAME + 1);
        nodeData.put(ReplicationNode.GROUP_NAME, GROUP_NAME);
        nodeData.put(ReplicationNode.HOST_PORT, hostPort);
        nodeData.put(ReplicationNode.HELPER_HOST_PORT, hostPort);
        nodeData.put(ReplicationNode.STORE_PATH, _storeFile.getAbsolutePath());

        int responseCode = getRestTestHelper().submitRequest(_nodeRestUrl + 1, "PUT", nodeData);
        assertEquals("Adding of a second replication node should fail", 409, responseCode);
    }

    public void testUpdateImmutableAttributeWithTheSameValueSucceeds() throws Exception
    {
        String hostPort = "localhost:" + _haPort;
        Map<String, Object> nodeData = new HashMap<String, Object>();
        nodeData.put(ReplicationNode.NAME, NODE_NAME);
        nodeData.put(ReplicationNode.GROUP_NAME, GROUP_NAME);
        nodeData.put(ReplicationNode.HOST_PORT, hostPort);
        nodeData.put(ReplicationNode.HELPER_HOST_PORT, hostPort);
        nodeData.put(ReplicationNode.STORE_PATH, _storeFile.getAbsolutePath());

        int responseCode = getRestTestHelper().submitRequest(_nodeRestUrl, "PUT", nodeData);
        assertEquals("Update with unchanged attribute should succeed", 200, responseCode);
    }

    private void assertReplicationNodeSetAttribute(String attributeName, Object initialValue,
            Object newValueBeforeHostActivation, Object newValueAfterHostActivation) throws Exception
    {
        Map<String, Object> nodeAttributes = getRestTestHelper().getJsonAsSingletonList(_nodeRestUrl);
        assertEquals("Unexpected " + attributeName + " after creation", initialValue, nodeAttributes.get(attributeName));

        int responseCode = getRestTestHelper().submitRequest(_nodeRestUrl, "PUT", Collections.<String, Object>singletonMap(attributeName, newValueBeforeHostActivation));
        assertEquals("Unexpected response code for node " + attributeName + " update", 200, responseCode);

        waitForAttributeChanged(attributeName, newValueBeforeHostActivation);

        responseCode = getRestTestHelper().submitRequest(_hostRestUrl, "PUT", Collections.<String, Object>singletonMap(VirtualHost.DESIRED_STATE, State.ACTIVE));
        assertEquals("Unexpected response code for virtual host update status", 200, responseCode);

        waitForAttributeChanged(attributeName, newValueBeforeHostActivation);

        responseCode = getRestTestHelper().submitRequest(_nodeRestUrl, "PUT", Collections.<String, Object>singletonMap(attributeName, newValueAfterHostActivation));
        assertEquals("Unexpected response code for node " + attributeName + " update", 200, responseCode);

        waitForAttributeChanged(attributeName, newValueAfterHostActivation);
    }

    private void waitForAttributeChanged(String attributeName, Object newValue) throws Exception
    {
        Map<String, Object> nodeAttributes = getRestTestHelper().getJsonAsSingletonList(_nodeRestUrl);
        long limit = System.currentTimeMillis() + 5000;
        while(!newValue.equals(nodeAttributes.get(attributeName)) && System.currentTimeMillis() < limit)
        {
            Thread.sleep(100l);
            nodeAttributes = getRestTestHelper().getJsonAsSingletonList(_nodeRestUrl);
        }
        assertEquals("Unexpected attribute " + attributeName, newValue, nodeAttributes.get(attributeName));
    }
}
