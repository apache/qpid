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
package org.apache.qpid.server.queue;

import junit.framework.TestCase;

import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.AMQException;

public class AMQQueueFactoryTest extends TestCase
{
    QueueRegistry _queueRegistry;
    VirtualHost _virtualHost;

    public void setUp()
    {
        ApplicationRegistry registry = (ApplicationRegistry) ApplicationRegistry.getInstance();

        _virtualHost = registry.getVirtualHostRegistry().getVirtualHost("test");

        _queueRegistry = _virtualHost.getQueueRegistry();

        assertEquals("Queues registered on an empty virtualhost", 0, _queueRegistry.getQueues().size());
    }

    public void tearDown()
    {
        ApplicationRegistry.remove();
    }

    private void verifyRegisteredQueueCount(int count)
    {
        assertEquals("Queue was not registered in virtualhost", count, _queueRegistry.getQueues().size());
    }

    public void testPriorityQueueRegistration()
    {
        FieldTable fieldTable = new FieldTable();
        fieldTable.put(new AMQShortString(AMQQueueFactory.X_QPID_PRIORITIES), 5);

        try
        {
            AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("testPriorityQueue"), false, new AMQShortString("owner"), false,
                                               _virtualHost, fieldTable);

            assertEquals("Queue not a priorty queue", AMQPriorityQueue.class, queue.getClass());
            verifyRegisteredQueueCount(1);
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }


    public void testSimpleQueueRegistration()
    {
        try
        {
            AMQShortString queueName = new AMQShortString("testSimpleQueueRegistration");
            AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);
            
            AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("testQueue"), false, new AMQShortString("owner"), false,
                                               _virtualHost, null);
            assertEquals("Queue not a simple queue", SimpleAMQQueue.class, queue.getClass());
            
            //verify that no alternate exchange or DLQ were produced
            QueueRegistry qReg = _virtualHost.getQueueRegistry();

            assertNull("Queue should not have an alternate exchange as DLQ wasnt enabled", queue.getAlternateExchange());
            assertNull("The DLQ should not exist", qReg.getQueue(dlQueueName));
            
            verifyRegisteredQueueCount(1);
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_DLQ_ENABLED} argument true does
     * cause the alternate exchange to be set and DLQ to be produced.
     */
    public void testDeadLetterQueueEnabled()
    {
        FieldTable fieldTable = new FieldTable();
        fieldTable.put(AMQQueueFactory.X_QPID_DLQ_ENABLED, true);
        
        AMQShortString queueName = new AMQShortString("testDeadLetterQueueEnabled");
        AMQShortString dlExchangeName = new AMQShortString(queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);
        
        try
        {
            QueueRegistry qReg = _virtualHost.getQueueRegistry();
            ExchangeRegistry exReg = _virtualHost.getExchangeRegistry();

            assertNull("The DLQ should not yet exist", qReg.getQueue(dlQueueName));
            assertNull("The alternate exchange should not yet exist", exReg.getExchange(dlExchangeName));

            AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueName, false, new AMQShortString("owner"), false,
                                               _virtualHost, fieldTable);

            Exchange altExchange = queue.getAlternateExchange();
            assertNotNull("Queue should have an alternate exchange as DLQ is enabled", altExchange);
            assertEquals("Alternate exchange name was not as expected", dlExchangeName, altExchange.getName());
            assertEquals("Alternate exchange type was not as expected", ExchangeDefaults.FANOUT_EXCHANGE_CLASS, altExchange.getType());

            assertNotNull("The alternate exchange was not registered as expected", exReg.getExchange(dlExchangeName));
            assertEquals("The registered exchange was not the expected exchange instance", altExchange, exReg.getExchange(dlExchangeName));

            AMQQueue dlQueue = qReg.getQueue(dlQueueName);
            assertNotNull("The DLQ was not registered as expected", dlQueue);
            assertTrue("DLQ should have been bound to the alternate exchange", altExchange.isBound(dlQueue));

            //2 queues should have been registered
            verifyRegisteredQueueCount(2);
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }
    
    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_DLQ_ENABLED} argument false does not 
     * result in the alternate exchange being set and DLQ being created.
     */
    public void testDeadLetterQueueDisabled()
    {
        FieldTable fieldTable = new FieldTable();
        fieldTable.put(AMQQueueFactory.X_QPID_DLQ_ENABLED, false);
        
        AMQShortString queueName = new AMQShortString("testDeadLetterQueueDisabled");
        AMQShortString dlExchangeName = new AMQShortString(queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);
        
        try
        {
            QueueRegistry qReg = _virtualHost.getQueueRegistry();
            ExchangeRegistry exReg = _virtualHost.getExchangeRegistry();

            assertNull("The DLQ should not yet exist", qReg.getQueue(dlQueueName));
            assertNull("The alternate exchange should not exist", exReg.getExchange(dlExchangeName));

            AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueName, false, new AMQShortString("owner"), false,
                                               _virtualHost, fieldTable);

            assertNull("Queue should not have an alternate exchange as DLQ is disabled", queue.getAlternateExchange());
            assertNull("The alternate exchange should still not exist", exReg.getExchange(dlExchangeName));
            
            assertNull("The DLQ should still not exist", qReg.getQueue(dlQueueName));

            //only 1 queue should have been registered
            verifyRegisteredQueueCount(1);
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }
    
    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_DLQ_ENABLED} argument true but
     * creating an auto-delete queue, does not result in the alternate exchange
     * being set and DLQ being created.
     */
    public void testDeadLetterQueueNotCreatedForAutodeleteQueues()
    {
        FieldTable fieldTable = new FieldTable();
        fieldTable.put(AMQQueueFactory.X_QPID_DLQ_ENABLED, true);
        
        AMQShortString queueName = new AMQShortString("testDeadLetterQueueNotCreatedForAutodeleteQueues");
        AMQShortString dlExchangeName = new AMQShortString(queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);
        
        try
        {
            QueueRegistry qReg = _virtualHost.getQueueRegistry();
            ExchangeRegistry exReg = _virtualHost.getExchangeRegistry();

            assertNull("The DLQ should not yet exist", qReg.getQueue(dlQueueName));
            assertNull("The alternate exchange should not exist", exReg.getExchange(dlExchangeName));

            //create an autodelete queue
            AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueName, false, new AMQShortString("owner"), true,
                                               _virtualHost, fieldTable);
            assertTrue("Queue should be autodelete", queue.isAutoDelete());

            //ensure that the autodelete property overrides the request to enable DLQ
            assertNull("Queue should not have an alternate exchange as queue is autodelete", queue.getAlternateExchange());
            assertNull("The alternate exchange should not exist as queue is autodelete", exReg.getExchange(dlExchangeName));
            assertNull("The DLQ should not exist as queue is autodelete", qReg.getQueue(dlQueueName));

            //only 1 queue should have been registered
            verifyRegisteredQueueCount(1);
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }
}
