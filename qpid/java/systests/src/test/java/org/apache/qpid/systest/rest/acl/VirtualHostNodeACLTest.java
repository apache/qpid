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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;

import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.model.AccessControlProvider;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ExternalFileBasedAuthenticationManager;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.model.adapter.FileBasedGroupProvider;
import org.apache.qpid.server.model.adapter.FileBasedGroupProviderImpl;
import org.apache.qpid.server.security.FileKeyStore;
import org.apache.qpid.server.security.FileTrustStore;
import org.apache.qpid.server.security.access.FileAccessControlProviderConstants;
import org.apache.qpid.server.security.acl.AbstractACLTestCase;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PlainPasswordDatabaseAuthenticationManager;
import org.apache.qpid.server.virtualhost.memory.MemoryVirtualHost;
import org.apache.qpid.server.virtualhostnode.JsonVirtualHostNode;
import org.apache.qpid.systest.rest.QpidRestTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.test.utils.TestSSLConstants;

public class VirtualHostNodeACLTest extends QpidRestTestCase
{
    private static final String TEST_VIRTUAL_HOST_NODE = "myTestVirtualHostNode";
    private static final String ALLOWED_USER = "user1";
    private static final String DENIED_USER = "user2";

    @Override
    protected void customizeConfiguration() throws IOException
    {
        super.customizeConfiguration();
        getRestTestHelper().configureTemporaryPasswordFile(this, ALLOWED_USER, DENIED_USER);

        AbstractACLTestCase.writeACLFileUtil(this, "ACL ALLOW-LOG ALL ACCESS MANAGEMENT",
                "ACL ALLOW-LOG " + ALLOWED_USER + " ALL VIRTUALHOSTNODE",
                "ACL DENY-LOG " + DENIED_USER + " ALL VIRTUALHOSTNODE",
                "ACL DENY-LOG ALL ALL");

        getBrokerConfiguration().setObjectAttribute(Plugin.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT,
                HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, true);

        Map<String, Object> virtualHostNodeAttributes = new HashMap<>();
        virtualHostNodeAttributes.put(VirtualHostNode.NAME, TEST_VIRTUAL_HOST_NODE);
        virtualHostNodeAttributes.put(VirtualHostNode.TYPE, getTestProfileVirtualHostNodeType());
        // TODO need better way to determine the VHN's optional attributes
        virtualHostNodeAttributes.put(JsonVirtualHostNode.STORE_PATH, getStoreLocation(TEST_VIRTUAL_HOST_NODE));


        getBrokerConfiguration().addObjectConfiguration(VirtualHostNode.class, virtualHostNodeAttributes);
    }

    public void testCreateVirtualHostNodeAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String hostName = getTestName();

        int responseCode = createVirtualHostNode(hostName);
        assertEquals("Virtual host node creation should be allowed", HttpServletResponse.SC_CREATED, responseCode);

        assertVirtualHostNodeExists(hostName);
    }

    public void testCreateVirtualHostNodeDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        String hostName = getTestName();

        int responseCode = createVirtualHostNode(hostName);
        assertEquals("Virtual host node creation should be denied", HttpServletResponse.SC_FORBIDDEN, responseCode);

        assertVirtualHostNodeDoesNotExist(hostName);
    }

    public void testDeleteVirtualHostNodeDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);
        getRestTestHelper().submitRequest("virtualhostnode/" + TEST_VIRTUAL_HOST_NODE, "DELETE", HttpServletResponse.SC_FORBIDDEN);

        assertVirtualHostNodeExists(TEST_VIRTUAL_HOST_NODE);
    }

    /* === Utility Methods === */

    private int createVirtualHostNode(String virtualHostNodeName) throws Exception
    {
        Map<String, Object> data = new HashMap<>();
        data.put(VirtualHostNode.NAME, virtualHostNodeName);
        data.put(VirtualHostNode.TYPE, getTestProfileVirtualHostNodeType());
        data.put(JsonVirtualHostNode.STORE_PATH, getStoreLocation(virtualHostNodeName));

        return getRestTestHelper().submitRequest("virtualhostnode/" + virtualHostNodeName, "PUT", data);
    }

    private void assertVirtualHostNodeDoesNotExist(String name) throws Exception
    {
        assertVirtualHostNodeExistence(name, false);
    }

    private void assertVirtualHostNodeExists(String name) throws Exception
    {
        assertVirtualHostNodeExistence(name, true);
    }

    private void assertVirtualHostNodeExistence(String name, boolean exists) throws Exception
    {
        List<Map<String, Object>> hosts = getRestTestHelper().getJsonAsList("virtualhostnode/" + name);
        assertEquals("Node " + name + (exists ? " does not exist" : " exists"), exists, !hosts.isEmpty());
    }

    private String getStoreLocation(String hostName)
    {
        return new File(TMP_FOLDER, "store-" + hostName + "-" + System.currentTimeMillis()).getAbsolutePath();
    }

}
