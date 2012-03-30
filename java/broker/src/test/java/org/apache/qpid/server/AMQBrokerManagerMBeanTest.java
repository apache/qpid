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
package org.apache.qpid.server;

import org.apache.commons.configuration.XMLConfiguration;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.TestLogActor;
import org.apache.qpid.server.queue.AMQPriorityQueue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.store.TestableMemoryMessageStoreFactory;
import org.apache.qpid.server.util.TestApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.test.utils.QpidTestCase;

import java.util.HashMap;
import java.util.Map;

public class AMQBrokerManagerMBeanTest extends QpidTestCase
{
    private QueueRegistry _queueRegistry;
    private ExchangeRegistry _exchangeRegistry;
    private VirtualHost _vHost;

    public void testExchangeOperations() throws Exception
    {
        String exchange1 = "testExchange1_" + System.currentTimeMillis();
        String exchange2 = "testExchange2_" + System.currentTimeMillis();
        String exchange3 = "testExchange3_" + System.currentTimeMillis();

        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange1)) == null);
        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange2)) == null);
        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange3)) == null);


        ManagedBroker mbean = new AMQBrokerManagerMBean((VirtualHostImpl.VirtualHostMBean) _vHost.getManagedObject());
        mbean.createNewExchange(exchange1, "direct", false);
        mbean.createNewExchange(exchange2, "topic", false);
        mbean.createNewExchange(exchange3, "headers", false);

        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange1)) != null);
        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange2)) != null);
        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange3)) != null);

        mbean.unregisterExchange(exchange1);
        mbean.unregisterExchange(exchange2);
        mbean.unregisterExchange(exchange3);

        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange1)) == null);
        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange2)) == null);
        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange3)) == null);
    }

    public void testQueueOperations() throws Exception
    {
        String queueName = "testQueue_" + System.currentTimeMillis();

        ManagedBroker mbean = new AMQBrokerManagerMBean((VirtualHostImpl.VirtualHostMBean) _vHost.getManagedObject());

        assertTrue(_queueRegistry.getQueue(new AMQShortString(queueName)) == null);

        mbean.createNewQueue(queueName, "test", false);
        assertTrue(_queueRegistry.getQueue(new AMQShortString(queueName)) != null);

        mbean.deleteQueue(queueName);
        assertTrue(_queueRegistry.getQueue(new AMQShortString(queueName)) == null);
    }

    public void testCreateNewQueueBindsToDefaultExchange() throws Exception
    {
        String queueName = "testQueue_" + System.currentTimeMillis();

        ManagedBroker mbean = new AMQBrokerManagerMBean((VirtualHostImpl.VirtualHostMBean) _vHost.getManagedObject());
        ExchangeRegistry exReg = _vHost.getExchangeRegistry();
        Exchange defaultExchange =  exReg.getDefaultExchange();

        mbean.createNewQueue(queueName, "test", false);
        assertTrue(_queueRegistry.getQueue(new AMQShortString(queueName)) != null);

        assertTrue("New queue should be bound to default exchange", defaultExchange.isBound(new AMQShortString(queueName)));
    }

    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_MAXIMUM_DELIVERY_COUNT} argument does cause the
     * maximum delivery count to be set on the Queue.
     */
    public void testCreateNewQueueWithMaximumDeliveryCount() throws Exception
    {
        final Map<String,Object> args = new HashMap<String, Object>();
        args.put(AMQQueueFactory.X_QPID_MAXIMUM_DELIVERY_COUNT, 5);

        final AMQShortString queueName = new AMQShortString("testCreateNewQueueWithMaximumDeliveryCount");

        final QueueRegistry qReg = _vHost.getQueueRegistry();

        assertNull("The queue should not yet exist", qReg.getQueue(queueName));

        final ManagedBroker mbean = new AMQBrokerManagerMBean((VirtualHostImpl.VirtualHostMBean) _vHost.getManagedObject());
        mbean.createNewQueue(queueName.asString(), "test", false, args);

        final AMQQueue createdQueue = qReg.getQueue(queueName);
        assertNotNull("The queue was not registered as expected", createdQueue);
        assertEquals("Unexpected maximum delivery count", 5, createdQueue.getMaximumDeliveryCount());
    }

    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_PRIORITIES} argument prompts creation of
     * a Priority Queue.
     */
    public void testCreatePriorityQueue() throws Exception
    {
        int numPriorities = 7;
        Map<String,Object> args = new HashMap<String, Object>();
        args.put(AMQQueueFactory.X_QPID_PRIORITIES, numPriorities);

        AMQShortString queueName = new AMQShortString("testCreatePriorityQueue");

        QueueRegistry qReg = _vHost.getQueueRegistry();

        assertNull("The queue should not yet exist", qReg.getQueue(queueName));

        ManagedBroker mbean = new AMQBrokerManagerMBean((VirtualHostImpl.VirtualHostMBean) _vHost.getManagedObject());
        mbean.createNewQueue(queueName.asString(), "test", false, args);

        AMQQueue queue = qReg.getQueue(queueName);
        assertEquals("Queue is not a priorty queue", AMQPriorityQueue.class, queue.getClass());
        assertEquals("Number of priorities supported was not as expected", numPriorities, ((AMQPriorityQueue)queue).getPriorities());
    }

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        CurrentActor.set(new TestLogActor(new SystemOutMessageLogger()));

        XMLConfiguration configXml = new XMLConfiguration();
        configXml.addProperty("virtualhosts.virtualhost(-1).name", "test");
        configXml.addProperty("virtualhosts.virtualhost(-1).test.store.factoryclass", TestableMemoryMessageStoreFactory.class.getName());

        ServerConfiguration configuration = new ServerConfiguration(configXml);

        ApplicationRegistry registry = new TestApplicationRegistry(configuration);
        ApplicationRegistry.initialise(registry);
        registry.getVirtualHostRegistry().setDefaultVirtualHostName("test");

        IApplicationRegistry appRegistry = ApplicationRegistry.getInstance();
        _vHost = appRegistry.getVirtualHostRegistry().getVirtualHost("test");
        _queueRegistry = _vHost.getQueueRegistry();
        _exchangeRegistry = _vHost.getExchangeRegistry();
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            ApplicationRegistry.remove();
        }
    }
}
