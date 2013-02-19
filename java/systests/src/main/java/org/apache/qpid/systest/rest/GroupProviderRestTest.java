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
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Group;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.UUIDGenerator;

public class GroupProviderRestTest extends QpidRestTestCase
{
    private static final String FILE_GROUP_MANAGER = "FileGroupManager";
    private File _groupFile;

    @Override
    public void setUp() throws Exception
    {
        _groupFile = createTemporaryGroupFile();

        getBrokerConfiguration().setBrokerAttribute(Broker.GROUP_FILE, _groupFile.getAbsolutePath());

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
        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("/rest/groupprovider");
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());
        for (Map<String, Object> provider : providerDetails)
        {
            assertProvider(FILE_GROUP_MANAGER, provider);
            Map<String, Object> data = getRestTestHelper().getJsonAsSingletonList("/rest/groupprovider/"
                    + provider.get(GroupProvider.NAME));
            assertNotNull("Cannot load data for " + provider.get(GroupProvider.NAME), data);
            assertProvider(FILE_GROUP_MANAGER, data);
        }
    }

    public void testCreateNewGroup() throws Exception
    {
        String groupName = "newGroup";

        Map<String, Object> data = getRestTestHelper().getJsonAsSingletonList("/rest/groupprovider/" + FILE_GROUP_MANAGER);
        assertNotNull("Cannot load data for provider", data);

        getRestTestHelper().assertNumberOfGroups(data, 1);

        getRestTestHelper().createGroup(groupName, FILE_GROUP_MANAGER);

        data = getRestTestHelper().getJsonAsSingletonList("/rest/groupprovider/" + FILE_GROUP_MANAGER);
        assertNotNull("Cannot load data for provider", data);

        getRestTestHelper().assertNumberOfGroups(data, 2);
    }

    public void testRemoveGroup() throws Exception
    {
        String groupName = "myGroup";

        Map<String, Object> data = getRestTestHelper().getJsonAsSingletonList("/rest/groupprovider/" + FILE_GROUP_MANAGER);
        assertNotNull("Cannot load data for provider", data);

        getRestTestHelper().assertNumberOfGroups(data, 1);

        getRestTestHelper().removeGroup(groupName, FILE_GROUP_MANAGER);

        data = getRestTestHelper().getJsonAsSingletonList("/rest/groupprovider/" + FILE_GROUP_MANAGER);
        assertNotNull("Cannot load data for provider", data);

        getRestTestHelper().assertNumberOfGroups(data, 0);
    }


    private void assertProvider(String type, Map<String, Object> provider)
    {
        Asserts.assertAttributesPresent(provider, GroupProvider.AVAILABLE_ATTRIBUTES,
                GroupProvider.CREATED, GroupProvider.UPDATED, GroupProvider.DESCRIPTION,
                GroupProvider.TIME_TO_LIVE);
        assertEquals("Unexpected value of provider attribute " + GroupProvider.STATE, State.ACTIVE.name(),
                provider.get(GroupProvider.STATE));
        assertEquals("Unexpected value of provider attribute " + GroupProvider.LIFETIME_POLICY,
                LifetimePolicy.PERMANENT.name(), provider.get(GroupProvider.LIFETIME_POLICY));
        assertEquals("Unexpected value of provider attribute " + GroupProvider.DURABLE, Boolean.TRUE,
                provider.get(GroupProvider.DURABLE));
        assertEquals("Unexpected value of provider attribute " + GroupProvider.TYPE, type,
                provider.get(GroupProvider.TYPE));

        final String name = (String) provider.get(GroupProvider.NAME);
        assertEquals("Unexpected value of provider attribute " + GroupProvider.NAME, type,
                name);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) provider.get("groups");
        assertNotNull("Groups were not found", groups);
        assertEquals("Unexpected number of groups", 1, groups.size());
        for (Map<String, Object> group : groups)
        {

            final String groupName = (String) group.get(Group.NAME);
            assertNotNull("Attribute " + Group.NAME, groupName);

            assertNotNull("Attribute " + Group.ID, group.get(Group.ID));
            assertEquals("Attribute " + Group.ID, UUIDGenerator.generateGroupUUID(name, groupName).toString(), group.get(Group.ID));
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
