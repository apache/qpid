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
package org.apache.qpid.server.security.auth;

import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


/**
 * Encapsulates the result of an attempt to authenticate using an {@link org.apache.qpid.server.model.AuthenticationProvider}.
 * <p>
 * The authentication status describes the overall outcome.
 * <p>
 * <ol>
 *  <li>If authentication status is SUCCESS, at least one {@link Principal} will be populated.
 *  </li>
 *  <li>If authentication status is CONTINUE, the authentication has failed because the user
 *      supplied incorrect credentials (etc).  If the authentication requires it, the next challenge
 *      is made available.
 *  </li>
 *  <li>If authentication status is ERROR , the authentication decision could not be made due
 *      to a failure (such as an external system), the {@link AuthenticationResult#getCause()}
 *      will provide the underlying exception.
 *  </li>
 * </ol>
 *
 * The main principal provided to the constructor is wrapped in an {@link AuthenticatedPrincipal}
 * to make it easier for the rest of the application to identify it among the set of other principals.
 */
public class AuthenticationResult
{
    public enum AuthenticationStatus
    {
        /** Authentication successful */
        SUCCESS,
        /** Authentication not successful due to credentials problem etc */
        CONTINUE,
        /** Problem prevented the authentication from being made e.g. failure of an external system */
        ERROR
    }

    private final AuthenticationStatus _status;
    private final byte[] _challenge;
    private final Exception _cause;
    private final Set<Principal> _principals = new HashSet<Principal>();
    private final Principal _mainPrincipal;

    public AuthenticationResult(final AuthenticationStatus status)
    {
        this(null, status, null);
    }

    public AuthenticationResult(Principal mainPrincipal)
    {
        this(mainPrincipal, Collections.<Principal>emptySet());
    }

    public AuthenticationResult(Principal mainPrincipal, Set<Principal> otherPrincipals)
    {
        AuthenticatedPrincipal specialQpidAuthenticatedPrincipal = new AuthenticatedPrincipal(mainPrincipal);
        _principals.addAll(otherPrincipals);
        _principals.remove(mainPrincipal);
        _principals.add(specialQpidAuthenticatedPrincipal);
        _mainPrincipal = mainPrincipal;

        _status = AuthenticationStatus.SUCCESS;
        _challenge = null;
        _cause = null;
    }

    public AuthenticationResult(final byte[] challenge, final AuthenticationStatus status)
    {
        _challenge = challenge;
        _status = status;
        _cause = null;
        _mainPrincipal = null;
    }

    public AuthenticationResult(final AuthenticationStatus error, final Exception cause)
    {
        _status = error;
        _challenge = null;
        _cause = cause;
        _mainPrincipal = null;
    }

    public AuthenticationResult(final byte[] challenge, final AuthenticationStatus status, final Exception cause)
    {
        if(status == AuthenticationStatus.SUCCESS)
        {
            throw new IllegalArgumentException("Successful authentication requires at least one principal");
        }

        _status = status;
        _challenge = challenge;
        _cause = cause;
        _mainPrincipal = null;
    }

    public Exception getCause()
    {
        return _cause;
    }

    public AuthenticationStatus getStatus()
    {
        return _status;
    }

    public byte[] getChallenge()
    {
        return _challenge;
    }

    public Set<Principal> getPrincipals()
    {
        return _principals;
    }

    public Principal getMainPrincipal()
    {
        return _mainPrincipal;
    }
}
