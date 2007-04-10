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

import javax.management.JMException;
import javax.management.openmbean.TabularData;

/** MBean class for AMQUserManagementMBean. It implements all the management features exposed for managing users. */
@MBeanDescription("User Management Interface")
public class AMQUserManagementMBean extends AMQManagedObject implements UserManagement
{

    public AMQUserManagementMBean() throws JMException
    {
        super(UserManagement.class, UserManagement.TYPE);
    }

    public String getObjectInstanceName()
    {
        return UserManagement.TYPE;
    }

    public boolean setPassword(@MBeanOperationParameter(name = "username", description = "Username")String username, @MBeanOperationParameter(name = "password", description = "Password")String password)
    {
        return true;
    }

    public boolean setRights(@MBeanOperationParameter(name = "username", description = "Username")String username, @MBeanOperationParameter(name = "read", description = "Administration read")boolean read, @MBeanOperationParameter(name = "write", description = "Administration write")boolean write, @MBeanOperationParameter(name = "admin", description = "Administration rights")boolean admin)
    {
        return true;
    }

    public boolean createUser(@MBeanOperationParameter(name = "username", description = "Username")String username, @MBeanOperationParameter(name = "password", description = "Password")String password, @MBeanOperationParameter(name = "read", description = "Administration read")boolean read, @MBeanOperationParameter(name = "write", description = "Administration write")boolean write, @MBeanOperationParameter(name = "admin", description = "Administration rights")boolean admin)
    {
        return true;
    }

    public boolean deleteUser(@MBeanOperationParameter(name = "username", description = "Username")String username)
    {
        return true;
    }

    @MBeanOperation(name = "viewUsers", description = "All users with access rights to the system.")
    public TabularData viewUsers()
    {
        return null;
    }
}
