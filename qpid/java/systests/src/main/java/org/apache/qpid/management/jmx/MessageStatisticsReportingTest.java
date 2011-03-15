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

import java.util.List;

import org.apache.qpid.util.LogMonitor;

/**
 * Test generation of message statistics reporting.
 */
public class MessageStatisticsReportingTest extends MessageStatisticsTestCase
{
    protected LogMonitor _monitor;
    
    public void configureStatistics() throws Exception
    {
        setConfigurationProperty("statistics.generation.broker", "true");
        setConfigurationProperty("statistics.generation.virtualhosts", "true");
        
        if (getName().equals("testEnabledStatisticsReporting"))
        {
            setConfigurationProperty("statistics.reporting.period", "10");
        }
        
        _monitor = new LogMonitor(_outputFile);
    }

    /**
     * Test enabling reporting.
     */
    public void testEnabledStatisticsReporting() throws Exception
    {
        sendUsing(_test, 10, 100);
        sendUsing(_dev, 20, 100);
        sendUsing(_local, 15, 100);
        
        Thread.sleep(10 * 1000); // 15s
        
        List<String> brokerStatsData = _monitor.findMatches("BRK-1008");
        List<String> brokerStatsMessages = _monitor.findMatches("BRK-1009");
        List<String> vhostStatsData = _monitor.findMatches("VHT-1003");
        List<String> vhostStatsMessages = _monitor.findMatches("VHT-1004");
        
        assertEquals("Incorrect number of broker data stats log messages", 2, brokerStatsData.size());
        assertEquals("Incorrect number of broker message stats log messages", 2, brokerStatsMessages.size());
        assertEquals("Incorrect number of virtualhost data stats log messages", 6, vhostStatsData.size());
        assertEquals("Incorrect number of virtualhost message stats log messages", 6, vhostStatsMessages.size());
    }

    /**
     * Test not enabling reporting.
     */
    public void testNotEnabledStatisticsReporting() throws Exception
    {
        sendUsing(_test, 10, 100);
        sendUsing(_dev, 20, 100);
        sendUsing(_local, 15, 100);
        
        Thread.sleep(10 * 1000); // 15s
        
        List<String> brokerStatsData = _monitor.findMatches("BRK-1008");
        List<String> brokerStatsMessages = _monitor.findMatches("BRK-1009");
        List<String> vhostStatsData = _monitor.findMatches("VHT-1003");
        List<String> vhostStatsMessages = _monitor.findMatches("VHT-1004");
        
        assertEquals("Incorrect number of broker data stats log messages", 0, brokerStatsData.size());
        assertEquals("Incorrect number of broker message stats log messages", 0, brokerStatsMessages.size());
        assertEquals("Incorrect number of virtualhost data stats log messages", 0, vhostStatsData.size());
        assertEquals("Incorrect number of virtualhost message stats log messages", 0, vhostStatsMessages.size());
    }
}
