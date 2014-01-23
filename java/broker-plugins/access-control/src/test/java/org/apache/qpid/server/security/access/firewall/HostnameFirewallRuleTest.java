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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;

import org.apache.qpid.server.security.access.firewall.HostnameFirewallRule;

import junit.framework.TestCase;

public class HostnameFirewallRuleTest extends TestCase
{
    private InetAddress _addressNotInRule;

    private HostnameFirewallRule _HostnameFirewallRule;

    @Override
    protected void setUp() throws Exception
    {
        _addressNotInRule = InetAddress.getByName("127.0.0.1");
    }

    public void testSingleHostname() throws Exception
    {
        String hostnameInRule = "hostnameInRule";
        InetAddress addressWithMatchingHostname = mock(InetAddress.class);
        when(addressWithMatchingHostname.getCanonicalHostName()).thenReturn(hostnameInRule);

        _HostnameFirewallRule = new HostnameFirewallRule(hostnameInRule);

        assertFalse(_HostnameFirewallRule.matches(_addressNotInRule));
        assertTrue(_HostnameFirewallRule.matches(addressWithMatchingHostname));
    }

    public void testSingleHostnameWildcard() throws Exception
    {
        String hostnameInRule = ".*FOO.*";
        InetAddress addressWithMatchingHostname = mock(InetAddress.class);
        when(addressWithMatchingHostname.getCanonicalHostName()).thenReturn("xxFOOxx");

        _HostnameFirewallRule = new HostnameFirewallRule(hostnameInRule);

        assertFalse(_HostnameFirewallRule.matches(_addressNotInRule));
        assertTrue(_HostnameFirewallRule.matches(addressWithMatchingHostname));
    }

    public void testMultipleHostnames() throws Exception
    {
        String[] hostnamesInRule = new String[] {"hostnameInRule1", "hostnameInRule2"};

        _HostnameFirewallRule = new HostnameFirewallRule(hostnamesInRule);

        assertFalse(_HostnameFirewallRule.matches(_addressNotInRule));
        for (String hostnameInRule : hostnamesInRule)
        {
            InetAddress addressWithMatchingHostname = mock(InetAddress.class);
            when(addressWithMatchingHostname.getCanonicalHostName()).thenReturn(hostnameInRule);

            assertTrue(_HostnameFirewallRule.matches(addressWithMatchingHostname));
        }
    }

    public void testEqualsAndHashCode()
    {
        String hostname1 = "hostname1";
        String hostname2 = "hostname2";

        HostnameFirewallRule rule = new HostnameFirewallRule(hostname1, hostname2);
        HostnameFirewallRule equalRule = new HostnameFirewallRule(hostname1, hostname2);

        assertTrue(rule.equals(rule));
        assertTrue(rule.equals(equalRule));
        assertTrue(equalRule.equals(rule));

        assertTrue(rule.hashCode() == equalRule.hashCode());

        assertFalse("Different hostnames should cause rules to be unequal",
                rule.equals(new HostnameFirewallRule(hostname1, "different-hostname")));
    }
}
