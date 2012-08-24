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

import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class GroupPrincipalAccessor
{
    private final List<GroupManager> _groupManagerList;

    public GroupPrincipalAccessor(List<GroupManager> groupManagerList)
    {
        _groupManagerList = groupManagerList;
    }

    public Set<Principal> getGroupPrincipals(String username)
    {
        Set<Principal> principals = new HashSet<Principal>();
        for (GroupManager groupManager : _groupManagerList)
        {
            Set<Principal> groups = groupManager.getGroupPrincipalsForUser(username);
            if (groups != null)
            {
                principals.addAll(groups);
            }
        }

        return Collections.unmodifiableSet(principals);
    }
}
