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
 *
 */
package org.apache.qpid.server.jmx.mbeans;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Statistics;

import junit.framework.TestCase;

public class ServerInformationMBeanTest extends TestCase
{
    private ManagedObjectRegistry _mockManagedObjectRegistry;
    private Broker _mockBroker;
    private Statistics _mockBrokerStatistics;
    private ServerInformationMBean _mbean;

    @Override
    protected void setUp() throws Exception
    {
        _mockManagedObjectRegistry = mock(ManagedObjectRegistry.class);
        _mockBroker = mock(Broker.class);
        _mockBrokerStatistics = mock(Statistics.class);
        when(_mockBroker.getStatistics()).thenReturn(_mockBrokerStatistics);

        _mbean = new ServerInformationMBean(_mockManagedObjectRegistry, _mockBroker);
    }

    public void testMBeanRegistersItself() throws Exception
    {
        ServerInformationMBean mbean = new ServerInformationMBean(_mockManagedObjectRegistry, _mockBroker);
        verify(_mockManagedObjectRegistry).registerObject(mbean);
    }

    /**********  Statistics **********/

    public void testGetMessageCount() throws Exception
    {
        assertStatistic("totalDataDelivered", 16384l, Connection.BYTES_OUT);
    }

    /**********  Attributes **********/

    public void testBuildVersion() throws Exception
    {
        assertAttribute("buildVersion", "0.0.1", Broker.BUILD_VERSION);
    }

    public void testProductVersion() throws Exception
    {
        assertAttribute("productVersion", "0.0.1", Broker.PRODUCT_VERSION);
    }

    /**********  Other Attributes **********/

    public void testIsStatisticsEnabled() throws Exception
    {
        assertTrue("isStatisticsEnabled", _mbean.isStatisticsEnabled());
    }

    private void assertStatistic(String jmxAttributeName, Object expectedValue, String underlyingAttributeName) throws Exception
    {
        when(_mockBrokerStatistics.getStatistic(underlyingAttributeName)).thenReturn(expectedValue);
        MBeanTestUtils.assertMBeanAttribute(_mbean, jmxAttributeName, expectedValue);
    }

    private void assertAttribute(String jmxAttributeName, Object expectedValue, String underlyingAttributeName) throws Exception
    {
        when(_mockBroker.getAttribute(underlyingAttributeName)).thenReturn(expectedValue);
        MBeanTestUtils.assertMBeanAttribute(_mbean, jmxAttributeName, expectedValue);
    }
}
