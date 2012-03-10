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
package org.apache.qpid.server;

import org.apache.qpid.test.utils.QpidTestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


public class BrokerOptionsTest extends QpidTestCase
{
    private BrokerOptions _options;
    
    private static final int TEST_PORT1 = 6789;
    private static final int TEST_PORT2 = 6790;
    

    protected void setUp()
    {
        _options = new BrokerOptions();
    }
    
    public void testDefaultPort()
    {
        assertEquals(Collections.<Integer>emptySet(), _options.getPorts());
    }

    public void testOverriddenPort()
    {
        _options.addPort(TEST_PORT1);
        assertEquals(Collections.singleton(TEST_PORT1), _options.getPorts());
    }

    public void testManyOverriddenPorts()
    {
        _options.addPort(TEST_PORT1);
        _options.addPort(TEST_PORT2);
        final Set<Integer> expectedPorts = new HashSet<Integer>(Arrays.asList(new Integer[] {TEST_PORT1, TEST_PORT2}));
        assertEquals(expectedPorts, _options.getPorts());
    }

    public void testDuplicateOverriddenPortsAreSilentlyIgnored()
    {
        _options.addPort(TEST_PORT1);
        _options.addPort(TEST_PORT2);
        _options.addPort(TEST_PORT1); // duplicate - should be silently ignored
        final Set<Integer> expectedPorts = new HashSet<Integer>(Arrays.asList(new Integer[] {TEST_PORT1, TEST_PORT2}));
        assertEquals(expectedPorts, _options.getPorts());
    }

    public void testDefaultSSLPort()
    {
        assertEquals(Collections.<Integer>emptySet(), _options.getSSLPorts());
    }

    public void testOverriddenSSLPort()
    {
        _options.addSSLPort(TEST_PORT1);
        assertEquals(Collections.singleton(TEST_PORT1), _options.getSSLPorts());
    }

    public void testManyOverriddenSSLPorts()
    {
        _options.addSSLPort(TEST_PORT1);
        _options.addSSLPort(TEST_PORT2);
        final Set<Integer> expectedPorts = new HashSet<Integer>(Arrays.asList(new Integer[] {TEST_PORT1, TEST_PORT2}));
        assertEquals(expectedPorts, _options.getSSLPorts());
    }

    public void testDuplicateOverriddenSSLPortsAreSilentlyIgnored()
    {
        _options.addSSLPort(TEST_PORT1);
        _options.addSSLPort(TEST_PORT2);
        _options.addSSLPort(TEST_PORT1); // duplicate - should be silently ignored
        final Set<Integer> expectedPorts = new HashSet<Integer>(Arrays.asList(new Integer[] {TEST_PORT1, TEST_PORT2}));
        assertEquals(expectedPorts, _options.getSSLPorts());
    }

    public void testDefaultConfigFile()
    {
        assertNull(_options.getConfigFile());
    }
    
    public void testOverriddenConfigFile()
    {
        final String testConfigFile = "etc/mytestconfig.xml";
        _options.setConfigFile(testConfigFile);
        assertEquals(testConfigFile, _options.getConfigFile());
    }

    public void testDefaultLogConfigFile()
    {
        assertNull(_options.getLogConfigFile());
    }

    public void testOverriddenLogConfigFile()
    {
        final String testLogConfigFile = "etc/mytestlog4j.xml";
        _options.setLogConfigFile(testLogConfigFile);
        assertEquals(testLogConfigFile, _options.getLogConfigFile());
    }

    public void testDefaultJmxPortRegistryServer()
    {
        assertNull(_options.getJmxPortRegistryServer());
    }

    public void testJmxPortRegistryServer()
    {
        _options.setJmxPortRegistryServer(TEST_PORT1);
        assertEquals(Integer.valueOf(TEST_PORT1), _options.getJmxPortRegistryServer());
    }

    public void testDefaultJmxPortConnectorServer()
    {
        assertNull(_options.getJmxPortConnectorServer());
    }

    public void testJmxPortConnectorServer()
    {
        _options.setJmxPortConnectorServer(TEST_PORT1);
        assertEquals(Integer.valueOf(TEST_PORT1), _options.getJmxPortConnectorServer());
    }

    public void testQpidHomeExposesSysProperty()
    {
        assertEquals(System.getProperty("QPID_HOME"), _options.getQpidHome());
    }
    
    public void testDefaultExcludesPortFor0_10()
    {
        assertEquals(Collections.EMPTY_SET, _options.getExcludedPorts(ProtocolExclusion.v0_10));
    }
    
    public void testOverriddenExcludesPortFor0_10()
    {
        _options.addExcludedPort(ProtocolExclusion.v0_10, TEST_PORT1);
        assertEquals(Collections.singleton(TEST_PORT1), _options.getExcludedPorts(ProtocolExclusion.v0_10));
    }

    public void testManyOverriddenExcludedPortFor0_10()
    {
        _options.addExcludedPort(ProtocolExclusion.v0_10, TEST_PORT1);
        _options.addExcludedPort(ProtocolExclusion.v0_10, TEST_PORT2);
        final Set<Integer> expectedPorts = new HashSet<Integer>(Arrays.asList(new Integer[] {TEST_PORT1, TEST_PORT2}));
        assertEquals(expectedPorts, _options.getExcludedPorts(ProtocolExclusion.v0_10));
    }

    public void testDuplicatedOverriddenExcludedPortFor0_10AreSilentlyIgnored()
    {
        _options.addExcludedPort(ProtocolExclusion.v0_10, TEST_PORT1);
        _options.addExcludedPort(ProtocolExclusion.v0_10, TEST_PORT2);
        final Set<Integer> expectedPorts = new HashSet<Integer>(Arrays.asList(new Integer[] {TEST_PORT1, TEST_PORT2}));
        assertEquals(expectedPorts, _options.getExcludedPorts(ProtocolExclusion.v0_10));
    }
    
    public void testDefaultBind()
    {
        assertNull(_options.getBind());
    }
    
    public void testOverriddenBind()
    {
        final String bind = "192.168.0.1";
        _options.setBind(bind);
        assertEquals(bind, _options.getBind());
    }

    public void testDefaultLogWatchFrequency()
    {
        assertEquals(0L, _options.getLogWatchFrequency());
    }

    public void testOverridenLogWatchFrequency()
    {
        final int myFreq = 10 * 1000;
        
        _options.setLogWatchFrequency(myFreq);
        assertEquals(myFreq, _options.getLogWatchFrequency());
    }
}
