/*
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
 */
package org.apache.qpid.server.security.auth;

import java.io.Serializable;
import java.security.Principal;
import java.util.Set;

import javax.security.auth.Subject;

import org.apache.qpid.server.security.auth.UsernamePrincipal;

/**
 * A simple Principal wrapper. Exists to allow us to identify the "primary" principal
 * by calling {@link Subject#getPrincipals(Class)}, passing in {@link AuthenticatedPrincipal}.class,
 * e.g. when logging.
 */
public final class AuthenticatedPrincipal implements Principal, Serializable
{
    private final Principal _wrappedPrincipal;

    /** convenience constructor for the common case where we're wrapping a {@link UsernamePrincipal} */
    public AuthenticatedPrincipal(String userPrincipalName)
    {
        this(new UsernamePrincipal(userPrincipalName));
    }

    public AuthenticatedPrincipal(Principal wrappedPrincipal)
    {
        if(wrappedPrincipal == null)
        {
            throw new IllegalArgumentException("Wrapped principal is null");
        }

        _wrappedPrincipal = wrappedPrincipal;
    }

    @Override
    public String getName()
    {
        return _wrappedPrincipal.getName();
    }

    @Override
    public int hashCode()
    {
        return _wrappedPrincipal.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (!(obj instanceof AuthenticatedPrincipal))
        {
            return false;
        }

        AuthenticatedPrincipal other = (AuthenticatedPrincipal) obj;

        return _wrappedPrincipal.equals(other._wrappedPrincipal);
    }

    public static AuthenticatedPrincipal getOptionalAuthenticatedPrincipalFromSubject(final Subject authSubject)
    {
        return getAuthenticatedPrincipalFromSubject(authSubject, true);
    }

    public static AuthenticatedPrincipal getAuthenticatedPrincipalFromSubject(final Subject authSubject)
    {
        return getAuthenticatedPrincipalFromSubject(authSubject, false);
    }

    private static AuthenticatedPrincipal getAuthenticatedPrincipalFromSubject(final Subject authSubject, boolean isPrincipalOptional)
    {
        if (authSubject == null)
        {
            throw new IllegalArgumentException("No authenticated subject.");
        }

        final Set<AuthenticatedPrincipal> principals = authSubject.getPrincipals(AuthenticatedPrincipal.class);
        int numberOfAuthenticatedPrincipals = principals.size();

        if(numberOfAuthenticatedPrincipals == 0 && isPrincipalOptional)
        {
            return null;
        }
        else
        {
            if (numberOfAuthenticatedPrincipals != 1)
            {
                throw new IllegalArgumentException(
                        "Can't find single AuthenticatedPrincipal in authenticated subject. There were "
                                + numberOfAuthenticatedPrincipals
                                + " authenticated principals out of a total number of principals of: " + authSubject.getPrincipals());
            }
            return principals.iterator().next();
        }
    }

    @Override
    public String toString()
    {
        return getName();
    }

}
