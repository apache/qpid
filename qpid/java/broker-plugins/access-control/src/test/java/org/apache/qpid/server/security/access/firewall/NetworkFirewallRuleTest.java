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
package org.apache.qpid.server.security.access.firewall;

import java.net.InetAddress;

import org.apache.qpid.server.security.access.firewall.NetworkFirewallRule;

import junit.framework.TestCase;

public class NetworkFirewallRuleTest extends TestCase
{
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final String OTHER_IP_1 = "192.168.23.1";
    private static final String OTHER_IP_2 = "192.168.23.2";

    private InetAddress _addressNotInRule;

    private NetworkFirewallRule _networkFirewallRule;

    @Override
    protected void setUp() throws Exception
    {
        _addressNotInRule = InetAddress.getByName(LOCALHOST_IP);
    }

    public void testIpRule() throws Exception
    {
        String ipAddressInRule = OTHER_IP_1;

        _networkFirewallRule = new NetworkFirewallRule(ipAddressInRule);

        assertFalse(_networkFirewallRule.matches(_addressNotInRule));
        assertTrue(_networkFirewallRule.matches(InetAddress.getByName(ipAddressInRule)));
    }

    public void testNetMask() throws Exception
    {
        String ipAddressInRule = "192.168.23.0/24";
        _networkFirewallRule = new NetworkFirewallRule(ipAddressInRule);

        assertFalse(_networkFirewallRule.matches(InetAddress.getByName("192.168.24.1")));
        assertTrue(_networkFirewallRule.matches(InetAddress.getByName("192.168.23.0")));
        assertTrue(_networkFirewallRule.matches(InetAddress.getByName("192.168.23.255")));
    }

    public void testWildcard() throws Exception
    {
        // Test xxx.xxx.*

        assertFalse(new NetworkFirewallRule("192.168.*")
             .matches(InetAddress.getByName("192.169.1.0")));

        assertTrue(new NetworkFirewallRule("192.168.*")
            .matches(InetAddress.getByName("192.168.1.0")));

        assertTrue(new NetworkFirewallRule("192.168.*")
            .matches(InetAddress.getByName("192.168.255.255")));

        // Test xxx.xxx.xxx.*

        assertFalse(new NetworkFirewallRule("192.168.1.*")
             .matches(InetAddress.getByName("192.169.2.0")));

        assertTrue(new NetworkFirewallRule("192.168.1.*")
            .matches(InetAddress.getByName("192.168.1.0")));

        assertTrue(new NetworkFirewallRule("192.168.1.*")
            .matches(InetAddress.getByName("192.168.1.255")));
    }

    public void testMultipleNetworks() throws Exception
    {
        String[] ipAddressesInRule = new String[] {OTHER_IP_1, OTHER_IP_2};

        _networkFirewallRule = new NetworkFirewallRule(ipAddressesInRule);

        assertFalse(_networkFirewallRule.matches(_addressNotInRule));
        for (String ipAddressInRule : ipAddressesInRule)
        {
            assertTrue(_networkFirewallRule.matches(InetAddress.getByName(ipAddressInRule)));
        }
    }

    public void testEqualsAndHashCode()
    {
        NetworkFirewallRule rule = new NetworkFirewallRule(LOCALHOST_IP, OTHER_IP_1);
        NetworkFirewallRule equalRule = new NetworkFirewallRule(LOCALHOST_IP, OTHER_IP_1);

        assertTrue(rule.equals(rule));
        assertTrue(rule.equals(equalRule));
        assertTrue(equalRule.equals(rule));

        assertTrue(rule.hashCode() == equalRule.hashCode());

        assertFalse("Different networks should cause rules to be unequal",
                rule.equals(new NetworkFirewallRule(LOCALHOST_IP, OTHER_IP_2)));
    }
}
