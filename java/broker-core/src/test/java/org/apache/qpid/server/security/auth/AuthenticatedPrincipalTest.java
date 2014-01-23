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

import java.security.Principal;

import javax.security.auth.Subject;

import org.apache.qpid.server.security.auth.UsernamePrincipal;

import junit.framework.TestCase;

public class AuthenticatedPrincipalTest extends TestCase
{

    private AuthenticatedPrincipal _authenticatedPrincipal = new AuthenticatedPrincipal(new UsernamePrincipal("name"));

    public void testGetAuthenticatedPrincipalFromSubject()
    {
        final Subject subject = createSubjectContainingAuthenticatedPrincipal();
        final AuthenticatedPrincipal actual = AuthenticatedPrincipal.getAuthenticatedPrincipalFromSubject(subject);
        assertSame(_authenticatedPrincipal, actual);
    }

    public void testAuthenticatedPrincipalNotInSubject()
    {
        try
        {
            AuthenticatedPrincipal.getAuthenticatedPrincipalFromSubject(new Subject());
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException iae)
        {
            // PASS
        }
    }

    public void testGetOptionalAuthenticatedPrincipalFromSubject()
    {
        final Subject subject = createSubjectContainingAuthenticatedPrincipal();
        final AuthenticatedPrincipal actual = AuthenticatedPrincipal.getOptionalAuthenticatedPrincipalFromSubject(subject);
        assertSame(_authenticatedPrincipal, actual);
    }

    public void testGetOptionalAuthenticatedPrincipalFromSubjectReturnsNullIfMissing()
    {
        Subject subjectWithNoPrincipals = new Subject();
        assertNull(AuthenticatedPrincipal.getOptionalAuthenticatedPrincipalFromSubject(subjectWithNoPrincipals));

        Subject subjectWithoutAuthenticatedPrincipal = new Subject();
        subjectWithoutAuthenticatedPrincipal.getPrincipals().add(new UsernamePrincipal("name1"));
        assertNull("Should return null for a subject containing a principal that isn't an AuthenticatedPrincipal",
                AuthenticatedPrincipal.getOptionalAuthenticatedPrincipalFromSubject(subjectWithoutAuthenticatedPrincipal));
    }

    public void testTooManyAuthenticatedPrincipalsInSubject()
    {
        final Subject subject = new Subject();
        subject.getPrincipals().add(new AuthenticatedPrincipal(new UsernamePrincipal("name1")));
        subject.getPrincipals().add(new AuthenticatedPrincipal(new UsernamePrincipal("name2")));

        try
        {
            AuthenticatedPrincipal.getAuthenticatedPrincipalFromSubject(subject);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException iae)
        {
            // PASS
        }
    }

    private Subject createSubjectContainingAuthenticatedPrincipal()
    {
        final Principal other = new Principal()
        {
            public String getName()
            {
                return "otherprincipal";
            }
        };

        final Subject subject = new Subject();
        subject.getPrincipals().add(_authenticatedPrincipal);
        subject.getPrincipals().add(other);
        return subject;
    }

    public void testEqualsAndHashcode()
    {
        AuthenticatedPrincipal user1principal1 = new AuthenticatedPrincipal(new UsernamePrincipal("user1"));
        AuthenticatedPrincipal user1principal2 = new AuthenticatedPrincipal(new UsernamePrincipal("user1"));

        assertTrue(user1principal1.equals(user1principal1));
        assertTrue(user1principal1.equals(user1principal2));
        assertTrue(user1principal2.equals(user1principal1));

        assertEquals(user1principal1.hashCode(), user1principal2.hashCode());
    }

    public void testEqualsAndHashcodeWithSameWrappedObject()
    {
        UsernamePrincipal wrappedPrincipal = new UsernamePrincipal("user1");
        AuthenticatedPrincipal user1principal1 = new AuthenticatedPrincipal(wrappedPrincipal);
        AuthenticatedPrincipal user1principal2 = new AuthenticatedPrincipal(wrappedPrincipal);

        assertTrue(user1principal1.equals(user1principal1));
        assertTrue(user1principal1.equals(user1principal2));
        assertTrue(user1principal2.equals(user1principal1));

        assertEquals(user1principal1.hashCode(), user1principal2.hashCode());
    }

    public void testEqualsWithDifferentUsernames()
    {
        AuthenticatedPrincipal user1principal1 = new AuthenticatedPrincipal(new UsernamePrincipal("user1"));
        AuthenticatedPrincipal user1principal2 = new AuthenticatedPrincipal(new UsernamePrincipal("user2"));

        assertFalse(user1principal1.equals(user1principal2));
        assertFalse(user1principal2.equals(user1principal1));
    }

    public void testEqualsWithDissimilarObjects()
    {
        UsernamePrincipal wrappedPrincipal = new UsernamePrincipal("user1");
        AuthenticatedPrincipal authenticatedPrincipal = new AuthenticatedPrincipal(wrappedPrincipal);

        assertFalse(authenticatedPrincipal.equals(wrappedPrincipal));
        assertFalse(wrappedPrincipal.equals(authenticatedPrincipal));
    }
}
