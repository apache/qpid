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
package org.apache.qpid.server.security;

import java.security.Principal;

import javax.security.auth.Subject;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.group.GroupPrincipalAccessor;

/**
 * Creates a {@link Subject} formed by the {@link Principal}'s returned from:
 * <ol>
 * <li>Authenticating using an {@link AuthenticationManager}</li>
 * <li>A {@link GroupPrincipalAccessor}</li>
 * </ol>
 *
 * <p>
 * SubjectCreator is a facade to the {@link AuthenticationManager}, and is intended to be
 * the single place that {@link Subject}'s are created in the broker.
 * </p>
 */
public class SubjectCreator
{
    private AuthenticationManager _authenticationManager;
    private GroupPrincipalAccessor _groupAccessor;

    public SubjectCreator(AuthenticationManager authenticationManager, GroupPrincipalAccessor groupAccessor)
    {
        _authenticationManager = authenticationManager;
        _groupAccessor = groupAccessor;
    }

   /**
    * Gets the known SASL mechanisms
    *
    * @return SASL mechanism names, space separated.
    */
    public String getMechanisms()
    {
        return _authenticationManager.getMechanisms();
    }

    /**
     * @see AuthenticationManager#createSaslServer(String, String, Principal)
     */
    public SaslServer createSaslServer(String mechanism, String localFQDN, Principal externalPrincipal) throws SaslException
    {
        return _authenticationManager.createSaslServer(mechanism, localFQDN, externalPrincipal);
    }

    /**
     * Authenticates a user using SASL negotiation.
     *
     * @param server SASL server
     * @param response SASL response to process
     */
    public SubjectAuthenticationResult authenticate(SaslServer server, byte[] response)
    {
        AuthenticationResult authenticationResult = _authenticationManager.authenticate(server, response);
        if(server.isComplete())
        {
            String username = server.getAuthorizationID();

            return createResultWithGroups(username, authenticationResult);
        }
        else
        {
            return new SubjectAuthenticationResult(authenticationResult);
        }
    }

    /**
     * Authenticates a user using their username and password.
     */
    public SubjectAuthenticationResult authenticate(String username, String password)
    {
        final AuthenticationResult authenticationResult = _authenticationManager.authenticate(username, password);

        return createResultWithGroups(username, authenticationResult);
    }

    private SubjectAuthenticationResult createResultWithGroups(String username, final AuthenticationResult authenticationResult)
    {
        if(authenticationResult.getStatus() == AuthenticationStatus.SUCCESS)
        {
            final Subject authenticationSubject = new Subject();

            authenticationSubject.getPrincipals().addAll(authenticationResult.getPrincipals());
            authenticationSubject.getPrincipals().addAll(_groupAccessor.getGroupPrincipals(username));

            authenticationSubject.setReadOnly();

            return new SubjectAuthenticationResult(authenticationResult, authenticationSubject);
        }
        else
        {
            return new SubjectAuthenticationResult(authenticationResult);
        }
    }

    public Subject createSubjectWithGroups(String username)
    {
        Subject authenticationSubject = new Subject();

        authenticationSubject.getPrincipals().add(new AuthenticatedPrincipal(username));
        authenticationSubject.getPrincipals().addAll(_groupAccessor.getGroupPrincipals(username));
        authenticationSubject.setReadOnly();

        return authenticationSubject;
    }
}
