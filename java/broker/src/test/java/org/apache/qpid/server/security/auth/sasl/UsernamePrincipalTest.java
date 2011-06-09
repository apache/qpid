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
package org.apache.qpid.server.security.auth.sasl;

import java.security.Principal;
import javax.security.auth.Subject;
import junit.framework.TestCase;

/**
 * Tests the UsernamePrincipal.
 *
 */
public class UsernamePrincipalTest extends TestCase
{
    public void testEqualitySameObject()
    {
        final UsernamePrincipal principal = new UsernamePrincipal("string");
        assertTrue(principal.equals(principal));
    }

    public void testEqualitySameName()
    {
        final String string = "string";
        final UsernamePrincipal principal1 = new UsernamePrincipal(string);
        final UsernamePrincipal principal2 = new UsernamePrincipal(string);
        assertTrue(principal1.equals(principal2));
    }

    public void testEqualityEqualName()
    {
        final UsernamePrincipal principal1 = new UsernamePrincipal(new String("string"));
        final UsernamePrincipal principal2 = new UsernamePrincipal(new String("string"));
        assertTrue(principal1.equals(principal2));
    }

    public void testInequalityDifferentUserPrincipals()
    {
        UsernamePrincipal principal1 = new UsernamePrincipal("string1");
        UsernamePrincipal principal2 = new UsernamePrincipal("string2");
        assertFalse(principal1.equals(principal2));
    }

    public void testInequalityNonUserPrincipal()
    {
        UsernamePrincipal principal = new UsernamePrincipal("string");
        assertFalse(principal.equals(new String("string")));
    }

    public void testInequalityNull()
    {
        UsernamePrincipal principal = new UsernamePrincipal("string");
        assertFalse(principal.equals(null));
    }

    public void testGetUsernamePrincipalFromSubject()
    {
        final UsernamePrincipal expected = new UsernamePrincipal("name");
        final Principal other = new Principal()
        {

            @Override
            public String getName()
            {
                return "otherprincipal";
            }
        };

        final Subject subject = new Subject();
        subject.getPrincipals().add(expected);
        subject.getPrincipals().add(other);

        final UsernamePrincipal actual = UsernamePrincipal.getUsernamePrincipalFromSubject(subject);
        assertSame(expected, actual);
    }

    public void testUsernamePrincipalNotInSubject()
    {
        try
        {
            UsernamePrincipal.getUsernamePrincipalFromSubject(new Subject());
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException iae)
        {
            // PASS
        }
    }

    public void testTooManyUsernamePrincipalInSubject()
    {
        final Subject subject = new Subject();
        subject.getPrincipals().add(new UsernamePrincipal("name1"));
        subject.getPrincipals().add(new UsernamePrincipal("name2"));
        try
        {

            UsernamePrincipal.getUsernamePrincipalFromSubject(subject);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException iae)
        {
            // PASS
        }
    }

}
