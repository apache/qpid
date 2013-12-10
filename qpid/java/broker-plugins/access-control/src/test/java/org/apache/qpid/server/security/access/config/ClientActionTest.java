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

import static org.mockito.Mockito.*;

import java.net.InetAddress;

import org.apache.qpid.server.security.access.firewall.FirewallRule;

import junit.framework.TestCase;

public class ClientActionTest extends TestCase
{
    private Action _action = mock(Action.class);
    private AclAction _ruleAction = mock(AclAction.class);
    private InetAddress _addressOfClient = mock(InetAddress.class);

    private ClientAction _clientAction = new ClientAction(_action);

    public void testMatches_returnsTrueWhenActionsMatchAndNoFirewallRule()
    {
        when(_action.matches(any(Action.class))).thenReturn(true);
        when(_ruleAction.getFirewallRule()).thenReturn(null);

        assertTrue(_clientAction.matches(_ruleAction, _addressOfClient));
    }

    public void testMatches_returnsFalseWhenActionsDontMatch()
    {
        FirewallRule firewallRule = mock(FirewallRule.class);
        when(firewallRule.matches(_addressOfClient)).thenReturn(true);

        when(_action.matches(any(Action.class))).thenReturn(false);
        when(_ruleAction.getFirewallRule()).thenReturn(firewallRule);

        assertFalse(_clientAction.matches(_ruleAction, _addressOfClient));
    }

    public void testMatches_returnsTrueWhenActionsAndFirewallRuleMatch()
    {
        FirewallRule firewallRule = mock(FirewallRule.class);
        when(firewallRule.matches(_addressOfClient)).thenReturn(true);

        when(_action.matches(any(Action.class))).thenReturn(true);
        when(_ruleAction.getFirewallRule()).thenReturn(firewallRule);

        assertTrue(_clientAction.matches(_ruleAction, _addressOfClient));
    }

    public void testMatches_ignoresFirewallRuleIfClientAddressIsNull()
    {
        FirewallRule firewallRule = mock(FirewallRule.class);

        when(_action.matches(any(Action.class))).thenReturn(true);
        when(_ruleAction.getFirewallRule()).thenReturn(firewallRule);

        assertTrue(_clientAction.matches(_ruleAction, null));

        verifyZeroInteractions(firewallRule);
    }

}
