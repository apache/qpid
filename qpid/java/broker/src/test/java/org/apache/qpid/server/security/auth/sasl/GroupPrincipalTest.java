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

import junit.framework.TestCase;

public class GroupPrincipalTest extends TestCase
{
    public void testGetName()
    {
        final GroupPrincipal principal = new GroupPrincipal("group");
        assertEquals("group", principal.getName());
    }

    public void testAddRejected()
    {
        final GroupPrincipal principal = new GroupPrincipal("group");
        final UsernamePrincipal user = new UsernamePrincipal("name");
        
        try
        {
            principal.addMember(user);
            fail("Exception not thrown");
        }
        catch (UnsupportedOperationException uso)
        {
            // PASS
        }
    }
    
    public void testEqualitySameName()
    {
        final String string = "string";
        final GroupPrincipal principal1 = new GroupPrincipal(string);
        final GroupPrincipal principal2 = new GroupPrincipal(string);
        assertTrue(principal1.equals(principal2));
    }

    public void testEqualityEqualName()
    {
        final GroupPrincipal principal1 = new GroupPrincipal(new String("string"));
        final GroupPrincipal principal2 = new GroupPrincipal(new String("string"));
        assertTrue(principal1.equals(principal2));
    }

    public void testInequalityDifferentGroupPrincipals()
    {
        GroupPrincipal principal1 = new GroupPrincipal("string1");
        GroupPrincipal principal2 = new GroupPrincipal("string2");
        assertFalse(principal1.equals(principal2));
    }

    public void testInequalityNonGroupPrincipal()
    {
        GroupPrincipal principal = new GroupPrincipal("string");
        assertFalse(principal.equals(new UsernamePrincipal("string")));
    }

    public void testInequalityNull()
    {
        GroupPrincipal principal = new GroupPrincipal("string");
        assertFalse(principal.equals(null));
    }

    


}
