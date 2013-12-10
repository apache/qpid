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
package org.apache.qpid.management.common.mbeans;

import org.apache.qpid.management.common.mbeans.annotations.MBeanAttribute;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperation;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperationParameter;

import javax.management.MBeanOperationInfo;
import javax.management.openmbean.TabularData;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public interface UserManagement
{

    String TYPE = "UserManagement";
    
    //TabularType and contained CompositeType key/description information.
    //For compatibility reasons, DONT MODIFY the existing key values if expanding the set.
    String USERNAME = "Username";
    String RIGHTS_READ_ONLY = "read";   // item deprecated
    String RIGHTS_READ_WRITE = "write"; // item deprecated
    String RIGHTS_ADMIN = "admin";      // item deprecated

    List<String> COMPOSITE_ITEM_NAMES = Collections.unmodifiableList(Arrays.asList(USERNAME, RIGHTS_READ_ONLY, RIGHTS_READ_WRITE, RIGHTS_ADMIN));
    List<String> COMPOSITE_ITEM_DESCRIPTIONS = Collections.unmodifiableList(
                              Arrays.asList("Broker Login username", 
                                            "Item no longer used", 
                                            "Item no longer used", 
                                            "Item no longer used"));

    List<String> TABULAR_UNIQUE_INDEX = Collections.unmodifiableList(Arrays.asList(USERNAME));

    //********** Operations *****************//
    /**
     * Set password for a given user.
     * 
     * @since Qpid JMX API 1.7
     *
     * @param username The username to create
     * @param password The password for the user
     *
     * @return The result of the operation
     */
    @MBeanOperation(name = "setPassword", description = "Set password for user.",
                    impact = MBeanOperationInfo.ACTION)
    boolean setPassword(@MBeanOperationParameter(name = "username", description = "Username")String username,
                        @MBeanOperationParameter(name = "password", description = "Password")String password);

    /**
     * Create users with given details.
     * 
     * @since Qpid JMX API 2.3 / 1.12
     * 
     * @param username The username to create
     * @param password The password for the user
     *
     * @return true if the user was created successfully, or false otherwise
     */
    @MBeanOperation(name = "createUser", description = "Create a new user.",
                    impact = MBeanOperationInfo.ACTION)
    boolean createUser(@MBeanOperationParameter(name = "username", description = "Username")String username,
                       @MBeanOperationParameter(name = "password", description = "Password")String password);
    
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
     * Reload the user data
     * 
     * Since Qpid JMX API 2.3 / 1.12 this operation reloads only the password data.
     * Since Qpid JMX API 1.2 but prior to 2.3 / 1.12 this operation reloads the password and authorisation files.
     * Prior to 1.2, only the authorisation file was reloaded.
     *
     * @return The result of the operation
     */
    @MBeanOperation(name = "reloadData", description = "Reload the authentication and authorisation files from disk.",
                    impact = MBeanOperationInfo.ACTION)
    boolean reloadData();

    /**
     * View users returns all the users that are currently available to the system.
     * 
     * Since Qpid JMX API 2.3 / 1.12 the items that corresponded to read, write and admin flags
     * are deprecated and always return false.
     *
     * @return a table of users data (Username, read, write, admin)
     */
    @MBeanOperation(name = "viewUsers", description = "All users that are currently available to the system.",
                    impact = MBeanOperationInfo.INFO)
    TabularData viewUsers();

    /**
     * The type of the underlying authentication provider being managed.
     *
     * @since Qpid JMX API 2.6
     */
    @MBeanAttribute(name="AuthenticationProviderType", description="The type of the underlying authentication provider being managed.")
    String getAuthenticationProviderType();
}
