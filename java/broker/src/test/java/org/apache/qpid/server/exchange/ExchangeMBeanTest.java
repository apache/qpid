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
package org.apache.qpid.server.exchange;

import org.apache.commons.lang.ArrayUtils;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.management.common.mbeans.ManagedExchange;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.util.InternalBrokerBaseCase;
import org.apache.qpid.server.virtualhost.VirtualHost;

import javax.management.JMException;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.TabularData;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Unit test class for testing different Exchange MBean operations
 */
public class ExchangeMBeanTest  extends InternalBrokerBaseCase
{
    private AMQQueue _queue;
    private QueueRegistry _queueRegistry;
    private VirtualHost _virtualHost;

    public void testGeneralProperties() throws Exception
    {
        DirectExchange exchange = new DirectExchange();
        exchange.initialise(_virtualHost, ExchangeDefaults.DIRECT_EXCHANGE_NAME, false, 0, true);
        ManagedObject managedObj = exchange.getManagedObject();
        ManagedExchange mbean = (ManagedExchange)managedObj;

        // test general exchange properties
        assertEquals("Unexpected exchange name", "amq.direct", mbean.getName());
        assertEquals("Unexpected exchange type", "direct", mbean.getExchangeType());
        assertEquals("Unexpected ticket number", Integer.valueOf(0), mbean.getTicketNo());
        assertFalse("Unexpected durable flag", mbean.isDurable());
        assertTrue("Unexpected auto delete flag", mbean.isAutoDelete());
    }

    public void testDirectExchangeMBean() throws Exception
    {
        DirectExchange exchange = new DirectExchange();
        exchange.initialise(_virtualHost, ExchangeDefaults.DIRECT_EXCHANGE_NAME, false, 0, true);
        ManagedObject managedObj = exchange.getManagedObject();
        ManagedExchange mbean = (ManagedExchange)managedObj;

        mbean.createNewBinding(_queue.getNameShortString().toString(), "binding1");
        mbean.createNewBinding(_queue.getNameShortString().toString(), "binding2");

        TabularData data = mbean.bindings();
        ArrayList<Object> list = new ArrayList<Object>(data.values());
        assertTrue(list.size() == 2);
    }

    public void testTopicExchangeMBean() throws Exception
    {
        TopicExchange exchange = new TopicExchange();
        exchange.initialise(_virtualHost,ExchangeDefaults.TOPIC_EXCHANGE_NAME, false, 0, true);
        ManagedObject managedObj = exchange.getManagedObject();
        ManagedExchange mbean = (ManagedExchange)managedObj;

        mbean.createNewBinding(_queue.getNameShortString().toString(), "binding1");
        mbean.createNewBinding(_queue.getNameShortString().toString(), "binding2");

        TabularData data = mbean.bindings();
        ArrayList<Object> list = new ArrayList<Object>(data.values());
        assertTrue(list.size() == 2);
    }

    public void testHeadersExchangeMBean() throws Exception
    {
        HeadersExchange exchange = new HeadersExchange();
        exchange.initialise(_virtualHost,ExchangeDefaults.HEADERS_EXCHANGE_NAME, false, 0, true);
        ManagedObject managedObj = exchange.getManagedObject();
        ManagedExchange mbean = (ManagedExchange)managedObj;

        mbean.createNewBinding(_queue.getNameShortString().toString(), "x-match=any,key1=binding1,key2=binding2");

        TabularData data = mbean.bindings();
        ArrayList<Object> list = new ArrayList<Object>(data.values());
        assertEquals("Unexpected number of bindings", 1, list.size());

        final Iterator<CompositeDataSupport> rowItr = (Iterator<CompositeDataSupport>) data.values().iterator();
        CompositeDataSupport row = rowItr.next();
        assertBinding(1, _queue.getName(), new String[]{"x-match=any","key1=binding1","key2=binding2"}, row);
    }

    /**
     * Included to ensure 0-10 Specification compliance:
     * 2.3.1.4 "the field in the bind arguments has no value and a field of the same name is present in the message headers
     */
    public void testHeadersExchangeMBeanMatchPropertyNoValue() throws Exception
    {
        HeadersExchange exchange = new HeadersExchange();
        exchange.initialise(_virtualHost,ExchangeDefaults.HEADERS_EXCHANGE_NAME, false, 0, true);
        ManagedObject managedObj = exchange.getManagedObject();
        ManagedExchange mbean = (ManagedExchange)managedObj;

        mbean.createNewBinding(_queue.getNameShortString().toString(), "x-match=any,key4,key5=");

        TabularData data = mbean.bindings();
        ArrayList<Object> list = new ArrayList<Object>(data.values());
        assertEquals("Unexpected number of bindings", 1, list.size());

        final Iterator<CompositeDataSupport> rowItr = (Iterator<CompositeDataSupport>) data.values().iterator();
        CompositeDataSupport row = rowItr.next();
        assertBinding(1, _queue.getName(), new String[]{"x-match=any","key4=","key5="}, row);
    }

    public void testInvalidHeaderBindingMalformed() throws Exception
    {
        HeadersExchange exchange = new HeadersExchange();
        exchange.initialise(_virtualHost,ExchangeDefaults.HEADERS_EXCHANGE_NAME, false, 0, true);
        ManagedObject managedObj = exchange.getManagedObject();
        ManagedExchange mbean = (ManagedExchange)managedObj;

        try
        {
            mbean.createNewBinding(_queue.getNameShortString().toString(), "x-match=any,=value4");
            fail("Exception not thrown");
        }
        catch (JMException jme)
        {
            //pass
        }
    }

    private void assertBinding(final int expectedBindingNo, final String expectedQueueName, final String[] expectedBindingArray,
                                final CompositeDataSupport row)
    {
        final Number bindingNumber = (Number) row.get(ManagedExchange.HDR_BINDING_NUMBER);
        final String queueName = (String) row.get(ManagedExchange.HDR_QUEUE_NAME);
        final String[] bindings = (String[]) row.get(ManagedExchange.HDR_QUEUE_BINDINGS);
        assertEquals("Unexpected binding number", expectedBindingNo, bindingNumber);
        assertEquals("Unexpected queue name", expectedQueueName, queueName);
        assertEquals("Unexpected no of bindings", expectedBindingArray.length, bindings.length);
        for(String binding : bindings)
        {
            assertTrue("Expected binding not found: " + binding, ArrayUtils.contains(expectedBindingArray, binding));
        }
    }

    /**
     * Test adding bindings and removing them from the default exchange via JMX.
     * <p>
     * QPID-2700
     */
    public void testDefaultBindings() throws Exception
    {
        int bindings = _queue.getBindingCount();
        
        Exchange exchange = _queue.getVirtualHost().getExchangeRegistry().getDefaultExchange();
        ManagedExchange mbean = (ManagedExchange) ((AbstractExchange) exchange).getManagedObject();
        
        mbean.createNewBinding(_queue.getName(), "robot");
        mbean.createNewBinding(_queue.getName(), "kitten");

        assertEquals("Should have added two bindings", bindings + 2, _queue.getBindingCount());
        
        mbean.removeBinding(_queue.getName(), "robot");

        assertEquals("Should have one extra binding", bindings + 1, _queue.getBindingCount());
        
        mbean.removeBinding(_queue.getName(), "kitten");

        assertEquals("Should have original number of binding", bindings, _queue.getBindingCount());
    }
    
    /**
     * Test adding bindings and removing them from the topic exchange via JMX.
     * <p>
     * QPID-2700
     */
    public void testTopicBindings() throws Exception
    {
        int bindings = _queue.getBindingCount();
        
        Exchange exchange = _queue.getVirtualHost().getExchangeRegistry().getExchange(new AMQShortString("amq.topic"));
        ManagedExchange mbean = (ManagedExchange) ((AbstractExchange) exchange).getManagedObject();
        
        mbean.createNewBinding(_queue.getName(), "robot.#");
        mbean.createNewBinding(_queue.getName(), "#.kitten");

        assertEquals("Should have added two bindings", bindings + 2, _queue.getBindingCount());
        
        mbean.removeBinding(_queue.getName(), "robot.#");

        assertEquals("Should have one extra binding", bindings + 1, _queue.getBindingCount());
        
        mbean.removeBinding(_queue.getName(), "#.kitten");

        assertEquals("Should have original number of binding", bindings, _queue.getBindingCount());
    }

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        IApplicationRegistry applicationRegistry = ApplicationRegistry.getInstance();
        _virtualHost = applicationRegistry.getVirtualHostRegistry().getVirtualHost("test");
        _queueRegistry = _virtualHost.getQueueRegistry();
        _queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("testQueue"), false, new AMQShortString("ExchangeMBeanTest"), false, false,
                                                    _virtualHost, null);
        _queueRegistry.registerQueue(_queue);
    }
}
