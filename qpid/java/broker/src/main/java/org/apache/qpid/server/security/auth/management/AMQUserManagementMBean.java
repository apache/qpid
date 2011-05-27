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
package org.apache.qpid.server.security.auth.management;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

import javax.management.JMException;
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

import org.apache.log4j.Logger;
import org.apache.qpid.management.common.mbeans.UserManagement;
import org.apache.qpid.management.common.mbeans.annotations.MBeanDescription;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperation;
import org.apache.qpid.server.management.AMQManagedObject;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;

/** MBean class for AMQUserManagementMBean. It implements all the management features exposed for managing users. */
@MBeanDescription("User Management Interface")
public class AMQUserManagementMBean extends AMQManagedObject implements UserManagement
{
    private static final Logger _logger = Logger.getLogger(AMQUserManagementMBean.class);

    private PrincipalDatabase _principalDatabase;

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

    public AMQUserManagementMBean() throws JMException
    {
        super(UserManagement.class, UserManagement.TYPE);
    }

    public String getObjectInstanceName()
    {
        return UserManagement.TYPE;
    }

    public boolean setPassword(String username, String password)
    {
        return setPassword(username, password.toCharArray());
    }
    
    public boolean setPassword(String username, char[] password)
    {
        try
        {
            //delegate password changes to the Principal Database
            return _principalDatabase.updatePassword(new UsernamePrincipal(username), password);
        }
        catch (AccountNotFoundException e)
        {
            _logger.warn("Attempt to set password of non-existent user'" + username + "'");
            return false;
        }
    }

    public boolean setRights(String username, boolean read, boolean write, boolean admin)
    {
        throw new UnsupportedOperationException("Support for setting access rights no longer supported.");
    }
    
    public boolean createUser(String username, String password)
    {
        if (_principalDatabase.createPrincipal(new UsernamePrincipal(username), password.toCharArray()))
        {
            return true;
        }

        return false;
    }
    
    public boolean createUser(String username, String password, boolean read, boolean write, boolean admin)
    {
        if (read || write || admin)
        {
            throw new UnsupportedOperationException("Support for setting access rights to true no longer supported.");
        }
        return createUser(username, password);
    }

    public boolean createUser(String username, char[] password, boolean read, boolean write, boolean admin)
    {
        return createUser(username, new String(password), read, write, admin);
    }

    public boolean deleteUser(String username)
    {
        try
        {
            _principalDatabase.deletePrincipal(new UsernamePrincipal(username));
        }
        catch (AccountNotFoundException e)
        {
            _logger.warn("Attempt to delete user (" + username + ") that doesn't exist");
            return false;
        }

        return true;
    }

    public boolean reloadData()
    {
        try
        {
            _principalDatabase.reload();
        }
        catch (IOException e)
        {
            _logger.warn("Reload failed due to:", e);
            return false;
        }
        // Reload successful
        return true;
    }


    @MBeanOperation(name = "viewUsers", description = "All users that are currently available to the system.")
    public TabularData viewUsers()
    {
        List<Principal> users = _principalDatabase.getUsers();

        TabularDataSupport userList = new TabularDataSupport(_userlistDataType);

        try
        {
            // Create the tabular list of message header contents
            for (Principal user : users)
            {
                // Create header attributes list
                
                // Read,Write,Admin items are depcreated and we return always false.
                Object[] itemData = {user.getName(), false, false, false};
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

    /*** Broker Methods **/

    /**
     * setPrincipalDatabase
     *
     * @param database set The Database to use for user lookup
     */
    public void setPrincipalDatabase(PrincipalDatabase database)
    {
        _principalDatabase = database;
    }
}
