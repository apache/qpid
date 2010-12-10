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

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.queue.AMQPriorityQueue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.queue.SimpleAMQQueue;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class AMQBrokerManagerMBeanTest extends TestCase
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


        ManagedBroker mbean = new AMQBrokerManagerMBean((VirtualHost.VirtualHostMBean) _vHost.getManagedObject());
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

        ManagedBroker mbean = new AMQBrokerManagerMBean((VirtualHost.VirtualHostMBean) _vHost.getManagedObject());

        assertTrue(_queueRegistry.getQueue(new AMQShortString(queueName)) == null);

        mbean.createNewQueue(queueName, "test", false);
        assertTrue(_queueRegistry.getQueue(new AMQShortString(queueName)) != null);

        mbean.deleteQueue(queueName);
        assertTrue(_queueRegistry.getQueue(new AMQShortString(queueName)) == null);
    }

    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_DLQ_ENABLED} argument true does
     * cause the alternate exchange to be set and DLQ to be produced.
     */
    public void testCreateNewQueueWithDLQEnabled() throws Exception
    {
        Map<String,Object> args = new HashMap<String, Object>();
        args.put(AMQQueueFactory.X_QPID_DLQ_ENABLED.asString(), true);

        AMQShortString queueName = new AMQShortString("testCreateNewQueueWithDLQEnabled");
        AMQShortString dlExchangeName = new AMQShortString(queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);

        QueueRegistry qReg = _vHost.getQueueRegistry();
        ExchangeRegistry exReg = _vHost.getExchangeRegistry();

        assertNull("The queue should not yet exist", qReg.getQueue(queueName));
        assertNull("The DLQ should not yet exist", qReg.getQueue(new AMQShortString(dlQueueName)));
        assertNull("The alternate exchange should not yet exist", exReg.getExchange(dlExchangeName));

        ManagedBroker mbean = new AMQBrokerManagerMBean((VirtualHost.VirtualHostMBean) _vHost.getManagedObject());
        mbean.createNewQueue(queueName.asString(), "test", false, args);

        Exchange altExchange = exReg.getExchange(dlExchangeName);
        assertNotNull("The alternate exchange should be registered as DLQ was enabled", altExchange);
        assertEquals("Alternate exchange type was not as expected", ExchangeDefaults.FANOUT_EXCHANGE_CLASS, altExchange.getType());

        AMQQueue dlQueue = qReg.getQueue(dlQueueName);
        assertNotNull("The DLQ was not registered as expected", dlQueue);
        assertTrue("DLQ should have been bound to the alternate exchange", altExchange.isBound(dlQueue));
    }
    
    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_DLQ_ENABLED} argument false does not 
     * result in the alternate exchange being set and DLQ being created.
     */
    public void testCreateNewQueueWithDLQDisabled() throws Exception
    {
        Map<String,Object> args = new HashMap<String, Object>();
        args.put(AMQQueueFactory.X_QPID_DLQ_ENABLED.asString(), false);

        AMQShortString queueName = new AMQShortString("testCreateNewQueueWithDLQDisabled");
        AMQShortString dlExchangeName = new AMQShortString(queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);

        QueueRegistry qReg = _vHost.getQueueRegistry();
        ExchangeRegistry exReg = _vHost.getExchangeRegistry();

        assertNull("The queue should not yet exist", qReg.getQueue(queueName));
        assertNull("The DLQ should not exist", qReg.getQueue(new AMQShortString(dlQueueName)));
        assertNull("The alternate exchange should not exist", exReg.getExchange(dlExchangeName));

        ManagedBroker mbean = new AMQBrokerManagerMBean((VirtualHost.VirtualHostMBean) _vHost.getManagedObject());
        mbean.createNewQueue(queueName.asString(), "test", false, args);

        Exchange altExchange = exReg.getExchange(dlExchangeName);
        assertNull("The alternate exchange should be not registered as DLQ was disabled", altExchange);

        AMQQueue dlQueue = qReg.getQueue(dlQueueName);
        assertNull("The DLQ should not be registered as DLQ was disabled on created queue", dlQueue);
        
        AMQQueue queue = qReg.getQueue(queueName);
        assertNull("The alternate exchange should be not set as DLQ was disabled", queue.getAlternateExchange());
    }

    /**
     * Tests that not setting the {@link AMQQueueFactory#X_QPID_DLQ_ENABLED} argument does not 
     * result in the alternate exchange being set and DLQ being created (assuming that
     * DLQing isn't enabled at broker or vhost levels).
     */
    public void testCreateNewQueueWithDLQUnspecified() throws Exception
    {
        AMQShortString queueName = new AMQShortString("testCreateNewQueueWithDLQUnspecified");
        AMQShortString dlExchangeName = new AMQShortString(queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);

        QueueRegistry qReg = _vHost.getQueueRegistry();
        ExchangeRegistry exReg = _vHost.getExchangeRegistry();

        assertNull("The queue should not yet exist", qReg.getQueue(queueName));
        assertNull("The DLQ should not exist", qReg.getQueue(new AMQShortString(dlQueueName)));
        assertNull("The alternate exchange should not exist", exReg.getExchange(dlExchangeName));

        ManagedBroker mbean = new AMQBrokerManagerMBean((VirtualHost.VirtualHostMBean) _vHost.getManagedObject());
        mbean.createNewQueue(queueName.asString(), "test", false, null);

        Exchange altExchange = exReg.getExchange(dlExchangeName);
        assertNull("The alternate exchange should be not registered as DLQ wasnt enabled", altExchange);

        AMQQueue dlQueue = qReg.getQueue(dlQueueName);
        assertNull("The DLQ should not be registered as DLQ wasnt enabled on created queue", dlQueue);
        
        AMQQueue queue = qReg.getQueue(queueName);
        assertNull("The alternate exchange should be not set as DLQ wasnt enabled", queue.getAlternateExchange());
    }

    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_PRIORITIES} argument prompts creation of
     * a Priority Queue.
     */
    public void testCreatePriorityQueue() throws Exception
    {
        int numPriorities = 7;
        Map<String,Object> args = new HashMap<String, Object>();
        args.put(AMQQueueFactory.X_QPID_PRIORITIES.asString(), numPriorities);

        AMQShortString queueName = new AMQShortString("testCreatePriorityQueue");

        QueueRegistry qReg = _vHost.getQueueRegistry();
        ExchangeRegistry exReg = _vHost.getExchangeRegistry();

        assertNull("The queue should not yet exist", qReg.getQueue(queueName));

        ManagedBroker mbean = new AMQBrokerManagerMBean((VirtualHost.VirtualHostMBean) _vHost.getManagedObject());
        mbean.createNewQueue(queueName.asString(), "test", false, args);
        
        AMQQueue queue = qReg.getQueue(queueName);
        assertEquals("Queue is not a priorty queue", AMQPriorityQueue.class, queue.getClass());
        assertEquals("Number of priorities supported was not as expected", numPriorities, ((AMQPriorityQueue)queue).getPriorities());
    }
    
    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        IApplicationRegistry appRegistry = ApplicationRegistry.getInstance();
        _vHost = appRegistry.getVirtualHostRegistry().getVirtualHost("test");
        _queueRegistry = _vHost.getQueueRegistry();
        _exchangeRegistry = _vHost.getExchangeRegistry();
    }

    @Override
    protected void tearDown() throws Exception
    {
        //Ensure we close the opened Registry
        ApplicationRegistry.remove();
    }
}
