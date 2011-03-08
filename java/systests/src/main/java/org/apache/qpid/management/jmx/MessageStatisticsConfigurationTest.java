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
package org.apache.qpid.management.jmx;

import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.management.common.mbeans.ManagedConnection;

/**
 * Test enabling generation of message statistics on a per-connection basis.
 */
public class MessageStatisticsConfigurationTest extends MessageStatisticsTestCase
{
    public void configureStatistics() throws Exception
    {
        setConfigurationProperty("statistics.generation.broker", Boolean.toString(getName().contains("Broker")));
        setConfigurationProperty("statistics.generation.virtualhosts", Boolean.toString(getName().contains("Virtualhost")));
        setConfigurationProperty("statistics.generation.connections", Boolean.toString(getName().contains("Connection")));
    }

    /**
     * Just broker statistics.
     */
    public void testGenerateBrokerStatistics() throws Exception
    {
        sendUsing(_test, 5, 200);
        Thread.sleep(1000);
        
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("test"))
        {
	        assertEquals("Incorrect connection total", 0,  mc.getTotalMessagesReceived());
	        assertEquals("Incorrect connection data", 0, mc.getTotalDataReceived());
	        assertFalse("Connection statistics should not be enabled", mc.isStatisticsEnabled());
        }
        
        ManagedBroker vhost = _jmxUtils.getManagedBroker("test");
        assertEquals("Incorrect vhost data", 0, vhost.getTotalMessagesReceived());
        assertEquals("Incorrect vhost data", 0, vhost.getTotalDataReceived());
        assertFalse("Vhost statistics should not be enabled", vhost.isStatisticsEnabled());

        if (!_broker.equals(VM))
        {
            assertEquals("Incorrect server total messages", 5, _jmxUtils.getServerInformation().getTotalMessagesReceived());
            assertEquals("Incorrect server total data", 1000, _jmxUtils.getServerInformation().getTotalDataReceived());
            assertTrue("Server statistics should be enabled", _jmxUtils.getServerInformation().isStatisticsEnabled());
        }
    }

    /**
     * Just virtualhost statistics.
     */
    public void testGenerateVirtualhostStatistics() throws Exception
    {
        sendUsing(_test, 5, 200);
        Thread.sleep(1000);
        
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("test"))
        {
            assertEquals("Incorrect connection total", 0,  mc.getTotalMessagesReceived());
            assertEquals("Incorrect connection data", 0, mc.getTotalDataReceived());
	        assertFalse("Connection statistics should not be enabled", mc.isStatisticsEnabled());
        }
        
        ManagedBroker vhost = _jmxUtils.getManagedBroker("test");
        assertEquals("Incorrect vhost data", 5, vhost.getTotalMessagesReceived());
        assertEquals("Incorrect vhost data", 1000, vhost.getTotalDataReceived());
        assertTrue("Vhost statistics should be enabled", vhost.isStatisticsEnabled());

        if (!_broker.equals(VM))
        {
            assertEquals("Incorrect server total messages", 0, _jmxUtils.getServerInformation().getTotalMessagesReceived());
            assertEquals("Incorrect server total data", 0, _jmxUtils.getServerInformation().getTotalDataReceived());
            assertFalse("Server statistics should not be enabled", _jmxUtils.getServerInformation().isStatisticsEnabled());
        }
    }

    /**
     * Just connection statistics.
     */
    public void testGenerateConnectionStatistics() throws Exception
    {
        sendUsing(_test, 5, 200);
        Thread.sleep(1000);
        
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("test"))
        {
            assertEquals("Incorrect connection total", 5,  mc.getTotalMessagesReceived());
            assertEquals("Incorrect connection data", 1000, mc.getTotalDataReceived());
	        assertTrue("Connection statistics should be enabled", mc.isStatisticsEnabled());
        }
        
        ManagedBroker vhost = _jmxUtils.getManagedBroker("test");
        assertEquals("Incorrect vhost data", 0, vhost.getTotalMessagesReceived());
        assertEquals("Incorrect vhost data", 0, vhost.getTotalDataReceived());
        assertFalse("Vhost statistics should not be enabled", vhost.isStatisticsEnabled());

        if (!_broker.equals(VM))
        {
            assertEquals("Incorrect server total messages", 0, _jmxUtils.getServerInformation().getTotalMessagesReceived());
            assertEquals("Incorrect server total data", 0, _jmxUtils.getServerInformation().getTotalDataReceived());
            assertFalse("Server statistics should not be enabled", _jmxUtils.getServerInformation().isStatisticsEnabled());
        }
    }

    /**
     * Both broker and virtualhost statistics.
     */
    public void testGenerateBrokerAndVirtualhostStatistics() throws Exception
    {
        sendUsing(_test, 5, 200);
        Thread.sleep(1000);
        
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("test"))
        {
            assertEquals("Incorrect connection total", 0,  mc.getTotalMessagesReceived());
            assertEquals("Incorrect connection data", 0, mc.getTotalDataReceived());
	        assertFalse("Connection statistics should not be enabled", mc.isStatisticsEnabled());
        }
        
        ManagedBroker vhost = _jmxUtils.getManagedBroker("test");
        assertEquals("Incorrect vhost data", 5, vhost.getTotalMessagesReceived());
        assertEquals("Incorrect vhost data", 1000, vhost.getTotalDataReceived());
        assertTrue("Vhost statistics should be enabled", vhost.isStatisticsEnabled());

        if (!_broker.equals(VM))
        {
            assertEquals("Incorrect server total messages", 5, _jmxUtils.getServerInformation().getTotalMessagesReceived());
            assertEquals("Incorrect server total data", 1000, _jmxUtils.getServerInformation().getTotalDataReceived());
            assertTrue("Server statistics should be enabled", _jmxUtils.getServerInformation().isStatisticsEnabled());
        }
    }

    /**
     * Broker, virtualhost and connection statistics.
     */
    public void testGenerateBrokerVirtualhostAndConnectionStatistics() throws Exception
    {
        sendUsing(_test, 5, 200);
        Thread.sleep(1000);
        
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("test"))
        {
            assertEquals("Incorrect connection total", 5,  mc.getTotalMessagesReceived());
            assertEquals("Incorrect connection data", 1000, mc.getTotalDataReceived());
	        assertTrue("Connection statistics should be enabled", mc.isStatisticsEnabled());
        }
        
        ManagedBroker vhost = _jmxUtils.getManagedBroker("test");
        assertEquals("Incorrect vhost data", 5, vhost.getTotalMessagesReceived());
        assertEquals("Incorrect vhost data", 1000, vhost.getTotalDataReceived());
        assertTrue("Vhost statistics should be enabled", vhost.isStatisticsEnabled());

        if (!_broker.equals(VM))
        {
            assertEquals("Incorrect server total messages", 5, _jmxUtils.getServerInformation().getTotalMessagesReceived());
            assertEquals("Incorrect server total data", 1000, _jmxUtils.getServerInformation().getTotalDataReceived());
            assertTrue("Server statistics should be enabled", _jmxUtils.getServerInformation().isStatisticsEnabled());
        }
    }
}
