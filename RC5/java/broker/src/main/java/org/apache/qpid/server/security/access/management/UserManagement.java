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
package org.apache.qpid.server.security.access.management;

import org.apache.qpid.server.management.MBeanOperation;
import org.apache.qpid.server.management.MBeanOperationParameter;
import org.apache.qpid.server.management.MBeanAttribute;
import org.apache.qpid.AMQException;

import javax.management.openmbean.TabularData;
import javax.management.openmbean.CompositeData;
import javax.management.JMException;
import javax.management.MBeanOperationInfo;
import java.io.IOException;

public interface UserManagement
{
    String TYPE = "UserManagement";

    //********** Operations *****************//
    /**
     * set password for user
     *
     * @param username The username to create
     * @param password The password for the user
     *
     * @return The result of the operation
     */
    @MBeanOperation(name = "setPassword", description = "Set password for user.",
                    impact = MBeanOperationInfo.ACTION)
    boolean setPassword(@MBeanOperationParameter(name = "username", description = "Username")String username,
                        @MBeanOperationParameter(name = "password", description = "Password")char[] password);

    /**
     * set rights for users with given details
     *
     * @param username The username to create
     * @param read     The set of permission to give the new user
     * @param write    The set of permission to give the new user
     * @param admin    The set of permission to give the new user
     *
     * @return The result of the operation
     */
    @MBeanOperation(name = "setRights", description = "Set access rights for user.",
                    impact = MBeanOperationInfo.ACTION)
    boolean setRights(@MBeanOperationParameter(name = "username", description = "Username")String username,
                      @MBeanOperationParameter(name = "read", description = "Administration read")boolean read,
                      @MBeanOperationParameter(name = "readAndWrite", description = "Administration write")boolean write,
                      @MBeanOperationParameter(name = "admin", description = "Administration rights")boolean admin);

    /**
     * Create users with given details
     *
     * @param username The username to create
     * @param password The password for the user
     * @param read     The set of permission to give the new user
     * @param write    The set of permission to give the new user
     * @param admin    The set of permission to give the new user
     *
     * @return The result of the operation
     */
    @MBeanOperation(name = "createUser", description = "Create new user from system.",
                    impact = MBeanOperationInfo.ACTION)
    boolean createUser(@MBeanOperationParameter(name = "username", description = "Username")String username,
                       @MBeanOperationParameter(name = "password", description = "Password")char[] password,
                       @MBeanOperationParameter(name = "read", description = "Administration read")boolean read,
                       @MBeanOperationParameter(name = "readAndWrite", description = "Administration write")boolean write,
                       @MBeanOperationParameter(name = "admin", description = "Administration rights")boolean admin);

    /**
     * View users returns all the users that are currently available to the system.
     *
     * @param username The user to delete
     *
     * @return The result of the operation
     */
    @MBeanOperation(name = "deleteUser", description = "Delete user from system.",
                    impact = MBeanOperationInfo.ACTION)
    boolean deleteUser(@MBeanOperationParameter(name = "username", description = "Username")String username);


    /**
     * Reload the date from disk
     *
     * @return The result of the operation
     */
    @MBeanOperation(name = "reloadData", description = "Reload the authentication file from disk.",
                    impact = MBeanOperationInfo.ACTION)
    boolean reloadData();

    /**
     * View users returns all the users that are currently available to the system.
     *
     * @return a table of users data (Username, read, write, admin)
     */
    @MBeanOperation(name = "viewUsers", description = "All users with access rights to the system.",
                    impact = MBeanOperationInfo.INFO)
    TabularData viewUsers();

}
