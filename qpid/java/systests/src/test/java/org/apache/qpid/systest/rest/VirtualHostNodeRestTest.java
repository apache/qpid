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
package org.apache.qpid.systest.rest;


import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.virtualhostnode.JsonVirtualHostNode;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

/**
 *
 * TODO: Add test to test the mutation of the storePath.  If the store path is mutated
 * whilst active then the store should be deleted next time we stop or close.
 */
public class VirtualHostNodeRestTest  extends QpidRestTestCase
{
    public void testGet() throws Exception
    {
        List<Map<String, Object>> virtualhostNodes = getRestTestHelper().getJsonAsList("virtualhostnode");
        assertNotNull("Virtualhostnodes data cannot be null", virtualhostNodes);
        assertEquals("Unexpected number of hosts", EXPECTED_VIRTUALHOSTS.length, virtualhostNodes.size());
        for (String nodeName : EXPECTED_VIRTUALHOSTS)
        {
            Map<String, Object> node = getRestTestHelper().find("name", nodeName, virtualhostNodes);
            Asserts.assertVirtualHostNode(nodeName, node);
        }
    }

    public void testCreateAndDeleteVirtualHostNode() throws Exception
    {
        String virtualhostNodeType = getTestProfileVirtualHostNodeType();
        String nodeName = "virtualhostnode-" + getTestName();
        File storePathAsFile = new File(getStoreLocation(nodeName));

        createAndDeleteVirtualHostNode(virtualhostNodeType, nodeName, storePathAsFile);
        assertFalse("Store should not exist after deletion", storePathAsFile.exists());
    }

    public void testCreateVirtualHostNodeWithDefaultStorePath() throws Exception
    {
        String virtualhostNodeType = getTestProfileVirtualHostNodeType();
        String nodeName = "virtualhostnode-" + getTestName();

        createVirtualHostNode(nodeName, virtualhostNodeType);

        String restUrl = "virtualhostnode/" + nodeName;
        Map<String, Object> virtualhostNode = getRestTestHelper().getJsonAsSingletonList(restUrl);
        Asserts.assertVirtualHostNode(nodeName, virtualhostNode);
        assertNull("Virtualhostnode should not automatically get a virtualhost child",
                   virtualhostNode.get("virtualhosts"));

        getRestTestHelper().submitRequest(restUrl, "DELETE", HttpServletResponse.SC_OK);

        List<Map<String, Object>> virtualHostNodes = getRestTestHelper().getJsonAsList(restUrl);
        assertEquals("Host should be deleted", 0, virtualHostNodes.size());
    }

    public void testRecoverVirtualHostNodeWithDesiredStateStopped() throws Exception
    {
        stopBroker();

        TestBrokerConfiguration config = getBrokerConfiguration();
        config.setObjectAttribute(VirtualHostNode.class, TEST3_VIRTUALHOST, ConfiguredObject.DESIRED_STATE, "STOPPED");
        config.setSaved(false);

        startBroker();

        String restUrl = "virtualhostnode/" + TEST3_VIRTUALHOST;
        assertActualAndDesireStates(restUrl, "STOPPED", "STOPPED");
    }

    public void testMutateState() throws Exception
    {
        String restUrl = "virtualhostnode/" + TEST3_VIRTUALHOST;

        assertActualAndDesireStates(restUrl, "ACTIVE", "ACTIVE");

        mutateVirtualHostNodeDesiredState(restUrl, "STOPPED");
        assertActualAndDesireStates(restUrl, "STOPPED", "STOPPED");

        mutateVirtualHostNodeDesiredState(restUrl, "ACTIVE");
        assertActualAndDesireStates(restUrl, "ACTIVE", "ACTIVE");
    }

    public void testMutateAttributes() throws Exception
    {
        String restUrl = "virtualhostnode/" + TEST3_VIRTUALHOST;

        Map<String, Object> virtualhostNode = getRestTestHelper().getJsonAsSingletonList(restUrl);
        assertNull(virtualhostNode.get(VirtualHostNode.DESCRIPTION));

        String newDescription = "My virtualhost node";
        Map<String, Object> newAttributes = new HashMap<String, Object>();
        newAttributes.put(VirtualHostNode.DESCRIPTION, newDescription);

        getRestTestHelper().submitRequest(restUrl, "PUT", newAttributes, HttpServletResponse.SC_OK);

        virtualhostNode = getRestTestHelper().getJsonAsSingletonList(restUrl);
        assertEquals(newDescription, virtualhostNode.get(VirtualHostNode.DESCRIPTION));
    }

    private void createAndDeleteVirtualHostNode(final String virtualhostNodeType,
                                                final String nodeName,
                                                final File storePathAsFile) throws Exception
    {
        assertFalse("Store should not exist", storePathAsFile.exists());

        createVirtualHostNode(nodeName, storePathAsFile.getAbsolutePath(), virtualhostNodeType);
        assertTrue("Store should exist after creation of node", storePathAsFile.exists());

        String restUrl = "virtualhostnode/" + nodeName;
        Map<String, Object> virtualhostNode = getRestTestHelper().getJsonAsSingletonList(restUrl);
        Asserts.assertVirtualHostNode(nodeName, virtualhostNode);
        assertNull("Virtualhostnode should not automatically get a virtualhost child",
                   virtualhostNode.get("virtualhosts"));

        getRestTestHelper().submitRequest(restUrl, "DELETE", HttpServletResponse.SC_OK);

        List<Map<String, Object>> virtualHostNodes = getRestTestHelper().getJsonAsList(restUrl);
        assertEquals("Host should be deleted", 0, virtualHostNodes.size());
    }

    private void assertActualAndDesireStates(final String restUrl,
                                             final String expectedDesiredState,
                                             final String expectedActualState) throws IOException
    {
        Map<String, Object> virtualhostNode = getRestTestHelper().getJsonAsSingletonList(restUrl);
        Asserts.assertActualAndDesiredState(expectedDesiredState, expectedActualState, virtualhostNode);
    }

    private void mutateVirtualHostNodeDesiredState(final String restUrl, final String newState) throws IOException
    {
        Map<String, Object> newAttributes = new HashMap<String, Object>();
        newAttributes.put(VirtualHostNode.DESIRED_STATE, newState);

        getRestTestHelper().submitRequest(restUrl, "PUT", newAttributes, HttpServletResponse.SC_OK);
    }

    private void createVirtualHostNode(String nodeName, String configStorePath, final String storeType) throws Exception
    {
        Map<String, Object> nodeData = new HashMap<String, Object>();
        nodeData.put(VirtualHostNode.NAME, nodeName);
        nodeData.put(VirtualHostNode.TYPE, storeType);
        if (configStorePath != null)
        {
            nodeData.put(JsonVirtualHostNode.STORE_PATH, configStorePath);
        }

        getRestTestHelper().submitRequest("virtualhostnode/" + nodeName,
                                          "PUT",
                                          nodeData,
                                          HttpServletResponse.SC_CREATED);
    }

    private void createVirtualHostNode(String nodeName, final String storeType) throws Exception
    {
        createVirtualHostNode(nodeName, null, storeType);
    }

    private String getStoreLocation(String hostName)
    {
        return new File(TMP_FOLDER, "store-" + hostName + "-" + System.currentTimeMillis()).getAbsolutePath();
    }
}
