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

import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.security.auth.UsernamePrincipal;

/**
 * Implementation of a group manager whose implementation is backed by a flat group file.
 * <p>
 * This plugin is configured in the following manner:
 * </p>
 * <pre>
 * &lt;file-group-manager&gt;
 *    &lt;attributes&gt;
 *       &lt;attribute&gt;
 *            &lt;name>groupFile&lt;/name&gt;
 *            &lt;value>${conf}/groups&lt;/value&gt;
 *        &lt;/attribute&gt;
 *    &lt;/attributes&gt;
 * &lt;/file-group-manager&gt;
 * </pre>
 */
public class FileGroupManager implements GroupManager
{
    private final FileGroupDatabase _groupDatabase;


    public FileGroupManager(String groupFile)
    {
        _groupDatabase = new FileGroupDatabase();
        try
        {
            _groupDatabase.setGroupFile(groupFile);
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Unable to set group file " + groupFile, e);
        }
    }

    @Override
    public Set<Principal> getGroupPrincipalsForUser(String userId)
    {
        Set<String> groups = _groupDatabase.getGroupsForUser(userId);
        if (groups.isEmpty())
        {
            return Collections.emptySet();
        }
        else
        {
            Set<Principal> principals = new HashSet<Principal>();
            for (String groupName : groups)
            {
                principals.add(new GroupPrincipal(groupName));
            }
            return principals;
        }
    }

    @Override
    public Set<Principal> getUserPrincipalsForGroup(String group)
    {
        Set<String> users = _groupDatabase.getUsersInGroup(group);
        if (users.isEmpty())
        {
            return Collections.emptySet();
        }
        else
        {
            Set<Principal> principals = new HashSet<Principal>();
            for (String user : users)
            {
                principals.add(new UsernamePrincipal(user));
            }
            return principals;
        }
    }

    @Override
    public Set<Principal> getGroupPrincipals()
    {
        Set<String> groups = _groupDatabase.getAllGroups();
        if (groups.isEmpty())
        {
            return Collections.emptySet();
        }
        else
        {
            Set<Principal> principals = new HashSet<Principal>();
            for (String groupName : groups)
            {
                principals.add(new GroupPrincipal(groupName));
            }
            return principals;
        }
    }

    @Override
    public void createGroup(String group)
    {
        _groupDatabase.createGroup(group);
    }

    @Override
    public void removeGroup(String group)
    {
        _groupDatabase.removeGroup(group);
    }

    @Override
    public void addUserToGroup(String user, String group)
    {
        _groupDatabase.addUserToGroup(user, group);
    }

    @Override
    public void removeUserFromGroup(String user, String group)
    {
        _groupDatabase.removeUserFromGroup(user, group);

    }

}
