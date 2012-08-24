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
import java.security.Principal;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.util.InternalBrokerBaseCase;

public class FileGroupManagerTest extends InternalBrokerBaseCase
{
    private static final String MYGROUP_USERS = "user1";
    private static final String MY_GROUP = "myGroup.users";
    private static final String MY_GROUP2 = "myGroup2.users";
    private File _tmpGroupFile;
    private FileGroupManager _manager;

    @Override
    public void tearDown() throws Exception
    {
        super.tearDown();
//TODO: implement closable
//        if (_manager != null)
//        {
//            _manager.close();
//        }

        if (_tmpGroupFile != null)
        {
            if (_tmpGroupFile.exists())
            {
                _tmpGroupFile.delete();
            }
        }
    }

    public void testValidGroupFile() throws Exception
    {
        final String groupFileName = writeGroupFile();
        final ConfigurationPlugin config = getConfig("groupFile", groupFileName);

        _manager = FileGroupManager.FACTORY.newInstance(config);
        assertNotNull(_manager);
    }

    public void testNonExistentGroupFile() throws Exception
    {
        final String filePath = "/does.not.exist/";
        final File fileFile = new File(filePath);

        assertFalse("File already exists", fileFile.exists());
        final ConfigurationPlugin config = getConfig("groupFile", filePath);

        try
        {
            _manager = FileGroupManager.FACTORY.newInstance(config);
            fail("expected exception was not thrown");
        }
        catch(ConfigurationException ce)
        {
            assertNotNull(ce.getCause());
            assertTrue(ce.getCause() instanceof FileNotFoundException);
        }
    }

    public void testGetGroupPrincipalsForUser() throws Exception
    {
        final String groupFileName = writeGroupFile();
        final ConfigurationPlugin config = getConfig("groupFile", groupFileName);
        _manager = FileGroupManager.FACTORY.newInstance(config);

        Set<Principal> principals = _manager.getGroupPrincipalsForUser("user1");
        assertEquals(1, principals.size());
        assertTrue(principals.contains(new GroupPrincipal("myGroup")));
    }

    public void testGetUserPrincipalsForGroup() throws Exception
    {
        final String groupFileName = writeGroupFile();
        final ConfigurationPlugin config = getConfig("groupFile", groupFileName);
        _manager = FileGroupManager.FACTORY.newInstance(config);

        Set<Principal> principals = _manager.getUserPrincipalsForGroup("myGroup");
        assertEquals(1, principals.size());
        assertTrue(principals.contains(new UsernamePrincipal("user1")));
    }

    public void testGetGroupPrincipals() throws Exception
    {
        final String groupFileName = writeGroupFile(MY_GROUP, MYGROUP_USERS, MY_GROUP2, MYGROUP_USERS);
        final ConfigurationPlugin config = getConfig("groupFile", groupFileName);
        _manager = FileGroupManager.FACTORY.newInstance(config);

        Set<Principal> principals = _manager.getGroupPrincipals();
        assertEquals(2, principals.size());
        assertTrue(principals.contains(new GroupPrincipal("myGroup")));
        assertTrue(principals.contains(new GroupPrincipal("myGroup2")));
    }

    public void testCreateGroup() throws Exception
    {
        final String groupFileName = writeGroupFile();
        final ConfigurationPlugin config = getConfig("groupFile", groupFileName);
        _manager = FileGroupManager.FACTORY.newInstance(config);

        Set<Principal> principals = _manager.getGroupPrincipals();
        assertEquals(1, principals.size());

        _manager.createGroup("myGroup2");

        principals = _manager.getGroupPrincipals();
        assertEquals(2, principals.size());
        assertTrue(principals.contains(new GroupPrincipal("myGroup2")));
    }

    public void testRemoveGroup() throws Exception
    {
        final String groupFileName = writeGroupFile(MY_GROUP, MYGROUP_USERS);
        final ConfigurationPlugin config = getConfig("groupFile", groupFileName);
        _manager = FileGroupManager.FACTORY.newInstance(config);

        Set<Principal> principals = _manager.getGroupPrincipals();
        assertEquals(1, principals.size());

        _manager.removeGroup("myGroup");

        principals = _manager.getGroupPrincipals();
        assertEquals(0, principals.size());
    }

    public void testAddUserToGroup() throws Exception
    {
        final String groupFileName = writeGroupFile(MY_GROUP, MYGROUP_USERS);
        final ConfigurationPlugin config = getConfig("groupFile", groupFileName);
        _manager = FileGroupManager.FACTORY.newInstance(config);

        Set<Principal> principals = _manager.getUserPrincipalsForGroup("myGroup");
        assertEquals(1, principals.size());
        assertFalse(principals.contains(new UsernamePrincipal("user2")));

        _manager.addUserToGroup("user2", "myGroup");

        principals = _manager.getUserPrincipalsForGroup("myGroup");
        assertEquals(2, principals.size());
        assertTrue(principals.contains(new UsernamePrincipal("user2")));
    }

    public void testRemoveUserInGroup() throws Exception
    {
        final String groupFileName = writeGroupFile(MY_GROUP, MYGROUP_USERS);
        final ConfigurationPlugin config = getConfig("groupFile", groupFileName);
        _manager = FileGroupManager.FACTORY.newInstance(config);

        Set<Principal> principals = _manager.getUserPrincipalsForGroup("myGroup");
        assertEquals(1, principals.size());
        assertTrue(principals.contains(new UsernamePrincipal("user1")));

        _manager.removeUserFromGroup("user1", "myGroup");

        principals = _manager.getUserPrincipalsForGroup("myGroup");
        assertEquals(0, principals.size());
    }

    private ConfigurationPlugin getConfig(final String argName, final String argValue) throws Exception
    {
        final ConfigurationPlugin config = new FileGroupManager.FileGroupManagerConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();

        if (argName != null)
        {
            xmlconfig.addProperty("file-group-manager.attributes.attribute.name", argName);
            xmlconfig.addProperty("file-group-manager.attributes.attribute.value", argValue);
        }

        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);
        config.setConfiguration("security", xmlconfig);
        return config;
    }

    private String writeGroupFile() throws Exception
    {
        return writeGroupFile(MY_GROUP, MYGROUP_USERS);
    }

    private String writeGroupFile(String... groupAndUsers) throws Exception
    {
        if (groupAndUsers.length % 2 != 0)
        {
            throw new IllegalArgumentException("Number of groupAndUsers must be even");
        }

        _tmpGroupFile = File.createTempFile("groups", "grp");
        _tmpGroupFile.deleteOnExit();

        Properties props = new Properties();
        for (int i = 0 ; i < groupAndUsers.length; i=i+2)
        {
            String group = groupAndUsers[i];
            String users = groupAndUsers[i+1];
            props.put(group, users);
        }

        props.store(new FileOutputStream(_tmpGroupFile), "test group file");

        return _tmpGroupFile.getCanonicalPath();
    }
}
