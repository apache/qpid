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
        
        ManagedBroker test = _jmxUtils.getManagedBroker("test");
        ManagedBroker dev = _jmxUtils.getManagedBroker("development");
        ManagedBroker local = _jmxUtils.getManagedBroker("localhost");

        long total = 0;
        long data = 0;
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("*"))
        {
            total += mc.getTotalMessages();
            data += mc.getTotalData();
        }
        assertEquals("Incorrect connection total", 45, total);
        assertEquals("Incorrect connection data", 45 * 100, data);
        if (!_broker.equals(VM))
        {
            assertEquals("Incorrect server total", 45, _jmxUtils.getServerInformation().getTotalMessages());
            assertEquals("Incorrect server data", 45 * 100, _jmxUtils.getServerInformation().getTotalData());
        }
        
        long testTotal = 0;
        long testData = 0;
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("test"))
        {
            testTotal += mc.getTotalMessages();
            testData += mc.getTotalData();
        }
        assertEquals("Incorrect test connection total", 10, testTotal);
        assertEquals("Incorrect test vhost total", 10, test.getTotalMessages());
        assertEquals("Incorrect test connection data", 10 * 100, testData);
        assertEquals("Incorrect test vhost data", 10 * 100, test.getTotalData());
        
        long devTotal = 0;
        long devData = 0;
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("development"))
        {
            devTotal += mc.getTotalMessages();
            devData += mc.getTotalData();
        }
        assertEquals("Incorrect test connection total", 20, devTotal);
        assertEquals("Incorrect development total", 20, dev.getTotalMessages());
        assertEquals("Incorrect test connection data", 20 * 100, devData);
        assertEquals("Incorrect development data", 20 * 100, dev.getTotalData());
        
        long localTotal = 0;
        long localData = 0;
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("localhost"))
        {
            localTotal += mc.getTotalMessages();
            localData += mc.getTotalData();
        }
        assertEquals("Incorrect test connection total", 15, localTotal);
        assertEquals("Incorrect localhost total", 15, local.getTotalMessages());
        assertEquals("Incorrect test connection data", 15 * 100, localData);
        assertEquals("Incorrect localhost data", 15 * 100, local.getTotalData());
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
        
        temp.close();
        
        ManagedBroker test = _jmxUtils.getManagedBroker("test");

        long total = 0;
        long data = 0;
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("*"))
        {
            total += mc.getTotalMessages();
            data += mc.getTotalData();
        }
        assertEquals("Incorrect active connection total", 20, total);
        assertEquals("Incorrect active connection data", 20 * 100, data);
        if (!_broker.equals(VM))
        {
            assertEquals("Incorrect server total", 30, _jmxUtils.getServerInformation().getTotalMessages());
            assertEquals("Incorrect server data", 30 * 100, _jmxUtils.getServerInformation().getTotalData());
        }
        
        long testTotal = 0;
        long testData = 0;
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("test"))
        {
            testTotal += mc.getTotalMessages();
            testData += mc.getTotalData();
        }
        assertEquals("Incorrect test active connection total", 20, testTotal);
        assertEquals("Incorrect test vhost total", 30, test.getTotalMessages());
        assertEquals("Incorrect test active connection data", 20 * 100, testData);
        assertEquals("Incorrect test vhost data", 30 * 100, test.getTotalData());
    }
    
    /**
     * Test message peak rate generation.
     */
    public void testMessagePeakRates() throws Exception
    {
        sendUsing(_test, 1, 10000);
        Thread.sleep(10 * 1000);
        sendUsing(_dev, 10, 10);
        
        ManagedBroker test = _jmxUtils.getManagedBroker("test");
        ManagedBroker dev = _jmxUtils.getManagedBroker("development");
        
        assertEquals("Incorrect test vhost peak messages", 1.0d, test.getPeakMessageRate());
        assertEquals("Incorrect test vhost peak data", 10000.0d, test.getPeakDataRate());
        assertEquals("Incorrect dev vhost peak messages", 10.0d, dev.getPeakMessageRate());
        assertEquals("Incorrect dev vhost peak data", 100.0d, dev.getPeakDataRate());

        if (!_broker.equals(VM))
        {
            assertEquals("Incorrect server peak messages", 10.0d, _jmxUtils.getServerInformation().getPeakMessageRate());
            assertEquals("Incorrect server peak data", 10000.0d, _jmxUtils.getServerInformation().getPeakDataRate());
        }
    }
    
    /**
     * Test message totals when a vhost has its statistics reset
     */
    public void testMessageTotalVhostReset() throws Exception
    {
        sendUsing(_test, 10, 10);
        sendUsing(_dev, 10, 10);
        
        ManagedBroker test = _jmxUtils.getManagedBroker("test");
        ManagedBroker dev = _jmxUtils.getManagedBroker("development");
        
        assertEquals("Incorrect test vhost total messages", 10, test.getTotalMessages());
        assertEquals("Incorrect test vhost total data", 100, test.getTotalData());
        assertEquals("Incorrect dev vhost total messages", 10, dev.getTotalMessages());
        assertEquals("Incorrect dev vhost total data", 100, dev.getTotalData());

        if (!_broker.equals(VM))
        {
            assertEquals("Incorrect server total messages", 20, _jmxUtils.getServerInformation().getTotalMessages());
            assertEquals("Incorrect server total data", 200, _jmxUtils.getServerInformation().getTotalData());
        }
        
        test.resetStatistics();
        
        assertEquals("Incorrect test vhost total messages", 0, test.getTotalMessages());
        assertEquals("Incorrect test vhost total data", 0, test.getTotalData());
        assertEquals("Incorrect dev vhost total messages", 10, dev.getTotalMessages());
        assertEquals("Incorrect dev vhost total data", 100, dev.getTotalData());

        if (!_broker.equals(VM))
        {
            assertEquals("Incorrect server total messages", 20, _jmxUtils.getServerInformation().getTotalMessages());
            assertEquals("Incorrect server total data", 200, _jmxUtils.getServerInformation().getTotalData());
        }
    }
}
