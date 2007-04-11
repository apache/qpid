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
package org.apache.qpid.server.security.access;

import org.apache.qpid.server.management.MBeanDescription;
import org.apache.qpid.server.management.AMQManagedObject;
import org.apache.qpid.server.management.MBeanOperationParameter;
import org.apache.qpid.server.management.MBeanOperation;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;
import org.apache.log4j.Logger;

import javax.management.JMException;
import javax.management.openmbean.TabularData;
import javax.security.auth.login.AccountNotFoundException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.security.Principal;

/** MBean class for AMQUserManagementMBean. It implements all the management features exposed for managing users. */
@MBeanDescription("User Management Interface")
public class AMQUserManagementMBean extends AMQManagedObject implements UserManagement
{

    private static final Logger _logger = Logger.getLogger(AMQUserManagementMBean.class);

    private PrincipalDatabase _principalDatabase;
    private File _accessFile;

    Map<String, Principal> _users = new HashMap<String, Principal>();

    public AMQUserManagementMBean() throws JMException
    {
        super(UserManagement.class, UserManagement.TYPE);
    }

    public String getObjectInstanceName()
    {
        return UserManagement.TYPE;
    }

    public boolean setPassword(@MBeanOperationParameter(name = "username", description = "Username")String username,
                               @MBeanOperationParameter(name = "password", description = "Password")String password)
    {
        try
        {
            return _principalDatabase.updatePassword(new UsernamePrincipal(username), password);
        }
        catch (AccountNotFoundException e)
        {
            _logger.warn("Attempt to set password of non-existant user'" + username + "'");
            return false;
        }
    }

    public boolean setRights(@MBeanOperationParameter(name = "username", description = "Username")String username,
                             @MBeanOperationParameter(name = "read", description = "Administration read")boolean read,
                             @MBeanOperationParameter(name = "write", description = "Administration write")boolean write,
                             @MBeanOperationParameter(name = "admin", description = "Administration rights")boolean admin)
    {
        return true;
    }

    public boolean createUser(@MBeanOperationParameter(name = "username", description = "Username")String username,
                              @MBeanOperationParameter(name = "password", description = "Password")String password,
                              @MBeanOperationParameter(name = "read", description = "Administration read")boolean read,
                              @MBeanOperationParameter(name = "write", description = "Administration write")boolean write,
                              @MBeanOperationParameter(name = "admin", description = "Administration rights")boolean admin)
    {
        try
        {
            if (_principalDatabase.createPrincipal(new UsernamePrincipal(username), password))
            {
                _users.remove(username);
                return true;
            }
        }
        catch (AccountNotFoundException e)
        {
            _logger.warn("Attempt to delete user (" + username + ") that doesn't exist");
        }

        return false;
    }

    public boolean deleteUser(@MBeanOperationParameter(name = "username", description = "Username")String username)
    {

        try
        {
            if (_principalDatabase.deletePrincipal(new UsernamePrincipal(username)))
            {
                _users.remove(username);
                return true;
            }
        }
        catch (AccountNotFoundException e)
        {
            _logger.warn("Attempt to delete user (" + username + ") that doesn't exist");
        }

        return false;
    }

    public boolean reloadData()
    {
        try
        {
            loadAccessFile();

            // Reload successful
            return true;
        }
        catch (IOException e)
        {
            _logger.info("Reload failed due to:" + e);
            // Reload unsuccessful
            return false;
        }
    }

    @MBeanOperation(name = "viewUsers", description = "All users with access rights to the system.")
    public TabularData viewUsers()
    {
        return null;
    }

    /*** Broker Methods **/

    /**
     * setPrincipalDatabase
     *
     * @param database
     *
     * @throws java.io.IOException If the file cannot be read
     */
    public void setPrincipalDatabase(PrincipalDatabase database)
    {
        _principalDatabase = database;
    }

    /**
     * setAccessFile
     *
     * @param accessFile the file to use for updating.
     *
     * @throws java.io.IOException If the file cannot be read
     */
    public void setAccessFile(File accessFile) throws IOException
    {
        _accessFile = accessFile;

        if (_accessFile != null)
        {
            loadAccessFile();
        }
        else
        {
            _logger.warn("Access rights file specified is null. Access rights not changed.");
        }
    }

    private void loadAccessFile() throws IOException
    {
        Properties accessRights = new Properties();
        accessRights.load(new FileInputStream(_accessFile));
        processAccessRights(accessRights);

    }

    /**
     * user=read user=write user=readwrite user=admin
     *
     * @param accessRights The properties list of access rights to process
     */
    private void processAccessRights(Properties accessRights)
    {
        //To change body of created methods use File | Settings | File Templates.
    }
}
