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
 *
 */
package org.apache.qpid.server.security.group;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;

import org.apache.qpid.server.security.group.FileGroupDatabase;

import junit.framework.TestCase;

public class FileGroupDatabaseTest extends TestCase
{
    private static final String USER1 = "user1";
    private static final String USER2 = "user2";
    private static final String USER3 = "user3";

    private static final String MY_GROUP = "myGroup";
    private static final String MY_GROUP2 = "myGroup2";
    private static final String MY_GROUP1 = "myGroup1";

    private FileGroupDatabase _groupDatabase = new FileGroupDatabase();
    private String _groupFile;

    public void testGetAllGroups() throws Exception
    {
        writeAndSetGroupFile("myGroup.users", USER1);

        Set<String> groups = _groupDatabase.getAllGroups();
        assertEquals(1, groups.size());
        assertTrue(groups.contains(MY_GROUP));
    }

    public void testGetAllGroupsWhenGroupFileEmpty() throws Exception
    {
        _groupDatabase.setGroupFile(_groupFile);

        Set<String> groups = _groupDatabase.getAllGroups();
        assertEquals(0, groups.size());
    }

    public void testMissingGroupFile() throws Exception
    {
        try
        {
            _groupDatabase.setGroupFile("/not/a/file");
            fail("Exception not thrown");
        }
        catch (FileNotFoundException fnfe)
        {
            // PASS
        }
    }

    public void testInvalidFormat() throws Exception
    {
        writeGroupFile("name.notvalid", USER1);

        try
        {
            _groupDatabase.setGroupFile(_groupFile);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException gde)
        {
            // PASS
        }
    }

    public void testGetUsersInGroup() throws Exception
    {
        writeGroupFile("myGroup.users", "user1,user2,user3");

        _groupDatabase.setGroupFile(_groupFile);

        Set<String> users = _groupDatabase.getUsersInGroup(MY_GROUP);
        assertNotNull(users);
        assertEquals(3, users.size());
    }

    public void testDuplicateUsersInGroupAreConflated() throws Exception
    {
        writeAndSetGroupFile("myGroup.users", "user1,user1,user3,user1");

        Set<String> users = _groupDatabase.getUsersInGroup(MY_GROUP);
        assertNotNull(users);
        assertEquals(2, users.size());
    }

    public void testGetUsersWithEmptyGroup() throws Exception
    {
        writeAndSetGroupFile("myGroup.users", "");

        Set<String> users = _groupDatabase.getUsersInGroup(MY_GROUP);
        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    public void testGetUsersInNonExistentGroup() throws Exception
    {
        writeAndSetGroupFile("myGroup.users", "user1,user2,user3");

        Set<String> users = _groupDatabase.getUsersInGroup("groupDoesntExist");
        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    public void testGetUsersInNullGroup() throws Exception
    {
        writeAndSetGroupFile();
        assertTrue(_groupDatabase.getUsersInGroup(null).isEmpty());
    }

    public void testGetGroupPrincipalsForUserWhenUserBelongsToOneGroup() throws Exception
    {
        writeAndSetGroupFile("myGroup.users", "user1,user2");
        Set<String> groups = _groupDatabase.getGroupsForUser(USER1);
        assertEquals(1, groups.size());
        assertTrue(groups.contains(MY_GROUP));
    }

    public void testGetGroupPrincipalsForUserWhenUserBelongsToTwoGroup() throws Exception
    {
        writeAndSetGroupFile("myGroup1.users", "user1,user2",
                             "myGroup2.users", "user1,user3");
        Set<String> groups = _groupDatabase.getGroupsForUser(USER1);
        assertEquals(2, groups.size());
        assertTrue(groups.contains(MY_GROUP1));
        assertTrue(groups.contains(MY_GROUP2));
    }

    public void testGetGroupPrincipalsForUserWhenUserAddedToGroup() throws Exception
    {
        writeAndSetGroupFile("myGroup1.users", "user1,user2",
                             "myGroup2.users", USER2);
        Set<String> groups = _groupDatabase.getGroupsForUser(USER1);
        assertEquals(1, groups.size());
        assertTrue(groups.contains(MY_GROUP1));

        _groupDatabase.addUserToGroup(USER1, MY_GROUP2);

        groups = _groupDatabase.getGroupsForUser(USER1);
        assertEquals(2, groups.size());
        assertTrue(groups.contains(MY_GROUP1));
        assertTrue(groups.contains(MY_GROUP2));

        Set<String> users =  _groupDatabase.getUsersInGroup(MY_GROUP2);
        assertEquals(2, users.size());
        assertTrue(users.contains(USER1));
        assertTrue(users.contains(USER2));
    }

    public void testGetGroupPrincipalsForUserWhenUserRemovedFromGroup() throws Exception
    {
        writeAndSetGroupFile("myGroup1.users", "user1,user2",
                             "myGroup2.users", "user1,user2");
        Set<String> groups = _groupDatabase.getGroupsForUser(USER1);
        assertEquals(2, groups.size());
        assertTrue(groups.contains(MY_GROUP1));
        assertTrue(groups.contains(MY_GROUP2));

        _groupDatabase.removeUserFromGroup(USER1, MY_GROUP2);

        groups = _groupDatabase.getGroupsForUser(USER1);
        assertEquals(1, groups.size());
        assertTrue(groups.contains(MY_GROUP1));
    }

    public void testGetGroupPrincipalsForUserWhenUserAdddedToGroupTheyAreAlreadyIn() throws Exception
    {
        writeAndSetGroupFile("myGroup.users", USER1);
        _groupDatabase.addUserToGroup(USER1, MY_GROUP);

        Set<String> groups = _groupDatabase.getGroupsForUser(USER1);

        assertEquals(1, groups.size());
        assertTrue(groups.contains(MY_GROUP));

        Set<String> users = _groupDatabase.getUsersInGroup(MY_GROUP);
        assertEquals(1, users.size());
        assertTrue(users.contains(USER1));
    }

    public void testGetGroupPrincipalsForUserWhenUserNotKnown() throws Exception
    {
        writeAndSetGroupFile("myGroup.users", "user1,user2");
        Set<String> groups = _groupDatabase.getGroupsForUser(USER3);
        assertEquals(0, groups.size());
    }

    public void testGetGroupPrincipalsForNullUser() throws Exception
    {
        writeAndSetGroupFile();
        assertTrue(_groupDatabase.getGroupsForUser(null).isEmpty());
    }

    public void testAddUserToExistingGroup() throws Exception
    {
        writeAndSetGroupFile("myGroup.users", "user1,user2");

        Set<String> users = _groupDatabase.getUsersInGroup(MY_GROUP);
        assertNotNull(users);
        assertEquals(2, users.size());

        _groupDatabase.addUserToGroup(USER3, MY_GROUP);

        users = _groupDatabase.getUsersInGroup(MY_GROUP);
        assertNotNull(users);
        assertEquals(3, users.size());
    }

    public void testAddUserToEmptyGroup() throws Exception
    {
        writeAndSetGroupFile("myGroup.users", "");

        Set<String> users = _groupDatabase.getUsersInGroup(MY_GROUP);
        assertNotNull(users);
        assertEquals(0, users.size());

        _groupDatabase.addUserToGroup(USER3, MY_GROUP);

        users = _groupDatabase.getUsersInGroup(MY_GROUP);
        assertNotNull(users);
        assertEquals(1, users.size());
    }

    public void testAddUserToNonExistentGroup() throws Exception
    {
        writeAndSetGroupFile();

        Set<String> users = _groupDatabase.getUsersInGroup(MY_GROUP);
        assertNotNull(users);
        assertEquals(0, users.size());

        try
        {
            _groupDatabase.addUserToGroup(USER3, MY_GROUP);
            fail("Expected exception not thrown");
        }
        catch(IllegalArgumentException e)
        {
            // pass
        }

        users = _groupDatabase.getUsersInGroup(MY_GROUP);
        assertNotNull(users);
        assertEquals(0, users.size());
    }

    public void testRemoveUserFromExistingGroup() throws Exception
    {
        writeAndSetGroupFile("myGroup.users", "user1,user2");

        Set<String> users = _groupDatabase.getUsersInGroup(MY_GROUP);
        assertNotNull(users);
        assertEquals(2, users.size());

        _groupDatabase.removeUserFromGroup(USER2, MY_GROUP);

        users = _groupDatabase.getUsersInGroup(MY_GROUP);
        assertNotNull(users);
        assertEquals(1, users.size());
    }

    public void testRemoveUserFromNonexistentGroup() throws Exception
    {
        writeAndSetGroupFile();

        try
        {
            _groupDatabase.removeUserFromGroup(USER1, MY_GROUP);
            fail("Expected exception not thrown");
        }
        catch(IllegalArgumentException e)
        {
            // pass
        }

        assertTrue(_groupDatabase.getUsersInGroup(MY_GROUP).isEmpty());
    }

    public void testRemoveUserFromGroupTwice() throws Exception
    {
        writeAndSetGroupFile("myGroup.users", USER1);
        assertTrue(_groupDatabase.getUsersInGroup(MY_GROUP).contains(USER1));

        _groupDatabase.removeUserFromGroup(USER1, MY_GROUP);
        assertTrue(_groupDatabase.getUsersInGroup(MY_GROUP).isEmpty());

        _groupDatabase.removeUserFromGroup(USER1, MY_GROUP);
        assertTrue(_groupDatabase.getUsersInGroup(MY_GROUP).isEmpty());
    }

    public void testAddUserPersistedToFile() throws Exception
    {
        writeAndSetGroupFile("myGroup.users", "user1,user2");

        Set<String> users = _groupDatabase.getUsersInGroup(MY_GROUP);
        assertEquals(2, users.size());

        _groupDatabase.addUserToGroup(USER3, MY_GROUP);
        assertEquals(3, users.size());

        FileGroupDatabase newGroupDatabase = new FileGroupDatabase();
        newGroupDatabase.setGroupFile(_groupFile);

        Set<String> newUsers = newGroupDatabase.getUsersInGroup(MY_GROUP);
        assertEquals(users.size(), newUsers.size());
    }

    public void testRemoveUserPersistedToFile() throws Exception
    {
        writeAndSetGroupFile("myGroup.users", "user1,user2");

        Set<String> users = _groupDatabase.getUsersInGroup(MY_GROUP);
        assertEquals(2, users.size());

        _groupDatabase.removeUserFromGroup(USER2, MY_GROUP);
        assertEquals(1, users.size());

        FileGroupDatabase newGroupDatabase = new FileGroupDatabase();
        newGroupDatabase.setGroupFile(_groupFile);

        Set<String> newUsers = newGroupDatabase.getUsersInGroup(MY_GROUP);
        assertEquals(users.size(), newUsers.size());
    }

    public void testCreateGroupPersistedToFile() throws Exception
    {
        writeAndSetGroupFile();

        Set<String> groups = _groupDatabase.getAllGroups();
        assertEquals(0, groups.size());

        _groupDatabase.createGroup(MY_GROUP);

        groups = _groupDatabase.getAllGroups();
        assertEquals(1, groups.size());
        assertTrue(groups.contains(MY_GROUP));

        FileGroupDatabase newGroupDatabase = new FileGroupDatabase();
        newGroupDatabase.setGroupFile(_groupFile);

        Set<String> newGroups = newGroupDatabase.getAllGroups();
        assertEquals(1, newGroups.size());
        assertTrue(newGroups.contains(MY_GROUP));
    }

    public void testRemoveGroupPersistedToFile() throws Exception
    {
        writeAndSetGroupFile("myGroup1.users", "user1,user2",
                             "myGroup2.users", "user1,user2");

        Set<String> groups = _groupDatabase.getAllGroups();
        assertEquals(2, groups.size());

        Set<String> groupsForUser1 = _groupDatabase.getGroupsForUser(USER1);
        assertEquals(2, groupsForUser1.size());

        _groupDatabase.removeGroup(MY_GROUP1);

        groups = _groupDatabase.getAllGroups();
        assertEquals(1, groups.size());
        assertTrue(groups.contains(MY_GROUP2));

        groupsForUser1 = _groupDatabase.getGroupsForUser(USER1);
        assertEquals(1, groupsForUser1.size());

        FileGroupDatabase newGroupDatabase = new FileGroupDatabase();
        newGroupDatabase.setGroupFile(_groupFile);

        Set<String> newGroups = newGroupDatabase.getAllGroups();
        assertEquals(1, newGroups.size());
        assertTrue(newGroups.contains(MY_GROUP2));

        Set<String> newGroupsForUser1 = newGroupDatabase.getGroupsForUser(USER1);
        assertEquals(1, newGroupsForUser1.size());
        assertTrue(newGroupsForUser1.contains(MY_GROUP2));
}

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _groupFile = createEmptyTestGroupFile();
    }

    private void writeAndSetGroupFile(String... groupAndUsers) throws Exception
    {
        writeGroupFile(groupAndUsers);
        _groupDatabase.setGroupFile(_groupFile);
    }

    private void writeGroupFile(String... groupAndUsers) throws Exception
    {
        if (groupAndUsers.length % 2 != 0)
        {
            throw new IllegalArgumentException("Number of groupAndUsers must be even");
        }

        Properties props = new Properties();
        for (int i = 0 ; i < groupAndUsers.length; i=i+2)
        {
            String group = groupAndUsers[i];
            String users = groupAndUsers[i+1];
            props.put(group, users);
        }

        props.store(new FileOutputStream(_groupFile), "test group file");
    }

    private String createEmptyTestGroupFile() throws IOException
    {
        File tmpGroupFile = File.createTempFile("groups", "grp");
        tmpGroupFile.deleteOnExit();

        return tmpGroupFile.getAbsolutePath();
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();

        if (_groupFile != null)
        {
            File groupFile = new File(_groupFile);
            if (groupFile.exists())
            {
                groupFile.delete();
            }
        }
    }

}
