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
import java.security.acl.Group;
import java.util.Enumeration;

/**
 * Immutable representation of a user group.  In Qpid, groups do <b>not</b> know
 * about their membership, and therefore the {@link #addMember(Principal)}
 * methods etc throw {@link UnsupportedOperationException}.
 *
 */
public class GroupPrincipal implements Group
{
    /** Name of the group */
    private final String _groupName;
    
    public GroupPrincipal(final String groupName)
    {
        _groupName = groupName;
    }

    public String getName()
    {
        return _groupName;
    }

    public boolean addMember(Principal user)
    {
        throw new UnsupportedOperationException("Not supported");
    }

    public boolean removeMember(Principal user)
    {
        throw new UnsupportedOperationException("Not supported");
    }

    public boolean isMember(Principal member)
    {
        throw new UnsupportedOperationException("Not supported");
    }

    public Enumeration<? extends Principal> members()
    {
        throw new UnsupportedOperationException("Not supported");
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    public int hashCode()
    {
        final int prime = 37;
        return prime * _groupName.hashCode();
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        else 
        {
            if (obj instanceof GroupPrincipal)
            {
                GroupPrincipal other = (GroupPrincipal) obj;
                return _groupName.equals(other._groupName);
            }
            else
            {
                return false;
            }
        }
    }
}
