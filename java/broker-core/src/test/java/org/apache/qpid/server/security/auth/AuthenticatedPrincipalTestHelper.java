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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;

/**
 * Helper class for testing that sets of principals contain {@link AuthenticatedPrincipal}'s that wrap
 * expected {@link Principal}'s.
 */
public class AuthenticatedPrincipalTestHelper
{
    public static void assertOnlyContainsWrapped(Principal wrappedPrincipal, Set<Principal> principals)
    {
        assertOnlyContainsWrappedAndSecondaryPrincipals(wrappedPrincipal, Collections.<Principal>emptySet(), principals);
    }


    public static void assertOnlyContainsWrappedAndSecondaryPrincipals(
            Principal      expectedWrappedPrincipal,
            Set<Principal> expectedSecondaryPrincipals,
            Set<Principal> actualPrincipals)
    {
        Assert.assertEquals("Principal set should contain one principal " + "but the principal set is: " + actualPrincipals,
                1 + expectedSecondaryPrincipals.size(),
                actualPrincipals.size());

        Set<Principal> expectedSet = new HashSet<Principal>(expectedSecondaryPrincipals);
        expectedSet.add(new AuthenticatedPrincipal(expectedWrappedPrincipal));

        Assert.assertEquals(expectedSet, actualPrincipals);
    }
}
