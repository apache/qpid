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


import junit.framework.TestCase;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.queue.AMQPriorityQueue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class VirtualHostConfigurationTest extends TestCase
{

    private VirtualHostConfiguration vhostConfig;
    private XMLConfiguration  configXml;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        //Highlight that this test will cause a new AR to be created
        ApplicationRegistry.getInstance();
        // Fill config file with stuff
        configXml = new XMLConfiguration();
        configXml.setRootElementName("virtualhosts");
        configXml.addProperty("virtualhost(-1).name", "test");
        configXml.addProperty("virtualhost(-1).name", "extra");
    }

    public void tearDown() throws Exception
    {
        //Correctly close the AR we created
        ApplicationRegistry.remove();

        super.tearDown();
    }
    
    public void testQueuePriority() throws Exception
    {
        // Set up queue with 5 priorities
        configXml.addProperty("virtualhost.test.queues(-1).queue(-1).name", "atest");
        configXml.addProperty("virtualhost.test.queues.queue.atest.exchange", "amq.direct");
        configXml.addProperty("virtualhost.test.queues.queue.atest.priorities",  "5");

        // Set up queue with JMS style priorities
        configXml.addProperty("virtualhost.test.queues(-1).queue(-1).name", "ptest");
        configXml.addProperty("virtualhost.test.queues.queue.ptest.exchange", "amq.direct");
        configXml.addProperty("virtualhost.test.queues.queue.ptest.priority", "true");
        
        // Set up queue with no priorities
        configXml.addProperty("virtualhost.test.queues(-1).queue(-1).name", "ntest");
        configXml.addProperty("virtualhost.test.queues.queue.ntest.exchange", "amq.direct");
        configXml.addProperty("virtualhost.test.queues.queue.ntest.priority", "false");
        
        VirtualHost vhost = new VirtualHost(new VirtualHostConfiguration("test", configXml.subset("virtualhost.test")));
        
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
        configXml.addProperty("virtualhost.test.queues.exchange", "amq.topic");
        configXml.addProperty("virtualhost.test.queues.maximumQueueDepth", "1");
        configXml.addProperty("virtualhost.test.queues.maximumMessageSize", "2");
        configXml.addProperty("virtualhost.test.queues.maximumMessageAge", "3");
        
        configXml.addProperty("virtualhost.test.queues(-1).queue(-1).name", "atest");
        configXml.addProperty("virtualhost.test.queues.queue.atest.exchange", "amq.direct");
        configXml.addProperty("virtualhost.test.queues.queue.atest.maximumQueueDepth", "4");
        configXml.addProperty("virtualhost.test.queues.queue.atest.maximumMessageSize", "5");
        configXml.addProperty("virtualhost.test.queues.queue.atest.maximumMessageAge", "6");

        configXml.addProperty("virtualhost.test.queues(-1).queue(-1).name", "btest");
        
        VirtualHost vhost = new VirtualHost(new VirtualHostConfiguration("test", configXml.subset("virtualhost.test")));
        
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
     * Tests the full set of configuration options for enabling DLQs in the broker configuration.
     */
    public void testIsDeadLetterQueueEnabled() throws Exception
    {
        // Set up vhosts and queues
        configXml.addProperty("virtualhost.test.queues.deadLetterQueues", "true");
        configXml.addProperty("virtualhost.test.queues(-1).queue(-1).name", "biggles");
        configXml.addProperty("virtualhost.test.queues.queue.biggles.deadLetterQueues", "false");
        configXml.addProperty("virtualhost.test.queues(-1).queue(-1).name", "beetle");

        configXml.addProperty("virtualhost.extra.queues(-1).queue(-1).name", "r2d2");
        configXml.addProperty("virtualhost.extra.queues.queue.r2d2.deadLetterQueues", "true");
        configXml.addProperty("virtualhost.extra.queues(-1).queue(-1).name", "c3p0");

        // Get vhosts
        VirtualHost test = new VirtualHost(new VirtualHostConfiguration("test", configXml.subset("virtualhost.test")));
        VirtualHost extra = new VirtualHost(new VirtualHostConfiguration("extra", configXml.subset("virtualhost.extra")));
        
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
    
}
