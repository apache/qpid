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
package org.apache.qpid.server.security.auth.database;

import org.apache.qpid.server.security.auth.sasl.AuthenticationProviderInitialiser;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Map;

import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AccountNotFoundException;

/** Represents a "user database" which is really a way of storing principals (i.e. usernames) and passwords. */
public interface PrincipalDatabase
{
    /**
     * Set the password for a given principal in the specified callback. This is used for certain SASL providers. The
     * user database implementation should look up the password in any way it chooses and set it in the callback by
     * calling its setPassword method.
     *
     * @param principal the principal
     * @param callback  the password callback that wants to receive the password
     *
     * @throws AccountNotFoundException if the account for specified principal could not be found
     * @throws IOException              if there was an error looking up the principal
     */
    void setPassword(Principal principal, PasswordCallback callback)
            throws IOException, AccountNotFoundException;

    /**
     * Set the password for a given principal in the specified callback. This is used for certain SASL providers. The
     * user database implementation should look up the password in any way it chooses and set it in the callback by
     * calling its setPassword method.
     *
     * @param principal the principal
     * @param password  the password to be verified
     *
     * @return true if the account is verified.
     *
     * @throws AccountNotFoundException if the account for specified principal could not be found
     */
    boolean verifyPassword(Principal principal, String password)
            throws AccountNotFoundException;

    boolean updatePassword(Principal principal, String password)
            throws AccountNotFoundException;

    boolean createPrincipal(Principal principal, String password)
            throws AccountNotFoundException;

    boolean deletePrincipal(Principal principal)
            throws AccountNotFoundException;

    public Map<String, AuthenticationProviderInitialiser> getMechanisms();
}
