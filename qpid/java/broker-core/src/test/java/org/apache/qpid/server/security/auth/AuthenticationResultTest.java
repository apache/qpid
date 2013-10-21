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

import static org.apache.qpid.server.security.auth.AuthenticatedPrincipalTestHelper.assertOnlyContainsWrapped;
import static org.apache.qpid.server.security.auth.AuthenticatedPrincipalTestHelper.assertOnlyContainsWrappedAndSecondaryPrincipals;
import static org.mockito.Mockito.mock;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

public class AuthenticationResultTest extends TestCase
{
    public void testConstructWithAuthenticationStatusContinue()
    {
        AuthenticationResult authenticationResult = new AuthenticationResult(AuthenticationResult.AuthenticationStatus.CONTINUE);
        assertSame(AuthenticationResult.AuthenticationStatus.CONTINUE, authenticationResult.getStatus());
        assertTrue(authenticationResult.getPrincipals().isEmpty());
    }

    public void testConstructWithAuthenticationStatusError()
    {
        AuthenticationResult authenticationResult = new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR);
        assertSame(AuthenticationResult.AuthenticationStatus.ERROR, authenticationResult.getStatus());
        assertTrue(authenticationResult.getPrincipals().isEmpty());
    }

    public void testConstructWithAuthenticationStatusSuccessThrowsException()
    {
        try
        {
            new AuthenticationResult(AuthenticationResult.AuthenticationStatus.SUCCESS);
            fail("Exception not thrown");
        }
        catch(IllegalArgumentException e)
        {
            // PASS
        }
    }

    public void testConstructWithPrincipal()
    {
        Principal mainPrincipal = mock(Principal.class);
        AuthenticationResult authenticationResult = new AuthenticationResult(mainPrincipal);

        assertOnlyContainsWrapped(mainPrincipal, authenticationResult.getPrincipals());
        assertSame(AuthenticationResult.AuthenticationStatus.SUCCESS, authenticationResult.getStatus());
    }

    public void testConstructWithNullPrincipalThrowsException()
    {
        try
        {
            new AuthenticationResult((Principal)null);
            fail("Exception not thrown");
        }
        catch(IllegalArgumentException e)
        {
            // pass
        }
    }

    public void testConstructWithSetOfPrincipals()
    {
        Principal mainPrincipal = mock(Principal.class);
        Principal secondaryPrincipal = mock(Principal.class);
        Set<Principal> secondaryPrincipals = Collections.singleton(secondaryPrincipal);

        AuthenticationResult authenticationResult = new AuthenticationResult(mainPrincipal, secondaryPrincipals);

        assertOnlyContainsWrappedAndSecondaryPrincipals(mainPrincipal, secondaryPrincipals, authenticationResult.getPrincipals());
        assertSame(AuthenticationResult.AuthenticationStatus.SUCCESS, authenticationResult.getStatus());
    }

    public void testConstructWithSetOfPrincipalsDeDuplicatesMainPrincipal()
    {
        Principal mainPrincipal = mock(Principal.class);
        Principal secondaryPrincipal = mock(Principal.class);

        Set<Principal> secondaryPrincipalsContainingDuplicateOfMainPrincipal = new HashSet<Principal>(
                Arrays.asList(secondaryPrincipal, mainPrincipal));
        Set<Principal> deDuplicatedSecondaryPrincipals = Collections.singleton(secondaryPrincipal);

        AuthenticationResult authenticationResult = new AuthenticationResult(
                mainPrincipal, secondaryPrincipalsContainingDuplicateOfMainPrincipal);

        assertOnlyContainsWrappedAndSecondaryPrincipals(mainPrincipal, deDuplicatedSecondaryPrincipals, authenticationResult.getPrincipals());

        assertSame(AuthenticationResult.AuthenticationStatus.SUCCESS, authenticationResult.getStatus());
    }
}
