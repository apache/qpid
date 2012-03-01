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

import junit.framework.TestCase;
import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.util.TestApplicationRegistry;

public class QueueConfigurationTest extends TestCase
{

    private VirtualHostConfiguration _emptyConf;
    private PropertiesConfiguration _env;
    private VirtualHostConfiguration _fullHostConf;

    public void setUp() throws Exception
    {
        _env = new PropertiesConfiguration();
        _emptyConf = new VirtualHostConfiguration("test", _env);

        PropertiesConfiguration fullEnv = new PropertiesConfiguration();
        fullEnv.setProperty("queues.maximumMessageAge", 1);
        fullEnv.setProperty("queues.maximumQueueDepth", 1);
        fullEnv.setProperty("queues.maximumMessageSize", 1);
        fullEnv.setProperty("queues.maximumMessageCount", 1);
        fullEnv.setProperty("queues.minimumAlertRepeatGap", 1);
        fullEnv.setProperty("queues.deadLetterQueues", true);
        fullEnv.setProperty("queues.maximumDeliveryCount", 5);

        _fullHostConf = new VirtualHostConfiguration("test", fullEnv);

    }

    public void testMaxDeliveryCount() throws Exception
    {
        try
        {
            ApplicationRegistry registry = new TestApplicationRegistry(new ServerConfiguration(_env));
            ApplicationRegistry.initialise(registry);

            // Check default value
            QueueConfiguration qConf = new QueueConfiguration("test", _emptyConf);
            assertEquals("Unexpected default server configuration for max delivery count ", 0, qConf.getMaxDeliveryCount());

            // Check explicit value
            VirtualHostConfiguration vhostConfig = overrideConfiguration("maximumDeliveryCount", 7);
            qConf = new QueueConfiguration("test", vhostConfig);
            assertEquals("Unexpected host configuration for max delivery count", 7, qConf.getMaxDeliveryCount());

            // Check inherited value
            qConf = new QueueConfiguration("test",  _fullHostConf);
            assertEquals("Unexpected queue configuration for max delivery count", 5, qConf.getMaxDeliveryCount());

        }
        finally
        {
            ApplicationRegistry.remove();
        }
    }

    /**
     * Tests that the default setting for DLQ configuration is disabled, and verifies that it can be overridden
     * at a broker or virtualhost level.
     * @throws Exception
     */
    public void testIsDeadLetterQueueEnabled() throws Exception
    {
        try
        {
            ApplicationRegistry registry = new TestApplicationRegistry(new ServerConfiguration(_env));
            ApplicationRegistry.initialise(registry);

            // Check default value
            QueueConfiguration qConf = new QueueConfiguration("test", _emptyConf);
            assertFalse("Unexpected queue configuration for dead letter enabled attribute", qConf.isDeadLetterQueueEnabled());

            // Check explicit value
            VirtualHostConfiguration vhostConfig = overrideConfiguration("deadLetterQueues", true);
            qConf = new QueueConfiguration("test", vhostConfig);
            assertTrue("Unexpected queue configuration for dead letter enabled attribute", qConf.isDeadLetterQueueEnabled());

            // Check inherited value
            qConf = new QueueConfiguration("test", _fullHostConf);
            assertTrue("Unexpected queue configuration for dead letter enabled attribute", qConf.isDeadLetterQueueEnabled());
        }
        finally
        {
            ApplicationRegistry.remove();
        }
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

    public void testGetMinimumAlertRepeatGap() throws ConfigurationException
    {
        // Check default value
        QueueConfiguration qConf = new QueueConfiguration("test", _emptyConf);
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

    private VirtualHostConfiguration overrideConfiguration(String property, Object value)
            throws ConfigurationException
    {
        PropertiesConfiguration queueConfig = new PropertiesConfiguration();
        queueConfig.setProperty("queues.queue.test." + property, value);

        CompositeConfiguration config = new CompositeConfiguration();
        config.addConfiguration(_fullHostConf.getConfig());
        config.addConfiguration(queueConfig);

        return new VirtualHostConfiguration("test", config);
    }
}
