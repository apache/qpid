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

import static org.apache.qpid.server.security.access.ObjectProperties.Property.*;

import org.apache.qpid.server.security.access.firewall.FirewallRule;
import org.apache.qpid.server.security.access.firewall.FirewallRuleFactory;

import static org.mockito.Mockito.*;

import junit.framework.TestCase;

public class AclRulePredicatesTest extends TestCase
{
    private AclRulePredicates _aclRulePredicates = new AclRulePredicates();
    private FirewallRuleFactory _firewallRuleFactory = mock(FirewallRuleFactory.class);

    @Override
    protected void setUp() throws Exception
    {
        _aclRulePredicates.setFirewallRuleFactory(_firewallRuleFactory);

        when(_firewallRuleFactory.createForHostname((String[]) any())).thenReturn(mock(FirewallRule.class));
        when(_firewallRuleFactory.createForNetwork((String[]) any())).thenReturn(mock(FirewallRule.class));
    }

    public void testParse()
    {
        String name = "name";
        String className = "class";

        _aclRulePredicates.parse(NAME.name(), name);
        _aclRulePredicates.parse(CLASS.name(), className);

        assertEquals(name, _aclRulePredicates.getObjectProperties().get(NAME));
        assertEquals(className, _aclRulePredicates.getObjectProperties().get(CLASS));
    }

    public void testParseHostnameFirewallRule()
    {
        String hostname = "hostname1,hostname2";
        _aclRulePredicates.parse(FROM_HOSTNAME.name(), hostname);

        verify(_firewallRuleFactory).createForHostname(new String[] {"hostname1", "hostname2"});
    }

    public void testParseNetworkFirewallRule()
    {
        _aclRulePredicates.setFirewallRuleFactory(_firewallRuleFactory);

        String networks = "network1,network2";
        _aclRulePredicates.parse(FROM_NETWORK.name(), networks);

        verify(_firewallRuleFactory).createForNetwork(new String[] {"network1", "network2"});
    }

    public void testParseThrowsExceptionIfBothHostnameAndNetworkSpecified()
    {
        _aclRulePredicates.parse(FROM_NETWORK.name(), "network1,network2");
        try
        {
            _aclRulePredicates.parse(FROM_HOSTNAME.name(), "hostname1,hostname2");
            fail("Exception not thrown");
        }
        catch(IllegalStateException e)
        {
            // pass
        }
    }
}
