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
import org.apache.qpid.server.model.GroupMember;

public class GroupRestTest extends QpidRestTestCase
{
    private static final String GROUP_NAME = "myGroup";
    private static final String FILE_GROUP_MANAGER = "FileGroupManager";
    private static final String EXISTING_MEMBER = "user1";
    private static final String NEW_MEMBER = "user2";

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
        Map<String, Object> group = getRestTestHelper().getJsonAsSingletonList("/rest/group/FileGroupManager/myGroup");
        List<Map<String, Object>> groupmembers = (List<Map<String, Object>>) group.get("groupmembers");
        assertEquals(1, groupmembers.size());

        Map<String, Object> member1 = groupmembers.get(0);
        assertEquals(EXISTING_MEMBER, (String)member1.get(GroupMember.NAME));
    }

    public void testCreateNewMemberOfGroup() throws Exception
    {
        Map<String, Object> group = getRestTestHelper().getJsonAsSingletonList("/rest/group/FileGroupManager/myGroup");
        getRestTestHelper().assertNumberOfGroupMembers(group, 1);

        getRestTestHelper().createNewGroupMember(FILE_GROUP_MANAGER, GROUP_NAME, NEW_MEMBER);

        group = getRestTestHelper().getJsonAsSingletonList("/rest/group/FileGroupManager/myGroup");
        getRestTestHelper().assertNumberOfGroupMembers(group, 2);
    }

    public void testRemoveMemberFromGroup() throws Exception
    {
        Map<String, Object> group = getRestTestHelper().getJsonAsSingletonList("/rest/group/FileGroupManager/myGroup");
        getRestTestHelper().assertNumberOfGroupMembers(group, 1);

        getRestTestHelper().removeMemberFromGroup(FILE_GROUP_MANAGER, GROUP_NAME, EXISTING_MEMBER);

        group = getRestTestHelper().getJsonAsSingletonList("/rest/group/FileGroupManager/myGroup");
        getRestTestHelper().assertNumberOfGroupMembers(group, 0);
    }

    private File createTemporaryGroupFile() throws Exception
    {
        File groupFile = File.createTempFile("group", "grp");
        groupFile.deleteOnExit();

        Properties props = new Properties();
        props.put(GROUP_NAME + ".users", EXISTING_MEMBER);

        props.store(new FileOutputStream(groupFile), "test group file");

        return groupFile;
    }
}
