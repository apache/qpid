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
package org.apache.qpid.server.security.auth.manager;

import java.security.Principal;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import org.apache.qpid.common.Closeable;
import org.apache.qpid.server.security.auth.AuthenticationResult;

/**
 * Implementations of the AuthenticationManager are responsible for determining
 * the authenticity of a user's credentials.
 * <p>
 * If the authentication is successful, the manager is responsible for producing an
 * {@link AuthenticationResult} containing the user's main {@link Principal} and zero or
 * more other implementation-specific principals.
 * </p>
 */
public interface AuthenticationManager extends Closeable
{
    /**
     * Initialise the authentication plugin.
     *
     */
    void initialise();

   /**
    * Gets the SASL mechanisms known to this manager.
    *
    * @return SASL mechanism names, space separated.
    */
    String getMechanisms();

    /**
     * Creates a SASL server for the specified mechanism name for the given
     * fully qualified domain name.
     *
     * @param mechanism mechanism name
     * @param localFQDN domain name
     * @param externalPrincipal externally authenticated Principal
     * @return SASL server
     * @throws SaslException
     */
    SaslServer createSaslServer(String mechanism, String localFQDN, Principal externalPrincipal) throws SaslException;

    /**
     * Authenticates a user using SASL negotiation.
     *
     * @param server SASL server
     * @param response SASL response to process
     *
     * @return authentication result
     */
    AuthenticationResult authenticate(SaslServer server, byte[] response);

    /**
     * Authenticates a user using their username and password.
     *
     * @param username username
     * @param password password
     *
     * @return authentication result
     */
    AuthenticationResult authenticate(String username, String password);

    /**
     * Called after manager creation to create the required resources, for example, user databases etc.
     */
    void onCreate();

    /**
     * Called before manager deletion to release and clean the resources created in {@link #onCreate()}.
     */
    void onDelete();
}
