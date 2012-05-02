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

import java.util.UUID;

import org.apache.commons.configuration.XMLConfiguration;

import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.TestLogActor;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.store.TestableMemoryMessageStoreFactory;
import org.apache.qpid.server.util.TestApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

public class AMQQueueFactoryTest extends QpidTestCase
{
    private QueueRegistry _queueRegistry;
    private VirtualHost _virtualHost;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        CurrentActor.set(new TestLogActor(new SystemOutMessageLogger()));

        XMLConfiguration configXml = new XMLConfiguration();
        configXml.addProperty("virtualhosts.virtualhost(-1).name", getName());
        configXml.addProperty("virtualhosts.virtualhost(-1)."+getName()+".store.factoryclass", TestableMemoryMessageStoreFactory.class.getName());

        ServerConfiguration configuration = new ServerConfiguration(configXml);

        ApplicationRegistry registry = new TestApplicationRegistry(configuration);
        ApplicationRegistry.initialise(registry);
        registry.getVirtualHostRegistry().setDefaultVirtualHostName(getName());

        _virtualHost = registry.getVirtualHostRegistry().getVirtualHost(getName());

        _queueRegistry = _virtualHost.getQueueRegistry();

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

    private void verifyRegisteredQueueCount(int count)
    {
        assertEquals("Queue was not registered in virtualhost", count, _queueRegistry.getQueues().size());
    }


    private void verifyQueueRegistered(String queueName)
    {
        assertNotNull("Queue " + queueName + " was not created", _queueRegistry.getQueue(queueName));
    }

    public void testPriorityQueueRegistration() throws Exception
    {
        FieldTable fieldTable = new FieldTable();
        fieldTable.put(new AMQShortString(AMQQueueFactory.X_QPID_PRIORITIES), 5);


        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("testPriorityQueue"), false, new AMQShortString("owner"), false,
                                           false, _virtualHost, fieldTable);

        assertEquals("Queue not a priorty queue", AMQPriorityQueue.class, queue.getClass());
        verifyQueueRegistered("testPriorityQueue");
        verifyRegisteredQueueCount(1);
    }


    public void testSimpleQueueRegistration() throws Exception
    {
        AMQShortString queueName = new AMQShortString("testSimpleQueueRegistration");
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);

        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueName, false, new AMQShortString("owner"), false,
                                           false, _virtualHost, null);
        assertEquals("Queue not a simple queue", SimpleAMQQueue.class, queue.getClass());
        verifyQueueRegistered("testSimpleQueueRegistration");

        //verify that no alternate exchange or DLQ were produced
        QueueRegistry qReg = _virtualHost.getQueueRegistry();

        assertNull("Queue should not have an alternate exchange as DLQ wasnt enabled", queue.getAlternateExchange());
        assertNull("The DLQ should not exist", qReg.getQueue(dlQueueName));

        verifyRegisteredQueueCount(1);
    }

    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_DLQ_ENABLED} argument true does
     * cause the alternate exchange to be set and DLQ to be produced.
     * @throws AMQException
     */
    public void testDeadLetterQueueEnabled() throws AMQException
    {
        FieldTable fieldTable = new FieldTable();
        fieldTable.setBoolean(AMQQueueFactory.X_QPID_DLQ_ENABLED, true);

        AMQShortString queueName = new AMQShortString("testDeadLetterQueueEnabled");
        AMQShortString dlExchangeName = new AMQShortString(queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);

        QueueRegistry qReg = _virtualHost.getQueueRegistry();
        ExchangeRegistry exReg = _virtualHost.getExchangeRegistry();

        assertNull("The DLQ should not yet exist", qReg.getQueue(dlQueueName));
        assertNull("The alternate exchange should not yet exist", exReg.getExchange(dlExchangeName));

        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueName, false, new AMQShortString("owner"), false, false,
                                           _virtualHost, fieldTable);

        Exchange altExchange = queue.getAlternateExchange();
        assertNotNull("Queue should have an alternate exchange as DLQ is enabled", altExchange);
        assertEquals("Alternate exchange name was not as expected", dlExchangeName, altExchange.getName());
        assertEquals("Alternate exchange type was not as expected", ExchangeDefaults.FANOUT_EXCHANGE_CLASS, altExchange.getType().getName());

        assertNotNull("The alternate exchange was not registered as expected", exReg.getExchange(dlExchangeName));
        assertEquals("The registered exchange was not the expected exchange instance", altExchange, exReg.getExchange(dlExchangeName));

        AMQQueue dlQueue = qReg.getQueue(dlQueueName);
        assertNotNull("The DLQ was not registered as expected", dlQueue);
        assertTrue("DLQ should have been bound to the alternate exchange", altExchange.isBound(dlQueue));
        assertNull("DLQ should have no alternate exchange", dlQueue.getAlternateExchange());
        assertEquals("DLQ should have a zero maximum delivery count", 0, dlQueue.getMaximumDeliveryCount());

        //2 queues should have been registered
        verifyRegisteredQueueCount(2);
    }

    /**
     * Tests that the deadLetterQueues/maximumDeliveryCount settings from the configuration
     * are not applied to the DLQ itself.
     * @throws AMQException
     */
    public void testDeadLetterQueueDoesNotInheritDLQorMDCSettings() throws AMQException
    {
        ApplicationRegistry.getInstance().getConfiguration().getConfig().addProperty("deadLetterQueues","true");
        ApplicationRegistry.getInstance().getConfiguration().getConfig().addProperty("maximumDeliveryCount","5");

        AMQShortString queueName = new AMQShortString("testDeadLetterQueueEnabled");
        AMQShortString dlExchangeName = new AMQShortString(queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);

        QueueRegistry qReg = _virtualHost.getQueueRegistry();
        ExchangeRegistry exReg = _virtualHost.getExchangeRegistry();

        assertNull("The DLQ should not yet exist", qReg.getQueue(dlQueueName));
        assertNull("The alternate exchange should not yet exist", exReg.getExchange(dlExchangeName));

        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueName, false, new AMQShortString("owner"), false, false,
                                           _virtualHost, null);

        assertEquals("Unexpected maximum delivery count", 5, queue.getMaximumDeliveryCount());
        Exchange altExchange = queue.getAlternateExchange();
        assertNotNull("Queue should have an alternate exchange as DLQ is enabled", altExchange);
        assertEquals("Alternate exchange name was not as expected", dlExchangeName, altExchange.getName());
        assertEquals("Alternate exchange type was not as expected", ExchangeDefaults.FANOUT_EXCHANGE_CLASS, altExchange.getType().getName());

        assertNotNull("The alternate exchange was not registered as expected", exReg.getExchange(dlExchangeName));
        assertEquals("The registered exchange was not the expected exchange instance", altExchange, exReg.getExchange(dlExchangeName));

        AMQQueue dlQueue = qReg.getQueue(dlQueueName);
        assertNotNull("The DLQ was not registered as expected", dlQueue);
        assertTrue("DLQ should have been bound to the alternate exchange", altExchange.isBound(dlQueue));
        assertNull("DLQ should have no alternate exchange", dlQueue.getAlternateExchange());
        assertEquals("DLQ should have a zero maximum delivery count", 0, dlQueue.getMaximumDeliveryCount());

        //2 queues should have been registered
        verifyRegisteredQueueCount(2);
    }

    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_DLQ_ENABLED} argument false does not
     * result in the alternate exchange being set and DLQ being created.
     * @throws AMQException
     */
    public void testDeadLetterQueueDisabled() throws AMQException
    {
        FieldTable fieldTable = new FieldTable();
        fieldTable.setBoolean(AMQQueueFactory.X_QPID_DLQ_ENABLED, false);

        AMQShortString queueName = new AMQShortString("testDeadLetterQueueDisabled");
        AMQShortString dlExchangeName = new AMQShortString(queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);

        QueueRegistry qReg = _virtualHost.getQueueRegistry();
        ExchangeRegistry exReg = _virtualHost.getExchangeRegistry();

        assertNull("The DLQ should not yet exist", qReg.getQueue(dlQueueName));
        assertNull("The alternate exchange should not exist", exReg.getExchange(dlExchangeName));

        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueName, false, new AMQShortString("owner"), false, false,
                                           _virtualHost, fieldTable);

        assertNull("Queue should not have an alternate exchange as DLQ is disabled", queue.getAlternateExchange());
        assertNull("The alternate exchange should still not exist", exReg.getExchange(dlExchangeName));

        assertNull("The DLQ should still not exist", qReg.getQueue(dlQueueName));

        //only 1 queue should have been registered
        verifyRegisteredQueueCount(1);
    }

    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_DLQ_ENABLED} argument true but
     * creating an auto-delete queue, does not result in the alternate exchange
     * being set and DLQ being created.
     * @throws AMQException
     */
    public void testDeadLetterQueueNotCreatedForAutodeleteQueues() throws AMQException
    {
        FieldTable fieldTable = new FieldTable();
        fieldTable.setBoolean(AMQQueueFactory.X_QPID_DLQ_ENABLED, true);

        AMQShortString queueName = new AMQShortString("testDeadLetterQueueNotCreatedForAutodeleteQueues");
        AMQShortString dlExchangeName = new AMQShortString(queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);

        QueueRegistry qReg = _virtualHost.getQueueRegistry();
        ExchangeRegistry exReg = _virtualHost.getExchangeRegistry();

        assertNull("The DLQ should not yet exist", qReg.getQueue(dlQueueName));
        assertNull("The alternate exchange should not exist", exReg.getExchange(dlExchangeName));

        //create an autodelete queue
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueName, false, new AMQShortString("owner"), true, false,
                                           _virtualHost, fieldTable);
        assertTrue("Queue should be autodelete", queue.isAutoDelete());

        //ensure that the autodelete property overrides the request to enable DLQ
        assertNull("Queue should not have an alternate exchange as queue is autodelete", queue.getAlternateExchange());
        assertNull("The alternate exchange should not exist as queue is autodelete", exReg.getExchange(dlExchangeName));
        assertNull("The DLQ should not exist as queue is autodelete", qReg.getQueue(dlQueueName));

        //only 1 queue should have been registered
        verifyRegisteredQueueCount(1);
    }

    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_MAXIMUM_DELIVERY_COUNT} argument has
     * the desired effect.
     */
    public void testMaximumDeliveryCount() throws Exception
    {
        final FieldTable fieldTable = new FieldTable();
        fieldTable.setInteger(AMQQueueFactory.X_QPID_MAXIMUM_DELIVERY_COUNT, 5);

        final AMQShortString queueName = new AMQShortString("testMaximumDeliveryCount");

        final AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueName, false, new AMQShortString("owner"), false, false,
                                           _virtualHost, fieldTable);

        assertNotNull("The queue was not registered as expected ", queue);
        assertEquals("Maximum delivery count not as expected", 5, queue.getMaximumDeliveryCount());

        verifyRegisteredQueueCount(1);
    }

    /**
     * Tests that omitting the {@link AMQQueueFactory#X_QPID_MAXIMUM_DELIVERY_COUNT} argument means
     * that queue is created with a default maximumDeliveryCount of zero (unless set in config).
     */
    public void testMaximumDeliveryCountDefault() throws Exception
    {

        final AMQShortString queueName = new AMQShortString("testMaximumDeliveryCount");

        final AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueName, false, new AMQShortString("owner"), false, false,
                                           _virtualHost, null);

        assertNotNull("The queue was not registered as expected ", queue);
        assertEquals("Maximum delivery count not as expected", 0, queue.getMaximumDeliveryCount());

        verifyRegisteredQueueCount(1);
    }

    /**
     * Tests queue creation with queue name set to null
     */
    public void testQueueNameNullValidation()
    {
        try
        {
            AMQQueueFactory.createAMQQueueImpl(null, false, new AMQShortString("owner"), true, false, _virtualHost, null);
            fail("queue with null name can not be created!");
        }
        catch (Exception e)
        {
            assertTrue(e instanceof IllegalArgumentException);
            assertEquals("Queue name must not be null", e.getMessage());
        }
    }

    /**
     * Tests queue creation with queue name length less 255 characters but
     * corresponding DLQ name length greater than 255.
     */
    public void testQueueNameWithLengthLessThan255ButDLQNameWithLengthGreaterThan255()
    {
        String queueName = "test-" + generateStringWithLength('a', 245);
        try
        {
            // change DLQ name to make its length bigger than exchange name
            ApplicationRegistry.getInstance().getConfiguration().getConfig()
                    .addProperty("deadLetterExchangeSuffix", "_DLE");
            ApplicationRegistry.getInstance().getConfiguration().getConfig()
                    .addProperty("deadLetterQueueSuffix", "_DLQUEUE");

            FieldTable fieldTable = new FieldTable();
            fieldTable.setBoolean(AMQQueueFactory.X_QPID_DLQ_ENABLED, true);
            AMQQueueFactory.createAMQQueueImpl(new AMQShortString(queueName), false, new AMQShortString("owner"),
                    false, false, _virtualHost, fieldTable);
            fail("queue with DLQ name having more than 255 characters can not be created!");
        }
        catch (Exception e)
        {
            assertTrue("Unexpected exception is thrown!", e instanceof IllegalArgumentException);
            assertTrue("Unexpected exception message!", e.getMessage().contains("DLQ queue name")
                    && e.getMessage().contains("length exceeds limit of 255"));
        }
        finally
        {
            ApplicationRegistry.getInstance().getConfiguration().getConfig()
                    .addProperty("deadLetterExchangeSuffix", DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
            ApplicationRegistry.getInstance().getConfiguration().getConfig()
                    .addProperty("deadLetterQueueSuffix", AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);
        }
    }

    /**
     * Tests queue creation with queue name length less 255 characters but
     * corresponding DL exchange name length greater than 255.
     */
    public void testQueueNameWithLengthLessThan255ButDLExchangeNameWithLengthGreaterThan255()
    {
        String queueName = "test-" + generateStringWithLength('a', 245);
        try
        {
            // change DLQ name to make its length bigger than exchange name
            ApplicationRegistry.getInstance().getConfiguration().getConfig()
                    .addProperty("deadLetterExchangeSuffix", "_DLEXCHANGE");
            ApplicationRegistry.getInstance().getConfiguration().getConfig()
                    .addProperty("deadLetterQueueSuffix", "_DLQ");

            FieldTable fieldTable = new FieldTable();
            fieldTable.setBoolean(AMQQueueFactory.X_QPID_DLQ_ENABLED, true);
            AMQQueueFactory.createAMQQueueImpl(new AMQShortString(queueName), false, new AMQShortString("owner"),
                    false, false, _virtualHost, fieldTable);
            fail("queue with DLE name having more than 255 characters can not be created!");
        }
        catch (Exception e)
        {
            assertTrue("Unexpected exception is thrown!", e instanceof IllegalArgumentException);
            assertTrue("Unexpected exception message!", e.getMessage().contains("DL exchange name")
                    && e.getMessage().contains("length exceeds limit of 255"));
        }
        finally
        {
            ApplicationRegistry.getInstance().getConfiguration().getConfig()
                    .addProperty("deadLetterExchangeSuffix", DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
            ApplicationRegistry.getInstance().getConfiguration().getConfig()
                    .addProperty("deadLetterQueueSuffix", AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);
        }
    }

    private String generateStringWithLength(char ch, int length)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++)
        {
            sb.append(ch);
        }
        return sb.toString();
    }
}
