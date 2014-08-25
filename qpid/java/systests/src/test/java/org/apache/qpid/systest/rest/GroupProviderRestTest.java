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
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Group;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.adapter.FileBasedGroupProvider;
import org.apache.qpid.server.model.adapter.FileBasedGroupProviderImpl;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.test.utils.TestFileUtils;

public class GroupProviderRestTest extends QpidRestTestCase
{
    private static final String FILE_GROUP_MANAGER = TestBrokerConfiguration.ENTRY_NAME_GROUP_FILE;
    private File _groupFile;

    @Override
    public void setUp() throws Exception
    {
        _groupFile = createTemporaryGroupFile();

        getBrokerConfiguration().addGroupFileConfiguration(_groupFile.getAbsolutePath());

        super.setUp();
    }

    @Override
    public void tearDown() throws Exception
    {
        super.tearDown();

        if (_groupFile != null)
        {
            if (_groupFile.exists())
            {
                _groupFile.delete();
            }
        }
    }

    public void testGet() throws Exception
    {
        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("groupprovider");
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());
        for (Map<String, Object> provider : providerDetails)
        {
            assertProvider(FILE_GROUP_MANAGER, FileBasedGroupProviderImpl.GROUP_FILE_PROVIDER_TYPE, provider);
            Map<String, Object> data = getRestTestHelper().getJsonAsSingletonList("groupprovider/"
                    + provider.get(GroupProvider.NAME));
            assertNotNull("Cannot load data for " + provider.get(GroupProvider.NAME), data);
            assertProvider(FILE_GROUP_MANAGER, FileBasedGroupProviderImpl.GROUP_FILE_PROVIDER_TYPE, data);
        }
    }

    public void testCreateNewGroup() throws Exception
    {
        String groupName = "newGroup";

        Map<String, Object> data = getRestTestHelper().getJsonAsSingletonList("groupprovider/" + FILE_GROUP_MANAGER);
        assertNotNull("Cannot load data for provider", data);

        getRestTestHelper().assertNumberOfGroups(data, 1);

        getRestTestHelper().createGroup(groupName, FILE_GROUP_MANAGER);

        data = getRestTestHelper().getJsonAsSingletonList("groupprovider/" + FILE_GROUP_MANAGER);
        assertNotNull("Cannot load data for provider", data);

        getRestTestHelper().assertNumberOfGroups(data, 2);
    }

    public void testRemoveGroup() throws Exception
    {
        String groupName = "myGroup";

        Map<String, Object> data = getRestTestHelper().getJsonAsSingletonList("groupprovider/" + FILE_GROUP_MANAGER);
        assertNotNull("Cannot load data for provider", data);

        getRestTestHelper().assertNumberOfGroups(data, 1);

        getRestTestHelper().removeGroup(groupName, FILE_GROUP_MANAGER);

        data = getRestTestHelper().getJsonAsSingletonList("groupprovider/" + FILE_GROUP_MANAGER);
        assertNotNull("Cannot load data for provider", data);

        getRestTestHelper().assertNumberOfGroups(data, 0);
    }

    public void testCreateNewFileGroupProviderFromExistingGroupFile() throws Exception
    {
        String[] groupMemberNames = {"test1","test2"};
        File groupFile = TestFileUtils.createTempFile(this, ".groups", "testusers.users=" + groupMemberNames[0] + "," + groupMemberNames[1]);
        try
        {
            String providerName = getTestName();
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(GroupProvider.NAME, providerName);
            attributes.put(GroupProvider.TYPE, FileBasedGroupProviderImpl.GROUP_FILE_PROVIDER_TYPE);
            attributes.put(FileBasedGroupProvider.PATH, groupFile.getAbsolutePath());

            int responseCode = getRestTestHelper().submitRequest("groupprovider/" + providerName, "PUT", attributes);
            assertEquals("Group provider was not created", 201, responseCode);

            Map<String, Object> data = getRestTestHelper().getJsonAsSingletonList("groupprovider/" + providerName + "?depth=2");
            assertProvider(providerName, FileBasedGroupProviderImpl.GROUP_FILE_PROVIDER_TYPE, data);
            assertEquals("Unexpected name", providerName, data.get(GroupProvider.NAME));
            assertEquals("Unexpected path", groupFile.getAbsolutePath(), data.get(FileBasedGroupProvider.PATH));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groups = (List<Map<String, Object>>) data.get("groups");
            assertEquals("Unexpected group size", 1, groups.size());
            Map<String, Object> group = groups.get(0);
            assertEquals("Unexpected group name", "testusers",group.get("name"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groupMemberList = (List<Map<String, Object>>) group.get("groupmembers");
            assertEquals("Unexpected group members size", 2, groupMemberList.size());

            for (String memberName : groupMemberNames)
            {
                boolean found = false;
                for (Map<String, Object> memberData : groupMemberList)
                {
                    Object name = memberData.get("name");
                    if (memberName.equals(name))
                    {
                        found = true;
                        break;
                    }
                }
                assertTrue("Cannot find group member " + memberName + " in " + groupMemberList , found);
            }
        }
        finally
        {
            groupFile.delete();
        }
    }

    public void testCreationOfNewFileGroupProviderFailsWhenPathIsMissed() throws Exception
    {
        String providerName = getTestName();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(GroupProvider.NAME, providerName);
        attributes.put(GroupProvider.TYPE, FileBasedGroupProviderImpl.GROUP_FILE_PROVIDER_TYPE);

        int responseCode = getRestTestHelper().submitRequest("groupprovider/" + providerName, "PUT", attributes);
        assertEquals("Group provider was created", 409, responseCode);
    }

    public void testCreateNewFileGroupProviderFromNonExistingGroupFile() throws Exception
    {
        File groupFile = new File(TMP_FOLDER + File.separator + getTestName() + File.separator + "groups");
        assertFalse("Group file should not exist", groupFile.exists());
        try
        {
            String providerName = getTestName();
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(GroupProvider.NAME, providerName);
            attributes.put(GroupProvider.TYPE, FileBasedGroupProviderImpl.GROUP_FILE_PROVIDER_TYPE);
            attributes.put(FileBasedGroupProvider.PATH, groupFile.getAbsolutePath());

            int responseCode = getRestTestHelper().submitRequest("groupprovider/" + providerName, "PUT", attributes);
            assertEquals("Group provider was not created", 201, responseCode);

            Map<String, Object> data = getRestTestHelper().getJsonAsSingletonList("groupprovider/" + providerName);
            assertEquals("Unexpected name", providerName, data.get(GroupProvider.NAME));
            assertEquals("Unexpected path", groupFile.getAbsolutePath(), data.get(FileBasedGroupProvider.PATH));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groups = (List<Map<String, Object>>) data.get("groups");
            assertNull("Unexpected groups", groups);

            assertTrue("Group file has not been created", groupFile.exists());
        }
        finally
        {
            groupFile.delete();
            groupFile.getParentFile().delete();
        }
    }

    public void testCreateNewFileGroupProviderForTheSameGroupFileFails() throws Exception
    {
        File groupFile = TestFileUtils.createTempFile(this, ".groups", "testusers.users=test1,test2");
        String providerName = getTestName();
        try
        {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(GroupProvider.NAME, providerName);
            attributes.put(GroupProvider.TYPE, FileBasedGroupProviderImpl.GROUP_FILE_PROVIDER_TYPE);
            attributes.put(FileBasedGroupProvider.PATH, groupFile.getAbsolutePath());

            int responseCode = getRestTestHelper().submitRequest("groupprovider/" + providerName, "PUT", attributes);
            assertEquals("Group provider was not created", 201, responseCode);

            attributes.put(GroupProvider.NAME, providerName + 2);
            responseCode = getRestTestHelper().submitRequest("groupprovider/" + providerName + 2, "PUT", attributes);
            assertEquals("Group provider for the same group file was created", 409, responseCode);
        }
        finally
        {
            groupFile.delete();
        }
    }

    public void testDeleteGroupProvider() throws Exception
    {
        File groupFile = TestFileUtils.createTempFile(this, ".groups", "testusers.users=test1,test2");
        String providerName = getTestName();
        try
        {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(GroupProvider.NAME, providerName);
            attributes.put(GroupProvider.TYPE, FileBasedGroupProviderImpl.GROUP_FILE_PROVIDER_TYPE);
            attributes.put(FileBasedGroupProvider.PATH, groupFile.getAbsolutePath());

            int responseCode = getRestTestHelper().submitRequest("groupprovider/" + providerName, "PUT", attributes);
            assertEquals("Expected to fail because we can have only one password provider", 201, responseCode);

            responseCode = getRestTestHelper().submitRequest("groupprovider/" + providerName , "DELETE");
            assertEquals("Group provider was not deleted", 200, responseCode);

            List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("groupprovider/" + providerName);
            assertEquals("Provider was not deleted", 0, providerDetails.size());
            assertFalse("Groups file should be deleted", groupFile.exists());
        }
        finally
        {
            groupFile.delete();
        }
    }

    public void testUpdateGroupProviderAttributesFails() throws Exception
    {
        File groupFile = TestFileUtils.createTempFile(this, ".groups", "testusers.users=test1,test2");
        String providerName = getTestName();
        try
        {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(GroupProvider.NAME, providerName);
            attributes.put(GroupProvider.TYPE, FileBasedGroupProviderImpl.GROUP_FILE_PROVIDER_TYPE);
            attributes.put(FileBasedGroupProvider.PATH, groupFile.getAbsolutePath());

            int responseCode = getRestTestHelper().submitRequest("groupprovider/" + providerName, "PUT", attributes);
            assertEquals("Expected to fail because we can have only one password provider", 201, responseCode);

            File newGroupFile = new File(TMP_FOLDER + File.separator + getTestName() + File.separator + "groups");
            attributes.put(FileBasedGroupProvider.PATH, newGroupFile.getAbsolutePath());

            responseCode = getRestTestHelper().submitRequest("groupprovider/" + providerName, "PUT", attributes);
            assertEquals("Expected to fail because we can have only one password provider", 409, responseCode);
        }
        finally
        {
            groupFile.delete();
        }
    }

    public void testRemovalOfGroupProviderInErrorStateUsingManagementMode() throws Exception
    {
        stopBroker();

        File file = new File(TMP_FOLDER, getTestName());
        if (file.exists())
        {
            file.delete();
        }
        assertFalse("Group file should not exist", file.exists());

        TestBrokerConfiguration config = getBrokerConfiguration();
        config.removeObjectConfiguration(GroupProvider.class, TestBrokerConfiguration.ENTRY_NAME_GROUP_FILE);
        UUID id = config.addGroupFileConfiguration(file.getAbsolutePath());
        config.setSaved(false);
        startBroker(0, true);

        getRestTestHelper().setUsernameAndPassword(BrokerOptions.MANAGEMENT_MODE_USER_NAME, MANAGEMENT_MODE_PASSWORD);

        Map<String, Object> groupProvider = getRestTestHelper().getJsonAsSingletonList("groupprovider/" + TestBrokerConfiguration.ENTRY_NAME_GROUP_FILE);
        assertEquals("Unexpected id", id.toString(), groupProvider.get(GroupProvider.ID));
        assertEquals("Unexpected path", file.getAbsolutePath() , groupProvider.get(FileBasedGroupProvider.PATH));
        assertEquals("Unexpected state", State.ERRORED.name() , groupProvider.get(GroupProvider.STATE));

        int status = getRestTestHelper().submitRequest("groupprovider/" + TestBrokerConfiguration.ENTRY_NAME_GROUP_FILE, "DELETE");
        assertEquals("ACL was not deleted", 200, status);

        List<Map<String, Object>> providers = getRestTestHelper().getJsonAsList("groupprovider/" + TestBrokerConfiguration.ENTRY_NAME_GROUP_FILE);
        assertEquals("Provider exists", 0, providers.size());
    }

    private void assertProvider(String name, String type, Map<String, Object> provider)
    {
        Asserts.assertAttributesPresent(provider, BrokerModel.getInstance().getTypeRegistry().getAttributeNames(
                GroupProvider.class),
                ConfiguredObject.TYPE,
                ConfiguredObject.CREATED_BY,
                ConfiguredObject.CREATED_TIME,
                ConfiguredObject.LAST_UPDATED_BY,
                ConfiguredObject.LAST_UPDATED_TIME,
                ConfiguredObject.DESCRIPTION,
                ConfiguredObject.CONTEXT,
                ConfiguredObject.DESIRED_STATE);
        assertEquals("Unexpected value of provider attribute " + GroupProvider.STATE, State.ACTIVE.name(),
                provider.get(GroupProvider.STATE));
        assertEquals("Unexpected value of provider attribute " + GroupProvider.LIFETIME_POLICY,
                LifetimePolicy.PERMANENT.name(), provider.get(GroupProvider.LIFETIME_POLICY));
        assertEquals("Unexpected value of provider attribute " + GroupProvider.DURABLE, Boolean.TRUE,
                provider.get(GroupProvider.DURABLE));
        assertEquals("Unexpected value of provider attribute " + GroupProvider.TYPE, type,
                provider.get(GroupProvider.TYPE));

        assertEquals("Unexpected value of provider attribute " + GroupProvider.NAME, name,
                (String) provider.get(GroupProvider.NAME));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) provider.get("groups");
        assertNotNull("Groups were not found", groups);
        assertEquals("Unexpected number of groups", 1, groups.size());
        for (Map<String, Object> group : groups)
        {

            final String groupName = (String) group.get(Group.NAME);
            assertNotNull("Attribute " + Group.NAME, groupName);

            assertNotNull("Attribute " + Group.ID, group.get(Group.ID));
        }
    }

    private File createTemporaryGroupFile() throws Exception
    {
        File groupFile = File.createTempFile("group", "grp");
        groupFile.deleteOnExit();

        Properties props = new Properties();
        props.put("myGroup.users", "guest");

        props.store(new FileOutputStream(groupFile), "test group file");

        return groupFile;
    }
}
