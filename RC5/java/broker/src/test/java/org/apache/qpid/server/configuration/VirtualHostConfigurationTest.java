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


import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.HierarchicalConfiguration.Node;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.queue.AMQPriorityQueue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;

import junit.framework.TestCase;

public class VirtualHostConfigurationTest extends TestCase
{

    private File configFile;
    private VirtualHostConfiguration vhostConfig;
    private XMLConfiguration  configXml;

    @Override
    protected void setUp() throws Exception
    {
        // Create temporary configuration file
        configFile = File.createTempFile(this.getName()+"config", ".xml");
        configFile.deleteOnExit();
        
        // Fill config file with stuff
        configXml = new XMLConfiguration();
        configXml.setRootElementName("virtualhosts");
        configXml.addProperty("virtualhost(-1).name", "test");
    }
    
    public void testQueuePriority() throws ConfigurationException, AMQException
    {
        // Set up queue with 5 priorities
        configXml.addProperty("virtualhost.test.queues(-1).queue(-1).name(-1)", 
                              "atest");
        configXml.addProperty("virtualhost.test.queues.queue.atest(-1).exchange", 
                              "amq.direct");
        configXml.addProperty("virtualhost.test.queues.queue.atest.priorities", 
                              "5");

        // Set up queue with JMS style priorities
        configXml.addProperty("virtualhost.test.queues(-1).queue(-1).name(-1)", 
                              "ptest");
        configXml.addProperty("virtualhost.test.queues.queue.ptest(-1).exchange", 
                              "amq.direct");
        configXml.addProperty("virtualhost.test.queues.queue.ptest.priority", 
                               "true");
        
        // Set up queue with no priorities
        configXml.addProperty("virtualhost.test.queues(-1).queue(-1).name(-1)", 
                              "ntest");
        configXml.addProperty("virtualhost.test.queues.queue.ntest(-1).exchange", 
                              "amq.direct");
        configXml.addProperty("virtualhost.test.queues.queue.ntest.priority", 
                              "false");
        configXml.save(configFile);
        
        // Setup virtual host configuration
        vhostConfig = new VirtualHostConfiguration(configFile.getAbsolutePath());
        
        // Do bindings and get resulting vhost
        vhostConfig.performBindings();
        VirtualHost vhost = ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost("test");
        
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
    
}
