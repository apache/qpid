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
package org.apache.qpid.server.security.access;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.security.Result;
import org.apache.qpid.server.security.access.plugins.Firewall;
import org.apache.qpid.server.security.access.plugins.FirewallConfiguration;
import org.apache.qpid.server.util.InternalBrokerBaseCase;

public class FirewallPluginTest extends InternalBrokerBaseCase
{
    public class RuleInfo
    {
        private String _access;
        private String _network;
        private String _hostname;
        
        public void setAccess(String _access)
        {
            this._access = _access;
        }
        
        public String getAccess()
        {
            return _access;
        }
        
        public void setNetwork(String _network)
        {
            this._network = _network;
        }
        
        public String getNetwork()
        {
            return _network;
        }
        
        public void setHostname(String _hostname)
        {
            this._hostname = _hostname;
        }
        
        public String getHostname()
        {
            return _hostname;
        }
    }

    // IP address
    private SocketAddress _address;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        
        _address = new InetSocketAddress("127.0.0.1", 65535);
    }

    private Firewall initialisePlugin(String defaultAction, RuleInfo[] rules) throws IOException, ConfigurationException
    {
        // Create sample config file
        File confFile = File.createTempFile(getClass().getSimpleName()+"conffile", null);
        confFile.deleteOnExit();
        BufferedWriter buf = new BufferedWriter(new FileWriter(confFile));
        buf.write("<firewall default-action=\""+defaultAction+"\">\n");
        if (rules != null)
        {
            for (RuleInfo rule : rules)
            {
                buf.write("<rule");
                buf.write(" access=\""+rule.getAccess()+"\"");
                if (rule.getHostname() != null)
                {
                    buf.write(" hostname=\""+rule.getHostname()+"\"");
                }
                if (rule.getNetwork() != null)
                {
                    buf.write(" network=\""+rule.getNetwork()+"\"");
                }
                buf.write("/>\n");
            }
        }
        buf.write("</firewall>");
        buf.close();
        
        // Configure plugin
        FirewallConfiguration config = new FirewallConfiguration();
        config.setConfiguration("", new XMLConfiguration(confFile));
        Firewall plugin = new Firewall();
        plugin.configure(config);
        return plugin;
    }

    private Firewall initialisePlugin(String string) throws ConfigurationException, IOException
    {
        return initialisePlugin(string, null);
    }
    
    public void testDefaultAction() throws Exception
    {
        // Test simple deny
        Firewall plugin = initialisePlugin("deny");
        assertEquals(Result.DENIED, plugin.access(ObjectType.VIRTUALHOST, _address));

        // Test simple allow
        plugin = initialisePlugin("allow");
        assertEquals(Result.ALLOWED, plugin.access(ObjectType.VIRTUALHOST, _address));
    }
    

    public void testSingleIPRule() throws Exception
    {
        RuleInfo rule = new RuleInfo();
        rule.setAccess("allow");
        rule.setNetwork("192.168.23.23");
        
        Firewall plugin = initialisePlugin("deny", new RuleInfo[]{rule});

        assertEquals(Result.DENIED, plugin.access(ObjectType.VIRTUALHOST, _address));
        
        // Set IP so that we're connected from the right address
        _address = new InetSocketAddress("192.168.23.23", 65535);
        assertEquals(Result.ALLOWED, plugin.access(ObjectType.VIRTUALHOST, _address));
    }
    
    public void testSingleNetworkRule() throws Exception
    {
        RuleInfo rule = new RuleInfo();
        rule.setAccess("allow");
        rule.setNetwork("192.168.23.0/24");
        
        Firewall plugin = initialisePlugin("deny", new RuleInfo[]{rule});

        assertEquals(Result.DENIED, plugin.access(ObjectType.VIRTUALHOST, _address));
        
        // Set IP so that we're connected from the right address
        _address = new InetSocketAddress("192.168.23.23", 65535);
        assertEquals(Result.ALLOWED, plugin.access(ObjectType.VIRTUALHOST, _address));
    }

    public void testSingleHostRule() throws Exception
    {
        RuleInfo rule = new RuleInfo();
        rule.setAccess("allow");
        rule.setHostname(new InetSocketAddress("127.0.0.1", 5672).getHostName());
        
        Firewall plugin = initialisePlugin("deny", new RuleInfo[]{rule});

        // Set IP so that we're connected from the right address
        _address = new InetSocketAddress("127.0.0.1", 65535);
        assertEquals(Result.ALLOWED, plugin.access(ObjectType.VIRTUALHOST, _address));
    }

    public void testSingleHostWilcardRule() throws Exception
    {
        RuleInfo rule = new RuleInfo();
        rule.setAccess("allow");
        String hostname = new InetSocketAddress("127.0.0.1", 0).getHostName();
        rule.setHostname(".*"+hostname.subSequence(hostname.length() - 1, hostname.length())+"*");
        Firewall plugin = initialisePlugin("deny", new RuleInfo[]{rule});

        // Set IP so that we're connected from the right address
        _address = new InetSocketAddress("127.0.0.1", 65535);
        assertEquals(Result.ALLOWED, plugin.access(ObjectType.VIRTUALHOST, _address));
    }
    
    public void testSeveralFirstAllowsAccess() throws Exception
    {
        RuleInfo firstRule = new RuleInfo();
        firstRule.setAccess("allow");
        firstRule.setNetwork("192.168.23.23");
        
        RuleInfo secondRule = new RuleInfo();
        secondRule.setAccess("deny");
        secondRule.setNetwork("192.168.42.42");

        RuleInfo thirdRule = new RuleInfo();
        thirdRule.setAccess("deny");
        thirdRule.setHostname("localhost");
        
        Firewall plugin = initialisePlugin("deny", new RuleInfo[]{firstRule, secondRule, thirdRule});

        assertEquals(Result.DENIED, plugin.access(ObjectType.VIRTUALHOST, _address));
        
        // Set IP so that we're connected from the right address
        _address = new InetSocketAddress("192.168.23.23", 65535);
        assertEquals(Result.ALLOWED, plugin.access(ObjectType.VIRTUALHOST, _address));
    }
    
    public void testSeveralLastAllowsAccess() throws Exception
    {
        RuleInfo firstRule = new RuleInfo();
        firstRule.setAccess("deny");
        firstRule.setHostname("localhost");
        
        RuleInfo secondRule = new RuleInfo();
        secondRule.setAccess("deny");
        secondRule.setNetwork("192.168.42.42");

        RuleInfo thirdRule = new RuleInfo();
        thirdRule.setAccess("allow");
        thirdRule.setNetwork("192.168.23.23");
        
        Firewall plugin = initialisePlugin("deny", new RuleInfo[]{firstRule, secondRule, thirdRule});

        assertEquals(Result.DENIED, plugin.access(ObjectType.VIRTUALHOST, _address));
        
        // Set IP so that we're connected from the right address
        _address = new InetSocketAddress("192.168.23.23", 65535);
        assertEquals(Result.ALLOWED, plugin.access(ObjectType.VIRTUALHOST, _address));
    }

    public void testNetmask() throws Exception
    {
        RuleInfo firstRule = new RuleInfo();
        firstRule.setAccess("allow");
        firstRule.setNetwork("192.168.23.0/24");
        Firewall plugin = initialisePlugin("deny", new RuleInfo[]{firstRule});

        assertEquals(Result.DENIED, plugin.access(ObjectType.VIRTUALHOST, _address));
        
        // Set IP so that we're connected from the right address
        _address = new InetSocketAddress("192.168.23.23", 65535);
        assertEquals(Result.ALLOWED, plugin.access(ObjectType.VIRTUALHOST, _address));
    }
    
    public void testCommaSeperatedNetmask() throws Exception
    {
        RuleInfo firstRule = new RuleInfo();
        firstRule.setAccess("allow");
        firstRule.setNetwork("10.1.1.1/8, 192.168.23.0/24");
        Firewall plugin = initialisePlugin("deny", new RuleInfo[]{firstRule});

        assertEquals(Result.DENIED, plugin.access(ObjectType.VIRTUALHOST, _address));
        
        // Set IP so that we're connected from the right address
        _address = new InetSocketAddress("192.168.23.23", 65535);
        assertEquals(Result.ALLOWED, plugin.access(ObjectType.VIRTUALHOST, _address));
    }
    
    public void testCommaSeperatedHostnames() throws Exception
    {
        RuleInfo firstRule = new RuleInfo();
        firstRule.setAccess("allow");
        firstRule.setHostname("foo, bar, "+new InetSocketAddress("127.0.0.1", 5672).getHostName());
        Firewall plugin = initialisePlugin("deny", new RuleInfo[]{firstRule});
        
        // Set IP so that we're connected from the right address
        _address = new InetSocketAddress("10.0.0.1", 65535);
        assertEquals(Result.DENIED, plugin.access(ObjectType.VIRTUALHOST, _address));
        
        // Set IP so that we're connected from the right address
        _address = new InetSocketAddress("127.0.0.1", 65535);
        assertEquals(Result.ALLOWED, plugin.access(ObjectType.VIRTUALHOST, _address));
    }
}
