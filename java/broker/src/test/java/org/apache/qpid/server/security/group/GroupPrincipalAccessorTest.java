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
package org.apache.qpid.server.security.group;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.qpid.server.model.GroupProvider;

public class GroupPrincipalAccessorTest extends TestCase
{
    private static final String USERNAME = "username";

    private GroupProvider _groupManager1 = mock(GroupProvider.class);
    private GroupProvider _groupManager2 = mock(GroupProvider.class);

    private Principal _group1 = mock(Principal.class);
    private Principal _group2 = mock(Principal.class);

    @Override
    public void setUp()
    {
        when(_groupManager1.getGroupPrincipalsForUser(USERNAME)).thenReturn(Collections.singleton(_group1));
        when(_groupManager2.getGroupPrincipalsForUser(USERNAME)).thenReturn(Collections.singleton(_group2));
    }

    public void testGetGroupPrincipals()
    {
        getAndAssertGroupPrincipals(_group1, _group2);
    }

    public void testGetGroupPrincipalsWhenAGroupManagerReturnsNull()
    {
        when(_groupManager1.getGroupPrincipalsForUser(USERNAME)).thenReturn(null);

        getAndAssertGroupPrincipals(_group2);
    }

    public void testGetGroupPrincipalsWhenAGroupManagerReturnsEmptySet()
    {
        when(_groupManager2.getGroupPrincipalsForUser(USERNAME)).thenReturn(new HashSet<Principal>());

        getAndAssertGroupPrincipals(_group1);
    }

    private void getAndAssertGroupPrincipals(Principal... expectedGroups)
    {
        GroupPrincipalAccessor groupPrincipalAccessor = new GroupPrincipalAccessor(Arrays.asList(_groupManager1, _groupManager2));

        Set<Principal> actualGroupPrincipals = groupPrincipalAccessor.getGroupPrincipals(USERNAME);

        Set<Principal> expectedGroupPrincipals = new HashSet<Principal>(Arrays.asList(expectedGroups));

        assertEquals(expectedGroupPrincipals, actualGroupPrincipals);
    }
}
