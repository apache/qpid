/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.qpid.server.configuration;


import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.queue.AMQPriorityQueue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.util.InternalBrokerBaseCase;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class VirtualHostConfigurationTest extends InternalBrokerBaseCase
{

    @Override
    public void createBroker()
    {
        // Prevent auto broker startup
    }

    public void testQueuePriority() throws Exception
    {
        // Set up queue with 5 priorities
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueuePriority.queues(-1).queue(-1).name(-1)",
                              "atest");
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueuePriority.queues.queue.atest(-1).exchange",
                              "amq.direct");
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueuePriority.queues.queue.atest.priorities",
                              "5");

        // Set up queue with JMS style priorities
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueuePriority.queues(-1).queue(-1).name(-1)",
                              "ptest");
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueuePriority.queues.queue.ptest(-1).exchange",
                              "amq.direct");
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueuePriority.queues.queue.ptest.priority",
                               "true");

        // Set up queue with no priorities
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueuePriority.queues(-1).queue(-1).name(-1)",
                              "ntest");
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueuePriority.queues.queue.ntest(-1).exchange",
                              "amq.direct");
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueuePriority.queues.queue.ntest.priority",
                              "false");

        // Start the broker now.
        super.createBroker();

        VirtualHost vhost =
                ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost(getName());

        // Check that atest was a priority queue with 5 priorities
        AMQQueue atest = vhost.getQueueRegistry().getQueue(new AMQShortString("atest"));
        assertTrue(atest instanceof AMQPriorityQueue);
        assertEquals(5, ((AMQPriorityQueue) atest).getPriorities());

        // Check that ptest was a priority queue with 10 priorities
        AMQQueue ptest = vhost.getQueueRegistry().getQueue(new AMQShortString("ptest"));
        assertTrue(ptest instanceof AMQPriorityQueue);
        assertEquals(10, ((AMQPriorityQueue) ptest).getPriorities());

        // Check that ntest wasn't a priority queue
        AMQQueue ntest = vhost.getQueueRegistry().getQueue(new AMQShortString("ntest"));
        assertFalse(ntest instanceof AMQPriorityQueue);
    }

    public void testQueueAlerts() throws Exception
    {
        // Set up queue with 5 priorities
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueueAlerts.queues.exchange", "amq.topic");
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueueAlerts.queues.maximumQueueDepth", "1");
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueueAlerts.queues.maximumMessageSize", "2");
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueueAlerts.queues.maximumMessageAge", "3");

        getConfigXml().addProperty("virtualhosts.virtualhost.testQueueAlerts.queues(-1).queue(1).name(1)", "atest");
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueueAlerts.queues.queue.atest(-1).exchange", "amq.direct");
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueueAlerts.queues.queue.atest(-1).maximumQueueDepth", "4");
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueueAlerts.queues.queue.atest(-1).maximumMessageSize", "5");
        getConfigXml().addProperty("virtualhosts.virtualhost.testQueueAlerts.queues.queue.atest(-1).maximumMessageAge", "6");

        getConfigXml().addProperty("virtualhosts.virtualhost.testQueueAlerts.queues(-1).queue(-1).name(-1)", "btest");

        // Start the broker now.
        super.createBroker();

        VirtualHost vhost =
                ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost(getName());

        // Check specifically configured values
        AMQQueue aTest = vhost.getQueueRegistry().getQueue(new AMQShortString("atest"));
        assertEquals(4, aTest.getMaximumQueueDepth());
        assertEquals(5, aTest.getMaximumMessageSize());
        assertEquals(6, aTest.getMaximumMessageAge());

        // Check default values
        AMQQueue bTest = vhost.getQueueRegistry().getQueue(new AMQShortString("btest"));
        assertEquals(1, bTest.getMaximumQueueDepth());
        assertEquals(2, bTest.getMaximumMessageSize());
        assertEquals(3, bTest.getMaximumMessageAge());
    }

    public void testMaxDeliveryCount() throws Exception
    {
        // Set up vhosts and queues
        getConfigXml().addProperty("virtualhosts.virtualhost." + getName() + ".queues.maximumDeliveryCount", 5);
        getConfigXml().addProperty("virtualhosts.virtualhost." + getName() + ".queues(-1).queue(-1).name", "biggles");
        getConfigXml().addProperty("virtualhosts.virtualhost." + getName() + ".queues.queue.biggles.maximumDeliveryCount", 4);
        getConfigXml().addProperty("virtualhosts.virtualhost." + getName() + ".queues(-1).queue(-1).name", "beetle");

        // Start the broker now.
        super.createBroker();

        // Get vhosts
        VirtualHost test = ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost(getName());

        // Enabled specifically
        assertEquals("Test vhost MDC was configured as enabled", 5 ,test.getConfiguration().getMaxDeliveryCount());

        // Enabled by test vhost default
        assertEquals("beetle queue DLQ was configured as enabled", test.getConfiguration().getMaxDeliveryCount(), test.getConfiguration().getQueueConfiguration("beetle").getMaxDeliveryCount());

        // Disabled specifically
        assertEquals("Biggles queue DLQ was configured as disabled", 4, test.getConfiguration().getQueueConfiguration("biggles").getMaxDeliveryCount());
    }

    /**
     * Tests the full set of configuration options for enabling DLQs in the broker configuration.
     */
    public void testIsDeadLetterQueueEnabled() throws Exception
    {
        // Set up vhosts and queues
        getConfigXml().addProperty("virtualhosts.virtualhost." + getName() + ".queues.deadLetterQueues", "true");
        getConfigXml().addProperty("virtualhosts.virtualhost." + getName() + ".queues(-1).queue(-1).name", "biggles");
        getConfigXml().addProperty("virtualhosts.virtualhost." + getName() + ".queues.queue.biggles.deadLetterQueues", "false");
        getConfigXml().addProperty("virtualhosts.virtualhost." + getName() + ".queues(-1).queue(-1).name", "beetle");


        getConfigXml().addProperty("virtualhosts.virtualhost.name", getName() + "Extra");
        getConfigXml().addProperty("virtualhosts.virtualhost." + getName() + "Extra.queues(-1).queue(-1).name", "r2d2");
        getConfigXml().addProperty("virtualhosts.virtualhost." + getName() + "Extra.queues.queue.r2d2.deadLetterQueues", "true");
        getConfigXml().addProperty("virtualhosts.virtualhost." + getName() + "Extra.queues(-1).queue(-1).name", "c3p0");
        getConfigXml().addProperty("virtualhosts.virtualhost." + getName() + "Extra.store.class", TestableMemoryMessageStore.class.getName());

        // Start the broker now.
        super.createBroker();

        // Get vhosts
        VirtualHost test = ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost(getName());
        VirtualHost extra = ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost(getName() + "Extra");

        // Enabled specifically
        assertTrue("Test vhost DLQ was configured as enabled", test.getConfiguration().isDeadLetterQueueEnabled());
        assertTrue("r2d2 queue DLQ was configured as enabled", extra.getConfiguration().getQueueConfiguration("r2d2").isDeadLetterQueueEnabled());

        // Enabled by test vhost default
        assertTrue("beetle queue DLQ was configured as enabled", test.getConfiguration().getQueueConfiguration("beetle").isDeadLetterQueueEnabled());

        // Disabled specifically
        assertFalse("Biggles queue DLQ was configured as disabled", test.getConfiguration().getQueueConfiguration("biggles").isDeadLetterQueueEnabled());

        // Using broker default of disabled
        assertFalse("Extra vhost DLQ disabled, using broker default", extra.getConfiguration().isDeadLetterQueueEnabled());
        assertFalse("c3p0 queue DLQ was configured as disabled", extra.getConfiguration().getQueueConfiguration("c3p0").isDeadLetterQueueEnabled());

        // Get queues
        AMQQueue biggles = test.getQueueRegistry().getQueue(new AMQShortString("biggles"));
        AMQQueue beetle = test.getQueueRegistry().getQueue(new AMQShortString("beetle"));
        AMQQueue r2d2 = extra.getQueueRegistry().getQueue(new AMQShortString("r2d2"));
        AMQQueue c3p0 = extra.getQueueRegistry().getQueue(new AMQShortString("c3p0"));

        // Disabled specifically for this queue, overriding virtualhost setting
        assertNull("Biggles queue should not have alt exchange as DLQ should be configured as disabled: " + biggles.getAlternateExchange(), biggles.getAlternateExchange());

        // Enabled for all queues on the virtualhost
        assertNotNull("Beetle queue should have an alt exchange as DLQ should be enabled, using test vhost default", beetle.getAlternateExchange());

        // Enabled specifically for this queue, overriding the default broker setting of disabled
        assertNotNull("R2D2 queue should have an alt exchange as DLQ should be configured as enabled", r2d2.getAlternateExchange());

        // Disabled by the default broker setting
        assertNull("C3PO queue should not have an alt exchange as DLQ should be disabled, using broker default", c3p0.getAlternateExchange());
    }

    /**
     * Test that the house keeping pool sizes is correctly processed
     *
     * @throws Exception
     */
    public void testHouseKeepingThreadCount() throws Exception
    {
        int initialPoolSize = 10;

        getConfigXml().addProperty("virtualhosts.virtualhost.testHouseKeepingThreadCount.housekeeping.poolSize",
                              initialPoolSize);

        // Start the broker now.
        super.createBroker();

        VirtualHost vhost =
                ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost(getName());

        assertEquals("HouseKeeping PoolSize not set correctly.",
                     initialPoolSize, vhost.getHouseKeepingPoolSize());
    }

    /**
     * Test default house keeping tasks
     *
     * @throws Exception
     */
    public void testDefaultHouseKeepingTasks() throws Exception
    {
        // Start the broker now.
        super.createBroker();

        VirtualHost vhost =
                ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost(getName());

        assertEquals("Default houseKeeping task count incorrect.", 2,
                     vhost.getHouseKeepingTaskCount());

        // Currently the two are tasks:
        // ExpiredMessageTask from VirtualHost
        // UpdateTask from the QMF ManagementExchange
    }

    /**
      * Test that we can dynamically change the thread pool size
      *
      * @throws Exception
      */
     public void testDynamicHouseKeepingPoolSizeChange() throws Exception
     {
         int initialPoolSize = 10;

         getConfigXml().addProperty("virtualhosts.virtualhost.testDynamicHouseKeepingPoolSizeChange.housekeeping.poolSize",
                               initialPoolSize);

         // Start the broker now.
         super.createBroker();

         VirtualHost vhost =
                 ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost(getName());

         assertEquals("HouseKeeping PoolSize not set correctly.",
                      initialPoolSize, vhost.getHouseKeepingPoolSize());

         vhost.setHouseKeepingPoolSize(1);

         assertEquals("HouseKeeping PoolSize not correctly change.",
                      1, vhost.getHouseKeepingPoolSize());

     }

     /**
      * Tests that the old element security.authentication.name is rejected.  This element
      * was never supported properly as authentication  is performed before the virtual host
      * is considered.
      */
     public void testSecurityAuthenticationNameRejected() throws Exception
     {
         getConfigXml().addProperty("virtualhosts.virtualhost.testSecurityAuthenticationNameRejected.security.authentication.name",
                 "testdb");

         try
         {
             super.createBroker();
             fail("Exception not thrown");
         }
         catch(ConfigurationException ce)
         {
             assertEquals("Incorrect error message",
                          "Validation error : security/authentication/name is no longer a supported element within the configuration xml." +
                          " It appears in virtual host definition : " + getName(),
                          ce.getMessage());
         }
     }

     /*
      * Tests that the old element housekeeping.expiredMessageCheckPeriod. ... (that was
      * replaced by housekeeping.checkPeriod) is rejected.
      */
     public void testExpiredMessageCheckPeriodRejected() throws Exception
     {
         getConfigXml().addProperty("virtualhosts.virtualhost.testExpiredMessageCheckPeriodRejected.housekeeping.expiredMessageCheckPeriod",
                 5);

         try
         {
             super.createBroker();
             fail("Exception not thrown");
         }
         catch (ConfigurationException ce)
         {
             assertEquals("Incorrect error message",
                     "Validation error : housekeeping/expiredMessageCheckPeriod must be replaced by housekeeping/checkPeriod." +
                     " It appears in virtual host definition : " + getName(),
                     ce.getMessage());
         }
     }
}
