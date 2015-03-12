/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.jmx.mbeans;

import java.io.IOException;
import java.util.Map;

import javax.management.JMException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;
import javax.security.auth.login.AccountNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.management.common.mbeans.UserManagement;
import org.apache.qpid.management.common.mbeans.annotations.MBeanDescription;
import org.apache.qpid.server.jmx.AMQManagedObject;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;

@MBeanDescription("User Management Interface")
public class UserManagementMBean extends AMQManagedObject implements UserManagement
{
    private static final Logger _logger = LoggerFactory.getLogger(UserManagementMBean.class);

    private PasswordCredentialManagingAuthenticationProvider _authProvider;

    private String _mbeanName;
    private String _type;

    // Setup for the TabularType
    private static final TabularType _userlistDataType; // Datatype for representing User Lists
    private static final CompositeType _userDataType; // Composite type for representing User

    static
    {
        OpenType[] userItemTypes = new OpenType[4]; // User item types.
        userItemTypes[0] = SimpleType.STRING;  // For Username
        userItemTypes[1] = SimpleType.BOOLEAN; // For Rights - Read - No longer in use
        userItemTypes[2] = SimpleType.BOOLEAN; // For Rights - Write - No longer in use
        userItemTypes[3] = SimpleType.BOOLEAN; // For Rights - Admin - No longer is use

        try
        {
            _userDataType =
                    new CompositeType("User", "User Data", COMPOSITE_ITEM_NAMES.toArray(new String[COMPOSITE_ITEM_NAMES.size()]),
                            COMPOSITE_ITEM_DESCRIPTIONS.toArray(new String[COMPOSITE_ITEM_DESCRIPTIONS.size()]), userItemTypes);

            _userlistDataType = new TabularType("Users", "List of users", _userDataType, TABULAR_UNIQUE_INDEX.toArray(new  String[TABULAR_UNIQUE_INDEX.size()]));
        }
        catch (OpenDataException e)
        {
            _logger.error("Tabular data setup for viewing users incorrect.", e);
            throw new ExceptionInInitializerError("Tabular data setup for viewing users incorrect");
        }
    }

    public UserManagementMBean(PasswordCredentialManagingAuthenticationProvider provider, ManagedObjectRegistry registry) throws JMException
    {
        super(UserManagement.class, UserManagement.TYPE, registry);
        _authProvider = provider;
        _mbeanName = UserManagement.TYPE + "-" + _authProvider.getName();
        _type = String.valueOf(_authProvider.getAttribute(AuthenticationProvider.TYPE));
        register();
    }

    @Override
    public String getObjectInstanceName()
    {
        return ObjectName.quote(_mbeanName);
    }

    @Override
    public boolean setPassword(String username, String password)
    {
        try
        {
            _authProvider.setPassword(username, password);
        }
        catch (AccountNotFoundException e)
        {
            _logger.warn("Attempt to set password of non-existent user '" + username + "'");
            return false;
        }
        return true;
    }

    @Override
    public boolean createUser(String username, String password)
    {
        return _authProvider.createUser(username, password, null);
    }

    @Override
    public boolean deleteUser(String username)
    {
        try
        {
            _authProvider.deleteUser(username);
        }
        catch (AccountNotFoundException e)
        {
            _logger.warn("Attempt to delete user (" + username + ") that doesn't exist");
            return false;
        }

        return true;
    }

    @Override
    public boolean reloadData()
    {
        try
        {
            _authProvider.reload();
            return true;
        }
        catch (IOException e)
        {
            _logger.error("Unable to reload user data", e);
            return false;
        }
    }

    @Override
    public TabularData viewUsers()
    {
        Map<String, Map<String, String>> users = _authProvider.getUsers();

        TabularDataSupport userList = new TabularDataSupport(_userlistDataType);

        try
        {
            // Create the tabular list of message header contents
            for (String user : users.keySet())
            {
                // Create header attributes list
                // Read,Write,Admin items are deprecated and we return always false.
                Object[] itemData = {user, false, false, false};
                CompositeData messageData = new CompositeDataSupport(_userDataType, COMPOSITE_ITEM_NAMES.toArray(new String[COMPOSITE_ITEM_NAMES.size()]), itemData);
                userList.put(messageData);
            }
        }
        catch (OpenDataException e)
        {
            _logger.warn("Unable to create user list due to :", e);
            return null;
        }

        return userList;
    }

    @Override
    public ManagedObject getParentObject()
    {
        return null;
    }

    @Override
    public String getAuthenticationProviderType()
    {
        return _type;
    }
}
