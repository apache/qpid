/*
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
 */

package org.apache.qpid.systest.rest.acl;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.security.acl.AbstractACLTestCase;
import org.apache.qpid.server.virtualhost.ProvidedStoreVirtualHostImpl;
import org.apache.qpid.server.virtualhostnode.JsonVirtualHostNode;
import org.apache.qpid.systest.rest.QpidRestTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class VirtualHostACLTest extends QpidRestTestCase
{
    private static final String VHN_WITHOUT_VH = "myVhnWithoutVh";

    private static final String ALLOWED_USER = "user1";
    private static final String DENIED_USER = "user2";

    @Override
    protected void customizeConfiguration() throws IOException
    {
        super.customizeConfiguration();
        getRestTestHelper().configureTemporaryPasswordFile(this, ALLOWED_USER, DENIED_USER);

        AbstractACLTestCase.writeACLFileUtil(this, "ACL ALLOW-LOG ALL ACCESS MANAGEMENT",
                "ACL ALLOW-LOG " + ALLOWED_USER + " ALL VIRTUALHOST",
                "ACL DENY-LOG " + DENIED_USER + " ALL VIRTUALHOST",
                "ACL DENY-LOG ALL ALL");

        getBrokerConfiguration().setObjectAttribute(Plugin.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT,
                HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, true);

        Map<String, Object> virtualHostNodeAttributes = new HashMap<>();
        virtualHostNodeAttributes.put(VirtualHostNode.NAME, VHN_WITHOUT_VH);
        virtualHostNodeAttributes.put(VirtualHostNode.TYPE, getTestProfileVirtualHostNodeType());
        // TODO need better way to determine the VHN's optional attributes
        virtualHostNodeAttributes.put(JsonVirtualHostNode.STORE_PATH, getStoreLocation(VHN_WITHOUT_VH));

        getBrokerConfiguration().addObjectConfiguration(VirtualHostNode.class, virtualHostNodeAttributes);
    }

    public void testCreateVirtualHostAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String hostName = getTestName();

        int responseCode = createVirtualHost(VHN_WITHOUT_VH, hostName);
        assertEquals("Virtual host creation should be allowed", HttpServletResponse.SC_CREATED, responseCode);

        assertVirtualHostExists(VHN_WITHOUT_VH, hostName);
    }

    public void testCreateVirtualHostDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        String hostName = getTestName();

        int responseCode = createVirtualHost(VHN_WITHOUT_VH, hostName);
        assertEquals("Virtual host creation should be denied", HttpServletResponse.SC_FORBIDDEN, responseCode);

        assertVirtualHostDoesNotExist(VHN_WITHOUT_VH, hostName);
    }

    public void testDeleteVirtualHostDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);
        getRestTestHelper().submitRequest("virtualhost/" + TEST2_VIRTUALHOST + "/" + TEST2_VIRTUALHOST, "DELETE", HttpServletResponse.SC_FORBIDDEN);

        assertVirtualHostExists(TEST2_VIRTUALHOST, TEST2_VIRTUALHOST);
    }

    public void testUpdateVirtualHostDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(VirtualHost.NAME, TEST2_VIRTUALHOST);
        attributes.put(VirtualHost.DESCRIPTION, "new description");

        getRestTestHelper().submitRequest("virtualhost/" + TEST2_VIRTUALHOST + "/" + TEST2_VIRTUALHOST, "PUT", attributes, HttpServletResponse.SC_FORBIDDEN);
    }

    /* === Utility Methods === */

    private int createVirtualHost(final String testVirtualHostNode, String virtualHostName) throws Exception
    {
        Map<String, Object> data = new HashMap<>();
        data.put(VirtualHost.NAME, virtualHostName);
        data.put(VirtualHost.TYPE, ProvidedStoreVirtualHostImpl.VIRTUAL_HOST_TYPE);

        return getRestTestHelper().submitRequest("virtualhost/" + testVirtualHostNode + "/" + virtualHostName, "PUT", data);
    }

    private void assertVirtualHostDoesNotExist(final String virtualHostNodeName, String virtualHostName) throws Exception
    {
        assertVirtualHostExistence(virtualHostNodeName, virtualHostName, false);
    }

    private void assertVirtualHostExists(final String virtualHostNodeName, String virtualHostName) throws Exception
    {
        assertVirtualHostExistence(virtualHostNodeName, virtualHostName, true);
    }

    private void assertVirtualHostExistence(final String virtualHostNodeName, String virtualHostName, boolean exists) throws Exception
    {
        List<Map<String, Object>> hosts = getRestTestHelper().getJsonAsList("virtualhost/" + virtualHostNodeName + "/" + virtualHostName);
        assertEquals("Node " + virtualHostName + (exists ? " does not exist" : " exists"), exists, !hosts.isEmpty());
    }

    private String getStoreLocation(String hostName)
    {
        return new File(TMP_FOLDER, "store-" + hostName + "-" + System.currentTimeMillis()).getAbsolutePath();
    }

}
