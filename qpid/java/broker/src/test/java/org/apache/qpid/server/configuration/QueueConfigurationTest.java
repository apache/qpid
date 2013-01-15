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
package org.apache.qpid.server.configuration;

import static org.mockito.Mockito.when;

import junit.framework.TestCase;
import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.util.BrokerTestHelper;

public class QueueConfigurationTest extends TestCase
{
    private VirtualHostConfiguration _emptyConf;
    private PropertiesConfiguration _env;
    private VirtualHostConfiguration _fullHostConf;
    private Broker _broker;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _broker = BrokerTestHelper.createBrokerMock();
        _env = new PropertiesConfiguration();
        _emptyConf = new VirtualHostConfiguration("test", _env, _broker);

        PropertiesConfiguration fullEnv = new PropertiesConfiguration();
        fullEnv.setProperty("queues.maximumMessageAge", 1);
        fullEnv.setProperty("queues.maximumQueueDepth", 1);
        fullEnv.setProperty("queues.maximumMessageSize", 1);
        fullEnv.setProperty("queues.maximumMessageCount", 1);
        fullEnv.setProperty("queues.minimumAlertRepeatGap", 1);
        fullEnv.setProperty("queues.deadLetterQueues", true);
        fullEnv.setProperty("queues.maximumDeliveryCount", 5);

        _fullHostConf = new VirtualHostConfiguration("test", fullEnv, _broker);

    }

    @Override
    public void tearDown() throws Exception
    {
        BrokerTestHelper.tearDown();
        super.tearDown();
    }

    public void testMaxDeliveryCount() throws Exception
    {
        // broker MAXIMUM_DELIVERY_ATTEMPTS attribute is not set
        when(_broker.getAttribute(Broker.MAXIMUM_DELIVERY_ATTEMPTS)).thenReturn(null);

        // Check default value
        QueueConfiguration qConf = new QueueConfiguration("test", _emptyConf);
        assertEquals("Unexpected default server configuration for max delivery count ", 0, qConf.getMaxDeliveryCount());

        // set broker MAXIMUM_DELIVERY_ATTEMPTS attribute to 2
        when(_broker.getAttribute(Broker.MAXIMUM_DELIVERY_ATTEMPTS)).thenReturn(2);

        // Check that queue inherits the MAXIMUM_DELIVERY_ATTEMPTS value from broker
        qConf = new QueueConfiguration("test", _emptyConf);
        assertEquals("Unexpected default server configuration for max delivery count ", 2, qConf.getMaxDeliveryCount());

        // Check explicit value
        VirtualHostConfiguration vhostConfig = overrideConfiguration("maximumDeliveryCount", 7);
        qConf = new QueueConfiguration("test", vhostConfig);
        assertEquals("Unexpected host configuration for max delivery count", 7, qConf.getMaxDeliveryCount());

        // Check inherited value
        qConf = new QueueConfiguration("test",  _fullHostConf);
        assertEquals("Unexpected queue configuration for max delivery count", 5, qConf.getMaxDeliveryCount());
    }

    /**
     * Tests that the default setting for DLQ configuration is disabled, and verifies that it can be overridden
     * at a broker or virtualhost level.
     * @throws Exception
     */
    public void testIsDeadLetterQueueEnabled() throws Exception
    {
        // enable dead letter queues broker wide
        when(_broker.getAttribute(Broker.DEAD_LETTER_QUEUE_ENABLED)).thenReturn(true);

        // Check that queue inherits the broker setting
        QueueConfiguration qConf = new QueueConfiguration("test", _emptyConf);
        assertTrue("Unexpected queue configuration for dead letter enabled attribute", qConf.isDeadLetterQueueEnabled());

        // broker DEAD_LETTER_QUEUE_ENABLED is not set
        when(_broker.getAttribute(Broker.DEAD_LETTER_QUEUE_ENABLED)).thenReturn(null);

        // Check that queue dead letter queue is not enabled
        qConf = new QueueConfiguration("test", _emptyConf);
        assertFalse("Unexpected queue configuration for dead letter enabled attribute", qConf.isDeadLetterQueueEnabled());

        // Check explicit value
        VirtualHostConfiguration vhostConfig = overrideConfiguration("deadLetterQueues", true);
        qConf = new QueueConfiguration("test", vhostConfig);
        assertTrue("Unexpected queue configuration for dead letter enabled attribute", qConf.isDeadLetterQueueEnabled());

        // Check inherited value
        qConf = new QueueConfiguration("test", _fullHostConf);
        assertTrue("Unexpected queue configuration for dead letter enabled attribute", qConf.isDeadLetterQueueEnabled());
    }

    public void testGetMaximumMessageAge() throws ConfigurationException
    {
        // Check default value
        QueueConfiguration qConf = new QueueConfiguration("test", _emptyConf);
        assertEquals(0, qConf.getMaximumMessageAge());

        // Check explicit value
        VirtualHostConfiguration vhostConfig = overrideConfiguration("maximumMessageAge", 2);

        qConf = new QueueConfiguration("test", vhostConfig);
        assertEquals(2, qConf.getMaximumMessageAge());

        // Check inherited value
        qConf = new QueueConfiguration("test", _fullHostConf);
        assertEquals(1, qConf.getMaximumMessageAge());
    }

    public void testGetMaximumQueueDepth() throws ConfigurationException
    {
        // Check default value
        QueueConfiguration qConf = new QueueConfiguration("test", _emptyConf);
        assertEquals(0, qConf.getMaximumQueueDepth());

        // Check explicit value
        VirtualHostConfiguration vhostConfig = overrideConfiguration("maximumQueueDepth", 2);
        qConf = new QueueConfiguration("test", vhostConfig);
        assertEquals(2, qConf.getMaximumQueueDepth());

        // Check inherited value
        qConf = new QueueConfiguration("test", _fullHostConf);
        assertEquals(1, qConf.getMaximumQueueDepth());
    }

    public void testGetMaximumMessageSize() throws ConfigurationException
    {
        // Check default value
        QueueConfiguration qConf = new QueueConfiguration("test", _emptyConf);
        assertEquals(0, qConf.getMaximumMessageSize());

        // Check explicit value
        VirtualHostConfiguration vhostConfig = overrideConfiguration("maximumMessageSize", 2);
        qConf = new QueueConfiguration("test", vhostConfig);
        assertEquals(2, qConf.getMaximumMessageSize());

        // Check inherited value
        qConf = new QueueConfiguration("test", _fullHostConf);
        assertEquals(1, qConf.getMaximumMessageSize());
    }

    public void testGetMaximumMessageCount() throws ConfigurationException
    {
        // Check default value
        QueueConfiguration qConf = new QueueConfiguration("test", _emptyConf);
        assertEquals(0, qConf.getMaximumMessageCount());

        // Check explicit value
        VirtualHostConfiguration vhostConfig = overrideConfiguration("maximumMessageCount", 2);
        qConf = new QueueConfiguration("test", vhostConfig);
        assertEquals(2, qConf.getMaximumMessageCount());

        // Check inherited value
        qConf = new QueueConfiguration("test", _fullHostConf);
        assertEquals(1, qConf.getMaximumMessageCount());
    }

    public void testGetMinimumAlertRepeatGap() throws Exception
    {
        // set broker attribute ALERT_REPEAT_GAP to 10
        when(_broker.getAttribute(Broker.ALERT_REPEAT_GAP)).thenReturn(10);

        // check that broker level setting is available on queue configuration
        QueueConfiguration qConf = new QueueConfiguration("test", _emptyConf);
        assertEquals(10, qConf.getMinimumAlertRepeatGap());

        // remove configuration for ALERT_REPEAT_GAP on broker level
        when(_broker.getAttribute(Broker.ALERT_REPEAT_GAP)).thenReturn(null);

        // Check default value
        qConf = new QueueConfiguration("test", _emptyConf);
        assertEquals(0, qConf.getMinimumAlertRepeatGap());

        // Check explicit value
        VirtualHostConfiguration vhostConfig = overrideConfiguration("minimumAlertRepeatGap", 2);
        qConf = new QueueConfiguration("test", vhostConfig);
        assertEquals(2, qConf.getMinimumAlertRepeatGap());

        // Check inherited value
        qConf = new QueueConfiguration("test", _fullHostConf);
        assertEquals(1, qConf.getMinimumAlertRepeatGap());
    }

    public void testSortQueueConfiguration() throws ConfigurationException
    {
        //Check default value
        QueueConfiguration qConf = new QueueConfiguration("test", _emptyConf);
        assertNull(qConf.getQueueSortKey());

        // Check explicit value
        final VirtualHostConfiguration vhostConfig = overrideConfiguration("sortKey", "test-sort-key");
        qConf = new QueueConfiguration("test", vhostConfig);
        assertEquals("test-sort-key", qConf.getQueueSortKey());
    }

    public void testQueueDescription() throws ConfigurationException
    {
        //Check default value
        QueueConfiguration qConf = new QueueConfiguration("test", _emptyConf);
        assertNull(qConf.getDescription());

        // Check explicit value
        final VirtualHostConfiguration vhostConfig = overrideConfiguration("description", "mydescription");
        qConf = new QueueConfiguration("test", vhostConfig);
        assertEquals("mydescription", qConf.getDescription());
    }

    private VirtualHostConfiguration overrideConfiguration(String property, Object value)
            throws ConfigurationException
    {
        PropertiesConfiguration queueConfig = new PropertiesConfiguration();
        queueConfig.setProperty("queues.queue.test." + property, value);

        CompositeConfiguration config = new CompositeConfiguration();
        config.addConfiguration(_fullHostConf.getConfig());
        config.addConfiguration(queueConfig);

        return new VirtualHostConfiguration("test", config, _broker);
    }
}
