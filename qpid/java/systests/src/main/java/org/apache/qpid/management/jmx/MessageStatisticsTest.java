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

import javax.jms.Connection;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.management.common.mbeans.ManagedConnection;

/**
 * Test generation of message statistics.
 */
public class MessageStatisticsTest extends MessageStatisticsTestCase
{
    public void configureStatistics() throws Exception
    {
        setConfigurationProperty("statistics.generation.broker", "true");
        setConfigurationProperty("statistics.generation.virtualhosts", "true");
        setConfigurationProperty("statistics.generation.connections", "true");
    }

    /**
     * Test message totals.
     */
    public void testMessageTotals() throws Exception
    {
        sendUsing(_test, 10, 100);
        sendUsing(_dev, 20, 100);
        sendUsing(_local, 5, 100);
        sendUsing(_local, 5, 100);
        sendUsing(_local, 5, 100);
        Thread.sleep(2000);
        
        ManagedBroker test = _jmxUtils.getManagedBroker("test");
        ManagedBroker dev = _jmxUtils.getManagedBroker("development");
        ManagedBroker local = _jmxUtils.getManagedBroker("localhost");

        if (!isBroker010())
        {
            long total = 0;
            long data = 0;
            for (ManagedConnection mc : _jmxUtils.getAllManagedConnections())
            {
                total += mc.getTotalMessagesReceived();
                data += mc.getTotalDataReceived();
            }
            assertEquals("Incorrect connection total", 45, total);
            assertEquals("Incorrect connection data", 4500, data);
        }
        if (!_broker.equals(VM))
        {
            assertEquals("Incorrect server total", 45, _jmxUtils.getServerInformation().getTotalMessagesReceived());
            assertEquals("Incorrect server data", 4500, _jmxUtils.getServerInformation().getTotalDataReceived());
        }
        
        if (!isBroker010())
        {
            long testTotal = 0;
            long testData = 0;
            for (ManagedConnection mc : _jmxUtils.getManagedConnections("test"))
            {
                testTotal += mc.getTotalMessagesReceived();
                testData += mc.getTotalDataReceived();
            }
            assertEquals("Incorrect test connection total", 10, testTotal);
            assertEquals("Incorrect test connection data", 1000, testData);
        }
        assertEquals("Incorrect test vhost total", 10, test.getTotalMessagesReceived());
        assertEquals("Incorrect test vhost data", 1000, test.getTotalDataReceived());
        
        if (!isBroker010())
        {
            long devTotal = 0;
            long devData = 0;
            for (ManagedConnection mc : _jmxUtils.getManagedConnections("development"))
            {
                devTotal += mc.getTotalMessagesReceived();
                devData += mc.getTotalDataReceived();
            }
            assertEquals("Incorrect test connection total", 20, devTotal);
            assertEquals("Incorrect test connection data", 2000, devData);
        }
        assertEquals("Incorrect development total", 20, dev.getTotalMessagesReceived());
        assertEquals("Incorrect development data", 2000, dev.getTotalDataReceived());
        
        if (!isBroker010())
        {
            long localTotal = 0;
            long localData = 0;
            for (ManagedConnection mc : _jmxUtils.getManagedConnections("localhost"))
            {
                localTotal += mc.getTotalMessagesReceived();
                localData += mc.getTotalDataReceived();
            }
            assertEquals("Incorrect test connection total", 15, localTotal);
            assertEquals("Incorrect test connection data", 1500, localData);
        }
        assertEquals("Incorrect localhost total", 15, local.getTotalMessagesReceived());
        assertEquals("Incorrect localhost data", 1500, local.getTotalDataReceived());
    }

    /**
     * Test message totals when a connection is closed.
     */
    public void testMessageTotalsWithClosedConnections() throws Exception
    {
        Connection temp = new AMQConnection(_brokerUrl, USER, USER, "clientid", "test");
        temp.start();
        
        sendUsing(_test, 10, 100);
        sendUsing(temp, 10, 100);
        sendUsing(_test, 10, 100);
        Thread.sleep(2000);
        
        temp.close();
        
        ManagedBroker test = _jmxUtils.getManagedBroker("test");

        if (!isBroker010())
        {
            long total = 0;
            long data = 0;
            for (ManagedConnection mc : _jmxUtils.getAllManagedConnections())
            {
                total += mc.getTotalMessagesReceived();
                data += mc.getTotalDataReceived();
            }
            assertEquals("Incorrect active connection total", 20, total);
            assertEquals("Incorrect active connection data", 2000, data);
        }
        if (!_broker.equals(VM))
        {
            assertEquals("Incorrect server total", 30, _jmxUtils.getServerInformation().getTotalMessagesReceived());
            assertEquals("Incorrect server data", 3000, _jmxUtils.getServerInformation().getTotalDataReceived());
        }
        
        if (!isBroker010())
        {
            long testTotal = 0;
            long testData = 0;
            for (ManagedConnection mc : _jmxUtils.getManagedConnections("test"))
            {
                testTotal += mc.getTotalMessagesReceived();
                testData += mc.getTotalDataReceived();
            }
            assertEquals("Incorrect test active connection total", 20, testTotal);
            assertEquals("Incorrect test active connection data", 20 * 100, testData);
        }
        assertEquals("Incorrect test vhost total", 30, test.getTotalMessagesReceived());
        assertEquals("Incorrect test vhost data", 30 * 100, test.getTotalDataReceived());
    }
    
    /**
     * Test message peak rate generation.
     */
    public void testMessagePeakRates() throws Exception
    {
        sendUsing(_test, 2, 10);
        Thread.sleep(10000);
        sendUsing(_dev, 4, 10);
        Thread.sleep(10000);
        
        ManagedBroker test = _jmxUtils.getManagedBroker("test");
        ManagedBroker dev = _jmxUtils.getManagedBroker("development");
        
        assertApprox("Incorrect test vhost peak messages", 0.2d, 1.0d, test.getPeakMessageReceiptRate());
        assertApprox("Incorrect test vhost peak data", 0.2d, 10.0d, test.getPeakDataReceiptRate());
        assertApprox("Incorrect dev vhost peak messages", 0.2d, 2.0d, dev.getPeakMessageReceiptRate());
        assertApprox("Incorrect dev vhost peak data", 0.2d, 20.0d, dev.getPeakDataReceiptRate());

        if (!_broker.equals(VM))
        {
            assertApprox("Incorrect server peak messages", 0.2d, 2.0d, _jmxUtils.getServerInformation().getPeakMessageReceiptRate());
            assertApprox("Incorrect server peak data", 0.2d, 20.0d, _jmxUtils.getServerInformation().getPeakDataReceiptRate());
        }
    }
    
    /**
     * Test message totals when a vhost has its statistics reset
     */
    public void testMessageTotalVhostReset() throws Exception
    {
        sendUsing(_test, 10, 10);
        sendUsing(_dev, 10, 10);
        Thread.sleep(2000);
        
        ManagedBroker test = _jmxUtils.getManagedBroker("test");
        ManagedBroker dev = _jmxUtils.getManagedBroker("development");
        
        assertEquals("Incorrect test vhost total messages", 10, test.getTotalMessagesReceived());
        assertEquals("Incorrect test vhost total data", 100, test.getTotalDataReceived());
        assertEquals("Incorrect dev vhost total messages", 10, dev.getTotalMessagesReceived());
        assertEquals("Incorrect dev vhost total data", 100, dev.getTotalDataReceived());

        if (!_broker.equals(VM))
        {
            assertEquals("Incorrect server total messages", 20, _jmxUtils.getServerInformation().getTotalMessagesReceived());
            assertEquals("Incorrect server total data", 200, _jmxUtils.getServerInformation().getTotalDataReceived());
        }
        
        test.resetStatistics();
        
        assertEquals("Incorrect test vhost total messages", 0, test.getTotalMessagesReceived());
        assertEquals("Incorrect test vhost total data", 0, test.getTotalDataReceived());
        assertEquals("Incorrect dev vhost total messages", 10, dev.getTotalMessagesReceived());
        assertEquals("Incorrect dev vhost total data", 100, dev.getTotalDataReceived());

        if (!_broker.equals(VM))
        {
            assertEquals("Incorrect server total messages", 20, _jmxUtils.getServerInformation().getTotalMessagesReceived());
            assertEquals("Incorrect server total data", 200, _jmxUtils.getServerInformation().getTotalDataReceived());
        }
    }
}
