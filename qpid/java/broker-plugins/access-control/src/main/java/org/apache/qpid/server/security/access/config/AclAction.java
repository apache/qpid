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
package org.apache.qpid.server.security.access.config;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.firewall.FirewallRule;

public class AclAction
{
    private Action _action;
    private FirewallRule _firewallRule;

    public AclAction(Operation operation, ObjectType object, AclRulePredicates predicates)
    {
        _action = new Action(operation, object, predicates.getObjectProperties());
        _firewallRule = predicates.getFirewallRule();
    }

    public AclAction(Operation operation)
    {
        _action = new Action(operation);
    }

    public AclAction(Operation operation, ObjectType object, ObjectProperties properties)
    {
        _action = new Action(operation, object, properties);
    }

    public FirewallRule getFirewallRule()
    {
        return _firewallRule;
    }

    public Action getAction()
    {
        return _action;
    }

    public boolean isAllowed()
    {
        return _action.isAllowed();
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder()
            .append(_action)
            .append(_firewallRule).toHashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
        {
            return false;
        }
        if (obj == this)
        {
            return true;
        }
        if (obj.getClass() != getClass())
        {
            return false;
        }
        AclAction rhs = (AclAction) obj;
        return new EqualsBuilder()
            .append(_action, rhs._action)
            .append(_firewallRule, rhs._firewallRule).isEquals();
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append(_action)
            .append(_firewallRule).toString();
    }
}
