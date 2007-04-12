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
import org.apache.qpid.server.management.MBeanInvocationHandlerImpl;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;
import org.apache.log4j.Logger;
import org.apache.commons.configuration.ConfigurationException;

import javax.management.JMException;
import javax.management.openmbean.TabularData;
import javax.security.auth.login.AccountNotFoundException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.util.Properties;

/** MBean class for AMQUserManagementMBean. It implements all the management features exposed for managing users. */
@MBeanDescription("User Management Interface")
public class AMQUserManagementMBean extends AMQManagedObject implements UserManagement
{

    private static final Logger _logger = Logger.getLogger(AMQUserManagementMBean.class);

    private PrincipalDatabase _principalDatabase;
    private String _accessFileName;
    private Properties _accessRights;
    private File _accessFile;

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

        if (_accessRights.get(username) == null)
        {
            if (_principalDatabase.getUser(username) == null)
            {
                return false;
            }
        }

        if (admin)
        {
            _accessRights.put(username, MBeanInvocationHandlerImpl.ADMIN);
        }
        else
        {
            if (read | write)
            {
                if (read)
                {
                    _accessRights.put(username, MBeanInvocationHandlerImpl.READONLY);
                }
                if (write)
                {
                    _accessRights.put(username, MBeanInvocationHandlerImpl.READWRITE);
                }
            }
            else
            {
                return false;
            }
        }

        saveAccessFile();

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
                _accessRights.put(username, "");

                return setRights(username, read, write, admin);
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
                _accessRights.remove(username);

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
            try
            {
                loadAccessFile();
            }
            catch (ConfigurationException e)
            {
                _logger.info("Reload failed due to:" + e);
                return false;
            }

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
        return null;       //todo
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

    /**
     * setAccessFile
     *
     * @param accessFile the file to use for updating.
     *
     * @throws java.io.IOException If the file cannot be accessed
     * @throws org.apache.commons.configuration.ConfigurationException
     *                             if checks on the file fail.
     */
    public void setAccessFile(String accessFile) throws IOException, ConfigurationException
    {
        _accessFileName = accessFile;

        if (_accessFileName != null)
        {
            loadAccessFile();
        }
        else
        {
            _logger.warn("Access rights file specified is null. Access rights not changed.");
        }
    }

    private void loadAccessFile() throws IOException, ConfigurationException
    {
        Properties accessRights = new Properties();

        _accessFile = new File(_accessFileName);

        if (!_accessFile.exists())
        {
            throw new ConfigurationException("'" + _accessFileName + "' does not exist");
        }

        if (!_accessFile.canRead())
        {
            throw new ConfigurationException("Cannot read '" + _accessFileName + "'.");
        }

        if (!_accessFile.canWrite())
        {
            _logger.warn("Unable to write to access file '" + _accessFileName + "' changes will not be preserved.");
        }

        accessRights.load(new FileInputStream(_accessFileName));
        processAccessRights(accessRights);
    }

    private void saveAccessFile()
    {
        try
        {
            _accessRights.store(new FileOutputStream(_accessFile), "");
        }
        catch (IOException e)
        {
            _logger.warn("Unable to write to access file '" + _accessFileName + "' changes will not be preserved.");
        }
    }

    /**
     * user=read user=write user=readwrite user=admin
     *
     * @param accessRights The properties list of access rights to process
     */
    private void processAccessRights(Properties accessRights)
    {
        _logger.info("Processing Access Rights:" + accessRights);
        _accessRights = accessRights;
        MBeanInvocationHandlerImpl.setAccessRights(_accessRights);
    }
}
