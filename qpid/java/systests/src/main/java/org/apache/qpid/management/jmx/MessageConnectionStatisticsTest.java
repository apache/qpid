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

import java.util.ArrayList;
import java.util.List;

import javax.jms.Connection;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.management.common.mbeans.ManagedConnection;

/**
 * Test enabling generation of message statistics on a per-connection basis.
 */
public class MessageConnectionStatisticsTest extends MessageStatisticsTestCase
{
    public void configureStatistics() throws Exception
    {
        // no statistics generation configured
    }

    /**
     * Test statistics on a single connection
     */
    public void testEnablingStatisticsPerConnection() throws Exception
    {
        ManagedBroker vhost = _jmxUtils.getManagedBroker("test");
        
        sendUsing(_test, 5, 200);
        Thread.sleep(1000);
        
        List<String> addresses = new ArrayList<String>();
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("test"))
        {
	        assertEquals("Incorrect connection total", 0,  mc.getTotalMessagesReceived());
	        assertEquals("Incorrect connection data", 0, mc.getTotalDataReceived());
	        assertFalse("Connection statistics should not be enabled", mc.isStatisticsEnabled());
            
            addresses.add(mc.getRemoteAddress());
        }
        assertEquals("Incorrect vhost total", 0, vhost.getTotalMessagesReceived());
        assertEquals("Incorrect vhost data", 0, vhost.getTotalDataReceived());
        
        Connection test = new AMQConnection(_brokerUrl, USER, USER, "clientid", "test");
        test.start();
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("test"))
        {
            if (addresses.contains(mc.getRemoteAddress()))
            {
                continue;
            }
            mc.setStatisticsEnabled(true);
	        assertEquals("Incorrect connection total", 0,  mc.getTotalMessagesReceived());
	        assertEquals("Incorrect connection data", 0, mc.getTotalDataReceived());
        }
        
        sendUsing(test, 5, 200);
        sendUsing(_test, 5, 200);
        Thread.sleep(1000);
        
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("test"))
        {
            if (addresses.contains(mc.getRemoteAddress()))
            {
		        assertEquals("Incorrect connection total", 0,  mc.getTotalMessagesReceived());
		        assertEquals("Incorrect connection data", 0, mc.getTotalDataReceived());
		        assertFalse("Connection statistics should not be enabled", mc.isStatisticsEnabled());
            }
            else
            {
		        assertEquals("Incorrect connection total", 5,  mc.getTotalMessagesReceived());
		        assertEquals("Incorrect connection data", 1000, mc.getTotalDataReceived());
		        assertTrue("Connection statistics should be enabled", mc.isStatisticsEnabled());
            }
        }
        assertEquals("Incorrect vhost total", 0, vhost.getTotalMessagesReceived());
        assertEquals("Incorrect vhost data", 0, vhost.getTotalDataReceived());
        assertFalse("Vhost statistics should not be enabled", vhost.isStatisticsEnabled());
        
        test.close();
    }
}
