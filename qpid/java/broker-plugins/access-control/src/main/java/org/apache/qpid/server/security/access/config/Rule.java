/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.server.security.access.config;

import org.apache.commons.lang.builder.CompareToBuilder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.qpid.server.security.access.Permission;

/**
 * An access control v2 rule.
 * 
 * A rule consists of {@link Permission} for a particular identity to perform an {@link Action}. The identity
 * may be either a user or a group.
 */
public class Rule implements Comparable<Rule>
{
	/** String indicating all identitied. */
	public static final String ALL = "all";
	
    private Integer _number;
    private Boolean _enabled = Boolean.TRUE;
    private String _identity;
    private Action _action;
    private Permission _permission;
    
    public Rule(Integer number, String identity, Action action, Permission permission)
    {
        setNumber(number);
        setIdentity(identity);
        setAction(action);
        setPermission(permission);
    }
    
    public Rule(String identity, Action action, Permission permission)
    {
        this(null, identity, action, permission);
    }
    
    public boolean isEnabled()
    {
        return _enabled;
    }
    
    public void setEnabled(boolean enabled)
    {
        _enabled = enabled;
    }
    
    public void enable()
    {
        _enabled = Boolean.TRUE;
    }
    
    public void disable()
    {
        _enabled = Boolean.FALSE;
    }

    public Integer getNumber()
    {
        return _number;
    }

    public void setNumber(Integer number)
    {
        _number = number;
    }

    public String getIdentity()
    {
        return _identity;
    }

    public void setIdentity(String identity)
    {
        _identity = identity;
    }
    
    public Action getAction()
    {
        return _action;
    }

    public void setAction(Action action)
    {
        _action = action;
    }

    public Permission getPermission()
    {
        return _permission;
    }

    public void setPermission(Permission permission)
    {
        _permission = permission;
    }

    /** @see Comparable#compareTo(Object) */
    public int compareTo(Rule r)
    {
        return new CompareToBuilder()
                .append(getAction(), r.getAction())
                .append(getIdentity(), r.getIdentity())
                .append(getPermission(), r.getPermission())
                .toComparison();
    }

    /** @see Object#equals(Object) */
    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Rule))
        {
            return false;
        }
        Rule r = (Rule) o;
        
        return new EqualsBuilder()
                .append(getIdentity(), r.getIdentity())
                .append(getAction(), r.getAction())
                .append(getPermission(), r.getPermission())
                .isEquals();
    }

    /** @see Object#hashCode() */
    @Override
    public int hashCode()
    {
        return new HashCodeBuilder()
                .append(getIdentity())
                .append(getAction())
                .append(getPermission())
                .toHashCode();
    }

    /** @see Object#toString() */
    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("#", getNumber())
                .append("identity", getIdentity())
                .append("action", getAction())
                .append("permission", getPermission())
                .append("enabled", isEnabled())
                .toString();
    }
}
