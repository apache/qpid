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

import java.net.InetAddress;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.firewall.FirewallRule;

/**
 * I represent an {@link Action} taken by a client from a known address. The address is used to
 * determine if I match an {@link AclAction}, which may contain firewall rules.
 */
public class ClientAction
{
    private Action _clientAction;

    public ClientAction(Action clientAction)
    {
        _clientAction = clientAction;
    }

    public ClientAction(Operation operation, ObjectType objectType, ObjectProperties properties)
    {
        _clientAction = new Action(operation, objectType, properties);
    }

    public boolean matches(AclAction ruleAction, InetAddress addressOfClient)
    {
        return _clientAction.matches(ruleAction.getAction())
                && addressOfClientMatches(ruleAction, addressOfClient);
    }

    private boolean addressOfClientMatches(AclAction ruleAction, InetAddress addressOfClient)
    {
        FirewallRule firewallRule = ruleAction.getFirewallRule();
        if(firewallRule == null || addressOfClient == null)
        {
            return true;
        }
        else
        {
            return firewallRule.matches(addressOfClient);
        }
    }

    public Operation getOperation()
    {
        return _clientAction.getOperation();
    }

    public ObjectType getObjectType()
    {
        return _clientAction.getObjectType();
    }

    public ObjectProperties getProperties()
    {
        return _clientAction.getProperties();
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append(_clientAction).toString();
    }
}
