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
import org.apache.commons.configuration.XMLConfiguration;
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
    public void setUp() throws Exception
    {
        super.setUp();
        // Set the default configuration items
        getConfigXml().clear();
        getConfigXml().addProperty("virtualhosts.virtualhost(-1).name", "test");
        getConfigXml().addProperty("virtualhosts.virtualhost(-1).test.store.class", TestableMemoryMessageStore.class.getName());

        getConfigXml().addProperty("virtualhosts.virtualhost.name", getName());
        getConfigXml().addProperty("virtualhosts.virtualhost."+getName()+".store.class", TestableMemoryMessageStore.class.getName());
    }

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


}
