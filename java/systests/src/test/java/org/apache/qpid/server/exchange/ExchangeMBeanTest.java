/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.exchange;

import junit.framework.TestCase;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import java.util.ArrayList;

/**
 * Unit test class for testing different Exchange MBean operations
 */
public class ExchangeMBeanTest  extends TestCase
{
    private AMQQueue _queue;
    private QueueRegistry _queueRegistry = ApplicationRegistry.getInstance().getQueueRegistry();

    /**
     * Test for direct exchange mbean
     * @throws Exception
     */
    public void testDirectExchangeMBean() throws Exception
    {
        DestNameExchange exchange = new DestNameExchange();
        exchange.initialise("amq.direct", false, 0, true);
        ManagedObject managedObj = exchange.getManagedObject();
        ManagedExchange mbean = (ManagedExchange)managedObj;

        mbean.createNewBinding(_queue.getName(), "binding1");
        mbean.createNewBinding(_queue.getName(), "binding2");

        TabularData data = mbean.bindings();
        ArrayList<CompositeData> list = new ArrayList<CompositeData>(data.values());
        assertTrue(list.size() == 2);

        // test general exchange properties
        assertEquals(mbean.getName(), "amq.direct");
        assertEquals(mbean.getExchangeType(), "direct");
        assertTrue(mbean.getTicketNo() == 0);
        assertTrue(!mbean.isDurable());
        assertTrue(mbean.isAutoDelete());
    }

    /**
     * Test for "topic" exchange mbean
     * @throws Exception
     */
    public void testTopicExchangeMBean() throws Exception
    {
        DestWildExchange exchange = new DestWildExchange();
        exchange.initialise("amq.topic", false, 0, true);
        ManagedObject managedObj = exchange.getManagedObject();
        ManagedExchange mbean = (ManagedExchange)managedObj;

        mbean.createNewBinding(_queue.getName(), "binding1");
        mbean.createNewBinding(_queue.getName(), "binding2");

        TabularData data = mbean.bindings();
        ArrayList<CompositeData> list = new ArrayList<CompositeData>(data.values());
        assertTrue(list.size() == 2);

        // test general exchange properties
        assertEquals(mbean.getName(), "amq.topic");
        assertEquals(mbean.getExchangeType(), "topic");
        assertTrue(mbean.getTicketNo() == 0);
        assertTrue(!mbean.isDurable());
        assertTrue(mbean.isAutoDelete());
    }

    /**
     * Test for "Headers" exchange mbean
     * @throws Exception
     */
    public void testHeadersExchangeMBean() throws Exception
    {
        HeadersExchange exchange = new HeadersExchange();
        exchange.initialise("amq.headers", false, 0, true);
        ManagedObject managedObj = exchange.getManagedObject();
        ManagedExchange mbean = (ManagedExchange)managedObj;

        mbean.createNewBinding(_queue.getName(), "key1=binding1,key2=binding2");
        mbean.createNewBinding(_queue.getName(), "key3=binding3");

        TabularData data = mbean.bindings();
        ArrayList<CompositeData> list = new ArrayList<CompositeData>(data.values());
        assertTrue(list.size() == 2);

        // test general exchange properties
        assertEquals(mbean.getName(), "amq.headers");
        assertEquals(mbean.getExchangeType(), "headers");
        assertTrue(mbean.getTicketNo() == 0);
        assertTrue(!mbean.isDurable());
        assertTrue(mbean.isAutoDelete());
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _queue = new AMQQueue("testQueue", false, "ExchangeMBeanTest", false, _queueRegistry);
        _queueRegistry.registerQueue(_queue);
    }
}
