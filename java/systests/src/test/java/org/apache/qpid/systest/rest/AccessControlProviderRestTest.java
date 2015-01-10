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
import java.util.UUID;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.model.AccessControlProvider;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.security.access.FileAccessControlProviderConstants;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.test.utils.TestFileUtils;

public class AccessControlProviderRestTest extends QpidRestTestCase
{
    private static final String ALLOWED_USER = "allowed";
    private static final String DENIED_USER = "denied";
    private static final String OTHER_USER = "other";

    private String  _aclFileContent1 =
                          "ACL ALLOW-LOG " + ALLOWED_USER + " ACCESS MANAGEMENT\n" +
                          "ACL ALLOW-LOG " + ALLOWED_USER + " CONFIGURE BROKER\n" +
                          "ACL DENY-LOG ALL ALL";

    private String  _aclFileContent2 =
                          "ACL ALLOW-LOG " + ALLOWED_USER + " ACCESS MANAGEMENT\n" +
                          "ACL ALLOW-LOG " + OTHER_USER + " ACCESS MANAGEMENT\n" +
                          "ACL ALLOW-LOG " + ALLOWED_USER + " CONFIGURE BROKER\n" +
                          "ACL DENY-LOG ALL ALL";

    @Override
    protected void customizeConfiguration() throws IOException
    {
        super.customizeConfiguration();
        getRestTestHelper().configureTemporaryPasswordFile(this, ALLOWED_USER, DENIED_USER, OTHER_USER);

        getBrokerConfiguration().setObjectAttribute(Plugin.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT,
                HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, true);
    }

    public void testCreateAccessControlProvider() throws Exception
    {
        String accessControlProviderName = getTestName();

        //verify that the access control provider doesn't exist, and
        //in doing so implicitly verify that the 'denied' user can
        //actually currently connect because no ACL is in effect yet
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);
        assertAccessControlProviderExistence(accessControlProviderName, false);

        //create the access control provider using the 'allowed' user
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);
        int responseCode = createAccessControlProvider(accessControlProviderName, _aclFileContent1);
        assertEquals("Access control provider creation should be allowed", 201, responseCode);

        //verify it exists with the 'allowed' user
        assertAccessControlProviderExistence(accessControlProviderName, true);

        //verify the 'denied' user can no longer access the management interface
        //due to the just-created ACL file now preventing it
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);
        assertCanAccessManagementInterface(accessControlProviderName, false);
    }

    public void testRemoveAccessControlProvider() throws Exception
    {
        String accessControlProviderName = getTestName();

        //verify that the access control provider doesn't exist, and
        //in doing so implicitly verify that the 'denied' user can
        //actually currently connect because no ACL is in effect yet
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);
        assertAccessControlProviderExistence(accessControlProviderName, false);

        //create the access control provider using the 'allowed' user
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);
        int responseCode = createAccessControlProvider(accessControlProviderName, _aclFileContent1);
        assertEquals("Access control provider creation should be allowed", 201, responseCode);

        //verify it exists with the 'allowed' user
        assertAccessControlProviderExistence(accessControlProviderName, true);

        //verify the 'denied' user can no longer access the management interface
        //due to the just-created ACL file now preventing it
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);
        assertCanAccessManagementInterface(accessControlProviderName, false);

        //remove the access control provider using the 'allowed' user
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        responseCode = getRestTestHelper().submitRequest("accesscontrolprovider/" + accessControlProviderName, "DELETE");
        assertEquals("Access control provider deletion should be allowed", 200, responseCode);
        assertAccessControlProviderExistence(accessControlProviderName, false);

        //verify it is gone again, using the 'denied' user to implicitly confirm it is
        //now able to connect to the management interface again because the ACL was removed.
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);
        assertAccessControlProviderExistence(accessControlProviderName, false);
    }

    public void testReplaceAccessControlProvider() throws Exception
    {


        //create the access control provider using the 'allowed' user
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);
        int responseCode = createAccessControlProvider(getTestName(), _aclFileContent1);
        assertEquals("Access control provider creation should be allowed", 201, responseCode);

        //verify it exists with the 'allowed' user
        assertAccessControlProviderExistence(getTestName(), true);

        //verify the 'denied' and 'other' user can no longer access the management
        //interface due to the just-created ACL file now preventing them
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);
        assertCanAccessManagementInterface(getTestName(), false);
        getRestTestHelper().setUsernameAndPassword(OTHER_USER, OTHER_USER);
        assertCanAccessManagementInterface(getTestName(), false);

        //create the replacement access control provider using the 'allowed' user.
        String accessControlProviderName2 = getTestName() + "2";
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);
        responseCode = createAccessControlProvider(getTestName(), _aclFileContent2);
        assertEquals("Access control provider creation should be allowed", 200, responseCode);

        //Verify that it took effect immediately, replacing the first access control provider

        //verify the 'denied' user still can't access the management interface, but the 'other' user now CAN.
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);
        assertCanAccessManagementInterface(accessControlProviderName2, false);
        getRestTestHelper().setUsernameAndPassword(OTHER_USER, OTHER_USER);
        assertCanAccessManagementInterface(accessControlProviderName2, true);


        //verify the 'denied' user still can't access the management interface, the 'other' user still can, thus
        //confirming that the second access control provider is still in effect
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);
        assertCanAccessManagementInterface(accessControlProviderName2, false);
        getRestTestHelper().setUsernameAndPassword(OTHER_USER, OTHER_USER);
        assertCanAccessManagementInterface(accessControlProviderName2, true);
    }



    public void testRemovalOfAccessControlProviderInErrorStateUsingManagementMode() throws Exception
    {
        stopBroker();

        File file = new File(TMP_FOLDER, getTestName());
        if (file.exists())
        {
            file.delete();
        }
        assertFalse("ACL file should not exist", file.exists());
        UUID id = getBrokerConfiguration().addAclFileConfiguration(file.getAbsolutePath());
        getBrokerConfiguration().setSaved(false);
        startBroker(0, true);

        getRestTestHelper().setUsernameAndPassword(BrokerOptions.MANAGEMENT_MODE_USER_NAME, MANAGEMENT_MODE_PASSWORD);

        Map<String, Object> acl = getRestTestHelper().getJsonAsSingletonList("accesscontrolprovider/" + TestBrokerConfiguration.ENTRY_NAME_ACL_FILE);
        assertEquals("Unexpected id", id.toString(), acl.get(AccessControlProvider.ID));
        assertEquals("Unexpected path", file.getAbsolutePath() , acl.get(FileAccessControlProviderConstants.PATH));
        assertEquals("Unexpected state", State.ERRORED.name() , acl.get(AccessControlProvider.STATE));

        int status = getRestTestHelper().submitRequest("accesscontrolprovider/" + TestBrokerConfiguration.ENTRY_NAME_ACL_FILE, "DELETE");
        assertEquals("ACL was not deleted", 200, status);

        List<Map<String, Object>> acls = getRestTestHelper().getJsonAsList("accesscontrolprovider/" + TestBrokerConfiguration.ENTRY_NAME_ACL_FILE);
        assertEquals("ACL exists", 0, acls.size());
    }

    private void assertCanAccessManagementInterface(String accessControlProviderName, boolean canAccess) throws Exception
    {
        int expected = canAccess ? 200 : 403;
        int responseCode = getRestTestHelper().submitRequest("accesscontrolprovider/" + accessControlProviderName, "GET");
        assertEquals("Unexpected response code", expected, responseCode);
    }

    private void assertAccessControlProviderExistence(String accessControlProviderName, boolean exists) throws Exception
    {
        String path = "accesscontrolprovider/" + accessControlProviderName;
        List<Map<String, Object>> providers = getRestTestHelper().getJsonAsList(path);
        assertEquals("Unexpected result", exists, !providers.isEmpty());
    }

    private int createAccessControlProvider(String accessControlProviderName, String content) throws Exception
    {
        File file = TestFileUtils.createTempFile(this, ".acl", content);
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AccessControlProvider.NAME, accessControlProviderName);
        attributes.put(AccessControlProvider.TYPE, FileAccessControlProviderConstants.ACL_FILE_PROVIDER_TYPE);
        attributes.put(FileAccessControlProviderConstants.PATH, file.getAbsoluteFile());

        return getRestTestHelper().submitRequest("accesscontrolprovider/" + accessControlProviderName, "PUT", attributes);
    }
}
