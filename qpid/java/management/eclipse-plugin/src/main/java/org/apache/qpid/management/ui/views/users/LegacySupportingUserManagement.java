/*
 *
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
package org.apache.qpid.management.ui.views.users;

import org.apache.qpid.management.common.mbeans.UserManagement;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperation;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperationParameter;

import javax.management.MBeanOperationInfo;

/**
 * UserManagement interface extension to provide the method signatures
 * for old UserManagement methods no longer supported by the broker.
 *
 * This interface is used only for the creation of MBean proxy objects
 * within the management console, for backwards compatibility with
 * functionality in older broker versions.
 */
public interface LegacySupportingUserManagement extends UserManagement
{
    /**
     * set password for user.
     *
     * Since Qpid JMX API 1.2 this operation expects plain text passwords to be provided. Prior to this, MD5 hashed passwords were supplied.
     *
     * @deprecated since Qpid JMX API 1.7
     *
     * @param username The username for which the password is to be set
     * @param password The password for the user
     *
     * @return The result of the operation
     */
    @Deprecated
    @MBeanOperation(name = "setPassword", description = "Set password for user.",
                    impact = MBeanOperationInfo.ACTION)
    boolean setPassword(@MBeanOperationParameter(name = "username", description = "Username")String username,
                        //NOTE: parameter name was changed to 'passwd' in Qpid JMX API 1.7 to protect against older, incompatible management clients
                        @MBeanOperationParameter(name = "passwd", description = "Password")char[] password);

    /**
     * Set rights for users with given details.
     * Since Qpid JMX API 2.3 all invocations will cause an exception to be thrown
     * as access rights can no longer be maintain via this interface.
     *
     * @deprecated since Qpid JMX API 2.3 / 1.12
     *
     * @param username The username to create
     * @param read     The set of permission to give the new user
     * @param write    The set of permission to give the new user
     * @param admin    The set of permission to give the new user
     *
     * @return The result of the operation
     */
    @Deprecated
    @MBeanOperation(name = "setRights", description = "Set access rights for user.",
                    impact = MBeanOperationInfo.ACTION)
    boolean setRights(@MBeanOperationParameter(name = "username", description = "Username")String username,
                      @MBeanOperationParameter(name = "read", description = "Administration read")boolean read,
                      @MBeanOperationParameter(name = "readAndWrite", description = "Administration write")boolean write,
                      @MBeanOperationParameter(name = "admin", description = "Administration rights")boolean admin);

    /**
     * Create users with given details.
     * Since Qpid JMX API 2.3 if the user passes true for parameters read, write, or admin, a
     * exception will be thrown as access rights can no longer be maintain via this interface.
     *
     * Since Qpid JMX API 1.2 this operation expects plain text passwords to be provided. Prior to this, MD5 hashed passwords were supplied.
     *
     * @deprecated since Qpid JMX API 1.7
     *
     * @param username The username to create
     * @param password The password for the user
     * @param read     The set of permission to give the new user
     * @param write    The set of permission to give the new user
     * @param admin    The set of permission to give the new user
     *
     * @return true if the user was created successfully, or false otherwise
     */
    @Deprecated
    @MBeanOperation(name = "createUser", description = "Create new user from system.",
                    impact = MBeanOperationInfo.ACTION)
    boolean createUser(@MBeanOperationParameter(name = "username", description = "Username")String username,
                       //NOTE: parameter name was changed to 'passwd' in Qpid JMX API 1.7 to protect against older, incompatible management clients
                       @MBeanOperationParameter(name = "passwd", description = "Password")char[] password,
                       @MBeanOperationParameter(name = "read", description = "Administration read")boolean read,
                       @MBeanOperationParameter(name = "readAndWrite", description = "Administration write")boolean write,
                       @MBeanOperationParameter(name = "admin", description = "Administration rights")boolean admin);

    /**
     * Create users with given details.
     * Since Qpid JMX API 2.3 if the user passes true for parameters read, write, or admin, a
     * exception will be thrown as access rights can no longer be maintain via this interface.
     *
     * @deprecated since Qpid JMX API 2.3 / 1.12
     * @since Qpid JMX API 1.7
     *
     * @param username The username to create
     * @param password The password for the user
     * @param read     The set of permission to give the new user
     * @param write    The set of permission to give the new user
     * @param admin    The set of permission to give the new user
     *
     * @return true if the user was created successfully, or false otherwise
     */
    @Deprecated
    @MBeanOperation(name = "createUser", description = "Create a new user.",
                    impact = MBeanOperationInfo.ACTION)
    boolean createUser(@MBeanOperationParameter(name = "username", description = "Username")String username,
                       @MBeanOperationParameter(name = "password", description = "Password")String password,
                       @MBeanOperationParameter(name = "read", description = "Administration read")boolean read,
                       @MBeanOperationParameter(name = "readAndWrite", description = "Administration write")boolean write,
                       @MBeanOperationParameter(name = "admin", description = "Administration rights")boolean admin);

}
